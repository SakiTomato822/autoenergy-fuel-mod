import android.app.Activity;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Local experimental installer. It stages files; activation requires a user reboot. */
public final class FixInstaller {
    static final String RC = "/vendor/etc/init/android.hardware.automotive.evs@1.0-service.rc";
    static final String LIB = "/vendor/lib64/libevsproxy_preload.so";
    static final String DAEMON = "/vendor/bin/dashcamd";
    static final String BACKUP = "/vendor/etc/dashcam-localfix-original.rc";
    static final String RC_TEMP = "/vendor/etc/dashcam-localfix-service.tmp";
    static final String MARK = "DASHCAM_LOCALFIX_OK";
    private static void ui(Activity a, TextView v, String s) {
        a.runOnUiThread(() -> v.setText(s));
    }
    static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
    static void checked(c bridge, String command) throws Exception {
        String result = bridge.c("(" + command + ") && echo " + MARK);
        if (!result.trim().endsWith(MARK)) throw new Exception("命令未完成：" + result);
    }
    static String strip(String rc) {
        return rc.replaceAll("(?m)^[ \\t]*setenv LD_PRELOAD /vendor/lib64/libevsproxy_preload\\.so[ \\t]*\\r?\\n?", "");
    }
    static String configure(String rc, boolean enable) throws Exception {
        rc = strip(rc);
        if (rc.matches("(?s).*setenv[ \\t]+LD_PRELOAD.*"))
            throw new Exception("发现其他 LD_PRELOAD 设置，请先核对，未写入");
        String service = "service vendor.evs-hal-mock /vendor/bin/hw/android.hardware.automotive.evs@1.0-service";
        int at = rc.indexOf(service);
        if (at < 0 || rc.indexOf(service, at + 1) >= 0)
            throw new Exception("EVS 服务配置与本补丁不匹配，未写入");
        if (enable) {
            int end = rc.indexOf('\n', at);
            if (end < 0) throw new Exception("EVS 配置缺少换行，未写入");
            rc = rc.substring(0, end + 1) + "    setenv LD_PRELOAD " + LIB + "\n" + rc.substring(end + 1);
            if (!rc.matches("(?s).*\\bDAC_OVERRIDE\\b.*")) {
                if (!rc.matches("(?s).*\\n[ \\t]*capabilities[ \\t]+.*"))
                    throw new Exception("缺少已知 capabilities 配置，未写入");
                rc = rc.replaceFirst("(?m)^([ \\t]*capabilities[ \\t]+[^\\r\\n]+)", "$1 DAC_OVERRIDE");
            }
        }
        return rc;
    }
    static byte[] asset(Activity a, String name) throws Exception {
        try (InputStream in = a.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toByteArray();
        }
    }
    static String sha(byte[] bytes) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte v : MessageDigest.getInstance("SHA-256").digest(bytes)) s.append(String.format("%02x", v & 255));
        return s.toString();
    }
    static void upload(c bridge, String temp, byte[] data) throws Exception {
        bridge.e(temp, data);
        // The original bridge's file-transfer CLSE exchange can leave packets
        // queued for the next shell channel. A fresh transport cannot consume
        // the previous channel's acknowledgement as an empty command result.
        bridge.a();
        bridge.b();
        String digest = sha(data);
        for (int attempt = 0; attempt < 20; attempt++) {
            String result = bridge.c("sha256sum " + q(temp)).trim();
            if (result.startsWith(digest + " ")) return;
            Thread.sleep(100);
        }
        throw new Exception("上传文件哈希不符，未替换目标：" + temp);
    }
    static void stage(c bridge, String target, byte[] data, String mode) throws Exception {
        String temp = target.equals(RC) ? RC_TEMP : target + ".localfix.tmp";
        String context = bridge.c("ls -Zd " + q(target)).trim().split("\\s+")[0];
        if (!context.matches("u:object_r:[a-zA-Z0-9_]+:s0")) throw new Exception("无法读取原文件安全上下文，未替换");
        upload(bridge, temp, data);
        checked(bridge, "test \"$(sha256sum " + q(temp) + " | cut -d ' ' -f 1)\" = " + q(sha(data))
            + " && chmod " + mode + " " + q(temp) + " && chown 0:0 " + q(temp)
            + " && chcon " + q(context) + " " + q(temp));
    }
    static void stageNew(c bridge, String target, byte[] data, String mode) throws Exception {
        String temp = target + ".localfix.tmp";
        upload(bridge, temp, data);
        checked(bridge, "test \"$(sha256sum " + q(temp) + " | cut -d ' ' -f 1)\" = " + q(sha(data))
            + " && chmod " + mode + " " + q(temp) + " && chown 0:0 " + q(temp)
            + " && chcon u:object_r:vendor_file:s0 " + q(temp));
    }
    public static void status(Activity a, TextView v) {
        c bridge = new c();
        try {
            bridge.b();
            String result = bridge.c("if ! test -c /dev/video6cif || ! test -d /sys/class/video4linux/video6cif; then echo NO_V6; elif grep -q 'setenv LD_PRELOAD /vendor/lib64/libevsproxy_preload.so' " + q(RC) + "; then echo CONFIGURED; else echo DISABLED; fi").trim();
            ui(a, v, result.equals("NO_V6") ? "不兼容：未注册 video6cif，无法四路录像。"
                : result.equals("CONFIGURED") ? "开机注入已配置；尚未验证有效录像。"
                : result.equals("DISABLED") ? "开机注入已关闭；当前加载的库在重启后退出。" : "无法确认状态，请检查本机 ADB。");
        } catch (Exception e) { ui(a, v, "状态查询失败：" + e.getMessage()); }
        finally { bridge.a(); }
    }
    public static void run(Activity a, boolean enable, TextView v) {
        c bridge = new c();
        boolean locked = false;
        try {
            ui(a, v, "本地修复实验版：检查车机配置…");
            bridge.b();
            if (!bridge.c("id").contains("uid=0")) throw new Exception("ADB 未取得 root，未写入");
            if (enable && !bridge.c("if test -c /dev/video6cif && test -d /sys/class/video4linux/video6cif; then echo V6_REGISTERED; fi").trim().equals("V6_REGISTERED")) {
                ui(a, v, "无法启用四路录像：本机未注册 video6cif 原始图接口。video12cif 是合成 360 画面，不能替代。此次未写入系统；如装过实验注入，请先恢复，再手动重启车机。");
                android.util.Log.w("DashCamLocalFix", "compatibility rejected: video6cif device and sysfs registration required; no system writes");
                return;
            }
            checked(bridge, "mkdir /data/local/tmp/dashcam-localfix-install.lock");
            locked = true;
            String original = bridge.c("cat " + q(RC));
            if (!enable) {
                String backup = bridge.c("cat " + q(BACKUP) + " 2>/dev/null || cat " + q(RC + ".localfix.backup"));
                if (backup.contains("service vendor.evs-hal-mock") && !backup.contains("LD_PRELOAD")) original = backup;
                else throw new Exception("原厂备份缺失或包含注入，请先人工核对，未替换");
            }
            String desired = configure(original, enable);
            checked(bridge, "mount -o remount,rw /vendor");
            checked(bridge, "if test -f " + q(RC + ".localfix.backup") + "; then test -f " + q(BACKUP)
                + " || mv " + q(RC + ".localfix.backup") + " " + q(BACKUP) + "; fi; test -f " + q(BACKUP)
                + " || cp -p " + q(RC) + " " + q(BACKUP));
            if (enable) {
                ui(a, v, "校验并暂存补丁文件…");
                stageNew(bridge, LIB, asset(a, "libevsproxy_preload.so"), "644");
                stageNew(bridge, DAEMON, asset(a, "dashcamd"), "755");
            }
            stage(bridge, RC, desired.getBytes(StandardCharsets.UTF_8), "644");
            if (enable) {
                checked(bridge, "mv -f " + q(LIB + ".localfix.tmp") + " " + q(LIB)
                    + " && mv -f " + q(DAEMON + ".localfix.tmp") + " " + q(DAEMON)
                    + " && mv -f " + q(RC_TEMP) + " " + q(RC) + " && sync");
                ui(a, v, "补丁已暂存。请停车后手动重启车机，再验证 360 和录像；当前尚未验证录制成功。");
            } else {
                checked(bridge, "mv -f " + q(RC_TEMP) + " " + q(RC) + " && sync");
                ui(a, v, "已移除开机注入。请手动重启车机完成恢复；本次保留运行中的库文件和录像。");
            }
        } catch (Exception e) {
            android.util.Log.e("DashCamLocalFix", "installer failed", e);
            ui(a, v, "补丁未完成：" + e.getMessage());
        } finally {
            if (locked) try { bridge.c("rmdir /data/local/tmp/dashcam-localfix-install.lock"); } catch (Exception ignored) {}
            bridge.a();
        }
    }
}
