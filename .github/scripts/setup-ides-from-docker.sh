#!/bin/bash
set -euo pipefail

# Script to extract JetBrains IDEs from SonarLint Docker images
# Usage: setup-ides-from-docker.sh <IDE_VERSION> <IDE_CACHE_DIR>
#   IDE_VERSION: IDE version from matrix (e.g., IC-2023.1.7, CL-2025.2)
#   IDE_CACHE_DIR: Base directory for IDE installation (e.g., /opt/jetbrains)

IDE_VERSION_FULL="${1:?}"
IDE_CACHE_DIR="${2:-/opt/jetbrains}"
ECR_REGISTRY="460386131003.dkr.ecr.eu-central-1.amazonaws.com"

# Parse IDE code and version
IDE_CODE="${IDE_VERSION_FULL%%-*}"
VERSION="${IDE_VERSION_FULL##*-}"

echo "Setting up IDE: ${IDE_CODE} version ${VERSION}"

# Determine if we should use legacy paths (for standard build IDEs)
# Legacy paths are used for the main build workflow IDEs only
USE_LEGACY_PATHS=false
case "${IDE_CODE}-${VERSION}" in
  IC-2023.1.7|IU-2023.1.7|CL-2023.1.7|RS-2024.3.7|RD-2024.3.7)
    USE_LEGACY_PATHS=true
    ;;
esac

# Map IDE code to Docker image and source/dest paths
case "${IDE_CODE}" in
  IC)  # IntelliJ IDEA Community
    IMAGE="sonarlint-idea"
    SOURCE_PATH="/opt/jetbrains/idea/${VERSION}"
    if [ "${USE_LEGACY_PATHS}" = "true" ]; then
      DEST_PATH="${IDE_CACHE_DIR}/intellij"
    else
      DEST_PATH="$HOME/.cache/JetBrains/IdeaIC/${VERSION}"
    fi
    ;;
  IU)  # IntelliJ IDEA Ultimate
    IMAGE="sonarlint-idea"
    SOURCE_PATH="/opt/jetbrains/idea/ultimate/${VERSION}"
    if [ "${USE_LEGACY_PATHS}" = "true" ]; then
      DEST_PATH="${IDE_CACHE_DIR}/ultimate"
    else
      DEST_PATH="$HOME/.cache/JetBrains/IdeaIU/${VERSION}"
    fi
    ;;
  CL)  # CLion
    IMAGE="sonarlint-clion"
    SOURCE_PATH="/opt/jetbrains/clion/${VERSION}"
    if [ "${USE_LEGACY_PATHS}" = "true" ]; then
      DEST_PATH="${IDE_CACHE_DIR}/clion"
    else
      DEST_PATH="$HOME/.cache/JetBrains/CLion/${VERSION}"
    fi
    ;;
  RD)  # Rider
    IMAGE="sonarlint-rider"
    if [ "${VERSION}" = "2023.3.6" ]; then
      echo "::error title=Rider 2023.3.6 is disabled in Docker image"
      exit 1
    fi
    SOURCE_PATH="/opt/jetbrains/rider/${VERSION}"
    if [ "${USE_LEGACY_PATHS}" = "true" ]; then
      DEST_PATH="${IDE_CACHE_DIR}/rider"
    else
      DEST_PATH="$HOME/.cache/JetBrains/Rider/${VERSION}"
    fi
    ;;
  RS)  # ReSharper (CLion with radler plugin)
    IMAGE="sonarlint-clion"
    SOURCE_PATH="/opt/jetbrains/clion/${VERSION}"
    if [ "${USE_LEGACY_PATHS}" = "true" ]; then
      DEST_PATH="${IDE_CACHE_DIR}/resharper"
    else
      DEST_PATH="$HOME/.cache/JetBrains/ReSharper/${VERSION}"
    fi
    ;;
  PY)  # PyCharm Professional
    IMAGE="sonarlint-pycharm"
    SOURCE_PATH="/opt/jetbrains/pycharm/professional/${VERSION}"
    DEST_PATH="$HOME/.cache/JetBrains/PyCharm/${VERSION}"
    ;;
  PC)  # PyCharm Community
    IMAGE="sonarlint-pycharm"
    SOURCE_PATH="/opt/jetbrains/pycharm/community/${VERSION}"
    DEST_PATH="$HOME/.cache/JetBrains/PyCharmCE/${VERSION}"
    ;;
  PS)  # PhpStorm
    IMAGE="sonarlint-phpstorm"
    SOURCE_PATH="/opt/jetbrains/phpstorm/${VERSION}"
    DEST_PATH="$HOME/.cache/JetBrains/PhpStorm/${VERSION}"
    ;;
  GO)  # GoLand
    IMAGE="sonarlint-goland"
    SOURCE_PATH="/opt/jetbrains/goland/${VERSION}"
    DEST_PATH="$HOME/.cache/JetBrains/GoLand/${VERSION}"
    ;;
  *)
    echo "Error: Unknown IDE code '${IDE_CODE}'"
    echo "Supported codes: IC, IU, CL, RD, RS (ReSharper), PY, PC, PS, GO"
    exit 1
    ;;
esac

echo "Pulling image: ${ECR_REGISTRY}/${IMAGE}:pr-139"
docker pull "${ECR_REGISTRY}/${IMAGE}:pr-139"

CONTAINER_ID=$(docker create "${ECR_REGISTRY}/${IMAGE}:pr-139")
echo "Created temporary container: ${CONTAINER_ID}"
mkdir -p "$(dirname "${DEST_PATH}")"
echo "Extracting IDE from ${SOURCE_PATH} to ${DEST_PATH}"
docker cp "${CONTAINER_ID}:${SOURCE_PATH}" "${DEST_PATH}"
docker rm "${CONTAINER_ID}"

echo "✓ IDE successfully extracted to: ${DEST_PATH}"
find "${DEST_PATH}" -maxdepth 1 -ls
