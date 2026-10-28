# CP middleware DVR 交叉引用核验（2026-10-17）

## 要解决的未知

`Samples` 中的 `MMI_DVR_Command`、`DVR_SystemSts` 和 `DVRConfig` 字符串可能只是生成式服务标识；未知是另一个 CP middleware 是否真正订阅并处理 DVR 事件。

## 检查范围

在已经只读取得的 CP B 槽 middleware 文件上做定向字符串交叉引用，范围为 `IVIService`、`VehicleService`、`ConfigService`、`MixtureService`、`DFSService`、`ChimeService`、`GaugeService`、`Samples`、`TcServer`、`TelltaleService`、`WarnServer`、`sendmsg`、`sendmsgEx` 等 14 个文件。没有启动服务、修改文件或发送 SOME/IP/车辆控制请求。

`MMI_DVR_Command` 的引用汇总保存在 `middleware-dvr-xrefs-20261017.txt`；同一批字符串/订阅日志汇总在 `middleware-dvr-string-summary-20261017.txt`。对 `MixtureService`、`DFSService`、`ChimeService`、`TcServer`、`sendmsgEx` 的代表性引用上下文保存在 `middleware-dvr-xref-contexts-20261017.txt`。

## 结果

只有部分生成式 middleware 保存了 `MMI_DVR_Command` 的两处、`DVR_SystemSts` 的两处和 `DVRConfig` 的两处引用。上下文均为构造并插入服务/事件名称表的代码：可见 `InstanceIdentifier`、`vector<string>::emplace_back` 或同类表项写入，随后是固定数值 ID（例如 `0x8109`/`33033`）及通用注册路径。没有发现这些字面量指向具体 `GetNewSamples`、`SetReceiveHandler` 或 `HandleEventMMI_DVR_Command` 函数。

`Samples` 的 `MMI_DVR_Command` 两处引用位于同一类服务实例注册/清理表；`SubscribeEvent` 的引用是通用初始化日志。其 `ProjectionScreen`/`ProjectionScreenResp` 引用仍落在通用日志、错误和响应处理路径，未形成 `MMI_DVR_Command` 到 ProjectionScreen 或 EVS 的数据流。其它服务出现的 `SubscribeEvent` 错误字符串也没有与 DVR 类型建立独立回调引用。

因此本轮关闭“DVR 消费者藏在相邻 middleware 文件”的假设，但不把生成式类型注册等同于运行时订阅成功，也不据此断言 CP 或硬件不存在。当前 CP 侧仍没有画面/录像/回放的可执行消费者证据。

## 重查条件

只有 CP 槽位或 build ID 变化、出现匹配的未剥离 middleware/debug/event-binding 文件，或运行记录能把 33033/相关字段访问与实际回调联系起来时才重查；同一批二进制不再重复做字符串普查。
