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
        const val API_SINGLE_TRIP_RESET_OPTION = 612369152
        const val RESET_OPTION_CHARGING = 612369154
        const val RESET_OPTION_PARKING = 612369156
    }

    private data class PropertyKey(val propertyId: Int, val areaId: Int)

    private val values = mutableMapOf<PropertyKey, Float>()
    private var activeScenario = initialScenario
    private var singleTripResetOption = RESET_OPTION_PARKING

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
            if (propertyId == API_SINGLE_TRIP_RESET_OPTION) {
                val option = rawValue?.toString()?.toDoubleOrNull()?.toInt()
                    ?: return "invalid reset option"
                setSingleTripResetOption(option)
                activeScenario = "custom"
                return "singleTripResetOption=$option"
            }
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
        intent.extras?.get("singleTripResetOption")?.toString()?.toDoubleOrNull()?.toInt()?.let {
            setSingleTripResetOption(it)
        }
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
        singleTripResetOption = singleTripResetOption,
        diagnostics = listOf(
            "simulated CarProperty scenario=$activeScenario",
            "propertyCount=${values.size}",
        ),
    )

    fun setSingleTripResetOption(value: Int) {
        singleTripResetOption = when (value) {
            RESET_OPTION_CHARGING -> RESET_OPTION_CHARGING
            else -> RESET_OPTION_PARKING
        }
    }

    fun resetSubtotalTrip() {
        set(API_TRIP_TOTAL_DISTANCE, 1, 0f)
        set(API_TRIP_AVG_SPEED, 1, 0f)
        set(API_TRIP_TOTAL_DURATION, 1, 0f)
        set(API_AVG_FUEL, 1, 0f)
    }

    private fun loadScenario(name: String) {
        values.clear()
        when (name.lowercase()) {
            "normal" -> seed(
                fuelPercent = 68f,
                rangeKm = 482f,
                currentAvgFuel = 7.6f,
                subtotalAvgFuel = 8.2f,
                odometerKm = 28641f,
                currentDistanceKm = 126.4f,
                currentAvgSpeed = 42.8f,
                currentDurationMinutes = 177f,
            )
            "low_fuel" -> seed(
                fuelPercent = 8f,
                rangeKm = 42f,
                currentAvgFuel = 10.8f,
                subtotalAvgFuel = 9.4f,
                odometerKm = 28672f,
                currentDistanceKm = 157.7f,
                currentAvgSpeed = 35.2f,
                currentDurationMinutes = 269f,
            )
            "full" -> seed(
                fuelPercent = 100f,
                rangeKm = 710f,
                currentAvgFuel = 6.5f,
                subtotalAvgFuel = 7.1f,
                odometerKm = 28678f,
                currentDistanceKm = 163.7f,
                currentAvgSpeed = 39.5f,
                currentDurationMinutes = 248f,
            )
            "sensor_fault" -> Unit
            else -> {
                activeScenario = "aggressive"
                seed(
                    fuelPercent = 22f,
                    rangeKm = 126f,
                    currentAvgFuel = 18.9f,
                    subtotalAvgFuel = 12.7f,
                    odometerKm = 28658f,
                    currentDistanceKm = 143.7f,
                    currentAvgSpeed = 76.4f,
                    currentDurationMinutes = 139f,
                )
                return
            }
        }
        activeScenario = name.lowercase()
    }

    private fun seed(
        fuelPercent: Float,
        rangeKm: Float,
        currentAvgFuel: Float,
        subtotalAvgFuel: Float,
        odometerKm: Float,
        currentDistanceKm: Float,
        currentAvgSpeed: Float,
        currentDurationMinutes: Float,
    ) {
        set(API_FUEL_PERCENT, 0, fuelPercent)
        set(API_OIL_RANGE, 0, rangeKm)
        set(DIRECT_RANGE_REMAINING, 0, rangeKm)
        set(DIRECT_PERF_ODOMETER, 0, odometerKm)
        set(API_AVG_FUEL, 1, subtotalAvgFuel)
        set(API_AVG_FUEL, 2, currentAvgFuel)
        set(API_TRIP_TOTAL_DISTANCE, 1, 873.5f)
        set(API_TRIP_AVG_SPEED, 1, 58.9f)
        set(API_TRIP_TOTAL_DURATION, 1, 1194f)
        set(API_TRIP_TOTAL_DISTANCE, 2, currentDistanceKm)
        set(API_TRIP_AVG_SPEED, 2, currentAvgSpeed)
        set(API_TRIP_TOTAL_DURATION, 2, currentDurationMinutes)
        singleTripResetOption = RESET_OPTION_PARKING
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
