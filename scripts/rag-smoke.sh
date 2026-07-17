#!/usr/bin/env bash
set -euo pipefail

fail() { echo "RAG_SMOKE_FAIL: $1" >&2; exit 1; }
normalize_timeout() {
  local name="$1" value="$2" maximum="$3"
  case "$value" in
    ''|*[!0-9]*) fail "$name must be a positive decimal integer" ;;
  esac
  while [[ "${#value}" -gt 1 && "$value" == 0* ]]; do value="${value#0}"; done
  [[ "$value" != 0 ]] || fail "$name must be greater than zero"
  if [[ "${#value}" -gt "${#maximum}" ]] \
      || [[ "${#value}" -eq "${#maximum}" && "$value" > "$maximum" ]]; then
    fail "$name must not exceed $maximum seconds"
  fi
  printf '%s' "$value"
}

BASE_URL="${RAG_SMOKE_BASE_URL:-http://localhost:8080}"
REQUEST_TIMEOUT="$(normalize_timeout RAG_SMOKE_REQUEST_TIMEOUT_SECONDS "${RAG_SMOKE_REQUEST_TIMEOUT_SECONDS:-30}" 300)"
CONNECT_TIMEOUT="$(normalize_timeout RAG_SMOKE_CONNECT_TIMEOUT_SECONDS "${RAG_SMOKE_CONNECT_TIMEOUT_SECONDS:-5}" 60)"
GET_TIMEOUT="$(normalize_timeout RAG_SMOKE_GET_TIMEOUT_SECONDS "${RAG_SMOKE_GET_TIMEOUT_SECONDS:-$REQUEST_TIMEOUT}" 300)"
POST_TIMEOUT="$(normalize_timeout RAG_SMOKE_POST_TIMEOUT_SECONDS "${RAG_SMOKE_POST_TIMEOUT_SECONDS:-$REQUEST_TIMEOUT}" 300)"
REVIEW_TIMEOUT="$(normalize_timeout RAG_SMOKE_REVIEW_TIMEOUT_SECONDS "${RAG_SMOKE_REVIEW_TIMEOUT_SECONDS:-180}" 1800)"
REPO_URL="${RAG_SMOKE_REPO_URL:-https://github.com/codereviewx/fixture-repo}"
REF="${RAG_SMOKE_REF:?Set RAG_SMOKE_REF to the indexed PR head ref or commit SHA}"
PR_NUMBER="${RAG_SMOKE_PR_NUMBER:-1}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

