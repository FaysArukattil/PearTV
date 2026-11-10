# LeanTV — Lightweight Android TV System Manager & Custom Launcher

LeanTV is a native Android TV system manager and custom launcher built using Jetpack Compose, Kotlin, and AndroidX. Designed specifically for low-overhead operation on Android TV and Google TV devices (including 1GB and 2GB RAM dongles and smart TVs), it combines an Apple tvOS-inspired lean home screen, deep installed package management, live RAM/CPU monitoring with background task termination, wireless local Quick Share, and on-device ADB debloating.

---

## Key Features

### 1. Apple tvOS-Inspired Custom Launcher (`HomeActivity`)
- **Fluid 60fps TV Remote Navigation:** Full D-pad navigation with focus scaling, glowing borders, and reactive ambient backdrop lighting.
- **Top Featured Shelf:** Large hero cards for recently opened and prominent applications.
- **Favorites & All Apps Rows:** Quick access to pinned favorites and complete horizontal application shelves with in-memory icon caching.
- **Live Digital Clock & Date:** Integrated top-corner clock and date display updated efficiently via coroutines.
- **App Management via Remote:** Long-press (or press the `MENU` key on your remote) on any app card to open, toggle favorites, trigger uninstallation, or inspect system application details.
- **Default Launcher Setup:** Guided setup flow using Android's `RoleManager.ROLE_HOME` (Android 10+) or system Home settings.

### 2. Installed Apps Explorer & Remote Uninstaller
- **Complete Package Inspection:** Lists all user and system packages installed on the device.
- **Detailed Metadata:** Displays application icon, label, package name, version, APK size (including split APKs), and install date.
- **Filter & Sort:** Toggle between **All Apps**, **User Apps**, and **System Apps**. Sort instantaneously by **Name (A-Z)**, **Size (Largest)**, or **Install Date (Newest)**.
- **Search:** Search across app labels and package names using the TV remote or voice IME.
- **Remote-Friendly Uninstaller:** Uninstalls user apps and updated system packages via `Intent.ACTION_DELETE` with a safety-first confirmation dialog (Cancel focused by default). System apps are clearly distinguished with safe handling.

### 3. Live System Monitor & Background Task Killer
- **Real-Time RAM Telemetry:** Shows total, used, and available RAM with smooth, color-graded animated usage bars (Mint `<70%`, Amber `70-85%`, Coral `>85%`).
- **Resilient CPU Telemetry:** Reports CPU core count and architecture with safe SELinux fallbacks on Android 10+ where `/proc/stat` access is restricted.
- **Running Processes List:** Shows active processes and background services with individual PSS memory footprint in megabytes.
- **Kill Task & Clean All:** Reclaim memory by terminating background caches and non-essential background processes via `ActivityManager.killBackgroundProcesses`.
- **Zero Background Waste:** Monitoring telemetry coroutines only run while the panel is visible on screen, immediately stopping when dismissed.

### 4. Wireless Quick Share (Local Network File & Link Transfer)
- **Zero-Dependency Local Server:** Embedded lightweight HTTP server running directly on the TV port `8765`.
- **Easy Pairing via QR & PIN:** Side panel generates a live QR code and random 4-digit security PIN. Scan with any smartphone camera or enter the local TV URL in your mobile browser.
- **Send Links & Text:** Send YouTube links, stream URLs, or text notes to the TV with one-click opening.
- **Direct File Uploads:** Upload APKs, photos, videos, or documents directly from your phone to `Downloads/LeanTV/` with real-time transfer progress.
- **Integrated File Opener:** Open received APKs, videos, or files directly from the Quick Share panel using Android's secure `FileProvider`.

### 5. On-Device ADB Debloater & Speed Tweaks
- **Built-in TCP ADB Client:** Connects to on-device Android debug daemon (`localhost:5555`) to disable OEM bloatware without a computer.
- **Curated Catalog:** Bundled safe lists for Xiaomi, Sony, TCL, Hisense, Philips, OnePlus, Realme, Nvidia Shield, and Fire TV.
- **Speed Tweaks:** One-click animation duration reduction (`0.5x`) and private DNS ad-blocking setup.
- **Undo / Restore Journal:** Tracks all debloating actions in private storage with one-click restore capabilities.

---

## Setting LeanTV as Default Home Launcher

Android OS security prevents any third-party app from setting itself as the default home launcher silently. To make LeanTV your default TV home screen:

1. When opening LeanTV for the first time, click **Set as Default** on the setup dialog.
2. If prompted by Android, select **LeanTV** and choose **Always**.
3. **Manual TV Settings Path:**
   - Open your TV's **Settings**.
   - Navigate to **Apps** &rarr; **Special app access** (or **Default apps**).
   - Select **Home app** &rarr; Choose **LeanTV**.
4. Every time you press the **Home** button on your remote or turn on your TV, LeanTV will launch instantly.

---

## Permissions Explained

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` | Required for local Quick Share HTTP server and on-device ADB loopback socket (`127.0.0.1:5555`). |
| `ACCESS_NETWORK_STATE` | Detects whether the TV is connected to local Wi-Fi or Ethernet for Quick Share. |
| `ACCESS_WIFI_STATE` | Retrieves the local Wi-Fi IPv4 address displayed for mobile pairing. |
| `QUERY_ALL_PACKAGES` | Required on Android 11+ (API 30+) to enumerate installed applications and system packages. |
| `REQUEST_DELETE_PACKAGES` | Allows LeanTV to invoke the system uninstaller prompt when you uninstall an app. |
| `KILL_BACKGROUND_PROCESSES` | Allows the System Monitor to terminate cached background processes to free RAM. |

---

## Building the Project

### Prerequisites
- JDK 17 (or newer)
- Android SDK with Build Tools 35.0.0 and SDK Platform 35
- Gradle 8.7+ (wrapper included)

### Compile Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Sideload onto Android TV
```bash
adb connect <YOUR_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture Summary

LeanTV is organized in modular layers:
- `com.leantv.app.launcher`: `HomeActivity`, `LauncherViewModel`, `HomeScreen` (Apple tvOS UI, carousel, shelves).
- `com.leantv.app.apps`: Installed app models, `InstalledAppsScanner`, and `InstalledAppsScreen`.
- `com.leantv.app.monitor`: `SystemMonitor`, telemetry models, and `SystemMonitorPanel`.
- `com.leantv.app.share`: `QuickShareServer`, `QrGenerator`, and `QuickSharePanel`.
- `com.leantv.app.adb` & `data`: On-device `AdbClient`, `Scanner`, `Catalog`, `Journal`, and `TweakEngine`.
- `com.leantv.app.ui`: Reusable D-pad components, fixed 1280x720 `TvCanvas` density scaling, and `Theme`.
