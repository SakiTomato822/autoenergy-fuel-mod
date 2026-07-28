package com.lynk.dvrprobe

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.math.roundToInt

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
    private val vhalSource = VhalGrpcSource()

    companion object {
        private const val DIRECT_INFO_FUEL_CAPACITY = 291504388
        private const val DIRECT_FUEL_LEVEL = 291504903
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

        private fun throwableSummary(throwable: Throwable): String {
            var current = throwable
            val visited = mutableSetOf<Throwable>()
            while (visited.add(current)) {
                val next = when (current) {
                    is InvocationTargetException -> current.targetException ?: current.cause
                    else -> current.cause
                }
                if (next == null || next === current) break
                current = next
            }
            return "${current.javaClass.simpleName}: ${current.message}"
        }
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

                val fuelPercentProp = wrapperBridge.resolvePropertyId(API_FUEL_PERCENT, false, diagnostics)
                val oilRangeProp = wrapperBridge.resolvePropertyId(API_OIL_RANGE, false, diagnostics)
                val avgFuelProp = wrapperBridge.resolvePropertyId(API_AVG_FUEL, false, diagnostics)
                val tripDistanceProp = wrapperBridge.resolvePropertyId(API_TRIP_TOTAL_DISTANCE, true, diagnostics)
                val tripSpeedProp = wrapperBridge.resolvePropertyId(API_TRIP_AVG_SPEED, true, diagnostics)
                val tripDurationProp = wrapperBridge.resolvePropertyId(API_TRIP_TOTAL_DURATION, true, diagnostics)

                // The Flyme adapter returns Integer tenths for average fuel and trip distance
                // on this DHU. Reading them through getFloatProperty causes ClassCastException.
                val avgFuelTrip1 = avgFuelProp?.let {
                    readNumericProperty(mgr, it, 1, diagnostics, "avgFuelTrip1", integerScale = 0.1f)
                } ?: vhalSource.readNumeric(API_AVG_FUEL, 1, diagnostics, "avgFuelTrip1", integerScale = 0.1f)
                val avgFuelTrip2 = avgFuelProp?.let {
                    readNumericProperty(mgr, it, 2, diagnostics, "avgFuelTrip2", integerScale = 0.1f)
                } ?: vhalSource.readNumeric(API_AVG_FUEL, 2, diagnostics, "avgFuelTrip2", integerScale = 0.1f)

                val trip1Distance = tripDistanceProp?.let {
                    readNumericProperty(mgr, it, 1, diagnostics, "trip1Distance", integerScale = 0.1f)
                } ?: vhalSource.readNumeric(API_TRIP_TOTAL_DISTANCE, 1, diagnostics, "trip1Distance", integerScale = 0.1f)
                val trip1Speed = tripSpeedProp?.let {
                    readNumericProperty(mgr, it, 1, diagnostics, "trip1AvgSpeed")
                } ?: vhalSource.readNumeric(API_TRIP_AVG_SPEED, 1, diagnostics, "trip1AvgSpeed")
                val trip1Duration = tripDurationProp?.let { readWrappedInt(mgr, it, 1, diagnostics, "trip1Duration") }
                    ?: vhalSource.readNumeric(API_TRIP_TOTAL_DURATION, 1, diagnostics, "trip1Duration")?.roundToInt()

                val trip2Distance = tripDistanceProp?.let {
                    readNumericProperty(mgr, it, 2, diagnostics, "trip2Distance", integerScale = 0.1f)
                } ?: vhalSource.readNumeric(API_TRIP_TOTAL_DISTANCE, 2, diagnostics, "trip2Distance", integerScale = 0.1f)
                val trip2Speed = tripSpeedProp?.let {
                    readNumericProperty(mgr, it, 2, diagnostics, "trip2AvgSpeed")
                } ?: vhalSource.readNumeric(API_TRIP_AVG_SPEED, 2, diagnostics, "trip2AvgSpeed")
                val trip2Duration = tripDurationProp?.let { readWrappedInt(mgr, it, 2, diagnostics, "trip2Duration") }
                    ?: vhalSource.readNumeric(API_TRIP_TOTAL_DURATION, 2, diagnostics, "trip2Duration")?.roundToInt()

                val vendorFuelPercent = fuelPercentProp?.let {
                    readWrappedInt(mgr, it, 0, diagnostics, "fuelPercent")
                }
                val fuelLevel = readNumericProperty(
                    mgr,
                    DIRECT_FUEL_LEVEL,
                    0,
                    diagnostics,
                    "fuelLevel",
                ) ?: vhalSource.readNumeric(DIRECT_FUEL_LEVEL, 0, diagnostics, "fuelLevel")
                val fuelCapacity = readNumericProperty(
                    mgr,
                    DIRECT_INFO_FUEL_CAPACITY,
                    0,
                    diagnostics,
                    "fuelCapacity",
                ) ?: vhalSource.readNumeric(DIRECT_INFO_FUEL_CAPACITY, 0, diagnostics, "fuelCapacity")
                val directFuelPercent = fuelLevel
                    ?.takeIf { it in 0f..100f && (fuelCapacity ?: 0f) > 100f }
                    ?.roundToInt()
                    ?.also { diagnostics += "fuelPercent=$it accepted from VHAL fuelLevel percentage-like value" }
                val derivedFuelPercent = directFuelPercent ?: deriveFuelPercent(fuelLevel, fuelCapacity, diagnostics)
                val fuelPercent = vendorFuelPercent ?: derivedFuelPercent

                val standardRange = (readNumericProperty(
                    mgr,
                    DIRECT_RANGE_REMAINING,
                    0,
                    diagnostics,
                    "rangeRemaining",
                ) ?: vhalSource.readNumeric(DIRECT_RANGE_REMAINING, 0, diagnostics, "rangeRemaining"))?.roundToInt()
                val vendorOilRange = oilRangeProp?.let {
                    readNumericProperty(mgr, it, 0, diagnostics, "oilRange")?.roundToInt()
                }
                val calculatedRange = deriveRange(
                    fuelLevel = fuelLevel,
                    fuelCapacity = fuelCapacity,
                    fuelPercent = fuelPercent,
                    averageFuel = avgFuelTrip2 ?: avgFuelTrip1,
                    diagnostics = diagnostics,
                )
                val oilRange = vendorOilRange ?: standardRange ?: calculatedRange
                val totalRange = standardRange ?: oilRange

                val odometer = readNumericProperty(
                    mgr,
                    DIRECT_PERF_ODOMETER,
                    0,
                    diagnostics,
                    "perfOdometer",
                ) ?: vhalSource.readNumeric(DIRECT_PERF_ODOMETER, 0, diagnostics, "perfOdometer")
                val singleTripResetOption = wrapperBridge.readAdaptedInt(
                    manager = mgr,
                    API_SINGLE_TRIP_RESET_OPTION,
                    isFunctionType = true,
                    areaId = 0,
                    diagnostics = diagnostics,
                    label = "singleTripResetOption",
                )

                vhalSource.appendDiagnostics(diagnostics)
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
            diagnostics += "reader.error=${throwableSummary(t)}"
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

    fun close() {
        vhalSource.close()
    }

    fun writeSingleTripResetOption(value: Int): Boolean {
        val diagnostics = mutableListOf<String>()
        val result = withPropertyManager { mgr ->
            val bridge = WrapperBridge.create(context, diagnostics)
            bridge.writeAdaptedInt(
                manager = mgr,
                apiId = API_SINGLE_TRIP_RESET_OPTION,
                isFunctionType = true,
                areaId = 0,
                apiValue = value,
                diagnostics = diagnostics,
            )
        } ?: false
        diagnostics.forEach { AppLog.d("WRITE", it) }
        if (result) {
            AppLog.i("WRITE", "single-trip reset option succeeded value=$value")
        } else {
            AppLog.w("WRITE", "single-trip reset option failed value=$value")
        }
        return result
    }

    fun resetSubtotalTrip(): Boolean {
        val diagnostics = mutableListOf<String>()
        val result = withPropertyManager { mgr ->
            val bridge = WrapperBridge.create(context, diagnostics)
            bridge.writeBoolean(
                manager = mgr,
                apiId = API_SUBTOTAL_TRIP_RESET,
                isFunctionType = true,
                areaId = 0,
                value = true,
                diagnostics = diagnostics,
            )
        } ?: false
        diagnostics.forEach { AppLog.d("WRITE", it) }
        if (result) {
            AppLog.i("WRITE", "subtotal reset succeeded")
        } else {
            AppLog.w("WRITE", "subtotal reset failed")
        }
        return result
    }

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
    }.onFailure {
        AppLog.e("CAR", "failed to acquire/use CarPropertyManager", it)
    }.getOrNull()

    private fun readWrappedInt(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Int? {
        return runCatching {
            val value = mgr.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, propId, areaId) as Int
            diagnostics += "$label=$value via wrapped.getIntProperty($propId,$areaId)"
            sanitizeInt(label, value, diagnostics)
        }.getOrElse {
            diagnostics += "$label error=${throwableSummary(it)} wrapped.getIntProperty($propId,$areaId)"
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
            diagnostics += "$label error=${throwableSummary(it)} wrapped.getFloatProperty($propId,$areaId)"
            null
        }
    }

    /**
     * Reads CarPropertyValue without declaring an expected boxed type. ECARX/Flyme exposes
     * several logical float properties as Integer tenths, and the typed API then throws a
     * ClassCastException before the app can inspect the actual value.
     */
    private fun readNumericProperty(
        mgr: Any,
        propId: Int,
        areaId: Int,
        diagnostics: MutableList<String>,
        label: String,
        integerScale: Float = 1f,
    ): Float? {
        val genericResult = runCatching {
            val propertyValue = mgr.javaClass
                .getMethod(
                    "getProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(mgr, propId, areaId)
                ?: error("getProperty returned null")
            propertyValue.javaClass.getMethod("getValue").invoke(propertyValue) as? Number
                ?: error("property value is not numeric")
        }

        genericResult.getOrNull()?.let { raw ->
            val isInteger = raw is Byte || raw is Short || raw is Int || raw is Long
            val scale = if (isInteger) integerScale else 1f
            val value = raw.toFloat() * scale
            diagnostics += "$label=$value raw=$raw type=${raw.javaClass.simpleName} scale=$scale via getProperty($propId,$areaId)"
            return sanitizeFloat(label, value, diagnostics)
        }

        genericResult.exceptionOrNull()?.let {
            diagnostics += "$label error=${throwableSummary(it)} getProperty($propId,$areaId)"
        }

        return runCatching {
            val value = mgr.javaClass
                .getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, propId, areaId) as Float
            diagnostics += "$label=$value via getFloatProperty($propId,$areaId) fallback"
            sanitizeFloat(label, value, diagnostics)
        }.getOrElse {
            diagnostics += "$label error=${throwableSummary(it)} getFloatProperty($propId,$areaId) fallback"
            null
        }
    }

    private fun deriveFuelPercent(
        fuelLevel: Float?,
        fuelCapacity: Float?,
        diagnostics: MutableList<String>,
    ): Int? {
        if (fuelLevel == null || fuelCapacity == null || fuelCapacity <= 0f) return null
        val percent = (fuelLevel / fuelCapacity * 100f).roundToInt()
        if (percent !in 0..100) {
            diagnostics += "fuelPercent derived invalid=$percent level=$fuelLevel capacity=$fuelCapacity"
            return null
        }
        diagnostics += "fuelPercent=$percent derived from standard fuelLevel/fuelCapacity"
        return percent
    }

    private fun deriveRange(
        fuelLevel: Float?,
        fuelCapacity: Float?,
        fuelPercent: Int?,
        averageFuel: Float?,
        diagnostics: MutableList<String>,
    ): Int? {
        if (fuelLevel == null || averageFuel == null || averageFuel <= 0.1f) return null

        // Android's standard FUEL_LEVEL and INFO_FUEL_CAPACITY use millilitres.
        // A few vendor implementations expose litres instead, so capacity identifies the unit.
        val fuelLitres = if ((fuelCapacity ?: fuelLevel) > 500f) fuelLevel / 1000f else fuelLevel
        val range = (fuelLitres / averageFuel * 100f).roundToInt()
        if (range !in 0..MAX_REASONABLE_RANGE_KM) {
            diagnostics += "oilRange derived invalid=$range fuelLitres=$fuelLitres avg=$averageFuel"
            return null
        }
        diagnostics += "oilRange=$range derived fuelLitres=$fuelLitres avg=$averageFuel fuelPercent=$fuelPercent"
        return range
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
            diagnostics += "$label error=${throwableSummary(it)} direct.getFloatProperty($propId,$areaId)"
            null
        }
    }

    private fun readDirectPropertyFloat(mgr: Any, propId: Int, areaId: Int, diagnostics: MutableList<String>, label: String): Float? {
        val reflected = runCatching {
            val valueObj = mgr.javaClass
                .getMethod("getProperty", Class::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(mgr, Float::class.javaObjectType, propId, areaId)
            val value = valueObj?.javaClass?.getMethod("getValue")?.invoke(valueObj) as? Float
            diagnostics += "$label=$value via direct.getProperty<Float>($propId,$areaId)"
            sanitizeFloat(label, value, diagnostics)
        }
        reflected.getOrNull()?.let { return it }
        reflected.exceptionOrNull()?.let {
            diagnostics += "$label error=${throwableSummary(it)} direct.getProperty<Float>($propId,$areaId)"
        }
        diagnostics += "$label fallback=direct.getFloatProperty($propId,$areaId)"
        return readWrappedFloat(mgr, propId, areaId, diagnostics, label)
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
            "fuelLevel", "fuelCapacity" -> value >= 0f && value <= 500_000f
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
        private val wrapper: Any?,
    ) {
        companion object {
            fun create(context: Context, diagnostics: MutableList<String>): WrapperBridge {
                val artifacts = listOf(
                    "/system/framework/AdapterAPI.jar",
                    "/system/framework/AdapterAPIImpl.jar",
                    "/system/framework/DFS.jar",
                )
                val existing = artifacts.filter { File(it).exists() }
                diagnostics += "wrapper.artifacts=$existing"
                if (existing.isEmpty()) {
                    diagnostics += "wrapper.identity enabled reason=no AdapterAPI artifacts"
                    return WrapperBridge(null)
                }

                val dexDir = File(context.codeCacheDir, "fuel_energy_wrapper_dex")
                dexDir.mkdirs()
                val loader = DexClassLoader(
                    existing.joinToString(File.pathSeparator),
                    dexDir.absolutePath,
                    null,
                    context.classLoader,
                )
                val factoryResult = runCatching {
                    val carClass = loader.loadClass("com.ecarx.xui.adaptapi.car.Car")
                    val method = carClass.getMethod("createWrapper", Context::class.java)
                    diagnostics += "wrapper.createWrapper exact=${methodSignature(method.parameterTypes)}"
                    method.invoke(null, context) ?: error("createWrapper returned null")
                }
                factoryResult.getOrNull()?.let {
                    diagnostics += "wrapper.createWrapper ok class=${it.javaClass.name}"
                    return WrapperBridge(it)
                }
                factoryResult.exceptionOrNull()?.let {
                    diagnostics += "wrapper.factory error=${throwableSummary(it)}"
                }

                val implementationResult = runCatching {
                    val implClass = loader.loadClass("com.ecarx.xui.adaptapi.car.impl.WrapperImpl")
                    diagnostics += "wrapper.impl classLoader=${implClass.classLoader?.javaClass?.name}"
                    diagnostics += "wrapper.impl trying=(android.content.Context)"
                    implClass.getDeclaredConstructor(Context::class.java)
                        .apply { isAccessible = true }
                        .newInstance(context)
                }
                implementationResult.getOrNull()?.let {
                    diagnostics += "wrapper.impl ok class=${it.javaClass.name}"
                    return WrapperBridge(it)
                }
                implementationResult.exceptionOrNull()?.let {
                    diagnostics += "wrapper.impl error=${throwableSummary(it)}"
                }

                diagnostics += "wrapper.identity enabled reason=wrapper unavailable; property ids pass through unchanged"
                return WrapperBridge(null)
            }

            private fun methodSignature(parameterTypes: Array<Class<*>>): String {
                return parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
            }
        }

        fun resolvePropertyId(apiId: Int, isFunctionType: Boolean, diagnostics: MutableList<String>): Int? {
            val wrappedType = if (isFunctionType) 2 else 3
            if (wrapper == null) {
                diagnostics += "wrapper.map identity api=$apiId type=$wrappedType -> $apiId"
                return apiId
            }
            return try {
                val wrappedObj = wrappedProperty(apiId, isFunctionType)
                val propId = wrappedObj?.javaClass?.getMethod("getPropertyId")?.invoke(wrappedObj) as? Int
                diagnostics += "wrapper.map api=$apiId type=$wrappedType -> $propId"
                propId
            } catch (t: Throwable) {
                diagnostics += "wrapper.map api=$apiId type=$wrappedType error=${throwableSummary(t)}"
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
            if (wrapper == null) {
                val rawValue = manager.javaClass
                    .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(manager, apiId, areaId) as Int
                diagnostics += "$label=$rawValue identity propertyId=$apiId"
                return@runCatching rawValue
            }
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
            diagnostics += "$label adapt error=${throwableSummary(it)}"
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
            val propertyId: Int
            val rawValue: Int
            if (wrapper == null) {
                propertyId = apiId
                rawValue = apiValue
            } else {
                val wrappedObj = wrappedProperty(apiId, isFunctionType)
                    ?: error("wrapper property unavailable")
                propertyId = wrappedObj.javaClass.getMethod("getPropertyId").invoke(wrappedObj) as Int
                val valueMethod = wrappedObj.javaClass.methods.first {
                    it.name == "getPropertyValue" && it.parameterCount == 1
                }
                rawValue = (valueMethod.invoke(wrappedObj, apiValue) as Number).toInt()
            }
            manager.javaClass
                .getMethod(
                    "setIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(manager, propertyId, areaId, rawValue)
            diagnostics += "wrapper.write api=$apiId propertyId=$propertyId apiValue=$apiValue raw=$rawValue identity=${wrapper == null}"
            true
        }.getOrElse {
            diagnostics += "wrapper.write api=$apiId error=${throwableSummary(it)}"
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
            diagnostics += "wrapper.writeBoolean api=$apiId propertyId=$propertyId value=$value identity=${wrapper == null}"
            true
        }.getOrElse {
            diagnostics += "wrapper.writeBoolean api=$apiId error=${throwableSummary(it)}"
            false
        }

        private fun wrappedProperty(apiId: Int, isFunctionType: Boolean): Any? {
            val actualWrapper = wrapper ?: return null
            val wrappedType = if (isFunctionType) 2 else 3
            return actualWrapper.javaClass
                .getMethod(
                    "getWrappedPropertyId",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(actualWrapper, wrappedType, apiId)
        }
    }
}
