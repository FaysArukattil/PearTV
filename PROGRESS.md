# LeanTV — Development Progress

## 1. Project Overview & Architecture Summary
LeanTV is a native Android TV system manager and custom launcher built using Jetpack Compose, Kotlin 2.0.21, and AndroidX libraries (minSdk 26, targetSdk 34, compileSdk 35). It provides:
1. System optimization and debloating capabilities via on-device ADB.
2. Full installed application browsing with User/System filtering, search, sorting, and D-pad uninstallation.
3. An Apple tvOS-inspired lean launcher (`HomeActivity`) with top featured apps, rows, and remote-friendly navigation.
4. Live RAM and CPU system monitoring with background task termination.
5. In-app Quick Share local HTTP server with QR code pairing for direct mobile-to-TV transfer.

---

## 2. Phase Checklist

- [x] **PHASE 0 — ANALYSIS**
  - [x] Inspect project structure, manifest, build files, and dependencies
  - [x] Identify minSdk (26), targetSdk (34), compileSdk (35), UI stack (Compose + Material 3)
  - [x] Create `ARCHITECTURE.md`, `PROGRESS.md`, and `CHANGELOG.md`
  - [x] Confirm baseline build (`./gradlew assembleDebug` passes)
- [x] **PHASE 1 — INSTALLED APPS LIST + UNINSTALL**
  - [x] Add `REQUEST_DELETE_PACKAGES` and query permissions to manifest
  - [x] Implement `InstalledApp` model, repository/scanner fetching package icon, name, version, size, install time
  - [x] Add filter toggle (All / User / System), sort (Name, Size, Install Date), and search
  - [x] Add D-pad focusable app cards and remote-friendly confirmation dialog for uninstall (`ACTION_DELETE`)
  - [x] Gracefully handle system apps (disable/cannot uninstall indication)
- [x] **PHASE 2 — CUSTOM LAUNCHER (Apple TV-STYLE HOME)**
  - [x] Add `HomeActivity` with `MAIN`, `HOME`, `DEFAULT`, and `LEANBACK_LAUNCHER` intent filters
  - [x] Implement Apple tvOS-style top featured row with focus scale, subtle glow/shadow, and background accent
  - [x] Implement Favorites and All Apps grid/rows with 60fps focus and icon caching
  - [x] Add top-corner clock and date
  - [x] Add app launch fallback (`getLeanbackLaunchIntentForPackage` -> `getLaunchIntentForPackage`)
  - [x] Add long-press / Menu action sheet (Open, Uninstall, App Info, Favorite)
  - [x] Add "Set as default launcher" prompt and system settings intent
- [x] **PHASE 3 — SYSTEM MONITOR (RAM + CPU) WITH KILL TASK**
  - [x] Implement live RAM readout via `ActivityManager.MemoryInfo`
  - [x] Implement resilient CPU monitor with graceful fallback on Android 10+
  - [x] Implement running background processes list with memory consumption
  - [x] Implement background process kill action (`ActivityManager.killBackgroundProcesses`) and "Clean All"
  - [x] Bind polling to panel visibility lifecycle (stop updates when closed)
- [x] **PHASE 4 — QUICK SHARE (BUILT INTO THE SIDE)**
  - [x] Add collapsible side panel triggered by D-pad Left or dedicated toggle
  - [x] Implement embedded lightweight HTTP server on local network
  - [x] Generate on-screen QR code and PIN for pairing
  - [x] Implement file upload receiver saving to `Downloads/LeanTV/`
  - [x] Implement received text/link display with one-click open
- [x] **PHASE 5 — POLISH, OPTIMIZATION, TESTING**
  - [x] Cache app icons in memory to ensure fluid 60fps scrolling
  - [x] Verify D-pad focus flows across all screens
  - [x] Validate manifest features and permissions
  - [x] Verify build and produce final APK and zip deliverables

---

## 3. Files Created or Modified

