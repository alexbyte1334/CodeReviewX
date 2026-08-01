# CodeReviewX

CodeReviewX is an evidence-constrained Java review workflow for self-hosted developers. It accepts a GitHub pull request, runs the bounded MiMo + RAG + Evidence Gate pipeline, streams durable progress, and exposes comment previews for explicit human approval.

## Quick start

Copy `.env.example` to `.env`, fill GitHub, MiMo, embedding, rerank and PostgreSQL values, then run `docker compose up --build -d`. Open `http://localhost:3000` and enter a repository URL and pull request number.

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

`POST /api/reviews` requires an `Idempotency-Key`. Every GitHub write requires explicit confirmed Preview approval and uses a stable marker to avoid duplicate comments.

## Verification

Run `mvn test` in `backend-java`, `npm ci && npm test && npm run typecheck && npm run build` in `frontend`, then run the static, secret, dependency and Docker scans from the repository root.

Formal RAG reports remain under `evals/rag/reports/`; credentials and runtime build output are never committed.
