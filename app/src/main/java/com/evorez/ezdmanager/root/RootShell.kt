package com.evorez.ezdmanager.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared hardware read/write/probe result across all EZD tuning features.
 */
sealed interface HwResult {
    data class Applied(val readBackValue: String) : HwResult
    data class Rejected(val reason: String) : HwResult
    data class NotFound(val pathsTried: List<String>) : HwResult
    data object PermissionDenied : HwResult
    data object SelinuxBlocked : HwResult
}

fun HwResult.describe(prefix: String = ""): String {
    val head = if (prefix.isNotBlank()) "$prefix: " else ""
    return when (this) {
        is HwResult.Applied -> "${head}Applied ($readBackValue)"
        is HwResult.Rejected -> "${head}Rejected ($reason)"
        is HwResult.NotFound -> "${head}NotFound (tried ${pathsTried.joinToString(", ")})"
        HwResult.PermissionDenied -> "${head}PermissionDenied (root su required)"
        HwResult.SelinuxBlocked -> "${head}SelinuxBlocked (SELinux policy blocked node access)"
    }
}

fun HwResult.shortTag(): String = when (this) {
    is HwResult.Applied -> "Applied"
    is HwResult.Rejected -> "Rejected"
    is HwResult.NotFound -> "NotFound"
    HwResult.PermissionDenied -> "PermissionDenied"
    HwResult.SelinuxBlocked -> "SelinuxBlocked"
}

data class RootExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
    val stdoutLines: List<String>
        get() = if (stdout.isBlank()) emptyList() else stdout.lines().dropLastWhile { it.isEmpty() }
    val stderrLines: List<String>
        get() = if (stderr.isBlank()) emptyList() else stderr.lines().dropLastWhile { it.isEmpty() }
}

/**
 * Unified RootShell helper.
 * - Executes commands through `su` with timeout and serialized single-worker dispatch.
 * - Returns exitCode + stdout + stderr.
 * - All /sys and /proc reads and writes go through this class (never direct java.io.File).
 */
object RootShell {
    private val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ezd-rootshell-worker").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = workerExecutor.asCoroutineDispatcher()
    private val commandCounter = AtomicInteger(0)

    @Volatile
    private var cachedShell: Shell? = null

    val executedCount: Int get() = commandCounter.get()

