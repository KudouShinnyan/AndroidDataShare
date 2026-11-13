#!/system/bin/sh
# AndroidDataShare execution engine - the single copy used by every backend.
#
# Two callers, byte-identical file:
#   1) App side: feed this file to `sh -s` through the selected backend, append one command.
#   2) Module side: sourced by the boot script, rules come from config/rules.txt.
#
# Only functions are defined here; nothing runs on source.
# Rule lines (pipe separated):
#   R|<gid>|<mode>|<action>|<src_user>|<src_pkg>|<src_rel>|<t_user>|<t_pkg>|<t_rel>|<status>|<extra>
#   C|<gid>|<mode>|<action>|<src_user>|<src_pkg>|<src_rel>|<host>|<container>|<inner>|<t_rel>|<status>|<tpl>
#   {pkg} in tpl is replaced by the container clone package; an empty tpl uses the old layout.
#   action = mount | link | copy ; status = active | paused | terminated
#   src_rel "-" means the whole package directory.

ADS_TAG=AndroidDataShare
ADS_MEDIA=/data/media
ADS_RUNTIME=/mnt/runtime
ADS_VIEWS="default read write"
ADS_APP_PKG=io.github.kudoushinnyan.datashare
ADS_MODULE=/data/adb/modules/AndroidDataShare
ADS_MODULE_STATE="$ADS_MODULE/config/state.json"
ADS_MODULE_RULES="$ADS_MODULE/config/rules.txt"
ADS_LINK_DIR="$ADS_MEDIA/0/Download/AndroidDataShare"
ADS_LINK_FILE="$ADS_LINK_DIR/linkstate.json"
ADS_MODULE_ZIP="$ADS_LINK_DIR/AndroidDataShare-module.zip"
ADS_RUNLOG="$ADS_LINK_DIR/run.log"
ADS_MODULE_PROP="$ADS_MODULE/module.prop"
ADS_MEDIA_PUBLIC=/storage/emulated

ads_log() {
    printf '[%s] %s\n' "$ADS_TAG" "$1" >&2
}

ads_step() {
    if [ "$ADS_PROGRESS" = "1" ]; then
        printf 'ADS_STEP|%s|%s\n' "$1" "$2"
    fi
    return 0
}

ads_choose_base() {
    [ "$(id -u)" = "0" ] && return 0
    ads_probe="$ADS_MEDIA/0/Android/data/.ads_probe.$$"
    if ( : > "$ads_probe" ) 2>/dev/null; then
        rm -f "$ads_probe" 2>/dev/null
        return 0
    fi
    ADS_MEDIA="$ADS_MEDIA_PUBLIC"
    ADS_LINK_DIR="$ADS_MEDIA/0/Download/AndroidDataShare"
    ADS_LINK_FILE="$ADS_LINK_DIR/linkstate.json"
    ADS_MODULE_ZIP="$ADS_LINK_DIR/AndroidDataShare-module.zip"
    ADS_RUNLOG="$ADS_LINK_DIR/run.log"
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
    ads_join_rel "$ADS_MEDIA/$R5/Android/data/$R6" "$R7"
}

ads_rule_dst() {
    if [ "$R1" = "R" ]; then
        ads_join_rel "$ADS_MEDIA/$R8/Android/data/$R9" "$R10"
    elif [ -n "$R13" ]; then
        ads_base=$(printf '%s' "$R13" | sed "s/{pkg}/$R9/g")
        ads_join_rel "$ads_base" "$R11"
    else
        ads_join_rel "$ADS_MEDIA/0/Android/obb/$R8/scopedStorage/$R9/Android/data/$R10" "$R11"
    fi
}

ads_view_path() {
    ads_join_rel "$ADS_RUNTIME/$1/emulated/$2/Android/data/$3" "$4"
}

ads_rule_dst_base() {
    if [ "$R1" = "R" ]; then
        printf '%s\n' "$ADS_MEDIA/$R8/Android/data/$R9"
    elif [ -n "$R13" ]; then
        printf '%s' "$R13" | sed "s/{pkg}/$R9/g"
    else
        printf '%s\n' "$ADS_MEDIA/0/Android/obb/$R8/scopedStorage/$R9/Android/data/$R10"
    fi
}

