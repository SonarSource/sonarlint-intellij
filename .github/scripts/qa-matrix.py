"""Build the PR or weekly UI integration-test matrix for GitHub Actions."""

import argparse
from fnmatch import fnmatchcase
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
IDEA_SUITES = ("OpenInIdeTests", "ConnectedAnalysisTests", "ConfigurationTests", "Standalone")
DOCS_PATHS = ("*.md", "HEADER", "LICENSE", "LICENSE.*", "docs/*",
              ".github/CODEOWNERS", ".github/renovate.json")
HARNESS_PATHS = ("its/*", ".github/*", "gradle/*", "*.gradle.kts", "gradle.properties", "mise.toml")
CLION_PATHS = ("clion/*", "clion-resharper/*", "src/main/resources/META-INF/plugin-clion*.xml")
RIDER_PATHS = ("rider/*", "src/*Rider*", "src/*rider*")


def read_versions():
    versions = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        setting = line.split("#", 1)[0].strip()
        if "=" in setting:
            key, value = setting.split("=", 1)
            versions[key.strip()] = value.strip()
    return versions


def git(*args, check=True):
    return subprocess.run(["git", *args], cwd=ROOT, check=check, text=True,
                          stdout=subprocess.PIPE, stderr=None if check else subprocess.DEVNULL)


def changed_files():
    base = os.environ.get("GITHUB_BASE_REF")
    event = os.environ.get("GITHUB_EVENT_NAME")
    if base:
        # Keep full history for merge-base computation; an unavailable base must fail.
        git("fetch", "--no-tags", "origin", base, check=False)
        return git("diff", "--name-only", f"origin/{base}...HEAD").stdout.splitlines()
    if event == "push":
        before = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()).get("before")
        if (before and before.strip("0")
                and git("cat-file", "-e", f"{before}^{{commit}}", check=False).returncode == 0):
            return git("diff", "--name-only", before, "HEAD").stdout.splitlines()
        # New branches and force-pushes without the previous revision need full coverage.
        return git("ls-files").stdout.splitlines()
    if event == "workflow_dispatch":
        return git("ls-files").stdout.splitlines()
    if git("rev-parse", "--verify", "HEAD~1", check=False).returncode == 0:
        return git("diff", "--name-only", "HEAD~1", "HEAD").stdout.splitlines()
    return git("ls-files").stdout.splitlines()


def matches(path, patterns):
    return any(fnmatchcase(path, pattern) for pattern in patterns)


def job(ide_version, suite):
    return {"ide_version": ide_version, "test_suite": suite}


def idea_suites(ide_version):
    return [job(ide_version, suite) for suite in IDEA_SUITES]


def pr_matrix(versions, paths):
    code_paths = [path for path in paths if not matches(path, DOCS_PATHS)]
    if paths and not code_paths:
        return []

    latest = versions["latestStableIdeVersion"]
    matrix = idea_suites(f"IC-{latest}")
    harness_changed = any(matches(path, HARNESS_PATHS) for path in code_paths)
    if harness_changed or any(matches(path, CLION_PATHS) for path in code_paths):
        matrix.append(job(f"CL-{latest}", "CLion"))
    if harness_changed or any(matches(path, RIDER_PATHS) for path in code_paths):
        rider = versions.get("latestRiderIdeVersion") or latest
        matrix.append(job(f"RD-{rider}", "Rider"))
    return matrix


def weekly_matrix(versions):
    minimum = versions["minSupportedIdeVersion"]
    latest = versions["latestStableIdeVersion"]
    eap = versions["eapIdeVersion"]
    rider_min = versions.get("minRiderIdeVersion") or minimum
    rider_latest = versions.get("latestRiderIdeVersion") or latest
    rider_eap = versions.get("eapRiderIdeVersion") or eap
    pycharm = versions.get("latestPyCharmIdeVersion") or latest

    flavors = [
        (f"CL-{minimum}", "CLion"),
        (f"CL-{latest}", "CLion"),
        (f"CL-{eap}", "CLion"),
        (f"RD-{rider_min}", "Rider"),
        (f"RD-{rider_latest}", "Rider"),
        (f"RD-{rider_eap}", "Rider"),
        (f"PS-{latest}", "PhpStorm"),
        (f"PY-{pycharm}", "PyCharm"),
        (f"GO-{latest}", "GoLand"),
        (f"IU-{minimum}", "PLSQL"),
    ]
    return (idea_suites(f"IC-{minimum}") + idea_suites(f"IU-{eap}")
            + [job(version, suite) for version, suite in flavors])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("pr", "weekly"))
    mode = parser.parse_args().mode
    versions = read_versions()
    matrix = pr_matrix(versions, changed_files()) if mode == "pr" else weekly_matrix(versions)
    matrix_json = json.dumps(matrix, separators=(",", ":"))
    output = f"matrix={matrix_json}\nskip_its={str(not matrix).lower()}\n"
    if output_path := os.environ.get("GITHUB_OUTPUT"):
        with open(output_path, "a", encoding="utf-8") as output_file:
            output_file.write(output)
    print(f"QA matrix ({mode}): {matrix_json}")
    print(f"skip_its={str(not matrix).lower()}")


if __name__ == "__main__":
    main()
