"""Exercise QA selection against real Git histories without network access."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
RELEASES = {"IIU": ("262.10968.63", "idea/idea-2026.2.3"),
            "CL": ("262.11879.74", "cpp/CLion-2026.2.4"),
            "RD": ("262.10968.170", "rider/JetBrains.Rider-2026.2.3.1"),
            "PCP": ("262.10968.92", "python/pycharm-2026.2.3"),
            "PS": ("262.10968.76", "webide/PhpStorm-2026.2.3"),
            "GO": ("262.10968.67", "go/goland-2026.2.3")}
EAPS = {"IIU": ("263.7307.44", "idea/idea-263.7307.44"),
        "CL": ("263.7307.35", "cpp/CLion-263.7307.35"),
        "RD": ("263.7307.48", "rider/JetBrains.Rider-2026.3-EAP8-263.7307.48.Checked"),
        "PCP": ("263.7307.50", "python/pycharm-263.7307.50"),
        "PS": ("263.7307.51", "webide/PhpStorm-263.7307.51"),
        "GO": ("263.7307.43", "go/goland-263.7307.43")}


def releases_json(products):
    """Shape of data.services.jetbrains.com/products/releases?latest=true, reduced to used fields."""
    return json.dumps({code: [{"build": build, "downloads": {"linux": {
        "link": f"https://download.jetbrains.com/{path}.tar.gz"}}}]
        for code, (build, path) in products.items()})


class QaMatrixTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="qa-matrix-")
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name)
        scripts = self.repo / ".github/scripts"
        scripts.mkdir(parents=True)
        shutil.copy2(ROOT / ".github/scripts/qa-matrix.py", scripts)
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
        self.write_releases(RELEASES, EAPS)
        self.env["JETBRAINS_RELEASES_URL"] = self.repo.as_uri() + "/releases-{kind}.json"
        # PR selection must use the fetched tracking ref, even offline.
        self.git("remote", "add", "origin", str(self.repo / "missing-remote"))

    def write_releases(self, releases, eaps):
        (self.repo / "releases-release.json").write_text(releases_json(releases))
        (self.repo / "releases-eap.json").write_text(releases_json(eaps))

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
        result = subprocess.run([sys.executable, ".github/scripts/qa-matrix.py", mode],
                                cwd=self.repo, env=self.env | env,
                                text=True, capture_output=True)
        values = {}
        if output.exists():
            for line in output.read_text().splitlines():
                key, value = line.split("=", 1)
                values[key] = value
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
                              ("clion-resharper/src/Main.kt", ("CLion",)),
                              ("src/main/resources/META-INF/plugin-clion.xml", ("CLion",)),
                              ("src/main/resources/META-INF/plugin-rider.xml", ("Rider",)),
                              ("src/main/RiderSupport.kt", ("Rider",)),
                              ("src/main/rider/Support.kt", ("Rider",)),
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

    def test_documentation_does_not_add_flavors_to_source_changes(self):
        for path in ["rider/README.md", "clion/README.md", "its/README.md",
                     ".github/ISSUE_TEMPLATE/bug_report.md", ".github/CODEOWNERS",
                     "docs/rider/setup.md"]:
            with self.subTest(path=path):
                self.git("reset", "--hard", self.base)
                self.commit("src/Main.kt")
                self.commit(path)
                result, values = self.run_matrix(GITHUB_BASE_REF="master")
                self.assertEqual(0, result.returncode, result.stderr)
                self.assert_suites(values)

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
                 ".github/scripts/qa-matrix.py", "gradle.properties")
        self.commit("OTHER.md")
        for env in [{"GITHUB_BASE_REF": "missing"}, {"GITHUB_BASE_REF": "master"}]:
            with self.subTest(env=env):
                result, values = self.run_matrix(**env)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual({}, values)

    def test_force_push_missing_before_runs_all_flavors(self):
        result, values = self.run_matrix(**self.push_event("1" * 40))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_suites(values, ("CLion", "Rider"))

    def test_empty_pr_diff_runs_core_suites(self):
        result, values = self.run_matrix(GITHUB_BASE_REF="master")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assert_suites(values)

    def test_invalid_mode_fails_without_emitting_matrix(self):
        result, values = self.run_matrix("unknown")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({}, values)

    def test_malformed_push_event_fails_without_emitting_matrix(self):
        env = self.push_event(self.base)
        Path(env["GITHUB_EVENT_PATH"]).write_text("{")
        result, values = self.run_matrix(**env)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({}, values)

    def test_pr_uses_latest_release_of_each_product(self):
        self.commit("its/src/Test.kt")
        result, values = self.run_matrix(GITHUB_BASE_REF="master")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertCountEqual(["IC-2026.2.3", "CL-2026.2.4", "RD-2026.2.3.1"],
                              {job["ide_version"] for job in json.loads(values["matrix"])})

    def test_docs_only_pr_does_not_query_releases(self):
        self.commit("README.md")
        result, values = self.run_matrix(GITHUB_BASE_REF="master",
                                         JETBRAINS_RELEASES_URL=(self.repo / "missing").as_uri())
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"matrix": "[]", "skip_its": "true"}, values)

    def test_weekly_versions_respect_minimum_overrides(self):
        (self.repo / "gradle.properties").write_text("""
