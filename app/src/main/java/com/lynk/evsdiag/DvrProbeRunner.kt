package com.lynk.dvrprobe

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class DvrProbeResult(
    val sessionDir: File,
    val reportFile: File,
)

private data class BoundService(
    val binder: IBinder?,
    val bound: Boolean,
    val error: String?,
    val connection: ServiceConnection?,
)

class DvrProbeRunner(private val context: Context) {
    suspend fun run(rootGranted: Boolean, onLog: (String) -> Unit): DvrProbeResult = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val sessionDir = File(baseDir, "dvr_readonly_probe/$stamp")
        sessionDir.mkdirs()
        val reportFile = File(sessionDir, "report.txt")
        val report = mutableListOf<String>()

        fun log(line: String) {
            report += line
            onLog(line)
        }

        val artifacts = listOf(
            "/system/framework/AdapterAPI.jar",
            "/system/framework/AdapterAPIImpl.jar",
            "/system/framework/DFS.jar",
            "/system/app/GeelyXSFEASCoreService/GeelyXSFEASCoreService.apk",
            "/system/app/DimService/DimService.apk",
            "/system/priv-app/GeelyXSFDeviceService/GeelyXSFDeviceService.apk",
            "/system/app/GeelyXSFCarService/GeelyXSFCarService.apk",
        )

        log("session: ${sessionDir.absolutePath}")
        log("package: ${context.packageName}")
        log("pid/uid: ${Process.myPid()}/${Process.myUid()}")
        log("artifacts:")
        artifacts.forEach { path ->
            val file = File(path)
            log("  ${if (file.exists()) "ok" else "missing"}  $path")
        }

        log("probe1: bind OpenAPI service")
        val openApiBound = bindService(Intent("ecarx.intent.action.OpenAPIService").setPackage("com.ecarx.sdk.openapi"))
        try {
            probeOpenApi(openApiBound, ::log)
        } finally {
            unbindQuietly(openApiBound)
        }

        log("probe2: bind DeviceInfo service")
        val deviceInfoBound = bindService(
            Intent("com.ecarx.deviceinfo.service.BIND_SERVICE").setPackage("com.ecarx.deviceinfo.service"),
        )
        try {
            probeDeviceInfo(deviceInfoBound, ::log)
        } finally {
            unbindQuietly(deviceInfoBound)
        }

        log("probe3: reflect new AdaptAPI stack")
        runNewAdaptApiProbe(artifacts, ::log)

        log("probe4: reflect old DVR SDK stack")
        runOldDvrApiProbe(artifacts, ::log)

        log("probe5: read-only CarProperty DVR ids")
        CarPropertyReadOnlyProbe.run(context, artifacts, ::log)

        if (rootGranted) {
            log("probe6: root logcat grep")
            val logcat = RootShell.run(
                "logcat -d | grep -Ei 'dvr|blackbox|dashcam|recorder|sdcard|tfcard|DVR_CAMERA|SET_DVR|RECORD_LOOP|69520609|889259008|889261568|StatusIcon_Dvr|VehicleDataBuilder|CarProperty|McuManager' | tail -n 180",
                timeoutMs = 15_000,
            )
            log("  logcat exit=${logcat.exitCode} timedOut=${logcat.timedOut}")
            if (logcat.stdout.isNotBlank()) {
                log("  ---- logcat ----")
                logcat.stdout.lineSequence().forEach { line -> log("  $line") }
            } else {
                log("  no matching logcat lines")
            }
            if (logcat.stderr.isNotBlank()) {
                log("  stderr=${logcat.stderr}")
            }
        }

