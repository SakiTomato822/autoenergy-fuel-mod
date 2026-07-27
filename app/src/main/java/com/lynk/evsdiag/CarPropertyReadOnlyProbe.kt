package com.lynk.dvrprobe

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File

object CarPropertyReadOnlyProbe {
    private data class PropTarget(
        val id: Int,
        val name: String,
        val source: String,
    )

    private val directDvrProps = listOf(
        PropTarget(695206090, "INFO_ID_DVR_SYSTEMSTS", "vehicle-hal"),
        PropTarget(695206091, "INFO_ID_DVR_RECORDING_CYCLESTS", "vehicle-hal"),
        PropTarget(695206092, "INFO_ID_DVR_SDCARDSTS", "vehicle-hal"),
        PropTarget(695206093, "INFO_ID_DVR_SDFORMATRESULT", "vehicle-hal"),
        PropTarget(695206094, "INFO_ID_DVR_HOME", "vehicle-hal"),
        PropTarget(695206095, "INFO_ID_DVR_RESTORE_FACTORY_SETTINGS", "vehicle-hal"),
        PropTarget(695206096, "INFO_ID_DVR_COMMANDRESPLIST", "vehicle-hal"),
        PropTarget(695206097, "INFO_ID_DVR_CURRENTVIDEO_COUNTS", "vehicle-hal"),
        PropTarget(695206098, "INFO_ID_DVR_COMMAND", "vehicle-hal-write-like-do-not-set"),
        PropTarget(695206099, "INFO_ID_DVR_CONTROL", "vehicle-hal-write-like-do-not-set"),
    )

    private val wrappedDvrProps = listOf(
        PropTarget(889259008, "DVR_FUNC_CAMERA_OPERATION_STATUS", "wrapper/SystemUI"),
        PropTarget(889261568, "DVR_FUNC_SDCARD_STATUS", "wrapper/SystemUI"),
    )

    fun run(context: Context, artifacts: List<String>, log: (String) -> Unit) {
        log("  safety: read-only only; no setProperty/no control/no record/no format")
        probeEcarxWrapper(context, artifacts, log)
        probeAndroidCarProperty(context, log)
    }

