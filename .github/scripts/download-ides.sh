#!/bin/bash
# Script to download a single JetBrains IDE from Repox/Artifactory
#
# Usage:
#   download-ides.sh <IDE_CODE> <DEST_DIR>
#
# Parameters:
#   IDE_CODE:  IDE identifier (e.g., IC-2025.3.2, PY-2023.3.7)
#   DEST_DIR:  Destination directory for extraction
#
# Environment variables (required):
#   ARTIFACTORY_URL:          Repox/Artifactory base URL
#   ARTIFACTORY_USER:         Authentication username
#   ARTIFACTORY_ACCESS_TOKEN: Authentication token
#
# Exit codes:
#   0: Success
#   1: Invalid parameters or download/extraction failure

set -euo pipefail

# Validate parameters
if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <IDE_CODE> <DEST_DIR>" >&2
    echo "Example: $0 IC-2025.3.2 ~/.cache/JetBrains/IdeaIC/2025.3.2" >&2
    exit 1
fi
IDE_CODE="$1"
DEST_DIR="$2"

# Parse IDE code and version
if [[ ! "${IDE_CODE}" =~ ^([A-Z]+)-(.+)$ ]]; then
    echo "::error::Invalid IDE code format: ${IDE_CODE}. Expected format: CODE-VERSION (e.g., IC-2025.3.2)"
    exit 1
fi
IDE_TYPE="${BASH_REMATCH[1]}"
IDE_VERSION="${BASH_REMATCH[2]}"

: "${ARTIFACTORY_URL:?}" "${ARTIFACTORY_USER:?}" "${ARTIFACTORY_ACCESS_TOKEN:?}"

# Returns 0 (true) if the version is 2025.3 or later (unified distribution), 1 otherwise.
# Unified distributions (2025.3+) no longer ship separate Community/Ultimate/Professional variants.
is_unified_distribution() {
    local version="$1"
    if [[ "${version}" =~ ^([0-9]+)\.([0-9]+) ]]; then
        local major="${BASH_REMATCH[1]}"
        local minor="${BASH_REMATCH[2]}"
        if [[ ${major} -gt 2025 ]] || [[ ${major} -eq 2025 && ${minor} -ge 3 ]]; then
            return 0
        fi
    fi
    return 1
}

# Map IDE type to Repox artifact path
case "${IDE_TYPE}" in
    IC|IU)
        # 2025.3+ uses a single unified distribution (no IC/IU distinction)
        if is_unified_distribution "${IDE_VERSION}"; then
            ARTIFACT_PATH="jetbrains-download/idea/idea-${IDE_VERSION}.tar.gz"
            IDE_NAME="IntelliJ IDEA"
        elif [[ "${IDE_TYPE}" == "IC" ]]; then
            ARTIFACT_PATH="jetbrains-download/idea/ideaIC-${IDE_VERSION}.tar.gz"
            IDE_NAME="IntelliJ IDEA Community"
        else
            ARTIFACT_PATH="jetbrains-download/idea/ideaIU-${IDE_VERSION}.tar.gz"
            IDE_NAME="IntelliJ IDEA Ultimate"
        fi
        ;;
    CL)
        ARTIFACT_PATH="jetbrains-download/cpp/CLion-${IDE_VERSION}.tar.gz"
        IDE_NAME="CLion"
        ;;
    RD)
        ARTIFACT_PATH="jetbrains-download/rider/JetBrains.Rider-${IDE_VERSION}.tar.gz"
        IDE_NAME="Rider"
        ;;
    PY|PC)
        # 2025.3+ uses a single unified distribution (no Community/Professional distinction)
        if is_unified_distribution "${IDE_VERSION}"; then
            ARTIFACT_PATH="jetbrains-download/python/pycharm-${IDE_VERSION}.tar.gz"
            IDE_NAME="PyCharm"
        elif [[ "${IDE_TYPE}" == "PC" ]]; then
            ARTIFACT_PATH="jetbrains-download/python/pycharm-community-${IDE_VERSION}.tar.gz"
            IDE_NAME="PyCharm Community"
        else
            ARTIFACT_PATH="jetbrains-download/python/pycharm-professional-${IDE_VERSION}.tar.gz"
            IDE_NAME="PyCharm Professional"
        fi
        ;;
    PS)
        ARTIFACT_PATH="jetbrains-download/webide/PhpStorm-${IDE_VERSION}.tar.gz"
        IDE_NAME="PhpStorm"
        ;;
    GO)
        ARTIFACT_PATH="jetbrains-download/go/goland-${IDE_VERSION}.tar.gz"
        IDE_NAME="GoLand"
        ;;
    *)
        echo "::error::Unknown IDE type '${IDE_TYPE}'. Supported: IC, IU, CL, RD, PY, PC, PS, GO"
        exit 1
        ;;
esac
DOWNLOAD_URL="${ARTIFACTORY_URL}/${ARTIFACT_PATH}"
echo "Downloading ${IDE_NAME} ${IDE_VERSION} from Repox..."
echo "  URL: ${DOWNLOAD_URL}"
echo "  Destination: ${DEST_DIR}"

TEMP_DIR=$(mktemp -d)
trap 'rm -rf "${TEMP_DIR}"' EXIT
TEMP_FILE="${TEMP_DIR}/ide.tar.gz"

echo "Downloading artifact..."
if ! curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" -o "${TEMP_FILE}" "${DOWNLOAD_URL}"; then
    echo "::error::Failed to download ${IDE_NAME} ${IDE_VERSION} from ${DOWNLOAD_URL}"
    exit 1
fi

echo "Download complete. Extracting to ${DEST_DIR}..."
EXTRACT_DIR="${TEMP_DIR}/extract"
mkdir -p "${EXTRACT_DIR}"
if ! tar -xzf "${TEMP_FILE}" -C "${EXTRACT_DIR}"; then
    echo "::error::Failed to extract ${TEMP_FILE} (${IDE_NAME} ${IDE_VERSION})"
    exit 1
fi
# Find the extracted IDE directory (should be the only directory in EXTRACT_DIR)
IDE_DIR=$(find "${EXTRACT_DIR}" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [[ -z "${IDE_DIR}" ]]; then
    echo "::error::No directory found after extraction (${IDE_NAME} ${IDE_VERSION})"
    exit 1
fi

mkdir -p "$(dirname "${DEST_DIR}")"
mv "${IDE_DIR}" "${DEST_DIR}"

echo "✓ Successfully downloaded and extracted ${IDE_NAME} ${IDE_VERSION}"
echo "  Location: ${DEST_DIR}"

# Verify Shell script exists
if ! compgen -G "${DEST_DIR}/bin/*.sh" > /dev/null; then
    echo "::warning::IDE extraction may have failed - no expected startup script found in ${DEST_DIR}/bin/"
    ls -la "${DEST_DIR}/bin/" || true
fi

exit 0
