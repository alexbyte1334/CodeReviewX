# CodeReviewX API

All review resources are addressed by an opaque UUID. Numeric task and run identifiers are internal implementation details.

## Create and observe

`POST /api/reviews` requires an `Idempotency-Key` and accepts `repositoryUrl`, `prNumber` and `inputMode`. It returns `202 Accepted` with a Review UUID. Repeating the same key returns the same UUID.

`GET /api/reviews/{uuid}` returns the durable snapshot. `GET /api/reviews/{uuid}/events` is replayable SSE using monotonic event IDs and `Last-Event-ID`. `POST /api/reviews/{uuid}/retry` is accepted only for failed runs.

## Evidence and publish

Evidence, trace, retrieval and previews are addressed below the UUID:

```text
GET   /api/reviews/{uuid}/issues/{issueKey}/evidence
GET   /api/reviews/{uuid}/trace
GET   /api/reviews/{uuid}/retrieval
GET   /api/reviews/{uuid}/previews
PATCH /api/reviews/{uuid}/previews/selection
POST  /api/reviews/{uuid}/previews/publish
```

Evidence is redacted at the outbound boundary. Publishing requires `confirmed: true`, revalidates the input snapshot and Evidence Gate, and uses a stable marker so repeated approval does not create duplicate comments. The old task and fixed public Demo endpoints are unavailable.
