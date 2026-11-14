# FreeCam UI Redesign + State Contract — Implementation Plan

> **Status (2026-11-14): implemented.** All tasks are done and committed locally. The
> order was changed on request: the pipeline work (stream, muxer, auth, events) went
> first, the design system and screens after. Two things changed as a result and are
> recorded in NOTES.md: the encoder runs **headless** (RootEncoder 2.4.5 supports a
> viewless mode, so no activity has to stay alive), and **motion detection is
> unavailable** (the library exposes no camera frames), so the camera records
> continuously and the UI says so. On-device verification is still outstanding: the
> camera phone has no ADB, and the viewer phone was in use by another session.
> `docs/superpowers/plans/2026-11-14-manual-device-checks.md` has the checks.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild both FreeCam apps on a graphite-and-amber "appliance" design system, and back them with a real state contract so every number on screen is true.

**Architecture:** `:common` becomes the single owner of the HTTP contract (serializable models, host selection, URL builders, event-log parsing) and is where the JVM unit tests live. `:camera` keeps a `CameraStateStore` that publishes one immutable snapshot of live state, serves it over the existing NanoHTTPD API in the shared JSON shape, and renders a redesigned dashboard whose telemetry is bound to that snapshot. `:viewer` consumes the same shared models, builds authenticated RTSP/HTTP URLs, and renders a redesigned camera list and player.

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.0, Gradle 8.13 (bundled) + JDK 17 (bundled), XML layouts + ViewBinding/DataBinding, Material Components 1.12, kotlinx-serialization 1.6.3, NanoHTTPD 2.3.1, RootEncoder 2.4.5 + RTSP-Server 1.2.1, Media3 1.4.1, OkHttp 4.12.0, Glide 4.16.0 (viewer, already present), JUnit 4.13.2 (`:common` tests).

**Spec:** `docs/superpowers/specs/2026-11-14-freecam-ui-redesign-design.md`

## Global Constraints

- Two apps plus a library in one Gradle project; modules `:camera`, `:viewer`, `:common`.
- minSdk 26, targetSdk 34, compileSdk 34, Java 8 source/target, Kotlin jvmTarget 1.8.
- Real build files are the Groovy ones (`camera/build.gradle`, `viewer/build.gradle`, `common/build.gradle`). The `*.gradle.kts` files are stale duplicates referencing a non-existent version catalog and are deleted in Task 1.
- No new third-party dependencies. No bundled font files. No Compose. No light theme.
- Camera phone target: Galaxy A2 Core, Android 8.1 Go, 1 GB RAM, screen ≈ 320×569 dp. Viewer test device: vivo V2434, Android 16, ≈ 384×857 dp.
- Palette (both apps, identical values): `carbon #101317`, `panel #171B21`, `rule #232A33`, `sodium #F2A33C`, `tape #E5484D`, `phosphor #58C7F3`, `bone #E9E7E2`, `mute #8B9199`. The old teal tokens are deleted.
- Type: system `sans-serif` everywhere; system `monospace` only for machine values (IP, port, token, uptime, bitrate, sizes, timestamps). Sizes: screen title 20sp/600, app-bar name 15sp/600, primary action 16sp/600, readout value 14sp, label 13sp `mute`, meta 12.5sp, mono data 12sp, clock 26sp mono.
- Spacing: 4dp grid, 16dp screen padding, 8dp group gap, 12dp tile gap, 1dp rules, radius 8dp buttons/tiles, 6dp chips/thumbnails, 5dp badges, touch targets ≥ 48dp, no elevation, no shadows, no gradients.
- No fabricated numbers: unknown is `—`; configured values are labelled `configured`; empty collections hide their strip rather than showing zeros.
- RTSP credentials: user `freecam`, password = the camera's existing per-install random token. Live URL: `rtsp://freecam:<token>@<host>:8554/live`. The pairing QR payload does **not** change.
- HTTP playback must send `Authorization: Bearer <token>`.
- API error envelope: `{"error":{"code":"<snake_case>","message":"<human sentence>"}}`.
- Build command (bundled toolchain, run from the project root):
  `& "java17/jdk-17.0.11+9/bin/java.exe" -cp "gradle-8.13/gradle-8.13/lib/*" org.gradle.launcher.GradleMain assembleDebug lintDebug testDebugUnitTest --no-daemon`
  Expected: `BUILD SUCCESSFUL` for all three tasks.
- Scope boundary: frame delivery to motion detection, MP4 recording, event *writing*, ntfy push and release signing are **out of this plan** and belong to the pipeline plan. The encoder must nevertheless actually start (that is Task 7) because the dashboard's `REC` badge claims it is live.

## Review Focus

Each line below is an input the spec implies but no screen spec pins down. The test named for it is added to the task that owns the code.

1. **Camera on plain Wi-Fi with no tailnet address** → `host` falls back to the LAN IP, the Tailnet row reads `not on tailnet`, and pairing over LAN still works. (Test: `HostAddressTest.selectHost_prefersTailnetThenLan`.)
2. **Battery percentage unavailable** (some devices report nothing) → the readout shows `—`, never `0%`. (Test: `CameraStatusTest.batteryPercentNullSerializesAsNull`.)
3. **No events recorded yet** → the viewer's status strip is hidden entirely, not rendered as `0 motion events`. (Test: `EventLogParserTest.emptyOrMissingFileYieldsEmptyList`.)
4. **Playback requested while the camera is offline** → the player shows a named fault (`Camera is offline`) with a `Retry` action, not a silent black screen. (Test: `StreamUrlsTest.httpPlaybackUrlKeepsPathAndHost` plus manual device check in Task 9.)
5. **Font scale 1.3 on a 320 dp-wide screen** → telemetry scrolls, state block and primary action stay fully visible and unclipped. (Manual: Task 11 step 5.)

---

### Task 1: Shared contract models in `:common`

**Files:**
- Delete: `camera/build.gradle.kts`, `viewer/build.gradle.kts`, `common/build.gradle.kts` (stale duplicates; Gradle uses the Groovy files)
- Rewrite: `common/src/main/java/com/freecam/common/model/CameraStatus.kt`
- Create: `common/src/main/java/com/freecam/common/model/ApiError.kt`
- Rewrite: `common/src/main/java/com/freecam/common/model/RecordingInfo.kt`
- Rewrite: `common/src/main/java/com/freecam/common/model/MotionEvent.kt`
- Modify: `common/src/main/java/com/freecam/common/model/Pairing.kt`
- Create: `common/src/main/java/com/freecam/common/api/ApiPaths.kt`
- Create: `common/src/test/java/com/freecam/common/model/CameraStatusTest.kt`
- Create: `common/src/test/java/com/freecam/common/model/RecordingInfoTest.kt`
- Create: `common/src/test/java/com/freecam/common/model/MotionEventTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class ApiError(val code: String, val message: String)`
  - `data class TailnetState(val connected: Boolean, val ip: String?)`
  - `data class BatteryState(val percent: Int?, val charging: Boolean)`
  - `data class StorageState(val usedBytes: Long, val freeBytes: Long, val capBytes: Long)`
  - `data class MotionState(val enabled: Boolean, val sensitivity: String, val eventsToday: Int, val available: Boolean)`
  - `data class CameraStatus(running: Boolean, recording: Boolean, uptimeMs: Long, cameraName: String, resolution: String, fps: Int, bitrateKbps: Int, rtspUrl: String, apiUrl: String, version: String, host: String?, tailnet: TailnetState, battery: BatteryState, storage: StorageState, motion: MotionState, recordingMode: String, error: ApiError?)` — all `@Serializable`, JSON names pinned in Task 1 step 2.
  - `data class RecordingInfo(filename: String, path: String, size: Long, startTime: Long, durationSec: Int)` — JSON `filename, path, size, start_time, duration_sec`.
  - `data class MotionEvent(id: String, ts: Long, severity: String, recordingPath: String?, thumbnail: String?)` — JSON `id, ts, severity, recording_path, thumbnail`.
  - `data class Pairing(v: Int = 1, name: String, host: String, rtspPort: Int = 8554, httpPort: Int = 8080, path: String = "live", token: String, mode: String = "local", rtspUser: String = "freecam")`
  - `object ApiPaths { const val STATUS = "/api/status"; const val SNAPSHOT = "/api/snapshot"; const val RECORDINGS = "/api/recordings"; const val EVENTS = "/api/events"; const val RECORDING_FILE = "/recordings/" }`

