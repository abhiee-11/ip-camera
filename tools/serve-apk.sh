#!/bin/bash
# serve-apk.sh - Serves built APKs over Wi-Fi for installation on the camera phone
# Run from the FreeCam project root directory

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$PROJECT_DIR/dist"

echo "Building APKs..."
cd "$PROJECT_DIR"
./gradlew assembleDebug

echo "Creating dist directory..."
mkdir -p "$DIST_DIR"

echo "Copying APKs..."
find "$PROJECT_DIR/camera/build/outputs/apk/debug" -name "*.apk" -exec cp {} "$DIST_DIR/freecam-camera-debug.apk" \;
find "$PROJECT_DIR/viewer/build/outputs/apk/debug" -name "*.apk" -exec cp {} "$DIST_DIR/freecam-viewer-debug.apk" \;

if [ ! -f "$DIST_DIR/freecam-camera-debug.apk" ] || [ ! -f "$DIST_DIR/freecam-viewer-debug.apk" ]; then
    echo "Error: APKs not found!"
    exit 1
fi

# Get local IP
LOCAL_IP=$(hostname -I | awk '{print $1}')
if [ -z "$LOCAL_IP" ]; then
    LOCAL_IP="localhost"
fi

echo ""
echo "=========================================="
echo "APKs ready at:"
echo "  Camera:  http://$LOCAL_IP:8000/freecam-camera-debug.apk"
echo "  Viewer:  http://$LOCAL_IP:8000/freecam-viewer-debug.apk"
echo ""
echo "Starting HTTP server on port 8000..."
echo "Open these URLs in the phone's browser to install."
echo "=========================================="
echo ""

cd "$DIST_DIR"
python3 -m http.server 8000