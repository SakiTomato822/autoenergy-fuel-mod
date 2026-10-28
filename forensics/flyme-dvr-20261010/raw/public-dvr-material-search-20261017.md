# 公共DVR材料与车机Wi‑Fi路径核验（2026-10-17）

## 解决的未知问题

确认是否能找到可直接复用的原厂DVR APK/包名，并区分“车机内置DVR消费者”与“独立Wi‑Fi记录仪手机端”。同时检查车机当前Wi‑Fi状态能否观察记录仪热点。

## 结果

1. 本地车机 `Download` 已在 B28 定向列举，没有 DVR/CardVR/Longhorn/RoadRover/旧 Neusoft 安装包；本机已安装包也没有这些候选。该分支没有现成可启动包。
2. 公开资料找到两个不同层级的候选：
   - App Store 的《领克行车记录仪》由浙江浩物网络科技有限公司发布，说明是通过 Wi‑Fi 连接记录仪、回放视频和设置记录仪；平台页明确是 iPhone 应用，不是车机 Android APK。页面还把《领克DVR》列为相关应用，后者同样描述连接车载记录仪、查看实时画面和文件。证据：[领克行车记录仪 App Store](https://apps.apple.com/tw/app/%E9%A2%86%E5%85%8B%E8%A1%8C%E8%BD%A6%E8%AE%B0%E5%BD%95%E4%BB%AA/id1582932738)、[领克DVR App Store](https://apps.apple.com/pt/app/%E9%A2%86%E5%85%8Bdvr/id6448488331)。
   - `com.bvc.geely.camera`（Geely GO）可在第三方 APK 索引中找到 Android 包，要求 Android 7.0+，说明为通过 Wi‑Fi 管理 Geely DVR；索引给出的 5.48 SHA-256 为 `caafda25468225f0c7e64c895f559758341f5598126d00f0c1946ec928337c64`。这是独立记录仪控制器的通用候选，不是已证明适配本车 Flyme Auto，也不是原厂车机画面消费者。证据：[APKPure 包信息](https://apkpure.net/geely-go/com.bvc.geely.camera)。未下载、未安装、未把第三方 APK 当作原厂实现。
3. 车机 Wi‑Fi 只读状态：当前为 SoftAP 模式（SSID `Sakiko 的领克 06`，2 个客户端），station 状态 `UNINITIALIZED`，`mPersistWifiState=0`；主动扫描返回 `No scan results`，`numScans=0`。因此本次不能用“没看到 DVR SSID”推断记录仪不存在：当前无线状态根本没有完成可用的周边扫描。
4. 公开车型资料要分开看：第三方 06 EM‑P 配置表把 2024/2025 EM‑P 多个版本的“内置行车记录仪”列为无或选装；领克官方 06 配置 PDF 是另一组燃油 06 车型，写的是“行车记录仪 USB 接口”，不能拿来证明 EM‑P 内置 DVR。这个组合更支持“可接独立记录仪附件/接口”，不支持“车机必有内置录制应用”。第三方页面：[EV视界 06 EM‑P 配置](https://www.evlook.com/lingke06xinnengyuan/peizhi)；官方页面：[领克 06 配置 PDF](https://dm30webimages.lynkco.com.cn/LynkCoPortal/Files/Cars/06/0922/0917pz.pdf)。

## 边界与下一步

当前最像的解释是：公开存在独立记录仪及其 Wi‑Fi 手机端，但没有证据表明本车 EM-P 的原厂 DVR 车机消费者已安装或已接入；CP/AP 中保留的 DVR 协议接口仍不能替代画面/录像证据。

只有在取得同年份同配置车辆的系统包/模块料号、车机进入 station 扫描状态、或确认车内独立记录仪上电并广播热点时，才值得继续下载并做 `com.bvc.geely.camera` 等候选的签名/协议兼容性检查。若出现明确 DVR 热点，下一次只做 Wi‑Fi/协议和 APK 兼容性验证，不直接安装到系统分区。
