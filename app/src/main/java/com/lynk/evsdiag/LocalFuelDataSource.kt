package com.lynk.dvrprobe

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Shared Downloads JSON is authoritative; prefs store URI locations only. */
object LocalFuelDataSource {
    private lateinit var context: Context
    private var state: FuelHistoryState? = null
    private var dirty = false
    private var lastWriteMs = 0L
    private const val DIRECTORY = "Download/AutoEnergyData/"

    @Synchronized fun read(context: Context): FuelHistoryState {
        this.context = context.applicationContext
        state?.let { return it }
        val locations = this.context.getSharedPreferences("fuel_data_location", Context.MODE_PRIVATE)
        // Recover valid generations, including a published file whose URI commit
        // was interrupted. Query exposes only this installation's own Downloads.
        val candidates = mutableListOf<String>()
        runCatching {
            this.context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.IS_PENDING}=0",
                arrayOf(DIRECTORY), "${MediaStore.Downloads._ID} DESC")?.use { cursor ->
                while (cursor.moveToNext() && candidates.size < 10) candidates += Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString()).toString()
            }
        }.onFailure { AppLog.w("DATA", "generation query failed", it) }
        for (key in listOf("latest_uri", "previous_uri")) locations.getString(key, null)?.let { candidates += it }
        for (uri in candidates.distinct()) {
            runCatching { FuelHistoryJson.decode(readText(Uri.parse(uri))) }.onSuccess {
                state = it
                AppLog.i("DATA", "loaded JSON uri=$uri points=${it.points.size}")
                return it
            }.onFailure { AppLog.w("DATA", "JSON generation rejected uri=$uri", it) }
        }
        if (candidates.isNotEmpty()) error("No valid local JSON generation; select an intact data file")
        state = migrateLegacy()
        dirty = true
        flush(force = true)
        return state!!
    }

    @Synchronized fun update(context: Context, change: (FuelHistoryState) -> FuelHistoryState) {
        state = change(read(context))
        dirty = true
    }

    @Synchronized fun flush(force: Boolean = false) {
        if (!dirty || (!force && System.currentTimeMillis() - lastWriteMs < 60_000L)) return
        val current = state ?: return
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "history-${System.currentTimeMillis()}.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AutoEnergyData/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: error("Cannot create local JSON data file")
        try {
            val json = FuelHistoryJson.encode(current)
            resolver.openOutputStream(uri, "w")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot write local JSON data file")
            FuelHistoryJson.decode(readText(uri))
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) == 1)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        val locations = context.getSharedPreferences("fuel_data_location", Context.MODE_PRIVATE)
        val oldLatest = locations.getString("latest_uri", null)
        val obsolete = locations.getString("previous_uri", null)
        check(locations.edit().putString("latest_uri", uri.toString()).putString("previous_uri", oldLatest).commit())
        // Prune only the generation explicitly owned by this installation.
        obsolete?.let { runCatching { resolver.delete(Uri.parse(it), null, null) } }
        dirty = false
        lastWriteMs = System.currentTimeMillis()
        AppLog.i("DATA", "JSON committed path=$DIRECTORY points=${current.points.size} uri=$uri")
    }

    @Synchronized fun selectExisting(context: Context, uri: Uri): Int {
        this.context = context.applicationContext
        val selected = FuelHistoryJson.decode(readText(uri))
        val prior = state
        val priorDirty = dirty
        state = selected
        dirty = true
        try { flush(force = true) } catch (error: Throwable) { state = prior; dirty = priorDirty; throw error }
        AppLog.i("DATA", "existing JSON selected points=${selected.points.size}")
        return selected.points.size
    }

    private fun readText(uri: Uri): String = context.contentResolver.openInputStream(uri)?.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            check(output.size() + count <= 20 * 1024 * 1024) { "data file too large" }
            output.write(buffer, 0, count)
        }
        output.toString("UTF-8")
    } ?: error("Cannot read JSON data file")

    private fun migrateLegacy(): FuelHistoryState {
        val prefs = context.getSharedPreferences("fuel_energy_trend", Context.MODE_PRIVATE)
        val points = prefs.getString("distance_points_v2", "").orEmpty().split('|').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size != 4) return@mapNotNull null
            runCatching { FuelTrendPoint(parts[0].toLong(), parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()) }.getOrNull()
        }
        return FuelHistoryState(points, prefs.getBoolean("has_previous_v2", false),
            prefs.getFloat("previous_trip_distance_v2", 0f), prefs.getFloat("previous_trip_fuel_v2", 0f),
            prefs.getFloat("chart_distance_v2", 0f), prefs.getFloat("pending_distance_v2", 0f), prefs.getFloat("pending_fuel_v2", 0f))
    }
}
