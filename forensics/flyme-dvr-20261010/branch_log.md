# DVR 本机调查分支记录

历史基线：handoff/START_HERE.md 及 evidence。沿用已完成检查，未重复读取 CA..D3，未发送 D8，未打开360。

## A：访问路径与版本，2026-10-10 20:50 CST
- 未知：真实身份、已有 ADB 是否可用、原生权限和当前版本。
- 本机实测：Codex UID10134、untrusted_app_27；ADB 5038 已连接 127.0.0.1:5555，shell UID0、u:r:su:s0、SELinux Permissive。无需 su 或修改权限。
- 路径：Termux → env -u LD_LIBRARY_PATH -u LD_PRELOAD adb -P 5038 -s 127.0.0.1:5555 shell。
- fingerprint：LYNK&CO/DHU615G/bx11_3c3222_module:11/RQ3A.210905.001/1-userdebug:userdebug/test-keys；DHU SW0BX110622H083100000；MCU DHUBX11G0622CSJ / HW1.5。不能仅据 fingerprint 确认与历史 OTA ELF 相同，旧 VA 暂不套用。
- 证据：raw/environment-assets.txt、raw/target-assets.txt。首次身份输出未落盘，关键身份和版本随后已保存。Android date 不支持 -Iseconds，后续使用普通 date。
- 新证据：当前 root ADB 能力、精确 MCU 版本。
- 重查条件：连接失败、重启、OTA/MCU升级；不周期重查。

## B1：本地 MCU 接收实现材料
- 未知：是否存在可读 MCU 升级固件用于解 D8 接收端。
- 检查：限定 /vendor、/system、/odm 与共享 Documents/Download，查固件文件名；不读串口、不执行升级器。
- 结果：所查范围未定位 3C5525 固件；共享存储部分旧目录 Permission denied，结果不覆盖这些目录，不证明整机无固件。
- 新证据：暂无；转向现存 EVS 原生代码。
- 重查条件：具体升级组件、嵌入固件或可读缓存线索；不重复文件名普查。

## B2：视频拓扑和选择实现，2026-10-10，10-11继续
- 未知：video6cif/video12cif 是双输入还是编译备选，是否能关联 DVR。
- 本机实测：EVS PID346 打开 /dev/video12cif；video4linux 列表有 video12cif、video0vicam 和三个 subdev，没有 video6cif。ub954_mipi 7-003d 绑定，已知 lock_status 可读；9-006b/max96722_status 不存在。均不作为 DVR 身份证据。
- 当前 ELF：raw/evs-service 184864字节；raw/evsapp 492768字节。已从运行路径拉取，不沿用旧绝对地址。
- 配置 JSON 包含默认 video0/mxc_isi 名称，与现场不符，不能假定实际使用。
- 证据：raw/video-runtime.txt、raw/video-config.txt。有限 logcat 4000行按主题过滤，内核日志为01-01日期，不作当前事件时间。
- 下一检查：对 video6/12 做代码交叉引用，恢复实际选择条件。
- 重查条件：ELF哈希变化、真实调用者或切换事件；不重复读锁状态或设备列表。

### B2 结论补充，2026-10-11
- 当前 EVS SHA256 d613407e2d085c94f211e4da56ada537beede1493e8a932922761eae708a3af9；evsapp 91a327726c96db987896e62f00433a4a13e7b0d67c7656b76ffd4c2fc8f5d5b6。10月11日远端哈希与10月10日拉取件相同，才沿用当前二进制分析。
- EVS 内嵌 .gnu_debugdata 解压恢复符号；SiengineCameraHardwareInterface(int) 在0x1bbb8，输入整数存于base+8；类型0→video6cif，尺寸1280×3200；类型1→video12cif，尺寸1920×896。此前口头把3200误读为800，已更正。
- EvsCamera(const char*) 0xf5a0 明确比较字符串：avm→0，apa→1，rvc→2；0xf7e8 调用工厂0x1bb18，再转入上述构造函数。EvsEnumerator 构造函数0x14dbc 将avm/apa/rvc添加到枚举表，GOT 0x281d8/1e0/1e8指向0x5d18/1c/20三个字符串。
- 结论【静态代码支持】：video6/video12是AVM/APA候选路径，不能把video6当DVR第二输入。此选择链没有建立D8/D7到视频的联系；不能扩大为整车无DVR。
- 完整指令：raw/evs-disasm.txt；符号：raw/evs-symbols.json；原始ELF和哈希可复核。安装binutils仅作离线工具，未附加进程或改变服务。

## A2：跨日连接恢复
- 未知：原5038 ADB server失去设备后，监听端点是否仍存在。
- 结果：persist.adb.tcp.port=5555、本机5555监听；重新connect 127.0.0.1:5555成功，真实root身份和MCU版本不变。未扫描网络。
- 重查条件：连接再次丢失；不定时重连。

## B3：DVR配置定义与消费者
- 未知：DVRConfig=0的枚举含义，是否存在另一个独立的模块支持字段。
- 本机实测：car_service --list显示活动配置实现为com.android.car.config.future.ECarXCarConfigService；系统ServiceManager不存在独立car_config服务。car_config是内部car service接口，不可据此判成缺失。--list尾部空服务引发NPE，列举内容仍保留；不重复该dump。
- 静态：CarService-3.dex含LocalConfigExcel→getDVRConfig；另含F110Config.mSupportDVRForF110。后者解析配置位成boolean，但尚未建立它与当前活动配置实现的数据流，不作为模块在线证据。
- 仅追字段真实引用、方法调用与解析；未重复历史102值检查。raw/config-dvr-dex-trace.txt、raw/config-dvr-consumers.txt保留代码单元和引用；dex-trace.cjs限定读取Dex，非运行时注入。
- 枚举值含义仍未恢复。重查条件：发现比较值的真实消费者/配置字典；不重复常量搜索。
- 工具说明：jadx直接安装会额外拉取191MB Java21依赖，已在下载阶段中止，未安装；仅下载jadx包也过慢，已中止。继续使用已存在Java17和轻量Dex解析，不改变Java版本。

