# DVRConfig来源、位宽与默认值

未知：历史cmd102的0是否为未初始化/失败默认，配置是否来自文件。只补此前未闭合的活动future实现数据流，不重复查询cmd102。

## 静态证据

活动类沿用B3：future.ECarXCarConfigService。getLocalConfig(code1764916) codeunit15读取CarPropertyService property0x28700370、area0，取byte[]；retryGetLocalCfg要求至少40字节。lambda$init$1(code1767192)取结果构造CarPropertyStatusMap，后者构造LocalConfigExcel。getLocalConfig将每字节转成带0x前缀hex并按竖线拆成String[]；这条活动链没有直接读XML。

LocalConfigExcel构造器(code1772020)初始v0=-1，codeunit271写mDVRConfig；配置null在433..442提前返回，保留-1。codeunit478令v6=3，核查到1830无后续v6写入；1828令v9=23，1830参数编码669d为v13、v9、v6、v6，即processVorNetConfig(array,23,3,3)。1833取结果、1834写字段。

helper(code1769740)索引越界返回-1；正常取array[index]去前2字符、parseInt(hex)，返回(value >> startBit) & (255 >> (8-width))。本字段为(byte[23] >> 3) & 7：第24字节bits3..5，范围0..7，不未经消费者定义解释成boolean。

getInfoFromConfigFile(code1764628)在propertyStatusMap为空时返回-1，否则经getLocalConfigExcel→CmdGetConf；cmd102→getDVRConfig沿用B3。0不是这些已证失败路径的默认结果。

## 替换来源

resetLocalConfigFromSystemProp(code1764160)仅在输入非空非零长度且全为0时，尝试sys.ecarx.local_config；字符串须非空且长度为数组长度*2，以两字符hex分段替换输入数组。本调查不执行该函数。不是全FF时无条件替换；configFFCount日志是另一统计。

## 本机实测与结果

2026-10-15通过既有本机ADB/HIDL --get只读查询0x28700370：40字节、AVAILABLE，非全0、非全FF；byte[23]=64(0x40)，DVR bits3..5=0。sys.ecarx.local_config为80字符hex，但全0替换条件不满足。在已恢复实现下，当前数据正常解析出0，而非上述默认/替换路径。配置来源不证明配置正确或DVR实装。

没有改配置、属性或字节。原始本地证据config-word-live-20261015.txt、config-word-sysprop-20261015.txt；摘要config-word-summary-20261015.json。静态证据config-bit-source-20261014.txt、config-callers-20261015.txt、config-origin-20261015.txt、config-service-source-20261015.txt与既有config-dvr-dex-trace.txt。

关闭了默认值/来源/位宽缺口；0的产品枚举、是否隐藏入口或约束发送仍未知。F110 support boolean属于另一分支，不作当前枚举证据。重查条件：配置字或活动实现变化、实际比较配置值的消费者出现、参数或字段写入反证。同一配置源不重复查询cmd102或全量配置。
