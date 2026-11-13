# config lists

[简体中文](README.md) · [繁體中文](README.zh-Hant.md) · [English](README.en.md) · [日本語](README.ja.md)

Edit only these .txt files and rebuild to apply. This file is not bundled into the APK.
Package names are case-sensitive; add only what you verified. Put the source in the PR description, not in list comments.

## Format
UTF-8; `#` comments; fields separated by spaces / commas / semicolons; normalised and de-duplicated in order at build time.

## desync_groups.txt · avoid
One line = a set of packages that should not share with each other. When the primary and a target both appear on one line and are different packages → an avoid warning (warning only, never blocks).

    com.gof.global com.gof.china

## sync_groups.txt · known-safe
One line = clients already known to stay in sync and reuse the same data. When the primary matches a line, its remaining packages are pinned to the top of the target picker in line order (pin only, no auto-select); when the primary and a target both appear on one line and differ → skip only the generic notice, silently.

    com.miHoYo.Yuanshen com.miHoYo.GenshinImpact com.miHoYo.ys.bilibil

This is not a guarantee: mount risk, identity read-back, version checks, auto-terminate and the revoke chain still apply. A wrong entry silently removes one warning — add only verified names.

## containers.txt · containers
One line = container package + directory template; `{pkg}` is the target app package, may appear more than once and is replaced everywhere during scanning. Syntax is in the comments at the top of the list.

    top.bienvenido.saas.i18n /sdcard/Android/obb/top.bienvenido.saas.i18n/scopedStorage/{pkg}/Android/data/{pkg}/

The directory may be somewhere under `/sdcard/Android/obb/<container>/`, `/sdcard/Android/data/<container>/` or `/data/data/<container>/`. One known container is built in (the line below); add other containers on new lines.
Scanning splits at the first `{pkg}`, enumerates the first-level subdirectories of the prefix, uses each directory name as the candidate package, replaces all `{pkg}` and lists it only if the resulting directory exists.
The entry appears when the container is installed on any user; its label comes from the group's user setting, falling back to the package name.

## Priority
avoid > known-safe silence > generic notice. A pair in both avoid and known-safe produces a build warning (not a failure); avoid wins at runtime. The container list does not take part.

## Submit
`assembleRelease` runs `genDesync` (in-Gradle, Windows-safe, no shell); `scripts/gen_desync.sh` is the standalone tool with the same rules.
Change only `config/*.txt`; leave `res`, Java, engine and the root README alone, and keep the `genDesync` task name and output directory.
Self-check: parses, matches as expected, no overlap warning in the build log.
