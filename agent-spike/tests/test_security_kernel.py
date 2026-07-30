import unittest

from codereviewx_agent.actions import ActionViolation, safe_path
from codereviewx_agent.fake_provider import FakeProvider
from codereviewx_agent.loop import BoundedReviewerLoop
from codereviewx_agent.tools import RepositoryTools


class SecurityKernelTest(unittest.TestCase):
    def test_blocks_path_escape(self):
        provider = FakeProvider([
            {"action": "get_file_context", "arguments": {"path": "../../.env"}}
        ])
        result = BoundedReviewerLoop(provider, RepositoryTools({})).run()
        self.assertTrue(result.blocked)
        self.assertEqual("ACTION_BLOCKED", result.error_code)

    def test_requires_observed_evidence(self):
        provider = FakeProvider([
            {"action": "finish", "arguments": {"findings": [{
                "issueKey": "X", "title": "claim", "path": "src/A.java",
                "line": 1, "evidencePaths": ["src/A.java"]
            }]}}
        ])
        result = BoundedReviewerLoop(
            provider, RepositoryTools({"src/A.java": "unsafe()"})).run()
        self.assertTrue(result.blocked)

    def test_safe_path_normalization(self):
        self.assertEqual("src/A.java", safe_path("src/A.java"))
        with self.assertRaises(ActionViolation):
            safe_path("/etc/passwd")


if __name__ == "__main__":
    unittest.main()
