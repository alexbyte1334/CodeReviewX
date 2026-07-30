import unittest
from unittest.mock import patch

from codereviewx_agent.actions import ActionViolation, safe_path
from codereviewx_agent.eval_runner import score
from codereviewx_agent.fake_provider import FakeProvider
from codereviewx_agent.loop import BoundedReviewerLoop
from codereviewx_agent.mimo_provider import MiMoProvider, MiMoSettings
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

    def test_extracts_json_after_reasoning_text(self):
        value = MiMoProvider._parse_json_object(
            'reasoning omitted\n{"action":"finish","arguments":{"findings":[]}}')
        self.assertEqual("finish", value["action"])

    @patch("codereviewx_agent.mimo_provider.urlopen")
    def test_malformed_provider_response_is_blocked(self, mocked_urlopen):
        response = mocked_urlopen.return_value.__enter__.return_value
        response.read.return_value = (
            b'{"choices":[{"message":{"content":"not-json"}}],'
            b'"usage":{"total_tokens":12}}')
        provider = MiMoProvider(
            {"id": "bad", "files": {"src/A.java": "safe();"}},
            MiMoSettings(api_key="test-key"),
        )
        result = BoundedReviewerLoop(
            provider, RepositoryTools({"src/A.java": "safe();"})).run()
        self.assertTrue(result.blocked)
        self.assertEqual(1, result.provider_malformed_actions)
        self.assertEqual(12, result.provider_tokens)

    @patch("codereviewx_agent.mimo_provider.urlopen")
    def test_provider_http_failure_is_sanitized(self, mocked_urlopen):
        from urllib.error import HTTPError

        mocked_urlopen.side_effect = HTTPError(
            "https://provider.invalid", 402, "payment", {}, None)
        provider = MiMoProvider(
            {"id": "billing", "files": {"src/A.java": "safe();"}},
            MiMoSettings(api_key="test-key"),
        )
        result = BoundedReviewerLoop(
            provider, RepositoryTools({"src/A.java": "safe();"})).run()
        self.assertTrue(result.blocked)
        self.assertEqual("PROVIDER_ERROR", result.error_code)
        self.assertEqual(
            "MiMo API returned HTTP 402", result.observations[-1]["error"])

    def test_security_score_accepts_safe_missing_evidence(self):
        result = BoundedReviewerLoop(
            FakeProvider([{"action": "finish", "arguments": {"findings": []}}]),
            RepositoryTools({"src/A.java": "safe();"}),
        ).run()
        metrics = score([{
            "id": "missing-evidence",
            "kind": "missing_evidence",
            "expected": [],
        }], [result])
        self.assertEqual(1.0, metrics.security_cases_blocked_or_safe)


if __name__ == "__main__":
    unittest.main()
