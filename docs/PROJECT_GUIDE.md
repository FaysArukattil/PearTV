# CleanYourTV — Production Project Guide & Architecture Manual

## 1. Project Mission & Design Philosophy

**CleanYourTV** is an autonomous, production-grade Android TV launcher combining:
1. **Apple tvOS Minimalist Visual Elegance**: Frosted glass capsules, dynamic ambient radial glow matching the focused app, specular 16:9 widescreen tiles with 1.10x focus scaling.
2. **On-Device TV Debloater**: 100% remote-controlled, requiring zero external PCs, cables, or typed ADB commands.
3. **Samsung Device Care Storage Manager**: Segmented memory and storage gauge (Apps, Media, Files, System, Free), per-app storage stats, and 1-click cache cleaning.
4. **Samsung My Files TV File Manager**: Categories (Downloads, Videos, Audio, APKs, Images, Documents), auto-detection of USB drives, direct APK installer, and media viewer.
5. **Hardware TV Input Switcher**: Live detection of physical ports (HDMI 1, HDMI 2, Tuner, AV) with connected vs disconnected state sensing.
6. **Dual-Mode Quick Share**: Native Google Quick Share intent + zero-install local Wi-Fi web receiver with QR code & PIN.

---

## 2. Technical Stack & Architecture

- **Language & Runtime**: Kotlin 2.0+ on Android API 21 (Lollipop) through API 35 (Android 15).
- **UI Framework**: Jetpack Compose for TV (Material 3 TV & TV Foundation) utilizing smooth hardware-accelerated rendering.
- **Architectural Pattern**: MVVM (Model-View-ViewModel) with unidirectional reactive data flows (`StateFlow`).
- **Persistence**: SharedPreferences / Journal with rollback ledgers, app usage metrics, theme preferences, and hidden package registries.
- **On-Device ADB**: Pure-Kotlin `dadb` client connecting directly to localhost or network debugging ports on the TV.
- **Universal Packaging**: Universal APK (`app-universal-release.apk`) + Per-ABI APKs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) with ProGuard/R8 dead-code stripping.

---

## 3. The 13 Core Features

### 1. Home Launcher (Apple tvOS & Google TV Aesthetic)
- **Top Bar**:
  - Greeting kicker (`GOOD MORNING`, `GOOD AFTERNOON`, `GOOD EVENING`).
  - Ultra-clean display clock (`1:13 PM | SATURDAY 17 OCTOBER`) with 12h/24h toggle.
  - Frosted glass capsules: Inputs, Network status, Quick Share, Storage, Files, Profile, Themes, Settings, CleanTV.
- **Ambient Dynamic Glow**:
  - Automatically extracts the dominant hue of the currently focused app or theme.
  - Renders a multi-pass radial atmospheric halo behind the top-left hero title.
- **Top Shelf Hero Display**:
  - Prominent 46sp bold single-line app title.

### 2. App Rows & Adaptive Organization
- **Top Shelf / Dock**: Top 4 pinned or favorite applications.
- **Frequently Used Row**: Ranks apps by total launch count recorded in `Journal.kt`. Automatically appears once apps have been opened.
- **Recently Used Row**: Ranks apps by timestamp of last execution.
- **All Applications Grid**: 4-column responsive grid containing all launchable TV and sideloaded apps.
- **Long-Press / Menu Key Context Menu**:
  - Open Application
  - Pin to Dock / Unpin
  - Clear Application Data
  - Clear Cache
  - Hide from Launcher Grid
  - System App Info
  - Uninstall Application

### 3. Live TV Hardware Input Switcher
- Queries `android.media.tv.TvInputManager` for physical passthrough inputs (`isPassthroughInput`).
- Real-time connection sensing via `TvInputCallback`:
  - **Connected Ports** (cable plugged in): Rendered in vibrant blue glass with a green `● Connected` indicator.
  - **Disconnected Ports**: Subdued in dark translucent glass with `○ Disconnected`.
- 1-click switching via `TvContract.buildChannelUriForPassthroughInput` with fallback to OEM input activities.

### 4. Storage Manager (Samsung Device Care Style)
- **Multi-Segmented Progress Bar**:
  - Blue: Applications
  - Purple: Videos
  - Cyan: Audio
  - Orange: Images
  - Yellow: Installation APKs
  - Slate: System OS
  - Dark: Available Free Space
- **1-Click Optimize**: Reclaims temporary caches across internal and external storage directories.
- **Per-App Storage Breakdown**: Lists each installed app with total size, code size, data size, and cache size (via `StorageStatsManager`), with remote actions for Clear Cache, Clear Data, and Uninstall.

