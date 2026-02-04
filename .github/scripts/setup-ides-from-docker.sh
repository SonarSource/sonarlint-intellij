#!/bin/bash
# Script to extract JetBrains IDEs from SonarLint Docker images
#
# Usage:
#   setup-ides-from-docker.sh
#
# Environment variables (required):
#   GITHUB_ENV: GitHub Actions environment file path
#   IDE_CACHE_DIR: Base directory for IDE installation (default: /opt/jetbrains)
#   INTELLIJ_VERSION: IntelliJ Community version to extract
#   CLION_VERSION: CLion version to extract
#   RIDER_VERSION: Rider version to extract
#   RESHARPER_VERSION: ReSharper (CLion) version to extract
#   ULTIMATE_VERSION: IntelliJ Ultimate version to extract
#
# Environment variables (optional, for cache awareness):
#   CACHE_INTELLIJ: "true" to skip IntelliJ extraction (already cached)
#   CACHE_CLION: "true" to skip CLion extraction (already cached)
#   CACHE_RIDER: "true" to skip Rider extraction (already cached)
#   CACHE_RESHARPER: "true" to skip ReSharper extraction (already cached)
#   CACHE_ULTIMATE: "true" to skip Ultimate extraction (already cached)
#
# Docker configuration:
#   DOCKER_IMAGE_TAG: Docker image tag (default: pr-139)
#   AWS_ACCOUNT_ID: AWS account ID (default: 460386131003)
#   AWS_REGION: AWS region (default: eu-central-1)

set -euo pipefail

: "${GITHUB_ENV:?}"
: "${IDE_CACHE_DIR:=/opt/jetbrains}"
: "${DOCKER_IMAGE_TAG:?}"
: "${AWS_ACCOUNT_ID:?}"
: "${AWS_REGION:?}"

: "${INTELLIJ_VERSION:?}" "${CLION_VERSION:?}" "${RIDER_VERSION:?}" "${RESHARPER_VERSION:?}" "${ULTIMATE_VERSION:?}"

: "${CACHE_INTELLIJ:=false}" "${CACHE_CLION:=false}" "${CACHE_RIDER:=false}" "${CACHE_RESHARPER:=false}" "${CACHE_ULTIMATE:=false}"

readonly ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Parse IDE version code into components
parse_ide_version() {
  local ide_version_full="$1"
  local -n code_ref="$2"
  local -n version_ref="$3"

  # shellcheck disable=SC2034
  code_ref="${ide_version_full%%-*}"
  # shellcheck disable=SC2034
  version_ref="${ide_version_full##*-}"
}

# Check if version should use legacy paths (/opt/jetbrains/xxx vs ~/.cache/JetBrains/XXX)
should_use_legacy_path() {
  local ide_code="$1"
  local version="$2"

  case "${ide_code}-${version}" in
    IC-2023.3.8|IU-2023.3.8|CL-2023.3.6|RS-2024.3.6|RD-2024.3.9)
      return 0  # true - use legacy
      ;;
    *)
      return 1  # false - use new path
      ;;
  esac
}

# Map IDE code to Docker image name and source path
# shellcheck disable=SC2034
get_ide_image_info() {
  local ide_code="$1"
  local version="$2"
  local ide_cache_dir="$3"
  local -n image_ref="$4"
  local -n source_ref="$5"
  local -n dest_ref="$6"

  local use_legacy
  if should_use_legacy_path "${ide_code}" "${version}"; then
    use_legacy="yes"
  else
    use_legacy="" # empty means use no
  fi

  case "${ide_code}" in
    IC)  # IntelliJ IDEA Community
      image_ref="sonarlint-idea"
      source_ref="/opt/jetbrains/idea/${version}"
      dest_ref="${use_legacy:+${ide_cache_dir}/intellij}"
      : "${dest_ref:=$HOME/.cache/JetBrains/IdeaIC/${version}}"
      ;;
    IU)  # IntelliJ IDEA Ultimate
      image_ref="sonarlint-idea"
      source_ref="/opt/jetbrains/idea/ultimate/${version}"
      dest_ref="${use_legacy:+${ide_cache_dir}/ultimate}"
      : "${dest_ref:=$HOME/.cache/JetBrains/IdeaIU/${version}}"
      ;;
    CL)  # CLion
      image_ref="sonarlint-clion"
      source_ref="/opt/jetbrains/clion/${version}"
      dest_ref="${use_legacy:+${ide_cache_dir}/clion}"
      : "${dest_ref:=$HOME/.cache/JetBrains/CLion/${version}}"
      ;;
    RD)  # Rider
      if [[ "${version}" == "2023.3.6" ]]; then
        echo "::error::Rider 2023.3.6 is disabled (critical security vulnerabilities)"
        return 1
      fi
      image_ref="sonarlint-rider"
      source_ref="/opt/jetbrains/rider/${version}"
      dest_ref="${use_legacy:+${ide_cache_dir}/rider}"
      : "${dest_ref:=$HOME/.cache/JetBrains/Rider/${version}}"
      ;;
    RS)  # ReSharper (CLion with radler plugin)
      image_ref="sonarlint-clion"
      source_ref="/opt/jetbrains/clion/${version}"
      dest_ref="${use_legacy:+${ide_cache_dir}/resharper}"
      : "${dest_ref:=$HOME/.cache/JetBrains/ReSharper/${version}}"
      ;;
    *)
      echo "::error::Unknown IDE code '${ide_code}'. Supported: IC, IU, CL, RD, RS"
      return 1
      ;;
  esac
}

