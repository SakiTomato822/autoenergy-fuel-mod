package com.lynk.dvrprobe

import android.content.Intent

class SimulatedCarProperties(initialScenario: String = "aggressive") {
    companion object {
        const val ACTION_UPDATE = "com.lynk.autoenergyfuel.SIMULATE_CAR_PROPERTY"

        const val DIRECT_RANGE_REMAINING = 291504904
        const val DIRECT_PERF_ODOMETER = 291504644
        const val API_FUEL_PERCENT = 4211968
        const val API_OIL_RANGE = 1054720
        const val API_AVG_FUEL = 4194560
        const val API_TRIP_TOTAL_DISTANCE = 612373760
        const val API_TRIP_AVG_SPEED = 612372992
        const val API_TRIP_TOTAL_DURATION = 612374016
    }

    private data class PropertyKey(val propertyId: Int, val areaId: Int)

    private val values = mutableMapOf<PropertyKey, Float>()
    private var activeScenario = initialScenario

    init {
        loadScenario(initialScenario)
    }

    fun applyCommand(intent: Intent): String {
        intent.getStringExtra("scenario")?.let {
            loadScenario(it)
            return "scenario=$activeScenario"
        }

        val extras = intent.extras
        if (extras?.containsKey("propertyId") == true) {
            val propertyId = number(extras.get("propertyId"))?.toInt()
                ?: return "invalid propertyId"
            val areaId = number(extras.get("areaId"))?.toInt() ?: 0
            val rawValue = extras.get("value")
            if (rawValue?.toString().equals("null", ignoreCase = true)) {
                values.remove(PropertyKey(propertyId, areaId))
                activeScenario = "custom"
                return "removed propertyId=$propertyId areaId=$areaId"
            }
            val value = number(rawValue) ?: return "invalid value"
            values[PropertyKey(propertyId, areaId)] = value
            activeScenario = "custom"
            return "propertyId=$propertyId areaId=$areaId value=$value"
        }

        applyNamedExtra(intent, "fuelPercent", API_FUEL_PERCENT, 0)
        applyNamedExtra(intent, "oilRangeKm", API_OIL_RANGE, 0)
        applyNamedExtra(intent, "totalRangeKm", DIRECT_RANGE_REMAINING, 0)
        applyNamedExtra(intent, "odometerKm", DIRECT_PERF_ODOMETER, 0)
        applyNamedExtra(intent, "avgFuelTrip1", API_AVG_FUEL, 1)
        applyNamedExtra(intent, "avgFuelTrip2", API_AVG_FUEL, 2)
        applyNamedExtra(intent, "trip1DistanceKm", API_TRIP_TOTAL_DISTANCE, 1)
        applyNamedExtra(intent, "trip2DistanceKm", API_TRIP_TOTAL_DISTANCE, 2)
        applyNamedExtra(intent, "trip1AvgSpeed", API_TRIP_AVG_SPEED, 1)
        applyNamedExtra(intent, "trip2AvgSpeed", API_TRIP_AVG_SPEED, 2)
        applyNamedExtra(intent, "trip1DurationMinutes", API_TRIP_TOTAL_DURATION, 1)
        applyNamedExtra(intent, "trip2DurationMinutes", API_TRIP_TOTAL_DURATION, 2)
        activeScenario = "custom"
        return "custom named properties"
    }

    fun snapshot(): FuelEnergySnapshot = FuelEnergySnapshot(
        avgFuelTrip1 = value(API_AVG_FUEL, 1),
        avgFuelTrip2 = value(API_AVG_FUEL, 2),
        fuelPercent = value(API_FUEL_PERCENT, 0)?.toInt(),
        oilRangeKm = value(API_OIL_RANGE, 0)?.toInt(),
        totalRangeKm = value(DIRECT_RANGE_REMAINING, 0)?.toInt(),
        odometerKm = value(DIRECT_PERF_ODOMETER, 0),
        trip1DistanceKm = value(API_TRIP_TOTAL_DISTANCE, 1),
        trip1AvgSpeed = value(API_TRIP_AVG_SPEED, 1),
        trip1DurationMinutes = value(API_TRIP_TOTAL_DURATION, 1)?.toInt(),
        trip2DistanceKm = value(API_TRIP_TOTAL_DISTANCE, 2),
        trip2AvgSpeed = value(API_TRIP_AVG_SPEED, 2),
        trip2DurationMinutes = value(API_TRIP_TOTAL_DURATION, 2)?.toInt(),
        diagnostics = listOf(
            "simulated CarProperty scenario=$activeScenario",
            "propertyCount=${values.size}",
        ),
    )

