# FreeCam

FreeCam turns an old, weak Android phone into a home security camera that can be watched live from 3 other phones anywhere in the world, with a rolling 7-day history. It is free, has no ads, no analytics, no accounts, and no third-party SDKs.

## Architecture

Two apps plus a shared library, all in ONE Gradle project:

1. **FreeCam Camera** (`:camera`) - Installs on the old phone and streams its camera
2. **FreeCam Viewer** (`:viewer`) - Installs on 3 phones and shows live video, history and events
3. **common** (`:common`) - Shared config models, API contract, JSON models and helpers

## Target Hardware

- **Camera phone:** Samsung Galaxy A2 Core, Android 8.1 Go (API 27), 1 GB RAM, weak CPU
- **USB data port is broken** - NO ADB, NO cable transfer
- **Free storage:** ~10 GB for recordings
- **Viewer phones:** Ordinary modern Android phones

## Phases

- **Phase 1 (current): Tailscale mode** - Camera runs RTSP server + HTTP API. Viewers connect via Tailscale `100.x.y.z` IP
- **Phase 2 (future): Relay mode** - Camera pushes RTMP/RTMPS to MediaMTX on Oracle Cloud free VM

## Build Requirements

- Android Studio (latest)
- JDK 17+
- Gradle 8.5+
- Python 3 (for `serve-apk.sh`)

## Building

```bash
cd FreeCam
./gradlew assembleDebug
```

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

## Pinned Dependencies (gradle/libs.versions.toml)

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
| Security Crypto | 1.1.0-alpha06 |
| Media3 ExoPlayer | 1.4.1 |
| RootEncoder (pedroSG94) | 1.0.2 |
| NanoHTTPD | 2.3.1 |
| ZXing Embedded | 4.3.0 |
| ZXing Core | 3.5.2 |
| Kotlinx Serialization | 1.6.3 |
| Kotlinx Coroutines | 1.8.0 |
| Timber | 5.0.1 |

## Camera App Features (Phase 1)

- Hardware H.264 encoding via RootEncoder
- RTSP server on port 8554 (`rtsp://<ip>:8554/live`)
- HTTP API on port 8080 with Bearer token auth
- Foreground service with wake lock + Wi-Fi lock
- Auto-restart on boot / encoder crash (exponential backoff)
- Nightly encoder restart at 04:00
- Recording modes: Off / Motion-only / Continuous (5-min segments)
- Storage management: 7-day retention + 6 GB cap (configurable)
- Thumbnail generation (320px wide, frame at 1s)
- Motion detection from low-res luma frames (3 fps)
- ntfy push notifications (configurable server/topic)
- QR pairing with Tailscale IP auto-detection
- Dim screen mode (black screen with clock)
- Battery optimization exemption request

## Viewer App Features

- Camera list with online status (ping every 15s)
- Add camera via QR scan or manual entry
- Live RTSP playback via ExoPlayer (Media3) with auto-reconnect
- History: 7-day chips → recordings list with thumbnails
- Playback: seek bar, 10s skip, 1x/2x/4x speed
- Events: motion events grouped by day with thumbnails
- Settings: theme, ntfy topic/server per camera, open ntfy app
- EncryptedSharedPreferences for tokens

## Security

- No cloud, no accounts, no analytics
- Only outbound traffic: ntfy POST (optional) + Phase 2 relay push
- Cleartext HTTP/RTSP only inside Tailscale (encrypted overlay)
- Tokens never logged or exposed in notifications
- Minimal permissions: camera, foreground service, internet, network state, wake lock, boot completed, notifications, battery optimization exemption

## Testing Checklist

- [ ] Build both APKs with `./gradlew assembleDebug`
- [ ] Lint passes: `./gradlew lintDebug`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest`
- [ ] Camera app: Start streaming, verify RTSP in VLC on same Wi-Fi
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

## License

MIT - Free, open source, no restrictions.