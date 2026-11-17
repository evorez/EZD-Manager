package com.evorez.ezdmanager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.topjohnwu.superuser.Shell
import java.util.concurrent.Executors

internal data class Policy(
    val id: String, val cpus: String, val current: Long?, val min: Long?, val max: Long?,
    val frequencies: List<Long>, val governors: String, val governor: String,
    val writable: Boolean, val original: Long?
)
internal data class Thermal(val id: String, val type: String, val temp: Long?)
internal data class Snapshot(val policies: List<Policy> = emptyList(), val thermal: List<Thermal> = emptyList())

/** All shell and preference operations run on one worker so refreshes cannot race writes. */
internal class DeviceController(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("cpu_baselines", Context.MODE_PRIVATE)
    private var active = false
    private var polling = false
    private var rootShell: Shell? = null
    private val failedBaselines = mutableSetOf<String>()
    var root by mutableStateOf("Not requested")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var snapshot by mutableStateOf(Snapshot())
        private set
    var battery by mutableStateOf("Unavailable")
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            readBattery()
            if (root == "Granted" && !polling && !busy) refresh()
            main.postDelayed(this, 3000)
        }
    }

    fun start() { if (!active) { active = true; main.post(tick) } }
    fun stop() { active = false; main.removeCallbacks(tick) }
    fun destroy() { stop(); worker.shutdown() }

    fun grantRoot() {
        if (busy) return
        busy = true
        root = "Requesting…"
        worker.execute {
            val granted = try {
                // Close a previously denied/non-root cached shell, allowing a fresh request.
                Shell.getCachedShell()?.close()
                Shell.getShell().also { rootShell = it }.isRoot
            } catch (_: Exception) { false }
            main.post {
                busy = false
                root = if (granted) "Granted" else "Not granted"
                message = if (granted) "" else "Root unavailable or denied. Check your root manager."
                if (granted) refresh() else snapshot = Snapshot()
            }
        }
    }

    private fun readBattery() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val parts = mutableListOf<String>()
        if (level >= 0 && scale > 0) parts.add("${level * 100 / scale}%")
        if (temp != Int.MIN_VALUE) parts.add("${temp / 10.0} °C")
        battery = parts.joinToString(" · ").ifEmpty { "Unavailable" }
    }

    private fun refresh() {
        polling = true
        worker.execute {
            val data = try { scan() } catch (_: Exception) { null }
            main.post {
                polling = false
                if (data == null) {
                    root = "Unavailable"
                    snapshot = Snapshot()
                    message = "Root read failed. Grant root again."
                } else snapshot = data
            }
        }
    }

    private fun scan(): Snapshot? {
        val shell = rootShell ?: return null
        if (!shell.isAlive || !shell.isRoot) return null
        val result = shell.newJob().add(SCAN).exec()
        if (!result.isSuccess) return null
        val policies = mutableListOf<Policy>()
        val zones = mutableListOf<Thermal>()
        result.out.forEach { line ->
            val p = line.split('|').map { it.trim() }
            if (p.size == 10 && p[0] == "P" && p[1].matches(Regex("policy[0-9]+"))) {
                val max = p[5].toLongOrNull()?.takeIf { it > 0 }
                val key = p[1]
                // Save the first readable baseline before allowing any write, never overwrite it.
                if (!prefs.contains(key) && max != null && !prefs.edit().putLong(key, max).commit()) failedBaselines.add(key)
                val original = if (key !in failedBaselines && prefs.contains(key)) prefs.getLong(key, 0).takeIf { it > 0 } else null
                policies.add(Policy(key, p[2], p[3].toLongOrNull(), p[4].toLongOrNull(), max,
                    p[6].split(Regex("\\s+")).mapNotNull { it.toLongOrNull()?.takeIf { n -> n > 0 } }.distinct().sorted(),
                    p[7], p[8], p[9] == "1", original))
            } else if (p.size == 4 && p[0] == "T" && p[1].matches(Regex("thermal_zone[0-9]+"))) {
                zones.add(Thermal(p[1], p[2], p[3].toLongOrNull()))
            }
        }
        return Snapshot(policies.sortedBy { it.id.removePrefix("policy").toIntOrNull() }, zones.sortedBy { it.id.removePrefix("thermal_zone").toIntOrNull() })
    }

    fun setMax(policy: Policy, target: Long, reset: Boolean = false) {
        // Only use a currently detected policy and a known available value, or its saved baseline.
        val current = snapshot.policies.find { it.id == policy.id } ?: return
        if (busy || root != "Granted" || !current.writable || current.original == null || target <= 0 ||
            (if (reset) target != current.original else target !in current.frequencies)) return
        busy = true
        message = "Applying…"
        worker.execute {
            val outcome = try {
                val shell = rootShell
                if (shell == null || !shell.isAlive || !shell.isRoot) "Root unavailable; nothing applied."
                else {
                    // The id and target are validated above; only scaling_max_freq is ever written.
                    val path = "/sys/devices/system/cpu/cpufreq/${current.id}/scaling_max_freq"
                    val write = shell.newJob().add("printf '%s' '$target' > '$path'").exec()
                    val read = shell.newJob().add("cat '$path'").exec()
                    val actual = if (read.isSuccess) read.out.firstOrNull()?.trim()?.toLongOrNull() else null
                    when {
                        !write.isSuccess -> "Write failed for ${current.id}; read-back: ${actual ?: "unavailable"} kHz."
                        actual == null -> "Read-back failed for ${current.id}; result unknown."
                        actual != target -> "Not applied: requested $target, read $actual kHz."
                        else -> "${current.id}: verified $actual kHz."
                    }
                }
            } catch (_: Exception) { "Write or read-back failed; result unknown." }
            val data = try { scan() } catch (_: Exception) { null }
            main.post {
                busy = false
                message = outcome
                if (data != null) snapshot = data else { root = "Unavailable"; snapshot = Snapshot() }
            }
        }
    }

    fun report(): String = buildString {
        appendLine("EZD Manager — device report")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Root: $root")
        appendLine("Battery: $battery")
        appendLine("CPU policies: ${snapshot.policies.size}")
        snapshot.policies.forEach {
            appendLine("${it.id}: CPUs ${it.cpus.ifBlank { "unknown" }}; current ${it.current ?: "?"}; min ${it.min ?: "?"}; max ${it.max ?: "?"}; original ${it.original ?: "?"} kHz")
            appendLine("  available kHz: ${it.frequencies.joinToString(" ").ifBlank { "unavailable" }}")
            appendLine("  governor: ${it.governor.ifBlank { "unavailable" }}; available: ${it.governors.ifBlank { "unavailable" }}; max writable: ${it.writable}")
        }
        appendLine("Thermal zones: ${snapshot.thermal.size}")
        snapshot.thermal.forEach { appendLine("${it.id} ${it.type}: ${it.temp?.let { n -> "${n / 1000.0} °C" } ?: "unavailable"}") }
        appendLine("Note: readings are live; baselines are saved on first successful discovery.")
    }

    companion object {
        // Unreadable/missing files yield empty fields, not fabricated capabilities.
        private val SCAN = """
            read_node() { if [ -r "${'$'}1" ]; then cat "${'$'}1" 2>/dev/null | tr '\n|' '  '; fi; }
            for d in /sys/devices/system/cpu/cpufreq/policy[0-9]*; do
              [ -d "${'$'}d" ] || continue
              w=0; [ -w "${'$'}d/scaling_max_freq" ] && w=1
              printf 'P|%s|%s|%s|%s|%s|%s|%s|%s|%s\n' "${'$'}{d##*/}" "${'$'}(read_node "${'$'}d/related_cpus")" "${'$'}(read_node "${'$'}d/scaling_cur_freq")" "${'$'}(read_node "${'$'}d/scaling_min_freq")" "${'$'}(read_node "${'$'}d/scaling_max_freq")" "${'$'}(read_node "${'$'}d/scaling_available_frequencies")" "${'$'}(read_node "${'$'}d/scaling_available_governors")" "${'$'}(read_node "${'$'}d/scaling_governor")" "${'$'}w"
            done
            for d in /sys/class/thermal/thermal_zone[0-9]*; do
              [ -d "${'$'}d" ] || continue
              printf 'T|%s|%s|%s\n' "${'$'}{d##*/}" "${'$'}(read_node "${'$'}d/type")" "${'$'}(read_node "${'$'}d/temp")"
            done
        """.trimIndent()
    }
}
