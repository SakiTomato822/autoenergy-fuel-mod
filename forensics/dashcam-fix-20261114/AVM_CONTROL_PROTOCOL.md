# AVM 控制与 MCU 消息来源核对（2026-11-14）

当前结论：尚未实现原厂 DVR 或四路原图录制。本轮闭合控制消息的去向，排除将轮速/标定响应误当作外置 AVM 能力响应；没有发送未知控制、标定、泊车或恢复出厂命令。

## 视图控制链

未知：MMI_AVMViewModeSwitch_1 是否存在可用的原始流切换入口。

实际运行态 VHAL 初始化表 kCanSendSignals 为 390 条、每条 40 字节：property +0，libc++ string +8，modelID +32。仅发布筛选后的映射，不发布进程内存。

- 0x29700233 / MMI_AVMViewModeSwitch_1 → MCU model B4 → callback 0x43b5a4 → TX index20 → CAN 0x2AE，byte1 bits4..7。
- 0x297000de / MMI_AVMViewAngleSwitch → B1 → 0x43b574，byte0 bits3..5。
- X/Y/touch 分别为 0x29700246/247/248 → B9/BA/BB。EVS 外置触摸路径以复合属性 0x22700000 打包三项传给 VHAL。

纠正：最初把记录边界偏移 8 字节，错误关联为 model 0x11f；正确 0x11f 是 MMI_VehicleModeReq，未发送过该命令。不能把解析错误描述成运行时映射变化。CAN TX 描述符的标准 ID 在 +6 半字，不能把 +8 的扩展 ID 字段当成实际 ID。

这证明视图请求进入外置控制链，但没有恢复数值枚举与原始视频输出模式的对应关系，不据此猜值发送。重查条件：明确模式定义、成功同硬件响应、相关固件或版本变化。

## 直接 MCU AVM 消息

未知：日志中的 0F/0D 和其它 AVM 子命令是否能提供远端身份/能力。

运行态声明表位于 VHAL VA 0x6059a0，1305 条、stride184；get/read/write ID 分别 +176/+178/+180。已用 CarService DEX 的 toString 名称交叉核对：

| 属性 | 名称 | get/read/write |
| --- | --- | --- |
| 0x28700393 | INFO_ID_MCU_AVM_CALIB_ACTION | 0 / 0F0A / 0F0B |
| 0x28700399 | INFO_ID_MCU_AVM_CALIBRATION_STATUS | 0F04 / 0F06 / 0F05 |
| 0x287003ad | INFO_ID__AVM_WHEEL_SPEED | 0F0C / 0F0D / 0 |
| 0x287003ac | INFO_ID_AVM_CAMERA_STATUS_INFO | 1F22 / 1F25 / 1F23 |

0F0D sender 0x449660 取 0x45a608 返回值；状态写入链 0x45ad1e → 0x43860c → 0x438b7c，后者由 CAN 0x124 的缓存 0x204001a4 计算。它是轮速数据，不是模块身份或能力应答。

MCU com_avm_rx 表 0x48f848：04 指向 bx lr；05 修改本地布尔状态/超时；0B 按操作码进入本地状态及固定长度缓冲处理；0C 排队轮速上报。不能将标定入口当作只读探测。

摄像头状态属于 DiagnosticManager 的 1F 组（initManager 0x1881fc 构造、0x188238 注册）。MCU com_diag_rx 表 0x490254 中 1F22 查询项无 callback，1F23 写入回调 0x44ccb8 修改本地打包状态位；尚未追到外置摄像头故障的查询/返回链。仅有 VHAL 声明不能证明本车支持查询。表内声明项为 1..37；此前提取包含的第38项实际跨入下一表，已修正 JSON。

cantp 任务名也不能直接证明 UDS：任务 0x448b28 调用的是一般 CAN 初始化/周期处理。

重查条件：新固件、明确远端返回来源或协议实现。相同周期日志、零状态、相同属性常量不重复检查。

## 下一条 AVM 分支

未知：外置模块实际报告哪些故障/视图反馈。

VHAL parse_0x2AB_Data（0x32a870）解析 AVM_ViewModeResp、AVM_ViewModeRespExtend 和 AVM_CameraFltSts；正常数据分支在 0x32bd18 从输入小端 64 位值提取 bits10..13 作为故障字段。MCU 物理 RX 描述符 index21 确有 CAN 0x2AB、长度8。尚未观察该分支的真实载荷/新鲜度，不能由字段存在或缓存零值推断摄像头状态。

已知 debug CAN 0x6E4 在 VHAL 有解析器，但现有 MCU RX 描述符清单没有该 ID；历史12秒未调用结果继续引用，不重跑空观察。清单缺项不证明物理总线没有该帧。

下一步优先追 0x2AB 的接收有效性与视图反馈枚举，或取得同硬件成功车的真实输出协议。未重开360、未修改EVS、未改GPIO/I2C/车辆配置。

版本：VHAL SHA256 2d4bbf5ec01ef76103047481b19ba761bc41a24677508739dbd9ceac772c24a4；MCU 镜像 SHA256 8f046241a41965e23dc0bfcee57a2e17861425adae6c2ee0de71d49601e8b7cd。MCU 为匹配历史版本的 OTA 静态文件，静态代码不等于实时 MCU 状态证明。
