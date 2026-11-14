# 四路记录仪本地实验

实际结论和每项复查条件见 [RESULT.md](RESULT.md)。本车外置AVM/HW1.5配置没有当前可用的video6cif原始四路接口，尚未实现录像。v3是安装流程修复及兼容性检查版，不是本车可用DVR成品。

`release/dashcam-localfix3.apk` 与原应用并存，包名com.dashcam.fixv11；签名为本地新证书，完整性校验改为绑定该证书、二维码校验保留。不会覆盖原应用数据。不要再用原版安装按钮原位覆盖活跃库。

v3安装前同时检查字符设备和sysfs注册；缺v6直接拒绝，未写入vendor。即使v6存在也只是必要条件，不能保证硬件、格式、能力和四路录像全部兼容。状态页不再用“文件存在”声称已启用/在录制。正常安装仍需手动整机重启激活；不由安装器自动重启服务。恢复保留运行中的库文件与录像，不删除映射文件，下次正常启动退出注入。

## 复建

在车机原生Termux环境，需Java、aapt2、GNU aarch64 as/ld.bfd/binutils、Node；Android SDK platform android-34及build-tools34.0.0。默认SDK为`$HOME/.build-tools/android-sdk`，可用`DVR_ANDROID_SDK`指定。原APK/资产在同仓库`../flyme-dvr-20261010/raw/dashcam-viewer-20261114`，有原件SHA校验。

先运行`sh src/fetch-tools.sh`取得固定版本smali依赖并验证SHA256。在out中自行生成PKCS12签名私钥、口令文件和DER公钥证书（别名localfix，文件名分别localfix-signing.p12、localfix-signing.pass、localfix-cert.der）；私钥/口令不在仓库。随后`node src/build.cjs`。构建会验证签名并运行aapt2；证书不同会相应改变APK SHA及签名绑定，不能复现发布包逐字节哈希。

## 已执行验证

- 两个AArch64独立测试通过：代理open/close分派、x8返回指针/栈；60次重试后有界失败和强引用重建。
- v2在车机实际加载并抓到apa真实帧，0字节录制已确认由尺寸不匹配导致；没有伪造四路输出。
- v3实际安装，启用前缺v6触发拒绝，vendor rc/库/daemon SHA256前后相同。
- 14:15恢复原厂开机rc并停止无产出recorder；不停止当前EVS/360进程。保留的daemon僵尸不能算正在录像。

evidence中的v2实验APK仅用于归档复核，不能当推荐版本。完整原始启动日志、画面帧、签名私钥留在车机本地，不随git发布；发布精选日志、哈希和复现观察脚本。

用户后续要求的“强行开启v6”审查见[FORCE_V6.md](FORCE_V6.md)：包括现成overlay入口限制、临时模块方向、车型初始化门控、GPIO副作用、实际格式表、公开SDK差异，以及经已有i2c-31进行一次已知ID寄存器读取的ENXIO结果。它仍未证明原始四路输入可用。
