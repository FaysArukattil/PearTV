
# Changelog

All notable changes to the LeanTV project will be documented in this file.

## [Phase 4 - Quick Share Local Server & Side Panel] - 2026-11-10

### Added
- Added `ACCESS_WIFI_STATE` permission in `AndroidManifest.xml`.
- Added `com.google.zxing:core:3.5.3` for on-device QR code generation.
- Added `FileProvider` definition and `res/xml/file_paths.xml` for opening received files.
- Created `ShareModels.kt` defining `SharedFile`, `SharedText`, and `QuickShareState`.
- Created `QrGenerator.kt` for generating QR codes directly into `ImageBitmap`.
- Created `QuickShareServer.kt` implementing a lightweight embedded HTTP server:
  - Local Wi-Fi IPv4 discovery and port binding (`8765`).
  - Random 4-digit security PIN verification for session security.
  - Mobile web upload portal (HTML5/CSS/JS dark mode matching LeanTV aesthetic).
  - Text & link transmission receiver (`/api/message`).
  - Streaming file upload receiver (`/api/upload`) saving to `Downloads/LeanTV/`.
- Created `QuickSharePanel.kt` slide-in side panel with live Wi-Fi IP, QR code, PIN code display, transfer progress indicator, and received content list with 1-click open.
- Wired Quick Share lifecycle to panel visibility (server stops when panel is closed).

## [Phase 3 - Live System Monitor & Background Task Killer] - 2026-11-10

### Added
- Added `KILL_BACKGROUND_PROCESSES` permission in `AndroidManifest.xml`.
- Created `SystemMonitor.kt` providing:
  - Real-time RAM sampling using `ActivityManager.MemoryInfo` (total, available, used, percentage, low memory alert).
  - Resilient CPU architecture inspection with SELinux-safe fallback on Android 10+ (API 29+).
  - Running processes enumeration with per-app PSS memory footprint (MB) and system app detection.
  - Safe background process termination via `ActivityManager.killBackgroundProcesses` and batch kill all.
- Created `SystemMonitorPanel.kt` slide-in drawer UI with:
  - Dynamic animated RAM usage bar with smooth color transitions (Mint -> Amber -> Coral).
  - CPU architecture and core status card.
  - "Clean All" action button with background task transparency notice.
  - Scrollable running process list with app icons, PSS consumption, and per-process "Kill" buttons.
- Bound monitor polling in `LauncherViewModel` to panel visibility (starts on open, stops immediately on close to save TV CPU and power).

## [Phase 2 - Custom Launcher (Apple tvOS Style)] - 2026-11-10

### Added
- Declared `HomeActivity` in `AndroidManifest.xml` with `singleTask` launchMode and intent filters for `HOME`, `DEFAULT`, `LEANBACK_LAUNCHER`, and `LAUNCHER`.
- Implemented `LauncherViewModel.kt` managing launchable apps, featured carousel, favorites persistence in `Journal`, live clock/date ticker, and default launcher flow.
- Implemented `HomeScreen.kt` with Apple tvOS-inspired design:
  - Top header with live digital clock, date, and quick shortcut pills (Tools, Settings).
  - Ambient gradient backdrop with focus-reactive radial glow.
  - Featured Apps row with large 280x160dp cards, 1.08x scale elevation, and amber glowing border.
  - Favorites and All Applications rows with fast 60fps horizontal scrolling and cached icons.
  - Remote Menu key (`KEYCODE_MENU`) / long-click action dialog for Open, Favorite toggle, Uninstall, and App Info.
  - "Set as Default Launcher" prompt using Android Q `RoleManager.ROLE_HOME` and `ACTION_HOME_SETTINGS` fallback.
- Added quick "Home screen" jump button to `MainActivity` navigation rail.

## [Phase 1 - Installed Apps List & Uninstaller] - 2026-11-10

### Added
- Added `REQUEST_DELETE_PACKAGES` permission in `AndroidManifest.xml`.
- Created `InstalledAppModels.kt` containing `InstalledAppInfo`, `AppTypeFilter`, and `AppSortOrder`.
- Created `IconCache.kt` providing high-performance in-memory LRU caching of app icons with async background decoding.
- Created `InstalledAppsScanner.kt` to inspect installed packages, calculate APK size (including splits), detect system/user status, and check launch intent availability.
- Created `InstalledAppsScreen.kt` with TV remote navigation, search bar, All/User/System filters, sorting by name/size/install date, and quick action dialogs.
- Implemented remote-safe uninstallation flow via `Intent.ACTION_DELETE` with confirmation dialog focusing Cancel by default.
- Integrated Installed Apps into the navigation rail and screen router with auto-refresh on resume.

## [Phase 0 - Baseline Analysis] - 2026-11-10

### Added
- Created `ARCHITECTURE.md` with complete module architecture and planned component breakdown.
- Created `PROGRESS.md` with phase tracking, decision logs, and resumption instructions.
- Validated baseline build with Kotlin 2.0.21, AGP 8.7.3, compileSdk 35, and targetSdk 34.
