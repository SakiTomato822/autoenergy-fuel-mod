# CP摄像头工具候选边界

未知：CP是否另有现成的DVR视频选择消费者。2026-10-12从cp_rootfs_b选择性读取run_camera(145 B)与se_v4l2_cap_display(51,184 B)，不执行二者。

run_camera脚本先写/proc/sys/kernel/printk，再执行se_v4l2_cap_drm -c 15。它调用的是cap_drm，不是本次读取的cap_display；两者不得混同。脚本不会在本调查中运行。

cap_display为AArch64 ELF，已剥离，无可用符号表。内置usage定义-c为视频设备位图、-w/-h尺寸、-d为DRM card、-s为mdp /dev/switch编号、-f保存文件；示例是video0/1/2/3采集播放。字符串含/dev/video%dcif与通用V4L2 ioctl/DRM错误。没有从这些usage/名字恢复到DVR模块选择、CAN请求或原厂DVR录制协议；不能把通用采集工具的-f当原厂DVR已实现。

结果：只确认有通用采集显示候选，不形成DVR使用证据。start.sh未启动这些工具，运行状态未检查；不为此开启画面或抢占设备。若后续出现明确调用者/硬件通道映射再追cap_drm或其选择代码，当前关闭此弱候选。

SHA256：run_camera=3d995f72bccaf4745ad694b0c212f2de07468acf9f2f1916684f718ca8e45c64；cap_display=c897a9534ec377ad83b80c9be84af1bf30cc1107809a12c81797baae0a7dbbe0。源槽运行状态沿用B4的不确定性。反汇编自动寻找debuglink时出现CRC不匹配告警，不使用该debuglink作证据。

重查条件：本车DVR视频通道的具体映射、有效调用者或工具/固件版本变化。没有这些条件不重复查看usage。
