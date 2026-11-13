# config 清单说明

[简体中文](README.md) · [繁體中文](README.zh-Hant.md) · [English](README.en.md) · [日本語](README.ja.md)

只改这些 txt，改完重新构建生效；本文件不编译进 APK。
包名大小写敏感，只写实测确认过的；来源写在 PR 描述，别写进清单注释。

## 格式
UTF-8；`#` 注释；字段用 空格 / 逗号 / 分号 分隔；编译期保序去重。

## desync_groups.txt · 避坑
一行 = 一组别互相共享的包名。主应用和目标端同落一行且不同包 → 弹避坑提示（只警告，不禁用）。

    com.gof.global com.gof.china

## sync_groups.txt · 已知可共享
一行 = 已知同步、可共用当前数据的一组服。主应用命中某行时，该行其余包名按行内顺序在添加目标应用的选择器里置顶（只置顶，不自动勾选）；主应用和目标端同落一行且不同包时 → 只跳过通用提示，静默。

    com.miHoYo.Yuanshen com.miHoYo.GenshinImpact com.miHoYo.ys.bilibil

不等于保证能共享：挂载风险、身份实读、版本检测、自动终止、撤销链都不豁免。误填会静默少弹一次，只写确认过的包名。

## containers.txt · 容器
一行 = 容器包名 + 目标APP目录模板；`{pkg}` 是目标应用包名，可能出现多次、扫描时全部替换。格式见清单开头注释。

    top.bienvenido.saas.i18n /sdcard/Android/obb/top.bienvenido.saas.i18n/scopedStorage/{pkg}/Android/data/{pkg}/

目录可能在 `/sdcard/Android/obb/容器APP包名/`、`/sdcard/Android/data/容器APP包名/` 或 `/data/data/容器APP包名/` 下的某个目录。默认内置元萝卜一行，别的容器另起一行。
扫描按首个 `{pkg}` 切前后缀，枚举前缀下一级子目录，目录名即候选包名，替换后目录存在就列为次级。
任意用户装了容器就显示，名称取组设置用户下的应用名、读不到回退包名。

## 优先级
避坑 > 白名单静默 > 通用提示。同一对包名同时进避坑+白名单，构建警告（不失败），运行时避坑优先。容器清单不参与。

## 提交
`assembleRelease` 自动跑 `genDesync`（纯 Gradle 进程内、Windows 可跑、不起 shell）；`scripts/gen_desync.sh` 是同规则独立工具。
只改 `config/*.txt`，别动 `res`、`Java`、`engine`、根 README，别改 `genDesync` 任务名与输出目录。
自检：能解析、命中对、构建日志无重叠警告。
