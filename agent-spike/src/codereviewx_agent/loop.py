from __future__ import annotations

from dataclasses import dataclass, field
from time import perf_counter
from typing import Any, Protocol

from .actions import ActionViolation, parse_action, safe_path, validate_findings
from .tools import RepositoryTools


class Provider(Protocol):
    def next_action(self, observations: list[dict[str, Any]]) -> dict[str, Any]: ...


@dataclass
class LoopResult:
    findings: list[dict[str, Any]] = field(default_factory=list)
    observations: list[dict[str, Any]] = field(default_factory=list)
    tool_calls: int = 0
    turns: int = 0
    illegal_actions: int = 0
    blocked: bool = False
    error_code: str | None = None
    duration_ms: int = 0


class BoundedReviewerLoop:
    def __init__(self, provider: Provider, tools: RepositoryTools,
                 max_turns: int = 4, max_tool_calls: int = 6):
        self.provider = provider
        self.tools = tools
        self.max_turns = max_turns
        self.max_tool_calls = max_tool_calls

    def run(self) -> LoopResult:
        started = perf_counter()
        result = LoopResult()
        observed_paths: set[str] = set()
        try:
            for turn in range(1, self.max_turns + 1):
                result.turns = turn
                action = parse_action(self.provider.next_action(list(result.observations)))
                if action.name == "finish":
                    result.findings = validate_findings(
                        action.arguments.get("findings"), observed_paths)
                    return self._finish(result, started)
                if result.tool_calls >= self.max_tool_calls:
                    raise ActionViolation("tool call budget exceeded")
                if action.name == "search_repository":
                    output = self.tools.search_repository(action.arguments.get("query"))
                    observed_paths.update(row["path"] for row in output["matches"])
                else:
                    path = safe_path(action.arguments.get("path"))
                    output = self.tools.get_file_context(
                        path, action.arguments.get("startLine", 1),
                        action.arguments.get("endLine", 200))
                    observed_paths.add(path)
                result.tool_calls += 1
                result.observations.append({
                    "action": action.name,
                    "arguments": action.arguments,
                    "observation": output,
                })
            raise ActionViolation("model turn budget exceeded")
        except ActionViolation as error:
            result.illegal_actions += 1
            result.blocked = True
            result.error_code = "ACTION_BLOCKED"
            result.observations.append({"error": str(error)})
            return self._finish(result, started)

    @staticmethod
    def _finish(result: LoopResult, started: float) -> LoopResult:
        result.duration_ms = int((perf_counter() - started) * 1000)
        return result
