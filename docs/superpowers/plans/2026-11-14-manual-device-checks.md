# Manual device checks — A2 Core and viewer phone

The camera phone has no working USB port and Android 8 has no wireless debugging,
so these checks are run by hand. The PC-side server is already running:

```
http://192.168.1.65:8000/freecam-camera-debug.apk
http://192.168.1.65:8000/freecam-viewer-debug.apk
```

Refresh the APKs after a build with:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\serve-apk.ps1 -NoBuild
```

## On the camera phone (Galaxy A2 Core)

1. Open the camera APK URL in the phone browser, allow installs, install, open.
2. Grant the camera permission when asked.
3. The dashboard shows `STOPPED`. Press **Start streaming**.
   - The badge must switch to **REC** and the uptime must start counting.
   - If a fault banner appears, it names the cause (for example
     `Encoder rejected the settings`).
4. Read the **Address** row in the telemetry. It is the tailnet address when
   Tailscale is up, otherwise the Wi-Fi address.
5. Open **Pair viewer** and copy the token.
6. Check the API from the PC:

   ```powershell
   curl.exe -H "Authorization: Bearer <token>" http://<phone-ip>:8080/api/status
   curl.exe -H "Authorization: Bearer <token>" http://<phone-ip>:8080/api/recordings
   curl.exe -H "Authorization: Bearer <token>" http://<phone-ip>:8080/api/events
   ```

   `/api/status` must show `"running":true` and `"error":null`. `/api/recordings`
   must list a clip with a real `duration_sec`.
7. After a few minutes, a 5-minute segment must appear on the phone under
   `Android/data/com.freecam.camera/files/recordings/`.
8. Turn the screen off and wait 30 seconds. The stream must keep running (the
   encoder is headless, no view is attached).
9. Reboot the phone. The camera must start by itself (Settings → Start on Boot).
10. Turn **Preview** on. The stream restarts and the preview shows the camera
    image, so the camera can be aimed. Turn it off again to go back to headless.
11. Set the font scale to the largest size and check the dashboard still fits:
    the badge, uptime and the primary action must stay visible, and the telemetry
    scrolls.

## Playing the stream in VLC

From any machine that can reach the phone (same Wi-Fi or the tailnet):

```
rtsp://freecam:<token>@<phone-ip>:8554/live
```

Use **Media → Open Network Stream** and paste that URL.

- Without the credentials the server answers `401 Unauthorized`. That is the
  authentication working, not a fault.
- If VLC shows the stream but no picture, check the camera's **Logs** screen.

## On the viewer phone

1. Install the viewer APK the same way.
2. Scan the QR code on the camera's **Pair viewer** screen.
3. The camera appears in the list with a thumbnail (or a placeholder if the camera
   cannot produce a frame yet), its live state and its address.
4. Tap it: the live picture appears with a `● LIVE` chip.
5. Open **History**: the recorded clip plays. **Events** lists what the camera
   recorded.
6. Stop the camera on the camera phone. The viewer should show a named fault
   (`Camera is offline`) with a Retry action, not a silent black screen.

## Narrow screen check (optional)

The camera phone is about 320 dp wide. To see the same width on a modern phone:

```powershell
adb shell wm size 600x1069
adb shell wm density 300
```

When you are done, always put it back:

```powershell
adb shell wm size reset
adb shell wm density reset
```
