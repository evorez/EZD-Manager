# Phase 2a testing and safety

Root is requested only on the Home screen. The app scans CPU policies and thermal zones as root and refreshes readings every three seconds while visible. Battery readings come from Android. CPU frequency is shown in MHz; the report uses kernel kHz. No thermal nodes are written.

The first readable `scaling_max_freq` for each discovered policy is saved in private app storage before enabling writes. Reset writes that saved value and verifies it. It is the value first observed by this app, not necessarily the kernel's factory default. Clearing app data or uninstalling removes these baselines; policies appearing later get a baseline on first discovery. A write is offered only when a max node appears writable and a baseline was saved; the selection additionally requires a kernel-provided frequency list. A kernel or SELinux policy can still reject a write. Both writes and resets report their read-back, and settings are not reapplied on boot. Another service can change a value after verification.

## Phone test

1. Install the Actions artifact APK (back up any data before uninstalling an incompatible prior build). Open Home and tap Grant root; approve the request in your root manager. Verify Home says Granted. Deny it separately to check that the app reports denial and shows no CPU controls.
2. On Home check battery, live per-policy CPU and thermal readings. Open Report and copy/share it; check policy IDs, kHz values, governors and thermal zones are present where supported.
3. On CPU note each current max and saved reset value. For a policy exposing Set max, choose a supported value not below its current minimum. Check for a verified message and compare the reported max. If rejected or clamped, check the error/read-back instead. The kernel may immediately change the value again.
4. Tap that policy's Reset and verify the saved value is read back. Repeat for any other changed policies. If reset fails, do not assume the value was restored; inspect the report and reboot if needed.
5. Reboot and confirm the app does not reapply settings. Reopen, grant root and check the saved reset values. This phase does not implement boot persistence or a bootloop guard; do not install the module as a tuning solution.

## Additional safeguards

Baselines are scoped to the Android build fingerprint, kernel version, policy ID and CPU membership so another ROM/kernel/topology does not inherit stale reset values. Old unscoped baselines are ignored. Reset still means first observed, not factory default. Values below the observed minimum are disabled in the menu; every write (including reset) rechecks the minimum and refuses an unreadable/invalid minimum. No minimum or thermal node is modified. Test rotation/backgrounding during a root request, and retry after denial.
