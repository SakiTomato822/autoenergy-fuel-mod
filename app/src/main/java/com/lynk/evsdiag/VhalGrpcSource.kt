package com.lynk.dvrprobe

import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Read-only client for the local VHAL stream exposed by the DHU.
 *
 * This deliberately implements only StartPropertyValuesStream and
 * SendAllPropertyValuesToStream. It never constructs or calls SetProperty.
 */
class VhalGrpcSource : Closeable {
    companion object {
        private const val HOST = "127.0.0.1"
        // EVCC's native VhalNative_getGrpcPort() returns 0x9c44 (40004).
        // Port 8500 exists only in its Java exception fallback.
        private const val PORT = 40004
        private const val CLIENT_ID = "evcam_prop_client"
        private const val STREAM_METHOD = "vhal_proto.VehicleServer/StartPropertyValuesStream"
        private const val SEND_ALL_METHOD = "vhal_proto.VehicleServer/SendAllPropertyValuesToStream"
        private val DIAGNOSTIC_PROPERTY_IDS = setOf(
            291504388, // fuel capacity
            291504644, // odometer
            291504903, // fuel level
            291504904, // remaining range
            4194560,   // average fuel
            612372992, // trip average speed
            612373760, // trip distance
            612374016, // trip duration
        )

        private object ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
            override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)
            override fun parse(stream: InputStream): ByteArray = stream.readBytes()
        }

        private fun descriptor(
            type: MethodDescriptor.MethodType,
            name: String,
        ): MethodDescriptor<ByteArray, ByteArray> =
            MethodDescriptor.newBuilder<ByteArray, ByteArray>()
                .setType(type)
                .setFullMethodName(name)
                .setRequestMarshaller(ByteArrayMarshaller)
                .setResponseMarshaller(ByteArrayMarshaller)
                .build()
    }

    private data class Key(val propertyId: Int, val areaId: Int)

    private data class Value(
        val propertyId: Int,
        val areaId: Int,
        val status: Int,
        val timestampNs: Long,
        val int32Values: List<Int>,
        val int64Values: List<Long>,
        val floatValues: List<Float>,
    ) {
        fun numeric(integerScale: Float): Float? {
            if (status != 0) return null
            floatValues.firstOrNull()?.let { return it }
            int32Values.firstOrNull()?.let { return it * integerScale }
            int64Values.firstOrNull()?.let { return it.toFloat() * integerScale }
            return null
        }
    }

    private val values = ConcurrentHashMap<Key, Value>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "autoenergy-vhal").apply { isDaemon = true }
    }
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val reconnectScheduled = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)
    private val firstUnparsedLogged = AtomicBoolean(false)
    private val loggedProperties = ConcurrentHashMap.newKeySet<Key>()
    private val state = AtomicReference("idle")
    private val lastError = AtomicReference<String?>(null)
    @Volatile
    private var channel: ManagedChannel? = null

    fun readNumeric(
        propertyId: Int,
        areaId: Int,
        diagnostics: MutableList<String>,
        label: String,
        integerScale: Float = 1f,
    ): Float? {
        ensureStarted()
        val value = values[Key(propertyId, areaId)] ?: return null
        val numeric = value.numeric(integerScale) ?: return null
        diagnostics +=
            "$label=$numeric via vhal.grpc($propertyId,$areaId) " +
                "type=${valueType(value)} status=${value.status} timestampNs=${value.timestampNs}"
        return numeric
    }

    fun appendDiagnostics(diagnostics: MutableList<String>) {
        ensureStarted()
        diagnostics += "vhal.grpc state=${state.get()} endpoint=$HOST:$PORT cached=${values.size}"
        lastError.get()?.let { diagnostics += "vhal.grpc error=$it" }
    }

    private fun ensureStarted() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        executor.execute(::connect)
    }

    private fun connect() {
        if (closed.get()) return
        reconnectScheduled.set(false)
        state.set("connecting")
        lastError.set(null)
        try {
            val headers = Metadata().apply {
                put(
                    Metadata.Key.of("session_id", Metadata.ASCII_STRING_MARSHALLER),
                    UUID.randomUUID().toString(),
                )
                put(
                    Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER),
                    CLIENT_ID,
                )
            }
            val newChannel = OkHttpChannelBuilder
                .forAddress(HOST, PORT)
                .usePlaintext()
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .intercept(MetadataUtils.newAttachHeadersInterceptor(headers))
                .build()
            channel = newChannel
            AppLog.i(
                "VHAL",
                "opening read-only stream endpoint=$HOST:$PORT clientId=$CLIENT_ID",
            )

            val streamCall = newChannel.newCall(
                descriptor(MethodDescriptor.MethodType.SERVER_STREAMING, STREAM_METHOD),
                CallOptions.DEFAULT,
            )
            ClientCalls.asyncServerStreamingCall(
                streamCall,
                ByteArray(0),
                object : StreamObserver<ByteArray> {
                    override fun onNext(chunk: ByteArray) {
                        reconnectAttempt.set(0)
                        val parsed = VhalProtoParser.parseChunk(chunk)
                        if (parsed.isEmpty() && firstUnparsedLogged.compareAndSet(false, true)) {
                            AppLog.w(
                                "VHAL",
                                "first unparsed stream chunk bytes=${chunk.size} head=${hexHead(chunk)}",
                            )
                        }
                        parsed.forEach { value ->
                            val key = Key(value.propertyId, value.areaId)
                            values[key] = value
                            if (value.propertyId in DIAGNOSTIC_PROPERTY_IDS &&
                                loggedProperties.add(key)
                            ) {
                                AppLog.i(
                                    "VHAL",
                                    "property available id=${value.propertyId} area=${value.areaId} " +
                                        "status=${value.status} ints=${value.int32Values.take(4)} " +
                                        "floats=${value.floatValues.take(4)} longs=${value.int64Values.take(2)}",
                                )
                            }
                        }
                        if (parsed.isNotEmpty()) {
                            state.set("streaming")
                        }
                    }

                    override fun onError(error: Throwable) {
                        failAndReconnect(error)
                    }

                    override fun onCompleted() {
                        failAndReconnect(IllegalStateException("property stream completed"))
                    }
                },
            )

            runCatching {
                ClientCalls.blockingUnaryCall(
                    newChannel.newCall(
                        descriptor(MethodDescriptor.MethodType.UNARY, SEND_ALL_METHOD),
                        CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS),
                    ),
                    ByteArray(0),
                )
            }.onSuccess {
                AppLog.i("VHAL", "initial property snapshot requested")
            }.onFailure {
                lastError.set("SendAll: ${throwableSummary(it)}")
            }
        } catch (t: Throwable) {
            failAndReconnect(t)
        }
    }

    private fun failAndReconnect(error: Throwable) {
        if (closed.get()) return
        if (!reconnectScheduled.compareAndSet(false, true)) return
        val summary = throwableSummary(error)
        val attempt = reconnectAttempt.incrementAndGet()
        val delaySeconds = when (attempt) {
            1 -> 5L
            2 -> 15L
            3 -> 30L
            else -> 60L
        }
        lastError.set(summary)
        state.set("retrying(${delaySeconds}s)")
        AppLog.w(
            "VHAL",
            "stream unavailable attempt=$attempt; retry in ${delaySeconds}s: $summary",
        )
        val failedChannel = channel
        channel = null
        failedChannel?.shutdownNow()
        executor.schedule(::connect, delaySeconds, TimeUnit.SECONDS)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        state.set("closed")
        channel?.shutdownNow()
        channel = null
        executor.shutdownNow()
    }

    private fun valueType(value: Value): String = when {
        value.floatValues.isNotEmpty() -> "Float"
        value.int32Values.isNotEmpty() -> "Int32"
        value.int64Values.isNotEmpty() -> "Int64"
        else -> "empty"
    }

    private fun hexHead(bytes: ByteArray): String =
        bytes.take(16).joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private fun throwableSummary(error: Throwable): String =
        generateSequence(error as Throwable?) { it.cause }
            .take(4)
            .joinToString(separator = " <- ") {
                "${it.javaClass.simpleName}: ${it.message ?: "(no message)"}"
            }

    private object VhalProtoParser {
        fun parseChunk(chunk: ByteArray): List<Value> {
            if (chunk.isEmpty()) return emptyList()
            val reader = ProtoReader(chunk)
            val nestedValues = mutableListOf<Value>()
            var firstVarintField1: Long? = null

            while (!reader.exhausted()) {
                val tag = reader.varint() ?: break
                val field = (tag ushr 3).toInt()
                val wire = (tag and 7).toInt()
                when {
                    field == 1 && wire == 0 -> {
                        firstVarintField1 = reader.varint() ?: break
                    }
                    field == 5 && wire == 2 -> {
                        val nested = reader.bytes() ?: break
                        parseVehicleValue(nested)?.let(nestedValues::add)
                    }
                    else -> if (!reader.skip(wire)) break
                }
            }

            if (nestedValues.isNotEmpty()) return nestedValues
            // Some firmware variants stream VehiclePropValue directly instead of
            // wrapping it in EmulatorMessage. A real property id is much larger
            // than the small EmulatorMessage command enum.
            if (firstVarintField1 != null && firstVarintField1 > 1_000_000L) {
                parseVehicleValue(chunk)?.let { return listOf(it) }
            }
            return emptyList()
        }

        private fun parseVehicleValue(bytes: ByteArray): Value? {
            val reader = ProtoReader(bytes)
            var propertyId: Int? = null
            var areaId = 0
            var status = 0
            var timestamp = 0L
            val ints = mutableListOf<Int>()
            val longs = mutableListOf<Long>()
            val floats = mutableListOf<Float>()

            while (!reader.exhausted()) {
                val tag = reader.varint() ?: return null
                val field = (tag ushr 3).toInt()
                val wire = (tag and 7).toInt()
                when (field) {
                    1 -> if (wire == 0) propertyId = reader.varint()?.toInt() ?: return null else if (!reader.skip(wire)) return null
                    3 -> if (wire == 0) timestamp = reader.varint() ?: return null else if (!reader.skip(wire)) return null
                    4 -> if (wire == 0) areaId = reader.varint()?.toInt() ?: return null else if (!reader.skip(wire)) return null
                    5 -> when (wire) {
                        0 -> ints += zigZag32(reader.varint() ?: return null)
                        2 -> {
                            val packed = ProtoReader(reader.bytes() ?: return null)
                            while (!packed.exhausted()) ints += zigZag32(packed.varint() ?: return null)
                        }
                        else -> if (!reader.skip(wire)) return null
                    }
                    6 -> when (wire) {
                        0 -> longs += zigZag64(reader.varint() ?: return null)
                        2 -> {
                            val packed = ProtoReader(reader.bytes() ?: return null)
                            while (!packed.exhausted()) longs += zigZag64(packed.varint() ?: return null)
                        }
                        else -> if (!reader.skip(wire)) return null
                    }
                    7 -> when (wire) {
                        5 -> floats += Float.fromBits(reader.fixed32() ?: return null)
                        2 -> {
                            val packed = reader.bytes() ?: return null
                            var offset = 0
                            while (offset + 4 <= packed.size) {
                                floats += ByteBuffer.wrap(packed, offset, 4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .float
                                offset += 4
                            }
                        }
                        else -> if (!reader.skip(wire)) return null
                    }
                    10 -> if (wire == 0) status = reader.varint()?.toInt() ?: return null else if (!reader.skip(wire)) return null
                    else -> if (!reader.skip(wire)) return null
                }
            }
            val prop = propertyId ?: return null
            if (prop <= 1_000_000) return null
            return Value(prop, areaId, status, timestamp, ints, longs, floats)
        }

        private fun zigZag32(value: Long): Int =
            ((value ushr 1) xor -(value and 1)).toInt()

        private fun zigZag64(value: Long): Long =
            (value ushr 1) xor -(value and 1)
    }

    private class ProtoReader(private val data: ByteArray) {
        private var offset = 0

        fun exhausted(): Boolean = offset >= data.size

        fun varint(): Long? {
            var result = 0L
            var shift = 0
            while (offset < data.size && shift < 64) {
                val next = data[offset++].toInt() and 0xff
                result = result or ((next and 0x7f).toLong() shl shift)
                if (next and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        fun bytes(): ByteArray? {
            val length = varint()?.toInt() ?: return null
            if (length < 0 || offset + length > data.size) return null
            return data.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun fixed32(): Int? {
            if (offset + 4 > data.size) return null
            return ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .also { offset += 4 }
        }

        fun skip(wire: Int): Boolean {
            return when (wire) {
                0 -> varint() != null
                1 -> advance(8)
                2 -> {
                    val length = varint()?.toInt() ?: return false
                    advance(length)
                }
                5 -> advance(4)
                else -> false
            }
        }

        private fun advance(length: Int): Boolean {
            if (length < 0 || offset + length > data.size) return false
            offset += length
            return true
        }
    }
}
