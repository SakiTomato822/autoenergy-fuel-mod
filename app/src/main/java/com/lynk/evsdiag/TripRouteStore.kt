package com.lynk.dvrprobe

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID

/** Private local WGS84 records; excluded from Android automatic backup. */
internal object TripRouteStore {
    private val gson = Gson()
    private fun directory(context: Context) = File(context.noBackupFilesDir, "routes").apply {
        check(isDirectory || mkdirs()) { "无法创建行程目录" }
    }
    fun create(context: Context): File = File(directory(context), "route-${System.currentTimeMillis()}-${UUID.randomUUID()}.jsonl").apply {
        check(createNewFile())
        appendText("{\"format\":\"AutoEnergyRoute\",\"schemaVersion\":1,\"coordinates\":\"WGS84\"}\n")
    }
    fun append(file: File, point: RoutePoint) { file.appendText(gson.toJson(point) + "\n") }
    fun finish(file: File) { file.appendText("{\"endedAtMs\":${System.currentTimeMillis()}}\n") }
    fun list(context: Context): List<File> = directory(context).listFiles().orEmpty()
        .filter { it.name.startsWith("route-") && it.extension == "jsonl" }
        .sortedByDescending { it.name }
    fun read(file: File): List<RoutePoint> {
        require(file.length() <= 20 * 1024 * 1024) { "行程文件过大" }
        val points = ArrayDeque<RoutePoint>()
        file.useLines { lines ->
            val iterator = lines.iterator()
            require(iterator.hasNext()) { "空的行程文件" }
            val header = JsonParser.parseString(iterator.next()).asJsonObject
            require(header["format"]?.asString == "AutoEnergyRoute" &&
                header["schemaVersion"]?.asInt == 1 && header["coordinates"]?.asString == "WGS84") { "不支持的行程格式或坐标系" }
            iterator.forEach { line ->
                // A killed process can leave a partial final line; ignore it.
                val point = runCatching {
                    val json = JsonParser.parseString(line).asJsonObject
                    require(listOf("timestampMs", "elapsedMs", "latitude", "longitude", "accuracyMetres", "breakBefore")
                        .all { json.has(it) && !json[it].isJsonNull })
                    gson.fromJson(json, RoutePoint::class.java)
                }.getOrNull()
                if (point != null && point.timestampMs > 0 && point.elapsedMs >= 0 &&
                    point.accuracyMetres.isFinite() && point.accuracyMetres in 0f..50f &&
                    (point.speedKmh == null || (point.speedKmh.isFinite() && point.speedKmh in 0f..250f)) &&
                    point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
                    point.longitude.isFinite() && point.longitude in -180.0..180.0) {
                    points.addLast(point)
                    if (points.size > 10_000) points.removeFirst()
                }
            }
        }
        return points.toList()
    }
}