    private fun probeEcarxWrapper(context: Context, artifacts: List<String>, log: (String) -> Unit) {
        log("  ecarx wrapper mapping:")
        val loader = buildLoader(context, artifacts)
        val carClass = loadClass(loader, "com.ecarx.xui.adaptapi.car.Car", log) ?: return
        val wrapper = try {
            carClass.getMethod("createWrapper", Context::class.java).invoke(null, context)
        } catch (t: Throwable) {
            log("    createWrapper(context) failed: ${unwrapThrowable(t)}")
            return
        }
        if (wrapper == null) {
            log("    createWrapper(context) -> null")
            return
        }

        wrappedDvrProps.forEach { target ->
            listOf(2 to "ID_TYPE_FUNCTION", 4 to "ID_TYPE_DVR").forEach { (type, typeName) ->
                val propertyId = try {
                    val obj = wrapper.javaClass
                        .getMethod("getWrappedPropertyId", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        .invoke(wrapper, type, target.id)
                    obj?.javaClass?.getMethod("getPropertyId")?.invoke(obj)
                } catch (t: Throwable) {
                    "error(${unwrapThrowable(t)})"
                }
                log("    ${target.name} wrapperId=${target.id} via $typeName -> propertyId=$propertyId")
            }
        }
    }

    private fun probeAndroidCarProperty(context: Context, log: (String) -> Unit) {
        log("  android.car CarPropertyManager:")
        val carClass = try {
            Class.forName("android.car.Car")
        } catch (t: Throwable) {
            log("    android.car.Car unavailable: ${unwrapThrowable(t)}")
            return
        }

        val car = try {
            carClass.getMethod("createCar", Context::class.java).invoke(null, context)
        } catch (t: Throwable) {
            log("    Car.createCar(context) failed: ${unwrapThrowable(t)}")
            return
        }
        if (car == null) {
            log("    Car.createCar(context) -> null")
            return
        }

        try {
            val serviceName = runCatching { carClass.getField("PROPERTY_SERVICE").get(null) as String }
                .getOrElse { "property" }
            val mgr = try {
                car.javaClass.getMethod("getCarManager", String::class.java).invoke(car, serviceName)
            } catch (t: Throwable) {
                log("    getCarManager($serviceName) failed: ${unwrapThrowable(t)}")
                null
            }
            if (mgr == null) {
                log("    property manager -> null")
                return
            }

            (directDvrProps + wrappedDvrProps).forEach { target ->
                probeOneProperty(mgr, target, log)
            }
            probePropertyList(mgr, log)
        } finally {
            runCatching { car.javaClass.getMethod("disconnect").invoke(car) }
        }
    }

    private fun probeOneProperty(mgr: Any, target: PropTarget, log: (String) -> Unit) {
        log("    prop ${target.id} ${target.name} [${target.source}]")
        val config = try {
            mgr.javaClass.getMethod("getCarPropertyConfig", Int::class.javaPrimitiveType).invoke(mgr, target.id)
        } catch (t: Throwable) {
            log("      config.error=${unwrapThrowable(t)}")
            null
        }
        if (config == null) {
            log("      config=null")
            val value = readValueForArea(mgr, target.id, 0)
            log("      value.area0=$value")
            return
        }

        log("      config=${describeConfig(config)}")
        val areas = readIntArray(config, "getAreaIds")
            ?.takeIf { it.isNotEmpty() }
            ?: intArrayOf(0)
        areas.forEach { areaId ->
            log("      value.area=$areaId ${readValueForArea(mgr, target.id, areaId)}")
        }
    }

    private fun readValueForArea(mgr: Any, propId: Int, areaId: Int): String {
        val attempts = mutableListOf<String>()

        listOf("getIntProperty", "getBooleanProperty", "getFloatProperty").forEach { methodName ->
            try {
                val value = mgr.javaClass
                    .getMethod(methodName, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(mgr, propId, areaId)
                return "$methodName=$value"
            } catch (t: Throwable) {
                attempts += "$methodName:${shortThrowable(t)}"
            }
        }

        listOf(
            Int::class.javaObjectType,
            java.lang.Boolean::class.javaObjectType,
            java.lang.Float::class.javaObjectType,
            java.lang.Long::class.javaObjectType,
            String::class.java,
        ).forEach { typeClass ->
            try {
                val valueObj = mgr.javaClass
                    .getMethod("getProperty", Class::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(mgr, typeClass, propId, areaId)
                return "getProperty<${typeClass.simpleName}>=${describeCarPropertyValue(valueObj)}"
            } catch (t: Throwable) {
                attempts += "getProperty<${typeClass.simpleName}>:${shortThrowable(t)}"
            }
        }

        return "unreadable(${attempts.joinToString(" | ")})"
    }

    private fun probePropertyList(mgr: Any, log: (String) -> Unit) {
        val idsOfInterest = (directDvrProps + wrappedDvrProps).map { it.id }.toSet()
        val list = try {
            mgr.javaClass.getMethod("getPropertyList").invoke(mgr)
        } catch (t: Throwable) {
            log("    getPropertyList.error=${unwrapThrowable(t)}")
            return
        }
        val configs = (list as? Iterable<*>)?.toList().orEmpty()
        log("    getPropertyList.count=${configs.size}")
        configs.forEach { config ->
            if (config == null) return@forEach
            val id = runCatching {
                config.javaClass.getMethod("getPropertyId").invoke(config) as Int
            }.getOrNull()
            if (id in idsOfInterest) {
                log("    getPropertyList.hit=${describeConfig(config)}")
            }
        }
    }

    private fun describeConfig(config: Any): String {
        val id = safeInvoke(config, "getPropertyId")
        val areaIds = readIntArray(config, "getAreaIds")?.joinToString(prefix = "[", postfix = "]") ?: "?"
        val access = safeInvoke(config, "getAccess")
        val changeMode = safeInvoke(config, "getChangeMode")
        val configArray = safeInvoke(config, "getConfigArray")
        return "id=$id areas=$areaIds access=$access changeMode=$changeMode configArray=$configArray"
    }

    private fun describeCarPropertyValue(valueObj: Any?): String {
        if (valueObj == null) return "null"
        val propId = safeInvoke(valueObj, "getPropertyId")
        val areaId = safeInvoke(valueObj, "getAreaId")
        val value = safeInvoke(valueObj, "getValue")
        val status = safeInvoke(valueObj, "getStatus")
        val timestamp = safeInvoke(valueObj, "getTimestamp")
        return "prop=$propId area=$areaId value=$value status=$status timestamp=$timestamp"
    }

    private fun safeInvoke(target: Any, methodName: String): String {
        return try {
            target.javaClass.getMethod(methodName).invoke(target)?.toString() ?: "null"
        } catch (t: Throwable) {
            "error(${shortThrowable(t)})"
        }
    }

    private fun readIntArray(target: Any, methodName: String): IntArray? {
        return try {
            target.javaClass.getMethod(methodName).invoke(target) as? IntArray
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildLoader(context: Context, artifacts: List<String>): DexClassLoader {
        val dexDir = File(context.codeCacheDir, "car_property_probe_dex")
        dexDir.mkdirs()
        val classpath = artifacts
            .map(::File)
            .filter(File::exists)
            .joinToString(File.pathSeparator) { it.absolutePath }
        return DexClassLoader(classpath, dexDir.absolutePath, null, context.classLoader)
    }

    private fun loadClass(loader: ClassLoader, className: String, log: (String) -> Unit): Class<*>? {
        return try {
            loader.loadClass(className)
        } catch (t: Throwable) {
            log("    loadClass($className) failed: ${unwrapThrowable(t)}")
            null
        }
    }

    private fun unwrapThrowable(t: Throwable): String {
        val cause = if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    private fun shortThrowable(t: Throwable): String {
        val cause = if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
        return cause.javaClass.simpleName
    }
}
