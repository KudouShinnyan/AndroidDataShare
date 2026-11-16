#!/system/bin/sh
# AndroidDataShare execution engine - the single copy used by every backend.
#
# Two callers, byte-identical file:
#   1) App side: feed this file to `sh -s` through the selected backend, append one command.
#   2) Module side: sourced by the boot script, rules come from config/rules.txt.
#
# Only functions are defined here; base paths are rebased once on source.
# Rule lines (pipe separated):
#   R|<gid>|<mode>|<action>|<src_user>|<src_pkg>|<src_rel>|<t_user>|<t_pkg>|<t_rel>|<status>|<extra>
#   C|<gid>|<mode>|<action>|<src_user>|<src_pkg>|<src_rel>|<host>|<container>|<inner>|<t_rel>|<status>|<tpl>
#   {pkg} in tpl is replaced by the container clone package; an empty tpl uses the old layout.
#   action = mount | link | copy ; status = active | paused | terminated
#   src_rel "-" means the whole package directory.

ADS_TAG=AndroidDataShare
ADS_MEDIA=/data/media
ADS_BASE_KIND=parent
ADS_RUNTIME=/mnt/runtime
ADS_VIEWS="default read write"
ADS_APP_PKG=io.github.kudoushinnyan.datashare
ADS_MODULE=/data/adb/modules/AndroidDataShare
ADS_MODULE_STATE="$ADS_MODULE/config/state.json"
ADS_MODULE_RULES="$ADS_MODULE/config/rules.txt"
ADS_MODULE_PROP="$ADS_MODULE/module.prop"
ADS_CTRL=/data/local/tmp/ads_ctrl

ads_base_for() {
    if [ "$ADS_BASE_KIND" = "view" ]; then
        printf '%s' "$ADS_MEDIA"
    else
        printf '%s/%s' "$ADS_MEDIA" "$1"
    fi
}

ads_rebase() {
    ADS_LINK_DIR="$(ads_base_for 0)/Download/AndroidDataShare"
    ADS_LINK_FILE="$ADS_LINK_DIR/linkstate.json"
    ADS_MODULE_ZIP="$ADS_LINK_DIR/AndroidDataShare-module.zip"
    ADS_RUNLOG="$ADS_LINK_DIR/run.log"
}

ads_log() {
    printf '[%s] %s\n' "$ADS_TAG" "$1" >&2
}

ads_step() {
    if [ "$ADS_PROGRESS" = "1" ]; then
        printf 'ADS_STEP|%s|%s\n' "$1" "$2"
    fi
    return 0
}

engine_ctrl() {
    case "$1" in
        pause)  printf 'pause\n' > "$ADS_CTRL" 2>/dev/null ;;
        cancel) printf 'cancel\n' > "$ADS_CTRL" 2>/dev/null ;;
        continue|clear) rm -f "$ADS_CTRL" 2>/dev/null ;;
    esac
    return 0
}

ads_ctrl_wait() {
    while [ -f "$ADS_CTRL" ]; do
        ads_c=$(cat "$ADS_CTRL" 2>/dev/null)
        case "$ads_c" in
            cancel) return 2 ;;
            pause) sleep 1 ;;
            *) rm -f "$ADS_CTRL" 2>/dev/null; return 0 ;;
        esac
    done
    return 0
}

