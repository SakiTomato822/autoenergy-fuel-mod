package com.lynk.dvrprobe

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.MessageDigest

data class FuelHistoryState(
    val points: List<FuelTrendPoint> = emptyList(),
    val hasPrevious: Boolean = false,
    val previousDistanceKm: Float = 0f,
    val previousFuelLitres: Float = 0f,
    val chartDistanceKm: Float = 0f,
    val pendingDistanceKm: Float = 0f,
    val pendingFuelLitres: Float = 0f,
)

/** Validated JSON is the durable source, not a SharedPreferences export. */
internal object FuelHistoryJson {
    private val gson = Gson()
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encode(state: FuelHistoryState): String {
        validate(state)
        val data = gson.toJsonTree(state)
        return com.google.gson.JsonObject().apply {
            addProperty("format", "AutoEnergyLocalData")
            addProperty("schemaVersion", 1)
            addProperty("writtenAtMs", System.currentTimeMillis())
            addProperty("sha256", hash(gson.toJson(data)))
            add("data", data)
        }.let { gson.toJson(it) }
    }

    fun decode(text: String): FuelHistoryState {
        require(text.toByteArray(Charsets.UTF_8).size <= 20 * 1024 * 1024) { "data file too large" }
        val root = JsonParser.parseString(text).asJsonObject
        require(root["format"].asString == "AutoEnergyLocalData" && root["schemaVersion"].asInt == 1) { "unsupported data file" }
        val data = root["data"].asJsonObject
        for (key in listOf("points", "hasPrevious", "previousDistanceKm", "previousFuelLitres", "chartDistanceKm", "pendingDistanceKm", "pendingFuelLitres")) {
            require(data.has(key) && !data[key].isJsonNull) { "missing $key" }
        }
        require(root["sha256"].asString == hash(gson.toJson(data))) { "data checksum mismatch" }
        return gson.fromJson(data, FuelHistoryState::class.java).also(::validate)
    }

    private fun validate(state: FuelHistoryState) {
        for (value in listOf(state.previousDistanceKm, state.previousFuelLitres, state.chartDistanceKm, state.pendingDistanceKm)) {
            require(value.isFinite() && value >= 0f) { "invalid progress" }
        }
        require(state.pendingFuelLitres.isFinite()) { "invalid fuel progress" }
        require(state.points.size <= 200_000) { "too many points" }
        var previous = -1f
        state.points.forEach {
            require(it.timestampMs > 0 && it.value.isFinite() && it.value in 0f..60f &&
                it.distanceKm.isFinite() && it.distanceKm >= previous && it.spanKm.isFinite() && it.spanKm > 0f) { "invalid curve point" }
            previous = it.distanceKm
        }
    }
}
