# 🐾 嗷呜工具箱 (AwooBox)

![Version](https://img.shields.io/badge/version-1.1.0-brightgreen?style=flat-square)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)

**面向小米 / HyperOS 的 Android 实用工具箱，嗷呜！**

修复 adbd 属性标签，管理手机管家的 GMS 自动关闭任务。嗷呜工具箱使用 Kotlin、Jetpack Compose 与 Material 3 Expressive 构建，在设置中集中管理 Shizuku、Root 权限与应用主题。

当前项目版本：**1.1.0**。

## ✨ 核心特性

- **修复 adbd 属性标签**：检查 `adbd_config_prop` 和 `shell_prop` 的 SELinux 标签、当前 TCP 端口与 Root 授权状态；支持修复 adbd 属性标签，并可选择同时修复 `shell_prop`。
- **拦截 GMS 自动关闭**：读取小米手机管家 `CleanerReceiver` 组件状态及其 AlarmManager 任务，展示执行身份、触发时间、调度窗口与重复周期；支持停用接收器和恢复默认状态。
- **权限集中管理**：通过 **设置 → 权限与服务** 检查 Shizuku 和 Root 授权；提供 Shizuku 授权请求及 Shizuku、KernelSU / Magisk 管理器入口。
- **主题自定义**：支持跟随系统、浅色与深色模式，动态取色，以及 Tonal Spot、Vibrant、Expressive、Neutral 调色板；关闭动态取色后，可选择嗷呜绿等五种主题色。
- **页面导航**：首页的“功能”和“设置”支持左右滑动切换；主题设置中可切换系统预测性返回过渡。

## 📥 下载与安装

发布的 APK 可从本仓库的 [Releases](../../releases) 页面获取；尚未发布时，可按下方步骤自行构建。

**手机直接安装**：将 APK 下载到手机，允许对应浏览器或文件管理器安装应用后，打开 APK 完成安装。

**ADB 手动安装**：

1. 在手机上开启开发者选项和 USB 调试或无线调试，连接电脑并完成调试授权。
2. 使用电脑端 ADB 安装，将文件名替换为实际下载或构建的 APK：

   ```bash
   adb -s <设备序列号> install -r AwooBox-v1.1.0-debug.apk
   ```

基础安装要求为 **Android 8.0 及以上**。具体系统功能依赖设备的 Root、Shizuku 运行身份及 HyperOS 实现，安装成功不代表所有功能均可用。

## 📂 使用指南

### 修复 adbd 属性标签

1. 在 **设置 → 权限与服务 → Root 权限** 检查授权状态。
2. 打开首页的 **修复 adbd 属性标签**，查看当前标签与检测结果。
3. 根据需要选择是否同时修复 `shell_prop`，点击 **修复属性标签**。
4. 修复后，在 Shizuku / Stellar 中自行重新启用 TCP 模式并确认启动结果。

此功能只修复属性文件的 SELinux 标签，**不会设置 TCP 端口或重启 adbd**。它针对标签异常造成的问题，不能解决所有 ADB / TCP 启动故障；属性文件重建或其他模块再次修改标签后，可能需要重新检查。

### 拦截 GMS 自动关闭

1. 启动 Shizuku，在 **设置 → 权限与服务 → Shizuku 权限** 为嗷呜工具箱授权。
2. 打开首页的 **拦截 GMS 自动关闭**，查看接收器状态与自动任务。
3. 点击 **停用自动关闭任务**，阅读影响说明并确认。
4. 如需恢复，点击 **恢复接收器默认状态** 并确认。

管理目标为主用户（用户 `0`）下的 `com.miui.securitycenter/.receiver.CleanerReceiver`。停用接收器后，相关闹钟可能仍显示为已登记，应结合组件状态判断是否已拦截。

该接收器还承担优化管理动画云控与快捷回复默认名单的刷新，停用会同时影响这些任务；不会停用整个手机管家。此功能不负责重新启用已被关闭的 Google 服务。系统或手机管家更新后，建议重新检查状态。

## 🔐 权限说明

### Shizuku 权限

目前用于查询和管理手机管家的 GMS 自动关闭任务，需要 **Shizuku 11 或更高版本**。请先在 Shizuku 中启动服务，再到嗷呜工具箱的 Shizuku 权限页面请求授权；拒绝授权后，也可到 Shizuku 的应用授权管理中手动允许。

**Shizuku 授权与 Root 授权相互独立。** Shizuku 以 ADB / Shell 身份运行时可用于状态查询，但部分 HyperOS 版本会拒绝修改系统手机管家组件。若出现 `Shell cannot change component state`，需要以 Root 身份运行 Shizuku；仅授予嗷呜工具箱 Root 权限，不会改变 Shizuku 的执行身份。

### Root 权限

目前用于读取和修复 adbd 属性标签，通过设备已有的 KernelSU / Magisk 等管理器获取超级用户权限。

**KernelSU 可能不会弹出授权窗口**，请在管理器的超级用户列表中手动允许“嗷呜工具箱”，再返回 **Root 权限** 页面重新检测。未获得授权时，应用会提示处理方式；修复前会再次检查权限。

## 🛠️ 本地构建

当前工程使用 Android Gradle Plugin **9.5.1**、Gradle **9.6.0**，`compileSdk` / `targetSdk` 为 **37**，Gradle Daemon JVM 配置为 **JDK 25**。Kotlin / Java 编译目标为 JVM 17。

1. 获取源码，用支持当前 AGP 的 Android Studio 打开工程。
2. 准备 JDK 25、Android SDK Platform 37，并在本机配置 SDK 路径；Android Studio 可生成 `local.properties`，该文件不应提交。
3. 在项目根目录运行以下 PowerShell 命令：

   ```powershell
   .\gradlew.bat :app:assembleDebug :app:lintDebug
   ```

Debug APK 输出至：

```text
app/build/outputs/apk/debug/AwooBox-v1.1.0-debug.apk
```

当前仓库提供 Windows 启动入口 `gradlew.bat`，未提供 Unix `gradlew` 脚本。其他系统可使用 Android Studio 的 Gradle 任务，或安装匹配版本的 Gradle。

Release 构建启用了代码与资源压缩，工程尚未配置发布签名；发布前需自行配置签名。签名密钥、本机配置、构建产物及调试截图等已纳入 `.gitignore`。

本地截图、录屏、UI 转储和临时补丁统一存放在 `tmp/` 目录，不提交到仓库。

## 💬 交流与反馈

使用时遇到问题或有功能建议，欢迎提交至本仓库的 [Issues](../../issues)。反馈时请附上设备型号、系统版本、应用版本、所用功能，以及 Shizuku 的运行身份或 Root 授权状态。

> 加入 **嗷呜系列官方 QQ 交流群**：`暂无`

嗷呜穿戴系列应用：[🐾 嗷呜计算器](https://github.com/NickWoluff/WearCalculator) · [🐾 嗷呜文件](https://github.com/NickWoluff/WearFiles)

© 2026 Nick Woluff. All Rights Reserved.