        reportFile.writeText(report.joinToString("\n"))
        if (rootGranted) {
            val publicDir = "/sdcard/Download/dvr_readonly_probe"
            val copyName = "report_$stamp.txt"
            val copy = RootShell.run(
                "mkdir -p $publicDir && cp '${reportFile.absolutePath}' '$publicDir/$copyName'",
                timeoutMs = 10_000,
            )
            if (copy.exitCode == 0) {
                onLog("public report: $publicDir/$copyName")
            } else {
                onLog("public report copy failed: exit=${copy.exitCode} stderr=${copy.stderr}")
            }
        }
        DvrProbeResult(sessionDir, reportFile)
    }

    private suspend fun bindService(intent: Intent): BoundService = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var finished = false
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (!finished) {
                        finished = true
                        cont.resume(BoundService(service, bound = true, error = null, connection = this))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit

                override fun onBindingDied(name: ComponentName?) {
                    if (!finished) {
                        finished = true
                        cont.resume(BoundService(null, bound = false, error = "binding died", connection = this))
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (!finished) {
                        finished = true
                        cont.resume(BoundService(null, bound = false, error = "null binding", connection = this))
                    }
                }
            }

            try {
                val ok = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                if (!ok && !finished) {
                    finished = true
                    cont.resume(BoundService(null, bound = false, error = "bindService returned false", connection = conn))
                }
            } catch (t: Throwable) {
                if (!finished) {
                    finished = true
                    cont.resume(BoundService(null, bound = false, error = "${t.javaClass.simpleName}: ${t.message}", connection = conn))
                }
            }

            cont.invokeOnCancellation {
                runCatching { context.unbindService(conn) }
            }
        }
    }

    private suspend fun unbindQuietly(bound: BoundService) = withContext(Dispatchers.Main) {
        val conn = bound.connection ?: return@withContext
        runCatching { context.unbindService(conn) }
    }

    private fun probeOpenApi(bound: BoundService, log: (String) -> Unit) {
        if (!bound.bound || bound.binder == null) {
            log("  bind failed: ${bound.error}")
            return
        }
        val services = callIServicePoolGetAvailableServices(bound.binder)
        log("  availableServices=${services.joinToString(",")}")
        log("  has dvr=${services.contains("dvr")} drivingevent=${services.contains("drivingevent")} deviceservice=${services.contains("deviceservice")}")
        listOf("dvr", "drivingevent", "deviceservice").forEach { name ->
            val serviceBinder = callIServicePoolGetService(bound.binder, name)
            log("  getService($name) -> ${describeBinder(serviceBinder)}")
            if (name == "deviceservice" && serviceBinder != null) {
                log("  deviceservice nested probe:")
                listOf("dvr", "drivingevent", "deviceservice", "vehicle", "device").forEach { nested ->
                    log("    getService($nested) -> ${describeBinder(callDeviceInfoGetService(serviceBinder, nested))}")
                }
            }
        }
    }

    private fun probeDeviceInfo(bound: BoundService, log: (String) -> Unit) {
        if (!bound.bound || bound.binder == null) {
            log("  bind failed: ${bound.error}")
            return
        }
        listOf("dvr", "drivingevent", "deviceservice").forEach { name ->
            log("  getService($name) -> ${describeBinder(callDeviceInfoGetService(bound.binder, name))}")
        }
    }

    private fun runNewAdaptApiProbe(artifacts: List<String>, log: (String) -> Unit) {
        val loader = buildLoader(artifacts)
        listOf(
            "com.ecarx.xui.adaptapi.dvr.forp.Dvr",
            "com.ecarx.xui.adaptapi.dvr.forp.IDvrManager",
            "com.ecarx.xui.adaptapi.dvr.forp.IFileManager",
            "com.ecarx.xui.adaptapi.dvr.impl.DvrImpl",
            "com.ecarx.xui.adaptapi.dvr.forp.impl.DvrForPImpl",
        ).forEach { className ->
            log("  class $className -> ${classAvailability(loader, className)}")
        }

        val dvrClass = loadClass(loader, "com.ecarx.xui.adaptapi.dvr.forp.Dvr", log) ?: return
        log("  Dvr methods=${describeMethodNames(dvrClass)}")
        val dvrObject = invokeStatic(
            targetClass = dvrClass,
            methodName = "create",
            parameterTypes = arrayOf(Context::class.java),
            args = arrayOf(context),
            log = log,
            label = "Dvr.create(context)",
        ) ?: return
        log("  Dvr.create -> ${dvrObject.javaClass.name}")

        val infoObj = invokeInstance(dvrObject, "getDvrInfos", emptyArray(), emptyArray(), log, "getDvrInfos()")
        if (infoObj != null) {
            log("  info.model=${safeInvokeString(infoObj, "getDvrInfoString", 1, log)}")
            log("  info.software=${safeInvokeString(infoObj, "getDvrInfoString", 2, log)}")
        } else {
            log("  getDvrInfos -> null")
        }

        val managerObj = invokeInstance(dvrObject, "getDvrManager", emptyArray(), emptyArray(), log, "getDvrManager()")
        if (managerObj == null) {
            log("  getDvrManager -> null")
            return
        }

        log("  currentState=${safeInvokeValue(managerObj, "getCurrentDvrStates", log)}")
        log("  sdCardState=${safeInvokeValue(managerObj, "getSDCardStates", log)}")
        listOf(
            4097 to "GENERAL_RECORDING",
            4099 to "EMERGENCY_RECORDING",
            4101 to "SDCARD_FORMAT",
            4105 to "SWITCH_CAMERA",
        ).forEach { (op, name) ->
            log("  support.$name=${safeInvokeValue(managerObj, "isDvrOperationSupported", log, op)}")
        }

        val fileManagerObj = invokeInstance(managerObj, "getFileManager", emptyArray(), emptyArray(), log, "getFileManager()")
        if (fileManagerObj == null) {
            log("  getFileManager -> null")
            return
        }

        listOf(
            1 to "emergency",
            2 to "general",
            5 to "parking",
            6 to "avm",
        ).forEach { (type, name) ->
            log("  fileCount.$name=${safeInvokeValue(fileManagerObj, "getDvrFileCount", log, type)}")
        }
        log("  getAllDvrFiles=${describeArrayLike(invokeInstance(fileManagerObj, "getAllDvrFiles", emptyArray(), emptyArray(), log, "getAllDvrFiles()"))}")
    }

    private fun runOldDvrApiProbe(artifacts: List<String>, log: (String) -> Unit) {
        val loader = buildLoader(artifacts)
        listOf(
            "com.ecarx.sdk.dvr.DVRAPI",
            "com.ecarx.sdk.dvr.DVRAPImpl",
            "com.ecarx.sdk.dvr.info.SDCardInfo",
            "com.ecarx.sdk.dvr.info.DVRStatusInfo",
        ).forEach { className ->
            log("  class $className -> ${classAvailability(loader, className)}")
        }

        val apiClass = loadClass(loader, "com.ecarx.sdk.dvr.DVRAPI", log) ?: return
        log("  DVRAPI methods=${describeMethodNames(apiClass)}")
        val apiObj = invokeStatic(
            targetClass = apiClass,
            methodName = "get",
            parameterTypes = arrayOf(Context::class.java),
            args = arrayOf(context),
            log = log,
            label = "DVRAPI.get(context)",
        ) ?: return

        log("  syncCurrentStatus=${safeInvokeVoid(apiObj, "syncCurrentStatus", log)}")
        log("  syncDVRSettingInfo=${safeInvokeValue(apiObj, "syncDVRSettingInfo", log)}")
        log("  getDVRRecStatus=${safeInvokeValue(apiObj, "getDVRRecStatus", log)}")
        log("  getDVRModel=${safeInvokeValue(apiObj, "getDVRModel", log)}")
    }

    private fun buildLoader(artifacts: List<String>): DexClassLoader {
        val dexDir = File(context.codeCacheDir, "dvr_probe_dex")
        dexDir.mkdirs()
        val classpath = artifacts
            .map(::File)
            .filter(File::exists)
            .joinToString(File.pathSeparator) { it.absolutePath }
        return DexClassLoader(classpath, dexDir.absolutePath, null, context.classLoader)
    }

    private fun classAvailability(loader: ClassLoader, className: String): String {
        return try {
            loader.loadClass(className)
            "ok"
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun loadClass(loader: ClassLoader, className: String, log: (String) -> Unit): Class<*>? {
        return try {
            loader.loadClass(className)
        } catch (t: Throwable) {
            log("  loadClass($className) failed: ${unwrapThrowable(t)}")
            null
        }
    }

    private fun invokeStatic(
        targetClass: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>,
        log: (String) -> Unit,
        label: String,
    ): Any? = try {
        targetClass.getMethod(methodName, *parameterTypes).invoke(null, *args)
    } catch (t: Throwable) {
        log("  $label failed: ${unwrapThrowable(t)}")
        null
    }

    private fun invokeInstance(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>,
        log: (String) -> Unit,
        label: String,
    ): Any? = try {
        target.javaClass.getMethod(methodName, *parameterTypes).invoke(target, *args)
    } catch (t: Throwable) {
        log("  $label failed: ${unwrapThrowable(t)}")
        null
    }

    private fun safeInvokeValue(target: Any, methodName: String, log: (String) -> Unit, vararg args: Any): String {
        return try {
            val method = when (args.size) {
                0 -> target.javaClass.getMethod(methodName)
                1 -> target.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                else -> throw IllegalArgumentException("unsupported arg count")
            }
            val value = method.invoke(target, *args)
            value?.toString() ?: "null"
        } catch (t: Throwable) {
            "error(${unwrapThrowable(t)})".also { log("  $methodName failed: ${unwrapThrowable(t)}") }
        }
    }

    private fun safeInvokeString(target: Any, methodName: String, arg: Int, log: (String) -> Unit): String {
        return try {
            val value = target.javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(target, arg)
            value?.toString() ?: "null"
        } catch (t: Throwable) {
            "error(${unwrapThrowable(t)})".also { log("  $methodName($arg) failed: ${unwrapThrowable(t)}") }
        }
    }

    private fun safeInvokeVoid(target: Any, methodName: String, log: (String) -> Unit): String {
        return try {
            target.javaClass.getMethod(methodName).invoke(target)
            "ok"
        } catch (t: Throwable) {
            "error(${unwrapThrowable(t)})".also { log("  $methodName failed: ${unwrapThrowable(t)}") }
        }
    }

    private fun unwrapThrowable(t: Throwable): String {
        val cause = if (t is InvocationTargetException) t.targetException ?: t else t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    private fun describeArrayLike(value: Any?): String {
        if (value == null) return "null"
        return when (value) {
            is Array<*> -> "count=${value.size}"
            is IntArray -> "count=${value.size}"
            else -> value.toString()
        }
    }

    private fun describeBinder(binder: IBinder?): String {
        return if (binder == null) {
            "null"
        } else {
            val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull() ?: "no-desc"
            "non-null($descriptor)"
        }
    }

    private fun describeMethodNames(clazz: Class<*>): String {
        return clazz.methods
            .map { method ->
                val params = method.parameterTypes.joinToString(",") { it.simpleName }
                "${method.name}($params)"
            }
            .distinct()
            .sorted()
            .joinToString("; ")
    }

    private fun callIServicePoolGetAvailableServices(binder: IBinder): List<String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.ecarx.sdk.openapi.IServicePool")
            binder.transact(1, data, reply, 0)
            reply.readException()
            reply.createStringArrayList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun callIServicePoolGetService(binder: IBinder, serviceName: String): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.ecarx.sdk.openapi.IServicePool")
            data.writeInt(Process.myPid())
            data.writeInt(Process.myUid())
            data.writeString(context.packageName)
            data.writeString(serviceName)
            binder.transact(2, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } catch (_: Throwable) {
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun callDeviceInfoGetService(binder: IBinder, serviceName: String): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("com.ecarx.sdk.deviceservice.IDeviceInfoService")
            data.writeString(serviceName)
            binder.transact(1, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } catch (_: Throwable) {
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