# List available versions in Docker image for debugging
list_available_versions() {
  local image_tag="$1"
  local base_path="$2"

  echo "Available versions in ${image_tag}:" >&2
  docker run --rm --entrypoint "" "${image_tag}" \
    find "${base_path}" -maxdepth 2 -type d -name "[0-9]*" 2>/dev/null || \
    echo "  (could not list versions)" >&2
}

# Extract a single IDE from Docker image
extract_ide() {
  local ide_version_full="$1"

  local ide_code version image source_path dest_path
  parse_ide_version "${ide_version_full}" ide_code version
  echo "Setting up IDE: ${ide_code} version ${version}..."

  get_ide_image_info "${ide_code}" "${version}" "${IDE_CACHE_DIR}" image source_path dest_path
  local image_tag="${ECR_REGISTRY}/${image}:${DOCKER_IMAGE_TAG}"
  echo "::debug::Pulling image: ${image_tag}"
  docker pull --quiet "${image_tag}"

  local container_id
  container_id=$(docker create "${image_tag}")
  echo "Created temporary container ${container_id:0:12} for ${image}:${DOCKER_IMAGE_TAG}"
  mkdir -p "$(dirname "${dest_path}")"
  echo "Extracting IDE from ${source_path} to ${dest_path}"

  if ! docker cp --quiet "${container_id}:${source_path}" "${dest_path}" 2>/dev/null; then
    echo "::error::IDE version ${ide_code}-${version} not found in ${image}:${DOCKER_IMAGE_TAG} at ${source_path}"
    list_available_versions "${image_tag}" "$(dirname "${source_path}")"
    docker rm "${container_id}" >/dev/null 2>&1
    return 1
  fi
  docker rm "${container_id}" >/dev/null 2>&1
  echo "✓ IDE successfully extracted to: ${dest_path}"
  find "${dest_path}" -maxdepth 1 -ls | head -10

  # Set environment variables for GitHub Actions
  case "${ide_code}" in
    IC)
      echo "IDEA_HOME=${dest_path}" >> "${GITHUB_ENV}"
      ;;
    IU)
      echo "ULTIMATE_HOME=${dest_path}" >> "${GITHUB_ENV}"
      ;;
    CL)
      echo "CLION_HOME=${dest_path}" >> "${GITHUB_ENV}"
      ;;
    RD)
      echo "RIDER_HOME=${dest_path}" >> "${GITHUB_ENV}"
      ;;
    RS)
      echo "RESHARPER_HOME=${dest_path}" >> "${GITHUB_ENV}"
      ;;
  esac
}

# Main execution: Extract IDEs with cache awareness
echo "Extracting missing IDEs from Docker images in parallel..."
declare -a pids=()

# Extract IntelliJ IDEA Community (if not cached)
if [[ "${CACHE_INTELLIJ}" != "true" ]]; then
  (extract_ide "IC-${INTELLIJ_VERSION}") &
  pids+=($!)
else
  echo "✓ IntelliJ IDEA Community (cached)"
fi

# Extract CLion (if not cached)
if [[ "${CACHE_CLION}" != "true" ]]; then
  (extract_ide "CL-${CLION_VERSION}") &
  pids+=($!)
else
  echo "✓ CLion (cached)"
fi

# Extract Rider (if not cached)
if [[ "${CACHE_RIDER}" != "true" ]]; then
  (extract_ide "RD-${RIDER_VERSION}") &
  pids+=($!)
else
  echo "✓ Rider (cached)"
fi

# Extract CLion for ReSharper (if not cached)
if [[ "${CACHE_RESHARPER}" != "true" ]]; then
  (extract_ide "RS-${RESHARPER_VERSION}") &
  pids+=($!)
else
  echo "✓ CLion (ReSharper) (cached)"
fi

# Extract IntelliJ IDEA Ultimate (if not cached)
if [[ "${CACHE_ULTIMATE}" != "true" ]]; then
  (extract_ide "IU-${ULTIMATE_VERSION}") &
  pids+=($!)
else
  echo "✓ IntelliJ IDEA Ultimate (cached)"
fi

# Wait for all extractions to complete
failed=0
for pid in "${pids[@]}"; do
  if ! wait "${pid}"; then
    failed=1
  fi
done

if [[ "${failed}" -eq 1 ]]; then
  echo "::error::One or more IDE extractions failed!"
  exit 1
fi

echo "All required IDEs ready"
ls -la "${IDE_CACHE_DIR}/" || true
