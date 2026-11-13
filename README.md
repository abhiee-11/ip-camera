# FreeCam

FreeCam turns an old, weak Android phone into a home security camera that can be watched live from other phones anywhere in the world, with a rolling history. It is free, has no ads, no analytics, no accounts, and no third-party SDKs.

## Architecture

Two apps plus a shared library, all in ONE Gradle project:

1. **FreeCam Camera** (`:camera`) - Installs on the old phone and streams its camera
2. **FreeCam Viewer** (`:viewer`) - Installs on viewer phones and shows live video, history and events
3. **common** (`:common`) - Shared config models, API contract, JSON models and helpers

## Target Hardware

- **Camera phone:** Samsung Galaxy A2 Core, Android 8.1 Go (API 27), 1 GB RAM, weak CPU
- **USB data port is broken** - NO ADB, NO cable transfer
- **Free storage:** ~10 GB for recordings
- **Viewer phones:** Ordinary modern Android phones

## Phases

- **Phase 1 (current): Tailscale mode** - Camera runs RTSP server + HTTP API. Viewers connect via Tailscale `100.x.y.z` IP
- **Phase 2 (future):** Relay mode (not yet implemented)

## Build Requirements

- Android Studio (latest) or command line with JDK 17+
- Gradle 8.13 (included in project at `gradle-8.13/`)
- Python 3 (for `serve-apk.sh`)

## Building

### Command Line (using bundled Gradle 8.13 + JDK 17)

```bash
cd FreeCam
# Use bundled Gradle + JDK 17
"java17/jdk-17.0.11+9/bin/java" -cp "gradle-8.13/gradle-8.13/lib/*" org.gradle.launcher.GradleMain assembleDebug --no-daemon
```

Or with a local Gradle installation:
```bash
cd FreeCam
./gradlew assembleDebug
```

### Build Targets

- `assembleDebug` - Debug APKs (unsigned)
- `assembleRelease` - Release APKs (requires signing config in `local.properties`)
- `lintDebug` - Run lint checks
- `testDebugUnitTest` - Run unit tests

## Installing on Camera Phone (No Cable)

The camera phone has no working USB data port. Use the provided script to serve APKs over Wi-Fi:

```bash
cd FreeCam
./tools/serve-apk.sh
```

This will:
1. Build both debug APKs
2. Copy them to `dist/` as `freecam-camera-debug.apk` and `freecam-viewer-debug.apk`
3. Start a Python HTTP server on port 8000
4. Print URLs like `http://192.168.1.50:8000/freecam-camera-debug.apk`

On the camera phone:
1. Connect to same Wi-Fi
2. Open browser and go to the printed URL
3. Allow "Install unknown apps" for the browser when prompted
4. Tap the downloaded APK to install

## Pinned Dependencies

| Library | Version |
|---------|---------|
| Android Gradle Plugin | 8.5.0 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| AndroidX Core KTX | 1.13.1 |
| AndroidX AppCompat | 1.7.0 |
| Material Components | 1.12.0 |
| Activity | 1.9.2 |
| Fragment | 1.8.3 |
| Lifecycle | 2.8.4 |
| Preference | 1.2.1 |
| Media3 ExoPlayer | 1.4.1 |
| RootEncoder library (pedroSG94) | 2.4.5 |
| RTSP-Server (pedroSG94) | 1.2.1 |
| NanoHTTPD | 2.3.1 |
| ZXing Embedded | 4.3.0 |
| ZXing Core | 3.5.2 |
| Kotlinx Serialization | 1.6.3 |
| Kotlinx Coroutines | 1.8.0 |

## Camera App Features (Phase 1)

- **RTSP Streaming**: Hardware H.264 encoding via RootEncoder + `RtspServerCamera2` from RTSP-Server 1.2.1 on port 8554 (`rtsp://<ip>:8554/live`)
- **HTTP API**: NanoHTTPD on port 8080 with Bearer token auth
  - `GET /api/status` - Camera status (streaming, recording, storage, uptime)
  - `GET /api/recordings` - List recordings with metadata
  - `GET /api/events` - Motion events log
  - `GET /recordings/<file>` - Download recording (supports Range header)
- **Foreground Service**: Wake lock + Wi-Fi lock, API 29+ `FOREGROUND_SERVICE_TYPE_CAMERA`
- **Auto-restart**: On boot (`BootReceiver`), encoder crash (exponential backoff), nightly at 04:00 (`AlarmManager`)
- **Recording Modes**: Off / Motion-only / Continuous (5-min segments, 5s pre-roll circular buffer)
- **Storage Management**: Age-based cleanup (default 7 days) + size cap (default 6 GB)
- **Motion Detection**: YUV frame difference (3 fps, low-res luma), sensitivity levels, cooldown, EMA reference frame
- **QR Pairing**: Generates pairing JSON with Tailscale IP (100.x.x.x) priority
- **Dim Screen Mode**: Black screen with clock, tap to exit
- **Battery Optimization Exemption**: Runtime request with settings deep-link

