# 强制开启 video6cif 可行性审查（2026-11-14）

用户新要求：研究是否可以强行开启v6。本阶段做定向只读、离线分析和一次已知ID寄存器查询，没有修改设备树、加载内核模块、绑定设备、切GPIO、写I2C控制寄存器或刷boot。14:30经当前已启用的i2c-31对已知地址0x6b进行一次芯片ID组合读，细节见下。

结论：软件层有研究方向，尚不能保证本车能输出原始四路帧。它至少涉及启动DT选型、内核probe门控、实际硬件与媒体图，超出APK或文件权限修改。不能将创建video6cif文件认定为打开物理输入。

## 新检查与证据

1. **是否有现成的运行时DT overlay入口？** 实测/proc/config.gz中`CONFIG_OF_OVERLAY`未开启，当前/config是configfs但无device-tree overlay接口；`CONFIG_KPROBES`与`CONFIG_FUNCTION_TRACER`未开。现成overlay/kprobe/ftrace捷径受限。`CONFIG_MODULES=y`、`CONFIG_MODVERSIONS=y`、未强制MODULE_SIG、modules_disabled=0，保留定制模块的理论方向，不能写成完全无法临时修改。旧kallsyms有导出的of_add/remove_property与of_platform_device_create；这不等于已做出可加载、可回滚模块。证据kernel-config.txt、v6-force-runtime-gates.txt。
2. **强制bind是否就能创建输入？** MAX96722驱动已注册并有bind/unbind，但目标i2c9控制器及CSI2平台设备均未创建；正常bind针对已有device，不能凭空补全被引导禁用的父总线。旧DT的MAX端点挂E95E0000并连接e8050000。即使修复DT，当前厂商probe在0xb43a78/88调用车型helper(2)/(3)，均不匹配则0xb43e38清匹配标志并继续probe，0xb44068～74依据此标志跳过两个延迟初始化任务；因此它可能注册出subdev而没有真正初始化出帧，并非简单probe返回失败。它不是只有DT一道开关。
3. **probe能否当只读识别？** 不能。当前原件0xb43a24申请vehicle_config_gpio147，0xb43a44将其设输出低，0xb43a64写低，然后才检查车型。继续路径还处理PDB、RPMsg以及解串器初始化。即使最终probe失败，也可能已经改变硬件引脚。没有运行此probe。进一步定向读取8KiB初始化代码：do_init_max96722_sensor(0x1148b18)先复位再读芯片ID；硬件初始化函数0x1147ad4会按sensor类型选择42/54/84项表执行I2C写入，所以不能把启用初始化任务当成一次无副作用查询。独立复核还确认：0x1148be8读ID0x000d，0xa0/0xa1立即进入配置；不匹配则重试，计数w20==3的第四次即使失败也跳到0x1148c24写0x08a0=4，再调用硬件初始化。ID失败并非拒绝配置的保护。
4. **目标四路格式是否真实存在于驱动？** 新读取boot_b三个各4096字节的格式代码/表静态页（源码已知函数/表指向，非全内核普扫）：MAX enum_framesizes 0xb42920读表0x26a16c0+292，原始尺寸1280×3200；flag==3分支读+484为1920×1536。UB954 enum_framesizes 0xb47370以及set_fmt 0xb480f4指表0x26a3348，其8个非零模式包括1920×896、1280×800等，没有1280×3200。具体字节/换算见format-table-summary.json。这支持原APK针对MAX原始图方案；模式表本身仍不证明硬件实装或信号在线。
5. **能否只让UB954接受1280×3200？** 当前set_fmt函数范围0xb480f4～0xb483d4仅选模式、写驱动结构/打印，不包含I2C事务。后面的reinit_des_store是另一个函数，不能混淆。表中没有目标尺寸，改表/EVS声明并不证明上游外置AVM会输出这种数据；不能以虚报宽高替代原始四路。
6. **是否有可用源码帮助做临时方案？** 找到芯擎公开SE1000 Ubuntu SDK，manifest指kernel/test，Makefile为5.10.110；按需取MAX96722、media图、CIF与CSI平台四个源文件，未下载整个SDK。公开MAX驱动与本车厂商版本明显不同（公开probe按sensor兼容项选择、读芯片ID，本车另有vehicle_config GPIO/模式/RPMsg定制）；同版本号不证明模块ABI或初始化寄存器兼容。公开se-media-dev按启动时available CSI建立async notifier/links，提示热加节点还需媒体图处理；本车实现仍需原件对照，不能直接套用公共源代码。