ads_prepare_dir() {
    mkdir -p "$1" 2>/dev/null || return 1
    [ "$ADS_MEDIA" = "$ADS_MEDIA_PUBLIC" ] && return 0
    if [ "$2" != "$ADS_LAST_PKG" ]; then
        ADS_LAST_PKG=$2
        ADS_LAST_UID=$(pm list packages -U "$2" 2>/dev/null | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)
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

ads_mount_one() {
    ads_step rule "$R2|$R4|$R1"
    ads_src=$(ads_rule_src)
    [ -d "$ads_src" ] || { ads_log "source missing: $ads_src"; return 1; }
    ads_dst=$(ads_rule_dst)
    ads_safe_target "$ads_dst" "$R1" || return 1
    ads_prepare_dir "$ads_dst" "$R9" || ads_log "cannot prepare target: $ads_dst"
    ads_ok=0
    ads_total=0
    if [ "$R1" = "R" ]; then
        for ads_v in $ADS_VIEWS; do
            ads_total=$((ads_total + 1))
            ads_t=$(ads_view_path "$ads_v" "$R8" "$R9" "$R10")
            if [ -d "$ads_t" ]; then
                ads_step mount "mount -o bind '$ads_src' '$ads_t'"
                mount -o bind "$ads_src" "$ads_t" 2>/dev/null && ads_ok=$((ads_ok + 1))
            fi
        done
    else
        ads_total=1
        [ -d "$ads_dst" ] && {
            ads_step mount "mount -o bind '$ads_src' '$ads_dst'"
            mount -o bind "$ads_src" "$ads_dst" 2>/dev/null && ads_ok=1
        }
    fi
    ads_log "mount $ads_ok/$ads_total $ads_src"
    [ "$ads_ok" -gt 0 ]
}

ads_umount_one() {
    ads_step rule "$R2|$R4|$R1"
    if [ "$R1" = "R" ]; then
        for ads_v in $ADS_VIEWS; do
            ads_t=$(ads_view_path "$ads_v" "$R8" "$R9" "$R10")
            [ -d "$ads_t" ] && umount "$ads_t" 2>/dev/null
        done
    fi
    ads_dst=$(ads_rule_dst)
    [ -d "$ads_dst" ] && umount "$ads_dst" 2>/dev/null
    rmdir "$ads_dst" 2>/dev/null
    return 0
}

ads_link_one() {
    ads_step rule "$R2|$R4|$R1"
    ads_src=$(ads_rule_src)
    ads_dst=$(ads_rule_dst)
    [ -e "$ads_src" ] || { ads_step fail "missing: $ads_src"; return 1; }
    ads_safe_target "$ads_dst" "$R1" || { ads_step fail "unsafe: $ads_dst"; return 1; }
    if [ -f "$ads_src" ]; then
        ads_prepare_dir "$(dirname "$ads_dst")" "$R9" || return 1
        ads_step link "ln -f '$ads_src' '$ads_dst'"
        ln -f "$ads_src" "$ads_dst" 2>/dev/null || { ads_step fail "link: $ads_src"; return 1; }
    else
        ads_prepare_dir "$ads_dst" "$R9" || return 1
        find "$ads_src" -type f 2>/dev/null | while IFS= read -r ads_f; do
            ads_rel=${ads_f#"$ads_src"/}
            case "$ads_rel" in *linkstate.json) continue ;; esac
            ads_t="$ads_dst/$ads_rel"
            ads_prepare_dir "$(dirname "$ads_t")" "$R9" || continue
            ads_step link "ln -f '$ads_f' '$ads_t'"
            ln -f "$ads_f" "$ads_t" 2>/dev/null || ads_step fail "link: $ads_f"
        done
    fi
    return 0
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

ads_clear_one() {
    ads_step rule "$R2|$R4|$R1"
    ads_src=$(ads_rule_src)
    ads_dst=$(ads_rule_dst)
    ads_safe_target "$ads_dst" "$R1" || return 1
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_clear.$$
    : > "$ads_tmp"
    if [ -f "$ads_src" ]; then
        printf '%s\n' "$ads_dst" >> "$ads_tmp"
    elif [ -d "$ads_src" ]; then
        find "$ads_src" -type f 2>/dev/null | while IFS= read -r ads_f; do
            printf '%s\n' "$ads_dst/${ads_f#"$ads_src"/}" >> "$ads_tmp"
        done
    fi
    if [ -d "$ads_dst" ]; then
        find "$ads_dst" -type f 2>/dev/null | while IFS= read -r ads_t; do
            ads_safe_target "$ads_t" "$R1" >/dev/null 2>&1 && printf '%s\n' "$ads_t" >> "$ads_tmp"
        done
    fi
    sort -u "$ads_tmp" | while IFS= read -r ads_p; do
        case "$ads_p" in *linkstate.json) continue ;; esac
        ads_step clear "rm -f '$ads_p'"
        rm -f "$ads_p" 2>/dev/null
    done
    rm -f "$ads_tmp"
    ads_log "cleared recorded paths under $ads_dst"
    return 0
}

ads_clear_base() {
    ads_b=$1
    ads_safe_target "$ads_b" "$2" || return 0
    [ "$ads_b" = "$ADS_MEDIA/$R5/Android/data/$R6" ] && {
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
    ADS_CLEARED=""
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        [ "$R2" = "$ads_gid" ] || continue
        ads_b=$(ads_rule_dst_base)
        case " $ADS_CLEARED " in *" $ads_b "*) continue ;; esac
        ADS_CLEARED="$ADS_CLEARED $ads_b"
        ads_clear_base "$ads_b" "$R1"
    done < "$ads_tmp"
    rm -f "$ads_tmp"
    ads_log "detach done: $ads_gid"
}

engine_reapply_gid() {
    engine_detach_gid "$1" "$2"
    ads_step phase "reapply $1"
    ads_gid=$1
    ads_file=$2
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_reapply.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    for ads_act in mount link copy; do
        while IFS= read -r ads_line; do
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
    rm -f "$ads_tmp"
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
    ads_file=$3
    case "$ads_op" in
        detach|terminate|clear)   engine_detach_gid "$ads_gid" "$ads_file" ;;
        reapply|restore|rebuild|resync) engine_reapply_gid "$ads_gid" "$ads_file" ;;
        pause)  engine_pause_gid "$ads_gid" "$ads_file" ;;
        resume) engine_resume_gid "$ads_gid" "$ads_file" ;;
        *) ads_log "unknown action: $ads_op"; return 1 ;;
    esac
}

