# HMI `HandleEventDVRConfig` 定向审查（2026-10-17）

## 要解决的未知

HMI 中唯一具体的 `ClusterServiceHandler::HandleEventDVRConfig()` 是否把 DVR 配置事件转成画面、窗口或 EVS 动作，还是仅记录配置。

## 检查

使用已读取的 CP B 槽 HMI 原件和保存的 `.text` 反汇编，定点查看 `0x1f3fd4` 及其唯一取样辅助函数 `0x1f3d70`。这是静态只读检查，不运行 HMI、不发送事件、不写 CP。

## 结果

`HandleEventDVRConfig` 从对象偏移 `+0x8a8` 的事件适配器调用 `GetNewSamples<unsigned char>`，随后只检查结果对象并返回。辅助函数遍历样本，每个字节进入 `MiddwareUtils::KSyslog::operator<<(unsigned char)`/`LogInfo`；错误路径也只是记录通信/取样错误。该函数及相邻取样代码没有 `ProjectionScreen`、EVS、窗口、视频或媒体调用，也没有向 DVR 命令事件转发的分支。

因此 `DVRConfig` 在该 HMI 原件中已闭合为取样加日志路径，不能作为实际 DVR 画面入口。它仍不证明其它未取文件或硬件模块不存在。

## 重查条件

仅当 CP 槽位/build 变化、出现匹配的未剥离实现，或运行记录证明该配置样本随后触发具体 UI/媒体调用时重查；相同 `HandleEventDVRConfig` 反汇编不再重复读取。
