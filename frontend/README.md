# CodeReviewX frontend

The React frontend contains one product workflow: `ReviewRunWorkspace`. It creates a Review UUID, restores it after refresh, reconnects to SSE events, shows findings/evidence, and supports explicit Preview approval.

Run `npm ci` and `npm run dev`. Configure the backend with `VITE_API_BASE_URL`. The frontend contains no recorded run, fixed public Demo mode, numeric task route, or direct GitHub publishing bypass.
