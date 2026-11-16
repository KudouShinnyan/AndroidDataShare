#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
. "$MODDIR/engine.sh"

[ -s "$MOD_RULES" ] || exit 0

while :; do
    [ "$(getprop sys.user.0.ce_available)" = "1" ] && break
    ads_probe="$ADS_MEDIA/0/Android/data/.ads_probe.$$"
    if ( : > "$ads_probe" ) 2>/dev/null; then
        rm -f "$ads_probe" 2>/dev/null
        break
    fi
    sleep 10
done

ADS_BOOT_RESTORE=1
ADS_RULES_FROM="$MOD_RULES"
engine_apply_all >/dev/null 2>&1
exit 0
