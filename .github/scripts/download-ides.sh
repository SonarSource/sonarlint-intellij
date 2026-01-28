#!/bin/bash
set -euo pipefail

# Script to download IDEs from Artifactory in parallel
# Usage: download-ides.sh <cache-intellij> <cache-clion> <cache-rider> <cache-resharper> <cache-ultimate>

CACHE_INTELLIJ="${1:-false}"
CACHE_CLION="${2:-false}"
CACHE_RIDER="${3:-false}"
CACHE_RESHARPER="${4:-false}"
CACHE_ULTIMATE="${5:-false}"

echo "Downloading missing IDEs from Repox in parallel..."
PIDS=()

# Download IntelliJ IDEA Community (if not cached)
if [[ "${CACHE_INTELLIJ}" != "true" ]]; then
  (
    echo "Downloading IntelliJ IDEA Community ${INTELLIJ_VERSION} from Repox..."
    mkdir -p "${IDE_CACHE_DIR}/intellij"
    curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" \
      "${ARTIFACTORY_URL}/jetbrains-download/idea/ideaIC-${INTELLIJ_VERSION}.tar.gz" | \
      tar -xz --strip-components=1 -C "${IDE_CACHE_DIR}/intellij"
    echo "✓ IntelliJ IDEA Community done"
  ) &
  PIDS+=($!)
else
  echo "✓ IntelliJ IDEA Community (cached)"
fi

# Download CLion (if not cached)
if [[ "${CACHE_CLION}" != "true" ]]; then
  (
    echo "Downloading CLion ${CLION_VERSION} from Repox..."
    mkdir -p "${IDE_CACHE_DIR}/clion"
    curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" \
      "${ARTIFACTORY_URL}/jetbrains-download/cpp/CLion-${CLION_VERSION}.tar.gz" | \
      tar -xz --strip-components=1 -C "${IDE_CACHE_DIR}/clion"
    echo "✓ CLion done"
  ) &
  PIDS+=($!)
else
  echo "✓ CLion (cached)"
fi

# Download Rider (if not cached)
if [[ "${CACHE_RIDER}" != "true" ]]; then
  (
    echo "Downloading Rider ${RIDER_VERSION} from Repox..."
    mkdir -p "${IDE_CACHE_DIR}/rider"
    curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" \
      "${ARTIFACTORY_URL}/jetbrains-download/rider/JetBrains.Rider-${RIDER_VERSION}.tar.gz" | \
      tar -xz --strip-components=1 -C "${IDE_CACHE_DIR}/rider"
    echo "✓ Rider done"
  ) &
  PIDS+=($!)
else
  echo "✓ Rider (cached)"
fi

# Download CLion for ReSharper (if not cached)
if [[ "${CACHE_RESHARPER}" != "true" ]]; then
  (
    echo "Downloading CLion ${RESHARPER_VERSION} (for ReSharper) from Repox..."
    mkdir -p "${IDE_CACHE_DIR}/resharper"
    curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" \
      "${ARTIFACTORY_URL}/jetbrains-download/cpp/CLion-${RESHARPER_VERSION}.tar.gz" | \
      tar -xz --strip-components=1 -C "${IDE_CACHE_DIR}/resharper"
    echo "✓ CLion (ReSharper) done"
  ) &
  PIDS+=($!)
else
  echo "✓ CLion (ReSharper) (cached)"
fi

# Download IntelliJ IDEA Ultimate (if not cached)
if [[ "${CACHE_ULTIMATE}" != "true" ]]; then
  (
    echo "Downloading IntelliJ IDEA Ultimate ${ULTIMATE_VERSION} from Repox..."
    mkdir -p "${IDE_CACHE_DIR}/ultimate"
    curl -fsSL -u "${ARTIFACTORY_USER}:${ARTIFACTORY_ACCESS_TOKEN}" \
      "${ARTIFACTORY_URL}/jetbrains-download/idea/ideaIU-${ULTIMATE_VERSION}.tar.gz" | \
      tar -xz --strip-components=1 -C "${IDE_CACHE_DIR}/ultimate"
    echo "✓ IntelliJ IDEA Ultimate done"
  ) &
  PIDS+=($!)
else
  echo "✓ IntelliJ IDEA Ultimate (cached)"
fi

# Wait for all downloads to complete
FAILED=0
for pid in "${PIDS[@]}"; do
  wait "$pid" || FAILED=1
done

if [[ $FAILED -eq 1 ]]; then
  echo "One or more IDE downloads failed!"
  exit 1
fi
