package com.evorez.ezdmanager.hardware

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.RootShellManager
import kotlin.math.roundToInt

class SystemToolsRepository(private val context: Context) {

    private val spoofPrefs = context.getSharedPreferences("spoof_baseline", Context.MODE_PRIVATE)
    private val netPrefs = context.getSharedPreferences("network_baseline", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var prevSfTimestamps: List<Long> = emptyList()

    companion object {
        val BUILT_IN_SPOOF_PRESETS = listOf(
            SpoofPreset("rog8", "ASUS ROG Phone 8 Pro", "asus", "asus", "ASUS_AI2401", "AI2401"),
            SpoofPreset("mi13pro", "Xiaomi 13 Pro", "Xiaomi", "Xiaomi", "2210132G", "nuwa"),
            SpoofPreset("bs5pro", "Black Shark 5 Pro", "blackshark", "blackshark", "KTUS-A0", "katyusha"),
            SpoofPreset("pixel9pro", "Google Pixel 9 Pro", "Google", "google", "Pixel 9 Pro", "caiman")
        )

        val DNS_PRESETS = listOf(
            DnsPreset("cloudflare", "Cloudflare", "1dot1dot1dot1.cloudflare-dns.com", "1.1.1.1", "https://cloudflare-dns.com/dns-query", "CF"),
            DnsPreset("google", "Google DNS", "dns.google", "8.8.8.8", "https://dns.google/dns-query", "GG"),
            DnsPreset("adguard", "AdGuard", "dns.adguard-dns.com", "94.140.14.14", "https://dns.adguard-dns.com/dns-query", "AG"),
            DnsPreset("quad9", "Quad9", "dns.quad9.net", "9.9.9.9", "https://dns.quad9.net/dns-query", "Q9"),
            DnsPreset("off", "System Default", "", "Auto", "", "SYS")
        )
    }

    // --- Power & Reboot (Phase 4) ---
    suspend fun isEdlVerifiedSupported(): Boolean {
        val res = RootShellManager.exec("getprop ro.board.platform; getprop ro.hardware", timeoutMs = 2_000L)
        if (!res.success) return false
        val combined = res.stdoutText.lowercase()
        val isQualcomm = listOf("qcom", "kona", "lahaina", "taro", "kalama", "pineapple", "msm", "sdm", "sm8", "sm7")
            .any { combined.contains(it) }
        if (!isQualcomm) return false
        val checkReboot = RootShellManager.exec("command -v reboot >/dev/null 2>&1 && echo OK", timeoutMs = 1_500L)
        return checkReboot.stdoutText.trim() == "OK"
    }

    suspend fun executePowerAction(action: PowerAction): Pair<Boolean, String> {
        val cmd = when (action) {
            PowerAction.REBOOT -> "svc power reboot || reboot"
            PowerAction.SOFT_REBOOT -> "setprop ctl.restart surfaceflinger && setprop ctl.restart zygote"
            PowerAction.RECOVERY -> "reboot recovery"
            PowerAction.BOOTLOADER -> "reboot bootloader"
            PowerAction.POWER_OFF -> "svc power shutdown || reboot -p"
            PowerAction.EDL -> {
                if (!isEdlVerifiedSupported()) {
                    return false to "EDL mode is only supported on verified Qualcomm platforms with root reboot support."
                }
                "reboot edl"
            }
        }
        val res = RootShellManager.exec(cmd, timeoutMs = 5_000L)
        return if (res.success) true to "${action.title} command dispatched"
        else false to "${action.title} failed: ${res.combinedText.ifBlank { "Requires root access" }}"
    }

    // --- Network Settings (Phase 9) ---
    suspend fun scanNetwork(vpnActive: Boolean = false): NetworkState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val link = cm?.getLinkProperties(activeNet)
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        val iface = link?.interfaceName ?: "None"

        val tcpCur = RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_congestion_control") ?: "Unavailable"
        val tcpAvail = RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_available_congestion_control")
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val fastOpenRaw = RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_fastopen")
        val tfoVal = fastOpenRaw?.toIntOrNull() ?: 0

        if (!netPrefs.contains("orig_tcp") && tcpCur != "Unavailable") {
            netPrefs.edit().putString("orig_tcp", tcpCur).putInt("orig_tfo", tfoVal).apply()
        }

        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull() ?: "off"
        val spec = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        }.getOrNull() ?: ""

