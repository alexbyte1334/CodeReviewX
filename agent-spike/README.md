# Dynamic Agent evaluation spike

This directory is deliberately not a deployed service. It compares a bounded
LangGraph tool loop with the current fixed Java pipeline and has no GitHub
publish tool.

```bash
cd agent-spike
python3.12 -m venv .venv
.venv/bin/pip install -e .
.venv/bin/python -m codereviewx_agent.eval_runner
```

The loop permits only `search_repository`, `get_file_context`, and `finish`,
with at most four model turns and six tool calls. Repository paths are
normalized and confined to the supplied snapshot. Findings without observed
path/line evidence are rejected.

The deterministic Fake Provider and 12-case fixture run without model keys.
The MiMo adapter implements the same `next_action` protocol and has no
publishing capability. Live reports are written to the ignored
`.runtime-evals` directory and contain aggregate/per-case metrics, never API
keys or raw model responses:

```bash
MIMO_EXECUTOR_API_KEY=... \
  .venv/bin/python -m codereviewx_agent.eval_runner --provider mimo
```

A live dynamic result is not sufficient for productization: the decision
remains blocked until the current Java pipeline is executed against the same
versioned corpus, including its measured token baseline. Start the Spring
service with MiMo configured, then run:

```bash
.venv/bin/python -m codereviewx_agent.fixed_pipeline_eval \
  --base-url http://localhost:8080 \
  --repetitions 3

.venv/bin/python -m codereviewx_agent.eval_runner \
  --provider mimo \
  --repetitions 3 \
  --fixed-report .runtime-evals/fixed-java-live-latest.json
```

The fixed runner generates bounded synthetic diffs from the same 12 cases and
calls the real synchronous Java `ReviewTask` pipeline. It reads only bounded
issues, run status, latency, and provider token totals; it never invokes a
GitHub publishing endpoint. An incomplete or failed fixed report cannot unlock
the LangGraph productization decision.
