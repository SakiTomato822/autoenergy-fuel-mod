package com.lynk.dvrprobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

/** Only called for visible tiles, on an IO worker. No prefetch or offline download. */
internal class OsmTileCache(context: Context) {
    private val directory = File(context.cacheDir, "osm-tiles").apply { mkdirs() }
    fun load(z: Int, x: Int, y: Int): Bitmap? {
        val file = File(directory, "$z-$x-$y.png")
        val metadata = File(directory, "$z-$x-$y.meta")
        val headers = Properties().apply {
            if (metadata.exists()) runCatching { metadata.inputStream().use { load(it) } }
        }
        val now = System.currentTimeMillis()
        if (file.exists() && now < (headers.getProperty("expires")?.toLongOrNull() ?: 0L)) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        val connection = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000; connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "AutoEnergyFuel/${BuildConfig.VERSION_NAME} (+https://github.com/SakiTomato822/autoenergy-fuel-mod)")
            if (file.exists()) {
                headers.getProperty("etag")?.let { connection.setRequestProperty("If-None-Match", it) }
                headers.getProperty("modified")?.let { connection.setRequestProperty("If-Modified-Since", it) }
            }
            val code = connection.responseCode
            if (code != 200 && code != 304) return null
            if (code == 200) {
                val bytes = connection.inputStream.use { stream ->
                    // Bound untrusted network responses before decoding.
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 1_048_576) {
                        val length = stream.read(buffer)
                        if (length < 0) break
                        output.write(buffer, 0, length)
                    }
                    val data = output.toByteArray()
                    if (data.size > 1_048_576) return null
                    data
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth != 256 || bounds.outHeight != 256) return null
                file.writeBytes(bytes)
            }
            val sevenDays = 7L * 24 * 60 * 60 * 1000
            val maxAge = Regex("max-age=(\\d+)").find(connection.getHeaderField("Cache-Control") ?: "")
                ?.groupValues?.get(1)?.toLongOrNull()?.coerceAtMost(31_536_000)?.times(1000)
            // Respect longer server lifetimes; retain at least seven days as OSM requests.
            headers.setProperty("expires", maxOf(now + sevenDays, now + (maxAge ?: 0), connection.expiration).toString())
            connection.getHeaderField("ETag")?.let { headers.setProperty("etag", it) }
            connection.getHeaderField("Last-Modified")?.let { headers.setProperty("modified", it) }
            metadata.outputStream().use { headers.store(it, null) }
            trim()
            return BitmapFactory.decodeFile(file.path)
        } finally { connection.disconnect() }
    }
    private fun trim() {
        val files = directory.listFiles()?.filter { it.extension == "png" } ?: return
        var bytes = files.sumOf { it.length() }
        if (bytes <= 64L * 1024 * 1024) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (bytes <= 48L * 1024 * 1024) break
            bytes -= file.length(); file.delete()
            File(directory, file.nameWithoutExtension + ".meta").delete()
        }
    }
}
