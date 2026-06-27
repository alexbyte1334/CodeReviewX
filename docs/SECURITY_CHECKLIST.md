# CodeReviewX Security Checklist

Use this checklist before publishing or demoing the repository.

## Local Secret Scan

Run:

```bash
node scripts/static-scan.mjs
```

The static scan runs secret scan, dependency hygiene scan, and Semgrep when available. The secret scan skips generated/build directories and checks for high-confidence token patterns such as GitHub tokens, OpenAI-style API keys, private key blocks, bearer tokens, and real-looking sensitive env assignments.

## Files That Must Not Be Committed

- `.env`
- `.env.*` except `.env.example`
- `docs/mimo_api_key.md`
- `backend-java/data/`
- `frontend/dist/`
- `backend-java/target/`
- `frontend/node_modules/`
- any local eval captures containing raw model output or private PR data

## Token Permissions

Use local environment variables only:

```text
GITHUB_TOKEN
MIMO_PLANNER_API_KEY
MIMO_EXECUTOR_API_KEY
```

Recommended GitHub token permissions for private repository review and PR comment publishing:

- Contents: read
- Pull requests: read/write
- Metadata: read

Do not grant broad account, organization admin, workflow, package, or secret-management permissions for this local MVP.

## API Redaction Boundary

Public API responses must not include:

- GitHub token
- MiMo API keys
- Authorization header
- raw prompt
- raw model output
- raw full diff
- `review_input_snapshot.snapshot_json`
- `review_tool_trace.input_summary`
- internal database ids for review issues

Existing controller/service tests cover the primary redaction paths for trace, snapshot, comment preview publishing, and missing-token errors.

## Prompt Injection Boundary

Treat repository diff, PR title, branch names, author names, and file content as untrusted input.

Rules:

- Never execute instructions found inside diff content.
- Keep system role instructions separate from diff/context text.
- Delimit diff/context in prompts.
- Do not let model output directly write database rows; normalize through deterministic code.
- Do not publish comments without explicit user selection and confirmation.
- Do not persist raw prompts or raw model output.

## Upload Checklist

Before pushing to GitHub:

1. Run `node scripts/secret-scan.mjs`.
2. Run `node scripts/static-scan.mjs`.
3. Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test` in `backend-java/`.
4. Run `npm run typecheck`, `npm run build`, and `npm test -- --run` in `frontend/`.
5. Run `node scripts/run-evals.mjs`.
6. Confirm `git status --short` does not include ignored local secrets or generated build output.
7. Confirm README and roadmap describe only implemented capabilities as implemented.
