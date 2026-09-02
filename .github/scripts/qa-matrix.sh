#!/bin/bash
# Build a GitHub Actions matrix for UI integration tests.
#
# Usage:
#   qa-matrix.sh pr      <min> <latest>
#   qa-matrix.sh nightly <min> <latest>
#   qa-matrix.sh eap     <eap>
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

MODE="${1:-}"
if [[ -z "${MODE}" ]]; then
  echo "Usage: $0 pr <min> <latest> | nightly <min> <latest> | eap <eap>" >&2
  exit 1
fi

read_prop_version() {
  local value="$1"
  echo "${value}" | cut -d'#' -f1 | xargs
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
    git fetch --no-tags --depth=1 origin "${GITHUB_BASE_REF}" >/dev/null 2>&1 || true
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

case "${MODE}" in
  pr)
    MIN="$(read_prop_version "${2:?min version required}")"
    LATEST="$(read_prop_version "${3:?latest version required}")"
    echo "PR matrix: latest=${LATEST} (min=${MIN} is used on nightly only)"
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
      MATRIX="$(append_json "${MATRIX}" "$(jq -nc --arg ver "RD-${LATEST}" '[{ide_version:$ver,qa_category:"RiderLatest"}]')")"
    fi
    emit "${MATRIX}" "false"
    ;;
  nightly)
    MIN="$(read_prop_version "${2:?min version required}")"
    LATEST="$(read_prop_version "${3:?latest version required}")"
    MATRIX="$(idea_suites "IC-${MIN}" "IdeaMin")"
    MATRIX="$(append_json "${MATRIX}" "$(jq -nc \
      --arg clmin "CL-${MIN}" --arg cllat "CL-${LATEST}" \
      --arg rdmin "RD-${MIN}" --arg rdlat "RD-${LATEST}" \
      --arg pslat "PS-${LATEST}" --arg pylat "PY-${LATEST}" \
      --arg golat "GO-${LATEST}" --arg iumin "IU-${MIN}" \
      '[
        {ide_version:$clmin,qa_category:"CLionMin"},
        {ide_version:$cllat,qa_category:"CLionLatest"},
        {ide_version:$rdmin,qa_category:"RiderMin"},
        {ide_version:$rdlat,qa_category:"RiderLatest"},
        {ide_version:$pslat,qa_category:"PhpStormLatest"},
        {ide_version:$pylat,qa_category:"PyCharmLatest"},
        {ide_version:$golat,qa_category:"GoLandLatest"},
        {ide_version:$iumin,qa_category:"IdeaUltimateMin"}
      ]')")"
    emit "${MATRIX}" "false"
    ;;
  eap)
    EAP="$(read_prop_version "${2:?eap version required}")"
    MATRIX="$(jq -nc --arg eap "${EAP}" '[
      {ide_version:("IU-" + $eap),qa_category:"IdeaUltimateEAP"},
      {ide_version:("CL-" + $eap),qa_category:"CLionEAP"},
      {ide_version:("RD-" + $eap),qa_category:"RiderEAP"}
    ]')"
    emit "${MATRIX}" "false"
    ;;
  *)
    echo "Unknown mode: ${MODE}" >&2
    exit 1
    ;;
esac