    suspend fun isSuGranted(forceFresh: Boolean = false): Boolean = withContext(dispatcher) {
        try {
            if (forceFresh) {
                runCatching { Shell.getCachedShell()?.close() }
                cachedShell = null
            }
            val shell = withTimeout(6_000L) {
                val existing = cachedShell
                if (!forceFresh && existing != null && existing.isAlive) existing
                else Shell.getShell().also { cachedShell = it }
            }
            shell.isRoot
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    suspend fun exec(command: String, timeoutMs: Long = 5_000L): RootExecResult {
        if (command.isBlank() || command.contains('\u0000')) {
            return RootExecResult(exitCode = -1, stdout = "", stderr = "Invalid command input")
        }
        return withContext(dispatcher) {
            try {
                withTimeout(timeoutMs) {
                    val shell = cachedShell?.takeIf { it.isAlive }
                        ?: runCatching { Shell.getShell() }.getOrNull()?.also { cachedShell = it }
                    if (shell == null || !shell.isRoot) {
                        return@withTimeout RootExecResult(
                            exitCode = 126,
                            stdout = "",
                            stderr = "Root (su) permission denied or unavailable"
                        )
                    }
                    commandCounter.incrementAndGet()
                    val jobResult = shell.newJob().add(command).exec()
                    RootExecResult(
                        exitCode = jobResult.code,
                        stdout = jobResult.out.joinToString("\n"),
                        stderr = jobResult.err.joinToString("\n"),
                        timedOut = false
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: TimeoutCancellationException) {
                RootExecResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Root command timed out after ${timeoutMs}ms",
                    timedOut = true
                )
            } catch (e: Exception) {
                RootExecResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = e.message ?: "RootShell execution error"
                )
            }
        }
    }

    /**
     * Fallback shell command through `/system/bin/sh` when `su` is not granted,
     * used strictly for unprivileged read-only probes (still avoids plain java.io.File on /sys or /proc).
     */
    suspend fun execLocalSh(command: String, timeoutMs: Long = 3_000L): RootExecResult {
        if (command.isBlank() || command.contains('\u0000')) {
            return RootExecResult(exitCode = -1, stdout = "", stderr = "Invalid command input")
        }
        return withContext(dispatcher) {
            var process: Process? = null
            try {
                withTimeout(timeoutMs) {
                    process = ProcessBuilder("sh", "-c", command).start()
                    val out = process!!.inputStream.bufferedReader().use { it.readText() }
                    val err = process!!.errorStream.bufferedReader().use { it.readText() }
                    val code = process!!.waitFor()
                    RootExecResult(code, out.trimEnd('\n'), err.trimEnd('\n'), timedOut = false)
                }
            } catch (ce: CancellationException) {
                runCatching { process?.destroyForcibly() }
                throw ce
            } catch (_: TimeoutCancellationException) {
                runCatching { process?.destroyForcibly() }
                RootExecResult(-1, "", "Local shell timed out after ${timeoutMs}ms", timedOut = true)
            } catch (e: Exception) {
                runCatching { process?.destroyForcibly() }
                RootExecResult(-1, "", e.message ?: "Local shell failed")
            }
        }
    }

    suspend fun getKernelVersion(): String {
        val su = exec("uname -r", 2_000L)
        if (su.isSuccess && su.stdout.isNotBlank()) return su.stdout.trim()
        val sh = execLocalSh("uname -r", 2_000L)
        return sh.stdout.trim().ifBlank { System.getProperty("os.version") ?: "Unknown" }
    }

    suspend fun getSelinuxMode(): String {
        val su = exec("getenforce 2>/dev/null || cat /sys/fs/selinux/enforce 2>/dev/null", 2_000L)
        val raw = if (su.stdout.isNotBlank()) su.stdout.trim()
        else execLocalSh("getenforce 2>/dev/null || cat /sys/fs/selinux/enforce 2>/dev/null", 2_000L).stdout.trim()
        return when (raw) {
            "1", "Enforcing" -> "Enforcing"
            "0", "Permissive" -> "Permissive"
            "" -> "Unknown"
            else -> raw
        }
    }

    suspend fun detectRootType(): String {
        val suCheck = exec(
            """
            if command -v magisk >/dev/null 2>&1 || [ -d /data/adb/magisk ]; then
              echo "Magisk (${'$'}(magisk -v 2>/dev/null | head -n1))"
            elif command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then
              echo "KernelSU (${'$'}(ksud -V 2>/dev/null | head -n1))"
            elif [ -d /data/adb/ap ]; then
              echo "APatch"
            else
              echo "Root (su)"
            fi
            """.trimIndent(),
            timeoutMs = 2_500L
        )
        return if (suCheck.isSuccess && suCheck.stdout.isNotBlank()) {
            suCheck.stdout.trim().replace(" ()", "")
        } else {
            "None"
        }
    }

    suspend fun readNode(path: String): String? {
        if (!isValidPath(path)) return null
        val qPath = shellQuote(path)
        val res = exec("if [ -r $qPath ]; then cat $qPath 2>/dev/null; fi", timeoutMs = 2_500L)
        if (res.isSuccess) return res.stdout.trim().takeIf { it.isNotEmpty() }
        if (res.exitCode == 126) {
            val local = execLocalSh("if [ -r $qPath ]; then cat $qPath 2>/dev/null; fi", timeoutMs = 2_000L)
            if (local.isSuccess) return local.stdout.trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    suspend fun readNodeDetailed(path: String): Pair<HwResult, String?> {
        if (!isValidPath(path)) return HwResult.Rejected("Invalid sysfs/proc path: $path") to null
        val qPath = shellQuote(path)
        val probe = exec(
            """
            if [ ! -e $qPath ]; then
              echo "NF"
            elif [ ! -r $qPath ]; then
              echo "NR"
            else
              OUT=${'$'}(cat $qPath 2>&1)
              EC=${'$'}?
              if [ ${'$'}EC -ne 0 ]; then
                echo "ERR|${'$'}OUT"
              else
                echo "OK|${'$'}OUT"
              fi
            fi
            """.trimIndent(),
            timeoutMs = 2_500L
        )
        if (probe.exitCode == 126) {
            return HwResult.PermissionDenied to null
        }
        val firstLine = probe.stdout.trim()
        return when {
            firstLine == "NF" -> HwResult.NotFound(listOf(path)) to null
            firstLine == "NR" -> {
                val selinux = getSelinuxMode()
                val res = if (selinux.equals("Enforcing", true)) HwResult.SelinuxBlocked else HwResult.PermissionDenied
                res to null
            }
            firstLine.startsWith("ERR|") -> {
                val msg = firstLine.removePrefix("ERR|").trim()
                val res = if (msg.contains("Permission denied", true) || msg.contains("avc", true)) {
                    if (getSelinuxMode().equals("Enforcing", true)) HwResult.SelinuxBlocked else HwResult.PermissionDenied
                } else {
                    HwResult.Rejected(msg.ifBlank { "Read error" })
                }
                res to null
            }
            firstLine.startsWith("OK|") -> {
                val valText = probe.stdout.removePrefix("OK|").trim()
                HwResult.Applied(valText) to valText
            }
            else -> HwResult.Rejected(probe.stderr.ifBlank { "Failed reading $path" }) to null
        }
    }

    /**
     * Writes [value] to sysfs/proc [path] strictly through `su`, reads back, and compares.
     */
    suspend fun writeAndVerify(path: String, value: String): HwResult {
        if (!isValidPath(path) || value.contains('\u0000') || value.contains('\n')) {
            return HwResult.Rejected("Invalid path or value")
        }
        val qPath = shellQuote(path)
        val qVal = shellQuote(value.trim())
        val res = exec(
            """
            if [ ! -e $qPath ]; then
              echo "STATUS|NF"
              exit 0
            fi
            WERR=${'$'}((printf '%s' $qVal > $qPath) 2>&1)
            WEC=${'$'}?
            RB=${'$'}(cat $qPath 2>/dev/null)
            if [ ${'$'}WEC -ne 0 ]; then
              SE=${'$'}(getenforce 2>/dev/null)
              echo "STATUS|WERR|${'$'}WEC|${'$'}SE|${'$'}WERR"
            else
              echo "STATUS|OK|${'$'}RB"
            fi
            """.trimIndent(),
            timeoutMs = 3_500L
        )
        if (res.exitCode == 126) return HwResult.PermissionDenied
        if (res.timedOut) return HwResult.Rejected("Write timed out for $path")

        val out = res.stdout.trim()
        if (out == "STATUS|NF") {
            return HwResult.NotFound(listOf(path))
        }
        if (out.startsWith("STATUS|WERR|")) {
            val parts = out.removePrefix("STATUS|WERR|").split('|', limit = 3)
            val se = parts.getOrNull(1).orEmpty().trim()
            val errMsg = parts.getOrNull(2).orEmpty().trim()
            return when {
                (errMsg.contains("Permission denied", true) || errMsg.contains("avc", true)) &&
                    (se.equals("Enforcing", true) || se == "1") -> HwResult.SelinuxBlocked
                errMsg.contains("Permission denied", true) -> HwResult.PermissionDenied
                else -> HwResult.Rejected(errMsg.ifBlank { "Kernel rejected write to $path" })
            }
        }
        if (out.startsWith("STATUS|OK|")) {
            val readBack = out.removePrefix("STATUS|OK|").trim()
            val expected = value.trim()
            val matches = readBack == expected ||
                readBack.split(Regex("\\s+")).any { it == "[$expected]" } ||
                (expected.toLongOrNull() != null && readBack.toLongOrNull() == expected.toLongOrNull())
            return if (matches) {
                HwResult.Applied(readBack)
            } else {
                HwResult.Rejected("Kernel clamped/overrode value ($readBack != $expected)")
            }
        }
        return HwResult.Rejected(res.stderr.ifBlank { "Write verification failed for $path" })
    }

    fun isValidPath(path: String): Boolean {
        return path.startsWith("/") && !path.contains("..") && !path.contains('\u0000')
    }

    fun isValidPropName(prop: String): Boolean {
        return prop.matches(Regex("^[a-zA-Z0-9._-]+$"))
    }

    fun shellQuote(raw: String): String {
        return "'" + raw.replace("'", "'\\''") + "'"
    }
}
