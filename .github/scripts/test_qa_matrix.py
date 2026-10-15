"""Exercise QA selection against real Git histories without network access."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class QaMatrixTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="qa-matrix-")
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name)
        scripts = self.repo / ".github/scripts"
        scripts.mkdir(parents=True)
        shutil.copy2(ROOT / ".github/scripts/qa-matrix.sh", scripts)
        shutil.copy2(ROOT / "gradle.properties", self.repo)
        self.git("init", "-b", "master")
        self.git("config", "user.name", "QA matrix test")
        self.git("config", "user.email", "qa@example.invalid")
        self.git("add", ".")
        self.base = self.commit("README.md")
        self.git("update-ref", "refs/remotes/origin/master", self.base)
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith("GITHUB_") and not key.startswith("GIT_")}
        self.env["GITHUB_OUTPUT"] = str(self.repo / "output")
        # PR selection must use the fetched tracking ref, even offline.
        self.git("remote", "add", "origin", str(self.repo / "missing-remote"))

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True,
                              text=True, capture_output=True).stdout.strip()

    def commit(self, path):
        file = self.repo / path
        file.parent.mkdir(parents=True, exist_ok=True)
        with file.open("a") as content:
            content.write("change\n")
        self.git("add", path)
        self.git("commit", "-m", "Test fixture")
        return self.git("rev-parse", "HEAD")

    def run_matrix(self, mode="pr", **env):
        output = Path(self.env["GITHUB_OUTPUT"])
        output.unlink(missing_ok=True)
        result = subprocess.run(["bash", ".github/scripts/qa-matrix.sh", mode],
                                cwd=self.repo, env=self.env | env,
                                text=True, capture_output=True)
        values = dict(line.split("=", 1) for line in output.read_text().splitlines()) if output.exists() else {}
        return result, values

    def push_event(self, before):
        event = self.repo / "event.json"
        event.write_text(json.dumps({"before": before}))
        return {"GITHUB_EVENT_NAME": "push", "GITHUB_EVENT_PATH": str(event)}

    def assert_suites(self, values, flavors=()):
        self.assertEqual("false", values["skip_its"])
        suites = [job["test_suite"] for job in json.loads(values["matrix"])]
        self.assertCountEqual(["OpenInIdeTests", "ConnectedAnalysisTests", "ConfigurationTests", "Standalone", *flavors], suites)

    def test_pr_path_selection(self):
        for path, flavors in [("README.md", None), ("src/Main.kt", ()),
                              ("rider/src/Main.kt", ("Rider",)), ("clion/src/Main.kt", ("CLion",)),
                              ("its/src/Test.kt", ("CLion", "Rider"))]:
            with self.subTest(path=path):
                self.git("reset", "--hard", self.base)
                self.commit(path)
                result, values = self.run_matrix(GITHUB_BASE_REF="master")
                self.assertEqual(0, result.returncode, result.stderr)
                if flavors is None:
                    self.assertEqual({"matrix": "[]", "skip_its": "true"}, values)
                else:
                    self.assert_suites(values, flavors)

    def test_push_includes_source_changes_before_final_docs_commit(self):
        self.commit("rider/src/Main.kt")
        self.commit("README.md")
        result, values = self.run_matrix(**self.push_event(self.base))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_suites(values, ("Rider",))

    def test_docs_only_push_skips_its(self):
        self.commit("README.md")
        self.commit("docs/setup.md")
        result, values = self.run_matrix(**self.push_event(self.base))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"matrix": "[]", "skip_its": "true"}, values)

    def test_new_branch_runs_all_flavors(self):
        result, values = self.run_matrix(**self.push_event("0" * 40))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_suites(values, ("CLion", "Rider"))

    def test_manual_run_covers_all_flavors_after_docs_commit(self):
        self.commit("README.md")
        result, values = self.run_matrix(GITHUB_EVENT_NAME="workflow_dispatch")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_suites(values, ("CLion", "Rider"))

    def test_unavailable_comparison_fails_without_emitting_matrix(self):
        self.commit("README.md")
        self.git("switch", "--orphan", "unrelated")
        self.git("restore", "--source", self.base, "--staged", "--worktree",
                 ".github/scripts/qa-matrix.sh", "gradle.properties")
        self.commit("OTHER.md")
        for env in [{"GITHUB_BASE_REF": "missing"}, {"GITHUB_BASE_REF": "master"},
                    self.push_event("1" * 40)]:
            with self.subTest(env=env):
                result, values = self.run_matrix(**env)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual({}, values)

    def test_weekly_matrix_keeps_minimum_flavor_and_eap_coverage(self):
        result, values = self.run_matrix("weekly")
        self.assertEqual(0, result.returncode, result.stderr)
        jobs = json.loads(values["matrix"])
        self.assertEqual(18, len(jobs))
        categories = [job["qa_category"] for job in jobs]
        self.assertEqual(4, categories.count("IdeaMin"))
        self.assertEqual(4, categories.count("IdeaUltimateEAP"))
        self.assertIn("RiderEAP", categories)
        self.assertIn("CLionEAP", categories)
        self.assertIn("PLSQL", [job["test_suite"] for job in jobs])


if __name__ == "__main__":
    unittest.main()