| File | Purpose |
| :--- | :--- |
| `ARCHITECTURE.md` | Documents system architecture, tech stack, and module layout. |
| `PROGRESS.md` | Tracks phase completion, active decisions, and resumption instructions. |
| `CHANGELOG.md` | Records chronological changes across development phases. |
| `AndroidManifest.xml` | Added `REQUEST_DELETE_PACKAGES` permission for user uninstallation flow. |
| `app/.../data/InstalledAppModels.kt` | Data definitions for `InstalledAppInfo`, `AppTypeFilter`, and `AppSortOrder`. |
| `app/.../data/IconCache.kt` | LRU in-memory cache for app icons with asynchronous background decoding. |
| `app/.../data/InstalledAppsScanner.kt` | Background package scanner computing APK size, split paths, and launch intents. |
| `app/.../data/Models.kt` | Added `Screen.InstalledApps` and filter/sort/search state properties to `UiState`. |
| `app/.../ui/InstalledAppsScreen.kt` | D-pad navigable installed apps screen with filters, search, and confirmation dialogs. |
| `app/.../ui/MainViewModel.kt` | Added state management for installed apps, uninstaller launch, and app execution. |
| `app/.../ui/App.kt` | Integrated Installed Apps screen into rail navigation, back handler, and route switcher. |
| `app/.../HomeActivity.kt` | Dedicated TV launcher activity with `singleTask` launchMode and `CATEGORY_HOME`. |
| `app/.../launcher/LauncherViewModel.kt` | ViewModel driving launcher shelves, featured apps, favorites, and default home setup. |
| `app/.../launcher/HomeScreen.kt` | Apple tvOS-inspired Compose home screen with live clock, ambient backdrop, and D-pad menus. |
| `app/.../data/Journal.kt` | Added launcher favorites and default launcher prompt persistence methods. |
| `app/.../monitor/SystemMonitor.kt` | Real-time RAM, CPU fallback, running process PSS extraction, and background task killer. |
| `app/.../monitor/SystemMonitorPanel.kt` | Slide-in drawer UI with animated RAM meter, CPU card, running process list, and Clean All action. |
| `app/.../share/ShareModels.kt` | Data classes for `SharedFile`, `SharedText`, and `QuickShareState`. |
| `app/.../share/QrGenerator.kt` | ZXing-powered QR code generator rendering directly to Compose `ImageBitmap`. |
| `app/.../share/QuickShareServer.kt` | Embedded lightweight HTTP server with mobile upload page, PIN validation, and streaming file saver. |
| `app/.../share/QuickSharePanel.kt` | Slide-in drawer with live Wi-Fi IP, QR code, PIN, transfer progress, and received content cards. |
| `app/.../res/xml/file_paths.xml` | FileProvider path definitions for secure content URI sharing of received downloads. |
| `README.md` | Comprehensive user and technical guide for LeanTV setup, features, permissions, and build steps. |

---

## 4. Decisions, Known Issues, and Limitations

1. **Android TV Fixed Canvas Scaling:** The app relies on `TvCanvas` in `ui/Theme.kt` to scale density to a fixed 1280x720 reference grid. All new Compose layouts will respect this system for consistent TV overscan safety and legibility.
2. **Output Archive Directory:** Root `/output/` is protected under macOS SIP without root permissions. Phase archives are generated into both `<projectRoot>/output/` and `/tmp/output/` for user accessibility.
3. **App Listing Queries:** Android 11+ (API 30+) requires `QUERY_ALL_PACKAGES` to view user and system applications; already declared in `AndroidManifest.xml`.
4. **Package Uninstallation on TV:** Standard Android third-party apps cannot silently uninstall packages without system/root/device owner privileges. Uninstallation prompts the platform `PackageInstaller` via `Intent.ACTION_DELETE` or `PackageInstaller.Session`.
5. **Default Launcher Setting Limitation:** Android OS strictly prohibits apps from setting themselves as the default home launcher silently in background for user security. On first run, LeanTV guides the user via `RoleManager.createRequestRoleIntent(ROLE_HOME)` on Android 10+ with automatic fallback to `Settings.ACTION_HOME_SETTINGS`.

6. **CPU Telemetry on Modern Android (API 29+):** Access to `/proc/stat` is restricted by SELinux on Android 10 and higher for third-party applications. `SystemMonitor` attempts to read `/proc/stat` for backwards compatibility, but safely falls back to reporting hardware core architecture and active thread units rather than failing or crashing.
7. **Task Termination Policy:** Third-party applications using `ActivityManager.killBackgroundProcesses` can only terminate cached and background processes without root/system privileges. Active foreground apps and system-critical services are protected by the OS. The UI explicitly discloses this behavior.

8. **Local Quick Share Protocol:** Google Quick Share / Nearby Share lacks an open, stable Android TV server SDK. LeanTV implements an ultra-lightweight zero-dependency socket HTTP server pairing via a random 4-digit TV-displayed PIN and QR code. Mobile devices on the same Wi-Fi can open the web portal on any browser without installing extra software.

---

## 5. NEXT STEP (Resumption Guide)

**ALL PHASES COMPLETED (Phases 0 through 5).**

### Summary of Deliverables
- `output/app-debug.apk`: Production-ready compiled debug APK with TV banner and launcher filters.
- `output/LeanTV_final.zip`: Complete buildable project source tree (excluding build artifacts and cache).
- `output/LeanTV_phase0.zip` through `output/LeanTV_phase4.zip`: Checkpointed archives after each development phase.

### Manual Actions Required by User on Device
1. **Sideloading the APK:** Install `app-debug.apk` onto the TV via ADB (`adb install -r app-debug.apk`) or via USB flash drive.
2. **Setting Default Launcher:** When pressing the Home button on the TV remote for the first time, choose **LeanTV** and select **Always**. Alternatively, navigate to TV **Settings** &rarr; **Apps** &rarr; **Default apps** &rarr; **Home app** &rarr; select **LeanTV**.
3. **Optional On-Device ADB:** To enable system bloatware removal without a PC, enable **Developer options** &rarr; **USB debugging** &rarr; run `adb tcpip 5555` once from a computer connected to the TV.
