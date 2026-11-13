#!/system/bin/sh
MODDIR=${0%/*}
[ -n "$MODDIR" ] && [ -d "$MODDIR" ] || MODDIR=/data/adb/modules/AndroidDataShare
MOD_CONF="$MODDIR/config"
MOD_STATE="$MOD_CONF/state.json"
MOD_RULES="$MOD_CONF/rules.txt"
