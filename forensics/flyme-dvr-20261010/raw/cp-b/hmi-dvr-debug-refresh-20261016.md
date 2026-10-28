# HMI CP DVR debug section 补取

## 要解决的未知

此前 `weston-simple-egl` 只取了 `.text/.rodata/.symtab` 等运行段，缺少约35 MiB DWARF。未知是缺失的调试信息能否暴露 `ST_MMI_DVR_Command` 的实际订阅、取样或画面消费者。

## 只读采集

源文件仍是 CP B 槽 `/usr/local/bin/cluster-e04/weston/weston-simple-egl`，文件大小64569216；通过既有 EROFS flat/inline reader 以 CP 分区块只读分块读取，未挂载、未运行程序、未写 CP。补取 section：`.debug_info` 22071279 B、`.debug_abbrev` 287445 B、`.debug_str` 12366937 B、`.debug_line_str` 21769 B、`.debug_loclists` 6138187 B、`.debug_rnglists` 1035430 B；共10237块。原始文件与读取元数据见 `hmi-debug-20261016/read-meta.json`。

## 结果

对真实 `.symtab` 的既有全量匹配结果 `hmi-dvr-symbols.txt` 复核，DVR 相关的具体机器符号只有：

* `ClusterServiceHandler::HandleEventDVRConfig()`；
* `EventAdapter<ST_DVR_SystemSts>::UnsetReceiveHandler()`；
* `EventAdapter<ST_MMI_DVR_Command>::UnsetReceiveHandler()`；
* `EventAdapter<unsigned char>::GetNewSamples<ClusterServiceHandler::HandleEventDVRConfig()>`。

新补的 `.debug_str` 确实包含 `ST_MMI_DVR_Command` 模板的通用成员名（`Subscribe`、`Unsubscribe`、`SetReceiveHandler`、`GetSubscriptionState` 等），也包含 `SubscribeEvent` 和 `HandleEventProjectionScreenResp`。这些是类型/模板字符串，不能当作调用点。与实际符号表结合，当前 HMI 原件没有对应 `ST_MMI_DVR_Command` 的 `GetNewSamples`、`SetReceiveHandler`、`Subscribe` 或 `HandleEventMMI_DVR_Command` 具体函数；已有 `UnsetReceiveHandler` 不能证明曾经订阅。

因此补取没有发现新的 HMI DVR 命令消费者，也没有建立 `MMI_DVR_Command`→`ProjectionScreen`/EVS 的数据流。它把“可能藏在缺失 DWARF 中”的缺口收窄为：需要未剥离匹配 CP middleware、生成的 event binding 或其它程序的实际实现；继续重复 HMI 字符串搜索不再有价值。

## 读取器边界与重查条件

section 数据已单独保存，调试 section 的内容读取成功。为恢复完整 ELF section table 而做的本地拼接副本未作为证据使用；结论只依赖原先保存的真实 `.symtab` 符号筛选和本次原始 DWARF section 字节。重查条件：CP build ID/槽位变化、出现匹配 middleware debug/symbol 文件、或具体 event binding/运行记录把33033与字段访问联系起来。相同 HMI 二进制不再重复读取。
