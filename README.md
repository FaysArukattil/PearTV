# CleanYourTV — Production Android TV Launcher, Debloater & System Care

<div align="center">
  <h3>Minimalist Apple tvOS-Style Launcher • On-Device TV Debloater • Samsung Device Care Storage • Samsung My Files Manager</h3>
  <p><strong>100% Remote-Driven • Zero PC Required • Universal TV & Android Box Compatibility (API 21+)</strong></p>
</div>

---

## 📺 Overview

**CleanYourTV** is a production-grade, standalone Android TV launcher and system management suite built with Jetpack Compose for TV, Kotlin, and coroutines. It eliminates all TV advertisements, sponsored content recommendations, manufacturer background telemetry, and launcher lag—transforming any Android TV, Google TV, Fire TV, stick, or projector into a blazing fast, minimalist entertainment hub matching the visual polish of Apple tvOS.

Unlike previous debloating scripts or tools that required a PC, terminal commands, or complicated USB cables, CleanYourTV is designed around a single principle: **the user installs one APK on their TV and controls everything entirely from their couch using only the TV remote control.**

---

## 🌟 The 13 Core Features

### 1. Apple tvOS Minimalist Launcher
- **Frosted Top Bar**: Live display clock (`1:13 PM | SATURDAY 17 OCTOBER`), greeting kicker (`GOOD AFTERNOON`), and frosted glass capsules for Inputs, Network SSID, Quick Share, Storage, Files, Profile, Themes, Settings, and CleanTV.
- **Dynamic Ambient Hero Glow**: Automatically extracts the dominant palette from the currently focused application or wallpaper theme, casting a smooth, multi-pass atmospheric halo behind the top-left title.
- **Top Shelf Display**: Prominent 46sp bold hero title matching the active card.

### 2. Intelligent App Shelves & Sections
- **Top Shelf / Dock**: Fast access to your top 4 pinned or favorite apps.
- **Frequently Used Row**: Ranks apps by real-world launch count tracked privately on-device in `Journal.kt`.
- **Recently Used Row**: Chronological history ordered by last opened timestamp.
- **All Applications Grid**: Responsive 4-column layout for all installed streaming apps, utilities, and games.
- **Context Action Menu**: Long-press OK or press `MENU` on any tile to Open, Pin to Dock, Clear Data, Clear Cache, Hide from Launcher, or Uninstall.

### 3. Live Hardware TV Input Switcher
- Live integration with `TvInputManager` actively querying physically present passthrough inputs (HDMI 1, HDMI 2, HDMI 3, TV Tuner, AV/Composite).
- **Intelligent Port State Sensing**: Connected ports are rendered in vibrant color with a `● Connected` status badge; empty/disconnected ports are gracefully grayed out with `○ Disconnected`.
- 1-click direct port switching via `TvContract.buildChannelUriForPassthroughInput`.

### 4. Samsung Device Care Style Storage Manager
- **Multi-Segmented Storage Bar**: Real-time visual gauge divided among Applications (Blue), Videos (Purple), Audio (Cyan), Images (Orange), Installation APKs (Yellow), System OS (Slate), and Available Free Space (Dark).
- **1-Click Cache Cleaner**: Reclaims system caches across internal and external storage with visual feedback.
- **Per-App Storage Stats**: Utilizes `StorageStatsManager` (API 26+) to display exact code size, user data, and cache footprint per application, with remote actions for Clear Cache, Clear Data, and Uninstall.

### 5. Native TV System Settings Shortcut
- Top bar Settings capsule directly invokes the TV's native settings (`com.android.tv.settings.MainSettings` or `Settings.ACTION_SETTINGS`), backed by remote keyevent fallbacks (`KEYCODE_SETTINGS`).

### 6. Google Account Profile & Switcher
- Automatically queries the logged-in Google account via `AccountManager` and displays the user's name and initial in a frosted top capsule.
- Profile dialog shows active account, list of all synced accounts, and direct shortcuts to Add Google Account or open System Accounts.

### 7. Samsung "My Files" Style TV File Manager
- **Category Browsing**: Downloads, Videos, Music, APK Installation Files, Photos, Documents.
- **Storage Volumes**: Auto-detects Internal Storage and any mounted USB Flash Drives or external hard drives in `/storage/*`.
- **Remote D-Pad Navigation**: Folder tree navigation with breadcrumb trails.
- **Built-in Actions**: Install APKs directly via Android's `PackageInstaller`, stream videos/audio in your preferred TV media player via secure `FileProvider`, rename files, and delete unwanted files.

### 8. Dual-Mode Quick Share (Phone-to-TV Transfer)
- **Mode 1: Native Google Quick Share**: Direct intent launch for Google Play Services Nearby Share (`ReceiveSurfaceActivity`).
- **Mode 2: Universal Wi-Fi AirShare**: Built-in lightweight web receiver running on the TV. Displays a QR code and 4-digit PIN. Any phone (iPhone, Android) or PC on the same Wi-Fi can scan the QR code in their browser and transfer files, videos, APKs, or links directly to the TV with **zero app installation required on the phone**!

