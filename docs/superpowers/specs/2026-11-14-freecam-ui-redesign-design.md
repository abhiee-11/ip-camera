# FreeCam UI redesign + state contract — design doc

- **Date:** 2026-11-14
- **Status:** draft for approval (decisions folded in after the builder review)
- **Applies to:** `:camera` and `:viewer` in this repo
- **Mockup (reference, not source of truth):** `C:\Users\LENOVO\AppData\Local\Temp\opencode\freecam-ui-mockup.html`

---

## 1. Intent and why

FreeCam turns an old phone into a home security camera. Two UIs follow from that
fact, and both are currently ignored by the code:

- The **camera app** runs on a phone bolted to a wall, streaming unattended, with
  nobody touching it. Its screen is read at arm's length, occasionally, in a dim
  room. It is instrumentation, not a content app.
- The **viewer app** is glanced at. It is a monitor, not a media library.

The current UI is the untouched Material template in both apps
(`Theme.MaterialComponents.DayNight.DarkActionBar`, primary `#03DAC5` on `#121212`,
every surface a 12dp-radius CardView with elevation). It was sized for a modern
phone, but the camera phone is a Samsung Galaxy A2 Core: 480×854 at 233dpi ≈
**320×569 dp**. It scrolls before it says anything useful.

Worse, it reports things that are not true. `tvEventsToday` is hardcoded to `0`
(`camera/ui/MainActivity.kt:158`), battery reads `N/A`, the address comes from
`getLocalIpAddress()` which may return the cellular IP, and a dead encoder looks
identical to a healthy one. The redesign and the truth-telling are one piece of
work: a screen that claims to report state must be fed real state.

---

## 2. Goals

1. One glance tells you whether the camera is working and what it is doing.
2. Every value on screen is real, sourced from live state — no placeholders, no
   hardcoded zeros.
3. Errors name the failure and the fix.
4. One visual system across both apps, and clearly not stock Material.
5. State and the primary action fit on 320×569dp without scrolling.
6. The remaining screens are re-themed to the same tokens, so the app never shows
   two palettes.

## 3. Non-goals

- No Jetpack Compose migration.
- No new third-party UI dependencies, no bundled font files.
- No light theme.
- **Not** the capture-pipeline rewrite (frame delivery, encoder configuration,
  recording muxing, event writing). That is a separate plan. Until it lands the
  UI reports the corresponding capability as unavailable rather than faking it.
- No Phase 2 relay UI.

## 4. Anti-goals

- Not a teal refresh, not a "Material You" pass.
- No chrome that does not report state: no hero, no marketing header, no
  decorative framing.
- Not the SaaS-card kit — not every element in an identical rounded card with a
  soft shadow.

---

## 5. Constraints

| Constraint | Detail |
|---|---|
| Camera hardware | Galaxy A2 Core, Android 8.1 Go (API 27), 1 GB RAM, weak CPU, broken USB data port, no wireless debugging (Android 8 has none) |
| Screen | 480×854 @ 233dpi ≈ 320×569 dp — the hard design constraint |
| Viewer hardware | vivo V2434, Android 16 (API 36), 720×1608 @ 300dpi ≈ 384×857 dp — connected over ADB, so the viewer UI is verifiable on device |
| Build | AGP 8.5.0, Kotlin 1.9.24, Gradle 8.13, JDK 17, minSdk 26, targetSdk 34 |
| UI stack | XML layouts, ViewBinding + DataBinding, Material Components 1.12 |
| Network | Tailscale only; camera serves RTSP :8554 and HTTP :8080 (Bearer token) |
| Stream library | RootEncoder 2.4.5 + RTSP-Server 1.2.1 (no frame callback in this version) |
| Deployment | APK installed by downloading from the PC over Wi-Fi; APK size and cold-start time matter |

---

## 6. Design system

### 6.1 Colour

One palette, both apps. The old teal tokens (`teal_200`, `primary`,
`primary_variant`, `secondary`, `error`, `dark_bg`, `dark_surface`,
`dark_surface_variant`, `on_dark`, `on_dark_variant`, `green_200`, `red_200`,
`gray_600`) are replaced.

