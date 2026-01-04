# AutoTerm3

A debug terminal app for Android Auto that renders monospaced text output on your car's head unit display.

## Overview

AutoTerm3 uses the Android Auto Car App Library to display a scrolling text buffer on the car's infotainment screen. It listens for input over TCP, allowing you to pipe terminal output from Termux, a Raspberry Pi via SSH, or any networked device.

## Features

- **Custom Surface Rendering**: Uses `NavigationTemplate` to access a raw drawing canvas
- **Monospaced Terminal Font**: White text on black background, 15px fixed size
- **TCP Server**: Listens on port 9000 for incoming text streams
- **Auto-Scrolling Buffer**: Circular buffer of 200 lines, newest at bottom
- **Real-time Updates**: Refreshes every second

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Auto Host                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  NavigationTemplate                   │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │              SurfaceContainer                  │  │  │
│  │  │  ┌──────────────────────────────────────────┐  │  │  │
│  │  │  │         Canvas (800x400 @ 160dpi)        │  │  │  │
│  │  │  │                                          │  │  │  │
│  │  │  │   AUTOTERM3 - LISTENING ON PORT 9000     │  │  │  │
│  │  │  │   Hello from Termux                      │  │  │  │
│  │  │  │   $ sensors                              │  │  │  │
│  │  │  │   temp1: 45.0°C                          │  │  │  │
│  │  │  └──────────────────────────────────────────┘  │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ TCP Port 9000
                              │
              ┌───────────────┴───────────────┐
              │                               │
         ┌────┴────┐                   ┌──────┴──────┐
         │ Termux  │                   │ Raspberry Pi│
         │ nc ...  │                   │ ssh | nc ...│
         └─────────┘                   └─────────────┘
```

### Components

| File | Purpose |
|------|---------|
| `TerminalCarAppService.kt` | Entry point, creates session |
| `TerminalSession.kt` | Manages car host connection |
| `TerminalScreen.kt` | Rendering, TCP server, buffer management |
| `automotive_app_desc.xml` | Declares app as `template` + `navigation` |

## Permissions

| Permission | Reason |
|------------|--------|
| `androidx.car.app.NAVIGATION_TEMPLATES` | Required for `NavigationTemplate` |
| `androidx.car.app.ACCESS_SURFACE` | Required for `SurfaceCallback` |
| `android.permission.INTERNET` | Required for TCP socket |

## Setup

### Prerequisites

- Android Studio
- Android SDK 34+
- Desktop Head Unit (DHU) for testing, or a compatible car

### Build & Install

```bash
# Build and install debug APK
./gradlew installDebug
```

### Testing with DHU

```bash
# Start the emulator with Google Play
emulator @Pixel_7_API_34

# Forward ADB port for DHU
adb forward tcp:5277 tcp:5277

# Start DHU
~/Library/Android/sdk/extras/google/auto/desktop-head-unit

# Forward the TCP port for terminal input
adb forward tcp:9000 tcp:9000
```

## Usage

### Sending Text to the Terminal

Once the app is running on Android Auto:

```bash
# Simple test
echo "Hello Android Auto" | nc localhost 9000

# Stream system logs
dmesg -w | nc localhost 9000

# Monitor sensors from a Raspberry Pi
ssh pi@192.168.1.100 "watch -n1 sensors" | nc localhost 9000

# Continuous data stream
while true; do date; sleep 1; done | nc localhost 9000
```

### From Termux (on the same phone)

```bash
# Direct localhost connection (no adb forward needed)
echo "Hello from Termux" | nc localhost 9000

# SSH tunnel from Pi
ssh pi@raspberry "tail -f /var/log/syslog" | nc localhost 9000
```

## Configuration

Edit `TerminalScreen.kt` to customize:

| Setting | Default | Location |
|---------|---------|----------|
| Port | `9000` | `ServerSocket(9000)` |
| Buffer size | `200` lines | `bufferSize = 200` |
| Text size | `15f` px | `textPaint.textSize = 15f` |
| Refresh rate | `1000ms` | `REFRESH_INTERVAL_MS` |

## Limitations

### DHU vs Real Car

| Environment | Navigation Apps | Side-loading |
|-------------|-----------------|--------------|
| Desktop Head Unit | ✅ All work | ✅ No restrictions |
| Most cars (Honda, Hyundai) | ✅ Works | ✅ Enable "Unknown sources" |
| Toyota/Lexus 2020+ | ❌ Blocked | ❌ Play Store only |
| Some Ford/GM | ❌ Blocked | ❌ Play Store only |

If your car blocks side-loaded NAVIGATION apps, you can:
1. Switch to `IOT` category (loses custom Surface, uses PaneTemplate)
2. Publish to Google Play Store

## Troubleshooting

### App not visible in car launcher
1. Enable **Developer settings** in Android Auto (tap version 10x)
2. Enable **Unknown sources**
3. Set **Application Mode** to "Developer"
4. Clear Android Auto cache and force stop
5. Reconnect to car

### "Address already in use" when forwarding port
```bash
# Kill existing forward
adb forward --remove tcp:9000

# Or use a different port
adb forward tcp:9001 tcp:9000
```

### Black screen, no text
- Check logcat for Surface availability:
  ```bash
  adb logcat | grep TerminalScreen
  ```
- Ensure `ACCESS_SURFACE` permission is granted

## License

Private / Personal Project