engine_apply_all() {
    ads_step phase "apply all"
    ads_fail=0
    ads_file=${ADS_RULES_FROM:-}
    ads_tmp=$(mktemp 2>/dev/null) || ads_tmp=/data/local/tmp/ads_apply.$$
    ads_rules_from "$ads_file" > "$ads_tmp"
    for ads_act in mount link copy; do
        while IFS= read -r ads_line; do
            ads_split "$ads_line"
            [ "$R4" = "$ads_act" ] || continue
            if [ "$R1" = "R" ]; then ads_st=$R11; else ads_st=$R12; fi
            [ "$ads_st" = "active" ] || continue
            if [ "$ADS_BOOT_RESTORE" = "1" ] && [ "$R3" != "mount" ]; then
                continue
            fi
            if [ "$ADS_MEDIA" = "$ADS_MEDIA_PUBLIC" ]; then
                if [ "$R1" != "R" ] || [ "$R5" != "0" ] || [ "$R8" != "0" ]; then
                    ads_step skip "$R2|$R7|unsupported on emulated storage"
                    ads_fail=$((ads_fail + 1))
                    continue
                fi
            fi
            case "$ads_act" in
                mount) ads_mount_one || ads_fail=$((ads_fail + 1)) ;;
                link)  ads_link_one  || ads_fail=$((ads_fail + 1)) ;;
                copy)  ads_copy_one  || ads_fail=$((ads_fail + 1)) ;;
            esac
        done < "$ads_tmp"
    done
    rm -f "$ads_tmp"
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
    ADS_CLEARED=""
    while IFS= read -r ads_line; do
        ads_split "$ads_line"
        ads_b=$(ads_rule_dst_base)
        case " $ADS_CLEARED " in *" $ads_b "*) continue ;; esac
        ADS_CLEARED="$ADS_CLEARED $ads_b"
        ads_clear_base "$ads_b" "$R1"
    done < "$ads_ct"
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
        case "$R4" in link|copy) ads_clear_one ;; esac
    done < "$ads_tmp"
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
        ads_f="$ADS_MEDIA/$ads_u/Android/data/$ads_p/$ads_sent"
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
    ads_base="$ADS_MEDIA/$ads_u/Android/data/$ads_p"
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
