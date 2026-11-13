# config 清單說明

[简体中文](README.md) · [繁體中文](README.zh-Hant.md) · [English](README.en.md) · [日本語](README.ja.md)

只改這些 txt，改完重新建置才會生效；本檔不會編進 APK。
套件名稱區分大小寫，只寫實測確認過的；來源寫在 PR 描述，別寫進清單註解。

## 格式
UTF-8；`#` 註解；欄位以 空格 / 逗號 / 分號 分隔；編譯期保序去重。

## desync_groups.txt · 避坑
一行 = 一組不該互相共用的套件。主應用與目標端同落一行且不同套件 → 彈避坑提示（只警告，不禁用）。

    com.gof.global com.gof.china

## sync_groups.txt · 已知可共用
一行 = 已知更新同步、可共用同一份資料的一組服。主應用命中某行時，該行其餘套件依行內順序在新增目標應用的選擇器裡置頂（只置頂，不自動勾選）；主應用與目標端同落一行且不同套件時 → 只跳過一般提示，靜默處理。

    com.miHoYo.Yuanshen com.miHoYo.GenshinImpact com.miHoYo.ys.bilibil

不等於保證能共用：掛載風險、身分實讀、版本偵測、自動終止、撤銷鏈都不豁免。誤填會靜默少彈一次，只寫確認過的套件名稱。

## containers.txt · 容器
一行 = 容器套件名稱 + 目標 APP 目錄範本；`{pkg}` 是目標應用套件名稱，可能出現多次，掃描時全部替換。格式見清單開頭註解。

    top.bienvenido.saas.i18n /sdcard/Android/obb/top.bienvenido.saas.i18n/scopedStorage/{pkg}/Android/data/{pkg}/

目錄可能在 `/sdcard/Android/obb/容器APP套件名稱/`、`/sdcard/Android/data/容器APP套件名稱/` 或 `/data/data/容器APP套件名稱/` 下的某個目錄。預設內建一行已知容器（即下列資料行），其他容器另起一行。
掃描按首個 `{pkg}` 切前後綴，列舉前綴下一層子目錄，目錄名即候選套件名稱，替換後目錄存在才列為次級。
任意使用者裝了容器就顯示，名稱取群組設定使用者下的應用名稱、讀不到則退回套件名稱。

## 優先順序
避坑 > 白名單靜默 > 一般提示。同一對套件同時進避坑與白名單，建置警告（不會失敗），執行時避坑優先。容器清單不參與。

## 提交
`assembleRelease` 會自動跑 `genDesync`（純 Gradle 行程內、Windows 可跑、不叫 shell）；`scripts/gen_desync.sh` 是同規則獨立工具。
只改 `config/*.txt`，別動 `res`、`Java`、`engine`、根 README，別改 `genDesync` 任務名稱與輸出目錄。
自檢：能解析、命中正確、建置日誌沒有重疊警告。
