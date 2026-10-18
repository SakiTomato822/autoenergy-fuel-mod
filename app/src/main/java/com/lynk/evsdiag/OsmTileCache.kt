package com.lynk.dvrprobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.Properties

/** Only called for visible tiles, on an IO worker. No prefetch or offline download. */
internal class OsmTileCache(context: Context) {
    private val directory = File(context.cacheDir, "osm-tiles").apply { mkdirs() }
    private val http by lazy { MapTileHttp(directory) }
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
        http.get("https://tile.openstreetmap.org/$z/$x/$y.png",
            if (file.exists()) headers.getProperty("etag") else null,
            if (file.exists()) headers.getProperty("modified") else null).use { response ->
            val code = response.code
            check(response.header("x-blocked") == null) { "tile provider refused request" }
            check(code == 200 || code == 304) { "tile HTTP $code" }
            if (code == 200) {
                val bytes = checkNotNull(response.body) { "missing tile body" }.byteStream().use { stream ->
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
                check(bounds.outWidth == 256 && bounds.outHeight == 256) { "invalid tile image" }
                file.writeBytes(bytes)
            }
            val sevenDays = 7L * 24 * 60 * 60 * 1000
            val maxAge = Regex("max-age=(\\d+)").find(response.header("Cache-Control") ?: "")
                ?.groupValues?.get(1)?.toLongOrNull()?.coerceAtMost(31_536_000)?.times(1000)
            // Respect longer server lifetimes; retain at least seven days as OSM requests.
            val expires = runCatching { java.time.ZonedDateTime.parse(response.header("Expires"),
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)
            headers.setProperty("expires", maxOf(now + sevenDays, now + (maxAge ?: 0), expires).toString())
            response.header("ETag")?.let { headers.setProperty("etag", it) }
            response.header("Last-Modified")?.let { headers.setProperty("modified", it) }
            metadata.outputStream().use { headers.store(it, null) }
            trim()
            return BitmapFactory.decodeFile(file.path)
        }
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
