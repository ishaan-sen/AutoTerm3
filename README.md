# AutoTerm3

**AutoTerm3** is an Android Auto application that functions as a debug terminal on your car's head unit. It renders a scrolling text buffer to a custom Surface using the Android Auto `NavigationTemplate`.

Designed for developers and hobbyists, it allows you to pipe terminal output (from Termux, a Raspberry Pi via SSH, or your local machine) directly to your car's dashboard.

## Features

- **Custom Surface Rendering**: Uses `NavigationTemplate` to access a canvas for raw drawing, bypassing standard Car App Library template limitations.
- **Monospaced Terminal Text**: Renders white text on a black background using a fixed 15px monospace font.
- **TCP Input Server**: Listens on **TCP port 9000** for incoming text streams.
- **Auto-Scrolling Buffer**: Maintains a circular buffer of the last 200 lines and auto-scrolls to new input.
- **Icon-Only UI**: Minimalist interface with a hidden "Pan" action strip to maximize screen real estate.

## Architecture

The app is built using the **Android for Cars App Library** (`androidx.car.app`).

### Key Components

- **`TerminalCarAppService`**: The entry point. Extends `CarAppService` and manages the host connection.
- **`TerminalSession`**: Manages the lifecycle of the app session.
- **`TerminalScreen`**: The core logic class.
    - **Rendering**: Implements `SurfaceCallback` to receive the `SurfaceContainer`. Uses standard android `Canvas` drawing operations to render text lines from the bottom up.
    - **Data**: Uses a thread-safe `ArrayDeque<String>` as a circular buffer for incoming lines.
    - **Networking**: runs a simple `ServerSocket` on port 9000 on a background thread. Incoming data is read line-by-line and added to the buffer.

### Permissions

- `androidx.car.app.NAVIGATION_TEMPLATES`: Required for using `NavigationTemplate`.
- `androidx.car.app.ACCESS_SURFACE`: Required for direct Surface access.
- `android.permission.INTERNET`: Required for the TCP server socket.

## Setup & Usage

### Prerequisites
- Android SDK
- Android Emulator with DHU (Desktop Head Unit) OR a real Android Auto head unit.
- `adb` (Android Debug Bridge)

### Installation
1.  Build and install the debug APK:
    ```bash
    ./gradlew installDebug
    ```
2.  Launch the Desktop Head Unit (if testing locally):
    ```bash
    desktop-head-unit
    ```
3.  Open **AutoTerm3** from the Android Auto launcher.

### Using the Terminal Input

The app listens on **TCP port 9000** on the device. To send text to it, you must route traffic to that port.

#### Option 1: Via ADB (Computer to Emulator/Phone)
Forward the port from your computer to the Android device:

```bash
adb forward tcp:9000 tcp:9000
```

Then send text using `netcat`:

```bash
# Send a single message
echo "Hello Android Auto" | nc localhost 9000

# Stream persistent data (e.g. system logs)
dmesg -w | nc localhost 9000
```

#### Option 2: Via Termux (On-Device)
If you are running Termux on the **same device** as AutoTerm3, you can connect directly to `localhost`:

```bash
# Pipe SSH output from a Raspberry Pi
ssh pi@192.168.1.100 "tail -f /var/log/syslog" | nc localhost 9000
```

### Troubleshooting
- **Black Screen**: Ensure you have granted all permissions. The app creates the surface on the first valid frame; if no text appears, try sending a line to force a refresh.
- **"Address in use" error**: If port 9000 is taken, modify `TerminalScreen.kt` to use a different port.
- **Connection Refused**: Ensure `adb forward` is active (if using USB) or that you are on the same network interface (if using WiFi debugging).

## License
Private / Personal Project.
