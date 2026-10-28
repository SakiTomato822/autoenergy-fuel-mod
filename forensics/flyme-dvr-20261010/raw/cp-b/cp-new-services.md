# CP `MixtureService` / `DFSService` 静态追踪

日期：2026-10-12。只检查本地 CP B 槽已提取文件和既有 ELF；没有现场调用、联网、写属性或自行提取新页。本记录不把部署名或字符串本身当作数据流证据。

## 文件身份与分析边界

路径均相对 `raw/cp-b/files/usr/local/bin/cluster-e04/middleware/`：

| 文件 | 大小 | SHA-256 | ELF |
|---|---:|---|---|
| `MixtureService` | 1,731,224 B | `4f9a63dd6a9b9adcffdae1a56cf635da524878538878eb32439e128ea6093288` | ELF64 little-endian AArch64，entry `0x18100`，build ID `f39f0151801c291f6e6aea3b0084c7fa46512596` |
| `DFSService` | 1,883,976 B | `524560ca6c43cd36884c61902c5272f2ac66956e56c78c5fb591265588721873` | ELF64 little-endian AArch64，entry `0x31980`，build ID `a2d5e0b663f56f8f01f10cbb3bb249a0f259cb23` |

两者有 `.dynsym`、代码、只读数据及 unwind 数据，但没有 `.symtab`、`.debug_*` 或 `.gnu_debugdata`；业务处理器不能通过本地符号名恢复。它们由 B 槽 `start.sh` 启动（DFSService 行 36，MixtureService 行 38）。本地 HMI partial ELF 属于另一个文件/构建身份，不能拿其 DWARF 名字补齐这两个服务。

## MixtureService：DVRConfig 发布链未闭合

`DVRConfig` 字符串的代码引用在 `0x5a600`、`0x5c154`。两处都位于较大的服务初始化代码区域（FDE 起点 `0x531a0`，长度 `0x132ac`）：引用后调用字符串构造及内部通用容器/键查找 helper（`0x4c094`、`0x4f108`）。附近代码在组装、登记接口/事件项；没有看到 DVR 配置载荷的赋值、字段读取或可确认的 `SkeletonEvent::Send` 调用。

该 ELF 有一组 `SkeletonEvent::Send` 通用调用点（例如 `0x449f0`、`0x44fe8`、`0x455e0` 等），但在 stripped 文件中没有可把任一调用点与 `DVRConfig` 对象或对应事件缓冲区关联的类型、符号或数据引用。故现有证据只能支持“服务初始化了名为 DVRConfig 的事件接口”；不能支持“该服务构造或发布 DVRConfig 内容”，也不能推出其中值怎样来自 MCU/车辆信号。

## DFSService：ProjectionScreen / CurView 有接口痕迹，无 DVR 消费链

### screen/entry 相关证据

DFS 的只读 `protodesc_cold` 区域在 VA `0x17f9df` 含 protobuf `ProjectionScreen` descriptor，字段为 `screen` 和 `entry`，二者 wire 类型均为 `int32`。代码中可见日志文案 `send project screen ---> val:`（rodata `0x160e08`）和 ` send cp screen view ---> entry: `（`0x160e58`）；对 `SendProjectionScreen` 的引用在 `0x3515c`、`0x35180`，对 `SendCPScreenView` 的引用在 `0x353cc`、`0x353ec`、`0x356f8`、`0xe4cd4`。这些具体引用所在窗口调用 `__strncat_chk` / `__strcat_chk` 组成日志文本并更新局部计数/状态；从该段指令不能证明后续发送、显示切换或 `screen`/`entry` 字段的业务效果。

`ProjectionScreen` 与 `ClusterNotifyCurView` 名称另在 `0x7abcc`、`0x7ca98`、`0xa52dc`、`0xa5340` 等代码位置构造/查找；可见上下文呈现服务方法/接口表初始化。它们不能单凭名称解释成 DVR 触发或当前画面消费者。ELF 里有 `ProxyMethod::Send` 的多个通用调用（首批在 `0x1284b4`、`0x128950`、`0x128cc8` 等），但没有恢复出这些调用分别绑定到哪个部署方法、使用何种消息字段；唯一 `ELinkNotifier::NotifyDirect` 调用点 `0x716a8` 位于通用 transport 代码，也未与 DVR 事件或 ProjectionScreen 字段连上。

### DVR 事件引用

DFS 的 `MMI_DVR_Command` 名称引用为 `0x9ae30`、`0x9dc34`，`DVRConfig` 为 `0xa03f4`、`0xa2158`；附近是同一大初始化 FDE（起点 `0x9857c`，长度 `0x14634`）的事件/接口名称及容器登记代码。对这些引用未见 `ST_MMI_DVR_Command` 字段偏移访问、`GetNewSamples`/接收回调、protobuf 字段转发或投向 ProjectionScreen/CurView 的控制路径。

本地 `cm_config.json` 的确把 `VehicleService` 的 `MMI_DVR_Command` 部署为 EventId `33033`、把 `MixtureService` 的 `DVRConfig` 部署为 EventId `32854`；DFS/IVI 的 ProjectionScreen 与 CurView 也列作方法部署。但部署配置只是路由元数据，不能填补上述代码链缺口。

## 结论、缺口和复查条件

在这两个已提取服务文件里，没有建立以下任一真实链路：

1. MCU CAN0 `0x2A0` 到 `ST_MMI_DVR_Command` 的解码、字段赋值或消费；
2. `MMI_DVR_Command` 或 `DVRConfig` 到 DFS `ProjectionScreen` / `ClusterNotifyCurView` 的调用、参数映射或发布；
3. ProjectionScreen 的 `screen`/`entry` 字段到有效画面切换操作的调用链；
4. MixtureService 对 DVRConfig 实际载荷的生产与发布。

因此这批文件不能裁定 `Enter`、`LVDSModeConfig` 的 0/1 含义，也不能确认有效 DVR 画面切换消费者。后续只有拿到匹配 build ID 的未剥离/调试构建、可映射 method/event ID 到处理器与字段的生成绑定表，或能定位上述通用 ProxyMethod 调用与具体 ProjectionScreen/CurView 数据对象的本车代码，才值得重开这一支。当前停止扩大泛搜。
