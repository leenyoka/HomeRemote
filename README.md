# HomeRemote

Turn any phone or tablet on your home WiFi into a full remote control for your Android TV.

The TV runs a small HTTP server. Any browser on the same network opens the remote UI — no app install on the phone needed.

## Features

- **D-pad, volume, back, home, power** — all the basics
- **Keyboard tab** — type text directly on the TV
- **Announce tab** — send a pop-up message to the TV screen with an alert tone and optional sender name
- **Now-playing indicator** — shows whether audio is playing or the TV is idle
- **QR code on screensaver** — scan to connect while the TV is at rest
- **Friendly URL** — set a static DNS entry on your router so phones reach the remote at `http://homeremote:8080` instead of a raw IP
- **Starts on boot** — the server comes up automatically after a reboot

## How it works

```
Phone browser  ──HTTP──►  NanoHTTPD server (port 8080)
                               │
                    RemoteAccessibilityService
                    (injects D-pad key events)
```

- [`RemoteServer`](app/src/main/java/com/homeremote/RemoteServer.kt) — NanoHTTPD serving the web UI and API endpoints
- [`RemoteAccessibilityService`](app/src/main/java/com/homeremote/RemoteAccessibilityService.kt) — injects key events via the accessibility API
- [`RemoteService`](app/src/main/java/com/homeremote/RemoteService.kt) — foreground service tying everything together; handles overlays, NSD, screensaver events
- [`MessageOverlay`](app/src/main/java/com/homeremote/MessageOverlay.kt) — `TYPE_APPLICATION_OVERLAY` pop-up for announcements
- [`QrOverlay`](app/src/main/java/com/homeremote/QrOverlay.kt) — QR code shown in the top-right corner during the screensaver
- [`RemoteDreamService`](app/src/main/java/com/homeremote/RemoteDreamService.kt) — custom Android screensaver (DreamService)
- [`index.html`](app/src/main/assets/remote/index.html) — the phone/tablet UI (plain HTML + JS, served directly by the TV)

## Requirements

- Android TV running **Android 8.0+** (tested on Mi Box 4 / Android 12)
- Android Studio + SDK (for building)
- `adb` in your PATH

## Setup

### 1. Enable ADB on the TV

Settings → Device Preferences → About → Build number (click 7×) → Developer options → USB debugging: **On**

Connect over WiFi:

```bash
adb connect YOUR_TV_IP:5555
```

### 2. Grant overlay permission

After the first install, the TV will prompt for "Display over other apps" permission. Grant it, or run:

```bash
adb shell appops set com.homeremote SYSTEM_ALERT_WINDOW allow
```

### 3. Enable the accessibility service

Settings → Device Preferences → Accessibility → HomeRemote → **On**

The deploy script re-enables this automatically after each install.

### 4. (Optional) Friendly URL

Add a static DNS entry on your router mapping `homeremote` → your TV's IP. Phones can then reach the remote at `http://homeremote:8080` without knowing the IP.

On iOS/Android you can also "Add to Home Screen" from the browser to get a standalone app icon that opens straight to the remote.

## Build & deploy

```bash
echo "YOUR_TV_IP" > .tv_ip   # one-time setup, file is gitignored
bash deploy.sh
```

The script builds the APK, installs it, restarts the app, and re-enables the accessibility service.

To set the TV IP via environment variable instead:

```bash
TV_IP=192.168.1.100 bash deploy.sh
```

## API

| Endpoint | Method | Description |
|---|---|---|
| `/` | GET | Serves the remote UI |
| `/api/ping` | GET | Health check — returns `pong` |
| `/api/status` | GET | `{"playing": true/false}` |
| `/api/cmd` | POST | Send a command (see below) |

### Commands (`POST /api/cmd`)

```json
{ "action": "key",     "value": "DPAD_CENTER" }
{ "action": "volume",  "value": "up" }
{ "action": "message", "value": "Alice\nDinner is ready|8000" }
{ "action": "dismiss" }
{ "action": "launch",  "value": "com.package.name" }
```

Message format: `"[SenderName]\n[body]|[durationMs]"` — the sender name line is optional.

## Security note

This server has no authentication. It is intended for trusted home networks only. Do not expose port 8080 to the internet.

The file `app/src/main/assets/adb/adbkey` (an ADB RSA private key) is gitignored and must never be committed.

## License

MIT
