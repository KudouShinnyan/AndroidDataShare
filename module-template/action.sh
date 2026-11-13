#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
. "$MODDIR/engine.sh"

[ -s "$MOD_RULES" ] || { printf 'Nothing to restore\n'; exit 0; }
ADS_BOOT_RESTORE=1
ADS_RULES_FROM="$MOD_RULES"
engine_apply_all >/dev/null 2>&1
printf 'Mount groups restored\n'
