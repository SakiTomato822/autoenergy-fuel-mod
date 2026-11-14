# v1.9.10 diag 静态审查

**范围：**逐项阅读 `diag/` 下两个说明文件、两个入口脚本、三个车机脚本和 `analyze.py`；未执行任何脚本，也未连接车机。`.DS_Store` 是随包附带的二进制 Finder 目录元数据，与采集逻辑无关。

**结论：**这套包能收集一部分运行时线索，但不适合对本车原样照跑。它会写入车机临时目录、启动并停止 logcat，还把 360、APA、挂 R 挡和记录仪预览列为操作步骤；本轮不需要这些操作。更关键的是，新 APK 的已知行为会让一次“无有效帧”变成 EVS mock 服务重启，采集结果可能把软件尺寸不兼容造成的重启误读成硬件或跨 cameraId 故障。现有的节点、格式和 SBL 证据已经回答了大部分静态硬件问题；diag 的潜在增量主要是某个新版本在某次运行中的进程、cameraId、FD 和服务日志。

## 1. 脚本行为与只读承诺

- `capture.command` 和 `capture.bat` 都启动本机 ADB server、尝试 `adb root`，在车机 `/data/local/tmp/diag25/` 创建目录，推送三个 `.sh` 并 chmod，然后启动采集（Unix：`capture.command:32–68, 97–100`；Windows：`capture.bat:24–53, 94–99`）。因此它不改 `/vendor` 或安装应用，但并非严格的无写入/无状态变化采集：会写远端文件并启动 logcat；`adb root` 还会尝试重启 adbd。Unix 的 `--check` 仍先执行 root 流程（`capture.command:47–59`），Windows 入口没有 `--check` 分支。
- `setup.sh` 采集完整 `getprop`、cmdline、dmesg、服务 rc/VINTF、进程、相机 FD、界面 dump 和应用配置，并在后台启动 logcat（`device/setup.sh:21–28, 33–70, 75–109, 116–127`）。它会把 `settings.conf`、`settings_app.conf` 内容写入包（`setup.sh:92–99`）；完整属性、命令行、进程参数、日志和截图也可能含设备标识或个人信息（截图见 `snap.sh:37`）。README 的隐私说明只点出序列号、Wi-Fi 和截图，范围偏窄（`README.md:70–74`）。
- 每一步在用户按回车后才写 `DashDiag: STEP`、记录时间和界面状态、枚举进程/FD、截屏（`snap.sh:12–27, 29–48`）。入口要求打开 360、APA、挂 R 挡、打开记录仪预览（`capture.command:112–120`; `capture.bat:100–108`）。脚本没有自动挂挡或启动 APA，但这些人工步骤会改变车辆/相机运行状态，不能归为只读观察。
- `finish.sh` 会结束记录器启动的 logcat PID，再用 `pkill -f` 按命令行模式兜底；后者可能匹配并结束同参数的其他 logcat 进程（`finish.sh:11–20`）。这三个车机脚本没有直接停止/杀掉 camera、EVS 或 `dashcamd` 进程的命令；显式停止的是 logcat。随后脚本读取 logcat/dmesg 尾部并统计诊断目录大小（`finish.sh:19–30`）。脚本没有清空 logcat/dmesg（没有 `logcat -c` 或 `dmesg -c`），也没有重启车机；README 里的重启只是建议（`README.md:76–80`; 入口提示见 `capture.command:147–150`）。但打开预览可能触发新 APK 自身的 watchdog 重启 EVS mock。若中途退出，入口没有保证运行 `finish.sh`，远端文件和实时 logcat 可能继续留存。

## 2 分析器的误判边界

