# AutoEnergy Fuel UI 1.0.0（预发布）

- 版本：`1.0.0`，versionCode `24`
- 预期标签：`v1.0.0`
- GitHub Release：标记为 prerelease
- 基线：`v0.6.7-osm-preview`（`ffc47607a709c36c1f9ee8cc94ca260f2b9c821a`），保留 Git 祖先历史。

## 版本范围

本版本回归纯油耗与里程统计：不包含 OSM 在线地图、GPS 轨迹记录或座舱桌面 Demo。保留车辆油量百分比、续航、里程与平均油耗读取，50 L 标称油箱的剩余升数估算，累计平均油耗推算的分段曲线，以及后台采集和本地历史数据源。

升数由油量百分比按车主确认的 50 L 标称容量换算，是近似值，不代表独立油位测量。油耗曲线沿用基线采集器的小计累计平均油耗与小计里程（`avgFuelTrip2` / `trip2DistanceKm`）推算；原始读数精度、采样间隔及行程重置识别会影响短区间结果。

## DUMP 授权与读取边界

应用启动和诊断页只检查 `android.permission.DUMP` 当前是否已由系统授予。已授权时直接走只读 MCU 数据路径，不调用 `su`。未授权时先向用户说明并等待确认；确认后才会在后台限时尝试以当前 Android 用户和实际应用包名执行 `pm grant`，随后重新读取系统授权状态。

设备不提供所需 shell 能力、命令被拒绝或超时时，应用不会报告授权成功，会显示可复制的 ADB 指令。用户在连接设备的 ADB shell 中执行后，可返回诊断页重新检测。应用不会安装或配置 root，也不尝试授予系统签名级 `CAR_VENDOR_EXTENSION` 等权限。

`CAR_ENERGY` 与 `CAR_SPEED` 的普通运行时授权流程与 DUMP 分开：应用说明用途并取得用户确认后，才请求 Android 系统授权。DUMP 未授权时仍可尝试有限的 CarProperty 备用读取；受权限限制或缺失的值会标记为不可用，不把错误或占位零值伪装为成功读数。

## 包名、升级与数据

- Release：`com.lynk.autoenergyfuel`
- Debug：`com.lynk.autoenergyfuel.dhureadonly`
- versionCode：`24`

包名与既有版本保持一致，签名规则沿用原配置。覆盖安装需匹配相同包名及签名；CI Debug 包不能覆盖 Release 包。升级不改变 `Download/AutoEnergyData/history-*.json` 数据源，既有曲线历史和采集基线继续使用。卸载后共享文件通常仍在，但重装后需要手动选择文件并重新授予 DUMP。

## 验证状态

这是预发布版本。[GitHub Actions 云端构建](https://github.com/SakiTomato822/autoenergy-fuel-mod/actions/runs/38502257220)已通过编译和 34 项单元测试（0 失败、0 错误、0 跳过），并核对以下发布条件：

- APK 包名为 `com.lynk.autoenergyfuel.dhureadonly`，versionCode `24`，versionName `1.0.0`；
- APK 不声明精确定位、粗略定位或定位前台服务权限；
- 签名证书 SHA-256 为 `91f7b9d6496414a0fab0a87c8fcc60f929df0dd9fa4d4a9a52215869178bab8d`，与 `0.6.7` 预发布 APK 一致，可覆盖对应 Debug 包；
- Release 附件直接取自通过上述检查的 GitHub Actions 产物。

`0.6.7` 的历史模拟器或实车记录仅适用于当时版本，不构成 `1.0.0` 实车验证。1.0.0 的授权弹窗、车型数据完整性和长时间采集稳定性仍需在目标车机上确认。
