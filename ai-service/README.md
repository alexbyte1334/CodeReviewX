# ai-service

> **Round 01 Status: Placeholder only. No business code is implemented in this round.**

This module will be the Python + FastAPI AI review service for CodeReviewX.

---

## Round 01 Notice

This directory contains a README placeholder only.

No FastAPI source code, `requirements.txt`, service modules, Semgrep integration, LLM invocation logic, Pydantic schemas, or Dockerfile exist in Round 01.

These will be created in a later round after ChatGPT Architect review and approval.

---

## Planned Responsibilities

1. **Fetch GitHub PR diff** — Parse the repository URL, call the GitHub API, and retrieve the PR diff and changed file list.
2. **Parse changed files** — Normalize file change information (path, change type, additions, deletions, patch).
3. **Run Semgrep** — Execute Semgrep static analysis on the changed code and convert findings to the standard ReviewIssue format.
4. **Call mock or real LLM** — In Round 03, use a mock LLM. In a later round, integrate a real LLM provider.
5. **Return structured Review JSON** — Validate and return a `AnalyzeResponse` containing summary, riskLevel, files, and issues.

---

## Planned Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| Python | 3.11 | Runtime |
| FastAPI | 0.100+ | Web framework |
| Pydantic | v2 | Request/response schema and validation |
| httpx | — | GitHub API HTTP client |
| Semgrep | latest | Static code analysis |
| pytest | — | Unit testing |
| uvicorn | — | ASGI server |

---

## Planned Module Boundaries

- **Does:** Fetch GitHub diff, run Semgrep, call mock/real LLM, validate JSON, return structured Review JSON.
- **Does not:** Write directly to MySQL, manage ReviewTask status, expose public business APIs, hold user sessions or authentication state.

---

## Planned Directory Structure (future rounds)

```text
ai-service/
├── app/
│   ├── main.py
│   ├── api/
│   │   └── review_api.py
│   ├── core/
│   │   └── config.py
│   ├── schemas/
│   │   ├── analyze_request.py
│   │   └── analyze_response.py
│   ├── services/
│   │   ├── review_analyzer.py
│   │   ├── github_service.py
│   │   ├── semgrep_service.py
│   │   └── llm_service.py
│   ├── prompts/
│   │   └── review_prompt.py
│   ├── validators/
│   │   └── review_json_validator.py
│   └── utils/
│       └── repo_parser.py
├── tests/
├── requirements.txt
└── Dockerfile
```

This structure will be created in Round 03 after ChatGPT Architect approval.

---

## Mock Mode

In Round 03, `LLM_PROVIDER=mock` will return a fixed Review JSON without calling a real LLM. This allows the full pipeline to be tested end-to-end without any API keys.