### 9. First-Run Auto Debloat Wizard
- **Automatic Device Probe**: Detects manufacturer brand and model (`ro.product.brand`, `ro.product.model`).
- **Comprehensive Bloat Catalog**: Over 5,000 package signatures mapped to safety tiers (Safe, Caution, Do Not Remove).
- **Granular Keep/Remove Toggles**: Custom toggles for Miracast, Google Assistant, Chromecast, Stock Launcher, Netflix, Prime Video, and OEM media players.
- **Never Bricks System**: Hardened core blacklist (`com.android.systemui`, `com.android.settings`, package installers, TV input hardware middleware).
- **100% Reversible**: Every modification is recorded in `Journal.kt`. 1-click restore via `pm enable-user` or `cmd package install-existing`.
- **Guided Remote Authorization**: In-app step-by-step remote walkthrough for enabling USB/Wireless debugging without a computer.

### 10. Built-in Wallpaper Themes & Personalization
- **Dynamic Gradient Themes**:
  - *Aurora Glow*: Deep cosmic emerald and indigo gradients.
  - *Pitch Black*: OLED pure black with minimal specular vignette.
  - *Deep Space*: Cosmic violet and nebula sapphire.
  - *Sunset Crimson*: Warm burgundy, wine, and amber glow.
  - *Apple Dark Glass*: Frosted slate with dynamic hero color accent.
- Custom wallpaper selection from TV storage via File Manager.
- Clock display format toggle (12 Hours AM/PM vs 24 Hours).
- Greeting kicker toggle.

### 11. Universal TV Support & ABI Builds
- Compatible with all Android TV, Google TV, Fire TV, and Android boxes/sticks from Android 5.0 (API 21) up to Android 15 (API 35).
- R8-minified, resource-shrunk release APKs:
  - `app-universal-release.apk` (1.29 MB) — Universal installer for all chipsets.
  - `app-armeabi-v7a-release.apk` (1.27 MB) — 32-bit ARM (Fire TV sticks, budget boxes).
  - `app-arm64-v8a-release.apk` (1.27 MB) — 64-bit ARM (Google TV, Sony Bravia, Shield TV).
  - `app-x86-release.apk` / `app-x86_64-release.apk` (1.27 MB) — Intel x86 TV emulators.

### 12. Real-Time TV System Monitor
- Slide-in monitor panel providing live RAM usage, CPU core count, and active background processes.
- "Clean All Running Processes" action to reclaim memory on weak 1-2 GB RAM hardware.

### 13. Universal TV-Style App Icon Engine
- Automatically queries widescreen 16:9 banners (`getActivityBanner` / `getApplicationBanner`).
- For mobile or sideloaded APKs without TV banners, automatically extracts dominant colors from the icon, generates a 16:9 squircle gradient plate, centers a crisp 48dp icon, and adds a sleek bottom title—making every app look like a native TV app from the Play Store!

---

## 🎮 TV Remote Navigation & Controls Map

| Remote Key | Home Screen Action | Dialog / Manager Action |
|:---|:---|:---|
| **D-Pad Left / Right** | Move focus between tiles in row / capsule bar | Navigate columns and action pills |
| **D-Pad Up / Down** | Move focus between Top Bar, Dock, Rows, Grid | Scroll lists and categories |
| **Center / OK / Select** | Launch app or open capsule | Open folder, install APK, toggle option |
| **Long-Press OK / Menu** | Open App Context Menu | Open file actions (Rename, Delete, Info) |
| **Back Button** | Close open modal/dialog; never exits launcher | Go up one directory or close screen |

---

## 🛠️ Build Commands & Terminal Guide

### 1. Clean Stale Builds & Build Release APKs
```bash
# Clean previous build artifacts and build release APKs
./gradlew clean assembleRelease
```

### 2. Output APK Locations
All compiled release APKs are signed with the debug keystore for instant sideloading:
- **Universal APK (Recommended for all TVs):**
  `app/build/outputs/apk/release/app-universal-release.apk`
- **32-bit ARM APK:**
  `app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
- **64-bit ARM APK:**
  `app/build/outputs/apk/release/app-arm64-v8a-release.apk`

### 3. Install on TV via Network ADB
Ensure your TV and computer are on the same Wi-Fi network and USB/Network debugging is enabled:
```bash
# Connect to your TV's IP address (e.g. 192.168.1.100)
adb connect <TV_IP_ADDRESS>:5555

# Verify connection
adb devices

# Install CleanYourTV
adb install -r app/build/outputs/apk/release/app-universal-release.apk

# Launch CleanYourTV directly
adb shell monkey -p com.leantv.app -c android.intent.category.LAUNCHER 1
```

---

## 📚 Project Documentation

- **[docs/PROJECT_GUIDE.md](docs/PROJECT_GUIDE.md)**: Exhaustive technical manual, architecture layers, permission breakdowns, and navigation maps.
- **[docs/REFERENCE_NOTES.md](docs/REFERENCE_NOTES.md)**: In-depth comparative analysis between the reference repository (`cleanyourtv`) and CleanYourTV's modern 100% on-device architecture.

---

## 📄 License & Attribution

- Built as a clean-room Kotlin & Jetpack Compose implementation for Android TV.
- Reference package dumps and design geometry studied from `https://github.com/Gathin23/cleanyourtv` under the MIT License.