ads_choose_base() {
    [ "$(id -u)" = "0" ] && return 0
    ads_probe="$ADS_MEDIA/0/Android/data/.ads_probe.$$"
    if ( : > "$ads_probe" ) 2>/dev/null; then
        rm -f "$ads_probe" 2>/dev/null
        return 0
    fi
    ADS_BASE_KIND=view
    ads_cur=$(am get-current-user 2>/dev/null)
    case "$ads_cur" in ''|*[!0-9]*) ads_cur=$(cmd activity get-current-user 2>/dev/null) ;; esac
    case "$ads_cur" in ''|*[!0-9]*) ads_cur='' ;; esac
    ads_mv=$(awk '$2 ~ /^\/storage\/emulated\/[0-9]+$/ {print $2}' /proc/mounts 2>/dev/null | sort -u)
    ads_cands="$EXTERNAL_STORAGE /sdcard $ads_mv"
    [ -n "$ads_cur" ] && ads_cands="$ads_cands /storage/emulated/$ads_cur"
    ads_cands="$ads_cands /storage/emulated/0"
    ads_seen=" "
    for ads_c in $ads_cands; do
        case "$ads_c" in ''|/data/media*|/mnt/runtime*) continue ;; esac
        case "$ads_seen" in *" $ads_c "*) continue ;; esac
        ads_seen="$ads_seen$ads_c "
        ads_p="$ads_c/Android/data/.ads_probe.$$"
        if ( : > "$ads_p" ) 2>/dev/null; then
            rm -f "$ads_p" 2>/dev/null
            ADS_MEDIA="$ads_c"
            ads_rebase
            return 0
        fi
    done
    ADS_MEDIA=/storage/emulated/0
    ads_rebase
    ads_log "public storage probe failed; using $ADS_MEDIA"
    ads_step fail "public storage probe failed"
}

ads_split() {
    R1='' R2='' R3='' R4='' R5='' R6='' R7='' R8='' R9='' R10='' R11='' R12='' R13=''
    IFS='|' read -r R1 R2 R3 R4 R5 R6 R7 R8 R9 R10 R11 R12 R13 <<EOF
$1
EOF
}

ads_join_rel() {
    if [ "$2" = "-" ] || [ -z "$2" ]; then
        printf '%s\n' "$1"
    else
        printf '%s/%s\n' "$1" "$2"
    fi
}

ads_rule_src() {
    ads_join_rel "$(ads_base_for "$R5")/Android/data/$R6" "$R7"
}

ads_rule_dst() {
    if [ "$R1" = "R" ]; then
        ads_join_rel "$(ads_base_for "$R8")/Android/data/$R9" "$R10"
    elif [ -n "$R13" ]; then
        ads_base=$(printf '%s' "$R13" | sed "s/{pkg}/$R9/g")
        ads_join_rel "$ads_base" "$R11"
    else
        ads_join_rel "$(ads_base_for 0)/Android/obb/$R8/scopedStorage/$R9/Android/data/$R10" "$R11"
    fi
}

ads_view_path() {
    ads_join_rel "$ADS_RUNTIME/$1/emulated/$2/Android/data/$3" "$4"
}

ads_rule_dst_base() {
    if [ "$R1" = "R" ]; then
        printf '%s\n' "$(ads_base_for "$R8")/Android/data/$R9"
    elif [ -n "$R13" ]; then
        printf '%s' "$R13" | sed "s/{pkg}/$R9/g"
    else
        printf '%s\n' "$(ads_base_for 0)/Android/obb/$R8/scopedStorage/$R9/Android/data/$R10"
    fi
}

ads_prepare_dir() {
    mkdir -p "$1" 2>/dev/null || return 1
    [ "$ADS_BASE_KIND" = "view" ] && return 0
    if [ "$2" != "$ADS_LAST_PKG" ]; then
        ADS_LAST_PKG=$2
        if [ "$R1" = "R" ]; then
            ADS_LAST_UID=$(pm list packages -U --user "$R8" "$2" 2>/dev/null | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)
        else
            ADS_LAST_UID=$(pm list packages -U "$2" 2>/dev/null | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)
        fi
        [ -n "$ADS_LAST_UID" ] || ADS_LAST_UID=$(dumpsys package "$2" 2>/dev/null | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1)
    fi
    if [ -n "$ADS_LAST_UID" ]; then
        chown "$ADS_LAST_UID:$ADS_LAST_UID" "$1" 2>/dev/null
    fi
    restorecon "$1" 2>/dev/null
    return 0
}

