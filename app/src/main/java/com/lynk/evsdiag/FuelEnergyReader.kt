package com.lynk.dvrprobe

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File

data class FuelEnergySnapshot(
    val avgFuelTrip1: Float?,
    val avgFuelTrip2: Float?,
    val fuelPercent: Int?,
    val oilRangeKm: Int?,
    val totalRangeKm: Int?,
    val odometerKm: Float?,
    val trip1DistanceKm: Float?,
    val trip1AvgSpeed: Float?,
    val trip1DurationMinutes: Int?,
    val trip2DistanceKm: Float?,
    val trip2AvgSpeed: Float?,
    val trip2DurationMinutes: Int?,
    val singleTripResetOption: Int?,
    val diagnostics: List<String>,
) {
    val summary: String
        get() = buildString {
            append(if (avgFuelTrip1 != null || avgFuelTrip2 != null) "avg fuel readable" else "avg fuel unreadable")
            append(" / ")
            append(if (fuelPercent != null) "fuel readable" else "fuel unreadable")
            append(" / ")
            append(if (oilRangeKm != null || totalRangeKm != null) "range readable" else "range unreadable")
            append(" / ")
            append(if (odometerKm != null) "odometer readable" else "odometer unreadable")
        }
}

class FuelEnergyReader(private val context: Context) {
    companion object {
        private const val DIRECT_RANGE_REMAINING = 291504904
        private const val DIRECT_PERF_ODOMETER = 291504644

        private const val API_FUEL_PERCENT = 4211968
        private const val API_OIL_RANGE = 1054720
        private const val API_AVG_FUEL = 4194560
        private const val API_TRIP_TOTAL_DISTANCE = 612373760
        private const val API_TRIP_AVG_SPEED = 612372992
        private const val API_TRIP_TOTAL_DURATION = 612374016
        private const val API_SINGLE_TRIP_RESET_OPTION = 612369152
        private const val API_SUBTOTAL_TRIP_RESET = 612368896

        private const val MAX_REASONABLE_RANGE_KM = 5000
        private const val MAX_REASONABLE_ODOMETER_KM = 2_000_000f
    }

