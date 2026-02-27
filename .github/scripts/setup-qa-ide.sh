#!/bin/bash
# Script to setup IDE for QA tests - detects embedded vs Repox sources
#
# Usage:
#   setup-qa-ide.sh <IDE_VERSION>
#
# Parameters:
#   IDE_VERSION: IDE identifier (e.g., IC-2025.3.2, PY-2023.3.7, CL-2024.3.6)
#
# Environment variables (optional):
#   ARTIFACTORY_URL:          Required for non-embedded IDEs (unless cached)
#   ARTIFACTORY_USER:         Required for non-embedded IDEs (unless cached)
#   ARTIFACTORY_ACCESS_TOKEN: Required for non-embedded IDEs (unless cached)
#   GITHUB_ENV:               GitHub Actions environment file (for setting variables)
#
# Container environment variables (for embedded IDEs):
#   IDEA_2023_DIR, IDEA_ULTIMATE_2023_DIR, CLION_2023_DIR, CLION_2024_DIR, RIDER_2023_DIR
#
# Sets GITHUB_ENV variables for Gradle build:
#   IDEA_HOME, CLION_HOME, RIDER_HOME, etc.
#
# Exit codes:
#   0: Success (IDE ready to use)
#   1: Invalid parameters or setup failure

set -euo pipefail

# Validate parameters
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <IDE_VERSION>" >&2
    echo "Example: $0 IC-2025.3.2" >&2
    exit 1
fi
IDE_VERSION="$1"

# Parse IDE code and version
if [[ ! "${IDE_VERSION}" =~ ^([A-Z]+)-(.+)$ ]]; then
    echo "::error::Invalid IDE version format: ${IDE_VERSION}. Expected format: CODE-VERSION"
    exit 1
fi
IDE_CODE="${BASH_REMATCH[1]}"
VERSION="${BASH_REMATCH[2]}"
YEAR="${VERSION%%.*}"

: "${GITHUB_ENV:=/dev/null}"

echo "Setting up IDE: ${IDE_CODE}-${VERSION} (year: ${YEAR})"

# Check if IDE is embedded in the container
EMBEDDED="false"
IDE_PATH=""
case "${IDE_CODE}-${YEAR}" in
    IC-2023)
        EMBEDDED="true"
        IDE_PATH="${IDEA_2023_DIR:?IDEA_2023_DIR is not set in the container}"
        ENV_VAR="IDEA_HOME"
        echo "✓ Using embedded IntelliJ Community 2023 from container"
        ;;
    IU-2023)
        EMBEDDED="true"
        IDE_PATH="${IDEA_ULTIMATE_2023_DIR:?IDEA_ULTIMATE_2023_DIR is not set in the container}"
        ENV_VAR="IDEA_HOME"
        echo "✓ Using embedded IntelliJ Ultimate 2023 from container"
        ;;
    CL-2023)
        EMBEDDED="true"
        IDE_PATH="${CLION_2023_DIR:?CLION_2023_DIR is not set in the container}"
        ENV_VAR="CLION_HOME"
        echo "✓ Using embedded CLion 2023 from container"
        ;;
    CL-2024)
        EMBEDDED="true"
        IDE_PATH="${CLION_2024_DIR:?CLION_2024_DIR is not set in the container}"
        ENV_VAR="CLION_HOME"
        echo "✓ Using embedded CLion 2024 from container"
        ;;
    RD-2023)
        EMBEDDED="true"
        IDE_PATH="${RIDER_2023_DIR:?RIDER_2023_DIR is not set in the container}"
        ENV_VAR="RIDER_HOME"
        echo "✓ Using embedded Rider 2023 from container"
        ;;
    RD-2024)
        EMBEDDED="true"
        IDE_PATH="${RIDER_2024_DIR:?RIDER_2024_DIR is not set in the container}"
        ENV_VAR="RIDER_HOME"
        echo "✓ Using embedded Rider 2024 from container"
        ;;
esac

# If not embedded, handle non-embedded IDE (download from Repox if needed)
if [[ "${EMBEDDED}" == "false" ]]; then
    echo "IDE is not embedded in container, will use Repox download"

    # Determine cache path based on IDE code
    case "${IDE_CODE}" in
        IC)
            CACHE_PATH="$HOME/.cache/JetBrains/IdeaIC/${VERSION}"
            ENV_VAR="IDEA_HOME"
            ;;
        IU)
            CACHE_PATH="$HOME/.cache/JetBrains/IdeaIU/${VERSION}"
            ENV_VAR="IDEA_HOME"
            ;;
        CL)
            CACHE_PATH="$HOME/.cache/JetBrains/CLion/${VERSION}"
            ENV_VAR="CLION_HOME"
            ;;
        RD)
            CACHE_PATH="$HOME/.cache/JetBrains/Rider/${VERSION}"
            ENV_VAR="RIDER_HOME"
            ;;
        PY)
            CACHE_PATH="$HOME/.cache/JetBrains/PyCharm/${VERSION}"
            ENV_VAR="PYCHARM_HOME"
            ;;
        PC)
            CACHE_PATH="$HOME/.cache/JetBrains/PyCharmCE/${VERSION}"
            ENV_VAR="PYCHARM_HOME"
            ;;
        PS)
            CACHE_PATH="$HOME/.cache/JetBrains/PhpStorm/${VERSION}"
            ENV_VAR="PHPSTORM_HOME"
            ;;
        GO)
            CACHE_PATH="$HOME/.cache/JetBrains/GoLand/${VERSION}"
            ENV_VAR="GOLAND_HOME"
            ;;
        *)
            echo "::error::Unsupported IDE code: ${IDE_CODE}"
            exit 1
            ;;
    esac
    IDE_PATH="${CACHE_PATH}"

    # Check if already in cache (from previous step or restored by GitHub Actions cache)
    if [[ -d "${CACHE_PATH}" && -n "$(ls -A "${CACHE_PATH}" 2>/dev/null)" ]]; then
        echo "✓ IDE found in cache: ${CACHE_PATH}"
    else
        echo "IDE not in cache, downloading from Repox..."
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        if ! "${SCRIPT_DIR}/download-ides.sh" "${IDE_VERSION}" "${CACHE_PATH}"; then
            echo "::error::Failed to download IDE from Repox"
            exit 1
        fi
    fi
fi
if [[ ! -d "${IDE_PATH}" ]]; then
    echo "::error::IDE path does not exist: ${IDE_PATH}"
    exit 1
fi
if [[ -z "$(ls -A "${IDE_PATH}" 2>/dev/null)" ]]; then
    echo "::error::IDE path is empty: ${IDE_PATH}"
    exit 1
fi
echo "${ENV_VAR}=${IDE_PATH}" >> "${GITHUB_ENV}"

echo "✓ IDE setup complete: ${IDE_CODE}-${VERSION}"
echo "  Path: ${IDE_PATH}"
echo "  Source: $(if [[ "${EMBEDDED}" == "true" ]]; then echo "container"; else echo "Repox"; fi)"

exit 0
