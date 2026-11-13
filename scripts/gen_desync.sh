#!/bin/sh
set -u
cfg=${1:-}
out=${2:-}
if [ -z "$cfg" ] || [ ! -d "$cfg" ] || [ -z "$out" ]; then
    echo "usage: gen_desync.sh <config-dir> <output-dir>" >&2
    exit 0
fi
mkdir -p "$out"

read_lines() {
    src=$1
    tmp=$2
    tag=$3
    : > "$tmp"
    if [ -f "$src" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            line=$(printf '%s' "$line" | tr -d '\r' | tr -d '\357\273\277')
            case "$line" in ''|'#'*) continue ;; esac
            printf '%s\n' "$(printf '%s' "$line" | tr ',;' '  ' | tr -s ' ' | sed 's/^ //;s/ $//')" >> "$tmp"
        done < "$src"
    else
        echo "$tag: source missing, generating an empty list" >&2
    fi
}

finish() {
    valid=$1
    dst=$2
    tag=$3
    awk '!seen[$0]++' "$valid" > "$dst"
    rm -f "$valid"
    echo "$tag: wrote $dst"
}

normalise() {
    src=$1
    dst=$2
    tag=$3
    tmp="$out/.${tag}.tmp"
    read_lines "$src" "$tmp" "$tag"
    valid="${tmp}.valid"
    : > "$valid"
    while IFS= read -r norm; do
        count=0
        ok=1
        for p in $norm; do
            printf '%s' "$p" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || ok=0
            count=$((count + 1))
        done
        if [ "$ok" -ne 1 ] || [ "$count" -lt 2 ]; then
            echo "$tag: skipped invalid or single-package line: $norm" >&2
            continue
        fi
        printf '%s\n' "$norm" >> "$valid"
    done < "$tmp"
    rm -f "$tmp"
    finish "$valid" "$dst" "$tag"
}

normalise_containers() {
    src=$1
    dst=$2
    tmp="$out/.containers.tmp"
    read_lines "$src" "$tmp" containers
    valid="${tmp}.valid"
    : > "$valid"
    while IFS= read -r norm; do
        count=0
        host=''
        tpl=''
        for p in $norm; do
            count=$((count + 1))
            [ "$count" = 1 ] && host=$p
            [ "$count" = 2 ] && tpl=$p
        done
        if [ "$count" -ne 2 ] \
            || ! printf '%s' "$host" | grep -Eq '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' \
            || ! printf '%s' "$tpl" | grep -Eq '^/.*\{pkg\}'; then
            echo "containers: skipped invalid line: $norm" >&2
            continue
        fi
        printf '%s %s\n' "$host" "$tpl" >> "$valid"
    done < "$tmp"
    rm -f "$tmp"
    finish "$valid" "$dst" containers
}

normalise "$cfg/desync_groups.txt" "$out/desync_groups.txt" desync
normalise "$cfg/sync_groups.txt" "$out/sync_groups.txt" sync
normalise_containers "$cfg/containers.txt" "$out/containers.txt"

overlaps=$(awk '
function key(a, b,   t) { if (a > b) { t = a; a = b; b = t }; return a "#" b }
NR == FNR {
    n = split($0, p, " ")
    for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) seen[key(p[i], p[j])] = 1
    next
}
{
    n = split($0, p, " ")
    for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) {
        k = key(p[i], p[j])
        if (k in seen && !(k in told)) { print k; told[k] = 1 }
    }
}
' "$out/desync_groups.txt" "$out/sync_groups.txt")
if [ -n "$overlaps" ]; then
    printf '%s\n' "$overlaps" | tr '#' ' ' | while IFS= read -r pair; do
        echo "desync/sync: overlapping package pair (desync takes priority): $pair" >&2
    done
fi
