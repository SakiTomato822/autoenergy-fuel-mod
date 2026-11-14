# v1.9.10 APK 离线核对

未安装、未执行新APK。com.dashcam.viewer versionCode82，versionName1.9.10。

- Sink::deliverFrame 0x29b3c：0x29b60/6c 仍只接收1280×3200；不匹配在更新时间戳前返回。没有适配本车1920×896合成输入。
- frame watchdog 0x142d0：5秒检查；有效帧超时约20秒，可 setprop ctl.restart vendor.evs-hal-mock；最短重启间隔约180秒。这是静态可达行为，不等于已在本车新版本复现；启动若提前失败也不能直接套用此链。
- proxy DirectBackend::acquireCamera 0x13300 仍先返回缓存对象，没有按请求ID隔离。
- Installer 仍先向原路径写入proxy/daemon/rc，再执行stop；未修复旧版写入时序风险。

未知：新版本是否修复本车尺寸/相机兼容问题。结果：核心尺寸要求未改变，不进行重复安装来验证已知拒收。重查：尺寸适配、相机对象隔离、安装时序修复或可关联的新运行证据。

资产SHA256：daemon 7556e05d5d717d32f838fc574c541e6f6644454199b8ded1f1ca91c8ab98dcaf；proxy c05819ea08bed6a4be1cc3030898cb40c250a9974b11b874ca52c766602985b3。证据为 evidence/ 内三个定向反汇编文件。
