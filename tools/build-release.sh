#!/bin/bash
# build-release.sh - Build signed release APKs for both camera and viewer apps
# Usage: ./build-release.sh [version_code] [version_name]
# Requires local.properties with signing config or environment variables

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

VERSION_CODE=${1:-1}
VERSION_NAME=${2:-"1.0.0"}

echo "Building FreeCam release APKs..."
echo "Version: $VERSION_NAME ($VERSION_CODE)"

# Check for signing config
if [ ! -f "local.properties" ]; then
    echo "ERROR: local.properties not found. Copy local.properties.example and configure signing."
    exit 1
fi

# Update version in both modules
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" camera/build.gradle
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/" camera/build.gradle
sed -i "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" viewer/build.gradle
sed -i "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/" viewer/build.gradle

# Build with Gradle
./gradlew clean assembleRelease --no-daemon

echo ""
echo "=== Build Complete ==="
echo "Camera APK: camera/build/outputs/apk/release/camera-release.apk"
echo "Viewer APK: viewer/build/outputs/apk/release/viewer-release.apk"
echo ""
echo "To verify signatures:"
echo "  apksigner verify --print-certs camera/build/outputs/apk/release/camera-release.apk"
echo "  apksigner verify --print-certs viewer/build/outputs/apk/release/viewer-release.apk"