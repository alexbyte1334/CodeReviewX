from __future__ import annotations

from dataclasses import dataclass

from .actions import ActionViolation, safe_path


@dataclass
class RepositoryTools:
    files: dict[str, str]

    def search_repository(self, query: object) -> dict:
        if not isinstance(query, str) or not query.strip() or len(query) > 200:
            raise ActionViolation("search query is invalid")
        needle = query.casefold()
        matches = [
            {"path": path, "line": line_no, "excerpt": line[:300]}
            for path, content in sorted(self.files.items())
            for line_no, line in enumerate(content.splitlines(), 1)
            if needle in line.casefold()
        ][:20]
        return {"matches": matches, "truncated": len(matches) == 20}

    def get_file_context(self, path_value: object, start_line: object = 1,
                         end_line: object = 200) -> dict:
        path = safe_path(path_value)
        if path not in self.files:
            raise ActionViolation("path is not present in the immutable snapshot")
        if not isinstance(start_line, int) or not isinstance(end_line, int):
            raise ActionViolation("line bounds must be integers")
        if start_line < 1 or end_line < start_line or end_line - start_line > 400:
            raise ActionViolation("line window is out of bounds")
        lines = self.files[path].splitlines()
        return {
            "path": path,
            "startLine": start_line,
            "endLine": min(end_line, len(lines)),
            "content": "\n".join(lines[start_line - 1:end_line]),
        }