## B4：接收侧CP本机固件材料
- 未知：本机是否可直接取得CP消费者代码，替代缺失的电脑OTA材料。
- 文件级升级缓存检查：/data/ota、/data/ota_package/sota为空；已查升级组件未给出MCU固件。有限vehicle.log头部262144字节按固件/配置主题过滤无命中，不继续重复抓全零状态。
- 新证据【本机实测】：分区表有cp_rootfs_a/b、cp_kernel_a/b、cp_dtb_a/b以及CP配置分区；rootfs各1GiB。各封装头指向偏移4096；B槽该偏移+1024存在EROFS magic 0xe0f5e1e2，blocksize4096、rootnid36、7820inodes。目录项支持该格式判断，非单一字节命中。
- AP ro.boot.slot_suffix=_b，但CP可能独立切换；当前仅标注B槽，未证明CP活动槽位。
- 只读解析：erofs-read.cjs以adb exec-out dd按需读取4096字节块，限定最多32MiB，解析flat/inline布局；不mount、不写分区、不执行CP程序。源格式依据 https://github.com/torvalds/linux/blob/master/fs/erofs/erofs_fs.h 。头部首次dd混入尾部stderr，后续加2>/dev/null，识别使用纯4096字节body文件。
- 待查：目录清单→cm_config与通信消费者→DVR命令订阅与处理分支。
- 重查条件：新槽位/版本线索、缺失目标文件、格式校验不通过；不重复整个分区普查。

### B4 结果，2026-10-11
- B槽目录清单7820项，按需读取约10.6MiB，未读取整个1GiB。cm_config定义DVR_SystemSts/8099、MMI_DVR_Command/8109、DVRConfig事件。
- 提取IVIService、VehicleService、ConfigService及启动脚本；脚本启动前两者，ConfigService被注释。这里只描述镜像内容，不据此认定运行态。
- HMI仅提取.text/.rodata/符号及链接相关节约18.5MiB到raw/cp-b/hmi-partial.elf；它是稀疏派生材料而非完整原件，manifest记录所选节，未填充节头已清零。HandleEventDVRConfig仅记录收到的配置字节，未提供枚举或画面进入分支。命令EventAdapter符号不能证明实际命令消费者。
- 结果：CP材料没有补上D8合法payload；不能凭同名字段把CP命令与D8连接。重查条件：找到实际接收处理函数、活动槽不同或新版本。

## B5：真正的VHAL下一跳与R52接收链，2026-10-11
- 未知：历史McuSerialComm::write之后到底进入哪个处理器；该接收端是否解释D8。
- 本机实测：当前VHAL PID352，fd16=/dev/rpmsg_ctrl2_ap_r5.99.99，fd17=对应100.100。当前ELF保存raw/current-vhal，mini debug解压raw/vhal-debug.elf；不沿用已退出PID347。
- 静态代码支持：McuSerialComm::open 0x18c15c创建99/99端点并将fd存this+408；write 0x18ea04通过该fd写入原vector。详见raw/vhal-open-disasm.txt、vhal-write-disasm.txt、vhal-link-runtime.txt。
- 只读提取r52_a/b各2MiB，封装payload起点4096、长度377908。ARM向量/复位跳转一致支持基址0x32800000；B编译20:39与运行属性R52BX110622H2039相符，但不当作活动槽密码学证明。
- R52 B分区SHA256 3927a809a0c3cfecb0815feb54c5c53f5e82295aa43d719da32e85d163725532；payload SHA256 3c6ca00e420b2076869750cc189ad10a309573ca3d5a14b694f8a19196a78d5c。
- 可信控制流：init 0x32820174创建AP0 99/99，callback 0x32815fa8将报文入3287f11c队列；解析0x32810b48验证55aa、长度与校验。帧main/sub在byte4/5，特殊分支仅main3/sub17版本和main3/sub13心跳；D8历史main0x13/sub1走普通分支0x32810dd4，将整帧放入3287f158。
- UART TX任务0x32821ed4从该队列取帧，0x32822190调用0x32816678，r0=3、r1缓冲、r2长度。所跟踪普通路径没有解释01A8或修改DVR payload。
- 新结论【静态代码支持+本机端点】：VHAL→RPMsg99/99→R52普通报文转发→UART通道3。外部MCU为后续待查接收者；R52是传输层，不能从它恢复D8枚举。证据raw/r52-ept-init.txt、r52-ap-callback.txt、r52-uarttx-disasm.txt、r52-b-arm-disasm.txt。
- 重查条件：R52/VHAL版本变化、发现另一条DVR专用处理路径；不重复反解Android发送序列化，不发送测试帧。

## B6：外部MCU固件具体来源，2026-10-11进行中
- 未知：真正解释DVR命令的MCU固件是否仍在本机升级组件/私有缓存中。
- /data/media/0/Documents、Download限定深度文件名检查未找到5525/bin/se_update/BX11包；仅覆盖所记录模式和深度。raw/firmware-direct-media-search.txt。
- ECarXOtaApp/ECarXOtaService静态路径指向/data/ota_package、payload.bin和A/B分区升级；尚无外部MCU镜像路径。没有调用任何升级方法。
- 实际com.flyme.auto.update位于/system_ext/app/OneOSOTA/OneOSOTA.apk，私有目录浅层仅数据库与偏好文件。下一步沿它的真实文件操作查专用MCU缓存，而非重复全盘搜文件名。
- 重查条件：出现真实路径、包版本变化或缓存更新；无此条件不再搜同一模式。

### B6结果与归档补查
- OneOSOTA.apk（18058786字节）SHA256 5c424a87f30224c8728c95695f1b0be04523c18037d8c03c1a818965ef51d6d4。FileUtil.sortList识别MB_UPDATE.bin，UpgradeManager将具体路径交McuUpdate，后者按128字节分块读取。两份OTAZipHelper实现固定输出/private/mcu_update.zip；方法存在不证明现场曾执行。
- /private不存在；/data/ota_package/ab_update.zip和ab_update目录不存在；/data/ota为空。私有目录深度3清单没有镜像。检查已确认的路径，无任何升级调用。
- 补查Termux forensics深度5、共享Documents/Download深度5归档与MB_UPDATE名称；Documents/.zip目录验证是CarPlay交接。未读取无关EVCC备份内容。没有新增MCU源程序。
- 证据raw/flyme-mcu-trace.txt、flyme-mcu-trace-2.txt、mcu-known-cache-check.txt、shared-firmware-archives.txt、non-dvr-archive-members.txt。最小缺失材料仍为历史3C5525_MB_UPDATE.bin（628016字节、A55AA55A头），并需核对与当前MCU版本的关系。
- 重查条件：具体镜像或新升级缓存出现；不重复普查。

