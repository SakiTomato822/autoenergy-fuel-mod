package com.lynk.dvrprobe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A permission check never invokes su. Only the explicit confirmation path does. */
internal class FuelPermissionGate(
    private val activity: AppCompatActivity,
    private val onReadAllowed: () -> Unit,
) : AutoCloseable {
    companion object {
        const val EXTRA_CHECK = "com.lynk.autoenergyfuel.CHECK_PERMISSIONS"
        private val runtimePermissions = arrayOf(
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_SPEED",
        )
    }

    private var dialog: AlertDialog? = null
    private var busy = false
    private val userId = DumpPermissionCommand.userIdFromUid(Process.myUid())
    private val command get() = DumpPermissionCommand.shell(activity.packageName, userId)
    private val adbCommand get() = DumpPermissionCommand.adb(activity.packageName, userId)
    private val runtimeRequest = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        AppLog.i("PERMISSION", "runtime car result=$result")
        if (busy && !activity.isFinishing && !activity.isDestroyed) grantDumpAfterConsent()
    }

    private fun granted(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun check(promptWhenMissing: Boolean) {
        if (activity.isFinishing || activity.isDestroyed || busy) return
        val dump = granted(DumpPermissionCommand.PERMISSION)
        AppLog.i("PERMISSION", "check package=${activity.packageName} user=$userId DUMP=$dump " +
            runtimePermissions.joinToString { "$it=${granted(it)}" })
        if (dump) {
            dialog?.dismiss()
            onReadAllowed()
            return
        }
        if (!promptWhenMissing || dialog != null) return
        show(AlertDialog.Builder(activity)
            .setTitle("需要车况读取权限")
            .setMessage("尚未获得 DUMP 权限，油量、续航及里程可能无法读取。\n\n" +
                "点击“确认授权”后，先申请系统支持的车辆基础读取权限，再通过车机已有的 Root 尝试执行：\n\n" +
                "$command\n\n" +
                "只为当前应用授予 DUMP；不会安装 Root。没有可用 Root 时，请使用 ADB 命令授权。")
            .setPositiveButton("确认授权") { _, _ -> requestRuntimeAfterConsent() }
            .setNeutralButton("查看 ADB 命令") { _, _ -> showAdbHelp("可在已连接车机的 ADB 终端执行以下命令。") }
            .setNegativeButton("暂不授权") { _, _ -> readLimited() }
            .setCancelable(false)
            .create())
    }

    private fun requestRuntimeAfterConsent() {
        busy = true
        val missing = runtimePermissions.filterNot(::granted)
        if (missing.isEmpty()) grantDumpAfterConsent()
        else runCatching { runtimeRequest.launch(missing.toTypedArray()) }.onFailure {
            AppLog.w("PERMISSION", "runtime request unavailable: ${it.javaClass.simpleName}")
            // DUMP and normal car runtime permissions are independent.
            grantDumpAfterConsent()
        }
    }

    private fun grantDumpAfterConsent() {
        if (granted(DumpPermissionCommand.PERMISSION)) {
            busy = false
            onReadAllowed()
            return
        }
        val progress = AlertDialog.Builder(activity)
            .setTitle("正在申请读取权限")
            .setMessage("如系统弹出 Root 请求，请确认。应用会在完成后重新检测权限，最长等待约 15 秒。")
            .setCancelable(false)
            .create()
        show(progress)
        activity.lifecycleScope.launch {
            try {
                AppLog.i("PERMISSION", "explicit user consent; executing fixed grant: $command")
                val result = withContext(Dispatchers.IO) {
                    try { RootShell.run(command, 15_000) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        ShellResult(-3, "", "${error.javaClass.simpleName}: ${error.message}")
                    }
                }
                val verified = granted(DumpPermissionCommand.PERMISSION)
                AppLog.i("PERMISSION", "grant exit=${result.exitCode} timeout=${result.timedOut} verified=$verified " +
                    "stderr=${result.stderr.take(1000)} stdout=${result.stdout.take(1000)}")
                progress.dismiss()
                busy = false
                if (activity.isFinishing || activity.isDestroyed) return@launch
                if (verified) {
                    Toast.makeText(activity, "读取权限已确认，开始采集", Toast.LENGTH_LONG).show()
                    onReadAllowed()
                } else {
                    val reason = when {
                        result.timedOut -> "授权等待超时，DUMP 仍未授予。"
                        result.exitCode == -2 -> "车机没有可用的 Root 通道，应用无法自行授予 DUMP。"
                        result.exitCode != 0 -> "系统拒绝了授权或 Root 不可用，DUMP 仍未授予。"
                        else -> "命令已结束，但系统检测到 DUMP 仍未授予。"
                    }
                    showAdbHelp(reason)
                }
            } finally {
                busy = false
                progress.dismiss()
            }
        }
    }

    private fun showAdbHelp(reason: String) {
        show(AlertDialog.Builder(activity)
            .setTitle("通过 ADB 授权")
            .setMessage("$reason\n\n$adbCommand\n\n" +
                "此命令需要从 ADB shell 执行；普通 Termux/App 进程无法自行授予。执行后点击“重新检测”。")
            .setNeutralButton("复制命令", null)
            .setPositiveButton("重新检测", null)
            .setNegativeButton("暂不授权") { _, _ -> readLimited() }
            .setCancelable(false)
            .create(), adbHelp = true)
    }

    private fun show(value: AlertDialog, adbHelp: Boolean = false) {
        dialog?.dismiss()
        dialog = value
        value.setOnDismissListener { if (dialog === value) dialog = null }
        value.show()
        if (adbHelp) {
            value.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("车况读取授权", adbCommand))
                Toast.makeText(activity, "ADB 命令已复制", Toast.LENGTH_SHORT).show()
            }
            value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (granted(DumpPermissionCommand.PERMISSION)) {
                    value.dismiss()
                    onReadAllowed()
                    Toast.makeText(activity, "读取权限已确认", Toast.LENGTH_SHORT).show()
                } else {
                    AppLog.w("PERMISSION", "manual recheck: DUMP still missing")
                    Toast.makeText(activity, "DUMP 仍未授予，请确认命令已在 ADB shell 执行", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readLimited() {
        AppLog.w("PERMISSION", "DUMP not granted; continuing with available read-only sources")
        Toast.makeText(activity, "仅读取可用数据；可在诊断页重新检测权限", Toast.LENGTH_LONG).show()
        onReadAllowed()
    }

    override fun close() {
        dialog?.dismiss()
        dialog = null
    }
}
