# 安装包约定

- 当前版本（最新）只存在于 `D:\PhysCard\app\build\outputs\apk\release\PhysCard.apk`，文件名不带版本号。
- 构建前，旧的 `PhysCard.apk` 会自动移入本目录，并按它自身的版本号重命名为 `PhysCard-v<版本号>.apk`。
- 本目录只放**旧版本**，最新版不归档。
- 同一版本已有归档时不会覆盖。
- debug 包不参与这套流程。

## 用法

```powershell
# 一键：归档旧包 → 构建 → 对齐签名 → 产出 PhysCard.apk
powershell -ExecutionPolicy Bypass -File "D:\PhysCard\version history\build-release.ps1"

# 只归档旧的 release 包（构建前手动调用）
powershell -ExecutionPolicy Bypass -File "D:\PhysCard\version history\archive-apk.ps1"
```

签名默认使用本机 Android debug keystore；正式发布可用参数传入自己的 keystore：

```powershell
powershell -ExecutionPolicy Bypass -File "D:\PhysCard\version history\build-release.ps1" `
  -Keystore D:\keys\release.jks -KeyAlias physcard -StorePass ****** -KeyPass ******
```