get() {
  local path="$1" attempt response
  for attempt in 1 2 3 4; do
    if response="$(curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" \
        --max-time "$GET_TIMEOUT" "$BASE_URL$path")"; then
      printf '%s' "$response"
      return 0
    fi
  done
  return 1
}
get_before_deadline() {
  local path="$1" deadline="$2" attempt now remaining request_window response
  for attempt in 1 2 3 4; do
    now="$(date +%s)"
    (( now < deadline )) || return 1
    remaining=$(( deadline - now ))
    request_window=$(( remaining < GET_TIMEOUT ? remaining : GET_TIMEOUT ))
    if response="$(curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" \
        --max-time "$request_window" "$BASE_URL$path")"; then
      printf '%s' "$response"
      return 0
    fi
  done
  return 1
}
post() { curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" --max-time "$POST_TIMEOUT" -H 'Content-Type: application/json' -d "$2" "$BASE_URL$1"; }
post_review() { curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" --max-time "$REVIEW_TIMEOUT" -H 'Content-Type: application/json' -d "$2" "$BASE_URL$1"; }

[[ "$REF" =~ ^[A-Za-z0-9_.-]{1,255}$ ]] || fail "ref is invalid"
status_query_key="ref"
[[ "$REF" =~ ^[0-9a-f]{40}$ ]] && status_query_key="commitSha"

health="$(get /api/health)" || fail "health endpoint unavailable"
echo "$health" | jq -e '.success == true and .data.status == "UP"' >/dev/null || fail "liveness is not UP"
echo "$health" | jq -e '.data.ragReady == true' >/dev/null || fail "RAG readiness is DOWN"

index="$(post /api/repositories/index "{\"repoUrl\":\"$REPO_URL\",\"ref\":\"$REF\"}")" || fail "index request failed"
job_id="$(echo "$index" | jq -er '.data.jobId // .data.id')" || fail "index response has no job id"
owner="$(echo "$REPO_URL" | sed -E 's#https?://github.com/([^/]+)/.*#\1#')"
repo="$(echo "$REPO_URL" | sed -E 's#https?://github.com/[^/]+/([^/]+).*#\1#')"
deadline=$(( $(date +%s) + 300 ))
status=""
while (( $(date +%s) < deadline )); do
  status_json="$(get_before_deadline "/api/repositories/$owner/$repo/index-status?$status_query_key=$REF" "$deadline")" || true
  status="$(echo "$status_json" | jq -r '.data.status // empty')"
  [[ "$status" == READY ]] && break
  [[ "$status" == FAILED ]] && fail "index job failed"
  remaining=$(( deadline - $(date +%s) ));
  (( remaining > 0 )) || break
  sleep $(( remaining < 5 ? remaining : 5 ))
done
[[ "$status" == READY ]] || fail "index did not reach READY within five minutes"

review="$(post_review /api/review-tasks "$(jq -nc --arg repo "$REPO_URL" --argjson pr "$PR_NUMBER" '{repoUrl:$repo,prNumber:$pr,provider:"mimo",reviewMode:"GITHUB_PR"}')")" || fail "GitHub PR review request failed"
run_id="$(echo "$review" | jq -er '.data.latestRunId')" || fail "review response has no run id"
task_id="$(echo "$review" | jq -er '.data.id')" || fail "review response has no task id"
trace="$(get "/api/review-runs/$run_id/trace")" || fail "trace unavailable"
echo "$trace" | jq -e --argjson expected '["rag.index.ensure","rag.query.build","rag.retrieve.hybrid","rag.rerank","rag.context.assemble"]' '([.data.items[]?.toolName] as $actual | ($expected - $actual | length) == 0 and ($actual | index("repository.context.index")) == null)' >/dev/null || fail "RAG trace contract missing or legacy trace present"
selected="$(get "/api/review-runs/$run_id/retrieval")" || fail "retrieval trace unavailable"
echo "$selected" | jq -e '.data.selectedCount > 0' >/dev/null || fail "selected chunk count is empty"
echo "$selected" | jq -e '.data.degraded == false' >/dev/null || fail "retrieval is degraded"
selected_count="$(echo "$selected" | jq -er '.data.selectedCount')"
issue_key="$(echo "$review" | jq -er '.data.issues[0].id')" || fail "review produced no issue evidence target"
evidence="$(get "/api/review-tasks/$task_id/issues/$issue_key/evidence")" || fail "evidence endpoint unavailable"
echo "$evidence" | jq -e '.data | length > 0' >/dev/null || fail "issue evidence is empty"
previews="$(get "/api/review-runs/$run_id/comment-previews")" || fail "comment previews unavailable"
preview_id="$(echo "$previews" | jq -er '.data.items[0].id // .data[0].id')" || fail "no comment preview"
before="$(echo "$previews" | jq -c --argjson id "$preview_id" '(.data.items // .data)[] | select(.id == $id) | {id,publishStatus,selectedForPublish}')"
code="$(curl --silent --show-error --output "$TMP/publish.json" --write-out '%{http_code}' \
  --connect-timeout "$CONNECT_TIMEOUT" --max-time "$POST_TIMEOUT" \
  -H 'Content-Type: application/json' -d '{"confirmed":false}' \
  "$BASE_URL/api/review-runs/$run_id/comment-previews/$preview_id/publish")"
if [[ "$code" != 400 ]]; then
  fail "unconfirmed publish was accepted"
fi
after="$(get "/api/review-runs/$run_id/comment-previews" | jq -c --argjson id "$preview_id" '(.data.items // .data)[] | select(.id == $id) | {id,publishStatus,selectedForPublish}')"
[[ "$before" == "$after" ]] || fail "unconfirmed publish changed preview state"

echo "RAG_SMOKE_PASS jobId=$job_id runId=$run_id selectedChunkCount=$selected_count degraded=false"
