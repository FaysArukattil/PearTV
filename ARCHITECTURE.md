# LeanTV — Architecture Overview

LeanTV is an Android TV application designed for lightweight system optimization, custom Apple tvOS-style launcher functionality, comprehensive installed package management, live system monitoring, and network quick-sharing.

---

## 1. Technical Stack & Environment Baseline

- **Language:** Kotlin 2.0.21
- **Minimum SDK (minSdk):** 26 (Android 8.0 Oreo)
- **Target SDK (targetSdk):** 34 (Android 14)
- **Compile SDK (compileSdk):** 35 (Android 15)
- **UI Toolkit:** Jetpack Compose (BOM `2024.10.01`, Compose Compiler `2.0.21`, Material 3, Foundation, Animation)
- **Architecture Pattern:** MVVM (Model-View-ViewModel) with Kotlin Coroutines & `StateFlow`
- **Display Target:** 10-foot TV UI, scaled via `TvCanvas` to a virtual 1280x720 density-normalized viewport to eliminate overscan and maintain 60fps D-pad remote responsiveness.

---

## 2. Module & Package Structure

The application is structured under the single application module `:app` with root package `com.leantv.app`:

```
com.leantv.app
├── MainActivity.kt               # Entrypoint activity hosting the Compose UI
│
├── adb/                          # Low-level on-device ADB protocol client
│   └── AdbClient.kt             # TCP connection (localhost:5555), handshake, RSA key management, shell command execution via dadb
│
├── data/                         # Data layer, models, scanning & system probing
│   ├── Catalog.kt               # Known bloatware definitions & recommendation database (loaded from assets/catalog.json)
│   ├── DeviceProbe.kt           # System hardware, ABI, RAM, storage, and TV brand heuristics
│   ├── Journal.kt               # Persistent SharedPreferences log of state changes & removed apps
│   ├── Models.kt                # UI and domain data structures (AppItem, UiState, DeviceProfile, Screen, etc.)
│   ├── Scanner.kt               # Package manager inspection, protected prefix safety logic, system app detection
│   └── TweakEngine.kt           # Animator duration and private DNS optimization actions
│
└── ui/                           # Jetpack Compose UI layer
    ├── App.kt                   # Root composition, navigation rail, dialog scrims, busy spinners, toasts
    ├── Components.kt            # D-pad focusable wrappers, buttons, switches, meters, and remote focus requesters
    ├── DebloatScreen.kt         # Bloatware removal list with tier categorization and batch actions
    ├── MainViewModel.kt         # Presentation logic, ADB connection management, coroutine orchestration
    ├── ManageScreens.kt         # Speed tweaks, restore screens, settings intent navigation
    ├── Screens.kt               # Welcome, setup wizard, device overview dashboard
    └── Theme.kt                 # Colors (Pal), TV safe padding (Safe), typography (Ty), TvCanvas scaling, backdrop gradient
```

---

## 3. Planned Expansions (Phases 1–5)

- **Phase 1 (Installed Apps & Uninstaller):**
  - Package: `com.leantv.app.apps`
  - Component: `InstalledAppsScreen.kt` + `AppManager.kt`
  - Provides All/User/System filters, search, sort by size/date/name, and remote-friendly uninstallation (`ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE`).

- **Phase 2 (Apple tvOS-Style Custom Launcher):**
  - Component: `HomeActivity.kt` (`MAIN`, `HOME`, `DEFAULT`, `LEANBACK_LAUNCHER`)
  - Package: `com.leantv.app.launcher`
  - High-performance, 60fps D-pad row navigation, top featured apps carousel, custom app categories, fast launch resolution (`getLeanbackLaunchIntentForPackage`).

- **Phase 3 (Live System Monitor & Task Killer):**
  - Package: `com.leantv.app.monitor`
  - Components: `SystemMonitor.kt`, `ProcessStats.kt`
  - Sliding drawer or panel showing real-time RAM (via `ActivityManager.MemoryInfo`), resilient CPU estimation, background process killer (`killBackgroundProcesses`).

- **Phase 4 (Quick Share Side Panel & Local Server):**
  - Package: `com.leantv.app.share`
  - Components: Embedded lightweight HTTP server (Ktor or NanoHTTPD) binding on local Wi-Fi, QR code generation, PIN security, file receiver saving to `Downloads/LeanTV/`.

- **Phase 5 (Performance Polish & Testing):**
  - Icon caching (LruCache / Coil/custom memory cache), leak elimination, full D-pad focus validation, comprehensive documentation.

