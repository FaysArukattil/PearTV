# CleanYourTV — Reference Notes & Comparative Architectural Analysis

## 1. Executive Summary

This document evaluates the reference repository [`cleanyourtv`](https://github.com/Gathin23/cleanyourtv) (MIT License, Copyright 2026 Gathin) and details how this project (**CleanYourTV**) advances the concept into a production-grade, 100% autonomous, on-device Android TV platform.

---

## 2. Reference Repository Architecture & Limitations

### 2.1 The External PC / Script Dependency
The reference project positions itself as a TV debloater and launcher, but its operational reality relies heavily on external host machines:
- **Debloating**: Accomplished via `tools/disable.sh` and `tools/disable.ps1` running on a separate Mac/Linux/Windows machine over network ADB.
- **Theme Switching**: Handled via `tools/theme.sh` which replaces XML theme assets on the TV from an external shell.
- **Diagnostics & Benchmarking**: Handled by `tools/bench.py`, `tools/privacy.py`, and `tools/tv.py`.
- **End-User Impact**: The average TV user cannot use these tools without technical knowledge, a laptop on the same network, USB/Wi-Fi ADB debugging enabled, and terminal proficiency.

### 2.2 Launcher Implementation Details
- **UI Toolkit**: Legacy Android Views with `RecyclerView`, `AppAdapter.java`, and custom XML views (`GlassView.java`, `HeroArt.java`).
- **Tile Geometry**: `180dp x 101dp` (16:9 aspect ratio) with `18dp` corner radius, `1.10f` focus scale, 2.5dp frosted glass stroke, and a top 45% specular shine gradient.
- **Hero Artwork**: `HeroArt.java` downsamples app icons to 48x27, boosts saturation by 1.7x, and applies a 3-pass box blur to cast an ambient colored halo behind the title text.
- **App Icons**: Banners used directly when present; mobile-only icons rendered on dark colored plates.

### 2.3 What Was Missing in the Reference
1. **No On-Device Debloating Wizard**: No way for the user on their sofa with a remote to pair with wireless debugging or execute debloats.
2. **No Storage Manager**: No system storage breakdown, no per-app data/cache measurements, and no on-screen cache cleanup.
3. **No File Manager**: No native way to browse USB sticks or internal storage, install downloaded APKs, or play local media.
4. **No Dynamic Input Switcher**: Did not actively query `TvInputManager` hardware states (connected vs disconnected HDMI ports).
5. **No Google Account Profile Integration**: No live user profile capsule or account switcher.
6. **No Native/Local Phone-to-TV Sharing**: Relied exclusively on external cast apps.

---

## 3. CleanYourTV Architectural Enhancements

| Dimension | Reference Repo (`cleanyourtv`) | Our CleanYourTV Implementation |
|---|---|---|
| **Execution Model** | Requires PC / Terminal / ADB Scripts | **100% On-Device Remote-First** (One APK install, remote control only) |
| **UI Framework** | Legacy View System (`RecyclerView`, XML) | **Jetpack Compose for TV** (Material 3 TV, Foundation, Smooth D-Pad) |
| **Debloat Engine** | External bash/python scripts | **Embedded Local ADB (`dadb`) + Guided On-Screen Pairing Wizard** |
| **Bloat Profiles** | Static `.txt` files in `examples/` | **Rich In-App Catalog** with Safe/Caution/Danger risk levels & reversibility |
| **Storage Management** | None | **Samsung Device Care Style Storage Screen** (Segmented gauge, per-app cache/data, 1-click optimize) |
| **File Management** | None | **Samsung My Files Style TV File Manager** (Categories, USB drive detection, APK installer) |
| **Input Switching** | Static or missing | **Live `TvInputManager` Detection** (Real HDMI states: colorful active, grayed disconnected) |
| **Account / Profile** | Missing | **Live Google Account Detection & Switcher** via `AccountManager` |
| **Phone-to-TV Sharing** | None | **Dual-Mode**: Google Quick Share intent + **Zero-Install Local Wi-Fi Web Server** (QR Code + PIN) |
| **Wallpaper & Theming** | Pushed from PC via `theme.sh` | **In-App Personalization Menu** (Pitch Black, Aurora, Sunset, Deep Space, Custom Image) |
| **App Icon Adaptation** | Basic script/XML plate | **Dynamic TV Banner Extraction + 16:9 Squircle Gradient Plate Generator** |

---

## 4. Bloatware Intelligence Adaptation

We adapted vendor-specific package signatures from the reference repository's community device dumps (Xiaomi/Redmi PatchWall, Sony Bravia, TCL, Amazon Fire TV, Hisense, Google TV, Philips, and OEM Projectors). 

We enhanced this data by adding:
1. **Safety Categories**: Safe to disable, Caution (may disable secondary feature like voice search or cast), and Dangerous (system UI, package installer, TV input drivers).
2. **Reversibility Guarantee**: Every modification is recorded in a persistent journal (`Journal.kt`) allowing instant 1-click restore via `pm enable-user` or `cmd package install-existing`.
3. **Never Brick System**: Strict regex blacklist (`CORE_RE`) ensuring core system packages, input hardware drivers, and package installers are protected under all circumstances.

---

## 5. Attributions & Compliance

- Reference repository code, asset structures, and device lists studied from `https://github.com/Gathin23/cleanyourtv` under the MIT License.
- Design geometry (180x101dp, 18dp radius, 1.10x focus scale) adopted to maintain the high-end Apple tvOS aesthetic demonstrated in the reference project.
- All code in CleanYourTV is clean-room implemented in Kotlin with Jetpack Compose.