- [ ] **Step 1: Delete the stale `*.gradle.kts` files**

```powershell
Remove-Item camera\build.gradle.kts, viewer\build.gradle.kts, common\build.gradle.kts
```

- [ ] **Step 2: Write the failing test for the status contract**

Create `common/src/test/java/com/freecam/common/model/CameraStatusTest.kt`:

```kotlin
package com.freecam.common.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStatusTest {

    private val json = Json { encodeDefaults = true; explicitNulls = true }

    private fun sample() = CameraStatus(
        running = true, recording = false, uptimeMs = 15_877_000L, cameraName = "Front door",
        resolution = "640x480", fps = 15, bitrateKbps = 500,
        rtspUrl = "rtsp://freecam:abc123@100.64.0.5:8554/live",
        apiUrl = "http://100.64.0.5:8080", version = "1.1.0", host = "100.64.0.5",
        tailnet = TailnetState(true, "100.64.0.5"),
        battery = BatteryState(87, true),
        storage = StorageState(4_200_000_000L, 1_800_000_000L, 6_000_000_000L),
        motion = MotionState(true, "medium", 3, false),
        recordingMode = "motion", error = null
    )

    @Test fun encodesTheExactWireShape() {
        val encoded = json.encodeToString(sample())
        assertTrue(encoded.contains("\"uptime_ms\":15877000"))
        assertTrue(encoded.contains("\"camera_name\":\"Front door\""))
        assertTrue(encoded.contains("\"bitrate_kbps\":500"))
        assertTrue(encoded.contains("\"tailnet\":{\"connected\":true,\"ip\":\"100.64.0.5\"}"))
        assertTrue(encoded.contains("\"battery\":{\"percent\":87,\"charging\":true}"))
        assertTrue(encoded.contains("\"storage\":{\"used_bytes\":4200000000,\"free_bytes\":1800000000,\"cap_bytes\":6000000000}"))
        assertTrue(encoded.contains("\"motion\":{\"enabled\":true,\"sensitivity\":\"medium\",\"events_today\":3,\"available\":false}"))
        assertTrue(encoded.contains("\"recording_mode\":\"motion\""))
        assertTrue(encoded.contains("\"error\":null"))
    }

    @Test fun batteryPercentNullSerializesAsNull() {
        val encoded = json.encodeToString(sample().copy(battery = BatteryState(null, false)))
        assertTrue(encoded.contains("\"battery\":{\"percent\":null,\"charging\":false}"))
    }

    @Test fun roundTrips() {
        assertEquals(sample(), json.decodeFromString<CameraStatus>(json.encodeToString(sample())))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `& "java17/jdk-17.0.11+9/bin/java.exe" -cp "gradle-8.13/gradle-8.13/lib/*" org.gradle.launcher.GradleMain :common:testDebugUnitTest --no-daemon --tests "com.freecam.common.model.CameraStatusTest"`
Expected: FAIL — compilation error, `CameraStatus` unresolved.

- [ ] **Step 4: Implement the models**

`CameraStatus.kt` defines `@Serializable data class CameraStatus` with the fields listed in the Interfaces block, each with `@SerialName` where the JSON name differs from the property name: `uptimeMs` → `"uptime_ms"`, `cameraName` → `"camera_name"`, `bitrateKbps` → `"bitrate_kbps"`, `rtspUrl` → `"rtsp_url"`, `apiUrl` → `"api_url"`. `TailnetState`, `BatteryState`, `StorageState` and `MotionState` live in the same file as top-level `@Serializable` classes with the field names pinned in the test: `StorageState` uses `used_bytes`/`free_bytes`/`cap_bytes`, `MotionState` uses `events_today`/`available`.

`ApiError.kt` holds `@Serializable data class ApiError(val code: String, val message: String)` with no `@SerialName` (camelCase is fine inside the envelope: `{"error":{"code":"not_streaming","message":"..."}}`).

`RecordingInfo.kt` and `MotionEvent.kt` are rewritten with `@SerialName("start_time")`, `@SerialName("duration_sec")`, `@SerialName("recording_path")`; `thumbnail` and `recordingPath` are nullable.

`Pairing.kt` gains `rtspUser: String = "freecam"` (the password is the existing `token`, so no new secret and no QR format change).

`ApiPaths.kt` holds the five path constants above.

- [ ] **Step 5: Run the test to verify it passes**

Run: the `:common:testDebugUnitTest` command from step 3.
Expected: PASS, 3 tests.

- [ ] **Step 6: Add the recording/event model tests and run them**

`RecordingInfoTest` asserts `json.encodeToString(RecordingInfo("a.mp4", "/recordings/a.mp4", 10L, 5L, 30))` contains `"start_time":5` and `"duration_sec":30` and `"size":10`. `MotionEventTest` asserts the encoded string contains `"recording_path":"/recordings/a.mp4"` and round-trips with a `null` `thumbnail`. Run `:common:testDebugUnitTest` again. Expected: PASS, 5 tests total.

- [ ] **Step 7: Commit**

```powershell
git add common camera/build.gradle.kts viewer/build.gradle.kts
git commit -m "feat(common): single contract models for status, recordings and events"
```

---

### Task 2: Host selection and URL builders in `:common`

**Files:**
- Create: `common/src/main/java/com/freecam/common/util/HostAddress.kt`
- Create: `common/src/main/java/com/freecam/common/util/StreamUrls.kt`
- Create: `common/src/test/java/com/freecam/common/util/HostAddressTest.kt`
- Create: `common/src/test/java/com/freecam/common/util/StreamUrlsTest.kt`

**Interfaces:**
- Consumes: `Pairing` (Task 1).
- Produces:
  - `data class HostChoice(val host: String?, val tailnetIp: String?)`
  - `fun isTailnetAddress(ip: String): Boolean` — true for `100.64.0.0/10` only (first octet `100`, second octet 64–127).
  - `fun selectHost(addresses: List<String>): HostChoice` — tailnet address wins; otherwise the first non-loopback IPv4 that is not a tailnet address is used as LAN host; `host` is `null` when the list holds nothing usable.
  - `fun buildRtspUrl(host: String, port: Int, path: String, user: String, token: String): String` → `rtsp://<user>:<token>@<host>:<port>/<path>`
  - `fun buildHttpUrl(host: String, port: Int, path: String): String` → `http://<host>:<port><path>` (path keeps its leading slash)

