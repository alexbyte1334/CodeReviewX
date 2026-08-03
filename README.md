# CodeReviewX

CodeReviewX is a personal macOS code-review workspace. It accepts a GitHub pull request, runs a bounded OpenAI-compatible review model with optional external RAG, streams durable progress, and exposes Evidence-backed comment previews for explicit human approval. Credentials stay on the local Mac and are stored through macOS Keychain.

The Personal Edition uses an OpenAI-compatible model endpoint and is packaged as an Apple Silicon macOS DMG. See [the personal macOS installation guide](docs/INSTALL_PERSONAL_MAC.md). The DMG includes the desktop shell, Java runtime and PostgreSQL/pgvector runtime; Embedding and Rerank remain external services configured during first launch.

## Quick start: personal macOS Release

Download the Apple Silicon DMG from the [latest GitHub Release](https://github.com/alexbyte1334/CodeReviewX/releases/latest), drag `CodeReviewX.app` into Applications, and complete the first-launch wizard. You do not need Docker, Homebrew, Java, or PostgreSQL. The wizard stores API credentials in macOS Keychain and keeps the local database under `~/Library/Application Support/CodeReviewX/`.

GitHub Token and model configuration are required for a real Review. Embedding and Rerank are optional: without them, the app runs in degraded mode and allows Review plus local Preview, but the backend blocks GitHub comment publishing until Evidence is available. See [INSTALL_PERSONAL_MAC.md](docs/INSTALL_PERSONAL_MAC.md) for permissions, troubleshooting, and clean-Mac acceptance.

## Quick start: source / Docker

Copy `.env.example` to `.env`, fill GitHub, OpenAI-compatible model, embedding, rerank and PostgreSQL values, then run `docker compose up --build -d`. Open `http://localhost:3000` and enter a repository URL and pull request number.

This baseline supports a new database only; back up existing data before resetting it. The product has no public fixed Demo, GitHub App, multi-tenant mode, automatic repair, or dynamic Python Agent runtime.

## Public API

- `POST /api/reviews`
- `GET /api/reviews/{uuid}`
- `GET /api/reviews/{uuid}/events`
- `POST /api/reviews/{uuid}/retry`
- `GET /api/reviews/{uuid}/issues/{issueKey}/evidence`
- `GET /api/reviews/{uuid}/previews`
- `PATCH /api/reviews/{uuid}/previews/selection`
- `POST /api/reviews/{uuid}/previews/publish`
- `GET /api/reviews/{uuid}/trace`
- `GET /api/reviews/{uuid}/retrieval`

`POST /api/reviews` requires an `Idempotency-Key`. Every GitHub write requires explicit confirmed Preview approval, valid Evidence, and uses a stable marker to avoid duplicate comments.

## Verification

Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test` in `backend-java`, `npm ci && npm test && npm run typecheck && npm run build` in `frontend`, then run `node scripts/static-scan.mjs`. To build the arm64 DMG locally, run `desktop/prepare-postgresql-arm64.sh` followed by `desktop/build-arm64.sh`.

Formal RAG reports remain under `evals/rag/reports/`; credentials and runtime build output are never committed.
