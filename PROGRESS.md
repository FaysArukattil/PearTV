# Pear TV — Development Progress & Optimization Roadmap

## 1. Project Overview & Architecture Summary
**Pear TV** (`com.peartv.app`) is an ultra-lightweight, 60fps native Android TV launcher, system cleaner, and local network sharing suite built using Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), and AndroidX libraries (minSdk 24, targetSdk 34, compileSdk 35).

It combines three foundational pillars engineered specifically for low-resource TV hardware (1GB–2GB RAM, low-end quad-core ARM Cortex-A53/A35 chipsets):
1. **Apple tvOS Launcher & Native Video Screensavers**:
   - High-performance 1280x720 density-normalized UI (`TvCanvas`) with Apple HIG motion physics (`AppleMotion`).
   - Adapted from the Apple Aerial screensaver ecosystem: live 4K/1080p aerial presets with intelligent local storage caching in `/sdcard/Movies/` to prevent network re-downloads and avoid bloating internal app storage.
2. **Clean TV (Integrated 1-Click System Debloater)**:
   - Adapted from the **CleanYourTV** open-source Android TV debloater architecture.
   - Comprehensive multi-vendor catalog (`catalog.json`) identifying manufacturer bloat, ad tracking services, telemetry, and background daemons (Xiaomi PatchWall, Google TV LauncherX, Amazon FireTV, Sony, TCL, Hisense).
   - On-device local ADB socket communication (`localhost:5555` via `Dadb`) and Shizuku integration for safe disabling without requiring a PC.
3. **Native Google/Samsung Quick Share via Reverse Engineering (REA)**:
   - Clean-room implementation based on reverse-engineering the Quick Share / Nearby Connections protocol (modules `:core-protocol` and `:discovery-android`).
   - Implements Google's offline wire formats (Protobuf Lite), BLE advertisement & fast GATT bootstrap, Wi-Fi Direct / Local Wi-Fi socket multiplexing, and UKEY2 secure channel cryptography.
   - Fallback zero-dependency web upload server with QR-code pairing for any browser on the local network.

---

## 2. Completed Phases (0 through 5)

- [x] **PHASE 0 — BASELINE & FOUNDATION**: Project structure, Android TV leanback manifests, permissions, base Compose UI.
- [x] **PHASE 1 — INSTALLED APPS MANAGER**: Package scanner, user/system filters, sorting, D-pad uninstallation.
- [x] **PHASE 2 — CUSTOM LAUNCHER (Apple TV-STYLE HOME)**: Top featured dock, ambient wallpaper glow, responsive grid, live clock, D-pad menus.
- [x] **PHASE 3 — LIVE SYSTEM MONITOR**: Real-time RAM (ActivityManager) and CPU telemetry, background task killer.
- [x] **PHASE 4 — QUICK SHARE & LOCAL TRANSFER**: Dual-engine Quick Share (native BLE/mDNS receiver + lightweight browser upload server).
- [x] **PHASE 5 — BRANDING & TV POLISH**: Apple HIG visual redesign (Apple Files, TV Cleaner, Storage, Screensavers, official Quick Share circular arrows glyph).

---

## 3. PHASE 6 — ULTRA LOW-SPEC TV OPTIMIZATION & SIZE REDUCTION (Active)

### Goal Checklist & Current Status:
- [x] **Comprehensive Profiling**:
  - Identified 22MB debug APK vs 3.8MB unoptimized release APK.
  - Identified 1.68 MB uncompressed aerial thumbnails in `assets/aerial_thumbs/`.
  - Identified ~450 KB of uncompressed brand PNGs and unused assets (`ic_airdrop_official.png`, `ic_quick_share_brand.png`, `ic_quick_share_arrows.png`).
  - Identified continuous `rememberInfiniteTransition` GPU redraw loops in `CleanTvWizardDialog.kt` (`LogoHero` and `ApplePermissionHero`).
  - Identified 600ms main-thread Binder polling in onboarding permission checks.
  - Identified dynamic colored RenderNode shadows (`Modifier.shadow(20.dp)`) causing GPU fillrate choking on Mali-450/G31 chips.
- [x] **Asset Compression & Pruning**:
  - Deleted unused PNG drawables: `ic_airdrop_official.png`, `ic_quick_share_arrows.png`, `ic_quick_share_brand.png`.
  - Converted active brand PNGs (`ic_apple_files_brand`, `ic_apple_storage_brand`, `ic_apple_tv_cleaner_brand`, `ic_quick_share_official`, `ic_screensavers_brand`) to WebP (384px lossless).
  - Converted all 27 aerial thumbnails in `assets/aerial_thumbs/` from JPG (1.68 MB) to WebP (~249 KB) — **saved 1.43 MB directly**.
  - Updated `ScreensaverSettingsDialog.kt` to load `.webp` thumbnails.
- [x] **ProGuard & R8 Optimization Rules**:
  - Added `-allowaccessmodification` and `-repackageclasses ''` in `proguard-rules.pro`.
  - Added `-assumenosideeffects class android.util.Log { v(...); d(...); i(...); }` to strip debug logging overhead.
  - Added Protobuf Lite and Shizuku keep rules.