    private fun loadScenario(name: String) {
        values.clear()
        when (name.lowercase()) {
            "normal" -> seed(
                fuelPercent = 68f,
                rangeKm = 482f,
                avgFuelTrip1 = 7.6f,
                avgFuelTrip2 = 8.2f,
                odometerKm = 28641f,
                trip1DistanceKm = 126.4f,
                trip1AvgSpeed = 42.8f,
                trip1DurationMinutes = 177f,
            )
            "low_fuel" -> seed(
                fuelPercent = 8f,
                rangeKm = 42f,
                avgFuelTrip1 = 10.8f,
                avgFuelTrip2 = 9.4f,
                odometerKm = 28672f,
                trip1DistanceKm = 157.7f,
                trip1AvgSpeed = 35.2f,
                trip1DurationMinutes = 269f,
            )
            "full" -> seed(
                fuelPercent = 100f,
                rangeKm = 710f,
                avgFuelTrip1 = 6.5f,
                avgFuelTrip2 = 7.1f,
                odometerKm = 28678f,
                trip1DistanceKm = 163.7f,
                trip1AvgSpeed = 39.5f,
                trip1DurationMinutes = 248f,
            )
            "sensor_fault" -> Unit
            else -> {
                activeScenario = "aggressive"
                seed(
                    fuelPercent = 22f,
                    rangeKm = 126f,
                    avgFuelTrip1 = 18.9f,
                    avgFuelTrip2 = 12.7f,
                    odometerKm = 28658f,
                    trip1DistanceKm = 143.7f,
                    trip1AvgSpeed = 76.4f,
                    trip1DurationMinutes = 139f,
                )
                return
            }
        }
        activeScenario = name.lowercase()
    }

    private fun seed(
        fuelPercent: Float,
        rangeKm: Float,
        avgFuelTrip1: Float,
        avgFuelTrip2: Float,
        odometerKm: Float,
        trip1DistanceKm: Float,
        trip1AvgSpeed: Float,
        trip1DurationMinutes: Float,
    ) {
        set(API_FUEL_PERCENT, 0, fuelPercent)
        set(API_OIL_RANGE, 0, rangeKm)
        set(DIRECT_RANGE_REMAINING, 0, rangeKm)
        set(DIRECT_PERF_ODOMETER, 0, odometerKm)
        set(API_AVG_FUEL, 1, avgFuelTrip1)
        set(API_AVG_FUEL, 2, avgFuelTrip2)
        set(API_TRIP_TOTAL_DISTANCE, 1, trip1DistanceKm)
        set(API_TRIP_AVG_SPEED, 1, trip1AvgSpeed)
        set(API_TRIP_TOTAL_DURATION, 1, trip1DurationMinutes)
        set(API_TRIP_TOTAL_DISTANCE, 2, 873.5f)
        set(API_TRIP_AVG_SPEED, 2, 58.9f)
        set(API_TRIP_TOTAL_DURATION, 2, 1194f)
    }

    private fun applyNamedExtra(intent: Intent, name: String, propertyId: Int, areaId: Int) {
        val extras = intent.extras ?: return
        if (!extras.containsKey(name)) return
        val raw = extras.get(name)
        if (raw?.toString().equals("null", ignoreCase = true)) {
            values.remove(PropertyKey(propertyId, areaId))
        } else {
            number(raw)?.let { set(propertyId, areaId, it) }
        }
    }

    private fun set(propertyId: Int, areaId: Int, value: Float) {
        values[PropertyKey(propertyId, areaId)] = value
    }

    private fun value(propertyId: Int, areaId: Int): Float? =
        values[PropertyKey(propertyId, areaId)]

    private fun number(value: Any?): Float? = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }
}
