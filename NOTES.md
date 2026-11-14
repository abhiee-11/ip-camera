# FreeCam Development Notes

## Decisions & Rationale

### RootEncoder Library
- Using `com.github.pedroSG94.RootEncoder:rootencoder:1.0.2` from JitPack
- Provides Camera2-based RTSP server and RTMP/RTMPS push
- Chosen for hardware H.264 encoding on weak devices
- No preview Surface required for background streaming

### NanoHTTPD for HTTP API
- Version 2.3.1 (stable, lightweight)
- Single-file server, easy to embed
- Supports Range requests for video seeking
- No dependencies

### EncryptedSharedPreferences
- Using `androidx.security:security-crypto:1.1.0-alpha06`
- AES256-GCM encryption for tokens and sensitive settings
- Falls back to plain SharedPreferences if keystore unavailable (API 23+)

### Media3 ExoPlayer
- Version 1.4.1 with RTSP extension
- Modern, actively maintained
- Supports RTSP over TCP/UDP with authentication via request headers

### ZXing (not ML Kit)
- `com.journeyapps:zxing-android-embedded:4.3.0` for scanning
- `com.google.zxing:core:3.5.2` for QR generation
- No Google Play Services dependency
- Works offline

### No Room Database
- Settings: EncryptedSharedPreferences (small, typed)
- Events: JSON file capped at 7 days (~few KB)
- Recordings: Filesystem with naming convention
- Simpler, less overhead, easier backup

### Dark Theme First
- Material Components DayNight theme
- Teal accent color
- Reduces power on OLED screens (common on viewer phones)

## Library Limitations & Workarounds

### RootEncoder RTSP Server
- **Limitation:** API may not expose low-res luma frames for motion detection
- **Workaround:** If `Camera2RTSP` doesn't provide frame callback, motion detection marked "unavailable" in UI, recording mode forced to Continuous
- **Note:** Check `RootEncoder` source for `setOnFrameCallback` or similar

### NanoHTTPD Range Requests
- **Limitation:** Basic Range support implemented manually
- **Testing:** Verify seeking works in ExoPlayer and VLC

### Media3 ExoPlayer RTSP
- **Limitation:** RTSP authentication varies by server
- **Workaround:** Bearer token via HTTP headers on RTSP SETUP (if supported) or rely on Tailscale network isolation
- **Note:** Document in README that RTSP auth depends on server implementation

### EncryptedSharedPreferences
- **Limitation:** Requires Android Keystore (API 23+)
- **Workaround:** Camera minSdk 26, so always available

### Battery Optimization
- **Limitation:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` not guaranteed
- **Workaround:** Foreground service + wake lock + Wi-Fi lock + START_STICKY + boot receiver

## Open Issues (Phase 1)

1. **Motion Detection Frames:** Need to verify RootEncoder provides low-res frame callback. If not, motion detection unavailable.
2. **RTSP Authentication:** RootEncoder RTSP server may not support auth. Document Tailscale reliance.
3. **Thumbnail Generation:** MediaMetadataRetriever may be slow on weak CPU. Consider generating only on demand.
4. **Storage Cleanup Race:** Cleanup runs while recording - need to protect currently-writing file.
5. **Nightly Restart:** AlarmManager exact alarm needs `SCHEDULE_EXACT_ALARM` permission (API 31+).

## Phase 2 Preparation

### CameraBackend Abstraction
Defined in `common/src/main/java/com/freecam/common/api/CameraBackend.kt`:
```kotlin
interface CameraBackend {
    suspend fun ping(): Boolean
    suspend fun status(): CameraStatus
    suspend fun recordings(day: String?): List<RecordingInfo>
    suspend fun events(from: Long, to: Long): List<MotionEvent>
    fun liveUrl(): String
    fun clipUrl(rec: RecordingInfo): String
    fun thumbUrl(rec: RecordingInfo): String?
    fun authHeaders(): Map<String, String>
}
```
- Phase 1: `PhoneBackend` implementation (done)
- Phase 2: `RelayBackend` implementation (talks to MediaMTX)

### Settings Already Include
- `destination_mode`: "local" | "relay" (relay disabled in Phase 1 UI)
- `relay_url`, `relay_stream_key` (to be added in Phase 2)

### Pairing Model Includes
- `mode`: "local" | "relay"
- Viewer switches backend based on this field

## Testing Notes

### Unit Tests (Common Module)
- Pairing JSON round-trip
- UrlBuilder URL construction
- StorageManager age/size cleanup logic (to be added)

### Unit Tests (Camera Module)
- Settings defaults and validation
- MotionDetector algorithm with synthetic frames
- StorageManager cleanup rules
- ApiServer auth and path traversal protection

### Manual Test Commands
```bash
# Test RTSP stream
vlc rtsp://100.64.x.x:8554/live