## B7：当前原生画面控制路径
- 未知：DVR是否绕过EVS枚举，从现有原生画面服务进入。
- 现场重新连接5038/localhost5555，进程PID已变，因此先按进程名定位；EVS/evsapp哈希与已分析件相同，不重新反解其映射。
- com.ecarx.parking=/system/app/OneOSAvmApp/OneOSAvmApp.apk；实际原生服务evs.render.engine进程evsrenderengine。存在avm-native文件不证明它运行；现场ServiceManager无avm_service，Java遗留AvmServiceManager不是当前服务已上线证据。
- evsrenderengine SHA256 62ea7cc0ed2822f4dfa44f1e37d41c144a2ab6efcfca32a25a7b5bcd896ab8ea，ELF自带完整符号表，无.gnu_debugdata。第一次按mini debug试提取失败后改用完整符号；空renderengine-debug.elf不作为证据。
- main 0x17628构造RVC/APA/AVM模块；onTransact 0x1a1f8以20..114范围跳表分派，未实现项及范围外走0x1afe8的BBinder默认处理。预览模式case22只读取三个int并写AVM全局参数；不是输入设备切换。已实现case为AVM/RVC控制，未恢复DVR预览处理分支。这是当前服务的边界，不扩大成所有整车固件无DVR。
- libtouchcontrol.so只发现通用RPMsg触摸转发符号，不能称专用JNI或DVR触控路径。没有抢占设备、试调用Binder或设置属性。
- 证据raw/renderengine-main-annotated.txt、renderengine-transact-annotated.txt（含完整95项跳表）、renderengine-avm-camera-disasm.txt、avm-native-maps.txt、avm-service-native-locator.txt。annotate-arm64.cjs仅作静态字符串/调用注释，寄存器推导可能跨分支，因此重要结论以原指令复核。
- 重查条件：原生服务/SDK版本变化、实际DVR调用者出现；不重复搜索同一属性常量。

## B8：运行设备树与视频父节点
- 未知：未枚举的第二视频链是实装输入还是共用配置；子节点okay为何无I2C设备。
- 只读/sys/firmware/fdt，163968字节，SHA256 c88fe11328c19447aadf802f92f80004f7ec2428940f925cb94ebae9298c413c。解析校验magic/总长/属性边界，1154节点、317phandle，解析工具fdt-inspect.cjs。
- UB954@7-003d→CSI4/e8070000→现场subdev se_cif.12与video12cif。video0vicam的of_node为screenshare_vicam，使用RPMsg，为屏幕共享路径，不认作独立DVR摄像头。
- MAX96722@6b子节点okay，但父/i2c@E95E0000为disabled；对应CSI2链接的存在不能代表整链可用。随后直接读取live /proc/device-tree父/子status仍为disabled/okay，排除只看启动FDT遗漏运行更改的证据缺陷。解释了9-006b设备不存在，不证明芯片实装或缺失。
- 证据raw/runtime-video-dt.jsonl、runtime-video-i2c-parent.jsonl、dt-video-binding.txt、live-dt-video-parent-status.txt。
- 有限dmesg仅命中重复lock状态，启动probe原因已被覆盖；无新增区分力，不计进展，不重复抓。
- 重查条件：DTB/驱动版本变化、新probe记录或硬件身份；不重复锁状态读取。

## B9：当前内核解串器驱动选择与UB954第二输入
- 未知：第二视频输入是否可由现有驱动切换，或本车仅选择一条通道；DVR能否共用现有视频链。
- 从运行/proc/kallsyms定位静态函数，再只读boot_b的Android v3头和ARM64 Image头。当前AP槽_b；Image未压缩。按_text=ffff800010000000映射，仅读取所需代码、字符串和注册页，未取整个41MiB内核、未读运行内存、未写分区或GPIO。
- max96722注册函数直接i2c_register_driver。probe含is_match_config_read_from_veh(2)/(3)，UB954 probe用另一份同名helper(0)/(1)。这些是视频方案筛选，不能当DVRConfig枚举；helper读取共享配置的行为只作离线分析，没有执行ioremap或读取/dev/mem。
- UB954 initialize 0xb47c24在0xb47c34/40调用write_reg(reg0x0c,value低8位0x81)。write_reg 0xb47614明确截低8位；stream 0x1149c20在首次开启时写reg0x20=0x20、末次关闭写0x30。TI DS90UB954-Q1官方手册7.6.13/7.6.33定义：0x81只开RX0，0x20只转发RX0且禁转RX1，0x30两端口禁转。来源 https://www.ti.com/lit/gpn/ds90ub954-q1 。首次symlink地址抓取超时，gpn官方文档成功。
- 结论【静态代码+官方寄存器定义】：所追当前驱动路径不提供现成RX1 DVR输入切换；这不排除前级外部模块在RX0上切换画面，不把“RX1禁用”推为没有DVR硬件。未执行任何寄存器读写。
- 证据raw/kernel-ub954-initialize-disasm.txt、kernel-ub954-write-reg-disasm.txt、kernel-ub954-stream-disasm.txt、kernel-max96722-probe-annotated.txt及对应页原件。boot页偏移=Image相对地址+4096；内核版本核对见live-dt-video-parent-status.txt、kernel-linux-banner-page.bin。
- 重查条件：内核/配置变化、发现其它有效通道控制调用、上游模块回应。当前不建议通过启用父节点或RX1试碰硬件。

## B10：历史状态是否由R52合成（2026-10-12）
- 未知：历史0x2B1全零报文来自UART对端，还是R52链路异常时自行生成/改写。仅分析已提取B固件，不重读全零状态，不执行串口操作。
- 静态代码：0x32819308以channel=3调用0x328273f8；后者从该通道寄存器结构的RX数据寄存器读一个字节，成功/仍有数据返回0/2。0x32819354..60把字节交给0x328117a0写入RX环3287f140；该环初始化见328192d4。
- 0x328216cc回传任务从该环组帧，校验55aa、长度及校验和；有效报文整体复制到聚合缓冲3287f165，随后经RPMsg99/99发AP/CP。中间0x328162f8只缓存3/23分支；0x32812ef0只复制前22字节到栈上解析13/02的220/250/255/284模型，不改原输入；2B1不命中这些分支。
- MCU无报文超时任务32820938..32820b10仅写告警及清错误标志后延时循环，未生成DVR替代状态帧。
- 结论【静态代码支持】：所追B固件正常UART3回传路径对历史13/02、2B1报文保持原数据，没有发现R52补零来源。将状态来源推进到UART对端，尚不能解释对端是否合成默认值、DVR是否在线。B镜像版本与属性相符但无运行槽证明，不能把此静态结论写成本轮实测通信。
- 证据raw/r52-mcu-return-parser.txt、r52-uart-rx-source-and-timeout.txt、r52-uart-rx-queue-xrefs.txt；交叉引用工具仅辅助定位，以上关键函数逐指令复核。
- 重查条件：R52版本或运行槽证据变化、发现其它RX环写入者、出现有效MCU接收实现。不会因继续出现全零报文重查。