    fun readSnapshot(): FuelEnergySnapshot {
        val diagnostics = mutableListOf<String>()
        return runCatching {
            val carClass = Class.forName("android.car.Car")
            val car = carClass.getMethod("createCar", Context::class.java).invoke(null, context)
                ?: throw IllegalStateException("Car.createCar returned null")

            try {
                val propertyService = runCatching { carClass.getField("PROPERTY_SERVICE").get(null) as String }
                    .getOrElse { "property" }
                val mgr = car.javaClass.getMethod("getCarManager", String::class.java).invoke(car, propertyService)
                    ?: throw IllegalStateException("getCarManager(property) returned null")

                val wrapperBridge = WrapperBridge.create(context, diagnostics)

                val fuelPercentProp = wrapperBridge?.resolvePropertyId(API_FUEL_PERCENT, false, diagnostics)
                val oilRangeProp = wrapperBridge?.resolvePropertyId(API_OIL_RANGE, false, diagnostics)
                val avgFuelProp = wrapperBridge?.resolvePropertyId(API_AVG_FUEL, false, diagnostics)
                val tripDistanceProp = wrapperBridge?.resolvePropertyId(API_TRIP_TOTAL_DISTANCE, true, diagnostics)
                val tripSpeedProp = wrapperBridge?.resolvePropertyId(API_TRIP_AVG_SPEED, true, diagnostics)
                val tripDurationProp = wrapperBridge?.resolvePropertyId(API_TRIP_TOTAL_DURATION, true, diagnostics)

                val totalRange = readDirectFloatAsInt(mgr, DIRECT_RANGE_REMAINING, 0, diagnostics, "rangeRemaining")
                val fuelPercent = fuelPercentProp?.let { readWrappedInt(mgr, it, 0, diagnostics, "fuelPercent") }
                val oilRange = oilRangeProp?.let { readWrappedFloatAsInt(mgr, it, 0, diagnostics, "oilRange") }
                val odometer = readDirectPropertyFloat(mgr, DIRECT_PERF_ODOMETER, 0, diagnostics, "perfOdometer")

                val avgFuelTrip1 = avgFuelProp?.let { readWrappedFloat(mgr, it, 1, diagnostics, "avgFuelTrip1") }
                val avgFuelTrip2 = avgFuelProp?.let { readWrappedFloat(mgr, it, 2, diagnostics, "avgFuelTrip2") }

                val trip1Distance = tripDistanceProp?.let { readWrappedFloat(mgr, it, 1, diagnostics, "trip1Distance") }
                val trip1Speed = tripSpeedProp?.let { readWrappedFloat(mgr, it, 1, diagnostics, "trip1AvgSpeed") }
                val trip1Duration = tripDurationProp?.let { readWrappedInt(mgr, it, 1, diagnostics, "trip1Duration") }

                val trip2Distance = tripDistanceProp?.let { readWrappedFloat(mgr, it, 2, diagnostics, "trip2Distance") }
                val trip2Speed = tripSpeedProp?.let { readWrappedFloat(mgr, it, 2, diagnostics, "trip2AvgSpeed") }
                val trip2Duration = tripDurationProp?.let { readWrappedInt(mgr, it, 2, diagnostics, "trip2Duration") }
                val singleTripResetOption = wrapperBridge?.readAdaptedInt(
                    manager = mgr,
                    API_SINGLE_TRIP_RESET_OPTION,
                    isFunctionType = true,
                    areaId = 0,
                    diagnostics = diagnostics,
                    label = "singleTripResetOption",
                )

                FuelEnergySnapshot(
                    avgFuelTrip1 = avgFuelTrip1,
                    avgFuelTrip2 = avgFuelTrip2,
                    fuelPercent = fuelPercent,
                    oilRangeKm = oilRange,
                    totalRangeKm = totalRange,
                    odometerKm = odometer,
                    trip1DistanceKm = trip1Distance,
                    trip1AvgSpeed = trip1Speed,
                    trip1DurationMinutes = trip1Duration,
                    trip2DistanceKm = trip2Distance,
                    trip2AvgSpeed = trip2Speed,
                    trip2DurationMinutes = trip2Duration,
                    singleTripResetOption = singleTripResetOption,
                    diagnostics = diagnostics,
                )
            } finally {
                runCatching { car.javaClass.getMethod("disconnect").invoke(car) }
            }
        }.getOrElse { t ->
            diagnostics += "reader.error=${t.javaClass.simpleName}: ${t.message}"
            FuelEnergySnapshot(
                avgFuelTrip1 = null,
                avgFuelTrip2 = null,
                fuelPercent = null,
                oilRangeKm = null,
                totalRangeKm = null,
                odometerKm = null,
                trip1DistanceKm = null,
                trip1AvgSpeed = null,
                trip1DurationMinutes = null,
                trip2DistanceKm = null,
                trip2AvgSpeed = null,
                trip2DurationMinutes = null,
                singleTripResetOption = null,
                diagnostics = diagnostics,
            )
        }
    }

    fun writeSingleTripResetOption(value: Int): Boolean = withPropertyManager { mgr ->
        val diagnostics = mutableListOf<String>()
        val bridge = WrapperBridge.create(context, diagnostics) ?: return@withPropertyManager false
        bridge.writeAdaptedInt(
            manager = mgr,
            apiId = API_SINGLE_TRIP_RESET_OPTION,
            isFunctionType = true,
            areaId = 0,
            apiValue = value,
            diagnostics = diagnostics,
        )
    } ?: false

    fun resetSubtotalTrip(): Boolean = withPropertyManager { mgr ->
        val diagnostics = mutableListOf<String>()
        val bridge = WrapperBridge.create(context, diagnostics) ?: return@withPropertyManager false
        bridge.writeBoolean(
            manager = mgr,
            apiId = API_SUBTOTAL_TRIP_RESET,
            isFunctionType = true,
            areaId = 0,
            value = true,
            diagnostics = diagnostics,
        )
    } ?: false

    private fun <T> withPropertyManager(block: (Any) -> T): T? = runCatching {
        val carClass = Class.forName("android.car.Car")
        val car = carClass.getMethod("createCar", Context::class.java).invoke(null, context)
            ?: return@runCatching null
        try {
            val propertyService = runCatching {
                carClass.getField("PROPERTY_SERVICE").get(null) as String
            }.getOrElse { "property" }
            val manager = car.javaClass
                .getMethod("getCarManager", String::class.java)
                .invoke(car, propertyService)
                ?: return@runCatching null
            block(manager)
        } finally {
            runCatching { car.javaClass.getMethod("disconnect").invoke(car) }
        }
    }.getOrNull()

