#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${RAG_SMOKE_BASE_URL:-http://localhost:8080}"
CONNECT_TIMEOUT="${RAG_SMOKE_CONNECT_TIMEOUT_SECONDS:-5}"
REQUEST_TIMEOUT="${RAG_SMOKE_REQUEST_TIMEOUT_SECONDS:-30}"
REPO_URL="${RAG_SMOKE_REPO_URL:-https://github.com/codereviewx/fixture-repo}"
REF="${RAG_SMOKE_REF:?Set RAG_SMOKE_REF to the indexed PR head ref or commit SHA}"
PR_NUMBER="${RAG_SMOKE_PR_NUMBER:-1}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "RAG_SMOKE_FAIL: $1" >&2; exit 1; }
get() { curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" --max-time "$REQUEST_TIMEOUT" --retry 3 "$BASE_URL$1"; }
post() { curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" --max-time "$REQUEST_TIMEOUT" --retry 3 -H 'Content-Type: application/json' -d "$2" "$BASE_URL$1"; }

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
  remaining=$(( deadline - $(date +%s) ));
  (( remaining > 0 )) || break
  request_window=$(( remaining < REQUEST_TIMEOUT ? remaining : REQUEST_TIMEOUT ));
  status_json="$(curl --fail-with-body --silent --show-error --connect-timeout "$CONNECT_TIMEOUT" --max-time "$request_window" "$BASE_URL/api/repositories/$owner/$repo/index-status?ref=$REF")" || true
  status="$(echo "$status_json" | jq -r '.data.status // empty')"
  [[ "$status" == READY ]] && break
  [[ "$status" == FAILED ]] && fail "index job failed"
  remaining=$(( deadline - $(date +%s) ));
  (( remaining > 0 )) || break
  sleep $(( remaining < 5 ? remaining : 5 ))
done
[[ "$status" == READY ]] || fail "index did not reach READY within five minutes"

review="$(post /api/review-tasks "$(jq -nc --arg repo "$REPO_URL" --argjson pr "$PR_NUMBER" '{repoUrl:$repo,prNumber:$pr,provider:"mimo",reviewMode:"GITHUB_PR"}')")" || fail "GitHub PR review request failed"
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
  --connect-timeout "$CONNECT_TIMEOUT" --max-time "$REQUEST_TIMEOUT" \
  -H 'Content-Type: application/json' -d '{"confirmed":false}' \
  "$BASE_URL/api/review-runs/$run_id/comment-previews/$preview_id/publish")"
if [[ "$code" != 400 ]]; then
  fail "unconfirmed publish was accepted"
fi
after="$(get "/api/review-runs/$run_id/comment-previews" | jq -c --argjson id "$preview_id" '(.data.items // .data)[] | select(.id == $id) | {id,publishStatus,selectedForPublish}')"
[[ "$before" == "$after" ]] || fail "unconfirmed publish changed preview state"

echo "RAG_SMOKE_PASS jobId=$job_id runId=$run_id selectedChunkCount=$selected_count degraded=false"
