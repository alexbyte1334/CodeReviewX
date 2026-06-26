# CodeReviewX Demo Script

**Manual Diff-Grounded AI Code Review Agent MVP**

Use this script for a local demo, about 10 minutes. Default mode uses the Mock provider, so no API key is required.

## 1. Start Backend

```bash
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run
```

Wait until the server is listening on port `8080`.

Note: only one backend instance can use the H2 file database at a time. Stop any existing instance before starting a new one.

## 2. Start Frontend

In a second terminal:

```bash
cd frontend
npm install   # first time only
npm run dev -- --host 127.0.0.1
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173).

## 3. Explain Product Positioning

CodeReviewX is a **Manual Diff-Grounded AI Code Review Agent MVP**.

- Users provide repository URL, PR number, and optionally paste a unified diff.
- The review agent produces structured findings with severity, category, and recommendations.
- Default mode uses deterministic mock findings for a safe local demo.
- Optional MiMo mode calls Xiaomi MiMo when `MIMO_API_KEY` is configured locally.

This MVP does not fetch PRs from GitHub, clone repositories, or write comments back to PRs.

## 4. Show Backend Status

Point to the backend connected indicator in the UI header.

Optional CLI check:

```bash
curl http://localhost:8080/api/health
```

Expected: `success=true`, `status=UP`.

## 5. Create Metadata-Only Review

In the create form:

- Repository URL: `https://github.com/example/demo`
- Pull Request Number: `1201`
- Leave Optional PR Diff empty

Click **Run Review Agent**.

Explain: without pasted diff, the agent runs with repo metadata only. In mock mode, this returns three deterministic demo findings.

## 6. Create Diff-Grounded Review

Create another task:

- Repository URL: `https://github.com/example/demo`
- Pull Request Number: `1202`
- Optional PR Diff: paste a small unified diff, for example:

```diff
diff --git a/src/AuthController.java b/src/AuthController.java
+String token = request.getHeader("Authorization");
+log.info("token={}", token);
```

Explain: when diff is provided, the review pipeline uses it as primary context. MiMo mode sends it in the prompt; mock mode still returns demo findings but the diff is persisted server-side for future real provider use.

Also mention guardrails:

- Whitespace-only diff is omitted.
- Diff over 20,000 characters is rejected.

## 7. Explain Review Summary

Select a completed task in Review History. The detail panel shows:

- Risk level: HIGH / MEDIUM / LOW / NONE
- Findings count and severity breakdown
- Reviewed target: repo + PR number
- Provider source: Mock Provider or Xiaomi MiMo
- Created timestamp

The backend computes `issueSummary` and `riskLevel`; the frontend displays them without recomputing unless needed for compatibility.

## 8. Explain Issue Cards

Each finding card includes:

- Severity, category, source, and status badges
- Title and location: file path + line range
- Description and recommendation

Source labels:

- `MOCK` -> Mock Provider, default
- `MIMO` -> Xiaomi MiMo, when configured and successful

## 9. Show Known Limitations

Be explicit that this MVP does not include:

- Automatic GitHub PR fetching or GitHub App integration
- Private repository access
- Repository clone
- Full repository analysis
- Production-grade review
- PR comment write-back
- Visual diff viewer or syntax highlighting
- RAG, MCP, Function Calling, or memory system
- Production auth or team model

Mock mode is the safe default. MiMo requires local `MIMO_API_KEY` configuration and falls back to mock on failure without exposing internals.

## 10. Explain Post-MVP Roadmap

Future directions, not implemented:

1. GitHub PR ingestion
2. Project rules / review policy
3. RAG / knowledge context
4. Function Calling / tool use
5. MCP integration
6. Memory system
7. PR comment workflow

## Optional: Xiaomi MiMo Mode

Only if `MIMO_API_KEY` is available locally. Never commit or display the key.

```bash
export MIMO_API_KEY="<local-secret>"
cd backend-java
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn spring-boot:run \
  -Dspring-boot.run.arguments="--codereviewx.review.provider=mimo"
```

Re-run a diff-grounded review. Successful MiMo responses show `source: MIMO`. Failures fall back to mock findings safely.
