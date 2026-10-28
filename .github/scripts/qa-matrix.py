"""Build the PR or weekly UI integration-test matrix for GitHub Actions."""

import argparse
from fnmatch import fnmatchcase
from functools import cache
import json
import os
from pathlib import Path
import subprocess
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parents[2]
RELEASES_URL = os.environ.get(
    "JETBRAINS_RELEASES_URL",
    "https://data.services.jetbrains.com/products/releases?code={codes}&latest=true&type={kind}")
# Matrix IDE code -> JetBrains product code and the tarball prefix download-ides.sh expects.
PRODUCTS = {"IC": ("IIU", "idea-"), "IU": ("IIU", "idea-"), "CL": ("CL", "CLion-"),
            "RD": ("RD", "JetBrains.Rider-"), "PY": ("PCP", "pycharm-"),
            "PS": ("PS", "PhpStorm-"), "GO": ("GO", "goland-")}
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


@cache
def releases(kind):
    codes = ",".join(sorted({code for code, _ in PRODUCTS.values()}))
    with urlopen(RELEASES_URL.format(codes=codes, kind=kind), timeout=30) as response:
        return json.load(response)


def build_number(release):
    return tuple(int(part) for part in release["build"].split("."))


def resolve(ide, kind):
    """Newest JetBrains `kind` ("release" or "eap") of `ide`, named as download-ides.sh expects."""
    code, prefix = PRODUCTS[ide]
    newest = releases(kind)[code][0]
    if kind == "eap" and build_number(newest) <= build_number(releases("release")[code][0]):
        # Between release cycles the newest EAP predates the current stable release.
        print(f"::notice::No {code} EAP newer than the current release; skipping {ide} EAP jobs")
        return None
    tarball = newest["downloads"]["linux"]["link"].rsplit("/", 1)[-1]
    if not (tarball.startswith(prefix) and tarball.endswith(".tar.gz")):
        raise ValueError(f"Unexpected {code} {kind} download name: {tarball}")
    return f"{ide}-{tarball[len(prefix):-len('.tar.gz')]}"


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


def pr_matrix(paths):
    code_paths = [path for path in paths if not matches(path, DOCS_PATHS)]
    if paths and not code_paths:
        return []

    matrix = idea_suites(resolve("IC", "release"))
    harness_changed = any(matches(path, HARNESS_PATHS) for path in code_paths)
    if harness_changed or any(matches(path, CLION_PATHS) for path in code_paths):
        matrix.append(job(resolve("CL", "release"), "CLion"))
    if harness_changed or any(matches(path, RIDER_PATHS) for path in code_paths):
        matrix.append(job(resolve("RD", "release"), "Rider"))
    return matrix


def weekly_matrix(versions):
    minimum = versions["minSupportedIdeVersion"]
    rider_min = versions.get("minRiderIdeVersion") or minimum
    idea_eap = resolve("IU", "eap")

    flavors = [
        (f"CL-{minimum}", "CLion"),
        (resolve("CL", "release"), "CLion"),
        (resolve("CL", "eap"), "CLion"),
        (f"RD-{rider_min}", "Rider"),
        (resolve("RD", "release"), "Rider"),
        (resolve("RD", "eap"), "Rider"),
        (resolve("PS", "release"), "PhpStorm"),
        (resolve("PY", "release"), "PyCharm"),
        (resolve("GO", "release"), "GoLand"),
        (f"IU-{minimum}", "PLSQL"),
    ]
    return (idea_suites(f"IC-{minimum}") + (idea_suites(idea_eap) if idea_eap else [])
            + [job(version, suite) for version, suite in flavors if version])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("pr", "weekly"))
    mode = parser.parse_args().mode
    matrix = pr_matrix(changed_files()) if mode == "pr" else weekly_matrix(read_versions())
    matrix_json = json.dumps(matrix, separators=(",", ":"))
    output = f"matrix={matrix_json}\nskip_its={str(not matrix).lower()}\n"
    if output_path := os.environ.get("GITHUB_OUTPUT"):
        with open(output_path, "a", encoding="utf-8") as output_file:
            output_file.write(output)
    print(f"QA matrix ({mode}): {matrix_json}")
    print(f"skip_its={str(not matrix).lower()}")


if __name__ == "__main__":
    main()
