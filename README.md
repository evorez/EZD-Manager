# EZD Manager

Original Android root performance/power manager. Phase 1 creates the Compose app shell and phone-friendly GitHub Actions build for a debug APK plus module ZIP containing that APK.

## Build without a computer

1. Create a GitHub repository and upload this project (including `.github/workflows/build.yml`).
2. Push to `main`, or open **Actions → Build APK and module → Run workflow**.
3. Open the completed workflow run and download the `EZD-Manager-<commit>` artifact. It contains `EZD-Manager-debug.apk` and `EZD-Manager-module.zip`.

The APK is currently an unsigned debug build intended for testing. Do not distribute it as a production release.

## Project map

- `app/` — Kotlin + Jetpack Compose Android application (min API 29, target API 35).
- `module/` — Magisk/KernelSU-compatible packaging foundation and APK installer/retry scripts.
- `.github/workflows/build.yml` — free GitHub-hosted build and artifact packaging.
- `docs/` — design and test plans as phases proceed.

## Phase 1 status

Only the app shell, logo placeholder, and packaging pipeline are in scope. No root commands or tuning controls are active. Phase 2 will add capability detection, read-back verified controls, safety/undo, and boot persistence. The uploaded Evorez PNG is included as temporary brand art.

## Safety

This foundation is not a device-tested tuning release. CPU/GPU/display/charging sysfs nodes and policies vary across ROMs and kernels. The module should not be installed as a performance controller until later phases implement guarded detection and tests. Bootloop guard and boot persistence are planned for Phase 2.
