#!/bin/bash
# Build a GitHub Actions matrix for UI integration tests.
#
# Usage:
#   qa-matrix.sh pr
#   qa-matrix.sh weekly
#
# Reads IDE versions from gradle.properties (see minSupportedIdeVersion,
# latestStableIdeVersion, eapIdeVersion, and optional per-product overrides).
#
# Writes GITHUB_OUTPUT keys:
#   matrix     JSON array of {ide_version, test_suite}
#   skip_its   true when a PR/push only touches documentation
#
# Exit codes:
#   0: Success
#   1: Invalid arguments

set -euo pipefail

: "${GITHUB_OUTPUT:=/dev/null}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PROPS="${ROOT}/gradle.properties"

MODE="${1:-}"
if [[ -z "${MODE}" ]]; then
  echo "Usage: $0 pr | weekly" >&2
  exit 1
fi

read_gradle_prop() {
  sed -n "s/^$1=//p" "${PROPS}" | cut -d'#' -f1 | xargs
}

prop_or() {
  local value
  value="$(read_gradle_prop "$1")"
  echo "${value:-$2}"
}

changed_files() {
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    # Do not pass --depth=1: setup-qa-matrix already checked out with fetch-depth 0.
    # A shallow fetch would write .git/shallow and can break merge-base computation.
    git fetch --no-tags origin "${GITHUB_BASE_REF}" >/dev/null 2>&1 || true
    git diff --name-only "origin/${GITHUB_BASE_REF}...HEAD"
    return
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "push" ]]; then
    local before
    before="$(jq -r '.before // empty' "${GITHUB_EVENT_PATH}")" || return
    if [[ -n "${before}" && ! "${before}" =~ ^0+$ ]] \
        && git cat-file -e "${before}^{commit}" 2>/dev/null; then
      git diff --name-only "${before}" HEAD
    else
      # New branch or force-push: the previous revision is not in this clone.
      git ls-files
    fi
    return
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "workflow_dispatch" ]]; then
    # A manual run requests coverage regardless of the last commit's paths.
    git ls-files
    return
  fi
  if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    git diff --name-only HEAD~1 HEAD
    return
  fi
  git ls-files
}

idea_suites() {
  local version="$1"
  jq -nc --arg ver "$version" '
    ["OpenInIdeTests","ConnectedAnalysisTests","ConfigurationTests","Standalone"]
    | map({ide_version: $ver, test_suite: .})
  '
}

append_json() {
  jq -nc --argjson a "$1" --argjson b "$2" '$a + $b'
}

emit() {
  local matrix="$1"
  local skip="${2:-false}"
  echo "matrix=${matrix}" >> "${GITHUB_OUTPUT}"
  echo "skip_its=${skip}" >> "${GITHUB_OUTPUT}"
  echo "QA matrix (${MODE}): ${matrix}"
  echo "skip_its=${skip}"
}

MIN="$(read_gradle_prop minSupportedIdeVersion)"
LATEST="$(read_gradle_prop latestStableIdeVersion)"
EAP="$(read_gradle_prop eapIdeVersion)"
RIDER_MIN="$(prop_or minRiderIdeVersion "${MIN}")"
RIDER_LATEST="$(prop_or latestRiderIdeVersion "${LATEST}")"
PYCHARM_LATEST="$(prop_or latestPyCharmIdeVersion "${LATEST}")"

case "${MODE}" in
  pr)
    echo "PR matrix: latest=${LATEST} rider=${RIDER_LATEST} (min=${MIN} is weekly-only)"
    # Capture the status before mapfile: process substitution hides git failures.
    CHANGED_FILES="$(changed_files)"
    FILES=()
    if [[ -n "${CHANGED_FILES}" ]]; then
      mapfile -t FILES <<< "${CHANGED_FILES}"
    fi
    if [[ ${#FILES[@]} -eq 0 ]]; then
      emit "$(idea_suites "IC-${LATEST}")" "false"
      exit 0
    fi
    docs_only=true
    run_clion=false
    run_rider=false
    for f in "${FILES[@]}"; do
      case "${f}" in
        *.md|HEADER|LICENSE|LICENSE.*|docs/*|.github/CODEOWNERS|.github/renovate.json)
          continue ;;
        its/*|.github/*|gradle/*|*.gradle.kts|gradle.properties|mise.toml)
          run_clion=true
          run_rider=true ;;
        clion/*|clion-resharper/*|src/main/resources/META-INF/plugin-clion*.xml)
          run_clion=true ;;
        rider/*|src/*Rider*|src/*rider*)
          run_rider=true ;;
        *) ;;
      esac
      docs_only=false
    done
    if [[ "${docs_only}" == "true" ]]; then
      emit "[]" "true"
      exit 0
    fi
    MATRIX="$(idea_suites "IC-${LATEST}")"
    if [[ "${run_clion}" == "true" ]]; then
      MATRIX="$(append_json "${MATRIX}" "$(jq -nc --arg ver "CL-${LATEST}" \
        '[{ide_version:$ver,test_suite:"CLion"}]')")"
    fi
    if [[ "${run_rider}" == "true" ]]; then
      MATRIX="$(append_json "${MATRIX}" "$(jq -nc --arg ver "RD-${RIDER_LATEST}" \
        '[{ide_version:$ver,test_suite:"Rider"}]')")"
    fi
    emit "${MATRIX}" "false"
    ;;
  weekly)
    RIDER_EAP="$(prop_or eapRiderIdeVersion "${EAP}")"
    MATRIX="$(idea_suites "IC-${MIN}")"
    MATRIX="$(append_json "${MATRIX}" "$(idea_suites "IU-${EAP}")")"
    MATRIX="$(append_json "${MATRIX}" "$(jq -nc \
      --arg clmin "CL-${MIN}" --arg cllat "CL-${LATEST}" --arg cleap "CL-${EAP}" \
      --arg rdmin "RD-${RIDER_MIN}" --arg rdlat "RD-${RIDER_LATEST}" --arg rdeap "RD-${RIDER_EAP}" \
      --arg pslat "PS-${LATEST}" --arg pylat "PY-${PYCHARM_LATEST}" \
      --arg golat "GO-${LATEST}" --arg iumin "IU-${MIN}" \
      '[
        {ide_version:$clmin,test_suite:"CLion"},
        {ide_version:$cllat,test_suite:"CLion"},
        {ide_version:$cleap,test_suite:"CLion"},
        {ide_version:$rdmin,test_suite:"Rider"},
        {ide_version:$rdlat,test_suite:"Rider"},
        {ide_version:$rdeap,test_suite:"Rider"},
        {ide_version:$pslat,test_suite:"PhpStorm"},
        {ide_version:$pylat,test_suite:"PyCharm"},
        {ide_version:$golat,test_suite:"GoLand"},
        {ide_version:$iumin,test_suite:"PLSQL"}
      ]')")"
    emit "${MATRIX}" "false"
    ;;
  *)
    echo "Unknown mode: ${MODE}" >&2
    exit 1
    ;;
esac
