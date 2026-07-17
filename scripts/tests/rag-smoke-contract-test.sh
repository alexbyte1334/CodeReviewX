#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"

cat >"$TMP/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '%q ' "$@" >>"$CURL_LOG"
printf '\n' >>"$CURL_LOG"

args=" $* "
output_file=""
previous=""
for argument in "$@"; do
  if [[ "$previous" == "--output" ]]; then
    output_file="$argument"
    break
  fi
  previous="$argument"
done

case "$args" in
  *" /api/health "*|*"/api/health "*)
    printf '%s\n' '{"success":true,"data":{"status":"UP","ragReady":true}}'
    ;;
  *"/api/repositories/index "*)
    printf '%s\n' '{"success":true,"data":{"jobId":7}}'
    ;;
  *"/index-status?"*)
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
  *"/evidence "*)
    printf '%s\n' '{"success":true,"data":[{"id":1}]}'
    ;;
  *"/publish "*)
    printf '%s\n' '{"success":false}' >"$output_file"
    printf '400'
    ;;
  *"/comment-previews "*)
    printf '%s\n' '{"success":true,"data":{"items":[{"id":20,"publishStatus":"PENDING","selectedForPublish":false}]}}'
    ;;
  *)
    printf 'unexpected curl invocation: %s\n' "$*" >&2
    exit 90
    ;;
esac
FAKE_CURL
chmod +x "$TMP/bin/curl"

CURL_LOG="$TMP/curl.log" \
PATH="$TMP/bin:$PATH" \
RAG_SMOKE_REF="$(printf 'a%.0s' {1..40})" \
RAG_SMOKE_CONNECT_TIMEOUT_SECONDS=005 \
RAG_SMOKE_REQUEST_TIMEOUT_SECONDS=017 \
RAG_SMOKE_GET_TIMEOUT_SECONDS=019 \
RAG_SMOKE_POST_TIMEOUT_SECONDS=023 \
RAG_SMOKE_REVIEW_TIMEOUT_SECONDS=0181 \
bash "$ROOT/scripts/rag-smoke.sh" >"$TMP/smoke.out"

invocation() {
  local endpoint="$1"
  grep -F "$endpoint" "$TMP/curl.log" | head -n 1
}

assert_no_retry() {
  local name="$1" line="$2"
  if [[ "$line" == *"--retry"* ]]; then
    printf 'FAIL: %s must not use curl built-in retry: %s\n' "$name" "$line" >&2
    exit 1
  fi
}

assert_timeout() {
  local name="$1" expected="$2" line="$3"
  [[ "$line" == *"--max-time $expected"* ]] || {
    printf 'FAIL: %s must use --max-time %s: %s\n' "$name" "$expected" "$line" >&2
    exit 1
  }
}

index_line="$(invocation '/api/repositories/index')"
review_line="$(invocation '/api/review-tasks')"
health_line="$(invocation '/api/health')"
status_line="$(invocation '/index-status')"
publish_line="$(invocation '/publish')"

assert_timeout "health GET" 19 "$health_line"
assert_timeout "index-status GET" 19 "$status_line"
assert_timeout "index POST" 23 "$index_line"
assert_timeout "publish POST" 23 "$publish_line"
assert_timeout "review POST" 181 "$review_line"
assert_no_retry "index POST" "$index_line"
assert_no_retry "review POST" "$review_line"
assert_no_retry "publish POST" "$publish_line"
assert_no_retry "health GET" "$health_line"
assert_no_retry "index-status GET" "$status_line"

assert_rejected_timeout() {
  local variable="$1" value="$2" label="$3"
  if env PATH="$TMP/bin:$PATH" CURL_LOG="$TMP/invalid-curl.log" \
      RAG_SMOKE_REF="$(printf 'b%.0s' {1..40})" "$variable=$value" \
      bash "$ROOT/scripts/rag-smoke.sh" >"$TMP/invalid.out" 2>"$TMP/invalid.err"; then
    printf 'FAIL: %s timeout was accepted\n' "$label" >&2
    exit 1
  fi
}

marker="$TMP/timeout-injection-executed"
assert_rejected_timeout RAG_SMOKE_GET_TIMEOUT_SECONDS \
  "\$(touch '$marker')" "command-substitution"
[[ ! -e "$marker" ]] || {
  printf 'FAIL: timeout validation executed command substitution\n' >&2
  exit 1
}
assert_rejected_timeout RAG_SMOKE_CONNECT_TIMEOUT_SECONDS 0 "zero"
assert_rejected_timeout RAG_SMOKE_POST_TIMEOUT_SECONDS -1 "negative"
assert_rejected_timeout RAG_SMOKE_REVIEW_TIMEOUT_SECONDS abc "non-numeric"
assert_rejected_timeout RAG_SMOKE_REQUEST_TIMEOUT_SECONDS 999999999 "over-limit"

printf 'PASS: rag smoke curl timeout and retry contract\n'
