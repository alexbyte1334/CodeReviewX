# Trusted Demo operations

The public frontend is GitHub Pages. The live API is one Spring Boot Railway
service backed by one PostgreSQL/pgvector service. No Python Worker is deployed.

## Provisioning

1. Create `alexbyte1334/CodeReviewX-DemoTarget` as a public repository.
2. Open one stable SQL-injection PR and record its PR number, base SHA, and head SHA.
3. Pre-index that exact 1024-dimensional head SHA.
4. Deploy `backend-java/Dockerfile` through `railway.json` with the
   `postgres` Spring profile.
5. Configure `DATABASE_URL`, RAG embedding/rerank variables, MiMo role keys,
   and the Demo variables from `.env.example`.
6. Use a fine-grained GitHub token limited to DemoTarget: Contents read and
   Pull requests read/write, with an expiry.
7. Set the GitHub repository variable `DEMO_API_BASE_URL` to the Railway HTTPS
   origin and redeploy Pages.

## Required smoke checks

- Incognito `POST /api/demo-runs` succeeds without a visitor key.
- Refreshing a URL containing `runId` restores the snapshot.
- SSE events appear within two seconds and reconnect from the last event ID.
- Unknown scenarios and malformed UUIDs fail before model use.
- Anonymous legacy and admin publish calls return 401/403.
- Repeating owner publish for the same run does not duplicate a comment.
- `/actuator/health/liveness` stays process-only; `/api/health` reports each dependency.
- The Pages bundle contains no local API address, admin token, or provider key.

Demo runs/events are deleted after seven days; IP hash rate buckets after 24
hours. Logs may include only public run UUID, step, state, duration, counts, and
safe error code.
