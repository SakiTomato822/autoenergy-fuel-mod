package com.lynk.dvrprobe

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val LOG_TAG = "AutoEnergyFuel"
    private const val LOG_FILE_NAME = "autoenergy.log"
    private const val MAX_LOG_BYTES = 768L * 1024L
    private const val BACKUP_COUNT = 2

    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            val directory = context.applicationContext
                .getExternalFilesDir(null)
                ?.resolve("logs")
                ?: File(context.applicationContext.filesDir, "logs")
            directory.mkdirs()
            logFile = File(directory, LOG_FILE_NAME)
        }
        i("BOOT", "logger initialized path=${path()}")
    }

    fun d(component: String, message: String) =
        write(Log.DEBUG, "D", component, message, null)

    fun i(component: String, message: String) =
        write(Log.INFO, "I", component, message, null)

    fun w(component: String, message: String, throwable: Throwable? = null) =
        write(Log.WARN, "W", component, message, throwable)

    fun e(component: String, message: String, throwable: Throwable? = null) =
        write(Log.ERROR, "E", component, message, throwable)

    fun path(): String = logFile?.absolutePath ?: "logger-not-initialized"

    fun readText(maxCharacters: Int = 240_000): String = synchronized(lock) {
        val file = logFile ?: return@synchronized "日志尚未初始化"
        if (!file.exists()) return@synchronized "暂无日志\n${file.absolutePath}"
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            if (text.length <= maxCharacters) {
                text
            } else {
                "（仅显示最后 $maxCharacters 个字符）\n" + text.takeLast(maxCharacters)
            }
        }.getOrElse {
            "读取日志失败：${it.javaClass.simpleName}: ${it.message}"
        }
    }

    fun clear() {
        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                file.writeText("", Charsets.UTF_8)
                repeat(BACKUP_COUNT) { index ->
                    File(file.parentFile, "$LOG_FILE_NAME.${index + 1}").delete()
                }
            }.onFailure {
                Log.e(LOG_TAG, "Failed to clear log", it)
            }
        }
        i("LOG", "log cleared by user")
    }

    private fun write(
        priority: Int,
        level: String,
        component: String,
        message: String,
        throwable: Throwable?,
    ) {
        val safeComponent = component.replace(Regex("[\\r\\n]+"), " ").take(32)
        val safeMessage = message.replace("\r", "").replace("\n", "\n    ")
        val throwableText = throwable?.let {
            "\n    " + Log.getStackTraceString(it).take(12_000).replace("\n", "\n    ")
        }.orEmpty()
        Log.println(priority, LOG_TAG, "[$safeComponent] $message")

        synchronized(lock) {
            val file = logFile ?: return
            runCatching {
                rotateIfNeeded(file)
                val timestamp = timestampFormat.format(Date())
                file.appendText(
                    "$timestamp $level [$safeComponent] $safeMessage$throwableText\n",
                    Charsets.UTF_8,
                )
            }.onFailure {
                Log.e(LOG_TAG, "Failed to append persistent log", it)
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_BYTES) return
        for (index in BACKUP_COUNT downTo 2) {
            val older = File(file.parentFile, "$LOG_FILE_NAME.${index - 1}")
            val newer = File(file.parentFile, "$LOG_FILE_NAME.$index")
            if (older.exists()) older.copyTo(newer, overwrite = true)
        }
        file.copyTo(File(file.parentFile, "$LOG_FILE_NAME.1"), overwrite = true)
        file.writeText("", Charsets.UTF_8)
    }
}
