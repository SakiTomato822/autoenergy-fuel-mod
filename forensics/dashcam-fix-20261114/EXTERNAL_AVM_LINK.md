# 外置 AVM 的实际视频链路与能力入口（2026-11-14）

目的：沿当前工作的外置 360 链路确认上游身份/输入协议，判断能否取得四路原图。B31–B35 的 APK、实帧、启动选择和 MAX 结论直接引用，不重复试装或读取全零状态。

## 1. 真实 UB954 读取（17:50 / 17:56 CST）

当前本机访问路径仍为 root ADB `127.0.0.1:5555`，独立 daemon 端口5038。已注册 `7-003d/name=ub954_mipi`，使用 `/dev/i2c-7` 的已知地址0x3d。

`src/read-ub954-identity.S` 只执行 I2C_FUNCS 和一次 I2C_RDWR：13组“1字节寄存器地址指针 + 1字节读取”，共26条msg，同一个adapter锁内完成。未扫描地址、slave-force、改GPIO、写控制数据、选择RX端口或访问清读状态。完整返回26才输出13字节；4秒timeout只限制工具，不重复读取。

| 寄存器 | 实读 | 含义 |
| --- | --- | --- |
| 00 | 7a | 当前7位本地地址0x3d，未设置地址override |
| 03 | 20 | revision2 / mask0 |
| 4c，读前/后 | 01 / 01 | RX_READ_PORT=0；全程未改端口选择 |
| 58 | 18 | pass-through-all=0，pass-through=0，auto-ack-all=0；back channel always-on=1；频率选择0 |
| 5b | b0 | SER_ID=0x58；FREEZE_DEVICE_ID=0，允许从远端自动加载 |
| 5c | 00 | 没有串行器访问别名 |
| f0–f5 | 5f 55 42 39 35 34 | 本地芯片只读标识 `_UB954` |

这是本地接收芯片的真实身份，以及其记录的上游地址。不能把0x58当作芯片型号、模块厂商或外置AVM的固件版本。转发关闭且alias未配置，当前还没有上游直接寄存器访问路径；本次没有启用转发或新建别名。

后续未知是输入类型，另编译 `src/read-ub954-transport.S`，一次6条msg只读4c、6d、4c，避免重复读取芯片身份。17:56返回 `01 7f 01`。0x6d低两位3，表示 **RAW10 / 100MHz，DS90UB913A/933兼容输入模式**；不是CSI输入兼容模式。这里RAW10是FPD-Link传输/并行输入格式，不证明收到四路未拼接摄像头原图。当前上游精确型号仍待证。

按此前已验证的YUYV 16bit以及25fps作比较，1280×3200×16×25=1,638,400,000bit/s，超过该RAW10/100MHz并行输入的10×100MHz理论上限（未计消隐/协议开销）。因此不能假设当前物理模式能直接传入同尺寸同帧率的未压缩四路流；不排除降帧、别的编码/输出模式或另一条链路。记录仪所需1280×3200属于既有MAX原始输入方案；修改本地尺寸或设备名不能产生这种数据。