- [ ] **Step 1: Write the failing host-selection test**

Create `common/src/test/java/com/freecam/common/util/HostAddressTest.kt`:

```kotlin
package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAddressTest {

    @Test fun tailnetRangeIsExactlyTheCgnatBlock() {
        assertTrue(isTailnetAddress("100.64.0.5"))
        assertTrue(isTailnetAddress("100.127.255.254"))
        assertFalse(isTailnetAddress("100.63.255.254"))
        assertFalse(isTailnetAddress("100.128.0.1"))
        assertFalse(isTailnetAddress("192.168.1.5"))
    }

    @Test fun selectHostPrefersTailnetThenLan() {
        assertEquals(HostChoice("100.64.0.5", "100.64.0.5"),
            selectHost(listOf("192.168.1.20", "100.64.0.5")))
        assertEquals(HostChoice("192.168.1.20", null),
            selectHost(listOf("192.168.1.20", "10.0.0.3")))
        assertEquals(HostChoice(null, null), selectHost(listOf("127.0.0.1")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `... org.gradle.launcher.GradleMain :common:testDebugUnitTest --no-daemon --tests "com.freecam.common.util.HostAddressTest"`
Expected: FAIL — `isTailnetAddress` unresolved.

- [ ] **Step 3: Implement `HostAddress.kt`**

`isTailnetAddress` splits on `.`, parses the first two octets, returns `octets[0] == 100 && octets[1] in 64..127`, and returns `false` for malformed input. `selectHost` walks the list once: the first tailnet address becomes both `host` and `tailnetIp`; the first other address becomes the LAN fallback for `host` only when no tailnet address was found; loopback and malformed addresses are skipped; with nothing usable, both fields are `null`.

- [ ] **Step 4: Write and run the URL builder test**

Create `common/src/test/java/com/freecam/common/util/StreamUrlsTest.kt`:

```kotlin
package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlsTest {

    @Test fun rtspUrlCarriesCredentialsAndPath() {
        assertEquals("rtsp://freecam:abc123@100.64.0.5:8554/live",
            buildRtspUrl("100.64.0.5", 8554, "live", "freecam", "abc123"))
    }

    @Test fun httpPlaybackUrlKeepsPathAndHost() {
        assertEquals("http://100.64.0.5:8080/recordings/a.mp4",
            buildHttpUrl("100.64.0.5", 8080, "/recordings/a.mp4"))
    }
}
```

Run `:common:testDebugUnitTest`. Expected: PASS, 5 tests total.

- [ ] **Step 5: Commit**

```powershell
git add common
git commit -m "feat(common): tailnet-first host selection and authenticated stream URLs"
```

---

### Task 3: Event log parsing and API path constants in `:common`

**Files:**
- Create: `common/src/main/java/com/freecam/common/util/EventLogParser.kt`
- Create: `common/src/test/java/com/freecam/common/util/EventLogParserTest.kt`

**Interfaces:**
- Consumes: `MotionEvent` (Task 1).
- Produces: `fun parseEventLog(json: String?): List<MotionEvent>` — `null`/blank/malformed input yields an empty list; entries lacking `id` get a UUID; `ts` defaults to `0`; the result is sorted by `ts` descending.

- [ ] **Step 1: Write the failing test**

Create `common/src/test/java/com/freecam/common/util/EventLogParserTest.kt`:

```kotlin
package com.freecam.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogParserTest {

    @Test fun emptyOrMissingFileYieldsEmptyList() {
        assertTrue(parseEventLog(null).isEmpty())
        assertTrue(parseEventLog("").isEmpty())
        assertTrue(parseEventLog("not json").isEmpty())
        assertTrue(parseEventLog("[]").isEmpty())
    }

    @Test fun parsesAndSortsNewestFirst() {
        val json = """[{"id":"a","ts":100,"severity":"low"},{"id":"b","ts":200,"severity":"high"}]"""
        val events = parseEventLog(json)
        assertEquals(listOf("b", "a"), events.map { it.id })
        assertEquals("high", events.first().severity)
    }

    @Test fun toleratesEntriesMissingFields() {
        val events = parseEventLog("""[{"ts":5}]""")
        assertEquals(1, events.size)
        assertEquals(5, events.first().ts)
        assertTrue(events.first().id.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `... :common:testDebugUnitTest --no-daemon --tests "com.freecam.common.util.EventLogParserTest"`
Expected: FAIL — `parseEventLog` unresolved.

- [ ] **Step 3: Implement `EventLogParser.kt`**

`parseEventLog` returns `emptyList()` for null/blank input. Otherwise it decodes with `Json { ignoreUnknownKeys = true }` into `List<MotionEvent>` inside a `runCatching`; on failure it returns `emptyList()`. Entries whose `id` is blank get `UUID.randomUUID().toString()`. The list is sorted by `ts` descending. Because `MotionEvent.id` is non-nullable, a missing `id` decodes as an empty string and is replaced afterwards.

- [ ] **Step 4: Run the test to verify it passes**

Run: `:common:testDebugUnitTest`.
Expected: PASS, 8 tests total.

- [ ] **Step 5: Commit**

```powershell
git add common
git commit -m "feat(common): tolerant event log parsing"
```

---

### Task 4: Camera state store and service lifecycle

**Files:**
- Create: `camera/src/main/java/com/freecam/camera/data/CameraStateStore.kt`
- Modify: `camera/src/main/java/com/freecam/camera/data/Settings.kt`
- Modify: `camera/src/main/java/com/freecam/camera/service/CameraService.kt`
- Modify: `camera/src/main/java/com/freecam/camera/service/NightlyRestartReceiver.kt`
- Modify: `camera/src/main/java/com/freecam/camera/service/BootReceiver.kt`

**Interfaces:**
- Consumes: `CameraStatus`, `TailnetState`, `BatteryState`, `StorageState`, `MotionState`, `ApiError` (Task 1).
- Produces:
  - `data class CameraUiState(running: Boolean, recording: Boolean, uptimeMs: Long, cameraName: String, resolution: String, fps: Int, bitrateKbps: Int, rtspUrl: String, apiUrl: String, host: String?, tailnetConnected: Boolean, tailnetIp: String?, batteryPercent: Int?, batteryCharging: Boolean, storageUsedBytes: Long, storageFreeBytes: Long, storageCapBytes: Long, motionEnabled: Boolean, motionSensitivity: String, motionAvailable: Boolean, eventsToday: Int, recordingMode: String, errorMessage: String?, errorCode: String?)`
  - `object CameraStateStore { @Volatile var state: CameraUiState; fun publish(next: CameraUiState) }`
  - `CameraService.ACTION_RESTART_CAMERA`, `CameraService.ACTION_RESET_TOKEN`
  - `CameraService.snapshot(): CameraUiState` — static accessor used by the UI.

- [ ] **Step 1: Add the state store**

Create `camera/src/main/java/com/freecam/camera/data/CameraStateStore.kt` holding the `CameraUiState` data class and the `CameraStateStore` object with a `@Volatile var state` initialised to an all-default instance (running false, empty strings, null host, null battery percent, zero sizes, motion unavailable).

- [ ] **Step 2: Fix the service lifecycle and publish state**

In `CameraService`:
- `onDestroy()` sets `isRunning = false` and clears the notification — this is the bug that makes the nightly restart a no-op today.
- `onStartCommand` handles the new actions: `ACTION_RESTART_CAMERA` stops the stream components and calls `startStreaming()` again (uptime resets, the notification refreshes); `ACTION_RESET_TOKEN` calls `settings.resetToken()` and updates the notification text.
- A `publishState()` method builds a `CameraUiState` from `settings`, the running flag, `uptimeMillis`, `BatteryManager` (percent `null` when the level is unavailable; `charging` from `BatteryManager.EXTRA_PLUGGED` or the charging intent), `StatFs` on `getExternalFilesDir(null)` for free space, `StorageManager.getStorageStats()` for used bytes and the configured cap, the event-log file parsed with `parseEventLog` for `eventsToday`, and the current error, then calls `CameraStateStore.publish(...)`.
- `publishState()` is called from `updateUI`-equivalent paths: after start, after stop, after each recording/motion callback, from the existing 1-second uptime loop (every 5th tick to limit battery drain), and on every error.
- `getLocalIpAddress()` is replaced by a `resolveHost()` helper that enumerates `NetworkInterface.getNetworkInterfaces()`, collects non-loopback IPv4 addresses, and calls `selectHost(...)` from `:common` (Task 2). The chosen host builds `rtspUrl` and `apiUrl`.
- `getEvents(from, to)` returns the parsed event list (read path) instead of an empty list.

- [ ] **Step 3: Make the nightly restart actually work**

In `NightlyRestartReceiver`: stop the service with `context.stopService(...)` (not an action intent, which is ignored), then post a `startForegroundService` intent with `ACTION_START` after a 500 ms delay via `Handler(Looper.getMainLooper())`. `CameraService.scheduleNightlyRestart()` is called again from `startStreaming()` so the alarm re-arms for the next day, and it uses `setExactAndAllowWhileIdle` where permitted, falling back to `set()`.

In `BootReceiver`: keep the existing behaviour, but guard with `settings.autoStart` and, on API 26+, use `startForegroundService` (already the case).

- [ ] **Step 4: Add the RTSP credentials to settings**

In `Settings`, replace the hardcoded `rtspPassword` constant with a property that returns the per-install token:

```kotlin
val rtspUser: String get() = "freecam"
val rtspPassword: String get() = token
```

(The token is already random per install and already travels in the pairing QR.)

- [ ] **Step 5: Build and check**

Run: `... GradleMain assembleDebug :common:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, `:common` tests still 8 passing.

- [ ] **Step 6: Commit**

```powershell
git add camera
git commit -m "feat(camera): publish one live state snapshot and fix service restart lifecycle"
```

---

### Task 5: Camera HTTP API on the shared contract

**Files:**
- Modify: `camera/src/main/java/com/freecam/camera/http/ApiServer.kt`
- Modify: `camera/src/main/java/com/freecam/camera/service/CameraService.kt`
- Create: `camera/src/main/java/com/freecam/camera/http/ApiResponses.kt`

**Interfaces:**
- Consumes: `CameraStatus`, `RecordingInfo`, `MotionEvent`, `ApiPaths`, `parseEventLog` (Tasks 1, 3), `CameraStateStore` (Task 4).
- Produces:
  - `object ApiResponses { fun error(code: String, message: String): String; fun json(value: String): String }` — `error` returns `{"error":{"code":"...","message":"..."}}` with the message JSON-escaped.
  - `ApiServer` gains `onSnapshot: () -> ByteArray?` (null until the pipeline provides frames) and `onStatus: () -> CameraStatus` (replacing the hand-built JSON string).

- [ ] **Step 1: Change the ApiServer constructor and error handling**

`ApiServer` now takes `onStatus: () -> CameraStatus` and `onSnapshot: () -> ByteArray?` in addition to the existing callbacks. `authenticate` stays as-is (Bearer token). Every non-2xx response body is produced by `ApiResponses.error(...)`: `unauthorized` → code `unauthorized`, message `Missing or invalid bearer token`; `not_found` → `not_found`, message `Unknown endpoint`; forbidden → `path_traversal`, message `Invalid file name`; server error → `server_error`, message `Unexpected server error`.

- [ ] **Step 2: Implement the four endpoints on the shared shapes**

- `/api/status` → `Json.encodeToString(onStatus())`.
- `/api/recordings` → `Json.encodeToString(storageManager.getRecordings())` using the shared `RecordingInfo` (path stays `/recordings/`).
- `/api/events` → `Json.encodeToString(parseEventLog(eventLogJson))`, filtered to the `from`/`to` window when parameters are present.
- `/api/snapshot` → when `onSnapshot()` returns bytes, respond `image/jpeg` with `Content-Type: image/jpeg`; otherwise respond `404` with the envelope `{"error":{"code":"not_streaming","message":"No frame is available yet"}}`.

- [ ] **Step 3: Stream recording files instead of loading them into memory**

Replace the whole-file `readText`-style read in `handleRecordingFile` with a bounded streaming read: honour the `Range` header, and use NanoHTTPD's `newChunkedResponse(Status.OK, MIME_MP4, inputStream)` for a full-file request, so a 40 MB clip does not become a 40 MB `String` in RAM on a 1 GB device. Keep the path-traversal rejection exactly as it is.

- [ ] **Step 4: Wire the service**

`CameraService` constructs `ApiServer` with `onStatus = { buildCameraStatus() }`, where `buildCameraStatus()` maps the current `CameraStateStore.state` into the shared `CameraStatus` (including `tailnet`, `battery`, `storage`, `motion` sub-objects and `error` = `ApiError(code, message)` or `null`), and `onSnapshot = { null }` until the pipeline lands.

- [ ] **Step 5: Build**

Run: `... GradleMain assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add camera
git commit -m "feat(camera): serve the shared status, event and snapshot contract over HTTP"
```

---

### Task 6: Design tokens, theme and shared drawables

**Files:**
- Rewrite: `camera/src/main/res/values/colors.xml`, `viewer/src/main/res/values/colors.xml`
- Rewrite: `camera/src/main/res/values/styles.xml`, `viewer/src/main/res/values/styles.xml`
- Create: `camera/src/main/res/values/dimens.xml`, `viewer/src/main/res/values/dimens.xml`
- Create: `camera/src/main/res/drawable/bg_tile.xml`, `bg_badge_tape.xml`, `bg_badge_mute.xml`, `bg_banner_fault.xml`, `bg_chip_live.xml`, `bg_rail_button.xml`
- Create: `viewer/src/main/res/drawable/bg_tile.xml`, `bg_banner_fault.xml`, `bg_chip_live.xml`, `bg_rail_button.xml`, `bg_thumb_placeholder.xml`
- Create: `camera/src/main/res/drawable/ic_sliders.xml`, `ic_overflow.xml`, `ic_preview.xml`, `ic_qr.xml`
- Create: `viewer/src/main/res/drawable/ic_scan.xml`, `ic_history.xml`, `ic_events.xml`, `ic_fullscreen.xml`
- Modify: `camera/src/main/AndroidManifest.xml`, `viewer/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: colour, dimension and style names used by every later layout task:
  - Colours: `carbon`, `panel`, `rule`, `sodium`, `tape`, `phosphor`, `bone`, `mute`, `on_accent` (`#14181D`).
  - Dimensions: `space_screen` 16dp, `space_group` 8dp, `space_tile` 12dp, `rule_width` 1dp, `radius_button` 8dp, `radius_chip` 6dp, `radius_badge` 5dp, `radius_thumb` 2dp, `touch_min` 48dp, `text_title` 20sp, `text_name` 15sp, `text_action` 16sp, `text_value` 14sp, `text_label` 13sp, `text_meta` 12.5sp, `text_mono` 12sp, `text_clock` 26sp.
  - Styles: `Theme.FreeCamCamera` (dark only, parent `Theme.MaterialComponents.DayNight.NoActionBar`, `colorPrimary` = `sodium`, `colorOnPrimary` = `on_accent`, `colorSecondary` = `phosphor`, `colorSurface` = `panel`, `android:colorBackground` = `carbon`, `colorOnSurface` = `bone`, `colorError` = `tape`), the same for `Theme.FreeCamViewer`, plus `Widget.FreeCam.PrimaryButton`, `Widget.FreeCam.DangerButton`, `Widget.FreeCam.Tile`, `Widget.FreeCam.SectionLabel`, `Widget.FreeCam.ReadoutLabel`, `Widget.FreeCam.ReadoutValue`, `Widget.FreeCam.ReadoutValueMono`, `Widget.FreeCam.AppBar`, `Widget.FreeCam.Chip`.
  - Layouts no longer use a `colorPrimary` toolbar or an elevated `CardView`.

- [ ] **Step 1: Rewrite both `colors.xml` files with identical values**

Both files contain exactly the nine colours listed above plus `on_accent`. Delete every teal-era colour name.

- [ ] **Step 2: Write the theme and component styles**

`Theme.FreeCamCamera` and `Theme.FreeCamViewer` both extend `Theme.MaterialComponents.DayNight.NoActionBar` (so no action bar; each screen supplies its own app bar) with the palette mapped as listed. Add `Widget.FreeCam.PrimaryButton` (style `Widget.MaterialComponents.Button`, `backgroundTint` = `sodium`, `android:textColor` = `on_accent`, `textSize` = `text_action`, `textStyle` = bold, `minHeight` = 46dp, `cornerRadius` = `radius_button`, `insetTop`/`insetBottom` = 0), `Widget.FreeCam.DangerButton` (style `Widget.MaterialComponents.Button.OutlinedButton`, `strokeColor` = `tape`, `android:textColor` = `tape`), `Widget.FreeCam.Tile` (a `MaterialCardView` style: `cardBackgroundColor` = `panel`, `strokeColor` = `rule`, `strokeWidth` = 1dp, `cardElevation` = 0dp, `cardCornerRadius` = `radius_button`), and text styles for the readout rows.

- [ ] **Step 3: Write the drawables**

Each drawable is a shape: `bg_tile` (panel fill, 1dp `rule` stroke, 8dp corners), `bg_badge_tape` (`tape` 12% fill, `tape` stroke, 5dp corners), `bg_badge_mute` (`mute` 12% fill, `mute` stroke, 5dp corners), `bg_banner_fault` (`tape` 10% fill, `tape` stroke, 8dp corners), `bg_chip_live` (`#0A0C0F` 80% fill, `tape` stroke, 5dp corners), `bg_rail_button` (panel fill, `rule` stroke, 8dp corners), `bg_thumb_placeholder` (panel fill, `rule` stroke, 2dp corners). Icons are vector drawables on a 24dp viewport with `strokeColor` `?attr/colorOnSurface` (or `sodium` for tile icons) and `strokeWidth` 1.6: `ic_sliders` (three horizontal lines with knobs), `ic_overflow` (three dots), `ic_preview` (a camera body), `ic_qr` (four corner brackets), `ic_scan` (corner brackets), `ic_history` (a clock), `ic_events` (three list lines), `ic_fullscreen` (four corner brackets).

- [ ] **Step 4: Point the manifests at the new themes**

In both manifests set the application theme to `Theme.FreeCamCamera` / `Theme.FreeCamViewer` (the `.NoActionBar` variants are removed because the base theme already has no action bar). Remove `android:package` from both manifests — `namespace` in the Gradle files is authoritative and the attribute is an error in AGP 8.

- [ ] **Step 5: Build**

Run: `... GradleMain assembleDebug lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL; lint reports no new errors (existing warnings are acceptable if they pre-date this change).

- [ ] **Step 6: Commit**

```powershell
git add camera viewer
git commit -m "feat(ui): graphite and amber design tokens for both apps"
```

---

### Task 7: Camera dashboard redesign

**Files:**
- Rewrite: `camera/src/main/res/layout/activity_main.xml`
- Modify: `camera/src/main/java/com/freecam/camera/ui/MainActivity.kt`
- Modify: `camera/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `CameraStateStore` / `CameraUiState` (Task 4), `CameraService.ACTION_RESTART_CAMERA` / `ACTION_RESET_TOKEN` (Task 4), tokens and styles (Task 6).
- Produces: view ids `tvStateBadge`, `tvStateClock`, `tvStateSubLine`, `bannerFault`, `tvFaultTitle`, `tvFaultMessage`, `btnPrimary`, `tilePreview`, `tilePairing`, `tvAddress`, `tvTailnet`, `tvBattery`, `tvStorage`, `tvMotion`, `tvEventsToday`, `previewContainer`, `overflowMenu`; and the constants `CameraService.ACTION_SET_PREVIEW` / `CameraService.EXTRA_PREVIEW_ENABLED` (handling lands in Task 8).

- [ ] **Step 1: Write the layout**

`activity_main.xml` is a `ConstraintLayout` (root, background `carbon`) holding a `ScrollView` whose only child is a vertical `LinearLayout`:

1. App bar row: camera name (`text_name`, `bone`) + two 32dp `ImageButton`s (`ic_sliders`, `ic_overflow`, tint `mute`).
2. `bannerFault` (initially `gone`): `bg_banner_fault`, containing `tvFaultTitle` (`tape`, 13sp, bold) and `tvFaultMessage` (`mute`, 13sp).
3. State block: `tvStateBadge` (a `TextView` using `bg_badge_tape`/`bg_badge_mute`, 12sp mono uppercase, padding 6dp/3dp) on the left, `tvStateClock` (mono 26sp, `bone`) on the right, and `tvStateSubLine` (12.5sp `mute`) underneath.
4. `btnPrimary` — full width, `Widget.FreeCam.PrimaryButton`, height 46dp.
5. `previewContainer` (a `FrameLayout`, initially `gone`) holding the `OpenGlView` used for local preview — sized 16:9, `layout_width=match_parent`, `layout_height=0dp` with `layout_constraintDimensionRatio="16:9"`.
6. Two tiles (`tilePreview`, `tilePairing`) in a horizontal `LinearLayout`, each `Widget.FreeCam.Tile`, weight 1, height 56dp, icon + label.
7. A `SectionLabel` "Telemetry", then `ReadoutRow`s for Address, Tailnet, Battery, Recordings, Motion, Events today. Each row is a horizontal `LinearLayout` with `layout_weight` label (`text_label`, `mute`) and value (`text_value` or mono style, right-aligned) plus a 1dp `rule` divider above.

The `ScrollView` fills the remaining height; the app bar, state block and primary action stay outside it so they are visible without scrolling at 320×569dp.

- [ ] **Step 2: Bind the state**

In `MainActivity`:
- `onCreate` reads `CameraService.snapshot()` and starts a 1-second `Handler` refresh that re-reads the snapshot and calls `render(state)`.
- `render(state)` sets: badge text `REC` (tape) when `state.running` else `STOPPED` (mute), and swaps the badge background accordingly; clock text = `HH:MM:SS` of `state.uptimeMs`; sub line = `streaming · configured 640×480 · 15fps · 500 kbps` (using the state's resolution/fps/bitrate and the literal word `configured`); primary button text `Stop streaming` (danger style) when running, `Start streaming` (primary style) otherwise; `btnPrimary` is disabled with text `starting…` while a start is in flight.
- Telemetry values: address = `state.host ?: "—"` in mono; tailnet = `connected` (phosphor) or `not on tailnet` (sodium); battery = `87% charging` / `87%` / `—` (mono, `—` when `batteryPercent` is null); storage = `4.2 / 6.0 GB` computed from bytes (mono); motion = `medium` / `off` / `unavailable` (mute) when `state.motionAvailable` is false; events today = the integer, or `—` when the event log is empty.
- The fault banner shows when `state.errorMessage != null`, with the title from `state.errorCode` mapped to a human phrase and the message below.

- [ ] **Step 3: Wire the actions**

- `btnPrimary` sends `ACTION_START` / `ACTION_STOP` to `CameraService` as it does today.
- `tilePreview` toggles the preview: it sends `CameraService.ACTION_SET_PREVIEW` with `CameraService.EXTRA_PREVIEW_ENABLED` (both constants are added to `CameraService` in this task; the stream-side handling arrives in Task 8), shows `previewContainer` with an `OpenGlView` when on, and hides it when off. While the switch is in flight the tile shows `Restarting…`.
- `tilePairing` opens `PairingActivity`.
- The overflow menu (`ic_overflow` → `PopupMenu`) offers `Restart camera` (`CameraService.ACTION_RESTART_CAMERA`), `Show logs` (`LogsActivity`), `Pair viewer` (`PairingActivity`), `Dim screen` (`DimScreenActivity`) and `Reset pairing token` (opens a confirmation dialog: title `Reset pairing token?`, message `Paired viewers will stop working until they scan the new code.`, buttons `Reset token` / `Cancel`; on confirm it sends `CameraService.ACTION_RESET_TOKEN`).
- The sliders button opens `SettingsActivity`.

- [ ] **Step 4: Strings**

Add to `camera/src/main/res/values/strings.xml`: `state_recording` (`REC`), `state_stopped` (`STOPPED`), `action_start_streaming` (`Start streaming`), `action_stop_streaming` (`Stop streaming`), `action_starting` (`starting…`), `tile_preview` (`Preview`), `tile_pair_viewer` (`Pair viewer`), `tile_restarting` (`Restarting…`), `section_telemetry` (`Telemetry`), `label_address` (`Address`), `label_tailnet` (`Tailnet`), `label_battery` (`Battery`), `label_recordings` (`Recordings`), `label_motion` (`Motion`), `label_events_today` (`Events today`), `value_tailnet_connected` (`connected`), `value_tailnet_absent` (`not on tailnet`), `value_motion_unavailable` (`unavailable`), `value_unknown` (`—`), `value_charging` (`charging`), `menu_restart_camera` (`Restart camera`), `menu_show_logs` (`Show logs`), `menu_dim_screen` (`Dim screen`), `menu_reset_token` (`Reset pairing token`), `dialog_reset_token_title` (`Reset pairing token?`), `dialog_reset_token_message` (`Paired viewers will stop working until they scan the new code.`), `dialog_reset_token_confirm` (`Reset token`), `dialog_cancel` (`Cancel`).

- [ ] **Step 5: Build and install on the test phone**

Run: `... GradleMain assembleDebug --no-daemon`, then
`adb -s 10AF3L22EM001DG install -r camera/build/outputs/apk/debug/camera-debug.apk`
Expected: BUILD SUCCESSFUL and `Success`.

- [ ] **Step 6: Screenshot the dashboard**

Launch `adb -s 10AF3L22EM001DG shell am start -n com.freecam.camera/.ui.MainActivity`, grant camera permission with `adb shell pm grant com.freecam.camera android.permission.CAMERA`, then
`adb -s 10AF3L22EM001DG exec-out screencap -p > camera-dashboard.png`.
Expected: the dark dashboard renders with the amber primary button, the `STOPPED` badge, and no clipping.

- [ ] **Step 7: Commit**

```powershell
git add camera
git commit -m "feat(camera): redesign the dashboard as a device readout"
```

---

### Task 8: Make the encoder actually run, with and without preview

**Files:**
- Modify: `camera/src/main/java/com/freecam/camera/stream/StreamController.kt`
- Modify: `camera/src/main/java/com/freecam/camera/service/CameraService.kt`
- Modify: `camera/src/main/java/com/freecam/camera/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `Settings.rtspUser` / `Settings.rtspPassword` (Task 4), the preview surface id `previewContainer` (Task 7).
- Produces: `StreamController.start(headless: Boolean, previewView: OpenGlView?)`; the service action `CameraService.ACTION_SET_PREVIEW` carrying the toggle as an extra `CameraService.EXTRA_PREVIEW_ENABLED` (`Boolean`).

- [ ] **Step 1: Fix the encoder configuration**

In `StreamController.start`:
- Construct `RtspServerCamera2` with the headless constructor `RtspServerCamera2(context, useOpengl = false, connectCheckerRtsp, Settings.rtspPort)` when no preview view is supplied, and with `RtspServerCamera2(openGlView, connectChecker, Settings.rtspPort)` when one is. The current code builds an unattached 1×1 `OpenGlView`, which never produces a GL context and prevents the stream from starting.
- Fix `prepareAudio`: the RootEncoder signature is `prepareAudio(bitrate, sampleRate, isStereo)` and the current call passes `audioSampleRate` as the bitrate and `128` as the sample rate. Call `prepareAudio(Settings.audioBitrateKbps * 1000, Settings.audioSampleRate, Settings.audioStereo)` and check the returned boolean; on `false`, report `onError("Audio encoder rejected the requested format")`.
- Check the boolean returned by `prepareVideo(width, height, fps, bitrate, rotation)` and report `onError("Video encoder rejected the requested format")` on `false`.
- Authenticate with `getStreamClient().setAuthorization(settings.rtspUser, settings.rtspPassword)` so the server uses `freecam` + the per-install token.

- [ ] **Step 2: Add the preview mode to the service**

`CameraService` gains `ACTION_SET_PREVIEW` with `EXTRA_PREVIEW_ENABLED`. On receipt it stops the current `StreamController`, recreates it in the requested mode, and restarts the stream. When preview is enabled, the service passes the `OpenGlView` published by the activity (the activity registers it via `CameraService.attachPreviewView(view)`); when disabled, the view is detached and the stream runs headless.

- [ ] **Step 3: Handle preview in the activity**

`MainActivity` creates an `OpenGlView` in `onCreate` and adds it to `previewContainer` (container hidden by default). `tilePreview` sends `ACTION_SET_PREVIEW` with the new value and shows or hides the container accordingly. Because the stream restarts when the mode changes, the state block shows `starting…` during the switch.

- [ ] **Step 4: Verify on the test phone**

Install (step 5 of Task 7 command), grant the camera permission, press Start, and screenshot.
Expected: the badge reads `REC`, the clock counts up, and with preview on the surface shows the camera image. `adb -s 10AF3L22EM001DG logcat -d | Select-String "freecam"` shows no `Failed to start RTSP server` line.

- [ ] **Step 5: Commit**

```powershell
git add camera
git commit -m "fix(camera): start the encoder headless or with a live preview surface"
```

---

### Task 9: Viewer camera list redesign

**Files:**
- Rewrite: `viewer/src/main/res/layout/activity_main.xml`, `viewer/src/main/res/layout/item_camera.xml`
- Modify: `viewer/src/main/java/com/freecam/viewer/ui/MainActivity.kt`
- Modify: `viewer/src/main/java/com/freecam/viewer/network/ApiClient.kt`
- Modify: `viewer/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `CameraStatus`, `RecordingInfo`, `MotionEvent`, `ApiPaths`, `selectHost`, `buildHttpUrl` (Tasks 1–3), tokens (Task 6), Glide 4.16.0 (already a viewer dependency).
- Produces: `ApiClient.getStatus(host, port, token): CameraStatus?`, `ApiClient.getEvents(host, port, token): List<MotionEvent>`, `ApiClient.getRecordings(host, port, token): List<RecordingInfo>` (all returning the shared models), and view ids `tvCameraName`, `tvCameraStatus`, `tvCameraAddress`, `ivCameraThumb`, `tvStatusStrip`, `tvEmptyState`, `emptyActions`.

- [ ] **Step 1: Rewrite `ApiClient` on the shared models**

Replace the hand-rolled `org.json` parsing with `Json { ignoreUnknownKeys = true }` decoding into the shared models. Delete the local `RecordingInfo` and `EventInfo` data classes at the bottom of the file and import the shared ones. Keep the OkHttp client, the 10 s connect / 15 s read timeouts and the `Authorization: Bearer <token>` header. Add `suspend fun getSnapshotUrl(host: String, port: Int): String` returning `buildHttpUrl(host, port, ApiPaths.SNAPSHOT)`.

- [ ] **Step 2: Rewrite the list layout**

`activity_main.xml`: a `CoordinatorLayout` with an app bar row (`Cameras` at `text_title`, a 40dp scan `ImageButton` tinted `mute`), a `StatusStrip` `TextView` (12.5sp `mute`, `panel` background, bottom rule, initially `gone`), a `SwipeRefreshLayout` holding the `RecyclerView`, and an empty state block (`tvEmptyState` "No cameras yet" 18sp `bone`, plus two buttons: `Scan a QR code` primary and `Add manually` outlined) that replaces the old grey centred label.

`item_camera.xml`: a flat row — a 92×56 `ImageView` (`ivCameraThumb`, `bg_thumb_placeholder` background, `scaleType=centerCrop`), then a vertical column with `tvCameraName` (15sp `bone`), `tvCameraStatus` (12.5sp `mute`, with a coloured status dot drawn as a `TextView` bullet in the same row) and `tvCameraAddress` (mono 11.5sp `mute`), and a chevron icon on the right. A 1dp rule separates rows; no `CardView`.

- [ ] **Step 3: Bind the list**

`MainActivity`:
- Loads each camera's status with `apiClient.getStatus(...)`. A successful status means the camera is reachable: the status line shows a cyan `● live`, the battery (`87% charging`) and the host address; an unreachable camera shows a muted `● offline` plus `offline since <time>`.
- The thumbnail is loaded with Glide from `buildHttpUrl(host, port, ApiPaths.SNAPSHOT)` with the bearer token as a header (`Glide.get(context).load(url).apply(RequestOptions().addHeader("Authorization", "Bearer $token"))`) and `bg_thumb_placeholder` as the placeholder; a `404` leaves the placeholder in place.
- The status strip is shown only when the camera reports `motion.eventsToday > 0` or `recording` is true, and reads `3 motion events today · 18 min recorded` — otherwise it stays `gone`. A camera with no data never shows zeros.
- The scan button starts the existing QR scanner; the overflow (or the scan button's long press) offers `Add camera manually`.

- [ ] **Step 4: Build, install and screenshot**

Run `... GradleMain assembleDebug --no-daemon`, then
`adb -s 10AF3L22EM001DG install -r viewer/build/outputs/apk/debug/viewer-debug.apk`, launch `com.freecam.viewer/.ui.MainActivity`, and capture a screenshot.
Expected: the dark list renders with flat rows, the status strip hidden when there is no data, and the empty state visible when no camera is paired.

- [ ] **Step 5: Commit**

```powershell
git add viewer
git commit -m "feat(viewer): redesign the camera list around real status and snapshots"
```

---

### Task 10: Viewer player redesign and playback authentication

**Files:**
- Rewrite: `viewer/src/main/res/layout/activity_player.xml`, `viewer/src/main/res/layout/custom_controller.xml`
- Modify: `viewer/src/main/java/com/freecam/viewer/ui/PlayerActivity.kt`
- Modify: `viewer/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Pairing` (Task 1), `buildRtspUrl`, `buildHttpUrl` (Task 2), `ApiPaths` (Task 1), tokens (Task 6).
- Produces: `PlayerActivity.playLive()` builds `rtsp://<rtspUser>:<token>@<host>:<rtspPort>/<path>`; `PlayerActivity.playRecording()` streams the file with an `Authorization: Bearer` header.

- [ ] **Step 1: Build authenticated URLs**

Live: `buildRtspUrl(pairing.host, pairing.rtspPort, pairing.path, pairing.rtspUser, pairing.token)`.
Playback: build a `DefaultHttpDataSource.Factory` with
`setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${pairing.token}"))` and pass it to `DefaultMediaSourceFactory(this, dataSourceFactory)`; the URL is `buildHttpUrl(pairing.host, pairing.httpPort, recording.path)`.

- [ ] **Step 2: Rewrite the player layout**

`activity_player.xml`: a `FrameLayout` with a black background containing the `PlayerView` (transparent controller), a `LiveChip` at the top-left (`● LIVE`, `bg_chip_live`, `tape`, mono 11sp) plus the camera name below it (`text_name`, `bone`), a `RightRail` on the right edge (three 38dp buttons: history, events, fullscreen, each `bg_rail_button`), a bottom live bar (`live` in `phosphor` · `started 14:02:11` · elapsed mono) and a centred fault block (`tvFault`, 15sp `bone`, with a `btnRetry` `PrimaryButton`) that is `gone` by default.

`custom_controller.xml` keeps the existing transport controls but restyled: the scrubber uses `sodium` and the timecodes are mono 11sp `mute`. The live mode does not show a progress bar.

- [ ] **Step 3: Handle the fault states**

`PlayerActivity` registers a `Player.Listener`; on `onPlayerError` the feed is hidden and the fault block shows a message chosen from the error: `Camera is offline` (network unreachable), `Wrong pairing token` (HTTP 401 during playback), `Stream authentication failed` (RTSP 401), `Could not play this clip` (everything else), with a `Retry` button that re-prepares the source.

- [ ] **Step 4: Build, install and verify**

Install the viewer APK, pair a camera by QR, open it, and screenshot both the live view and the history playback.
Expected: live video renders (no 401 in logcat: `adb logcat -d | Select-String "ExoPlayerImplInternal" | Select-String "401"` returns nothing), and a history clip plays with the bearer header attached.

- [ ] **Step 5: Commit**

```powershell
git add viewer
git commit -m "feat(viewer): redesign the player and authenticate live and recorded playback"
```

---

### Task 11: Re-theme the remaining screens

**Files:**
- Modify: `camera/src/main/res/layout/activity_pairing.xml`, `activity_logs.xml`, `activity_settings.xml`, `activity_dim_screen.xml`
- Modify: `camera/src/main/java/com/freecam/camera/ui/PairingActivity.kt`, `LogsActivity.kt`, `SettingsActivity.kt`, `DimScreenActivity.kt`
- Modify: `viewer/src/main/res/layout/activity_history.xml`, `activity_events.xml`, `activity_add_camera.xml`, `activity_viewer_settings.xml`
- Modify: `viewer/src/main/java/com/freecam/viewer/ui/HistoryActivity.kt`, `EventsActivity.kt`, `AddCameraActivity.kt`, `ViewerSettingsActivity.kt`
- Modify: `camera/src/main/res/xml/preferences.xml`, `viewer/src/main/res/xml/viewer_preferences.xml`

**Interfaces:**
- Consumes: tokens and styles (Task 6), shared models (Task 1).
- Produces: consistent look across every remaining screen; no behavioural change.

- [ ] **Step 1: Camera screens**

- **Pairing**: keep the white QR card (a QR needs a white quiet zone) but give it a 1dp `rule` border, 8dp corners and no elevation; the address and token become mono (`text_mono`, `bone`); "Copy token" becomes a `Widget.FreeCam.Tile`; the warning line becomes 12.5sp `mute`. The QR payload stays byte-identical to today (type, version, name, rtsp_url, api_url, token, mode) — the RTSP password is the token, so no new field is needed.
- **Logs**: the body becomes a `panel` surface with mono 11sp rows; "Copy all" and "Clear" become tiles, "Clear" a `DangerButton`.
- **Settings**: the preference fragment keeps its structure; the screen background becomes `carbon` and each `PreferenceCategory` title is styled with `Widget.FreeCam.SectionLabel`.
- **Dim screen**: pure black, a 24sp mono clock in `mute`, and "tap to exit" at 12sp `mute`.

- [ ] **Step 2: Viewer screens**

- **History**: `CardView` rows become flat rows with a 1dp rule: filename in mono 13sp, duration and size on one 12.5sp `mute` line, a play icon on the right. Day headings use `Widget.FreeCam.SectionLabel`.
- **Events**: the same flat row shape: timestamp in mono, severity word, and the clip it refers to.
- **Add camera / Viewer settings**: `carbon` background, `PrimaryButton` for save.

- [ ] **Step 3: Build and screenshot**

Run `... GradleMain assembleDebug lintDebug --no-daemon`, install both APKs on the test phone, and screenshot each screen.
Expected: BUILD SUCCESSFUL; no screen shows a teal token, an elevated card, or a `colorPrimary` toolbar.

- [ ] **Step 4: Commit**

```powershell
git add camera viewer
git commit -m "style: re-theme the remaining screens to the graphite and amber system"
```

---

### Task 12: Verification sweep and documentation

**Files:**
- Modify: `README.md`, `NOTES.md`
- Create: `docs/superpowers/plans/2026-11-14-manual-device-checks.md`

**Interfaces:**
- Consumes: everything above.
- Produces: a written device checklist the owner can run on the A2 Core, and README/API documentation that matches the shipped behaviour.

- [ ] **Step 1: Full build gate**

Run: `... GradleMain assembleDebug lintDebug testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL; `:common` unit tests pass (8 tests); lint reports no errors.

- [ ] **Step 2: Viewer on the connected phone**

Install the viewer APK, pair a camera over the tailnet, and verify: list renders, thumbnail loads or falls back to the placeholder, live view plays, history plays, and no 401 appears in logcat. Capture screenshots of the list and the player.

- [ ] **Step 3: Narrow-width check**

Emulate a 320dp viewport on the test phone:
`adb -s 10AF3L22EM001DG shell wm size 600x1069` and `adb -s 10AF3L22EM001DG shell wm density 300`, reopen the viewer list and player, screenshot, then restore with `adb shell wm size reset` and `adb shell wm density reset`.
Expected: no clipped text, the strip and rows still fit.

- [ ] **Step 4: Camera app smoke test on the test phone**

Install the camera APK on the connected phone, grant the camera permission, start streaming, screenshot the dashboard, and check the API from this PC:
`curl -H "Authorization: Bearer <token>" http://<device-ip>:8080/api/status`
Expected: JSON with `running`, `tailnet`, `battery`, `storage` and `motion` populated; `/api/snapshot` returns the `not_streaming` envelope.

- [ ] **Step 5: Write the A2 Core manual checklist**

`docs/superpowers/plans/2026-11-14-manual-device-checks.md` lists, in order: install the APK by downloading it from the PC over Wi-Fi (`tools/serve-apk.sh`), grant the camera permission, start streaming, confirm `REC`, open the preview and aim the camera, confirm the tailnet row reads `connected`, check the dashboard at 320×569dp for clipping, set the font scale to the largest size and re-check, and confirm `curl http://<tailnet-ip>:8080/api/status` matches the screen.

- [ ] **Step 6: Update the docs**

`README.md`: replace the UI description with the new design system, the dashboard readout list, and the four API endpoints including `/api/snapshot` and the error envelope. `NOTES.md`: append a version-history row and a note that the camera-side frame pipeline, recording, ntfy and release signing remain outstanding.

- [ ] **Step 7: Commit**

```powershell
git add README.md NOTES.md docs
git commit -m "docs: describe the new dashboard, the API contract and the device checklist"
```

---

## Self-review notes

- **Spec coverage:** §6 tokens → Task 6; §7.1 dashboard → Tasks 4, 7; §7.2 camera re-theme → Task 11; §7.3 viewer list → Task 9; §7.4 player → Task 10; §7.5 viewer re-theme → Task 11; §8.1–8.2 contract → Tasks 1, 4, 5; §8.3 snapshot → Task 5 (endpoint) and Task 9 (viewer); §8.4 events read path → Tasks 3, 5; §8.6 error envelope → Task 5; §8.7 playback auth → Tasks 2, 8, 10; §8.8 host selection → Tasks 2, 4; §8.9 honesty rules → Tasks 7, 9; §9 accessibility → Tasks 6, 7, 12; §12 verification → Tasks 7, 9, 10, 12. §13 (frame pipeline, recording, event writing, ntfy, signing) is explicitly out of scope and is not implemented here.
- **Review Focus coverage:** (1) → Task 2 test; (2) → Task 1 test plus Task 7 rendering `—`; (3) → Task 3 test plus Task 9 hiding the strip; (4) → Task 2 test plus Task 10 fault mapping and Task 12 device check; (5) → Task 12 step 3 and the A2 Core checklist.
- **Type consistency:** `CameraStatus`, `CameraUiState`, `RecordingInfo`, `MotionEvent`, `Pairing`, `ApiError` and the four sub-state classes are defined once in Task 1 and used verbatim afterwards; `selectHost`, `isTailnetAddress`, `buildRtspUrl`, `buildHttpUrl`, `parseEventLog` are defined once in Tasks 2–3; `CameraStateStore` and `CameraService.snapshot()` are defined in Task 4 and consumed in Task 7.
- **Proportion:** the plan states signatures, JSON shapes, exact copy and test assertions; the layout and drawable bodies are left to the implementer, matching the spec's design system.
