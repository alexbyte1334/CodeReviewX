import type { DemoCreateResponse, DemoSnapshot } from '../types/demoRun';

export const DEMO_API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const SCENARIO_ID = 'sql-injection-pr';

async function requireJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json() as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // The stable HTTP status remains useful when an intermediary returns HTML.
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export async function loadRecordedRun(): Promise<DemoSnapshot> {
  const base = import.meta.env.BASE_URL || '/';
  const response = await fetch(`${base}recorded-run.json`, { cache: 'no-store' });
  const snapshot = await requireJson<DemoSnapshot>(response);
  return { ...snapshot, mode: 'REPLAY' };
}

export async function createDemoRun(): Promise<DemoCreateResponse> {
  const response = await fetch(`${DEMO_API_BASE}/api/demo-runs`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
    },
    body: JSON.stringify({ scenarioId: SCENARIO_ID }),
  });
  return requireJson<DemoCreateResponse>(response);
}

export async function getDemoSnapshot(runId: string): Promise<DemoSnapshot> {
  const response = await fetch(`${DEMO_API_BASE}/api/demo-runs/${encodeURIComponent(runId)}`);
  return requireJson<DemoSnapshot>(response);
}

export async function decideDemoRun(
  runId: string,
  decision: 'APPROVE_PREVIEW' | 'REJECT',
  selectedPreviewIds: number[],
): Promise<DemoSnapshot> {
  const response = await fetch(
    `${DEMO_API_BASE}/api/demo-runs/${encodeURIComponent(runId)}/decision`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, selectedPreviewIds }),
    },
  );
  return requireJson<DemoSnapshot>(response);
}
