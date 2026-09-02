#!/bin/bash
# Build a GitHub Actions matrix for UI integration tests.
#
# Usage:
#   qa-matrix.sh pr
#   qa-matrix.sh nightly
#   qa-matrix.sh eap
#
# Reads IDE versions from gradle.properties (see minSupportedIdeVersion,
# latestStableIdeVersion, eapIdeVersion, and optional per-product overrides).
#
# Writes GITHUB_OUTPUT keys:
#   matrix     JSON array of {ide_version, qa_category, test_suite?}
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
  echo "Usage: $0 pr | nightly | eap" >&2
  exit 1
fi

read_gradle_prop() {
  local key="$1"
  local line
  line="$(grep "^${key}=" "${PROPS}" || true)"
  if [[ -z "${line}" ]]; then
    echo ""
    return
  fi
  echo "${line#*=}" | cut -d'#' -f1 | xargs
}

prop_or() {
  local override="$1"
  local fallback="$2"
  local value
  value="$(read_gradle_prop "${override}")"
  if [[ -n "${value}" ]]; then
    echo "${value}"
  else
    echo "${fallback}"
  fi
}

is_docs_path() {
  local f="$1"
  case "${f}" in
    *.md|HEADER|LICENSE|LICENSE.*|docs/*|.github/CODEOWNERS|.github/renovate.json)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_harness_path() {
  local f="$1"
  case "${f}" in
    its/README.md)
      return 1
      ;;
    its/*|.github/*|gradle/*|*.gradle.kts|gradle.properties|settings.gradle.kts|mise.toml)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_clion_path() {
  local f="$1"
  case "${f}" in
    clion/*|clion-resharper/*|src/main/resources/META-INF/plugin-clion*.xml|its/projects/sample-cpp/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_rider_path() {
  local f="$1"
  case "${f}" in
    rider/*|src/main/resources/META-INF/plugin-rider.xml|its/projects/sample-rider/*|its/projects/sample-complex-rider/*)
      return 0
      ;;
    *Rider*|*rider*)
      case "${f}" in
        src/*|its/src/test/*)
          return 0
          ;;
      esac
      return 1
      ;;
    *)
      return 1
      ;;
  esac
}

changed_files() {
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    # Do not pass --depth=1: setup-qa-matrix already checked out with fetch-depth 0.
    # A shallow fetch would write .git/shallow and can break merge-base computation.
    git fetch --no-tags origin "${GITHUB_BASE_REF}" >/dev/null 2>&1 || true
    if git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
      git diff --name-only "origin/${GITHUB_BASE_REF}...HEAD"
      return
    fi
  fi
  if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    git diff --name-only HEAD~1 HEAD
    return
  fi
  git ls-files
}

idea_suites() {
  local version="$1"
  local category="$2"
  jq -nc --arg ver "$version" --arg cat "$category" '
    ["OpenInIdeTests","ConnectedAnalysisTests","ConfigurationTests","Standalone"]
    | map({ide_version: $ver, qa_category: $cat, test_suite: .})
  '
}

append_json() {
  local acc="$1"
  local more="$2"
  if [[ -z "${acc}" || "${acc}" == "[]" ]]; then
    echo "${more}"
  else
    jq -nc --argjson a "${acc}" --argjson b "${more}" '$a + $b'
  fi
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
    echo "PR matrix: latest=${LATEST} rider=${RIDER_LATEST} (min=${MIN} is nightly-only)"
    mapfile -t FILES < <(changed_files)
    if [[ ${#FILES[@]} -eq 0 ]]; then
      emit "$(idea_suites "IC-${LATEST}" "IdeaLatest")" "false"
      exit 0
    fi
    docs_only=true
    run_clion=false
    run_rider=false
    for f in "${FILES[@]}"; do
      if ! is_docs_path "${f}"; then
        docs_only=false
      fi
      if is_harness_path "${f}" || is_clion_path "${f}"; then
        run_clion=true
      fi
      if is_harness_path "${f}" || is_rider_path "${f}"; then
        run_rider=true
      fi
    done
    if [[ "${docs_only}" == "true" ]]; then
      emit "[]" "true"
      exit 0
    fi
    MATRIX="$(idea_suites "IC-${LATEST}" "IdeaLatest")"
    if [[ "${run_clion}" == "true" ]]; then
      MATRIX="$(append_json "${MATRIX}" "$(jq -nc --arg ver "CL-${LATEST}" '[{ide_version:$ver,qa_category:"CLionLatest"}]')")"
    fi
    if [[ "${run_rider}" == "true" ]]; then
      MATRIX="$(append_json "${MATRIX}" "$(jq -nc --arg ver "RD-${RIDER_LATEST}" '[{ide_version:$ver,qa_category:"RiderLatest"}]')")"
    fi
    emit "${MATRIX}" "false"
    ;;
  nightly)
    MATRIX="$(idea_suites "IC-${MIN}" "IdeaMin")"
    MATRIX="$(append_json "${MATRIX}" "$(jq -nc \
      --arg clmin "CL-${MIN}" --arg cllat "CL-${LATEST}" \
      --arg rdmin "RD-${RIDER_MIN}" --arg rdlat "RD-${RIDER_LATEST}" \
      --arg pslat "PS-${LATEST}" --arg pylat "PY-${PYCHARM_LATEST}" \
      --arg golat "GO-${LATEST}" --arg iumin "IU-${MIN}" \
      '[
        {ide_version:$clmin,qa_category:"CLionMin"},
        {ide_version:$cllat,qa_category:"CLionLatest"},
        {ide_version:$rdmin,qa_category:"RiderMin"},
        {ide_version:$rdlat,qa_category:"RiderLatest"},
        {ide_version:$pslat,qa_category:"PhpStormLatest"},
        {ide_version:$pylat,qa_category:"PyCharmLatest"},
        {ide_version:$golat,qa_category:"GoLandLatest"},
        {ide_version:$iumin,qa_category:"IdeaUltimateMin",test_suite:"PLSQL"}
      ]')")"
    emit "${MATRIX}" "false"
    ;;
  eap)
    MATRIX="$(jq -nc --arg eap "${EAP}" --arg rd "$(prop_or eapRiderIdeVersion "${EAP}")" '[
      {ide_version:("IU-" + $eap),qa_category:"IdeaUltimateEAP"},
      {ide_version:("CL-" + $eap),qa_category:"CLionEAP"},
      {ide_version:("RD-" + $rd),qa_category:"RiderEAP"}
    ]')"
    emit "${MATRIX}" "false"
    ;;
  *)
    echo "Unknown mode: ${MODE}" >&2
    exit 1
    ;;
esac
