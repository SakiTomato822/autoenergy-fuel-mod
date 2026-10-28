# Flyme Auto 2.5.0 DVR 接口静态复核

日期：2026-09-23。范围：离线分析 OTA 2.5.0 VHAL、CP 调试符号及随包 MCU 更新文件。只读；没有写 VHAL 属性或发送车机/车辆命令。

## 已证实

- 0x297000D8 是 framework 命名的 INFO_ID_MMI_DVRENTER（VehicleProperty.java:793）。按 VehicleProperty 位域拆分，它是 VENDOR group (0x20000000)、CAN area (0x09000000)、BYTES type (0x00700000)、unique ID 0xD8。OTA VHAL 注册/config 区域在文件偏移 0xBCE0 有 D8 00 70 29 03 00 00 00；该属性后的 access 值 3 对应 READ_WRITE。这里能恢复访问类型，但不能据此恢复 VehiclePropConfig 的完整 area/configArray 或 payload 值域。
- 新版发送表 vhal_evs_diff/vhal_send_tables_reconstructed.json 的 new_250.rows[136] 为 D8→0x1A8；发送项步长 0x28，ID 字段在 +0x20。D8 的 ModuleDataManager::convertPropValToCanSignal（VA 0x2CEC94）命中该项，读取 ID 并存入 CanSignalModel。这证实 Android VHAL 静态发送表把该 property 关联到 0x1A8。
- VehicleDataBuilder::encapsulateCANData（0x2F380C）读取 VehiclePropValue 的 bytes 长度及数据指针，将输入 payload 原样放入 signal model；该函数没有 D8 专属分支、枚举转换、位打包、缩放或有效值表。空 bytes 会生成空 payload。当前恢复到的 VHAL 静态代码不足以确定 D8 合法长度或允许字节值。
- McuCanMessage::Builder::build（0x1DA5C0）对每条模型记录写入“payload 长度 + 2”、两字节 ID（先高后低）和原 payload。故此 Android→MCU body 中 D8 条目编码的 ID 字节为 01 A8，之后跟原始 D8 bytes。CanManager::sendCanMessage 设置 route byte 为 0、cmdCtrl=3；McuCanMessage 构造器设置 main/sub command 0x13/0x01；McuManager 最终交给 McuSerialComm::write。这些是编译态 Android→MCU 路径，不证明 MCU 会将 ID 0x1A8 原样发到车辆 CAN，也不证明它接受该 payload。
- 0x297000D7 的 framework 名称为 INFO_ID_MMI_DVR_LVDS_MODE_CONFIG，同样是 CAN/BYTES/access 3；其注册原始项在 VHAL 文件偏移 0xA2F8。但 D7 没有出现在当前完整 outbound/send 表，因而尚无 Android→MCU 写路由证据。CP DWARF 的 ST_MMI_DVR_Command 中 MMI_DVR_LVDSModeConfig 是 offset 6 的 uint8、MMI_DVREnter 是 offset 8 的 uint8；这些只是 CP 结构字段，尚未找到从 D7/D8 到 CP 结构的调用/数据流。现有 EVS/AVM 视频链也没有证明 D8 会打开 DVR UI 或切换 DVR 视频。
- 接收侧仍只有名称/表项级联系：0x297000D3 / DVR_ControlRequest 由 0x2B1 接收表映射。历史实车被动样本的 0x2B1 为 DLC 8 全零，未观察到与 D8 写入相关的回执或状态变化。CP ST_DVR_SystemSts 中另有同名 DVR_ControlRequest 字段，也不能据此建立 0x2B1 字节到 D8 命令的语义转换。

## MCU 固件检索范围与负结果

对随 OTA 的 3C5525_MB_UPDATE.bin（628,016 bytes，头 A5 5A A5 5A，非 ELF）做了只读 ASCII 搜索，关键词包括 DVR、LVDS、DVRENTER、0x2B1、0x1A8、recording、SD card；未找到可归属 DVR/LVDS 的字符串、枚举或协议表。文件中能见一般 CAN 初始化/TX 日志字样，但无法确定其指向该接口。当前没有该文件的调试符号、DBC、协议映射或已识别的 MCU 容器/指令集。因此未能从固件恢复 D8 合法值、0x1A8 bit layout、MCU 转发行为或 0x2B1 回执规则。原始镜像中孤立的数值字节匹配不能作为协议证据，也没有继续对未知格式镜像做猜测性反汇编。

## 尚未证实

1. D8 的合法 payload 长度、各字节意义、值域，以及是否能单独触发进入 DVR。
2. 0x1A8 在 MCU 下游是否为 CAN arbitration ID，及信号 bit/byte layout。
3. 0x2B1 与 DVR_ControlRequest 是否为 D8 的 ACK/状态回报；当前无可证实应答转换。
4. D7 是否有其他服务侧写入路径，以及 DVR 图像是否由 LVDS 路径或 AVM/EVS 路径显示。
5. 新版接口与旧 Neusoft sx_dvr_enter、旧 DVR_ControlRequest RPC callback 的字段和值是否对应。名称相似不足以建立语义映射；旧端状态值不可移植为 D8 值。

## 最短下一步

最有价值的静态靶点是获取与该 MCU 固件精确匹配的 DBC/信号定义或厂商调试符号/协议文档，先解开 0x1A8 的 payload schema 与 0x2B1 的回执字段；若没有这些材料，则使用已知正常工作的原厂 DVR UI 操作做一次被动同步抓取，记录 Vehicle HAL set payload、Android→MCU UART 帧和 MCU 下游总线/回执，不注入或重放任何命令。若目标是确认“仅 UI 缺失还是 DVR 硬件/配置未上线”，还需捕获 DVR 模块上电后的身份/在线状态及视频输入链路；仅凭 VHAL 声明无法区分。