| Token | Hex | Used for |
|---|---|---|
| `carbon` | `#101317` | app background |
| `panel` | `#171B21` | tiles, list rows, toolbar surface, overlays |
| `rule` | `#232A33` | 1dp dividers and borders |
| `sodium` | `#F2A33C` | the one accent: primary action, icons in tiles, section emphasis |
| `tape` | `#E5484D` | REC state, recording, faults, destructive actions |
| `phosphor` | `#58C7F3` | live state, tailnet/network state, machine data highlights |
| `bone` | `#E9E7E2` | primary text |
| `mute` | `#8B9199` | labels, secondary text, disabled |

Rules that keep this from drifting:

- **Amber is scarce.** It marks the action you want and nothing else. A screen
  shows at most one filled amber element.
- **Red means recording or fault.** Nothing else. A destructive action is
  *outlined* red, never filled — filled amber is reserved for the safe action.
- **Cyan means live or networked.** Never a button.
- No elevation, no shadows, no gradients, no image backgrounds.

### 6.2 Type

System `sans-serif` (Roboto) for everything; system `monospace` **only** for
machine values — IP addresses, ports, token, uptime, bitrate, sizes, timestamps.
Those strings are copy-paste artefacts, so monospace is functional, not
decorative.

| Role | Size / weight |
|---|---|
| Screen title | 20sp / 600 |
| Camera name (app bar) | 15sp / 600 |
| Primary action | 16sp / 600 |
| Readout value | 14sp regular, tabular mono where numeric |
| Readout label, section label | 13sp regular, `mute` |
| Secondary/meta line | 12.5sp |
| Mono data | 12sp |
| Clock / uptime (camera app) | 26sp mono, tabular |

Sentence case everywhere. The only uppercase is device marking text that
appears on real hardware: `REC`, `STOPPED`, `LIVE`.

### 6.3 Spacing and shape

4dp grid. 16dp screen padding, 8dp within a group, 12dp between tiles, 1dp
rules. Corner radius 8dp on buttons and tiles, 6dp on chips and thumbnails, 5dp
on badges. No shadows. Touch targets ≥ 48dp. Icons 20–24dp with a
`contentDescription`.

### 6.4 Components

| Component | Definition |
|---|---|
| `PrimaryButton` | filled `sodium`, label `#14181D`, 46dp tall, 8dp radius |
| `DangerButton` | 1dp `tape` border, `tape` label, transparent fill |
| `Tile` | `panel` fill, 1dp `rule` border, sodium icon + 13.5sp label, 56dp tall |
| `StateBadge` | 9dp dot + mono uppercase label; `tape` when the encoder is live, `mute` when idle |
| `Clock` | mono 26sp, tabular figures, right-aligned |
| `ReadoutRow` | label `mute` 13sp left, value right 14sp; 1dp rule above; mono for machine values |
| `SectionLabel` | 13sp `mute`, 14dp top padding, no rule |
| `FaultBanner` | 1dp `tape` border, `tape` 10% fill, bold cause line + remedy line, 13dp margin |
| `StatusStrip` | single-line summary under the viewer toolbar, `panel` fill, bottom rule |
| `CameraRow` | 92×56 thumbnail (2dp radius) + name + status line + mono address + chevron |
| `LiveChip` | `tape` 1dp border on 80% black, mono uppercase, 6px dot |
| `RightRail` | vertical stack of 38dp icon buttons on `panel` with `rule` borders |
| `AppBar` | `panel`/transparent surface with a bottom rule; never `colorPrimary` |
| `PreviewSurface` | 16:9 `OpenGlView` hosted by the camera dashboard when preview is on |

### 6.5 Motion

Effectively none. The A2 Core is weak and the app is unattended; a fault banner
appears immediately, list items do not animate, the REC dot does not pulse.

---

## 7. Screen specs

### 7.1 Camera dashboard — **redesign**

Layout, top to bottom:

1. **App bar** — camera name (15sp) left; two 32dp icon buttons right: sliders
   icon (Settings) and overflow (⋮). Overflow contains: Restart camera,
   Show logs, Pair viewer, Dim screen, Reset pairing token.
2. **Fault banner** (only when faulted) — bold cause, then the remedy.
3. **State block** — left: `StateBadge`; right: `Clock` (uptime, mono 26sp).
   Underneath a 12.5sp `mute` line: `streaming · configured 640×480 · 15fps ·
   500 kbps`.
4. **Primary action** — full width. Streaming → `DangerButton` "Stop streaming".
   Stopped → `PrimaryButton` "Start streaming". Disabled + "starting…" while the
   encoder comes up.