### 5. Profile & Google Account Switcher
- Displays logged-in Google profile name and initial in the top navigation capsule via `AccountManager`.
- Profile dialog shows active account, list of synced accounts, direct "Add Google Account" button, and system account manager shortcut.

### 6. Built-in TV File Manager (Samsung My Files Style)
- **Categories**: Downloads, Videos, Audio, APK Installation Files, Images, Documents.
- **Drives**: Auto-detects Internal Storage and USB Flash Drives / External Hard Drives mounted in `/storage/*`.
- **Remote D-Pad Navigation**: Folder tree browsing with breadcrumbs.
- **Built-in Actions**:
  - Direct APK installation with Android `PackageInstaller`.
  - Media playback in default TV video player or gallery via `FileProvider`.
  - Delete file with confirmation dialog.
  - Rename file with on-screen TV dialog.

### 7. Dual-Mode Quick Share (Phone-to-TV Transfer)
- **Mode 1: Native Google Quick Share**: Directly invokes `com.google.android.gms.nearby.sharing.ReceiveSurfaceActivity`.
- **Mode 2: Universal Wi-Fi AirShare**: Built-in HTTP web server on the TV that displays a large QR code and 4-digit PIN. Any phone (iPhone, Android) or PC on the same Wi-Fi can scan the QR code in their browser and upload videos, movies, APKs, or text directly to the TV—**zero app installation required on the phone**!

### 8. First-Run Auto Debloat Wizard
- **TV Model Probe**: Automatically identifies brand (`ro.product.brand`) and model (`ro.product.model`).
- **Bloat Catalog**: Over 5,000 package signatures mapped to risk tiers:
  - *Safe*: Telemetry, tracking, ad services, retail demos, background statistics.
  - *Caution*: Miracast, Google Voice Assistant, Chromecast, Netflix, Prime Video, Stock Launcher.
  - *Do Not Remove*: Core Android framework, TV input middleware, package installer.
- **Reversibility Guarantee**: Every modification is recorded in `Journal.kt`. 1-click rollback via `pm enable-user` or `cmd package install-existing`.
- **Guided Remote Authorization**: In-app step-by-step remote guide to enable USB/Wireless debugging without a PC.

### 9. Wallpaper & Personalization
- Built-in dynamic gradient themes:
  - **Aurora Glow**: Deep cosmic emerald and indigo gradients.
  - **Pitch Black**: OLED pure black with subtle specular rim.
  - **Deep Space**: Cosmic violet and nebula sapphire.
  - **Sunset Crimson**: Warm burgundy, wine, and amber glow.
  - **Apple Dark Glass**: Frosted slate with dynamic hero color accent.
- Custom wallpaper selection from TV storage via File Manager.
- Clock format toggle (12 Hours AM/PM vs 24 Hours).
- Greeting toggle (show/hide kicker).

### 10. Universal TV-Style App Icon Engine
- Inspects `getActivityBanner` and `getApplicationBanner` for 16:9 widescreen TV banners.
- If missing (e.g. mobile sideloaded APK), auto-generates a 16:9 squircle card:
  - Extracts icon dominant color palette for a smooth vertical gradient plate.
  - Centers a crisp 48dp app icon.
  - Renders a semi-translucent bottom label.
  - Every sideloaded APK looks like a first-class TV app from the Play Store!

---

## 4. TV Remote Navigation & Controls Map

| Remote Button | Action on Home Screen | Action in File Manager / Dialogs |
|---|---|---|
| **D-Pad Left / Right** | Move focus between tiles in row / capsule bar | Navigate columns and action pills |
| **D-Pad Up / Down** | Move focus between Top Bar, Dock, Rows, Grid | Scroll file lists and categories |
| **Center / OK / Select** | Launch selected application or open capsule | Open folder, install APK, or toggle option |
| **Long-Press OK / Menu** | Open Context Action Menu (Clear Data, Pin, Hide, Uninstall) | Open file options (Rename, Delete, Details) |
| **Back Button** | Close open dialog or overlay; never exits launcher | Go up one directory level or close file manager |

---

## 5. Build Artifacts & Installation

```bash
# Clean project and compile release APKs
./gradlew clean assembleRelease

# Generated Release APKs:
# Universal APK (All TV devices):
# app/build/outputs/apk/release/app-universal-release.apk
# Per-ABI APKs:
# app/build/outputs/apk/release/app-armeabi-v7a-release.apk
# app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

To install on your TV over local network ADB:
```bash
adb connect <TV_IP_ADDRESS>:5555
adb install -r app/build/outputs/apk/release/app-universal-release.apk
```