    private fun readWrappedInt(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Int? {
        return runCatching {
            val value = mgr.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, propId, areaId) as Int
            diagnostics += "$label=$value via wrapped.getIntProperty($propId,$areaId)"
            sanitizeInt(label, value, diagnostics)
        }.getOrElse {
            diagnostics += "$label error=${it.javaClass.simpleName} wrapped.getIntProperty($propId,$areaId)"
            null
        }
    }

    private fun readWrappedFloat(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Float? {
        return runCatching {
            val value = mgr.javaClass
                .getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, propId, areaId) as Float
            diagnostics += "$label=$value via wrapped.getFloatProperty($propId,$areaId)"
            sanitizeFloat(label, value, diagnostics)
        }.getOrElse {
            diagnostics += "$label error=${it.javaClass.simpleName} wrapped.getFloatProperty($propId,$areaId)"
            null
        }
    }

    private fun readWrappedFloatAsInt(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Int? {
        return readWrappedFloat(mgr, propId, areaId, diagnostics, label)?.toInt()
    }

    private fun readDirectFloatAsInt(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Int? {
        return runCatching {
            val value = mgr.javaClass
                .getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, propId, areaId) as Float
            diagnostics += "$label=$value via direct.getFloatProperty($propId,$areaId)"
            sanitizeFloat(label, value, diagnostics)?.toInt()
        }.getOrElse {
            diagnostics += "$label error=${it.javaClass.simpleName} direct.getFloatProperty($propId,$areaId)"
            null
        }
    }

    private fun readDirectPropertyFloat(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Float? {
        return runCatching {
            val valueObj = mgr.javaClass
                .getMethod("getProperty", Class::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, Float::class.javaObjectType, propId, areaId)
            val value = valueObj?.javaClass?.getMethod("getValue")?.invoke(valueObj) as? Float
            diagnostics += "$label=$value via direct.getProperty<Float>($propId,$areaId)"
            sanitizeFloat(label, value, diagnostics)
        }.getOrElse {
            diagnostics += "$label error=${it.javaClass.simpleName} direct.getProperty<Float>($propId,$areaId)"
            null
        }
    }

    private fun sanitizeInt(label: String, value: Int?, diagnostics: MutableList<String>): Int? {
        if (value == null) return null
        val valid = when (label) {
            "fuelPercent" -> value in 0..100
            "trip1Duration", "trip2Duration" -> value in 0..(24 * 60 * 30)
            else -> value != Int.MIN_VALUE && value >= 0
        }
        if (!valid) {
            diagnostics += "$label invalidInt=$value"
            return null
        }
        return value
    }

    private fun sanitizeFloat(label: String, value: Float?, diagnostics: MutableList<String>): Float? {
        if (value == null || value.isNaN() || value.isInfinite()) {
            diagnostics += "$label invalidFloat=$value"
            return null
        }
        val valid = when (label) {
            "rangeRemaining", "oilRange" -> value >= 0f && value <= MAX_REASONABLE_RANGE_KM
            "perfOdometer" -> value >= 0f && value <= MAX_REASONABLE_ODOMETER_KM
            "avgFuelTrip1", "avgFuelTrip2" -> value >= 0f && value <= 100f
            "trip1Distance", "trip2Distance" -> value >= 0f && value <= MAX_REASONABLE_ODOMETER_KM
            "trip1AvgSpeed", "trip2AvgSpeed" -> value >= 0f && value <= 400f
            else -> true
        }
        if (!valid) {
            diagnostics += "$label invalidFloat=$value"
            return null
        }
        return value
    }

    private class WrapperBridge private constructor(
        private val wrapper: Any,
    ) {
        companion object {
            fun create(context: Context, diagnostics: MutableList<String>): WrapperBridge? {
                val artifacts = listOf(
                    "/system/framework/AdapterAPI.jar",
                    "/system/framework/AdapterAPIImpl.jar",
                    "/system/framework/DFS.jar",
                )
                val existing = artifacts.filter { File(it).exists() }
                diagnostics += "wrapper.artifacts=$existing"
                if (existing.isEmpty()) return null

                val dexDir = File(context.codeCacheDir, "fuel_energy_wrapper_dex")
                dexDir.mkdirs()
                val loader = DexClassLoader(
                    existing.joinToString(File.pathSeparator),
                    dexDir.absolutePath,
                    null,
                    context.classLoader,
                )
                return try {
                    val carClass = loader.loadClass("com.ecarx.xui.adaptapi.car.Car")
                    val wrapper = runCatching {
                        diagnostics += "wrapper.createWrapper signature=Context"
                        carClass.getMethod("createWrapper", Context::class.java).invoke(null, context)
                    }.recoverCatching {
                        diagnostics += "wrapper.createWrapper fallback=WrapperImpl(Context)"
                        val implClass = loader.loadClass("com.ecarx.xui.adaptapi.car.impl.WrapperImpl")
                        implClass.getConstructor(Context::class.java).newInstance(context)
                    }.getOrThrow()
                    if (wrapper == null) {
                        diagnostics += "wrapper.createWrapper -> null"
                        null
                    } else {
                        diagnostics += "wrapper.createWrapper ok"
                        WrapperBridge(wrapper)
                    }
                } catch (t: Throwable) {
                    diagnostics += "wrapper.error=${t.javaClass.simpleName}: ${t.message}"
                    null
                }
            }
        }

        fun resolvePropertyId(apiId: Int, isFunctionType: Boolean, diagnostics: MutableList<String>): Int? {
            val wrappedType = if (isFunctionType) 2 else 3
            return try {
                val wrappedObj = wrappedProperty(apiId, isFunctionType)
                val propId = wrappedObj?.javaClass?.getMethod("getPropertyId")?.invoke(wrappedObj) as? Int
                diagnostics += "wrapper.map api=$apiId type=$wrappedType -> $propId"
                propId
            } catch (t: Throwable) {
                diagnostics += "wrapper.map api=$apiId type=$wrappedType error=${t.javaClass.simpleName}"
                null
            }
        }

        fun readAdaptedInt(
            manager: Any,
            apiId: Int,
            isFunctionType: Boolean,
            areaId: Int,
            diagnostics: MutableList<String>,
            label: String,
        ): Int? = runCatching {
            val wrappedObj = wrappedProperty(apiId, isFunctionType)
                ?: error("wrapper property unavailable")
            val propertyId = wrappedObj.javaClass.getMethod("getPropertyId").invoke(wrappedObj) as Int
            val rawValue = manager.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, propertyId, areaId) as Int
            val adaptMethod = wrappedObj.javaClass.methods.first {
                it.name == "getPropertyAdaptValue" && it.parameterCount == 1
            }
            val apiValue = (adaptMethod.invoke(wrappedObj, rawValue) as Number).toInt()
            diagnostics += "$label=$apiValue raw=$rawValue propertyId=$propertyId"
            apiValue
        }.getOrElse {
            diagnostics += "$label adapt error=${it.javaClass.simpleName}: ${it.message}"
            null
        }

        fun writeAdaptedInt(
            manager: Any,
            apiId: Int,
            isFunctionType: Boolean,
            areaId: Int,
            apiValue: Int,
            diagnostics: MutableList<String>,
        ): Boolean = runCatching {
            val wrappedObj = wrappedProperty(apiId, isFunctionType)
                ?: error("wrapper property unavailable")
            val propertyId = wrappedObj.javaClass.getMethod("getPropertyId").invoke(wrappedObj) as Int
            val valueMethod = wrappedObj.javaClass.methods.first {
                it.name == "getPropertyValue" && it.parameterCount == 1
            }
            val rawValue = (valueMethod.invoke(wrappedObj, apiValue) as Number).toInt()
            manager.javaClass
                .getMethod(
                    "setIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(manager, propertyId, areaId, rawValue)
            diagnostics += "wrapper.write api=$apiId propertyId=$propertyId apiValue=$apiValue raw=$rawValue"
            true
        }.getOrElse {
            diagnostics += "wrapper.write api=$apiId error=${it.javaClass.simpleName}: ${it.message}"
            false
        }

        fun writeBoolean(
            manager: Any,
            apiId: Int,
            isFunctionType: Boolean,
            areaId: Int,
            value: Boolean,
            diagnostics: MutableList<String>,
        ): Boolean = runCatching {
            val propertyId = resolvePropertyId(apiId, isFunctionType, diagnostics)
                ?: error("wrapper property unavailable")
            manager.javaClass
                .getMethod(
                    "setBooleanProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                )
                .invoke(manager, propertyId, areaId, value)
            diagnostics += "wrapper.writeBoolean api=$apiId propertyId=$propertyId value=$value"
            true
        }.getOrElse {
            diagnostics += "wrapper.writeBoolean api=$apiId error=${it.javaClass.simpleName}: ${it.message}"
            false
        }

        private fun wrappedProperty(apiId: Int, isFunctionType: Boolean): Any? {
            val wrappedType = if (isFunctionType) 2 else 3
            return wrapper.javaClass
                .getMethod(
                    "getWrappedPropertyId",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(wrapper, wrappedType, apiId)
        }
    }
}
