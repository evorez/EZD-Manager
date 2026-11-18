package com.evorez.ezdmanager.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import android.util.Base64
import androidx.core.content.ContextCompat
import android.os.Environment
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.model.AppBackupOptions
import com.evorez.ezdmanager.model.BackupPreview
import com.evorez.ezdmanager.model.LocalAppBackupRecord
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.model.ShizukuStatus
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupExportOptions(
    val includeEzdConfig: Boolean = true,
    val selectedApps: Set<String> = emptySet(),
    val includeMessagesAndCallLogs: Boolean = false,
    val includeNetworkConfig: Boolean = true,
    val includeBluetoothConfig: Boolean = false,
    val includeRootBaselines: Boolean = false,
    val passphrase: String = ""
)

data class BackupReport(
    val success: Boolean,
    val summary: String,
    val backedUpItems: List<String>,
    val skippedItems: List<String>
)

class BackupRestoreManager(
    private val context: Context,
    private val store: SettingsDataStore
) {
    companion object {
        const val ARCHIVE_VERSION = 1
        private val EXCLUDED_SESSION_PATTERNS = listOf("auth", "token", "session", "cookie", "credential", "account", "login", "oauth")
    }

    suspend fun createEncryptedBackup(
        targetUri: Uri,
        options: BackupExportOptions,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val backedUp = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val categories = mutableListOf<String>()

        val payload = JSONObject()
        val nowIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        payload.put("archiveVersion", ARCHIVE_VERSION)
        payload.put("createdAt", nowIso)
        payload.put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")

        // A. EZD Configuration Backup
        if (options.includeEzdConfig) {
            val ezdJson = store.exportConfigurationJson()
            payload.put("ezdConfig", ezdJson)
            categories.add("EZD Config")
            backedUp.add("EZD Settings, Themes, Profiles, Per-App Rules, Spoof Presets & Whitelist")
        }

        // B. Device Data Backup (Granular, never includes login sessions/tokens/caches)
        val deviceData = JSONObject()

        if (options.selectedApps.isNotEmpty()) {
            val appSnapshots = JSONArray()
            val pm = context.packageManager
            var exportedCount = 0
            options.selectedApps.forEach { pkg ->
                val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                if (info != null) {
                    val appObj = JSONObject().apply {
                        put("packageName", pkg)
                        put("versionName", info.versionName ?: "")
                        put("cacheExcluded", true)
                        put("loginSessionsExcluded", true)
                    }
                    if (rootStatus == RootStatus.GRANTED) {
                        val safePkg = pkg.replace(Regex("[^a-zA-Z0-9._]"), "")
                        val prefsRes = RootShellManager.exec(
                            "ls /data/user/0/$safePkg/shared_prefs 2>/dev/null | head -n 12",
                            timeoutMs = 2_000L
                        )
                        val safeFiles = prefsRes.stdout.filter { fname ->
                            val low = fname.lowercase()
                            low.endsWith(".xml") && EXCLUDED_SESSION_PATTERNS.none { pat -> low.contains(pat) }
                        }
                        appObj.put("nonSensitivePrefsFiles", JSONArray(safeFiles))
                        appObj.put("excludedSensitiveFiles", prefsRes.stdout.size - safeFiles.size)
                    }
                    appSnapshots.put(appObj)
                    exportedCount++
                }
            }
            deviceData.put("selectedApps", appSnapshots)
            categories.add("Selected Apps ($exportedCount)")
            backedUp.add("Selected Apps metadata ($exportedCount apps; caches & login/session tokens excluded)")
        }

        if (options.includeMessagesAndCallLogs) {
            val hasSms = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            val hasCalls = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            if (hasSms || hasCalls) {
                val msgObj = JSONObject()
                if (hasSms) {
                    val count = runCatching {
                        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), null, null, null)?.use { it.count } ?: 0
                    }.getOrDefault(0)
                    msgObj.put("smsCount", count)
                }
                if (hasCalls) {
                    val count = runCatching {
                        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, null)?.use { it.count } ?: 0
                    }.getOrDefault(0)
                    msgObj.put("callLogCount", count)
                }
                deviceData.put("messagesCallsSummary", msgObj)
                categories.add("Messages & Call Logs")
                backedUp.add("Messages & Call Log summary metadata")
            } else {
                skipped.add("Messages & Call Logs skipped (READ_SMS / READ_CALL_LOG permissions not requested/granted)")
            }
        }

        if (options.includeNetworkConfig) {
            val netObj = JSONObject().apply {
                put("privateDnsMode", runCatching { android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode") }.getOrNull() ?: "off")
                put("privateDnsSpecifier", runCatching { android.provider.Settings.Global.getString(context.contentResolver, "private_dns_specifier") }.getOrNull() ?: "")
            }
            if (rootStatus == RootStatus.GRANTED) {
                netObj.put("tcpCongestion", RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_congestion_control") ?: "")
                netObj.put("tcpFastOpen", RootShellManager.readSysfs("/proc/sys/net/ipv4/tcp_fastopen") ?: "")
            }
            deviceData.put("networkConfig", netObj)
            categories.add("Network Config")
            backedUp.add("Network DNS & TCP congestion preferences (Wi-Fi passphrases excluded)")
        }

        if (options.includeBluetoothConfig) {
            if (rootStatus == RootStatus.GRANTED) {
                val btRes = RootShellManager.exec("settings get global bluetooth_on; settings get secure bluetooth_name", timeoutMs = 2_000L)
                deviceData.put("bluetoothConfig", JSONObject().apply {
                    put("bluetoothOn", btRes.stdout.getOrNull(0) ?: "")
                    put("bluetoothName", btRes.stdout.getOrNull(1) ?: "")
                })
                categories.add("Bluetooth Config")
                backedUp.add("Bluetooth adapter settings (pairing keys excluded)")
            } else {
                skipped.add("Bluetooth Config skipped (requires root)")
            }
        }

        if (options.includeRootBaselines) {
            val cpuPrefs = context.getSharedPreferences("cpu_baselines", Context.MODE_PRIVATE).all
            val gpuPrefs = context.getSharedPreferences("gpu_baselines", Context.MODE_PRIVATE).all
            deviceData.put("rootBaselines", JSONObject().apply {
                put("cpuEntries", cpuPrefs.size)
                put("gpuEntries", gpuPrefs.size)
            })
            categories.add("EZD Root Baselines")
            backedUp.add("Hardware restore baselines (${cpuPrefs.size + gpuPrefs.size} nodes)")
        }

        payload.put("deviceData", deviceData)
        payload.put("categories", JSONArray(categories))

        val plaintext = payload.toString().toByteArray(Charsets.UTF_8)
        val sha256Hex = sha256(plaintext)

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val effectiveSecret = options.passphrase.ifBlank { "EZD_LOCAL_${Build.FINGERPRINT}" }
        val key = deriveAesKey(effectiveSecret, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val wrapper = JSONObject().apply {
            put("format", "EZD_BACKUP_AES_GCM")
            put("archiveVersion", ARCHIVE_VERSION)
            put("createdAt", nowIso)
            put("hasCustomPassphrase", options.passphrase.isNotBlank())
            put("sha256", sha256Hex)
            put("saltB64", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("ivB64", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertextB64", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }

        val writeOk = runCatching {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(wrapper.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Cannot open output stream")
        }.isSuccess

        return@withContext if (writeOk) {
            BackupReport(
                success = true,
                summary = "Encrypted AES-256-GCM archive saved (${categories.size} categories, ${skipped.size} skipped).",
                backedUpItems = backedUp,
                skippedItems = skipped
            )
        } else {
            BackupReport(false, "Failed to write backup file to selected destination.", backedUp, skipped)
        }
    }

    suspend fun inspectBackupArchive(
        sourceUri: Uri,
        passphrase: String
    ): Result<Pair<BackupPreview, JSONObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to open backup file")
            val wrapper = JSONObject(raw)
            val version = wrapper.optInt("archiveVersion", 0)
            require(version == ARCHIVE_VERSION) { "Unsupported backup archive version: $version" }

            val needsPass = wrapper.optBoolean("hasCustomPassphrase", false)
            if (needsPass && passphrase.isBlank()) {
                error("This archive is encrypted with a custom passphrase. Enter your passphrase to preview.")
            }

            val salt = Base64.decode(wrapper.getString("saltB64"), Base64.NO_WRAP)
            val iv = Base64.decode(wrapper.getString("ivB64"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(wrapper.getString("ciphertextB64"), Base64.NO_WRAP)
            val effectiveSecret = if (needsPass) passphrase else "EZD_LOCAL_${Build.FINGERPRINT}"
            val key = deriveAesKey(effectiveSecret, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)

            val computedSha = sha256(plaintext)
            val storedSha = wrapper.optString("sha256", "")
            val checksumValid = computedSha.equals(storedSha, ignoreCase = true)

            val payload = JSONObject(String(plaintext, Charsets.UTF_8))
            val catsArr = payload.optJSONArray("categories")
            val cats = buildList {
                if (catsArr != null) {
                    for (i in 0 until catsArr.length()) add(catsArr.getString(i))
                }
            }
            val devObj = payload.optJSONObject("deviceData")
            val appsArr = devObj?.optJSONArray("selectedApps")
            val preview = BackupPreview(
                archiveVersion = version,
                createdAt = payload.optString("createdAt", wrapper.optString("createdAt", "Unknown")),
                includesEzdConfig = payload.has("ezdConfig"),
                includesApps = appsArr != null && appsArr.length() > 0,
                appCount = appsArr?.length() ?: 0,
                categories = cats,
                checksumValid = checksumValid
            )
            preview to payload
        }
    }

    suspend fun restoreFromPayload(
        payload: JSONObject,
        restoreEzdConfig: Boolean,
        restoreNetworkConfig: Boolean,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        if (restoreEzdConfig && payload.has("ezdConfig")) {
            store.importConfigurationJson(payload.getJSONObject("ezdConfig"))
            restored.add("EZD Configuration (Themes, Profiles, Per-App Rules, Spoof Presets & Whitelist)")
        } else if (restoreEzdConfig) {
            skipped.add("EZD Config not present in archive")
        }

        val dev = payload.optJSONObject("deviceData")
        if (restoreNetworkConfig && dev != null && dev.has("networkConfig")) {
            val net = dev.getJSONObject("networkConfig")
            if (rootStatus == RootStatus.GRANTED) {
                val tcp = net.optString("tcpCongestion")
                if (tcp.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    RootShellManager.writeAndVerify("/proc/sys/net/ipv4/tcp_congestion_control", tcp)
                }
                val dnsMode = net.optString("privateDnsMode")
                val dnsHost = net.optString("privateDnsSpecifier")
                if (dnsMode == "hostname" && dnsHost.isNotBlank()) {
                    RootShellManager.exec(
                        "settings put global private_dns_mode hostname; settings put global private_dns_specifier ${RootShellManager.shellQuote(dnsHost)}"
                    )
                }
                restored.add("Network DNS & TCP congestion preferences")
            } else {
                skipped.add("Network system settings skipped (requires root)")
            }
        }

        if (dev != null && dev.has("selectedApps")) {
            skipped.add("App APK/login session restore is omitted by design to protect account security")
        }

        BackupReport(
            success = restored.isNotEmpty(),
            summary = if (restored.isNotEmpty()) {
                "Restored ${restored.size} section(s) cleanly."
            } else {
                "Nothing restored (${skipped.size} skipped)."
            },
            backedUpItems = restored,
            skippedItems = skipped
        )
    }

    // --- Real Local App Backup & Restore (Base APK, App Data, Ext Data, OBB, Media) ---

    private fun appBackupRootDir(): File {
        // Accessible home folder inside Downloads (works without root or broad storage perms)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicEzd = File(downloadsDir, "EZD Manager/Backups")
        if (runCatching { publicEzd.mkdirs(); publicEzd.canWrite() }.getOrDefault(false)) {
            return publicEzd
        }
        val rootHome = File("/storage/emulated/0/EZD Manager/Backups")
        if (runCatching { rootHome.mkdirs(); rootHome.canWrite() }.getOrDefault(false)) {
            return rootHome
        }
        val ext = context.getExternalFilesDir("app_backups") ?: File(context.filesDir, "app_backups")
        if (!ext.exists()) ext.mkdirs()
        return ext
    }

    private fun candidateBackupDirs(): List<File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOfNotNull(
            File(downloadsDir, "EZD Manager/Backups"),
            File("/storage/emulated/0/EZD Manager/Backups"),
            context.getExternalFilesDir("app_backups"),
            File(context.filesDir, "app_backups")
        ).distinctBy { it.absolutePath }.filter { it.exists() && it.isDirectory }
    }

    suspend fun createLocalAppBackups(
        selectedPackages: Set<String>,
        options: AppBackupOptions,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        if (selectedPackages.isEmpty()) {
            return@withContext BackupReport(false, "Select at least one app to back up.", emptyList(), emptyList())
        }
        val rootGranted = rootStatus == RootStatus.GRANTED
        val backedUp = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val pm = context.packageManager
        val rootDir = appBackupRootDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val createdIso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        selectedPackages.forEach { rawPkg ->
            val pkg = rawPkg.replace(Regex("[^a-zA-Z0-9._]"), "")
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val pkgInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            if (appInfo == null || pkgInfo == null) {
                skipped.add("$pkg: package info unavailable")
                return@forEach
            }
            val appLabel = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
            val bundleId = "${pkg}_$stamp"
            val bundleDir = File(rootDir, bundleId).apply { mkdirs() }
            val parts = mutableListOf<String>()

            // 1. Base APK (+ Split APKs)
            if (options.includeBaseApk) {
                val apkDir = File(bundleDir, "apks").apply { mkdirs() }
                val sources = buildList {
                    appInfo.sourceDir?.let { add(it) }
                    appInfo.splitSourceDirs?.let { addAll(it) }
                }.distinct()
                var apkCount = 0
                sources.forEachIndexed { idx, srcPath ->
                    val srcFile = File(srcPath)
                    val destName = if (idx == 0) "base.apk" else "split_${idx}_${srcFile.name}"
                    val destFile = File(apkDir, destName)
                    val copied = runCatching {
                        FileInputStream(srcFile).use { input ->
                            FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        true
                    }.getOrDefault(false) || (rootGranted && RootShellManager.exec(
                        "cp ${RootShellManager.shellQuote(srcPath)} ${RootShellManager.shellQuote(destFile.absolutePath)}"
                    ).success && destFile.exists())
                    if (copied && destFile.length() > 0L) apkCount++
                }
                if (apkCount > 0) {
                    parts.add("Base APK ($apkCount file${if (apkCount > 1) "s" else ""})")
                } else {
                    skipped.add("$appLabel Base APK could not be read")
                }
            }

            // 2. App Data (/data/user/0/<pkg>)
            if (options.includeAppData) {
                if (rootGranted) {
                    val dataTar = File(bundleDir, "app_data.tar.gz")
                    val qTar = RootShellManager.shellQuote(dataTar.absolutePath)
                    val res = RootShellManager.exec(
                        "DATA_DIR=\"/data/user/0/$pkg\"; [ -d \"\$DATA_DIR\" ] || DATA_DIR=\"/data/data/$pkg\"; " +
                            "if [ -d \"\$DATA_DIR\" ]; then tar --exclude='./cache' --exclude='./code_cache' --exclude='./no_backup' " +
                            "-czf $qTar -C \"\$DATA_DIR\" . 2>/dev/null && echo OK; fi",
                        timeoutMs = 25_000L
                    )
                    if (res.stdoutText.contains("OK") && dataTar.exists() && dataTar.length() > 0L) {
                        parts.add("App Data")
                    } else {
                        runCatching { dataTar.delete() }
                        skipped.add("$appLabel App Data empty or protected")
                    }
                } else {
                    skipped.add("$appLabel App Data skipped (requires root)")
                }
            }

            // 3. External Data (/sdcard/Android/data/<pkg>)
            if (options.includeExternalData) {
                val extTar = File(bundleDir, "external_data.tar.gz")
                val ok = archiveStorageSubdir(rootGranted, "/sdcard/Android/data/$pkg", extTar)
                if (ok) parts.add("External Data")
                else skipped.add("$appLabel External Data absent or inaccessible")
            }

            // 4. OBB Files (/sdcard/Android/obb/<pkg>)
            if (options.includeObb) {
                val obbTar = File(bundleDir, "obb.tar.gz")
                val ok = archiveStorageSubdir(rootGranted, "/sdcard/Android/obb/$pkg", obbTar)
                if (ok) parts.add("OBB Files")
                else skipped.add("$appLabel OBB files absent")
            }

            // 5. Media Files (/sdcard/Android/media/<pkg>)
            if (options.includeMedia) {
                val mediaTar = File(bundleDir, "media.tar.gz")
                val ok = archiveStorageSubdir(rootGranted, "/sdcard/Android/media/$pkg", mediaTar)
                if (ok) parts.add("Media Files")
                else skipped.add("$appLabel Media files absent")
            }

            val totalBytes = bundleDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (parts.isNotEmpty()) {
                val manifest = JSONObject().apply {
                    put("id", bundleId)
                    put("packageName", pkg)
                    put("appLabel", appLabel)
                    put("versionName", pkgInfo.versionName ?: "")
                    put("uid", appInfo.uid)
                    put("createdAt", createdIso)
                    put("parts", JSONArray(parts))
                    put("totalSizeBytes", totalBytes)
                }
                File(bundleDir, "manifest.json").writeText(manifest.toString(2))
                backedUp.add("$appLabel: ${parts.joinToString(", ")} (${formatBytes(totalBytes)})")
            } else {
                bundleDir.deleteRecursively()
            }
        }

        BackupReport(
            success = backedUp.isNotEmpty(),
            summary = if (backedUp.isNotEmpty()) {
                "Backed up ${backedUp.size} app bundle(s) with ${skipped.size} note(s)."
            } else {
                "No app components were backed up."
            },
            backedUpItems = backedUp,
            skippedItems = skipped
        )
    }

    private suspend fun archiveStorageSubdir(rootGranted: Boolean, sourcePath: String, destTar: File): Boolean {
        val qSrc = RootShellManager.shellQuote(sourcePath)
        val qDest = RootShellManager.shellQuote(destTar.absolutePath)
        if (rootGranted) {
            val res = RootShellManager.exec(
                "if [ -d $qSrc ] && [ -n \"\$(ls -A $qSrc 2>/dev/null)\" ]; then tar -czf $qDest -C $qSrc . 2>/dev/null && echo OK; fi",
                timeoutMs = 25_000L
            )
            if (res.stdoutText.contains("OK") && destTar.exists() && destTar.length() > 0L) return true
        }
        val dir = File(sourcePath)
        if (dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)) {
            val res = RootShellManager.execUnprivileged("tar -czf $qDest -C $qSrc . 2>/dev/null && echo OK", timeoutMs = 15_000L)
            if (res.stdoutText.contains("OK") && destTar.exists() && destTar.length() > 0L) return true
        }
        runCatching { destTar.delete() }
        return false
    }

    suspend fun listLocalAppBackups(): List<LocalAppBackupRecord> = withContext(Dispatchers.IO) {
        candidateBackupDirs()
            .flatMap { rootDir -> rootDir.listFiles()?.toList().orEmpty() }
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val mf = File(dir, "manifest.json")
                if (!mf.exists()) return@mapNotNull null
                runCatching {
                    val o = JSONObject(mf.readText())
                    val partsArr = o.optJSONArray("parts")
                    val parts = buildList {
                        if (partsArr != null) {
                            for (i in 0 until partsArr.length()) add(partsArr.getString(i))
                        }
                    }
                    LocalAppBackupRecord(
                        id = o.optString("id", dir.name),
                        folderPath = dir.absolutePath,
                        packageName = o.getString("packageName"),
                        appLabel = o.optString("appLabel", o.getString("packageName")),
                        versionName = o.optString("versionName", ""),
                        createdAt = o.optString("createdAt", ""),
                        parts = parts,
                        totalSizeBytes = o.optLong("totalSizeBytes", dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
                    )
                }.getOrNull()
            }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }

    suspend fun restoreLocalAppBackup(
        record: LocalAppBackupRecord,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = ShizukuManager.refreshStatus(context) == ShizukuStatus.GRANTED
        val bundleDir = File(record.folderPath)
        if (!bundleDir.exists()) {
            return@withContext BackupReport(false, "Backup bundle no longer exists.", emptyList(), emptyList())
        }
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val pkg = record.packageName.replace(Regex("[^a-zA-Z0-9._]"), "")

        // 1. Restore Base / Split APKs (Root OR Shizuku)
        val apkDir = File(bundleDir, "apks")
        val apkFiles = apkDir.listFiles { f -> f.extension == "apk" }?.sortedBy { it.name } ?: emptyList()
        if (apkFiles.isNotEmpty()) {
            if (rootGranted || shizukuGranted) {
                val paths = apkFiles.joinToString(" ") { RootShellManager.shellQuote(it.absolutePath) }
                val cmd = if (apkFiles.size == 1) {
                    "pm install -r -d $paths"
                } else {
                    "SID=\$(pm install-create -r -d | sed -n 's/.*\\[\\([0-9]*\\)\\].*/\\1/p'); " +
                        "if [ -n \"\$SID\" ]; then " +
                        apkFiles.mapIndexed { i, f -> "pm install-write -S ${f.length()} \$SID $i ${RootShellManager.shellQuote(f.absolutePath)}" }.joinToString(" && ") +
                        " && pm install-commit \$SID; fi"
                }
                val res = CommandBridge.execPrivileged(
                    cmd,
                    useRoot = rootGranted,
                    useShizuku = shizukuGranted,
                    timeoutMs = 35_000L
                )
                if (res.success && res.combinedText.contains("Success", ignoreCase = true)) {
                    restored.add("Base APK (${apkFiles.size} package file${if (apkFiles.size > 1) "s" else ""})")
                } else {
                    skipped.add("APK restore failed: ${res.combinedText.take(120)}")
                }
            } else {
                skipped.add("Silent APK install requires Root or Shizuku (APK saved at ${apkDir.absolutePath})")
            }
        }

        // 2. Restore App Data
        val dataTar = File(bundleDir, "app_data.tar.gz")
        if (dataTar.exists()) {
            if (rootGranted) {
                val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()
                val qTar = RootShellManager.shellQuote(dataTar.absolutePath)
                val ownerCmd = if (uid != null) "chown -R $uid:$uid \"\$DATA_DIR\" && " else ""
                val res = RootShellManager.exec(
                    "am force-stop $pkg 2>/dev/null; DATA_DIR=\"/data/user/0/$pkg\"; [ -d \"\$DATA_DIR\" ] || DATA_DIR=\"/data/data/$pkg\"; " +
                        "if [ -d \"\$DATA_DIR\" ]; then tar -xzf $qTar -C \"\$DATA_DIR\" && ${ownerCmd}restorecon -RF \"\$DATA_DIR\" 2>/dev/null; echo OK; fi",
                    timeoutMs = 30_000L
                )
                if (res.stdoutText.contains("OK")) restored.add("App Data")
                else skipped.add("App Data restore failed (ensure app is installed)")
            } else {
                skipped.add("App Data restore requires root")
            }
        }

        // 3. External Data
        val extTar = File(bundleDir, "external_data.tar.gz")
        if (extTar.exists()) {
            if (extractStorageSubdir(rootGranted, extTar, "/sdcard/Android/data/$pkg")) {
                restored.add("External Data")
            } else {
                skipped.add("External Data restore failed")
            }
        }

        // 4. OBB
        val obbTar = File(bundleDir, "obb.tar.gz")
        if (obbTar.exists()) {
            if (extractStorageSubdir(rootGranted, obbTar, "/sdcard/Android/obb/$pkg")) {
                restored.add("OBB Files")
            } else {
                skipped.add("OBB restore failed")
            }
        }

        // 5. Media
        val mediaTar = File(bundleDir, "media.tar.gz")
        if (mediaTar.exists()) {
            if (extractStorageSubdir(rootGranted, mediaTar, "/sdcard/Android/media/$pkg")) {
                restored.add("Media Files")
            } else {
                skipped.add("Media restore failed")
            }
        }

        BackupReport(
            success = restored.isNotEmpty(),
            summary = if (restored.isNotEmpty()) {
                "Restored ${record.appLabel}: ${restored.joinToString(", ")}."
            } else {
                "Could not restore ${record.appLabel}."
            },
            backedUpItems = restored,
            skippedItems = skipped
        )
    }

    private suspend fun extractStorageSubdir(rootGranted: Boolean, srcTar: File, destPath: String): Boolean {
        val qTar = RootShellManager.shellQuote(srcTar.absolutePath)
        val qDest = RootShellManager.shellQuote(destPath)
        return if (rootGranted) {
            val res = RootShellManager.exec("mkdir -p $qDest && tar -xzf $qTar -C $qDest && echo OK", timeoutMs = 25_000L)
            res.stdoutText.contains("OK")
        } else {
            val res = RootShellManager.execUnprivileged("mkdir -p $qDest && tar -xzf $qTar -C $qDest && echo OK", timeoutMs = 15_000L)
            res.stdoutText.contains("OK")
        }
    }

    suspend fun deleteLocalAppBackup(record: LocalAppBackupRecord): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(record.folderPath).deleteRecursively() }.getOrDefault(false)
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
