#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/state"

cat >"$TMP/bin/date" <<'FAKE_DATE'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == "+%s" ]] || exit 91
count_file="$FAKE_STATE/date.count"
count=0
[[ ! -f "$count_file" ]] || count="$(cat "$count_file")"
count=$(( count + 1 ))
printf '%s\n' "$count" >"$count_file"
if [[ "$FAKE_MODE" == deadline && "$count" == 2 ]]; then
  printf '1298\n' >"$FAKE_CLOCK"
fi
cat "$FAKE_CLOCK"
FAKE_DATE

cat >"$TMP/bin/sleep" <<'FAKE_SLEEP'
#!/usr/bin/env bash
set -euo pipefail
now="$(cat "$FAKE_CLOCK")"
printf '%s\n' "$(( now + $1 ))" >"$FAKE_CLOCK"
FAKE_SLEEP

cat >"$TMP/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '%q ' "$@" >>"$CURL_LOG"
printf '\n' >>"$CURL_LOG"
args=" $* "

count_call() {
  local name="$1" file="$FAKE_STATE/$1.count" count=0
  [[ ! -f "$file" ]] || count="$(cat "$file")"
  count=$(( count + 1 ))
  printf '%s\n' "$count" >"$file"
  printf '%s' "$count"
}

max_time=0
output_file=""
previous=""
for argument in "$@"; do
  [[ "$previous" != "--max-time" ]] || max_time="$argument"
  [[ "$previous" != "--output" ]] || output_file="$argument"
  previous="$argument"
done

case "$args" in
  *"/api/health "*)
    count="$(count_call health)"
    if [[ "$FAKE_MODE" == get-body && "$count" -lt 3 ]]; then
      printf 'upstream-error-health\n'
      exit 22
    fi
    printf '%s\n' '{"success":true,"data":{"status":"UP","ragReady":true}}'
    ;;
  *"/api/repositories/index "*)
    printf '%s\n' '{"success":true,"data":{"jobId":7}}'
    ;;
  *"/index-status?"*)
    count="$(count_call status)"
    if [[ "$FAKE_MODE" == deadline ]]; then
      now="$(cat "$FAKE_CLOCK")"
      printf '%s\n' "$(( now + max_time ))" >"$FAKE_CLOCK"
      exit 28
    fi
    if [[ "$FAKE_MODE" == status-body && "$count" -lt 3 ]]; then
      printf 'upstream-error-status\n'
      exit 22
    fi
    printf '%s\n' '{"success":true,"data":{"status":"READY"}}'
    ;;
  *"/api/review-tasks "*)
    printf '%s\n' '{"success":true,"data":{"id":9,"latestRunId":8,"issues":[{"id":10}]}}'
    ;;
  *"/trace "*)
    printf '%s\n' '{"success":true,"data":{"items":[{"toolName":"rag.index.ensure"},{"toolName":"rag.query.build"},{"toolName":"rag.retrieve.hybrid"},{"toolName":"rag.rerank"},{"toolName":"rag.context.assemble"}]}}'
    ;;
  *"/retrieval "*)
    printf '%s\n' '{"success":true,"data":{"selectedCount":1,"degraded":false}}'
    ;;
  *"/evidence "*) printf '%s\n' '{"success":true,"data":[{"id":1}]}' ;;
  *"/publish "*)
    printf '%s\n' '{"success":false}' >"$output_file"
    printf '400'
    ;;
  *"/comment-previews "*)
    printf '%s\n' '{"success":true,"data":{"items":[{"id":20,"publishStatus":"PENDING","selectedForPublish":false}]}}'
    ;;
  *) exit 90 ;;
esac
FAKE_CURL
chmod +x "$TMP/bin/date" "$TMP/bin/sleep" "$TMP/bin/curl"

run_smoke() {
  local mode="$1" log="$2"
  rm -f "$TMP/state"/*.count
  printf '1000\n' >"$TMP/clock"
  env PATH="$TMP/bin:$PATH" CURL_LOG="$log" FAKE_STATE="$TMP/state" \
    FAKE_CLOCK="$TMP/clock" FAKE_MODE="$mode" \
    RAG_SMOKE_GET_TIMEOUT_SECONDS=19 RAG_SMOKE_REF="$(printf 'a%.0s' {1..40})" \
    bash "$ROOT/scripts/rag-smoke.sh"
}

run_smoke get-body "$TMP/get-body.log" >"$TMP/get-body.out"
[[ "$(cat "$TMP/state/health.count")" == 3 ]] || {
  printf 'FAIL: shell GET retry did not make three health attempts\n' >&2
  exit 1
}
if grep -q -- '--retry' "$TMP/get-body.log"; then
  printf 'FAIL: curl built-in retry remains enabled\n' >&2
  exit 1
fi

run_smoke status-body "$TMP/status-body.log" >"$TMP/status-body.out"
[[ "$(cat "$TMP/state/status.count")" == 3 ]] || {
  printf 'FAIL: shell status retry did not make three status attempts\n' >&2
  exit 1
}
if grep -q -- '--retry' "$TMP/status-body.log"; then
  printf 'FAIL: curl built-in retry remains enabled\n' >&2
  exit 1
fi

if run_smoke deadline "$TMP/deadline.log" >"$TMP/deadline.out" 2>"$TMP/deadline.err"; then
  printf 'FAIL: deadline scenario unexpectedly completed\n' >&2
  exit 1
fi
[[ "$(cat "$TMP/clock")" -le 1300 ]] || {
  printf 'FAIL: status retries crossed the five-minute deadline\n' >&2
  exit 1
}
[[ "$(cat "$TMP/state/status.count")" == 1 ]] || {
  printf 'FAIL: status retried after consuming the remaining deadline\n' >&2
  exit 1
}
status_line="$(grep -F '/index-status' "$TMP/deadline.log")"
[[ "$status_line" == *"--max-time 2"* ]] || {
  printf 'FAIL: final status attempt did not use the two-second remaining budget: %s\n' "$status_line" >&2
  exit 1
}

printf 'PASS: rag smoke shell retry and hard deadline contract\n'