- [x] **Gradle Build Properties**:
  - Enabled `android.enableResourceOptimizations=true` and disabled unused build features (BuildConfig, AIDL, Renderscript, Shaders) in `gradle.properties`.
- [x] **Pear TV Rebranding (No "LeanTV" text)**:
  - Replaced all user-facing strings across `FileManagerScreen.kt`, `QuickShareServer.kt`, `QuickSharePanel.kt`, `AppClassifier.kt`, `Models.kt`, `Scanner.kt`, `Screens.kt`, `CleanTvScreen.kt`, `catalog.json`, `FileManagerHelper.kt`, `TvFileDestinationFactory.kt`, and `AppleTvScreensaver.kt`.
  - Public directory names migrated seamlessly to `PearTvQuickshare` and `PearTvScreensaver` with backward-compatible legacy directory detection.
- [x] **Asset Cutout & Edge Polish**:
  - Replaced flat-clipped rounded rectangles in `ic_apple_files_brand.webp` and `ic_screensavers_brand.webp` with true continuous Apple superellipse squircles ($p = 4.8$) with 20px inset padding.
  - Supersampled at 4x resolution (1536x1536) and downscaled using Lanczos filtering to eliminate all hard boundary cuts, jagged corners, and cutting marks.
  - Verified edge rows/columns have zero alpha leakage on canvas boundaries.
- [x] **Onboarding & Runtime Smoothness Overhaul**:
  - Removed continuous `rememberInfiniteTransition` pulse loops from `CleanTvWizardDialog.kt`.
  - Replaced dynamic RenderNode elevation shadows with high-performance draw-phase border & specular highlight styling.
  - Replaced 600ms main-thread polling in `PermissionPage` with `LifecycleResumeEffect` / on-demand checks.
  - Optimized `Components.kt` (`Focusable` & `TvButton`) to eliminate GPU fillrate bottlenecks on low-end Mali/PowerVR TV chipsets.
  - Updated `Theme.kt` with `drawWithCache` to avoid re-allocating radial gradient brushes per frame.
- [x] **Build Optimization & Size Reduction**:
  - Configured `app/build.gradle.kts` for split ARM APKs (`armeabi-v7a`, `arm64-v8a`) and universal release APK.
  - Stripped non-English translations with `resourceConfigurations += listOf("en")`.
  - Stripped Kotlin parameter, call, and receiver assertions (`-Xno-param-assertions`, `-Xno-call-assertions`, `-Xno-receiver-assertions`).
  - Added ProfileInstaller (`androidx.profileinstaller:1.4.1`) and baseline profile (`baseline-prof.txt`) for immediate AOT compilation on TV installation.
- [x] **Verification & Metrics**:
  - Debug APK: **22.3 MB** ➔ Release APK: **2.3 MB** (**90% size reduction!**).
  - Single ARM64 APK: **2.3 MB**; Universal APK: **2.3 MB**.
  - All features preserved: Quick Share (Protobuf Lite), Clean TV debloater with ADB loopback, Apple Aerial screensavers, and File Manager.

---

## 4. Key Component Origins & Technical Implementation

### A. Quick Share Protocol (Reverse Engineered - REA)
The native Quick Share engine in `:core-protocol` and `:discovery-android` was developed by reverse-engineering Google's Nearby Connections / Quick Share specification:
- **Wire Formats**: Protobuf-based frames (`offline_wire_formats.proto`, `wire_format.proto`) handling connection requests, bandwidth upgrade negotiation, and chunked payload transfers.
- **Discovery Layer**: Low-energy Bluetooth (BLE) service UUID `0000fef3-0000-1000-8000-00805f9b34fb` broadcasting fast advertisements.
- **Connection Handshake**: Initial exchange via BLE GATT server or Wi-Fi Aware, followed by UKEY2 (Unix-like Key Exchange 2) Diffie-Hellman curve25519 key derivation for encrypted communication.
- **High-Speed Transfer**: Bandwidth upgrade to local Wi-Fi TCP socket server (port multiplexing) or Wi-Fi Direct P2P Group Owner.

### B. Clean TV (Debloater Engine)
Based on the architectural principles of **CleanYourTV**:
- Categorizes packages into Safe (Protected), Recommended Debloat (OEM telemetry, tracking, ad servers), Optional (vendor storefronts, keyboards), and Advanced (OEM stock launchers).
- Probes TV hardware via `DeviceProbe.kt` (MediaTek, Amlogic, Realtek, Broadcom) to apply chipset-specific safe heuristics.
- Executes non-destructive `pm disable-user --user 0` and uninstallation via on-device loopback ADB (`127.0.0.1:5555`) using the embedded Kotlin `Dadb` client.

### C. Apple TV Aerial Screensaver
Adapted from the Apple Aerial open-source catalog:
- Streams official Apple CDN high-bitrate H.264/HEVC aerial videos (ISS Space, Earth, Oceans, Cities).
- Uses `AerialVideoCache` to store completed streams in external public storage (`/sdcard/Movies/`), keeping app package memory footprint negligible in TV system settings.
