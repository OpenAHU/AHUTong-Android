import subprocess
import unittest
from unittest.mock import patch

from ci_select_run import should_run


REPOSITORY = "OpenAHU/AHUTong-Android"
COMMIT = "a" * 40
OTHER_COMMIT = "b" * 40


class SelectRunTest(unittest.TestCase):
    @patch("ci_select_run.subprocess.run")
    @patch("ci_select_run.subprocess.check_output", return_value="0\n")
    def test_only_master_runs_when_both_branches_share_a_commit(self, pulls, ref):
        ref.return_value = subprocess.CompletedProcess([], 0, f"{COMMIT}\n")
        self.assertTrue(should_run("push", REPOSITORY, "master", COMMIT))
        ref.assert_not_called()
        self.assertFalse(should_run("push", REPOSITORY, "develop", COMMIT))
        self.assertIn(f"repos/{REPOSITORY}/git/ref/heads/master", ref.call_args.args[0])
        pulls.assert_called_once()

    @patch("ci_select_run.subprocess.run")
    @patch("ci_select_run.subprocess.check_output", return_value="0\n")
    def test_develop_still_runs_for_its_own_commit(self, pulls, ref):
        ref.return_value = subprocess.CompletedProcess([], 0, f"{OTHER_COMMIT}\n")
        self.assertTrue(should_run("push", REPOSITORY, "develop", COMMIT))
        self.assertIn("head=OpenAHU:develop", pulls.call_args.args[0])

    @patch("ci_select_run.subprocess.run")
    @patch("ci_select_run.subprocess.check_output", return_value="0\n")
    def test_develop_runs_when_master_cannot_be_read(self, pulls, ref):
        ref.return_value = subprocess.CompletedProcess([], 1, "")
        self.assertTrue(should_run("push", REPOSITORY, "develop", COMMIT))
        pulls.assert_called_once()

    @patch("ci_select_run.subprocess.run")
    @patch("ci_select_run.subprocess.check_output", return_value="1\n")
    def test_push_with_open_pr_prefers_the_merge_commit(self, pulls, ref):
        self.assertFalse(should_run("push", REPOSITORY, "feature", COMMIT))
        self.assertIn("head=OpenAHU:feature", pulls.call_args.args[0])
        ref.assert_not_called()

    @patch("ci_select_run.subprocess.run")
    @patch("ci_select_run.subprocess.check_output")
    def test_pr_and_manual_events_bypass_push_selection(self, pulls, ref):
        for event in ("pull_request", "workflow_dispatch"):
            with self.subTest(event=event):
                self.assertTrue(should_run(event, REPOSITORY, "develop", COMMIT))
        pulls.assert_not_called()
        ref.assert_not_called()
