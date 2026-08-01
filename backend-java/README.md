# CodeReviewX Java backend

The backend is a self-hosted Spring Boot service. The only review workflow is the UUID-based `/api/reviews` API. It persists queued execution, event replay, findings, evidence, provider traces, previews and publish decisions.

Run with `mvn spring-boot:run`. The local profile uses H2 for development; Docker Compose uses PostgreSQL/pgvector. A clean baseline requires a fresh database and does not migrate historical task/run records.

The lifecycle is: create an idempotent Review UUID, ingest GitHub data, index and retrieve context, run MiMo, pass Evidence Gate, review previews, and explicitly publish approved comments. SSE uses `Last-Event-ID`; queued and abandoned runs are recovered after restart; only failed runs can be retried.
