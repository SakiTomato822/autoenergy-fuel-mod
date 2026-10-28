# 车机 Download 旁路候选核验（2026-10-17）

## 要解决的未知

车机 `Download` 是否已经有可直接启动、移植或复用的原厂/第三方 DVR APK，能绕过当前桌面入口缺失。

## 检查

ADB 重连后只读列出 `/sdcard/Download` 下的 APK/APKS/XAPK/ZIP/JAR 文件名和大小；不安装、不启动、不修改文件。

## 结果

目录中有 EVCC、AVM、启动器、车辆只读探针和 MCU 补充包等文件，没有文件名指向 DVR、CardVR、Longhorn、RoadRover 或旧 Neusoft DVR 的 APK/安装包。结合已完成的包管理器检查，当前没有现成 APK 可作为“直接启动隐藏 DVR”的旁路材料。

## 重查条件

只有 Download 新增明确 DVR 包、系统安装包/版本变化，或获得与本车模块匹配的厂商 APK 时重查；不重复列出同一目录。
