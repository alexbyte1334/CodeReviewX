from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Any

ALLOWED_ACTIONS = {"search_repository", "get_file_context", "finish"}


class ActionViolation(ValueError):
    """An invalid, unsafe, or over-budget model action."""


@dataclass(frozen=True)
class Action:
    name: str
    arguments: dict[str, Any]


def parse_action(value: Any) -> Action:
    if not isinstance(value, dict) or set(value) != {"action", "arguments"}:
        raise ActionViolation("action must contain exactly action and arguments")
    name = value["action"]
    arguments = value["arguments"]
    if name not in ALLOWED_ACTIONS or not isinstance(arguments, dict):
        raise ActionViolation("action is not allowlisted")
    return Action(name, arguments)


def safe_path(value: Any) -> str:
    if not isinstance(value, str) or not value or "\x00" in value or "\\" in value:
        raise ActionViolation("path is invalid")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts:
        raise ActionViolation("path escapes the repository snapshot")
    normalized = str(path)
    if normalized == "." or normalized.startswith(".git/"):
        raise ActionViolation("path is outside the reviewable snapshot")
    return normalized


def validate_findings(raw: Any, observed_paths: set[str]) -> list[dict[str, Any]]:
    if not isinstance(raw, list):
        raise ActionViolation("finish.findings must be a list")
    findings: list[dict[str, Any]] = []
    required = {"issueKey", "title", "path", "line", "evidencePaths"}
    for item in raw:
        if not isinstance(item, dict) or not required.issubset(item):
            raise ActionViolation("finding schema is invalid")
        path = safe_path(item["path"])
        evidence = item["evidencePaths"]
        if not isinstance(item["line"], int) or item["line"] <= 0:
            raise ActionViolation("finding line must be positive")
        if not isinstance(evidence, list) or not evidence:
            raise ActionViolation("finding has no evidence")
        normalized_evidence = [safe_path(entry) for entry in evidence]
        if path not in observed_paths or any(entry not in observed_paths for entry in normalized_evidence):
            raise ActionViolation("finding cites unobserved evidence")
        findings.append({**item, "path": path, "evidencePaths": normalized_evidence})
    return findings
