# CP 与 MCU DVR 语义连线核查

日期：2026-10-12。范围限于本地 CP B 槽提取件、`cm_config.json`、`hmi-partial.elf`、已有 MCU 补充固件静态结果；没有读车辆、联网或改动控制状态。

## 结论

没有恢复出 MCU CAN0 标准 ID `0x2A0` 与 CP `ST_MMI_DVR_Command` / EventId `33033` 之间的真实解码或消费链。MCU 固件支持的结论仍止于：模型 `1A8` 的输入最低位写入发送缓冲 `204004d7` byte 1 bit 3，静态发送路径将 index 33 的 8 字节缓冲放到 CAN0 ID `0x2A0`。现有 CP 证据没有把该位接到 `MMI_DVREnter`、`MMI_DVR_LVDSModeConfig` 或任何画面切换处理器。

因此，`Enter` 与 `LVDSModeConfig` 的 0/1 取值语义未恢复；不能据其成员名或 MCU 单比特回调推断进入/退出画面。模型 `1A6` 可能对应 CP 结构 offset 6 的说法仍是待证假设。

## CP 事件与结构证据

B 槽 `/usr/local/bin/cluster-e04/etc/cm_config.json` 将 `/ServiceInterface/VehicleService/Events/MMI_DVR_Command` 部署为 `EventId: 33033`，列在 `VehicleService` 的 `EventDefaultGroup`（group id 1）中。`ST_MMI_DVR_Command` 的 DWARF 条目（`handoff/evidence/cluster_dvr_dwarf.json`）只有布局：18 字节；offset 6 是 `MMI_DVR_LVDSModeConfig: uint8_t`，offset 8 是 `MMI_DVREnter: uint8_t`。DWARF 未给出枚举、有效值或字段访问代码。

名称相近的 `DVRConfig` 是另一服务接口：`/ServiceInterface/MixtureService/Events/DVRConfig`，EventId 32854。整数 EventId 33033 也被 MixtureService 的 `FCW` 使用，故单独的 `33033` 不能充当跨服务数据流证据。该分支不把已知 `DVRConfig` 字节日志消费者外推到 MMI 命令。

## 可执行文件交叉引用

CP 的 `VehicleService`、`IVIService`、`ConfigService` 均为 AArch64 ELF；本地副本没有可用的完整 DWARF/函数符号。三者包含 `MMI_DVR_Command` 字符串，并能在代码中找到 EventId `0x8109`（十进制 33033），但引用位于服务/事件部署名称和标识的初始化代码。静态反汇编表现为字符串构造、`InstanceIdentifier` 条目写入及 `std::_Rb_tree` 操作；它能确认部署元数据存在，不能确认谁订阅、谁解析字段或怎样控制画面。

- `VehicleService`：名称 xref 位于 `0x89dbc`、`0x8ca40`；`0x8109` 写入位于 `0x8ca5c`、`0x927d0`。此处 `DVR_SystemSts`、`DVRConfig` 也作为部署名出现。
- `IVIService`：名称 xref 位于 `0x32240`、`0x34f2c`；`0x8109` 写入位于 `0x34f48`、`0x3a3bc`。
- `ConfigService`：名称 xref 位于 `0x1c33c`、`0x1facc`；`0x8109` 写入位于 `0x1fae8`、`0x265a0`。

对这些引用没有恢复出 `0x2A0` 的 CAN 收发或信号解包调用链。`VehicleService` 的 `0x2a0` 立即数命中在 `0xae548`；邻近代码把该数写入栈上局部量后传给字符串构造相关 helper，周围还有 `0x2a9`、`0x1f9` 等常数。不能将它解释为 CAN 标识。其余可见的 `#0x2a0` 多为对象/栈偏移。

## HMI 可见消费者边界

`hmi-partial.elf` 来自 CP B 槽 `weston-simple-egl`，保留 `.text`、`.rodata`、符号和链接相关节；manifest 明确标记为 partial，未包含全部 debug 页，不能把它当完整原件。

保留的符号表中，DVR 相关处理器是 `ClusterServiceHandler::HandleEventDVRConfig()` 及其 `uint8_t` `GetNewSamples` 实例。命令事件 `EventAdapter<ST_MMI_DVR_Command>` 只有 `UnsetReceiveHandler()` 实例；没有相应 `Subscribe()`、`SetReceiveHandler()`、`GetNewSamples()` 或 `HandleEventMMI_DVR_Command()` 符号。保留的 `ClusterServiceHandler::SubscribeEvent()` 调用点中也未见 DVR 命令订阅。由此可说：在此 HMI 部分镜像可见的执行代码中，没有 DVR 命令消费或画面切换处理器。这个结论只覆盖该二进制的已提取内容，不代表 CP 或整车其他程序。

已有 B7 对 `evsrenderengine` Binder 跳表的分析也未恢复 DVR 画面进入分支；本分支没有重做该分析或扩大为整车结论。

## 停止条件与复查材料

当前本地证据不能解开以下缺口：

1. 生成 `VehicleService` 事件的实际信号/序列化表，以及 `33033` 的发布者、订阅者和字段读写函数；
2. MCU CAN0 `0x2A0` byte 1 bit 3 到 `MMI_DVR_Command` 18 字节字段的转换规则；
3. `MMI_DVREnter`、`MMI_DVR_LVDSModeConfig` 的合法取值与实际 UI/EVS/显示服务消费点。

需要新的源级材料才能有条件重查：CP middleware 的未剥离构建/匹配版本 DWARF，或明示信号到 ARA event 的映射表与实际 UI 消费者二进制。若判断 HMI 缺失 debug 页可能包含关键线索，应先向主代理申请读取原 64 MB HMI 的新增页；本分支没有自行提取。收到上述材料或出现版本变化后再开分支，否则停止当前核查。
