# DVR D8 路由与旧端语义复核（Luna Xhigh）

日期：2026-09-23。只读离线分析；没有写 VHAL 属性、发送车机命令或操作车辆。复核对象为 OTA 2.5.0 中的 `android.hardware.automotive.vehicle@2.0-service`，不是早期 Flyme VHAL 探针，也不是旧 Neusoft 固件。

## 新增结论

`0x297000D8` → 发送声明表项 `0x1A8` 的关系继续到达当前 VHAL 的 `McuCanMessage` 编码器：`ModuleDataManager` 从属性发送声明的 `row + 0x20` 取出 `0x1A8`，放入 `CanSignalModel` 的 16 位 ID 字段；后续 builder 将这个字段按高字节、低字节写进发给 MCU 的消息 body。因此此处可证实 `0x1A8` 在 Android→MCU 串行消息中的编码为 `01 A8`。

这还不能证明 MCU 将它原样发成外部 CAN arbitration ID，也不能解出 D8 的允许值、property value 到 payload 的具体位编码、对端 ACK 或录像动作。线级总线/ECU语义仍未知。

## 可复核的新版路径

- 表项：[`vhal_send_tables_reconstructed.json`](vhal_evs_diff/vhal_send_tables_reconstructed.json) 的 `new_250.rows[136]` 是 property `0x297000D8`、`can_id=0x1A8`，行步长 `0x28`，记录的 CAN ID 字段偏移为 `+0x20`。Framework 常量名为 `INFO_ID_MMI_DVRENTER`（`apk_out/android_car_framework_jadx/sources/android/hardware/automotive/vehicle/V2_0/VehicleProperty.java:793`）。JSON 的 `is_dvr=false` 只是提取脚本将标签范围限在 `0x297000CA..0x297000D3`，不代表 D8 非 DVR。
- 属性转换：OTA ELF 的 `ModuleDataManager::convertPropValToCanSignal` 位于 VA `0x2CEC94`。它调用 `VehicleDataBuilder::encapsulateCANData` (`0x2F380C`) 和 `getSendCanConfigDeclarationByPropId` (`0x2F3F0C`)。查表函数逐项比较属性 ID，表项步长为 `0x28`。在 `0x2CF344–0x2CF34C`，转换器从命中表项 `+0x20` 读取 32 位字段，再以 `strh` 存入局部 `CanSignalModel` 的 `+0xA`；D8 命中值为 `0x1A8`。模型向量记录步长也是 `0x28`。属性的数据字节由 `encapsulateCANData` 生成；现有声明未提供足以确认 D8 数据位、取值表或缩放的 schema。
- MCU/CAN 管理：`VehicleEmulator::doSetValueToCan` 的 D8 所属属性分支把 route 参数置零，之后将转换所得向量交给 `McuManager::sendCanMessage` (`0x1894E8`)。该包装函数最终跳到 `CanManager::sendCanMessage` (`0x1D8BC0`)。后者把 route byte 放入 builder 的 `+0x28`，把模型数放入 `+0x29`，复制每个 40-byte `CanSignalModel`，调用 `Message::setCmdCtrl(3)`，再进入 `McuManager::sendMessage(unique_ptr<Message>&&)` (`0x1895E4`)。这里的 route byte 是代码字段和值 0；二进制没有足够命名信息证明其协议名就是“CAN 通道”。
- MCU 消息 body：`McuCanMessage::Builder::build` (`0x1DA5C0`) 遍历模型。body 由 route byte、模型数，以及每条记录的“payload 长度 + 2”、16 位 ID、payload 字节组成。ID 字段 `[record+0xA]` 先右移 8 位输出高字节，再输出低字节，因此 `0x1A8` 编码为 `01 A8`。`McuCanMessage` 构造器 (`0x1DA7E4`) 设置 main/sub command 为 `0x13/0x01`；当前 `CanManager` 发送路径将 `cmdCtrl` 设为 3。
- 串行发送边界：`McuManager::sendMessage` 读取 Message 的 cmdCtrl、body length、main/sub command、sn/priority 与 body，构造以 `0xAA` 起始、带长度字段和 check code 的字节向量，并调用 `McuSerialComm::write` (`0x18EA04`)。这是 ELF 中可见的 Android→MCU 调用终点。外部 CAN 收发、MCU 是否转发/改写 ID 和车辆总线上的实际帧，不能从此调用单独推出。

以上地址来自已提取 OTA ELF 的 ARM64 指令；已有函数范围与调用摘要见 [`vhal_send_path_diff.json`](vhal_evs_diff/vhal_send_path_diff.json)。这些是编译态路径，不等于已在车上触发 D8。实车记录中的 `0x2B1` 样本为全零，不能证明 enter 转换发生。

## 与旧 Neusoft 的可证语义关系

旧 Neusoft `DVRLogin` 图标逻辑只在 `sx_dvr_enter` 当前为 0 时写 1（`apk_out/old_DVRLogin_dex_jadx/sources/com/neusoft/dvrlogin/MainActivity.java:93-99`）。旧 DVR Activity 启动时将 0/1 改为 2；若为 5 则改为 4（`apk_out/old_DVR_dex_jadx/sources/com/neusoft/cardvr/view/MainActivity.java:313-322`）。旧 `StateManager.a(int, byte[])` 在回调类型 `i==2` 时处理 `DVR_ControlRequest`：`bArr[1]==1` 且 setting 非零才安排进入；值 3 触发退出（`apk_out/old_DVR_dex_jadx/sources/com/neusoft/cardvr/e/a.java:82-111`）。这是旧 app 的本地 setting 与 RPC callback 协同状态机证据。

当前 Flyme 2.5.0 中，同名 inbound property `0x297000D3 / DVR_ControlRequest` 的已重建 CAN 来源是 `0x2B1`（`vhal_send_path_diff.md` 的 D3 段；完整表项见 JSON）。新旧两边都出现名称 `DVR_ControlRequest`，只能支持名称层面的相似性。旧 `i==2` callback 类型、`bArr[1]` 的 1/3、setting 值 1/2/4/5，与新 D3 的 property payload、CAN 字段以及 D8 enter 值之间，没有已恢复的编码表或交叉引用能证明一一对应。尤其不能把旧 subtype 1 当作新 D8 的写入值。

新 CP 调试结构另有 `ST_MMI_DVR_Command::MMI_DVREnter`（`cluster_dvr_dwarf.json`）；它与 Android VHAL 的 D8 表项是不同接口证据。当前没有数据流证明 D8 转成该 CP 字段，也没有证据把旧 Neusoft RPC 或 `sx_dvr_enter` 映射到任一新结构字段。

## 后续最有区分力的证据

优先恢复 D8 的信号声明/位宽和值域，以及 MCU 接收 `main/sub=0x13/0x01` 后是否把模型 ID 转成外部 CAN 帧；再以被动总线记录或离车台架样本验证 `0x1A8` 和对端反馈。旧端要做语义匹配，则需找回 RPC `DVR_ControlRequest` 的生产者/协议结构，并证明其字段到当前 D8/D3 的变换。当前证据不支持试发命令。
