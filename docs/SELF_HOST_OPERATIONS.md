# Self-host operations

The repository no longer deploys a fixed public Demo. Use Docker Compose or the Railway Docker image as a self-hosted deployment, with secrets stored only in deployment configuration.

Before starting, configure PostgreSQL, GitHub, MiMo, Embedding and Rerank readiness values in `.env`. Use a fresh database for the clean baseline and back up previous data before resetting it.

Validate health/readiness, create a Review UUID, reconnect SSE from `Last-Event-ID`, confirm Evidence appears before Preview approval, verify unconfirmed publish is rejected, and verify repeated confirmed publish does not duplicate a GitHub comment.
