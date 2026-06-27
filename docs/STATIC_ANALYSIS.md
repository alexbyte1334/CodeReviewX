# CodeReviewX Static Analysis

CodeReviewX uses a lightweight static analysis toolchain suitable for a local MVP and resume project.

## Local Command

Run all local static checks:

```bash
node scripts/static-scan.mjs
```

This runs:

- `node scripts/secret-scan.mjs`
- `node scripts/dependency-scan.mjs`
- `semgrep scan --config .semgrep.yml --error` when Semgrep is installed

If Semgrep is not installed locally, the command skips Semgrep by default and prints guidance. To require Semgrep in CI or a strict local run:

```bash
REQUIRE_SEMGREP=1 node scripts/static-scan.mjs
```

## Semgrep

The Semgrep config lives in `.semgrep.yml`.

Install Semgrep locally with Homebrew:

```bash
brew install semgrep
```

On macOS/Homebrew, `scripts/static-scan.mjs` sets `SEMGREP_SEND_METRICS=off` by default and uses `/opt/homebrew/etc/ca-certificates/cert.pem` as `SSL_CERT_FILE` when present. This keeps local scans deterministic and avoids certificate-store issues from the Homebrew Semgrep runtime.

Current project rules focus on:

- preventing Authorization/header leaks in backend public response code
- avoiding hardcoded frontend API URLs outside `VITE_API_BASE_URL`
- preventing token-like values from being logged in frontend/scripts
- flagging `System.out` / `System.err` in backend main code

## Dependency Scan

`scripts/dependency-scan.mjs` is an offline dependency hygiene scan. It reads:

- `frontend/package.json`
- `frontend/package-lock.json`
- `backend-java/pom.xml`

It writes:

```text
evals/reports/dependency-scan-latest.json
```

It checks for:

- direct npm dependencies missing from the lockfile
- wildcard or `latest` npm dependency ranges
- Maven `SNAPSHOT` dependencies
- local MVP runtime notes such as H2 being compile/runtime scoped

This is not a CVE database scan. For a production project, add online vulnerability scanning such as `npm audit`, OWASP Dependency-Check, Dependabot, or GitHub Advanced Security.

## CI

`.github/workflows/ci.yml` includes a static analysis job that runs:

```bash
node scripts/static-scan.mjs
```

CI should install or provide Semgrep before setting `REQUIRE_SEMGREP=1`.