ads_clean_rules() {
    while IFS= read -r ads_line; do
        case "$ads_line" in
            ''|'#'*) continue ;;
            'R|'*|'C|'*) printf '%s\n' "$ads_line" ;;
        esac
    done
}

ads_safe_target() {
    case "$2" in
        C)
            case "$1" in
                /data/media/*|/data/user/*|/mnt/runtime/*)
                    case "$1" in *..*) return 1 ;; esac
                    return 0 ;;
            esac
            return 1 ;;
    esac
    case "$1" in
        */Android/data/*|*/scopedStorage/*/Android/data/*) return 0 ;;
        *) ads_log "refuse unsafe path: $1"; return 1 ;;
    esac
}

ads_visible_path() {
    ads_join_rel "/storage/emulated/$1/Android/data/$2" "$3"
}

ads_probe_present() {
    ads_marker=$(find "$ads_src" -type f 2>/dev/null | head -1)
    [ -n "$ads_marker" ] || return 2
    ads_rel=${ads_marker#"$ads_src"}
    ads_rel=${ads_rel#/}
    [ -e "$1/$ads_rel" ] && return 0
    return 1
}

ads_umount_views() {
    for ads_v in $ADS_VIEWS; do
        ads_t=$(ads_view_path "$ads_v" "$R8" "$R9" "$R10")
        [ -d "$ads_t" ] && umount "$ads_t" 2>/dev/null
    done
    return 0
}

ads_mount_one() {
    ads_step rule "$R2|$R4|$R1"
    ads_diag=""
    ads_mode=single
    ads_probe=skip
    ads_ok=0
    ads_total=0
    ads_src=$(ads_rule_src)
    ads_dst=$(ads_rule_dst)
    if [ ! -d "$ads_src" ]; then
        ads_diag="source missing: $ads_src"
    elif [ "$ads_src" = "$ads_dst" ]; then
        ads_diag="source equals target: $ads_src"
    elif ! ads_safe_target "$ads_dst" "$R1"; then
        ads_diag="unsafe target: $ads_dst"
    else
        ads_prepare_dir "$ads_dst" "$R9" || ads_diag="$ads_diag cannot prepare target: $ads_dst;"
        if [ "$R1" != "R" ]; then
            ads_total=1
            if [ -d "$ads_dst" ]; then
                ads_step mount "mount -o bind '$ads_src' '$ads_dst'"
                ads_err=$(mount -o bind "$ads_src" "$ads_dst" 2>&1) && ads_ok=1 \
                    || ads_diag="$ads_diag bind $ads_dst failed: $ads_err;"
            else
                ads_diag="$ads_diag target dir absent: $ads_dst;"
            fi
        else
            ads_mode=views
            ads_vis=$(ads_visible_path "$R8" "$R9" "$R10")
            for ads_v in $ADS_VIEWS; do
                ads_total=$((ads_total + 1))
                ads_t=$(ads_view_path "$ads_v" "$R8" "$R9" "$R10")
                if [ ! -d "$ads_t" ]; then
                    ads_diag="$ads_diag $ads_v:absent($ads_t);"
                    continue
                fi
                ads_step mount "mount -o bind '$ads_src' '$ads_t'"
                ads_err=$(mount -o bind "$ads_src" "$ads_t" 2>&1) \
                    && ads_ok=$((ads_ok + 1)) \
                    || ads_diag="$ads_diag $ads_v:fail($ads_err);"
            done
            ads_use_base=0
            if [ "$ads_ok" -eq 0 ]; then
                ads_diag="$ads_diag no runtime view mounted;"
                ads_use_base=1
            else
                ads_probe_present "$ads_vis"
                case $? in
                    0) ads_probe=visible-views ;;
                    2) ads_probe=nofile-views ;;
                    *) ads_probe=not-visible; ads_diag="$ads_diag probe failed on views;"; ads_use_base=1 ;;
                esac
            fi
            if [ "$ads_use_base" = "1" ]; then
                ads_umount_views
                ads_mode=base
                ads_ok=0
                ads_total=1
                ads_step mount "mount -o bind '$ads_src' '$ads_dst'"
                ads_err=$(mount -o bind "$ads_src" "$ads_dst" 2>&1) \
                    && ads_ok=1 \
                    || ads_diag="$ads_diag bind $ads_dst failed: $ads_err;"
                ads_probe_present "$ads_vis"
                case $? in
                    0) ads_probe=visible-base ;;
                    2) ads_probe=nofile-base ;;
                    *) ads_probe=not-visible-base; ads_diag="$ads_diag still not visible at $ads_vis;" ;;
                esac
            fi
        fi
    fi
    ads_summary="mount $ads_ok/$ads_total $ads_src mode=$ads_mode probe=$ads_probe [$ads_diag]"
    ads_step mount "$(printf '%s' "$ads_summary" | tr '\n' ' ')"
    [ "$ads_ok" -gt 0 ]
}

ads_umount_one() {
    ads_step rule "$R2|$R4|$R1"
    [ "$R1" = "R" ] && ads_umount_views
    ads_dst=$(ads_rule_dst)
    [ -d "$ads_dst" ] && umount "$ads_dst" 2>/dev/null
    rmdir "$ads_dst" 2>/dev/null
    return 0
}

ads_link_probe() {
    ads_p_src=$(ads_rule_src)
    ads_p_dst=$(ads_rule_dst)
    ads_p_base=$(ads_rule_dst_base)
    [ -e "$ads_p_src" ] || return 2
    ads_safe_target "$ads_p_dst" "$R1" || return 2
    if [ -f "$ads_p_src" ]; then
        ads_p_f=$ads_p_src
    else
        ads_p_f=$(find "$ads_p_src" -type f 2>/dev/null | head -1)
    fi
    [ -n "$ads_p_f" ] || return 2
    ads_p_made=''
    ads_p_cur=$ads_p_base
    while [ ! -d "$ads_p_cur" ]; do
        ads_p_made="$ads_p_made $ads_p_cur"
        ads_p_cur=$(dirname "$ads_p_cur")
    done
    [ -z "$ads_p_made" ] || mkdir -p "$ads_p_base" 2>/dev/null || return 1
    ads_p_probe="$ads_p_base/.ads_link_probe.$$"
    rm -f "$ads_p_probe" 2>/dev/null
    ADS_PROBE_ERR=$(ln "$ads_p_f" "$ads_p_probe" 2>&1)
    ads_p_rc=$?
    if [ "$ads_p_rc" -eq 0 ]; then
        if [ ! -e "$ads_p_probe" ]; then
            ads_p_rc=1
            ADS_PROBE_ERR="link reported success but target file is missing"
        else
            ads_p_i1=$(stat -c %i "$ads_p_f" 2>/dev/null)
            ads_p_i2=$(stat -c %i "$ads_p_probe" 2>/dev/null)
            if [ -n "$ads_p_i1" ] && [ -n "$ads_p_i2" ] && [ "$ads_p_i1" != "$ads_p_i2" ]; then
                ads_p_rc=1
                ADS_PROBE_ERR="inode mismatch after link"
            fi
        fi
    fi
    rm -f "$ads_p_probe" 2>/dev/null
    for ads_p_d in $ads_p_made; do
        rmdir "$ads_p_d" 2>/dev/null
    done
    return "$ads_p_rc"
}

ads_precheck_links() {
    ADS_LINK_OK=1
    ADS_PROBE_ERR=''
    ads_p_seen=0
    while IFS= read -r ads_line; do
        case "$ads_line" in ''|'#'*) continue ;; esac
        case "$ads_line" in 'R|'*|'C|'*) ;; *) continue ;; esac
        ads_split "$ads_line"
        [ "$R4" = "link" ] || continue
        ads_link_probe
        ads_p=$?
        [ "$ads_p" -eq 2 ] && continue
        ads_p_seen=1
        if [ "$ads_p" -ne 0 ]; then
            ADS_LINK_OK=0
            break
        fi
    done
    if [ "$ADS_LINK_OK" = "0" ]; then
        ads_step precheck "hardlink unsupported [$(printf '%s' "$ADS_PROBE_ERR" | tr '\n' ' ')]"
        printf 'ADS_LINK_UNSUPPORTED\n'
    elif [ "$ads_p_seen" = "1" ]; then
        ads_step precheck "hardlink precheck ok"
    fi
    return 0
}

ads_link_one() {
    ads_step rule "$R2|$R4|$R1"
    if [ "$ADS_LINK_OK" = "0" ]; then
        ads_step skip "$R2|$R7|hardlink unsupported"
        return 1
    fi
    ads_src=$(ads_rule_src)
    ads_dst=$(ads_rule_dst)
    [ -e "$ads_src" ] || { ads_step skip "$R2|$R7|missing source"; return 1; }
    ads_safe_target "$ads_dst" "$R1" || { ads_step fail "unsafe: $ads_dst"; return 1; }
    if [ -f "$ads_src" ]; then
        ads_prepare_dir "$(dirname "$ads_dst")" "$R9" || return 1
        if ads_err=$(ln -f "$ads_src" "$ads_dst" 2>&1); then
            ads_step link "ln -f '$ads_src' '$ads_dst'"
            return 0
        fi
        ads_step fail "link failed: $ads_src [$(printf '%s' "$ads_err" | tr '\n' ' ')]"
        printf 'ADS_LINK_UNSUPPORTED\n'
        return 1
    fi
    ads_list=$(mktemp 2>/dev/null) || ads_list=/data/local/tmp/ads_link_list.$$
    if ! find "$ads_src" -type f > "$ads_list" 2>/dev/null; then
        rm -f "$ads_list"
        ads_step fail "cannot walk: $ads_src"
        return 1
    fi
    ads_prepare_dir "$ads_dst" "$R9" || { rm -f "$ads_list"; return 1; }
    ads_nlink=0 ads_nfail=0 ads_err1='' ads_fail1='' ads_cancelled=0
    while IFS= read -r ads_f; do
        ads_ctrl_wait
        if [ $? -eq 2 ]; then ads_cancelled=1; break; fi
        ads_rel=${ads_f#"$ads_src"/}
        case "$ads_rel" in *linkstate.json) continue ;; esac
        ads_t="$ads_dst/$ads_rel"
        ads_prepare_dir "$(dirname "$ads_t")" "$R9" || { ads_nfail=$((ads_nfail + 1)); continue; }
        if ads_err=$(ln -f "$ads_f" "$ads_t" 2>&1); then
            ads_nlink=$((ads_nlink + 1))
            ads_step link "ln -f '$ads_f' '$ads_t'"
        else
            if [ -z "$ads_err1" ]; then ads_err1=$ads_err; ads_fail1=$ads_f; fi
            ads_nfail=$((ads_nfail + 1))
        fi
    done < "$ads_list"
    rm -f "$ads_list"
    if [ "$ads_cancelled" = "1" ]; then
        ADS_CANCELLED=1
        ads_step fail "cancelled by user"
        return 1
    fi
    ads_diag=$(printf '%s' "${ads_err1:-none}" | tr '\n' ' ')
    ads_step link "link $ads_nlink linked, $ads_nfail failed $ads_src first=${ads_fail1:-none} [$ads_diag]"
    if [ "$ads_nlink" -eq 0 ] && [ "$ads_nfail" -gt 0 ]; then
        printf 'ADS_LINK_UNSUPPORTED\n'
    fi
    [ "$ads_nfail" -eq 0 ]
}

ads_copy_one() {
    ads_step rule "$R2|$R4|$R1"
    ads_src=$(ads_rule_src)
    ads_dst=$(ads_rule_dst)
    [ -e "$ads_src" ] || { ads_step fail "missing: $ads_src"; return 1; }
    ads_safe_target "$ads_dst" "$R1" || { ads_step fail "unsafe: $ads_dst"; return 1; }
    if [ -f "$ads_src" ]; then
        ads_prepare_dir "$(dirname "$ads_dst")" "$R9" || return 1
        ads_step copy "cp -f '$ads_src' '$ads_dst'"
        cp -f "$ads_src" "$ads_dst" 2>/dev/null || { ads_step fail "copy: $ads_src"; return 1; }
    else
        ads_prepare_dir "$ads_dst" "$R9" || return 1
        ads_step copy "cp -a '$ads_src'/. '$ads_dst'/"
        cp -a "$ads_src"/. "$ads_dst"/ 2>/dev/null || { ads_step fail "copy: $ads_src"; return 1; }
    fi
    return 0
}

engine_clear_bases() {
    ads_only=${1:-}
    ADS_CLEARED=""
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ -n "$ads_only" ] && [ "$R4" != "$ads_only" ] && continue
        [ "$R4" = "link" ] && [ "$ADS_LINK_OK" = "0" ] && continue
        ads_b=$(ads_rule_dst_base)
        case " $ADS_CLEARED " in *" $ads_b "*) continue ;; esac
        ADS_CLEARED="$ADS_CLEARED $ads_b"
        ads_clear_base "$ads_b" "$R1"
    done
}

ads_clear_base() {
    ads_b=$1
    ads_safe_target "$ads_b" "$2" || return 0
    [ "$ads_b" = "$(ads_base_for "$R5")/Android/data/$R6" ] && {
        ads_log "refuse clearing primary: $ads_b"; return 0; }
    [ -d "$ads_b" ] || return 0
    ads_step clear "clear '$ads_b'"
    for ads_x in "$ads_b"/* "$ads_b"/.[!.]* "$ads_b"/..?*; do
        [ -e "$ads_x" ] || [ -L "$ads_x" ] || continue
        case "$ads_x" in *linkstate.json) continue ;; esac
        rm -rf "$ads_x" 2>/dev/null
    done
    return 0
}

engine_cancel_cleanup() {
    ads_step phase "cancel: clearing link targets"
    engine_clear_bases link < "$1"
    printf 'ADS_CANCELLED\n'
}

ads_rules_from() {
    if [ -n "$1" ] && [ -f "$1" ]; then
        ads_clean_rules < "$1"
    else
        ads_clean_rules
    fi
}

engine_detach_gid() {
    ads_step phase "detach $1"
    ads_gid=$1
    ads_file=$2
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_detach.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R2" = "$ads_gid" ] || continue
        ads_umount_one
    done < "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R2" = "$ads_gid" ] && printf '%s\n' "$ads_line"
    done < "$ads_tmp" | engine_clear_bases
    rm -f "$ads_tmp"
    ads_log "detach done: $ads_gid"
}

engine_reapply_gid() {
    engine_detach_gid "$1" "$2"
    ads_step phase "reapply $1"
    ADS_CANCELLED=0
    ads_gid=$1
    ads_file=$2
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_reapply.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    for ads_act in mount link copy; do
        [ "$ADS_CANCELLED" = "1" ] && break
        while IFS= read -r ads_line; do
            [ "$ADS_CANCELLED" = "1" ] && break
            ads_split "$ads_line"
            [ "$R2" = "$ads_gid" ] || continue
            [ "$R4" = "$ads_act" ] || continue
            case "$ads_act" in
                mount) ads_mount_one ;;
                link)  ads_link_one ;;
                copy)  ads_copy_one ;;
            esac
        done < "$ads_tmp"
    done
    [ "$ADS_CANCELLED" = "1" ] && engine_cancel_cleanup "$ads_tmp"
    rm -f "$ads_tmp" "$ADS_CTRL" 2>/dev/null
    ads_log "reapply done: $ads_gid"
}

engine_pause_gid() {
    ads_step phase "pause $1"
    ads_gid=$1
    ads_file=$2
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_pause.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R2" = "$ads_gid" ] && [ "$R4" = "mount" ] && ads_umount_one
    done < "$ads_tmp"
    rm -f "$ads_tmp"
    ads_log "pause done: $ads_gid"
}

engine_resume_gid() {
    ads_step phase "resume $1"
    ads_gid=$1
    ads_file=$2
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_resume.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R2" = "$ads_gid" ] && [ "$R4" = "mount" ] && ads_mount_one
    done < "$ads_tmp"
    rm -f "$ads_tmp"
    ads_log "resume done: $ads_gid"
}

engine_action() {
    ads_step phase "action $1 $2"
    ads_op=$1
    ads_gid=$2
    ads_file=$(mktemp 2>/dev/null) || ads_file=/data/local/tmp/ads_action.$$
    ads_clean_rules > "$ads_file"
    case "$ads_op" in
        detach|terminate|clear)   engine_detach_gid "$ads_gid" "$ads_file" ;;
        reapply|restore|rebuild|resync) engine_reapply_gid "$ads_gid" "$ads_file" ;;
        pause)  engine_pause_gid "$ads_gid" "$ads_file" ;;
        resume) engine_resume_gid "$ads_gid" "$ads_file" ;;
        *) ads_log "unknown action: $ads_op"; rm -f "$ads_file"; return 1 ;;
    esac
    rm -f "$ads_file" 2>/dev/null
}

engine_apply_all() {
    ads_step phase "apply all"
    ads_fail=0
    ADS_CANCELLED=0
    ads_file=${ADS_RULES_FROM:-}
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_apply.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    for ads_act in mount link copy; do
        [ "$ADS_CANCELLED" = "1" ] && break
        while IFS= read -r ads_line; do
            [ "$ADS_CANCELLED" = "1" ] && break
            ads_split "$ads_line"
            [ "$R4" = "$ads_act" ] || continue
            if [ "$R1" = "R" ]; then ads_st=$R11; else ads_st=$R12; fi
            [ "$ads_st" = "active" ] || continue
            if [ "$ADS_BOOT_RESTORE" = "1" ] && [ "$R3" != "mount" ]; then
                continue
            fi
            if [ "$ADS_BASE_KIND" = "view" ]; then
                if [ "$R1" != "R" ] || [ "$R5" != "0" ] || [ "$R8" != "0" ]; then
                    ads_step skip "$R2|$R7|unsupported on emulated storage"
                    ads_fail=$((ads_fail + 1))
                    continue
                fi
            fi
            case "$ads_act" in
                mount) ads_mount_one || ads_fail=$((ads_fail + 1)) ;;
                link)  ads_link_one  || { [ "$ADS_CANCELLED" = "1" ] || ads_fail=$((ads_fail + 1)); } ;;
                copy)  ads_copy_one  || ads_fail=$((ads_fail + 1)) ;;
            esac
        done < "$ads_tmp"
    done
    [ "$ADS_CANCELLED" = "1" ] && engine_cancel_cleanup "$ads_tmp"
    rm -f "$ads_tmp"
    rm -f "$ADS_CTRL" 2>/dev/null
    if [ "$ads_fail" -gt 0 ]; then
        printf 'ADS_RULE_FAIL|%s\n' "$ads_fail"
    fi
    ads_log "apply_all done (fail=$ads_fail)"
}

engine_clear_targets() {
    ads_step phase "clear target directories"
    ads_ct=$(mktemp 2>/dev/null) || ads_ct=/data/local/tmp/ads_ct.$$
    ads_clean_rules > "$ads_ct"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        ads_umount_one
    done < "$ads_ct"
    engine_clear_bases < "$ads_ct"
    rm -f "$ads_ct"
    ads_log "clear_targets done"
}

engine_release_rules() {
    ads_step phase "release old rules"
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_rel.$$
    ads_clean_rules > "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R4" = "mount" ] && ads_umount_one
    done < "$ads_tmp"
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        case "$R4" in link|copy) printf '%s\n' "$ads_line" ;; esac
    done < "$ads_tmp" | engine_clear_bases
    rm -f "$ads_tmp"
    ads_log "release_rules done"
}

engine_versions() {
    ads_u=$1
    ads_p=$2
    ads_sent=$3
    ads_vc=$(dumpsys package "$ads_p" 2>/dev/null | awk '/versionCode=/{print; exit}' | tr -dc '0-9')
    ads_vn=$(dumpsys package "$ads_p" 2>/dev/null | awk '/versionName=/{sub(/.*versionName=/,""); sub(/ .*/,""); print; exit}')
    ads_hash=-
    if [ -n "$ads_sent" ] && [ "$ads_sent" != "-" ]; then
        ads_f="$(ads_base_for "$ads_u")/Android/data/$ads_p/$ads_sent"
        if [ -f "$ads_f" ]; then
            ads_hash=$(cksum "$ads_f" 2>/dev/null | awk '{print $1}')
            [ -n "$ads_hash" ] || ads_hash=-
        fi
    fi
    [ -n "$ads_vc" ] || ads_vc=-
    [ -n "$ads_vn" ] || ads_vn=-
    printf 'vc=%s|vn=%s|sent=%s\n' "$ads_vc" "$ads_vn" "$ads_hash"
}

