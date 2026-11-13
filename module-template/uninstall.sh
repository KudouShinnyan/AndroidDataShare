#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
. "$MODDIR/engine.sh"
if [ -f "$MOD_RULES" ]; then
    while IFS= read -r ads_line; do
        case "$ads_line" in ''|'#'*) continue ;; esac
        ads_split "$ads_line"
        [ "$R4" = "mount" ] && ads_umount_one
    done < "$MOD_RULES"
fi