- **尺寸结论不是 V4L2 实测。**分析器只从特定格式的 logcat 文本 `frame #N WxH` 提取尺寸（`analyze.py:21, 79–81`），没有读取 V4L2 format/像素格式或把尺寸关联到 `/dev/video*`。只要日志里出现非 `1280x3200` 的尺寸，就会给出“dashcamd 会静默丢帧”结论（`analyze.py:280–282`）。本车已知 `/dev/video12cif` 是 `1920x896 YUYV` 的完整 360 合成 UI，不能把该尺寸当作四路原始画面或传感器链路状态。结合新 APK 静态证据——`Sink::deliverFrame` 在更新最后有效帧时间前拒绝非 `1280x3200`，watchdog 约 20 秒后可设置 `ctl.restart vendor.evs-hal-mock`——尺寸不兼容确实可能造成应用无帧并重启 EVS mock；这支持软件/输入格式不匹配的解释，不能单凭它推断 MAX/CSI2 或 AVM 硬件故障。diag 的日志分类只认 `no frames for Ns, restarting evs hal` 文本（`analyze.py:25, 61–63`），未必能识别属性触发的服务重启及其因果。
- **跨 ID 判定没有事件关联。**只要整段日志出现至少两个不同的 open ID，且任意时刻存在无帧、忽略 start、start 失败或 `Killing previous camera`，就判定跨 cameraId 抢占（`analyze.py:232–269`）。它没有验证这些事件是否来自同一进程、是否同时发生、哪个 ID 没有帧；文字中的“同时/先后”“底层只有一个厂商相机”也不是该判定实际证明的事实。EVS 服务被 APK watchdog 重启后出现的日志可能被这条规则错误归因。
- **步骤对齐方向不对。**用户完成一个操作并按回车后，脚本才打步骤标记和时间（`capture.command:102–109`; `snap.sh:12–16`）；分析器却只找该时间之后 60 秒的事件（`analyze.py:200–228`）。发生在打开预览/APA期间、按回车之前的失败可能被对齐到上一个步骤或显示成“无事件”，所以不能用该栏证明某个步骤没有触发故障。完整时间线仍可人工查看，但没有对应事件就不代表没有发生。
- **“零字节录像”不在分析器判定范围内。**`setup.sh` 会对 `/data/media/0/DashCam` 做一次顶层 `ls -l`（`setup.sh:92–99`），可能顺带列出直接子项的大小，但不递归检查录像、不关联录制步骤/时间；`finish.sh` 的 `size.txt` 统计的是诊断目录（`finish.sh:24–30`），不是行车记录仪输出。分析器不读取 `dashcam_state.txt` 或录像文件。它的“没有帧日志”只表示没匹配到 `frame #N WxH` 日志，不能证明录像为零字节，也不能区分没有日志、日志级别/格式不同和真正没有帧（`analyze.py:246–250, 283–288`）。
- **代理状态可能是假阴性；明确 open 失败也可能被漏掉。**`snap.sh` 只查一个硬编码 EVS PID 名称；查不到 PID 时不会写 `evs proxy loaded` 行，maps 不可读也可能得到 0（`snap.sh:20–24`）。分析器却把匹配到的第一个数字 0 解释成“未加载”（`analyze.py:290–295`）。此外 `open_fail` 被计数但没有参与判定分支（`analyze.py:237–244, 262–288`）。
- 分析器实际读取 logcat 文件、`steps.txt` 和 `owner_notes.txt`；对 `props.txt` 仅检查是否存在，完全不读 `fd_video_*`（`analyze.py:298–345`）。README 所称分析需要这些文件，更像给人工复核的建议，不是该自动判定器的输入（`README.md:70–74`）。

## 3 本车证据的复用与新增价值

直接复用既有结果：HW1.5 外置 AVM；原厂 360 正常；`/dev/video12cif` 输出完整 `1920x896 YUYV` 360 合成 UI；`/dev/video6cif` 未注册；SBL 按 `AVM_data83/HW15` 主动禁用 MAX/CSI2；旧四路程序只接受 `1280x3200` 四路原图并逐行分离。反复打开原厂 360、重复静态节点检查或重跑不产生新状态的步骤，不会回答新的未知。

diag 真正可能新增的是**特定 APK 版本的一次运行态证据**：当时的 app/`dashcamd` PID 和命令行、谁持有哪个 video FD、EVS PID/maps、cameraId 的 open/start/fail 时间线、服务是否重启、当前应用包版本。`setup.sh` / `snap.sh` 分别采集其中一部分（`setup.sh:75–87, 104–109`; `snap.sh:14–48`）。但它没有采实际帧、V4L2 格式或录像文件，因此无法单独验证四路原始流或零字节录像。

## 4 最小增量建议

当前已知静态证据和新 APK 代码足以解释“输入不是四路 `1280x3200` 原图时应用收不到有效帧并触发 EVS mock watchdog”。目前没有必要照跑 3–5 分钟整套流程，也不需要重查原厂 360、挂 R 挡或触发 APA。若以后确有未解的新问题，只在车辆停稳、P 挡时做**一份有明确问题指向的本地状态快照**：应用包版本/路径、当前服务状态、app 与 `dashcamd` PID、`/proc/<pid>/fd` 指向、video 节点名；需要日志时只取现有缓冲区中与该事件相邻的片段，不清日志、不截图、不上传完整属性/用户配置。既有证据未变化时不重做静态采集。

如果必须验证新 APK 的运行态，先把目标限定为“是否出现尺寸拒收及 `vendor.evs-hal-mock` 重启”，并把该重启视为应用 watchdog 的可预期副作用；本轮没有必要执行该复现。不能把 watchdog 重启当作传感器/链路故障证据；后续实验沿用用户既有的本地调查授权，以实际需要及当前停车条件确定范围。