5. **Preview** — when preview is on, a 16:9 `OpenGlView` sits between the state
   block and the primary action, so the camera can be aimed. The tile toggles
   it. Toggling **restarts the stream** (see below), so the button reads
   "Restarting…" while it applies.
6. **Two tiles** — "Preview" and "Pair viewer".
7. **Telemetry readout** — `ReadoutRow`s: Address (mono, host actually in use),
   Tailnet (cyan `connected` / amber `not on tailnet`), Battery (mono `%`,
   `charging`), Recordings (`used / cap`, mono), Motion (`medium` /
   `unavailable` until the pipeline lands), Events today (real count, or `—`).

**Badge semantics.** `REC` means *the encoder is live and serving* — not that a
file is being written. Recording-to-disk is a separate indicator and stays hidden
until recording exists.

**Configured vs measured.** Resolution, fps and bitrate are settings, not
measurements. They are labelled `configured` until the API can report real
encoder state. Nothing on this screen is presented as measured when it is not.

**Binding rules.**

- The state block, preview and primary action are always visible; telemetry
  scrolls.
- Values come from one snapshot of live state (service → UI), refreshed on a
  timer and on every state change — never recomputed per widget.
- The preview surface is the same `OpenGlView` the encoder renders into. In
  service mode (screen off, preview off) the stream runs on
  `RtspServerCamera2(context, useOpengl = false, …)`, which needs no GL surface.
  With preview on, it runs on `RtspServerCamera2(openGlView, …)` with the view
  attached to the dashboard. This is what fixes the current detached 1×1
  `OpenGlView` that prevents the stream from starting at all.

### 7.2 Camera, remaining screens — **re-theme only**

Layout and behaviour are unchanged; colours, type and component shapes follow
§6.

- **Pairing** — QR stays on a white card (QR needs a white quiet zone) but with
  a `rule` border and no elevation; address and token become mono; "Copy token"
  becomes an outlined `Tile`; the warning line uses `mute` at 12.5sp.
- **Logs** — toolbar becomes `panel`; the log body is a `panel` surface with
  mono 11sp lines and `rule` separators; "Copy all" and "Clear" become
  outlined tiles, "Clear" being a `DangerButton`.
- **Settings** — the preference screen follows the Material preference widget
  colours, but the background is `carbon` and each category gets a
  `SectionLabel` in `mute`.
- **Dim screen** — pure black with a 24sp mono clock in `mute`, "tap to exit" at
  12sp.

### 7.3 Viewer camera list — **redesign**

1. **App bar** — "Cameras" (20sp) left; one 40dp scan icon button right
   (QR pairing). "Add camera" moves into the overflow menu as a text action.
2. **Status strip** — one `mute` line under the toolbar:
   `3 motion events today · 18 min recorded`. **Hidden entirely when there is no
   data** — a strip reading zeros is a lie.
3. **Camera rows** — flat rows separated by 1dp rules, no cards:
   thumbnail (92×56, from `/api/snapshot`, cached), name (15sp), status line
   (`● live` cyan / `● offline` mute + `87% charging` / `offline since 14:02`),
   address in mono 11.5sp, chevron on the right.
4. **Thumbnails** — `/api/snapshot` returns `404 not_streaming` until the
   pipeline can produce frames. The viewer then shows a `panel` placeholder
   block, not a broken image. The endpoint and the LRU cache are built now so
   thumbnails light up later without touching the viewer again.
5. **Offline cameras** stay in the list, visually receded, showing
   "offline since <time>". They do not open a player that will fail.
6. **Empty state** — actionable: "No cameras yet" + two buttons, "Scan a QR
   code" (primary) and "Add manually".
7. **Swipe to refresh** keeps its position, tinted `phosphor`.

Thumbnails are 92×56 JPEGs in an in-memory LRU cache of 16, loaded on a
background dispatcher.

### 7.4 Viewer player — **redesign**

- **Live mode**: black background, `LiveChip` (`● LIVE`) and camera name
  top-left, `RightRail` on the right (History, Events, Fullscreen), and a bottom
  live bar — `live` (cyan) · `started 14:02:11` · elapsed mono. **No progress
  bar on a live stream.**
- **Playback mode** (history clip): a scrubber and timecodes are correct here;
  the scrubber is `sodium`, the current-position dot is `sodium`.