minSupportedIdeVersion=2024.2 # inline comments are allowed
minRiderIdeVersion=2024.2.7
""")
        result, values = self.run_matrix("weekly")
        self.assertEqual(0, result.returncode, result.stderr)
        jobs = json.loads(values["matrix"])
        self.assertEqual(["RD-2024.2.7", "RD-2026.2.3.1", "RD-2026.3-EAP8-263.7307.48.Checked"],
                         [job["ide_version"] for job in jobs if job["test_suite"] == "Rider"])
        self.assertEqual(["CL-2024.2", "CL-2026.2.4", "CL-263.7307.35"],
                         [job["ide_version"] for job in jobs if job["test_suite"] == "CLion"])
        self.assertIn({"ide_version": "PY-2026.2.3", "test_suite": "PyCharm"}, jobs)

    def test_blank_minimum_override_uses_shared_minimum(self):
        (self.repo / "gradle.properties").write_text("minSupportedIdeVersion=2024.2\nminRiderIdeVersion=\n")
        result, values = self.run_matrix("weekly")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn({"ide_version": "RD-2024.2", "test_suite": "Rider"}, json.loads(values["matrix"]))

    def test_weekly_matrix_keeps_minimum_flavor_and_eap_coverage(self):
        result, values = self.run_matrix("weekly")
        self.assertEqual(0, result.returncode, result.stderr)
        jobs = json.loads(values["matrix"])
        self.assertEqual(18, len(jobs))
        self.assertEqual(18, len({(job["ide_version"], job["test_suite"]) for job in jobs}))
        versions = [job["ide_version"] for job in jobs]
        self.assertEqual(4, sum(version.startswith("IC-") for version in versions))
        self.assertEqual(4, versions.count("IU-263.7307.44"))
        self.assertIn("PLSQL", [job["test_suite"] for job in jobs])

    def test_weekly_skips_eap_older_than_release(self):
        self.write_releases(RELEASES, {code: ("262.1.1", path) for code, (_, path) in EAPS.items()})
        result, values = self.run_matrix("weekly")
        self.assertEqual(0, result.returncode, result.stderr)
        versions = [job["ide_version"] for job in json.loads(values["matrix"])]
        self.assertEqual(12, len(versions))
        self.assertFalse([version for version in versions if "263." in version])

    def test_unusable_releases_fail_without_emitting_matrix(self):
        renamed = RELEASES | {"CL": ("262.11879.74", "cpp/clion-2026.2.4")}
        for name, releases in [("unreachable", None), ("unexpected tarball name", renamed),
                               ("missing product", {"IIU": RELEASES["IIU"]})]:
            with self.subTest(name):
                env = {}
                if releases is None:
                    env["JETBRAINS_RELEASES_URL"] = (self.repo / "missing").as_uri()
                else:
                    self.write_releases(releases, EAPS)
                result, values = self.run_matrix("weekly", **env)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual({}, values)


if __name__ == "__main__":
    unittest.main()
