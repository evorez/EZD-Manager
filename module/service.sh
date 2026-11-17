#!/system/bin/sh
# Startup retry only; tuning is added in Phase 2 after detection and verification.
MODDIR=${0%/*}
[ -f "$MODDIR/.apk_install_pending" ] || exit 0
n=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$n" -lt 90 ]; do
  sleep 2
  n=$((n + 1))
done
TMPAPK=/data/local/tmp/ezd_companion.apk
if cp "$MODDIR/companion.apk" "$TMPAPK" && pm install -r "$TMPAPK" >/dev/null 2>&1; then
  rm -f "$MODDIR/.apk_install_pending"
fi
rm -f "$TMPAPK"