- **Fault state**: if the stream fails, replace the feed with a centred message
  naming the cause (`Camera is offline`, `Wrong pairing token`, `Stream
  authentication failed`) and one action (`Retry` / `Re-pair`).
- **Back behaviour**: back leaves fullscreen first, then the player.

### 7.5 Viewer, remaining screens — **re-theme only**

- **History** — flat rows: filename (mono 13sp), duration and size on one meta
  line, play icon on the right. Day grouping stays, with `SectionLabel` headers.
- **Events** — same flat row pattern: timestamp (mono), severity word, and the
  clip it refers to. Tapping opens the clip.
- **Add camera / Viewer settings** — same form styling, `carbon` background,
  `PrimaryButton` for save.

---

## 8. State contract (this plan) and what it excludes

The UI cannot be honest until the read path exists. The **read path** — status
fields, error envelope, shared models, playback authentication — is **in this
plan**. Anything that requires camera frames is **not**:

| In this plan | Deferred to the pipeline plan |
|---|---|
| `/api/status` fields and error envelope | Frame delivery from the encoder |
| `/api/events` read path + model | Writing/persisting events |
| `/api/snapshot` endpoint + viewer cache | Producing the snapshot image |
| `:common` model consolidation | Recording to MP4 |
| RTSP + HTTP playback authentication | `duration_sec` from real recordings |
| Real event count in the UI | ntfy push notifications |
| | Release signing / CI |

Until the pipeline lands, `/api/snapshot` answers `404 not_streaming` and the
camera reports motion as `unavailable`. The UI is honest about what exists.

### 8.1 One contract in `:common`

`:common` owns the serializable models; `:camera` and `:viewer` both import
them. Today each app defines its own duplicates and `:common`'s models match
neither — delete the duplicates and make `:common` the source of truth.

### 8.2 `/api/status` (GET, Bearer auth)

```
running, recording, uptime_ms, camera_name, resolution, fps, bitrate_kbps,
rtsp_url, api_url, version,
host,                      // the address actually in use, tailnet preferred
tailnet: { connected, ip },
battery: { percent, charging },
storage: { used_bytes, free_bytes, cap_bytes },
motion: { enabled, sensitivity, events_today, available },
recording_mode,
error: { code, message } | null
```

The viewer already expects most of these and receives none, so its status card
shows `-1`, `0` and `off`.

### 8.3 `/api/snapshot` (GET, Bearer auth) — new

Returns the most recent frame as `image/jpeg`; `404` with
`{"error":{"code":"not_streaming","message":"..."}}` while no frame source
exists.

### 8.4 `/api/events` (GET, Bearer auth)

Returns the stored events as
`{ id, ts, severity, recording_path, thumbnail }`. Until events are written,
the list is empty and the viewer hides the strip rather than showing zeros.

### 8.5 `/api/recordings` (GET, Bearer auth)

`{ filename, path, size, start_time, duration_sec }`.

### 8.6 Errors

API error bodies become `{"error":{"code":"...","message":"..."}}` so the UI can
name the fault instead of showing a generic failure.

### 8.7 Playback authentication

- **RTSP live**: RTSP-Server 1.2.1's `RtspServerStreamClient.setAuthorization()`
  calls `rtspServer.setAuth(user, password)` — server-side auth is already
  wired by the existing call in `StreamController`. The credentials become
  user `freecam` and the camera's **existing per-install random token** as the
  password. The viewer builds
  `rtsp://freecam:<token>@<host>:8554/live`, which Media3 supports (Basic and
  Digest via URI userinfo).
  **The pairing QR does not change format** — it already carries `token`, and
  already-paired viewers keep working.
- **HTTP playback**: ExoPlayer must send `Authorization: Bearer <token>` on
  `/recordings/...` requests, via a `DefaultHttpDataSource.Factory` with
  default request properties. Today it sends nothing and every playback attempt
  is a 401.

### 8.8 Tailnet and host selection

- `tailnet.connected` is true only when a 100.64.0.0/10 address is present on
  some interface **and** the HTTP API answers on that address. A 100.x address
  alone is a heuristic, not proof, so the row reads "not on tailnet" otherwise.
- `host` prefers the tailnet address, falls back to the Wi-Fi address, and is
  never the cellular address. `PairingActivity` already implements this
  priority; it moves into shared code.
- When neither is available, `host` is null and the camera app says so instead
  of showing `0.0.0.0`.

