# AndroidDataShare

[简体中文](README.md) · [繁體中文](README.zh-Hant.md) · [English](README.en.md) · [日本語](README.ja.md)

Once the primary app has downloaded its asset pack, the other clients of the same game — a different server (another package), a cross-user install, or a container clone — can share that one copy: no second download, or several GB less space.

## Requirements

This is a configuration tool only: executing a share requires at least Shizuku; Shizuku-only can only Copy, generally limited to user 0 under /storage/emulated/0/, and a few directories marked private still fail. Sui / root can also use hard links and mounts. The project ships one APK. The boot-restore module is generated on demand and flashed once for mount groups only; hard links and copies live on disk and survive a reboot without any module. It is not resident and never checks versions; this app compares baselines.

## Use

Install the APK, pick a backend in the Execution permissions section on the home screen and tap Request permission, then create a group, choose the primary app and shared directories, add targets and save. Configuration and saving stay disabled until permission is granted, so nothing is set up without being able to run. Choose hard links / copy / mount to suit the game; the method can be switched later, and deleting a group revokes its share.

Mount groups can be restored automatically after a reboot: tap the generate button on the home screen and install the resulting zip from local storage in Magisk / KernelSU / APatch. Without the module everything still works, hard-link and copy groups need no such step; mount groups need this app opened once after a reboot to re-apply.

## Notes

- Keep both sides on the same resource version and update schedule. If they cannot be synced, no method aligns them: do not create a share and let each side download and update on its own.
- Hard links take no extra space; they do not follow later updates to the primary app, so tap Re-share to rebuild. Copy keeps an independent snapshot, takes extra space and likewise does not follow updates. Files inside a mounted directory follow automatically, but a mount may not take effect as expected; whether it works is confirmed by reading as the target app, and a successful command is not proof. You choose which directories are shared: if they contain account or login-state files, those are shared or cleared too — check before you proceed.
- Not every device or system combination has been verified. Anti-cheat and account risks in online games are your own responsibility; check behaviour on your device before using important data.