        return NetworkState(
            interfaceName = iface,
            transportType = transport,
            tcpCongestion = tcpCur,
            availableTcp = tcpAvail,
            tcpFastOpenSupported = fastOpenRaw != null,
            tcpFastOpenValue = tfoVal,
            privateDnsMode = mode,
            privateDnsSpecifier = spec,
            originalTcpCongestion = netPrefs.getString("orig_tcp", null),
            vpnActive = vpnActive
        )
    }

    suspend fun setTcpCongestion(algo: String): Pair<Boolean, String> {
        if (!algo.matches(Regex("^[a-zA-Z0-9_-]+$"))) return false to "Invalid TCP algorithm"
        val (ok, msg) = RootShellManager.writeAndVerify("/proc/sys/net/ipv4/tcp_congestion_control", algo)
        return if (ok) true to "TCP congestion control set to $algo"
        else false to "TCP write failed: ${msg ?: "requires root/kernel support"}"
    }

    suspend fun applyTcpFastPreset(available: List<String>): Pair<Boolean, String> {
        val target = listOf("bbr", "westwood", "cubic").firstOrNull { it in available }
            ?: return false to "No supported TCP congestion algorithm found (requires root)"
        val (ok, msg) = setTcpCongestion(target)
        val tfoCheck = RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_fastopen")
        if (tfoCheck != null) {
            RootShellManager.writeAndVerify("/proc/sys/net/ipv4/tcp_fastopen", "3")
        }
        return if (ok) true to "TCP Fast applied ($target)" else false to msg
    }

    suspend fun applyTcpRescuePreset(available: List<String>): Pair<Boolean, String> {
        val target = netPrefs.getString("orig_tcp", null)?.takeIf { it in available }
            ?: listOf("cubic", "reno").firstOrNull { it in available }
            ?: return false to "No baseline TCP congestion algorithm available"
        val (ok, msg) = setTcpCongestion(target)
        val origTfo = netPrefs.getInt("orig_tfo", 1)
        if (RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_fastopen") != null) {
            RootShellManager.writeAndVerify("/proc/sys/net/ipv4/tcp_fastopen", origTfo.toString())
        }
        return if (ok) true to "TCP Rescue applied (restored $target)" else false to msg
    }

    suspend fun applyDnsPreset(preset: DnsPreset): Pair<Boolean, String> {
        val cmd = if (preset.id == "off" || preset.hostname.isBlank()) {
            "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier"
        } else {
            val qHost = RootShellManager.shellQuote(preset.hostname)
            "settings put global private_dns_mode hostname; settings put global private_dns_specifier $qHost"
        }
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to if (preset.id == "off") "Private DNS restored to system default" else "Private DNS set to ${preset.name}"
        } else {
            false to "Changing Private DNS requires root access"
        }
    }

    suspend fun resetNetworkSettings(availableTcp: List<String>): Pair<Boolean, String> {
        val (_, dnsMsg) = applyDnsPreset(DNS_PRESETS.last())
        val (_, tcpMsg) = applyTcpRescuePreset(availableTcp)
        return true to "$dnsMsg · $tcpMsg"
    }

    // --- Battery Saving Tools (Phase 10) ---
    suspend fun applyGmsBackgroundManagement(restrictNonEssential: Boolean): Pair<Boolean, String> {
        // Safely tune non-essential telemetry/analytics standby without killing auth/FCM/Play Services!
        val bucket = if (restrictNonEssential) "rare" else "active"
        val cmd = "am set-standby-bucket com.google.android.gms.feedback $bucket 2>/dev/null; " +
            "am set-standby-bucket com.google.android.partnersetup $bucket 2>/dev/null; " +
            "echo OK"
        val res = RootShellManager.exec(cmd, timeoutMs = 3_500L)
        return if (res.success) {
            true to "GMS non-essential telemetry set to $bucket bucket (Auth & Push notifications protected)"
        } else {
            false to "GMS background tuning requires root access"
        }
    }

    suspend fun applyBackgroundAppLimit(preset: String): Pair<Boolean, String> {
        val cmd = when (preset) {
            "Strict" -> "settings put global cached_apps_freezer enabled; settings put global app_standby_enabled 1"
            "Balanced" -> "settings put global cached_apps_freezer device_default; settings put global app_standby_enabled 1"
            else -> "settings delete global cached_apps_freezer; settings put global app_standby_enabled 1"
        }
        val res = RootShellManager.exec(cmd)
        return if (res.success) true to "Background app limit preset set to $preset"
        else false to "Background limit requires root access"
    }

    suspend fun runSafeMemoryTrim(apps: List<AppEntry>, whitelist: Set<String>): Pair<Boolean, String> {
        val targets = apps.filter { !it.isSystemApp && it.packageName !in whitelist }.take(15)
        if (targets.isEmpty()) return true to "No non-whitelisted user apps to trim"
        val cmds = targets.joinToString("; ") {
            val q = RootShellManager.shellQuote(it.packageName)
            "am send-trim-memory $q BACKGROUND 2>/dev/null"
        }
        val res = RootShellManager.exec(cmds, timeoutMs = 5_000L)
        return if (res.success) true to "Sent safe Android memory trim to ${targets.size} non-whitelisted apps"
        else false to "Memory trim requires root access"
    }

    suspend fun setAppBackgroundRestriction(
        packageName: String,
        restrict: Boolean,
        whitelist: Set<String>
    ): Pair<Boolean, String> {
        if (restrict && packageName in whitelist) {
            return false to "$packageName is protected on the Whitelist and will not be restricted."
        }
        val qPkg = RootShellManager.shellQuote(packageName)
        val mode = if (restrict) "ignore" else "allow"
        val cmd = "cmd appops set $qPkg RUN_IN_BACKGROUND $mode && cmd appops set $qPkg RUN_ANY_IN_BACKGROUND $mode"
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to "${if (restrict) "Restricted" else "Unrestricted"} background activity for $packageName"
        } else {
            false to "Background restriction requires root access"
        }
    }

    // --- Audio Enhancer (Phase 8: Genuine center frequencies in Hz) ---
    fun initAudioEngine(savedPreset: String = "Dynamic"): AudioState {
        val availableEffects = runCatching { AudioEffect.queryEffects()?.toList().orEmpty() }.getOrDefault(emptyList())
        val hasEq = availableEffects.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER }
        val hasBass = availableEffects.any { it.type == AudioEffect.EFFECT_TYPE_BASS_BOOST }
        val hasLoud = availableEffects.any { it.type == AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER }
        if (!hasEq) {
            return AudioState(
                engineReady = false,
                statusNote = "Device audio HAL does not expose a global Equalizer DSP effect."
            )
        }
        return runCatching {
            if (equalizer == null) {
                equalizer = Equalizer(0, 0).apply { enabled = true }
            }
            if (hasBass && bassBoost == null) {
                bassBoost = runCatching { BassBoost(0, 0).apply { enabled = true } }.getOrNull()
            }
            if (hasLoud && loudnessEnhancer == null) {
                loudnessEnhancer = runCatching { LoudnessEnhancer(0).apply { enabled = true } }.getOrNull()
            }
            val eq = equalizer ?: return@runCatching AudioState(
                engineReady = false,
                statusNote = "AudioFlinger global session rejected Equalizer attachment."
            )
            val count = eq.numberOfBands.toInt().coerceIn(1, 10)
            val freqs = (0 until count).map { (eq.getCenterFreq(it.toShort()) / 1000).coerceAtLeast(1) }
            val levels = (0 until count).map { eq.getBandLevel(it.toShort()).toInt() }
            val baseState = AudioState(
                activePreset = savedPreset,
                bassStrength = bassBoost?.roundedStrength?.toInt() ?: 300,
                loudnessGainMb = 0,
                bandLevels = levels,
                bandsHz = freqs,
                engineReady = true,
                statusNote = "Attached to hardware AudioEffect (${count} bands)"
            )
            applyAudioPreset(savedPreset, baseState)
        }.getOrElse {
            releaseAudio()
            AudioState(
                engineReady = false,
                statusNote = "AudioEffect global session unavailable on this Android runtime."
            )
        }
    }

    fun applyAudioPreset(presetName: String, current: AudioState): AudioState {
        if (!current.engineReady) return current.copy(activePreset = presetName)
        val bandCount = current.bandsHz.size.coerceAtLeast(1)
        val template = when (presetName) {
            "Bass" -> listOf(800, 500, 0, -100, 100)
            "Vocal" -> listOf(-200, 200, 650, 550, 150)
            "Music" -> listOf(450, 250, 100, 350, 500)
            "Dynamic" -> listOf(550, 200, 300, 450, 600)
            "Gaming" -> listOf(350, 150, 400, 700, 650)
            else -> List(bandCount) { 0 }
        }
        val mappedLevels = List(bandCount) { idx ->
            val mappedIdx = ((idx.toFloat() / bandCount) * template.size).toInt().coerceIn(0, template.lastIndex)
            template[mappedIdx]
        }
        val bass = when (presetName) {
            "Bass" -> 850
            "Gaming" -> 600
            "Dynamic" -> 500
            "Music" -> 450
            "Vocal" -> 150
            else -> 0
        }
        val loud = when (presetName) {
            "Gaming" -> 500
            "Dynamic" -> 350
            "Bass" -> 250
            else -> 150
        }
        applyAudioParams(mappedLevels, bass, loud)
        return current.copy(
            activePreset = presetName,
            bandLevels = mappedLevels,
            bassStrength = bass,
            loudnessGainMb = loud
        )
    }

    fun applyAudioParams(levels: List<Int>, bassStrength: Int, loudnessMb: Int) {
        runCatching {
            val eq = equalizer ?: return@runCatching
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            levels.forEachIndexed { idx, lvl ->
                if (idx < eq.numberOfBands) {
                    eq.setBandLevel(idx.toShort(), lvl.coerceIn(min, max).toShort())
                }
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) bb.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            }
            loudnessEnhancer?.setTargetGain(loudnessMb.coerceIn(0, 1200))
        }
    }

    fun releaseAudio() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }

    // --- Genuine FPS Meter (Phase 12F: Never confuse refresh rate with game FPS) ---
    suspend fun readGenuineFps(): FpsReading {
        // 1. Check kernel measured_fps sysfs nodes (Qualcomm / DRM / MediaTek)
        val sysfsRes = RootShellManager.exec(
            "for p in /sys/class/drm/sde-crtc-0/measured_fps /sys/class/graphics/fb0/measured_fps /sys/kernel/fps/fps; do " +
                "if [ -r \"${'$'}p\" ]; then echo \"SYS|${'$'}p|$(cat \"${'$'}p\" 2>/dev/null)\"; break; fi; done",
            timeoutMs = 2_000L
        )
        sysfsRes.stdout.firstOrNull { it.startsWith("SYS|") }?.let { line ->
            val parts = line.split('|', limit = 3)
            val valNum = Regex("([0-9]+(?:\\.[0-9]+)?)").find(parts.getOrNull(2).orEmpty())
                ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.roundToInt()
            if (valNum != null && valNum > 0) {
                return FpsReading(fps = valNum, source = parts[1])
            }
        }

        // 2. Fallback: SurfaceFlinger --latency timestamps
        val sfRes = RootShellManager.exec("dumpsys SurfaceFlinger --latency 2>/dev/null | tail -n +2", timeoutMs = 2_000L)
        if (sfRes.success && sfRes.stdout.size >= 4) {
            val timestamps = sfRes.stdout.mapNotNull { row ->
                val cols = row.trim().split(Regex("\\s+"))
                val presentNs = cols.getOrNull(1)?.toLongOrNull()
                presentNs?.takeIf { it > 0L && it != Long.MAX_VALUE }
            }.distinct().sorted()
            if (timestamps.size >= 4) {
                val deltasMs = timestamps.zipWithNext { a, b -> (b - a) / 1_000_000.0 }
                    .filter { it in 4.0..250.0 }
                if (deltasMs.isNotEmpty()) {
                    val avgMs = deltasMs.average()
                    val avgFps = (1000.0 / avgMs).roundToInt().coerceIn(1, 240)
                    val minFps = (1000.0 / deltasMs.max()).roundToInt().coerceIn(1, avgFps)
                    val maxFps = (1000.0 / deltasMs.min()).roundToInt().coerceIn(avgFps, 240)
                    prevSfTimestamps = timestamps
                    return FpsReading(
                        fps = avgFps,
                        minFps = minFps,
                        avgFps = avgFps,
                        maxFps = maxFps,
                        source = "SurfaceFlinger Frame Latency"
                    )
                }
            }
        }

        return FpsReading(
            fps = null,
            source = "FPS unavailable (requires active surface + root SurfaceFlinger/kernel access)"
        )
    }

    // --- Device Spoof (Phase 7) ---
    suspend fun scanSpoofState(): SpoofState {
        val check = RootShellManager.exec("command -v resetprop >/dev/null 2>&1 && echo YES || echo NO", timeoutMs = 2_500L)
        val hasResetprop = check.stdout.firstOrNull()?.trim() == "YES"
        if (!spoofPrefs.contains("orig_model")) {
            spoofPrefs.edit()
                .putString("orig_manufacturer", Build.MANUFACTURER)
                .putString("orig_brand", Build.BRAND)
                .putString("orig_model", Build.MODEL)
                .putString("orig_device", Build.DEVICE)
                .apply()
        }
        val props = RootShellManager.exec(
            "getprop ro.product.manufacturer; getprop ro.product.brand; getprop ro.product.model; getprop ro.product.device",
            timeoutMs = 2_500L
        )
        val lines = props.stdout
        return SpoofState(
            resetpropAvailable = hasResetprop,
            currentManufacturer = lines.getOrNull(0)?.ifBlank { Build.MANUFACTURER } ?: Build.MANUFACTURER,
            currentBrand = lines.getOrNull(1)?.ifBlank { Build.BRAND } ?: Build.BRAND,
            currentModel = lines.getOrNull(2)?.ifBlank { Build.MODEL } ?: Build.MODEL,
            currentDevice = lines.getOrNull(3)?.ifBlank { Build.DEVICE } ?: Build.DEVICE,
            hasBaseline = spoofPrefs.contains("orig_model"),
            statusMessage = if (hasResetprop) "" else "resetprop binary not found (requires Magisk / KernelSU / APatch)."
        )
    }

    suspend fun applySpoofPreset(preset: SpoofPreset): Pair<Boolean, String> {
        val props = mapOf(
            "ro.product.manufacturer" to preset.manufacturer,
            "ro.product.brand" to preset.brand,
            "ro.product.model" to preset.model,
            "ro.product.device" to preset.device
        )
        val cmds = props.entries.joinToString("; ") { (k, v) ->
            "resetprop ${RootShellManager.shellQuote(k)} ${RootShellManager.shellQuote(v)}"
        }
        val res = RootShellManager.exec(cmds, timeoutMs = 4_000L)
        return if (res.success) {
            true to "Applied spoof preset: ${preset.name} (${preset.model}). Some apps require restart or reboot for cached Build fields."
        } else {
            false to "resetprop failed: ${res.combinedText.ifBlank { "root/resetprop unavailable" }}"
        }
    }

    suspend fun resetSpoofToOriginal(): Pair<Boolean, String> {
        val mfg = spoofPrefs.getString("orig_manufacturer", Build.MANUFACTURER) ?: Build.MANUFACTURER
        val brand = spoofPrefs.getString("orig_brand", Build.BRAND) ?: Build.BRAND
        val model = spoofPrefs.getString("orig_model", Build.MODEL) ?: Build.MODEL
        val dev = spoofPrefs.getString("orig_device", Build.DEVICE) ?: Build.DEVICE
        return applySpoofPreset(
            SpoofPreset("orig", "Original Device", mfg, brand, model, dev)
        )
    }

    // --- Installed Apps ---
    fun loadInstalledApps(includeSystem: Boolean): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPkgs = pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        val allApps = runCatching { pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS) }
            .getOrDefault(emptyList())

        val combined = if (allApps.isNotEmpty()) {
            allApps.mapNotNull { info ->
                val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSys) return@mapNotNull null
                val pkgInfo = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isSystemApp = isSys,
                    versionName = pkgInfo?.versionName ?: "",
                    isDisabled = !info.enabled
                )
            }
        } else {
            launcherPkgs.mapNotNull { pkg ->
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val isSys = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                if (!includeSystem && isSys) return@mapNotNull null
                AppEntry(
                    packageName = pkg,
                    label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                    isSystemApp = isSys
                )
            }
        }
        return combined
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<AppEntry> { it.isSystemApp }.thenBy { it.label.lowercase() })
    }

    suspend fun clearAppCache(packageName: String): Pair<Boolean, String> {
        val qPkg = RootShellManager.shellQuote(packageName)
        val res = RootShellManager.exec(
            "rm -rf /data/user/0/$packageName/cache/* /sdcard/Android/data/$packageName/cache/* 2>/dev/null; pm trim-caches 128M >/dev/null 2>&1; echo OK"
        )
        return if (res.success) true to "Cleared cache for $packageName" else false to "Cache clear requires root ($qPkg)"
    }

    suspend fun forceStopApp(packageName: String): Pair<Boolean, String> {
        val qPkg = RootShellManager.shellQuote(packageName)
        val res = RootShellManager.exec("am force-stop $qPkg")
        return if (res.success) true to "Force stopped $packageName" else false to "Force stop requires root"
    }

    suspend fun toggleFreezeApp(packageName: String, freeze: Boolean): Pair<Boolean, String> {
        val qPkg = RootShellManager.shellQuote(packageName)
        val cmd = if (freeze) "pm disable-user --user 0 $qPkg" else "pm enable $qPkg"
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to "${if (freeze) "Frozen" else "Unfrozen"} $packageName"
        } else {
            false to "Freeze/unfreeze failed: ${res.combinedText.ifBlank { "Requires root" }}"
        }
    }

    suspend fun uninstallApp(app: AppEntry): Pair<Boolean, String> {
        val qPkg = RootShellManager.shellQuote(app.packageName)
        val cmd = if (app.isSystemApp) "pm uninstall -k --user 0 $qPkg" else "pm uninstall $qPkg"
        val res = RootShellManager.exec(cmd)
        return if (res.success) true to "Uninstalled ${app.label}" else false to "Uninstall failed: ${res.combinedText.ifBlank { "Requires root" }}"
    }
}