7. **能否利用已有总线先确认芯片？** 旧DT的E95E0000与已启用的gpio_i2c_mfi/i2c-31都使用scl-gpios=<0x2e 4 0>、sda-gpios=<0x2e 3 0>，因此先利用已有adapter，无需创建i2c9/改引脚。当前厂商read_reg 0xb435b0在client0x6b时选地址0x6b，初始化0x1148be8用其读取0x000d并比较0xa0/0xa1；公开源代码同样使用该ID。制作硬编码单次读取器：I2C_FUNCS能力查询后仅一次I2C_RDWR，两消息为地址指针[0x00,0x0d]和读1字节，未写控制值、未扫描、未设置slave-force/重试/timeout参数。14:30:11实测返回errno6/ENXIO，没有有效ID；EVS仍running。只说明当前路由和供电下未应答，不证明芯片未实装（可能被线路选择/复位/供电隔离）。现有GPIO总线由其他硬件方案选用，不能自行推其连接等价性。证据src/read-max-id.S、max-id-bus31-one-shot.txt。重查需先有线路/供电/硬件变化或对应模块正证据，不重复无ACK查询。

## 候选路线与进入条件

|路线|当前依据|还缺什么|
|---|---|---|
|定制临时内核模块，恢复目标DT/实例（或在确认线路后复用i2c-31）并建立媒体图|模块可用；部分OF/平台接口导出|匹配内核ABI/符号版本，安全的车型初始化门控绕过，真实MAX96722/CSI连线正证据，局部撤回设计；尚未形成可执行验证|
|修改boot/DT及相应车型门控|SBL实际覆盖DT；MAX驱动还有第二道检查|受支持的临时启动/完整回滚、硬件身份与接线、匹配初始化；本轮未制作刷写镜像|
|外置AVM模块切为原始四路输出再走现有链|目前仅实证完整1920×896合成UI|模块型号、支持模式/控制协议及物理带宽/布局证据；现有DVR控制字段不能自动当原图切换协议|

最小有效下一材料是本车外置AVM/车机主板的料号或同硬件成功原始四路记录，以及作者成功车型与接口说明。由软件可以继续审内核ABI和媒体图，但未确认硬件时强行复用MAX96722初始化不能构成有区分力且可恢复的验证。外置AVM配置本身不是芯片未实装的证明。

重查条件：获得硬件/作者成功匹配证据、匹配厂商内核源码/符号版本、确认可控临时模块路线，或系统/内核/引导配置变化；不重复扫v6设备名、全零状态或试开360。

## 官方材料来源

- Linux 5.10 OF overlay机制：https://www.kernel.org/doc/html/v5.10/devicetree/overlay-notes.html
- Linux 5.10 I2C设备实例化：https://www.kernel.org/doc/html/v5.10/i2c/instantiating-devices.html
- 芯擎SDK manifest：https://gitlab.com/siengine-ubuntu-sdk/manifests/-/raw/main/se1000-ubuntu-ser1.1.xml
- 公开内核Makefile：https://gitlab.com/siengine-ubuntu-sdk/kernel/-/raw/test/Makefile
- 按需源码均来自该kernel/test仓库drivers/media/platform/siengine/；保存文件和SHA256，只供对照，非本车源码身份担保。