engine_scan_items() {
    ads_u=$1
    ads_p=$2
    ads_base="$(ads_base_for "$ads_u")/Android/data/$ads_p"
    [ -d "$ads_base" ] || return 0
    for ads_x in "$ads_base"/*; do
        [ -d "$ads_x" ] && printf 'D|%s\n' "${ads_x##*/}"
        [ -f "$ads_x" ] && printf 'F|%s\n' "${ads_x##*/}"
    done | sort -u | head -300
}

engine_list_users() {
    pm list users 2>/dev/null
}

engine_list_packages() {
    pm list packages --user "$1" 2>/dev/null | awk -F: '{print $2}' | sort
}

engine_container_scan() {
    ads_prefix=$1
    ads_rest=$2
    [ -d "$ads_prefix" ] || return 0
    for ads_d in "$ads_prefix"/*; do
        [ -d "$ads_d" ] || continue
        ads_pkg=${ads_d##*/}
        case "$ads_pkg" in .*|*..*) continue ;; esac
        ads_tail=$(printf '%s' "$ads_rest" | sed "s/{pkg}/$ads_pkg/g")
        [ -d "$ads_prefix$ads_pkg$ads_tail" ] && printf '%s\n' "$ads_pkg"
    done | sort -u | head -200
}

engine_publish() {
    mkdir -p "${1%/*}" 2>/dev/null || { ads_log "cannot create ${1%/*}"; return 1; }
    ads_tmp="$1.tmp"
    if [ -n "$2" ]; then
        [ -f "$2" ] || return 1
        cat "$2" > "$ads_tmp" 2>/dev/null || { rm -f "$ads_tmp"; return 1; }
    else
        cat > "$ads_tmp" 2>/dev/null || { rm -f "$ads_tmp"; return 1; }
    fi
    mv -f "$ads_tmp" "$1" 2>/dev/null || { rm -f "$ads_tmp"; return 1; }
    chmod 0644 "$1" 2>/dev/null
    [ -n "$2" ] && rm -f "$2" 2>/dev/null
    return 0
}

engine_flag() {
    if [ -f "$2" ]; then
        printf '%s=1\n' "$1"
    else
        printf '%s=0\n' "$1"
    fi
}

engine_read() {
    [ -f "$1" ] || return 0
    cat "$1" 2>/dev/null
}


engine_linkstate_remove() {
    rm -f "$ADS_LINK_FILE" 2>/dev/null
    return 0
}

engine_module_config_remove() {
    rm -f "$ADS_MODULE_STATE" "$ADS_MODULE_RULES" 2>/dev/null
    return 0
}

ads_rebase