## B11：MCU固件获取路线收敛（2026-10-12）
- 未知：已识别/private/mcu_update.zip以外，升级组件是否有实际调用者提供另一份固件来源。引用B6缓存与分区枚举，不重复查空目录。
- 检查OneOSOTA两份DEX对copyMcuZipByMpuZip的直接引用，仅找到两个同名定义，无直接调用者；不能把库方法存在写成当前升级流程或可获取固件的证明，反射调用未排除。证据raw/mcu-copy-callers-1.txt、mcu-copy-callers-2.txt。
- 现有分区名称里m4/r52是SoC固件，不能未经证明当外部MCU固件；本检查不增加已恢复DVR协议语义。
- 最小可继续材料为历史3C5525_MB_UPDATE.bin（628016字节，头A55AA55A）本体。取得后须先识别格式/架构/版本，确认与当前MCU关系，再追13/01、模型1A8接收侧；不能因文件可反汇编就直接发命令。
- 重查条件：得到实际升级调用记录/新文件路径/新升级组件版本；不因用户继续询问而重复缓存枚举。

## B12：取得外部MCU源固件并恢复D8接收位（2026-10-12）
- 未知：D8/模型1A8在外部MCU如何解释，是否仅透传；用户提供新文件，符合重查条件。来源/sdcard/Download/flyme_mcu_firmware_supplement_20261012.zip，工作副本raw/mcu-supplement。
- 628016字节，SHA256 8f046241a41965e23dc0bfcee57a2e17861425adae6c2ee0de71d49601e8b7cd，与包内MANIFEST一致。头A55AA55A，向量表文件offset1000：SP2042f000、Reset421421；以420000映射后复位421420为Thumb有效启动流。未确认头全部字段及尾部校验算法，不制作刷写文件。
- 固件字符串DHUBX11G0622CSJ（offset711b5/711d6）匹配B1已记录当前MCU版本，含SW0BX110622H083100000与Jun 22 2026。版本相符提高相关性，不证明当前运行内容逐字节相同。
- 入口：452e36调用448d90校验55aa/长度/checksum，452d34将有效帧送队列；450228取帧经4501d8转448ee4。主命令表48f1f8每项8字节，main13(hex)索引18→44a6c9；44a6c8处理sub1，将原始offset3起80字节拷贝至内部队列缓冲204206dd等，不作为直接调用授权。
- 接收队列1的任务42df5e，任务记录fileoffset67718，callback45bc3d；callback45bc3c→45a8e8→439984。439984拆多模型条目、按模型索引调用表48aaac，并更新发送调度表48910a。1A8表项offset69e4a为210214000300a801，callback43c67d。
- 关键新增【静态代码支持】：43c67c..686读取204004d7的byte1，以BFI(r0,#3,#1)写byte1 bit3，只保留输入整数最低1位，其他位保留。相邻1A7→byte1 bit2(Home)、1A2→byte0 bits4..5、1A3→byte0 bit6、1A4→byte0 bit7，映射与AP模型名称一致。故D8不是外部MCU原始bytes透传，已恢复接收端位打包；不能把单比特位宽直接等同于已验证进入画面。
- 所有这些模型的调度索引33(hex21)。439984将其倒计数设3；43a6b0在计数为1时清缓冲byte0、byte1除bit1外全部、byte2除bits1..2外全部、byte4bits0..1。D8位在清理范围内，但该清理函数与完整实际CAN发送周期的连接尚未恢复，不宣称具体毫秒或已经建立退出方案。
- 启动数据copy表4998d8由463848执行，源49a488→RAM20408000等；data-copy-map.json记录映射。基于映射读取RX描述表20408bbc，2B1为index20、length8；这仅定义接收项，不是模块在线证明。初期一次错将绝对地址48f1f8的文件偏移写成8f1f8，得到无效零表；已改为减420000=6f1f8，最终命令表与调用链一致，零表不作证据。
- 证据：raw/mcu-supplement/*-disasm.txt、dvr-model-callbacks.json、data-copy-map.json、can-rx-descriptors.json。全文件反汇编夹杂数据，仅用作定位；关键函数从已证实入口重新反汇编。未联网、未刷写、未写车辆属性、未发任何控制帧。
- 下一未知：调度索引33对应的实际外部CAN发送/使能条件、1位请求的UI进入/退出语义及回执。恢复后才能形成最小主动验证；若找不到消费语义须保持为候选，不猜旧系统值。
- 重查条件：固件哈希/版本不同，或对入口/数据初始化映射出现反证；同文件的架构和上述D8打包不重复检查。

### B12补充：D8到实际CAN标识的连接
- 发送buffer表2040ae6c按index33项指向204004d7，长度8；初始化源fileoffset7d3fc为该指针，对应整体copy映射。435d7e取index33，经只读映射表4890bc（index33→33→33）传434744。
- 434744读取配置表488364，每项20字节；index33项fileoffset685f8：000021000000a002000000002908000049634300。+0控制器0，+6=0x2A0；+8=0（此处不可误标为CAN ID）。4347a0把+6载入r3，经46d190→46cb08保留至r8→46cbb0传46f14e的r2；46f20c..224在标准帧分支将该值左移18写mailbox ID寄存器。支持CAN0标准ID0x2A0，DLC8，byte1 bit3为1A8模型的落点。此为静态发送路径，不是本轮抓到线上报文。
- 所以1A8是AP模型编号，已找到它在本固件合并为CAN0x2A0中的一位；不能发送独立CAN1A8替代。
- 仍未完成配置使能/调度触发的全部分支与DVR对端处理，不把1位断言为本车已经可用。当前未提出/执行控制写入。

## B13：AP与MCU报文偏移一致性复核
- 未知：MCU要求原包offset8为count、length>=9，而历史body简述只有route/count，是否有一字节错位。因明确证据缺口复核既有AP，不重取现场文件。
- AP sendMessage 1895e4在offset6写getSnPrio，body从offset7开始；所以offset7=route、8=count、9=entrylen、10/11=modelID、12开始payload，与MCU一致。AP wire length字段=bodylength+3，单条1字节payload时为9，满足MCU检查。历史把该字段称为整包总长度的表述应以本条更正。
- 结果：单字节D8载荷的格式得到两端支持，但没有生成/发送报文，snPrio取值、UI语义及退出验证仍未完成。
- 证据raw/mcu-supplement/ap-wire-header.md及两份ap-*-recheck.txt。重查条件：AP/MCU版本变化或另一发送入口修改报文头；同版本不重复复核。

## B14：CP DVR事件实际消费者边界
- 未知：CAN0/2A0的请求是否可接到CP事件33033并证明Enter/LVDS/画面语义。用户明确授权后使用Luna xhigh子代理，限本地静态分析。
- VehicleService的MMI_DVR_Command事件为33033，DVRConfig属于MixtureService的32854；33033还在另一服务用于FCW，不能脱离服务认定数据流。ST_MMI_DVR_Command布局只给uint8/uint16，未提供有效值枚举。
- 三个CP middleware中的命令名称与ID引用只确认部署初始化，未恢复信号解包或消费链。HMI部分镜像的可见处理代码只有DVRConfig日志处理，没有发现DVR命令订阅/采样/处理器；不能扩大为完整CP或整车不存在消费者。
- 结果：没有把模型1A6/1A8与CP字段连成执行链，不据字段名字猜0/1语义。不重复B7的画面服务检查。
- 证据raw/mcu-supplement/cp-semantics-agent.md含各ELF引用地址和partial边界。重查条件：匹配版本的信号映射表、未剥离middleware/消费者代码，或补足HMI缺失调试信息后有具体新线索。

## B15：MCU发送使能与请求清理
- 未知：index33是否受明确配置禁用、请求清位是否已接上发送路径。仅分析同固件此前未闭合分支，不重新确认模型常量。
- 434744要求管理器204172d4 state(+12)=2、ready(+8)!=0；flags(+4) bit1走直接驱动，bit1=0且bit3=0拒绝，只有bit3则走替代handler。初始化4345c0写flags7/ready1，43856a请求state2并调用缓存初始化4360d8；TX扫描另受4360fc/436124控制，提供正常可达路径；不能当成本车运行状态实测。
- 直接路径46d190返回0后才执行index33 callback43a6b0；替代handler路径外层无条件返回0，故callback连“低层提交成功”也不保证。两者都不是外部ACK。
- 请求计数3经callback变2、1，第三次执行清D8位及若干共用请求位，计数保留1。尚无实际时间基准，也不能把自动清位当作退出DVR的验证。该MCU路径未恢复DVRConfig直接门控，AP侧配置影响仍未知。
- 证据raw/mcu-supplement/tx-enable-agent.md含指令和拒绝条件。重查条件：运行时有效状态/发送记录、版本变化、计时源的新静态证据；不重复位映射。计时与任务使能初始化继续追踪中。

### B15补充：内部时基与暂停边界（表身份已更正）
- 撤回将20409a20表index33的C1/0x50解释为D8发送描述/周期的说法：该28B表由RX缓存/状态处理路径读取，不能因索引同为33就套到TX index33。435d7e直接取2040ae6c发送buffer表，并不读取20409804+218的28B表。独立TX报告已更正；C1不作D8使能依据。
- 439984写的0x14在半字数组2040a5fc+2*index，后续消费者未闭合。内部计数的墙钟单位也未闭合，不换算毫秒。
- 调度gate +f23在4360fc置1、436124清0，436148仅等于1时扫描；停调度不直接清D8，并停止推进post-send清理次数。因此软件自动清位不是任意失败场景下的有保证恢复方案。
- 同报告追加地址与原指令。实际时长保持未知；只有时基调用来源或真实运行事件可触发复查。

## B16：2B1缓存数据与上报状态字节
- 未知：历史全零2B1是否必须由外部CAN实收产生。MCU formatter449dc8/44a518始终从2040aa78[index]缓存复制数据，有效状态为0也不阻止上报。
- 2B1 index20描述+12初值0，构造output byte7时检查*(204099ac)所指状态数组[index]；非零才写1。初始化指针指向2040007a。历史原帧byte7=0，只能说上报时该状态未置位，不能证明曾收到全零CAN，也不能区分未收到、超时或其他清位原因。
- 未闭合缓存启动值、状态置位/清位与外部RX的完整链、上报周期。未把类似计时管理代码直接套到该数组，继续追对象初始化和调用者。既有交接主要保存汇总/单帧，当前检查没有找到可用于有效状态变化分析的完整序列，不将缺少序列当模块离线证据。
- 证据raw/mcu-supplement/rx-validity-agent.md。重查条件：状态数组写入/对象映射的新代码证据、保留完整状态字节的历史序列或实际有效接收事件；重复全零且byte7=0不构成进展。

### B16补充：状态位语义收紧（表身份更正）
- 4366f4中r5=204181e8是计时对象，r6=20409804才是读取+1a8状态指针的对象；现已闭合同一数组关系，先前独立报告中的基址误读已更正。
- 撤回“2B1 row0/12为0，因此维护函数不适用”的推导：那是物理CAN分发表20408bbc，4366e0实际读取20409804+218→20409a20的另一张28B处理表；index20实际为010000000c9048000800640001000000bde14300...，row0=1、row12=1。已保存status-maintenance-audit.txt、buffer-table-audit.txt并要求两个独立报告更正。byte7仍只能保守称软件状态字段，不因表身份修正就自动推断有效/超时。
- 448b28事件3→438618→4366e0为独立维护链；435fd4→435f28会更新缓存/事件但尚未闭合到硬件RX及2B1状态写入。报告上游也未恢复。继续限定范围追硬件RX到缓存的链，不重复读状态。

### B16补充：缓存模板与可证覆盖链
- 463848第二张启动清零表49990c的三段均不覆盖缓存204003c4或状态2040008e；不能仅凭通用启动清零说缓存默认零。证据data-zero-map.json与data-zero-map-notes.md。
- 专用初始化435cb0会按2040a790源指针表、48dea0长度表，将ROM模板复制到2040aa78目的缓存。index20源48e254、length8、目的204003c4，模板为20 00 00 00 00 00 00 00。4360d8在20400079==0时置1并转入初始化；另一436678条件按移位后的N标志解释为入口bit0/bit4等，不是原始bit31/27。不能认定实际历史执行路径，但必须撤回“已证明启动默认全零”的潜在解释。
- 4348f0遍历物理CAN分发表，匹配2B1与DLC8后经row+20回调435fd5→435fd4(index20,frame*)；435fd4需缓存处理gate +eb7==1，从frame取得data指针，经435f28覆盖204003c4。上游42a338/42a408→43480c为frame record处理链，硬件来源正在最后审校；静态分发链不是本轮实收证明。
- 历史缓存全零不同于专用模板，但仍不能在未排除其他清零/覆盖、未证当前运行内容及调用条件时推DVR实体在线；即便有CAN来源，也需区分网关代发和DVR自身。

### B16最终审校：条件式硬件RX链
- 独立报告已重写，撤回表身份混淆和位移位号误读，见rx-validity-agent.md及rx-validity-xref-disasm.txt。
- 42a4e8→46cf88将回调42a339安装到控制器对象+610；46cc7c取得硬件基址（channel0=40304000）并调46f544读取MMIO/FIFO，填80字节record的ID、DLC(+72)、payload(+8)。42a338类别0路径取记录，经43480c→4348f0匹配物理表并调用435fd4→435f28。缓存处理行index20走掩码再比较更新，不能概括为无变换原样透传。
- 结果：固件存在硬件收帧到2B1缓存的条件式路径；不是本轮线上抓帧，也不证明历史样本执行了此路径。软件status与缓存分别上报，状态产品语义、其它缓存写入者和报告调度仍未唯一闭合。
- 本轮实际使用判断：已恢复原厂控制/接收代码，但尚不足确认本车独立DVR在线或实现画面/录像；没有形成具备效果与恢复依据的主动验证。已向用户补问模块料号/位置/线束文字信息，以区分缺入口和硬件未上线；不要求插卡或操作画面。

## B17：CP新组件的定向获取
- 未知：此前只看部署标识的服务之外，是否存在真实DVR配置或画面消费者。启动脚本证明MixtureService、DFSService会启动；ConfigService在该脚本中注释。cm_config列8个IPC deployment、0个SOME/IP deployment，不由此推物理CAN来源。Inventory未找到独立命名DBC/ARXML，不能排除编译内置映射。
- 2026-10-12本机ADB5038重连localhost5555后，用已有EROFS只读工具按清单读取cp_rootfs_b的MixtureService 1,731,224 B、DFSService 1,883,976 B；实际884块3,620,864 B，未读整分区。原read-summary已备份，新增记录read-summary-b17-services.json。槽运行状态仍未证明，沿用B4边界。
- SHA256分别4f9a63dd6a9b9adcffdae1a56cf635da524878538878eb32439e128ea6093288、524560ca6c43cd36884c61902c5272f2ac66956e56c78c5fb591265588721873。二者AArch64且无symtab/gnu_debugdata。Mixture的DVRConfig只恢复初始化注册；DFS有ProjectionScreen protobuf(screen/entry两个int32)及发送日志路径，但未恢复画面API调用或与DVR事件的绑定。未据通用ProxyMethod::Send/NotifyDirect声称有DVR消费者。完整地址、身份和边界见raw/cp-b/cp-new-services.md；该候选核查已收敛。
- 额外选择性读run_camera/cap_display共51,329 B：前者实际调用cap_drm，后者只有通用V4L2/DRM采集显示usage，没有DVR关联证据。未运行工具、未抢占设备，该弱候选关闭。证据raw/cp-b/camera-helper-boundary.md。
- 重查条件：服务实现中新数据流、明确DVR调用者或具体硬件通道映射；不重复常量或通用usage发现。

### AP配置门槛局部检查边界
- 未知为DVRConfig是否阻止AP发D8。查看generic onSetProperty两层入口，未恢复Java配置到D8的消费者链，不据局部无匹配推断配置不起作用。此次无可用于启用的新结果，不计实际使用进展。证据raw/mcu-supplement/ap-config-gate-notes.md；没有具体数据流线索不重复泛扫入口。

## B18：2B1状态维护与AP解释（2026-10-13/14）
- 未知：byte7是接收有效还是缺报，客户端是否把它用作可用性。此前尚未闭合这些函数，沿用其他已完成分支。
- 4366e0在index20 flags1下处理接收事件，门控非零时清status0；无事件且内部计数耗尽，在门控/延迟条件满足时置status1。初装100计数、收帧后重装4计数，不能换算毫秒；不宣称全部写入者已穷举。
- AP响应Builder取wire byte7 bit0，经CanManager/ModuleDataManager传VehicleDataBuilder；普通属性构造将其等于1的布尔值写VehiclePropValue.status。AOSP 2.0定义0 AVAILABLE/1 UNAVAILABLE。0仍不能证明独立DVR在线。
- 门控4388c0对应CAN2FC byte0低2位PEPS_PowerModeValidity、bits2..4 PEPS_PowerMode，AP34843c/3484b0字符串和位提取相符；Validity==1或PowerMode==0令回调0，其余PowerMode值4映3。
- 证据status-semantics-20261013.md、ap-mode-parser-20261014.txt及定点反汇编。重查条件：模式枚举、其他状态写入者、版本/映射变化或可关联接收事件的记录；重复零样本不重查。

## B19：13/02报告触发链审校（2026-10-14）
- 未知：wrapper表48fa4c的实际消费者。因此前TBB分支读错有明确证据缺陷复核。
- TBB448f94字节05 1c 14 2b 34 24 58 3d 46 4f：type5→448fdc，而非+2fc分支。它读取48f9fc[index*4]，index20→48fa4c→44a1c1。43e1bc→45b23a（有门控）→45ad0e→4490c8(type5,index20)→44905c/448f6c→该wrapper→发送工作队列→450074以非零输出回调、模式2调用wrapper→449dc8实际格式化。
- 结果：关闭“表无消费者”的缺口，证明状态事件可以触发缓存报告，不认定每次报告均有新RX或最终UART实发。旧report-trigger-20261013.md已标撤回错误，当前证据report-trigger-20261014.md及三份新asm。
- 重查条件：版本/跳表变化、通信门控的新解释或实际事件/报告序列。不会重复扫描wrapper绝对指针。

## B20：已知模式条件下的有限只读查询与时间戳缺陷（2026-10-14）
- 未知：当前模式是否允许缺报维护，以及客户端读数能否证明新收帧。模式属性首次定位，不重做历史DVR常量普查。
- initPropIDToNameMap w22=29700001，PEPS validity名称项key+148=29700149，PowerMode名称项key+149=2970014a。只读接口静态支持--get。
- 本地ADB5038连接丢失后重连localhost5555成功。dumpsys普通Binder路径找不到HIDL接口，转lshal debug成功，不把首次接口失败当VHAL/DVR缺失。12:41:55两项属性值均2、AVAILABLE；模式回调条件非零，但其他gate未证明。
- 12:42:46只查询一次D3，可用状态AVAILABLE、bytes4全零，没有新的模块在线证据；未轮询。EmulatedVehicleHal::get在eaa2c调用elapsedRealtimeNano并在eaa34写返回timestamp，故不能用查询的新timestamp证明新RX。这是明确证据限制。
- AP存在状态改变时另行遍历信号生成属性的分支（2f1b3c以后），不能仅因数据未变就断言必然压掉状态；具体全部发布门控未穷举，不据本轮单值作结论。
- 证据RESULT_20261014.md、power-mode-property-map-20261014.txt、power-gate-hidl-live-20261014.txt、dvr-availability-conditioned-20261014.txt、property-get-freshness-20261014.txt。重查条件：模式或可用性实际变化、新RX计数/事件、版本/发布门控新代码；相同零值/查询timestamp不重查。

## B21：DVRConfig默认/真实配置来源（2026-10-15）
- 未知：0是否只是解析失败默认、活动实现是否直接读XML。沿用B3已完成的枚举/消费者检查，只补配置来源链。
- getLocalConfig读VHAL0x28700370→40字节配置→String[]→LocalConfigExcel。mDVRConfig默认-1；字段从(byte[23]>>3)&7取得，范围0..7，不是单比特boolean。全0配置才尝试sys.ecarx.local_config替换。
- 本机只读：40字节非全0非全FF，第24字节0x40，DVR bits3..5=0；替换条件不满足。没有重复查询cmd102，排除了这几个失败默认路径作为0的来源，仍未恢复0的产品枚举，不改配置。
- 证据raw/config-source-provenance-20261015.md及config-word-summary-20261015.json列代码偏移、原始输出、边界。重查条件：配置/版本变化、实际消费者或参数反证，不重复读同一配置。
- 下一具体候选：当前HOME解析为com.flyme.auto.launcher/.main.LauncherActivity，APK/system/priv-app/GeelyAutoLauncher/GeelyAutoLauncher.apk；定向查入口是否消费配置102，而非把桌面常量当功能实现。

## B22：缺报维护的延迟装载/递减（2026-10-15）
- 未知：44139c的延迟字段由谁改变；补未闭合分支，不重采零值。
- 字段2040b1ee+4：441390写入、4413a8非零减1。438478在模式回调变化且新值不等于2时装760；44144e只有模式结果2且2041cdc0+0x55为0才递减，其余分支重装760。不能将模式非零解释为超时计数必然运行。
- flag别名2041ce15在4384b0由模式变化到2的分支置1，后续44144e重装一次760并清flag；模式持续2才有后续递减路径。这个写入者已闭合，不再作为未知重复扫描。
- 结果：进一步限制AVAILABLE的在线证明力；没有新增DVR可用证据或控制操作。调度单位、flag的全部来源和真实值未闭合。
- 证据raw/mcu-supplement/delay-gate-provenance-20261015.md及三份定点反汇编。重查条件：flag/调度来源或同序列事件的新证据、版本变化；相同全零/AVAILABLE不重查。

## B23：当前桌面应用过滤与DVR图标候选（2026-10-15）
- 未知：是否已有DVR应用仅被当前HOME按DVRConfig隐藏。定向获取当前桌面classes.dex、resources.arsc、manifest；未启动界面或改列表。
- AppFilter Lu1/c构造器code4834472读取0x7f03000b filtered_components；基础数组8项均非DVR。按x6/c.c返回结果追加的0x7f110086是CarPlay组件，不是DVR。k2/c.a code4228504在加入AllApps前执行过滤谓词。
- 谓词还有安装来源com.carbit.service.display规则（保留com.carbit.virtual.launcher）与动态包名单c；后者来自applist.a.b code2803608查询游戏中心installed_games的packageName。未查询provider或穷举所有桌面展示链，不能由基础数组无DVR推全局无隐藏。
- 基础资源出现com.longhorn.dvr、com.roadrover.geely.cardvr的图标名；定向pm path两个包均无返回路径，不是已安装可直接打开的入口。图标不是实现或可用录像证据。user0标准cmd overlay list无当前HOME目标，未发现该方式覆盖名单；厂商自定义替换不由此穷举排除。
- 重连ADB后按dvr/dash/cardvr/longhorn/roadrover/行车/记录筛选完整pm list，仍无包名命中；第一次因ADB瞬断失败，未把失败当结果。桌面DEX定向配置搜索仅命中Carplay的getCarConfig(224)，未命中DVRConfig/0x28700370/0x297000配置，不能据此证明整个系统无消费者。
- resources.arsc长度3140060与头部声明一致，SHA256 18c075010f410d8f98a7f036fc7350886b8bc458e8e93b240db9b7c8b42bc300。仅资源+manifest重装为离线解析容器；aapt2关于未附带图片/XML的警告不代表原APK缺文件。证据home-resource-table-20261015.txt、home-filter-class-20261015.txt、home-dvr-package-presence-20261015.txt、overlay-list-20261015.txt。
- 结果：没有发现“解除本条过滤即可使用”的DVR入口；两个具体新包候选关闭，不泛扫历史包列表。重查条件：候选包安装/版本变化、实际DVR组件进入过滤链、资源覆盖或新入口消费者；不重复搜图标名字。

## B24：补取CP HMI DWARF并审校DVR命令消费者（2026-10-16）
- 未知：此前HMI partial省略的约35MiB DWARF是否隐藏了MMI_DVR_Command订阅/画面消费者。只读分块补取原CP B槽weston-simple-egl的.debug_info/.debug_str等六个section，共10237块；未运行或写CP。
- 新debug字符串包含ST_MMI_DVR_Command模板通用Subscribe/SetReceiveHandler等名字，但真实符号筛选仍只有HandleEventDVRConfig、ST_DVR_SystemSts::UnsetReceiveHandler、ST_MMI_DVR_Command::UnsetReceiveHandler及DVRConfig的GetNewSamples。没有具体MMI命令GetNewSamples/SetReceiveHandler/Subscribe/HandleEvent实现；通用模板字符串不当调用证据。
- 结果：关闭“关键HMI消费者只藏在缺失DWARF”的假设；仍无MMI_DVR_Command→ProjectionScreen/EVS数据流。保留HMI实现边界，不扩大为CP/整车不存在。
- 证据raw/cp-b/hmi-dvr-debug-refresh-20261016.md及raw/cp-b/hmi-debug-20261016/read-meta.json。重查条件：CP build ID/槽位变化、匹配middleware debug或event binding、实际运行记录联系33033与字段访问；相同二进制不再重读。

## B25：CP middleware DVR交叉引用核验（2026-10-17）
- 未知：此前新取的CP middleware中，是否有除HMI外的实际MMI_DVR事件消费者。只读检查14个已取二进制的DVR类型字符串交叉引用和代表性反汇编，不启动服务、不发SOME/IP、不写CP。
- `MMI_DVR_Command`、`DVR_SystemSts`、`DVRConfig`在有引用的服务中均只落在生成式服务/事件名称表构造、`InstanceIdentifier`/`vector<string>`追加及固定ID写入路径；没有具体`GetNewSamples`、`SetReceiveHandler`或`HandleEventMMI_DVR_Command`实现/独立引用。`Samples`的`SubscribeEvent`是通用初始化日志，ProjectionScreen路径仍是通用日志/错误处理，未接到MMI_DVR。
- 结果：关闭“消费者在相邻middleware二进制中”的假设；注册表存在不等于运行时订阅或DVR可用，仍无画面/录像/回放消费者证据。没有实际使用进展，也没有执行控制或写入。
- 证据raw/cp-b/middleware-dvr-crosscheck-20261017.md、middleware-dvr-xrefs-20261017.txt、middleware-dvr-xref-contexts-20261017.txt、middleware-dvr-string-summary-20261017.txt。重查条件：CP槽位/build变化、匹配未剥离middleware/debug/event binding、或运行记录把33033与实际回调联系起来；同批二进制不再重复扫描。

## B26：HMI DVRConfig处理器定向审查（2026-10-17）
- 未知：HMI唯一具体`HandleEventDVRConfig`是否将配置事件转成画面/媒体动作。对已取得CP B槽HMI在0x1f3fd4及0x1f3d70做定点静态反汇编，不运行、不发事件、不写CP。
- 结果：函数从事件适配器取`unsigned char`样本，辅助函数逐字节调用`LogInfo`/`KSyslog::operator<<`；函数本体只检查取样结果并返回。没有ProjectionScreen、EVS、窗口、视频或媒体调用，也没有DVR命令转发。因此DVRConfig在该HMI原件中是取样/日志路径，不是画面入口。
- 证据raw/cp-b/hmi-dvrconfig-handler-20261017.md及既有raw/cp-b/hmi-dvrconfig-disasm.txt。重查条件：CP槽位/build变化、匹配未剥离实现，或运行记录把配置样本和具体UI/媒体调用联系起来；相同反汇编不重查。

## B27：CP cm_config DVR部署核验（2026-10-17）
- 未知：DVR事件/投影接口是否在CP服务描述层实际部署，而非二进制残留；部署是否已证明客户端消费。只读解析既有CP B槽`cm_config.json`，不启动服务、不写配置、不发SOME/IP。
- 结果：VehicleService默认事件组含`DVR_SystemSts`(32921)和`MMI_DVR_Command`(33033)；MixtureService默认事件组含`DVRConfig`(32854)；IVI/DFS分别部署`ProjectionScreen`方法及响应事件。部署层证据成立，但不证明订阅成功、事件到达、DVR回调调用投影或有录像/回放；结合B25/B26仍无MMI_DVR→ProjectionScreen/EVS数据流。
- 证据raw/cp-b/cm-dvr-deployment-20261017.md、cm-dvr-deployment-extract-20261017.txt。重查条件：槽位/build或部署变化，或运行记录证明33033/32854订阅与回调；相同cm_config不重查。

## B28：车机Download旁路候选核验（2026-10-17）
- 未知：Download是否已有可直接启动/复用的DVR APK。ADB重连后只读列出APK/APKS/XAPK/ZIP/JAR文件名和大小，不安装、不启动、不改文件。
- 结果：目录没有DVR/CardVR/Longhorn/RoadRover/旧Neusoft DVR安装包；只有EVCC、AVM、启动器、只读探针和MCU补充包等。关闭“现成APK旁路”分支。
- 证据raw/device-download-dvr-candidates-20261017.md。重查条件：目录新增明确DVR包、系统包/版本变化或取得匹配厂商APK；同目录不重复列出。

## B29：公开DVR材料与Wi‑Fi路径核验（2026-10-17）
- 未知：是否存在可复用的公开DVR包，以及当前车机无线状态是否能直接观察独立记录仪热点。公开资料定向搜索并读取 App Store / APK 索引；车机侧只读查看 Wi‑Fi 状态并尝试一次扫描，不安装、不写系统、不发送DVR控制帧。
- 找到《领克行车记录仪》和《领克DVR》手机端资料：均描述Wi‑Fi连接独立记录仪、视频回放/设置或实时画面；未找到与当前Flyme Auto车机匹配的Android原厂包。另有 `com.bvc.geely.camera`（Geely GO）Android候选，要求Android 7.0+，同样依赖Wi‑Fi DVR，不能据此认定是本车原厂消费者。
- 车机当前处于SoftAP（SSID `Sakiko 的领克 06`、2客户端），station `UNINITIALIZED`，`mPersistWifiState=0`；扫描结果为空且计数为0。因此“没扫描到DVR热点”是受路径限制的无效阴性，不能当作缺件证据。
- 结果：新增可追踪候选包名，但不计实际使用进展；继续优先寻找同配置 donor 包/模块料号。重查条件：出现匹配DVR热点、车机进入station扫描、取得车辆系统包/模块料号或APK版本变化；同一SoftAP空扫描不重查。
- 证据：`raw/public-dvr-material-search-20261017.md`。

## B30：隐藏/已卸载DVR包名补漏（2026-10-17）
- 未知：原厂DVR是否仅被禁用或对当前用户卸载。只读执行 `pm list packages -u -f` 并按已知DVR/供应商关键词筛选，不改包状态、不启动、不安装。
- 结果：无 `dvr`、`cardvr`、`longhorn`、`roadrover`、`neusoft`、`dash`、`行车`、`记录` 匹配输出；关闭同名隐藏/卸载包窄分支。仍不排除无明显命名的系统内置实现或配置缺失。
- 证据：`raw/hidden-package-dvr-check-20261017.md`。重查条件：系统/包清单变化、donor包或具体组件名出现；相同清单不重查。
