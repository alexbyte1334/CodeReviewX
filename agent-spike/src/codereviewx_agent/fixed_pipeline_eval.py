from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

from .eval_runner import score


@dataclass
class FixedResult:
    findings: list[dict]
    blocked: bool
    illegal_actions: int
    turns: int
    tool_calls: int
    duration_ms: int
    provider_requests: int
    provider_tokens: int
    provider_malformed_actions: int
    error_code: str | None


def unified_diff(files: dict[str, str]) -> str:
    sections = []
    for path, content in files.items():
        lines = content.splitlines() or [""]
        added = "\n".join("+" + line for line in lines)
        sections.append(
            f"diff --git a/{path} b/{path}\n"
            "new file mode 100644\n"
            "--- /dev/null\n"
            f"+++ b/{path}\n"
            f"@@ -0,0 +1,{len(lines)} @@\n{added}\n"
        )
    return "".join(sections)


def request_json(method: str, url: str, payload: dict | None,
                 timeout_seconds: int) -> dict:
    body = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            return json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        code = f"HTTP_{error.code}"
        try:
            parsed = json.loads(error.read().decode())
            message = str(parsed.get("message", ""))
            if ":" in message:
                code = message.split(":", 1)[0]
        except (ValueError, UnicodeDecodeError):
            pass
        raise RuntimeError(code) from None
    except (urllib.error.URLError, TimeoutError) as error:
        reason = type(getattr(error, "reason", error)).__name__.upper()
        raise RuntimeError(f"BACKEND_{reason}") from None


def map_findings(case: dict, issues: list[dict]) -> list[dict]:
    expected_matchers = case.get("expectedMatchers", {})
    remaining = list(issues)
    keys: list[str] = []
    for expected_key, matcher in expected_matchers.items():
        paths = set(matcher.get("paths", []))
        keywords = [word.lower() for word in matcher.get("keywords", [])]
        match = next(
            (
                issue for issue in remaining
                if issue.get("filePath") in paths
                and any(
                    keyword in " ".join(str(issue.get(field) or "") for field in (
                        "title", "description", "recommendation"
                    )).lower()
                    for keyword in keywords
                )
            ),
            None,
        )
        if match is not None:
            keys.append(expected_key)
            remaining.remove(match)
    keys.extend(
        f"FIXED-UNMATCHED-{case['id']}-{index}"
        for index, _ in enumerate(remaining, start=1)
    )
    return [{"issueKey": key} for key in keys]


def run_case(base_url: str, case: dict, timeout_seconds: int) -> FixedResult:
    started = time.monotonic()
    try:
        created = request_json(
            "POST",
            f"{base_url}/api/review-tasks",
            {
                "repoUrl": "https://github.com/alexbyte1334/CodeReviewX-EvalCorpus",
                "prNumber": 1,
                "reviewMode": "MANUAL_DIFF",
                "provider": "mimo",
                "diffText": unified_diff(case["files"]),
            },
            timeout_seconds,
        ).get("data", {})
        run_id = created.get("latestRunId")
        if not run_id:
            return failed_result(started, created.get("errorCode") or "FIXED_RUN_MISSING")
        run = request_json(
            "GET",
            f"{base_url}/api/review-runs/{run_id}",
            None,
            timeout_seconds,
        ).get("data", {})
        if created.get("status") != "SUCCESS" or run.get("status") != "SUCCESS":
            return failed_result(
                started,
                created.get("errorCode") or run.get("errorCode") or "FIXED_RUN_FAILED",
            )
        provider = run.get("providerSummary") or {}
        return FixedResult(
            findings=map_findings(case, created.get("issues") or []),
            blocked=False,
            illegal_actions=0,
            turns=1,
            tool_calls=3,
            duration_ms=elapsed_ms(started),
            provider_requests=3,
            provider_tokens=int(provider.get("totalTokens") or 0),
            provider_malformed_actions=0,
            error_code=None,
        )
    except RuntimeError as error:
        return failed_result(started, str(error))


def failed_result(started: float, code: str) -> FixedResult:
    return FixedResult(
        findings=[],
        blocked=True,
        illegal_actions=0,
        turns=1,
        tool_calls=0,
        duration_ms=elapsed_ms(started),
        provider_requests=0,
        provider_tokens=0,
        provider_malformed_actions=0,
        error_code=code[:120],
    )


def elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


def evaluate(root: Path, base_url: str, repetitions: int,
             timeout_seconds: int) -> dict:
    cases = json.loads((root / "evals" / "cases.json").read_text())
    repeated = [
        [run_case(base_url, case, timeout_seconds) for case in cases]
        for _ in range(repetitions)
    ]
    combined = score(
        [case for _ in repeated for case in cases],
        [result for run in repeated for result in run],
    )
    return {
        "datasetVersion": "v1",
        "pipeline": "fixed-java",
        "caseCount": len(cases),
        "repetitions": repetitions,
        "generatedAt": datetime.now(UTC).isoformat(),
        "aggregate": asdict(combined),
        "runs": [
            [
                {
                    "caseId": case["id"],
                    "findings": [item["issueKey"] for item in result.findings],
                    "durationMs": result.duration_ms,
                    "tokens": result.provider_tokens,
                    "errorCode": result.error_code,
                }
                for case, result in zip(cases, results, strict=True)
            ]
            for results in repeated
        ],
        "complete": all(
            result.error_code is None for results in repeated for result in results
        ),
        "note": (
            "This report calls the real synchronous Java ReviewTask pipeline "
            "with the same versioned corpus. It never invokes GitHub publishing."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.repetitions < 1:
        raise SystemExit("--repetitions must be positive")
    root = Path(__file__).resolve().parents[2]
    output = args.output or root / ".runtime-evals" / "fixed-java-live-latest.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    report = evaluate(
        root, args.base_url.rstrip("/"), args.repetitions, args.timeout_seconds
    )
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "output": str(output),
        "complete": report["complete"],
        "aggregate": report["aggregate"],
    }, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
