import unittest
import json
import tempfile
from pathlib import Path
from unittest.mock import patch

from codereviewx_agent.eval_runner import (
    Metrics,
    load_fixed_metrics,
    stable_two_additional_findings,
)
from codereviewx_agent.fixed_pipeline_eval import (
    map_findings,
    run_case,
    unified_diff,
)


class FixedPipelineEvalTest(unittest.TestCase):
    @staticmethod
    def metrics(recall):
        return Metrics(
            expected_recall=recall,
            actionable_precision=1.0,
            evidence_pass=1.0,
            illegal_action_rate=0.0,
            malformed_action_rate=0.0,
            median_tool_calls=0,
            median_latency_ms=0,
            p95_latency_ms=0,
            median_tokens=0,
            max_tokens=0,
            security_cases_blocked_or_safe=1.0,
        )

    def test_builds_bounded_added_file_diff(self):
        diff = unified_diff({
            "src/A.java": "first\nsecond",
            "src/B.java": "only",
        })

        self.assertIn("diff --git a/src/A.java b/src/A.java", diff)
        self.assertIn("@@ -0,0 +1,2 @@", diff)
        self.assertIn("+first\n+second", diff)
        self.assertIn("+++ b/src/B.java", diff)

    def test_maps_expected_location_and_preserves_false_positives(self):
        case = {
            "id": "cross-auth",
            "expectedMatchers": {
                "AUTH-1": {
                    "paths": ["src/Auth.java", "src/AuthService.java"],
                    "keywords": ["authorization", "owner"],
                }
            },
        }
        mapped = map_findings(case, [
            {
                "filePath": "src/AuthService.java",
                "title": "Missing ownership check",
            },
            {"filePath": "src/Other.java", "title": "Unrelated issue"},
        ])

        self.assertEqual("AUTH-1", mapped[0]["issueKey"])
        self.assertEqual(
            "FIXED-UNMATCHED-cross-auth-1",
            mapped[1]["issueKey"],
        )

    @patch("codereviewx_agent.fixed_pipeline_eval.request_json")
    def test_runs_real_java_api_contract_and_reads_tokens(self, request_json):
        request_json.side_effect = [
            {
                "data": {
                    "latestRunId": 42,
                    "status": "SUCCESS",
                    "issues": [{
                        "filePath": "src/AuthService.java",
                        "title": "Missing ownership check",
                    }],
                }
            },
            {
                "data": {
                    "status": "SUCCESS",
                    "providerSummary": {"totalTokens": 321},
                }
            },
        ]
        case = {
            "id": "cross-auth",
            "files": {"src/AuthService.java": "repository.delete(id);"},
            "expectedMatchers": {
                "AUTH-1": {
                    "paths": ["src/AuthService.java"],
                    "keywords": ["owner"],
                }
            },
        }

        result = run_case("https://api.example.test", case, 90)

        self.assertEqual([{"issueKey": "AUTH-1"}], result.findings)
        self.assertEqual(321, result.provider_tokens)
        self.assertIsNone(result.error_code)
        self.assertEqual("POST", request_json.call_args_list[0].args[0])
        self.assertEqual(
            "https://api.example.test/api/review-tasks",
            request_json.call_args_list[0].args[1],
        )
        self.assertEqual("MANUAL_DIFF", request_json.call_args_list[0].args[2]["reviewMode"])
        self.assertEqual(
            "https://api.example.test/api/review-runs/42",
            request_json.call_args_list[1].args[1],
        )

    def test_incomplete_fixed_report_cannot_be_used_as_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "fixed.json"
            report.write_text(json.dumps({
                "datasetVersion": "v1",
                "pipeline": "fixed-java",
                "complete": False,
                "aggregate": {},
            }))

            metrics, raw = load_fixed_metrics(report)

        self.assertIsNone(metrics)
        self.assertFalse(raw["complete"])

    def test_requires_two_additional_findings_in_every_repetition(self):
        cases = [
            {"id": "one", "expected": ["A", "B"]},
            {"id": "two", "expected": ["C", "D"]},
        ]
        fixed_report = {
            "runs": [
                [
                    {"caseId": "one", "findings": ["A"]},
                    {"caseId": "two", "findings": []},
                ],
                [
                    {"caseId": "one", "findings": ["A"]},
                    {"caseId": "two", "findings": []},
                ],
            ]
        }

        self.assertTrue(stable_two_additional_findings(
            cases,
            [self.metrics(0.75), self.metrics(0.75)],
            fixed_report,
        ))
        self.assertFalse(stable_two_additional_findings(
            cases,
            [self.metrics(0.75), self.metrics(0.5)],
            fixed_report,
        ))


if __name__ == "__main__":
    unittest.main()
