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

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-11-13 | Initial M0 skeleton |

## Future Improvements

- Add pre-roll to motion clips (circular buffer)
- HLS/DASH playback for relay mode
- Web viewer (single HTML page)
- Automatic Tailscale IP detection via Tailscale API
- Multi-camera support on single phone
- Cloud backup option (user's own S3/R2)