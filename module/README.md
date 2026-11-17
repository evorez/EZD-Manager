# Module packaging foundation

The workflow copies the CI-built companion APK into this module directory and zips it. `customize.sh` attempts `pm install -r`; if Package Manager is not ready, `service.sh` retries once Android reports boot complete. This phase does not apply CPU/GPU/display settings or boot-time profiles. Do not install this scaffold as a tuning module until a device-tested release is explicitly provided.
