#!/system/bin/sh
# Installs the bundled EZD Manager APK. Copy to /data/local/tmp first so the package installer can read it.
ui_print "- Installing EZD Manager module"
if [ ! -f "$MODPATH/companion.apk" ]; then
  abort "! companion.apk missing; rebuild the module zip using the CI workflow."
fi
TMPAPK=/data/local/tmp/ezd_companion.apk
if cp "$MODPATH/companion.apk" "$TMPAPK" && pm install -r "$TMPAPK" >/dev/null 2>&1; then
  ui_print "- EZD Manager APK installed"
else
  ui_print "! APK install deferred; will retry after boot"
  touch "$MODPATH/.apk_install_pending"
fi
rm -f "$TMPAPK"
ui_print "- No device tuning is applied by this foundation build"
