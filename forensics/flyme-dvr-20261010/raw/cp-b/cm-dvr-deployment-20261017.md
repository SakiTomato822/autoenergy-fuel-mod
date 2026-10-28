# CP `cm_config` DVR 部署核验（2026-10-17）

## 要解决的未知

CP middleware 中的 DVR 类型是否只是二进制残留，还是在 SOME/IP/IPC 配置中实际部署；同时确认部署声明是否已经等于客户端消费。

## 检查

只读解析已取得的 CP B 槽 `/usr/local/bin/cluster-e04/etc/cm_config.json`，提取服务事件、方法部署和默认事件组。没有启动服务、修改配置或发 SOME/IP 请求。原始定向提取见 `cm-dvr-deployment-extract-20261017.txt`。

## 结果

配置实际声明了：

* VehicleService：`DVR_SystemStsDeployment`（EventId 32921）和 `MMI_DVR_CommandDeployment`（EventId 33033），二者都在 `EventDefaultGroup`；
* MixtureService：`DVRConfigDeployment`（EventId 32854），在其 `EventDefaultGroup`；
* IVIService：`ProjectionScreenDeployment` 方法（MethodId 4）及 `ProjectionScreenRespDeployment`；
* DFSService：`ProjectionScreenDeployment` 方法（MethodId 3）及 `ProjectionScreenRespDeployment`。

这确认 CP 的服务描述层为 DVR 事件和投影方法预留了实际部署关系，比单纯字符串或图标更强。但 `cm_config` 只描述服务端点和事件组，不证明某个进程已成功订阅、事件曾经到达、投影方法被 DVR 回调调用，或存在录像/回放页面。结合 B25/B26 的代码结果，当前仍没有 MMI_DVR 到 ProjectionScreen/EVS 的客户端数据流。

## 重查条件

仅当槽位/build 变化、部署被改动，或运行记录能证明 33033/32854 事件的订阅与回调时重查；相同 `cm_config` 不重复解析。