资料：TI [DS90UB954-Q1 Rev.C](https://www.ti.com/lit/gpn/ds90ub954-q1)，§7.5.3远端I2C，§7.6.1/4/80/83/84/101/155–160。特别是0x4c分银行、0x58转发、0x5b地址自动加载、0x5c alias、0x6d输入模式、f0–f5本地ID。依据寄存器表避开reset、错误计数及其它清读状态。

重查条件：链路模式/配置/驱动版本改变、上游身份新证据、可恢复的明确转发访问方案。相同配置不重复读本地ID，也不以alias=0反复试读远端地址。

## 2. 其它身份/能力入口

- `RenderAvmInsideViewBX11::drawAVMVersionView` 0x4004c在本地组装ASCII `20250523`（4009c–400b0的立即数）；这是内置AVM实现的绘图路径，不能当作外置ECU自报版本。`Constants::Version::OUTSIDEVAL` 的 `C_202212160924` 同样是本地字符串。证据 `version-view-static.txt`。重查：新版本、具体外置版本响应/实现。
- libtouchcontrol已有两个RPMsg端点配置（1554→1557、1555→1560），但当前evsrenderengine344、evsapp400未见对应打开的RPMsg fd/端点。外置变体 `RenderApaViewBX11::AvmTouchEventProcess` 调用VHAL invokeSet，含属性0x29700235和按下/释放的单字节载荷；尚不能把编译进库中的RPMsg端点当作当前外置ECU控制通道。未发送触摸/模式命令。重查：实际端点/调用证据、版本变化。
- VHAL存在 `AVM_DevelopMessage_RX1`，解析函数 `CANAnalysis::parse_0x6E4_Data` 0x32a380；名字映射0x29700249。已有12秒有界只读hook未观察到调用，进程仍存活；只说明观察窗口无该parser调用，不证明物理总线没有6E4，也不是有效能力查询。证据 `avm-develop-parser.txt`、`avm-debug-observation.txt`。仅在已知触发条件/总线路由新证据时重观，不重复空等。
- 新定点审查了VHAL AVMManager以及DiagnosticManager/DiagnosticUtils。前者按MCU message子命令及配置映射处理属性；后者有通用MCU诊断转发与LCD DTC监听。当前尚未闭合到外置AVM的型号/能力查询、UDS寻址或原始输出切换实现。代码存在不是可直接执行的查询；不据此声明整车无诊断接口。离线反汇编仅供继续分析。

## 3. CP 是否接管四路原图

从cp_dtb_a/b分别只取头页和35个payload页，依据包装头offset4096和FDT totalsize截取，静态FDT A141360/B141844字节。两个槽位均有18个camera CSI/CIF节点，均disabled；相关I2C E95C/E95E同样disabled。另有启用的MAX96722@E9670000子节点，但它不能把disabled的整条CSI/CIF图证明为工作链。

当前未找到CP已接管四路原图的正证据。静态槽位DT不是CP运行时DT：启动选择、修改或不同运行图仍是证据边界。重查条件为运行时CP设备注册/视频链路证据、boot/slot/DT变化；同两槽不再重复提取。

## 当前判断与下一步

本轮新增了真正的链路身份、上游地址及输入协议证据；**尚未实现四路录制或原厂DVR实际使用**。本车外置AVM链路与记录仪依赖的MAX原始流路径确有差别，已超过“权限漏扫/简单改节点”的解释。

下一条有价值的证据是同款成功车的硬件/启动方案，或本车外置AVM明确的型号/固件/上游输出控制协议。保留继续追踪现有CAN/VHAL控制映射的分支；不猜数值发送、不会用校准/恢复出厂功能充当探测，也不循环读空状态。

## 4. 用户补充：成功车辆同样没有自动泊车

用户先提出旧06无自动泊车可能解释兼容差异，随后明确群友成功车辆也没有自动泊车。将“有没有自动泊车就是分界”降为不成立的单因解释，不继续围绕该猜测读状态或改配置；具体成功车型/硬件版本尚待确认。

离线新定位OneOSAvmApp的 `openApa`：它检查externalAvmLockStatus，然后经CarFunManager设置功能0x23040100。这表明此软件的自动泊车控制也能依赖外置AVM，不能把APA功能、名为apa的视频接口、内置/外置AVM三者划等号。未执行该路径。`getAvmConfig`查询0x800500，AdapterAPIImpl转成配置cmd103。活动future配置的mAPA与mAVM是独立字段，沿用既有配置来源审查；这些字段不能取代用户实际功能确认或物理输入证明。

顺便复用既有六个OTA DEX，定向查AVM/DVR/ECU目标/身份读取字符串：ECarXOtaService和三个App dex无匹配；OneOSOTA第一dex仅通用配置常量/图标名，第二dex含IHU/TEM ECU类型与通用任务字段。未发现外置AVM明确升级目标或身份握手；这只是已取APK的窄检查，不证明整车无该固件/动态下发目标。未启动OTA/升级，也未扩大为网络诊断扫描。证据 `ota-avm-targeted-strings.json`。重查：新APK/native或AVM升级包/目标配置出现。

继续优先比较成功车辆的实际video6分辨率、启动HW/AVM选择和物理方案，或取得本车上游模块的明确协议。当前不通过开启APA、标定或恢复出厂来尝试生成原始视频。
