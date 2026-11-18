package com.evorez.ezdmanager.root

import com.evorez.ezdmanager.model.RootStatus
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val timedOut: Boolean = false,
    val error: String? = null
) {
    val stdoutText: String get() = stdout.joinToString("\n")
    val combinedText: String
        get() = buildString {
            if (stdout.isNotEmpty()) appendLine(stdout.joinToString("\n"))
            if (stderr.isNotEmpty()) appendLine(stderr.joinToString("\n"))
            if (error != null) appendLine(error)
        }.trim()
}

/**
 * Centralized, thread-safe root shell wrapper backed by a single-threaded worker dispatcher
 * so all shell writes, reads, and probes execute sequentially without racing UI or sysfs state.
 */
object RootShellManager {
    private val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ezd-root-worker").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = workerExecutor.asCoroutineDispatcher()
    private val commandCounter = AtomicInteger(0)

    @Volatile
    private var rootShell: Shell? = null

    val executedCount: Int get() = commandCounter.get()

    fun hasSuBinary(): Boolean {
        val knownPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su"
        )
        if (knownPaths.any { runCatching { File(it).exists() }.getOrDefault(false) }) {
            return true
        }
        val pathEnv = System.getenv("PATH") ?: return false
        return pathEnv.split(":").any { dir ->
            dir.isNotBlank() && runCatching { File(dir, "su").exists() }.getOrDefault(false)
        }
    }

    suspend fun probeRoot(forceFreshRequest: Boolean = false): RootStatus = withContext(dispatcher) {
        val suExists = hasSuBinary()
        try {
            if (forceFreshRequest) {
                runCatching { Shell.getCachedShell()?.close() }
                rootShell = null
            }
            val shell = withTimeout(8_000L) {
                val existing = rootShell
                if (!forceFreshRequest && existing != null && existing.isAlive) {
                    existing
                } else {
                    Shell.getShell().also { rootShell = it }
                }
            }
            when {
                shell.isRoot -> RootStatus.GRANTED
                suExists -> RootStatus.DENIED
                else -> RootStatus.UNAVAILABLE
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            if (suExists) RootStatus.DENIED else RootStatus.UNAVAILABLE
        }
    }

    /**
     * Executes command through the primary Root (su) shell.
     * If root is unavailable or denied and allowShizukuSecondary is true,
     * it seamlessly routes through the secondary Shizuku execution path.
     */
    suspend fun exec(
        command: String,
        timeoutMs: Long = 6_000L,
        allowShizukuSecondary: Boolean = true
    ): CommandResult {
        if (command.isBlank() || command.contains('\u0000')) {
            return CommandResult(false, -1, emptyList(), emptyList(), error = "Invalid command input")
        }
        return withContext(dispatcher) {
            try {
                withTimeout(timeoutMs) {
                    val shell = rootShell?.takeIf { it.isAlive } ?: runCatching { Shell.getShell() }.getOrNull()?.also { rootShell = it }
                    if (shell != null && shell.isRoot) {
                        commandCounter.incrementAndGet()
                        val result = shell.newJob().add(command).exec()
                        return@withTimeout CommandResult(
                            success = result.isSuccess,
                            exitCode = result.code,
                            stdout = result.out,
                            stderr = result.err
                        )
                    }

                    // Secondary Execution Path: Shizuku
                    if (allowShizukuSecondary && ShizukuManager.isPermissionGranted()) {
                        commandCounter.incrementAndGet()
                        return@withTimeout ShizukuManager.exec(command, timeoutMs)
                    }

                    CommandResult(
                        success = false,
                        exitCode = 126,
                        stdout = emptyList(),
                        stderr = emptyList(),
                        error = if (allowShizukuSecondary) "Neither Root nor Shizuku access is granted" else "Root access is not granted"
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                CommandResult(
                    success = false,
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = emptyList(),
                    timedOut = true,
                    error = "Command timed out after ${timeoutMs}ms"
                )
            } catch (e: Exception) {
                if (allowShizukuSecondary && ShizukuManager.isPermissionGranted()) {
                    return@withContext ShizukuManager.exec(command, timeoutMs)
                }
                CommandResult(
                    success = false,
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = emptyList(),
                    error = e.message ?: "Shell execution failed"
                )
            }
        }
    }

    suspend fun execUnprivileged(command: String, timeoutMs: Long = 4_000L): CommandResult {
        if (command.isBlank() || command.contains('\u0000')) {
            return CommandResult(false, -1, emptyList(), emptyList(), error = "Invalid command input")
        }
        return withContext(dispatcher) {
            try {
                withTimeout(timeoutMs) {
                    commandCounter.incrementAndGet()
                    val process = ProcessBuilder("sh", "-c", command).start()
                    val out = process.inputStream.bufferedReader().readLines()
                    val err = process.errorStream.bufferedReader().readLines()
                    val code = process.waitFor()
                    CommandResult(code == 0, code, out, err)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                CommandResult(false, -1, emptyList(), emptyList(), timedOut = true, error = "Timed out")
            } catch (e: Exception) {
                CommandResult(false, -1, emptyList(), emptyList(), error = e.message ?: "sh failed")
            }
        }
    }

    suspend fun readSysfs(path: String): String? {
        if (!isValidPath(path)) return null
        val qPath = shellQuote(path)
        val res = exec("if [ -r $qPath ]; then cat $qPath 2>/dev/null; fi", timeoutMs = 2_500L)
        return if (res.success) res.stdoutText.trim().takeIf { it.isNotEmpty() } else null
    }

    suspend fun writeAndVerify(path: String, value: String): Pair<Boolean, String?> {
        if (!isValidPath(path) || value.contains('\u0000') || value.contains('\n')) {
            return false to "Invalid path or value"
        }
        val qPath = shellQuote(path)
        val qVal = shellQuote(value)
        val check = exec("[ -e $qPath ] && [ -w $qPath ] && echo OK || echo NO", timeoutMs = 2_500L)
        if (check.stdout.firstOrNull()?.trim() != "OK") {
            return false to "Node missing or not writable: $path"
        }
        val writeRes = exec("printf '%s' $qVal > $qPath", timeoutMs = 3_000L)
        val readBack = readSysfs(path)
        return when {
            !writeRes.success -> false to (writeRes.stderr.firstOrNull() ?: "Write rejected; read-back: ${readBack ?: "unavailable"}")
            readBack == null -> false to "Read-back unavailable"
            readBack != value.trim() -> false to "Kernel clamped/overrode value ($readBack)"
            else -> true to readBack
        }
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
