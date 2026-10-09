package com.lynk.dvrprobe

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Fixed allowlist, read-only queries. No su, settings writes or arbitrary shell commands. */
class McuDumpSource(private val context: Context) {
    fun read(diagnostics: MutableList<String>): Map<String, Long> {
        if (context.checkSelfPermission("android.permission.DUMP") != PackageManager.PERMISSION_GRANTED) {
            diagnostics += "mcu.dump unavailable: grant android.permission.DUMP via ADB once"
            return emptyMap()
        }
        val properties = listOf(
            Triple("currentFuel", "28700440", 2), Triple("subtotalFuel", "28700048", 2),
            Triple("fuelUnit", "28700026", 1), Triple("fuelPercent", "2870043a", 2),
            Triple("oilRange", "2870001e", 4), Triple("resetOption", "28700022", 1),
            Triple("odometer", "2870043b", 4), Triple("subtotalDistance", "28700020", 4),
            Triple("currentDistance", "2870043c", 4), Triple("subtotalSpeed", "28700047", 2),
            Triple("currentSpeed", "2870043e", 2), Triple("subtotalDuration", "28700049", 4),
            Triple("currentDuration", "2870043d", 4),
        )
        val result = mutableMapOf<String, Long>()
        for ((label, id, length) in properties) {
            runCatching {
                val process = ProcessBuilder("/system/bin/dumpsys", "-t", "1", "car_service", "get-property-value", id, "0")
                    .redirectErrorStream(true).start()
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val output = executor.submit<String> {
                        process.inputStream.bufferedReader().use { reader ->
                            val text = StringBuilder(); val buffer = CharArray(2048)
                            while (true) { val n = reader.read(buffer); if (n < 0) break
                                if (text.length < 32768) text.append(buffer, 0, minOf(n, 32768 - text.length)) }
                            text.toString()
                        }
                    }
                    if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly(); error("query timed out")
                    }
                    val raw = output.get(500, TimeUnit.MILLISECONDS)
                    check(process.exitValue() == 0) { "dumpsys failed: ${raw.take(160)}" }
                    val value = McuDumpParser.parse(raw, id.toLong(16), length)
                    if (value != null) { result[label] = value; diagnostics += "mcu.dump $label=$value prop=0x$id area=0" }
                    else diagnostics += "mcu.dump $label rejected: ${raw.take(240).replace('\n', ' ')}"
                } finally { process.destroy(); executor.shutdownNow() }
            }.onFailure { diagnostics += "mcu.dump $label error=${it.javaClass.simpleName}: ${it.message}" }
        }
        return result
    }
}

internal object McuDumpParser {
    fun parse(text: String, property: Long, length: Int): Long? {
        val prop = Regex("(?:Property|prop|propertyId|mProp)\\s*[=:]\\s*(0x[0-9a-fA-F]+|[0-9]+)\\b").find(text)?.groupValues?.get(1) ?: return null
        val actual = if (prop.startsWith("0x")) prop.substring(2).toLongOrNull(16) else prop.toLongOrNull()
        if (actual != property) return null
        if (!Regex("(?:zone|areaId|area|mAreaId)\\s*[=:]\\s*(?:0x0|0)\\b").containsMatchIn(text)) return null
        if (!Regex("(?:status|mStatus)\\s*[=:]\\s*(?:0|AVAILABLE)\\b").containsMatchIn(text)) return null
        val raw = Regex("(?:byteValues|bytes|mByteValues)\\s*[=:]\\s*\\[([^]]*)]").find(text)?.groupValues?.get(1) ?: return null
        val tokens = raw.split(',').map { it.trim() }
        if (tokens.size != length) return null
        var result = 0L
        for (token in tokens) { val byte = token.toIntOrNull() ?: return null
            if (byte !in -128..255) return null
            result = (result shl 8) or (byte.toLong() and 255) }
        return result
    }
}
