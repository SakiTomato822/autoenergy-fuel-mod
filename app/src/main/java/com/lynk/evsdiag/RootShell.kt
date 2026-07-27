package com.lynk.dvrprobe

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    fun probe(timeoutMs: Long = 6_000): RootStatus {
        val result = run("id", timeoutMs)
        return when {
            result.timedOut -> RootStatus(
                granted = false,
                summary = "Root request timed out",
                detail = "Please allow the SU prompt on the head unit, then try again.",
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

            "uid=0" !in result.stdout -> RootStatus(
                granted = false,
                summary = "SU returned but root is not active",
                detail = result.stdout.ifBlank { "Unexpected id output." },
            )

            else -> RootStatus(
                granted = true,
                summary = "Root is ready",
                detail = result.stdout,
            )
        }
    }

    fun run(command: String, timeoutMs: Long = 10_000): ShellResult {
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

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ShellResult(-1, "", "timeout", timedOut = true)
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText().trim() }
        val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
        return ShellResult(process.exitValue(), stdout, stderr)
    }
}
