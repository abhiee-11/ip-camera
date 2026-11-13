#!/bin/sh
# Gradle wrapper script
# This is a minimal wrapper that downloads gradle if needed

set -e

GRADLE_VERSION="8.5"
GRADLE_DIST="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/${GRADLE_DIST}"

GRADLE_USER_HOME="${GRADLE_USER_HOME:-${HOME}/.gradle}"
WRAPPER_DIR="${GRADLE_USER_HOME}/wrapper/dists/${GRADLE_DIST}"

# Find or download gradle
if [ ! -f "${WRAPPER_DIR}/gradle-${GRADLE_VERSION}/bin/gradle" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "${WRAPPER_DIR}"
    cd "${WRAPPER_DIR}"
    curl -L -o "${GRADLE_DIST}" "${GRADLE_URL}"
    unzip -q "${GRADLE_DIST}"
fi

exec "${WRAPPER_DIR}/gradle-${GRADLE_VERSION}/bin/gradle" "$@"