# Test API
curl -H "Authorization: Bearer <token>" http://100.64.x.x:8080/api/status
curl -H "Authorization: Bearer <token>" http://100.64.x.x:8080/api/recordings
curl -H "Authorization: Bearer <token>" -H "Range: bytes=0-1023" http://100.64.x.x:8080/recordings/20240101_120000.mp4
```

## 2026-11-14 — Pipeline findings and trade-offs

### Headless streaming (no attached view required)
RootEncoder 2.4.5 exposes `Camera2Base(context, useOpengl)`. With `useOpengl = false`
the camera renders straight into the encoder's MediaCodec input surface
(`prepareCamera(videoEncoder.getInputSurface(), fps)`), so the RTSP server runs with
**no view attached at all**. The earlier code built a 1x1 `OpenGlView` that was never
attached, so it never got a GL context and the stream never started.

Consequence: the "keep an activity alive with a small attached view" fallback is not
needed. The camera streams headless, survives the activity being closed, and the
preview surface is an optional extra for aiming the camera (switching to it restarts
the stream). "Dim screen" is now a near-black screen with a clock, the lowest
brightness and keep-screen-on, which is useful but no longer load-bearing.

### Motion detection is unavailable, by design
RootEncoder 2.4.5 and RTSP-Server 1.2.1 expose no camera frames to the app: there
is no frame callback and no way to attach a second ImageReader without owning the
camera session. Every "cheap" alternative (watching encoded frame sizes for spikes,
watching the RTSP byte counters) measures the encoder, not the scene, and cannot tell
motion from a static view.

Decision: **motion detection is reported as `unavailable`** and the camera records
**continuously** instead of pretending to detect motion. The dashboard says
`unavailable`; it does not show a fake count. Re-enabling motion needs either a
RootEncoder version with a frame callback or a forked camera pipeline.

### Nightly restart is off by default
The old receiver stopped the service and immediately started it again while the
static `isRunning` flag was still true, so the service came back alive but not
streaming, and the alarm never re-armed itself. The restart now stops the service
properly, waits, starts a fresh one and re-arms the alarm, but the setting is **off
by default** until it has run on the real phone without stranding the camera.

### RTSP authentication
RTSP-Server 1.2.1 supports Basic authentication: `RtspServerStreamClient.setAuthorization(user, password)`
calls `RtspServer.setAuth(...)`, and the server answers `401` with
`WWW-Authenticate: Basic realm="pedroSG94"` otherwise. The camera uses user `freecam`
and the **per-install pairing token** as the password, so the pairing QR did not have
to change and already-paired viewers keep working. Media3 plays these URLs as
`rtsp://freecam:<token>@<host>:8554/live`.

Note on exposure: the RTSP server and the HTTP API bind to all interfaces, so the
camera is reachable from any network the phone is on (the tailnet and the local
Wi-Fi). Tailscale access controls and the device's own firewall are the boundary.
There is no built-in option to bind the server to one interface only.

### MP4 recording
Recording now uses the camera library's own recorder (`startRecord`/`stopRecord` on
`Camera2Base`), which writes a real MP4 with the codec's SPS/PPS. The previous code
muxed frames it never received into a hand-built MediaFormat, which could not produce
a playable file. Segments are 5 minutes; `duration_sec` is read back from the file
with MediaMetadataRetriever.

### Release signing
The build now reads the signing values from `local.properties` or from `-P` flags and
only applies the release signing config when all four are present. The old code only
looked at Gradle project properties, so the CI job wrote the values into
`local.properties` and the release build stayed unsigned.

A keystore was generated on this machine and both release APKs were verified with
`apksigner`:

- keystore: `freecam-release.jks` (git-ignored, **back this up**)
- alias: `freecam`
- certificate: `CN=FreeCam, OU=FreeCam, O=FreeCam, L=-, ST=-, C=US`
- SHA-256: `7714a1d2bffb4289e8f9802b31e1f3fdceeea9c0ddf5c1a3b82167631be8d30d`
- password: `df93c4f7ada5406282f7` (also in `local.properties`, which is git-ignored)

The same four values are what the GitHub secrets `SIGNING_KEY` (base64 of the
keystore), `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` and `SIGNING_STORE_PASSWORD`
need. **This password is in a file that is not committed, but it is also written in
this note — change the key before it matters.**

### ntfy notifications
The camera posts to the configured ntfy topic when the stream starts and when an
error happens. The topic is the secret: whoever holds it receives the alerts. With no
topic configured nothing is sent.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-11-13 | Initial M0 skeleton |
| 1.1.0 | 2026-11-13 | M1 fix: RTSP server via pedroSG94/RTSP-Server 1.2.1 |
| 1.2.0 | 2026-11-13 | M2: HTTP API Server (NanoHTTPD) |
| 1.3.0 | 2026-11-13 | M3: Motion Detection & Recording |
| 1.4.0 | 2026-11-13 | M4: Camera UI & Settings |
| 1.5.0 | 2026-11-13 | M5: Viewer App complete |
| 1.6.0 | 2026-11-14 | Shared contract, headless encoder fix, library MP4 recording, real events, authenticated playback, graphite and amber redesign |

## Git Push Log

- **2026-11-13 13:15**: Push failed - waiting for GitHub authentication (PAT/gh/SSH). Will retry after next milestone.
- **2026-11-13 13:37**: Push timed out after M2 commit. Will retry after next milestone.
- **2026-11-13 14:28**: Push timed out after M5 commit. Will retry after next milestone.

## Future Improvements

- Add pre-roll to motion clips (circular buffer)
- HLS/DASH playback for relay mode
- Web viewer (single HTML page)
- Automatic Tailscale IP detection via Tailscale API
- Multi-camera support on single phone
- Cloud backup option (user's own S3/R2)