### 8.9 Honesty rules (binding on every screen)

- No fabricated numbers. A value that is unknown is `—`, and the row is hidden
  when that is clearer.
- Configured values are labelled `configured`.
- A live stream shows no progress bar; a recording shows its real duration.

---

## 9. Accessibility and performance

- Contrast on `carbon`: `bone` ≈ 13:1, `mute` ≈ 5.6:1, `sodium` ≈ 9:1,
  `tape` ≈ 4.9:1 (use `tape` text at ≥13sp, bold).
- Touch targets ≥48dp; every icon button has a `contentDescription`.
- Dark only, but the layout must survive a 1.3× font scale without clipping.
- Thumbnails are small JPEGs in an LRU cache — no Bitmap scaling on the main
  thread, no large bitmaps in the camera app at all.
- The camera app must stay light: no animations, no shadows, one screen
  inflation.

---

## 10. Approaches considered

| Approach | Verdict |
|---|---|
| **(a)** Rewrite the UI in Compose | Rejected — toolchain and APK-size cost on a 1 GB API 27 device, for no benefit at this stage. Revisit if the app ever gains a modern-only surface. |
| **(b)** Restyle in place with stock Material components | Rejected — Material's card/button vocabulary cannot express the readout rows, right rail or fault banner the design needs. |
| **(c)** XML layouts + custom styles/drawables + one shared token set per app | **Chosen** — works with the existing build, no new dependencies, expressible in the current stack. |
| **(d)** Web viewer (HTML5 page served by the camera) | Deferred — useful later for desktop viewing, but it does not replace the two native apps and belongs with Phase 2. |

---

## 11. Left to the builder

- Exact drawable XML for badge, tiles, banner, chip and rail buttons; the
  `styles.xml` and `dimens.xml` entries; one `colors.xml` per app with identical
  values.
- View ids and DataBinding wiring for each new element, and the mapping from
  live state to each readout.
- The snapshot fetch + LRU cache in the viewer, with the `404` placeholder.
- Service API for the new overflow actions: `ACTION_RESTART_CAMERA` and
  `ACTION_RESET_TOKEN`, both confirmed by a dialog in the UI.
- Whether `:common` exposes the palette as resources or each app duplicates the
  `colors.xml` — call it: **duplicate the file in both apps** with identical
  values. Sharing resources across two application modules adds resource-merging
  coupling for no gain.
- Exact copy for new strings, written in plain language: what happened, what to
  do, no apologies, no filler.

---

## 12. Verification

**Automated (this machine):** `assembleDebug lintDebug testDebugUnitTest` with
the bundled Gradle 8.13 + JDK 17 — green. Unit tests for the shared models
(serialisation round-trip), the status JSON shape, and the host-selection
priority.

**Viewer on device (vivo V2434, API 36, connected over ADB):** install the
debug APK, pair by QR, and verify the list, thumbnails, live playback, history
playback and both 401 scenarios are gone. Screenshots taken on device. Narrow
layout is checked by temporarily resizing the device
(`adb shell wm size` / `wm density`) to a 320dp-wide viewport to stand in for
the A2 Core, then restoring it.

**Camera app:** the A2 Core cannot be reached by ADB (broken USB, and Android 8
has no wireless debugging). Verification is therefore:

1. Build green.
2. Install the camera APK on the connected vivo as a smoke test — it has a
   camera — to catch resource and layout errors, accepting that API 36 differs
   from API 27 (notably, Android 14+ forbids starting a camera foreground
   service from `BOOT_COMPLETED`, so boot auto-start cannot be exercised there).
3. **Manual step with the owner:** install the release candidate on the A2 Core
   by downloading it from the PC over Wi-Fi (`tools/serve-apk.sh`), then check
   the dashboard at 320×569dp, the preview surface, and the fault banner, and
   confirm the values match
   `curl -H "Authorization: Bearer <token>" http://<tailnet-ip>:8080/api/status`.
   The API can be exercised from the PC over the LAN or tailnet without ADB —
   that is the primary functional check for the camera side.
4. Font scale 1.3 on the dashboard.

---

## 13. Dependencies outside this document

The capture pipeline (frame delivery, encoder configuration, recording muxing,
event writing, the nightly-restart fix, ntfy push, release signing) is
specified separately and is the next plan. Until it lands the camera cannot
produce frames, so motion detection and recording stay unimplemented — and the
UI says exactly that.
