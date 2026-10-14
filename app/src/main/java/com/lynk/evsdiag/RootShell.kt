package com.lynk.dvrprobe

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
)

data class RootStatus(
    val granted: Boolean,
    val summary: String,
    val detail: String,
)

object RootShell {
    private const val MAX_CAPTURE_CHARS = 32 * 1024
    private const val TERMINATION_WAIT_MS = 250L
    private const val OUTPUT_DRAIN_WAIT_MS = 500L

    /** Call only after the user has explicitly chosen to attempt a root operation. */
    fun probe(timeoutMs: Long = 6_000): RootStatus {
        val result = run("id -u", timeoutMs)
        return when {
            result.timedOut -> RootStatus(
                granted = false,
                summary = "Root check timed out",
                detail = "su did not finish within ${timeoutMs} ms.",
            )

            result.exitCode != 0 -> RootStatus(
                granted = false,
                summary = "SU command failed",
                detail = buildString {
                    append("exit=")
                    append(result.exitCode)
                    if (result.stderr.isNotBlank()) {
                        append(" stderr=")
                        append(result.stderr)
                    }
                },
            )

            result.stdout.trim() != "0" -> RootStatus(
                granted = false,
                summary = "Root is unavailable",
                detail = result.stdout.ifBlank { "id -u did not return 0." },
            )

            else -> RootStatus(
                granted = true,
                summary = "Root is ready",
                detail = result.stdout.trim(),
            )
        }
    }

    fun run(command: String, timeoutMs: Long = 10_000): ShellResult {
        require(timeoutMs >= 0) { "timeoutMs must be non-negative" }
        val process = try {
            ProcessBuilder("su", "-c", command)
                .directory(File("/"))
                .redirectErrorStream(false)
                .start()
        } catch (e: IOException) {
            return ShellResult(
                exitCode = -2,
                stdout = "",
                stderr = e.message ?: "failed to start su",
            )
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "root-shell-output").apply { isDaemon = true }
        }
        val stdoutFuture = readers.submit<String> { readLimited(process.inputStream) }
        val stderrFuture = readers.submit<String> { readLimited(process.errorStream) }
        closeQuietly(process.outputStream)

        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                waitAfterDestroy(process)
                val stdout = collect(stdoutFuture)
                val stderr = collect(stderrFuture)
                return ShellResult(
                    exitCode = -1,
                    stdout = stdout,
                    stderr = listOf(stderr, "timed out after ${timeoutMs} ms")
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                    timedOut = true,
                )
            }

            return ShellResult(
                exitCode = process.exitValue(),
                stdout = collect(stdoutFuture),
                stderr = collect(stderrFuture),
            )
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            waitAfterDestroy(process)
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                waitAfterDestroy(process)
            }
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            closeQuietly(process.outputStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
        }
    }

    private fun readLimited(input: InputStream): String {
        val output = StringBuilder()
        var truncated = false
        InputStreamReader(input, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(2048)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val remaining = MAX_CAPTURE_CHARS - output.length
                if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                if (count > remaining) truncated = true
            }
        }
        if (truncated) output.append("\n[output truncated]")
        return output.toString().trim()
    }

    private fun collect(future: Future<String>): String = try {
        future.get(OUTPUT_DRAIN_WAIT_MS, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        future.cancel(true)
        "[output drain timed out]"
    } catch (error: ExecutionException) {
        "[output read failed: ${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}]"
    }

    private fun waitAfterDestroy(process: Process) {
        try {
            process.waitFor(TERMINATION_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun closeQuietly(input: AutoCloseable) {
        runCatching { input.close() }
    }
}