## Viewer App Features

- **Camera List**: Online status ping (every 15s), swipe to refresh, long-press menu
- **Add Camera**: QR scan (ZXing) or manual entry, parses camera QR format
- **Live RTSP Playback**: Media3 ExoPlayer 1.4.1 with auto-reconnect, custom controller
- **History**: Day chips → recordings list with thumbnails, tap to play
- **Playback Controls**: Seek bar, 10s skip back/forward, 1x/2x/4x speed
- **Events**: Motion events grouped by day with thumbnails, tap to play recording
- **Settings**: Auto-connect, screen-on, HW decoding, low latency, SSL verify, cache clear
- **Secure Storage**: Tokens stored in SharedPreferences (no EncryptedSharedPreferences dependency)

## Security

- No cloud, no accounts, no analytics, no third-party SDKs
- Only outbound traffic: optional ntfy POST notifications
- Cleartext HTTP/RTSP only inside Tailscale (encrypted overlay)
- Tokens never logged or exposed in notifications
- Minimal permissions: camera, foreground service (camera), internet, network state, wake lock, boot completed, notifications, battery optimization exemption

## Project Structure

```
FreeCam/
├── .github/workflows/ci.yml       # GitHub Actions CI/CD
├── common/                        # Shared library
│   └── src/main/java/com/freecam/common/
│       ├── model/                 # Pairing, CameraStatus, RecordingInfo, MotionEvent
│       └── util/                  # TimeFormat, Bytes, PairingParser
├── camera/                        # Camera app
│   ├── src/main/
│   │   ├── java/com/freecam/camera/
│   │   │   ├── service/           # CameraService, StreamController, ApiServer, NightlyRestartReceiver, BootReceiver
│   │   │   ├── motion/            # MotionDetector
│   │   │   ├── recording/         # RecordingManager
│   │   │   ├── storage/           # StorageManager
│   │   │   ├── data/              # Settings
│   │   │   └── ui/                # MainActivity, SettingsActivity, PairingActivity, LogsActivity, DimScreenActivity
│   │   ├── res/                   # Layouts, drawables, values, xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── viewer/                        # Viewer app
│   ├── src/main/
│   │   ├── java/com/freecam/viewer/
│   │   │   ├── network/           # ApiClient, RecordingInfo
│   │   │   └── ui/                # MainActivity, PlayerActivity, HistoryActivity, EventsActivity, ViewerSettingsActivity, AddCameraActivity
│   │   ├── res/                   # Layouts, drawables, values, xml, menu
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── tools/
│   ├── serve-apk.sh               # Wi-Fi APK server
│   └── build-release.sh           # Signed release build script
├── local.properties.example       # Signing config template
├── settings.gradle                # Module includes
├── build.gradle                   # Root buildscript (Kotlin 1.9.24)
├── gradle-8.13/                   # Bundled Gradle 8.13
├── java17/                        # Bundled Temurin JDK 17
└── NOTES.md                       # Decisions & push log
```

## Testing Checklist

- [ ] Build both APKs: `./gradlew assembleDebug`
- [ ] Lint passes: `./gradlew lintDebug`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest`
- [ ] Camera app: Start streaming, verify RTSP in VLC (`rtsp://<phone-ip>:8554/live`)
- [ ] Camera app: Screen off, verify stream continues
- [ ] Camera app: Reboot phone, verify auto-start
- [ ] Camera app: Wi-Fi drop + return, verify reconnection
- [ ] Camera app: Fill storage to cap, verify cleanup
- [ ] Camera app: Reset token, verify re-pairing needed
- [ ] Viewer app: Scan QR, test connection, save
- [ ] Viewer app: Live view, rotate, background/foreground
- [ ] Viewer app: History playback with seek/skip/speed
- [ ] Viewer app: Events list with playback
- [ ] 24-hour continuous run test

## CI/CD (M9)

GitHub Actions workflow at `.github/workflows/ci.yml`:
- **CI**: On every push/PR to main - builds debug, runs lint, runs unit tests, uploads APKs as artifacts
- **Release**: On push to main - builds signed release APKs (requires signing secrets), creates GitHub Release

### Release Signing Setup

1. Generate a keystore:
   ```bash
   keytool -genkeypair -alias freecam -keyalg RSA -keysize 2048 -validity 10000 -keystore signing.jks
   ```

2. Add GitHub repository secrets:
   - `SIGNING_KEY` - base64 encoded `signing.jks`
   - `SIGNING_KEY_ALIAS` - key alias (e.g., `freecam`)
   - `SIGNING_KEY_PASSWORD` - key password
   - `SIGNING_STORE_PASSWORD` - keystore password

3. For local release builds, copy `local.properties.example` to `local.properties` and fill in values.

4. Build locally:
   ```bash
   ./tools/build-release.sh 1 "1.0.0"
   ```

## License

MIT - Free, open source, no restrictions.