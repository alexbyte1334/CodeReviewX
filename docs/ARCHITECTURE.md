# CodeReviewX architecture

CodeReviewX is an evidence-constrained Java Agent Workflow for self-hosted developers.

The asynchronous flow is: create a UUID Review, persist a queued event, load GitHub metadata and diff, run commit-scoped RAG indexing/retrieval, execute the configured OpenAI-compatible model through the structured review workflow, pass Evidence Gate, create previews, and publish only after explicit human approval.

State and events are durable, SSE is replayable, and queued or abandoned work is recovered after restart. Provider, GitHub, RAG and Evidence failures become explicit failed states. The system does not deploy a Python worker or dynamic Agent runtime. PostgreSQL/pgvector is the production store; local H2 is only a development profile.
