# 2B1软件状态的条件式语义

未知：13/02 byte7到底表示接收有效还是缺报，以及AP怎样使用它。本次只追此前未闭合的维护回调和AP消费者；固件身份、位打包和实际访问方式沿用历史，不读车辆状态或运行内存。

## MCU侧：受门控的接收/计数状态

缓存处理表必须用20409804+218→20409a20；index20行+0=1、+12=1，不能套物理CAN分发表20408bbc的字段。以下是index20在此静态行不变时的分支，不宣称状态只有这些写入者。

- 匹配接收的435fd4在+eb7==1时将事件数组204181e8+e4d[index]写3（436010），与数据是否改变无关。
- 维护4366e0检测该事件bit1；有事件时清bit1，并从48bc9c[index*4+2]装载计数/5（43670a..72c）。index20该半字为20，因此重装4个计数单位。
- flags=1意味着bit6/bit7均0，接收事件分支随后调用4388c0。回调非零，且status[index]非零时，4367be..7e4清status为0，并调用该行的43e1bc回调。回调为0时不走此清位分支。不能说每次收帧无条件清状态。
- 无事件时，若计数非零，4367f0..87a在4388c0非零且44139c返回0时递减计数；耗尽后，对row0=1且status!=1的项在436832置status=1并调用43e1bc。门控为0则重装；44139c非零则不执行该递减。因此此路径的1对应受门控的计数耗尽/缺报状态，而不是“收到有效帧”。
- 4388c0读取20400384 byte0：低2位==1则返回0，否则返回bits2..4，其中4映成3。缓存指针表index8指向20400384、物理来源行ID2FC/DLC8；未恢复其产品模式枚举，不擅称点火/电源状态。44139c读取2040b1ee+4半字，产品意义未闭合。
- 435cb0初装计数使用48bc9c[index*4]半字/5；index20为500/5=100。接收后重装另半字20/5=4。它们是不同阶段的内部计数值，未确认维护调用墙钟周期，不换算毫秒。

## 掩码交叉核对

index20掩码48900c为3f e7 00 0f 1f ef c0 00。按AP从8字节大端数据还原整数的布局，对应DVR字段：ControlRequest14..15落wire byte6 bits6..7；SD48..50、SDFormat53..55落byte1；System56..59、RecordingCycle60..61落byte0。掩码保留这些字段。这是为防止混表作的缓存身份核对，不是再次发现常量作为功能进展。

## AP侧：状态位进入属性可用性

McuCanResponse::Builder(1da8c8)从body首字节取bit0，写对象+42（1da964..96c）；这是原始wire byte7。响应构造器1dab68..6c保持该字段。CanManager::processMessage在13/02分支从+42取值，存sp+68（1d93c4..3c8），在1d95f8作为w3传ModuleDataManager::parseCanData(2cec60)。CAN普通分支转VehicleDataBuilder::parseCanData(2f0bf4)，保持该w3值。

VehicleDataBuilder在2f0fc0..fcc计算输入状态==1的布尔值；2f1548/1564和2f1f7c/1f98把它写入待输出属性状态，并在2f159c、2f1fd4存到VehiclePropValue+16。普通生成属性因此状态1→UNAVAILABLE、0→AVAILABLE。框架布局和枚举依据AOSP Vehicle HAL 2.0 types.hal（AVAILABLE=0、UNAVAILABLE=1；timestamp/areaId/prop之后是status）：https://android.googlesource.com/platform/hardware/interfaces/+/76fa88e/automotive/vehicle/2.0/types.hal 。本地代码链支持位值的实际使用；不据框架枚举反推硬件身份。

## 结果与边界

已把byte7从“业务意义完全未知的软件布尔值”推进为：存在受接收事件/计数耗尽和模式门控控制的缺报状态路径，AP普通属性构造流程将其映射为可用/不可用。历史byte7=0不能证明收到过新帧：初始值、门控未满足、计数未耗尽等仍未排除，其他写入者也未穷举。缓存全零仍不证明独立DVR实体在线或离线。未发控制、未启画面、未重读全零。

证据：status-maintenance-20261013.txt、rx-event-20261013.txt、status-callback-20261013.txt、status-gate-20261013.txt、status-delay-gate-20261013.txt、ap-status-consumer-20261013.txt、ap-status-builder-20261013.txt。ap-dvr-status-parser-20261013.txt只作数据解析补充，非单独的有效性定义。

重查条件：版本/表映射变化；发现status的其他写入者或模式枚举；取得同时包含模式、接收事件/计数和报告的历史序列。重复只有全零/byte7=0的样本不触发复查。报告formatter的调用关系另见report-trigger-20261013.md，当前仍不假定周期主动上报。
