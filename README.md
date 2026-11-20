# EZD Manager

Rewritten as a **React + Vite + TypeScript + Tailwind CSS v4** web application preserving all core hardware tuning, privilege mode, theming, per-app tuning, and system management workflows from the original Android project (`evorez/EZD-Manager`).

## Features

- **Home Dashboard & Beast Mode**: Live telemetry simulation (CPU load, GPU clock, RAM, Battery/SoC thermal sensors), expandable Thermal Watchdog limits, Beast Execution Report, Performance Profiles, and Quick Actions.
- **Work Mode Authority Engine**: Interactive privilege simulation (`Auto`, `Root`, `Shizuku`, `Basic`) enforcing tool badges and strict root locks across the UI.
- **Tools & Unified CPU/GPU Engine**: 17 interactive tool panels (CPU/GPU clocks & 8-core hotplug, ZRAM & Linux VM, I/O schedulers & thermal profiles, Display refresh/HBM/CABC, Touch & HW Overlays, Network TCP/DNS, Audio 5-band DSP + 4 animated `<canvas>` visualizers, Battery Saving & Fast Charge, Device Spoof, Profiles, Root Modules + WebUI, Build.prop Editor with auto-backups, Boot Guard, Encrypted EZD Backup with SHA-256 + file import/export, Granular App Backup, and Root Terminal with command history).
- **Per-App Engine & DNS Routing**: Search, category filter, sort, system app toggle, app lifecycle actions, and per-app profile/Hz/spoof/DNS configuration.
- **14 Frosted Glass & Special Themes**: Full `WHITE`, `BLACK`, and `SPECIAL` theme families plus `Lite Mode`, ambient textures (`Plain`, `Paper`, `Moon`, `Silver`), 3 font styles, draggable/pinnable floating pill shortcuts, and GitHub release OTA checking.

## Development

```bash
npm install
npm run dev
```

Build and type-check:

```bash
npm run build
npm run lint
```
