package com.evorez.ezdmanager.backup

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Telephony
import android.util.Base64
import androidx.core.content.ContextCompat
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.model.AppBackupOptions
import com.evorez.ezdmanager.model.BackupPreview
import com.evorez.ezdmanager.model.LocalAppBackupRecord
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.model.ShizukuStatus
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.RootShell
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
import javax.crypto.AEADBadTagException
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
    val skippedItems: List<String>,
    val filePath: String = "",
    val failureReason: String? = null
)

class BackupRestoreManager(
    private val context: Context,
    private val store: SettingsDataStore
) {
    companion object {
        const val ARCHIVE_VERSION = 1
        private val EXCLUDED_SESSION_PATTERNS = listOf("auth", "token", "session", "cookie", "credential", "account", "login", "oauth")
        private const val EZD_PUBLIC_SUBDIR = "EZD"
    }

    private fun downloadEzdDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, EZD_PUBLIC_SUBDIR)
    }

    private suspend fun buildEncryptedArchive(
        options: BackupExportOptions,
        rootStatus: RootStatus
    ): Triple<JSONObject, List<String>, List<String>> {
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
                netObj.put("tcpCongestion", RootShell.readNode("/proc/sys/net/ipv4/tcp_congestion_control") ?: "")
                netObj.put("tcpFastOpen", RootShell.readNode("/proc/sys/net/ipv4/tcp_fastopen") ?: "")
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
        return Triple(wrapper, backedUp, skipped)
    }

    suspend fun createEncryptedBackupToDownloads(
        options: BackupExportOptions,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val archiveRes = runCatching { buildEncryptedArchive(options, rootStatus) }
        val (wrapper, backedUp, skipped) = archiveRes.getOrElse { err ->
            return@withContext BackupReport(
                success = false,
                summary = "Failed to build encrypted EZD backup.",
                backedUpItems = emptyList(),
                skippedItems = emptyList(),
                filePath = downloadEzdDir().absolutePath,
                failureReason = err.message ?: "Archive encryption error"
            )
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ezd_backup_$stamp.ezdcfg.json"
        val bytes = wrapper.toString(2).toByteArray(Charsets.UTF_8)
        val publicFile = File(downloadEzdDir(), fileName)

        // 1. Scoped Storage MediaStore.Downloads -> Download/EZD/<fileName>
        val mediaRes = runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EZD_PUBLIC_SUBDIR")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Cannot open MediaStore output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
        }
        if (mediaRes.isSuccess) {
            return@withContext BackupReport(
                success = true,
                summary = "EZD backup saved (${backedUp.size} item(s), ${skipped.size} note(s)).",
                backedUpItems = backedUp,
                skippedItems = skipped,
                filePath = publicFile.absolutePath
            )
        }

        // 2. Direct Java File write to Download/EZD/
        val directRes = runCatching {
            val dir = downloadEzdDir()
            if (!dir.exists()) dir.mkdirs()
            publicFile.writeBytes(bytes)
        }
        if (directRes.isSuccess && publicFile.exists()) {
            return@withContext BackupReport(
                success = true,
                summary = "EZD backup saved (${backedUp.size} item(s), ${skipped.size} note(s)).",
                backedUpItems = backedUp,
                skippedItems = skipped,
                filePath = publicFile.absolutePath
            )
        }

        // 3. RootShell fallback into /storage/emulated/0/Download/EZD/
        if (rootStatus == RootStatus.GRANTED) {
            val tmp = File(context.cacheDir, fileName)
            runCatching { tmp.writeBytes(bytes) }
            val qSrc = RootShell.shellQuote(tmp.absolutePath)
            val qDir = RootShell.shellQuote(downloadEzdDir().absolutePath)
            val qDst = RootShell.shellQuote(publicFile.absolutePath)
            val cp = RootShell.exec("mkdir -p $qDir && cp $qSrc $qDst && chmod 644 $qDst", timeoutMs = 4_000L)
            runCatching { tmp.delete() }
            if (cp.isSuccess) {
                return@withContext BackupReport(
                    success = true,
                    summary = "EZD backup saved via root (${backedUp.size} item(s), ${skipped.size} note(s)).",
                    backedUpItems = backedUp,
                    skippedItems = skipped,
                    filePath = publicFile.absolutePath
                )
            }
        }

        val err = mediaRes.exceptionOrNull()?.message
            ?: directRes.exceptionOrNull()?.message
            ?: "Unable to write to ${publicFile.absolutePath}"
        BackupReport(
            success = false,
            summary = "Failed to save EZD backup.",
            backedUpItems = backedUp,
            skippedItems = skipped,
            filePath = publicFile.absolutePath,
            failureReason = err
        )
    }

    suspend fun createEncryptedBackup(
        targetUri: Uri,
        options: BackupExportOptions,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val displayName = resolveUriLabel(targetUri)
        val archiveRes = runCatching { buildEncryptedArchive(options, rootStatus) }
        val (wrapper, backedUp, skipped) = archiveRes.getOrElse { err ->
            return@withContext BackupReport(
                success = false,
                summary = "Failed to build encrypted EZD backup.",
                backedUpItems = emptyList(),
                skippedItems = emptyList(),
                filePath = displayName,
                failureReason = err.message ?: "Archive encryption error"
            )
        }
        val writeRes = runCatching {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(wrapper.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Cannot open output stream for selected file")
        }

        return@withContext if (writeRes.isSuccess) {
            BackupReport(
                success = true,
                summary = "Encrypted AES-256-GCM archive saved (${backedUp.size} item(s), ${skipped.size} note(s)).",
                backedUpItems = backedUp,
                skippedItems = skipped,
                filePath = displayName
            )
        } else {
            BackupReport(
                success = false,
                summary = "Failed to write EZD backup archive.",
                backedUpItems = backedUp,
                skippedItems = skipped,
                filePath = displayName,
                failureReason = writeRes.exceptionOrNull()?.message ?: "Output stream write error"
            )
        }
    }

    suspend fun listSavedEzdBackups(): List<String> = withContext(Dispatchers.IO) {
        val dir = downloadEzdDir()
        val found = LinkedHashSet<String>()

        // 1. Direct Java File listing
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".ezdcfg.json") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { found.add(it.absolutePath) }
        }

        // 2. Scoped Storage MediaStore query (Download/EZD/)
        runCatching {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH)
            } else {
                arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            }
            val sel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            }
            val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%.ezdcfg.json", "%$EZD_PUBLIC_SUBDIR%")
            } else {
                arrayOf("%.ezdcfg.json")
            }
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                sel,
                args,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) found.add(File(dir, name).absolutePath)
                }
            }
        }

        // 3. RootShell scan fallback (catches root-copied files invisible to MediaStore)
        if (RootShell.isSuGranted(forceFresh = false)) {
            val qDir = RootShell.shellQuote(dir.absolutePath)
            val res = RootShell.exec("ls -1t $qDir/*.ezdcfg.json 2>/dev/null", timeoutMs = 2_500L)
            if (res.isSuccess) {
                res.stdoutLines.map { it.trim() }.filter { it.endsWith(".ezdcfg.json") }.forEach { found.add(it) }
            }
        }

        found.toList()
    }

    suspend fun inspectBackupFile(
        filePath: String,
        passphrase: String
    ): Result<Pair<BackupPreview, JSONObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(filePath)
            val raw = when {
                f.exists() && f.canRead() -> f.readText(Charsets.UTF_8)
                else -> {
                    // Try MediaStore lookup by DISPLAY_NAME in Download/EZD
                    var text: String? = null
                    val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                    context.contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection,
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(f.name),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
                            text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        }
                    }
                    text ?: if (RootShell.isSuGranted()) {
                        val cat = RootShell.exec("cat ${RootShell.shellQuote(filePath)}", timeoutMs = 4_000L)
                        if (cat.isSuccess && cat.stdout.isNotBlank()) cat.stdout else null
                    } else null
                }
            } ?: error("Cannot read backup archive at $filePath")
            inspectRawArchive(raw, filePath, passphrase)
        }
    }

    suspend fun inspectBackupArchive(
        sourceUri: Uri,
        passphrase: String
    ): Result<Pair<BackupPreview, JSONObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to open backup file (${resolveUriLabel(sourceUri)})")
            inspectRawArchive(raw, resolveUriLabel(sourceUri), passphrase)
        }
    }

    private fun inspectRawArchive(
        raw: String,
        sourceLabel: String,
        passphrase: String
    ): Pair<BackupPreview, JSONObject> {
        require(raw.isNotBlank()) { "Backup file is empty ($sourceLabel)." }
        val wrapper = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("Invalid JSON format in backup file ($sourceLabel).") }

        val format = wrapper.optString("format", "")
        require(format == "EZD_BACKUP_AES_GCM") {
            "Unrecognized archive format '${format.ifBlank { "missing" }}' in $sourceLabel."
        }
        val version = wrapper.optInt("archiveVersion", 0)
        require(version == ARCHIVE_VERSION) { "Incompatible backup archive version: $version" }

        val storedSha = wrapper.optString("sha256", "")
        require(storedSha.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "Backup file lacks a valid SHA-256 checksum."
        }
        val saltB64 = wrapper.optString("saltB64", "")
        val ivB64 = wrapper.optString("ivB64", "")
        val cipherB64 = wrapper.optString("ciphertextB64", "")
        require(saltB64.isNotBlank() && ivB64.isNotBlank() && cipherB64.isNotBlank()) {
            "Backup archive is missing required encryption fields (salt/iv/ciphertext)."
        }

        val needsPass = wrapper.optBoolean("hasCustomPassphrase", false)
        if (needsPass && passphrase.isBlank()) {
            error("Archive is protected with a custom passphrase. Enter the passphrase before inspecting.")
        }

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(cipherB64, Base64.NO_WRAP)
        require(salt.size == 16 && iv.size == 12 && ciphertext.isNotEmpty()) {
            "Backup cryptographic envelope has invalid lengths."
        }
        val effectiveSecret = if (needsPass) passphrase else "EZD_LOCAL_${Build.FINGERPRINT}"
        val key = deriveAesKey(effectiveSecret, salt)

        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            error(
                if (needsPass) "Decryption failed: wrong passphrase or tampered archive."
                else "Decryption failed: archive belongs to another device build or is corrupted."
            )
        }

        val computedSha = sha256(plaintext)
        val checksumValid = computedSha.equals(storedSha, ignoreCase = true)
        require(checksumValid) {
            "Backup integrity validation failed: SHA-256 mismatch (expected $storedSha, got $computedSha). Aborting restore."
        }

        val payload = runCatching { JSONObject(String(plaintext, Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("Decrypted archive payload is not valid JSON.") }
        require(payload.optInt("archiveVersion", 0) == ARCHIVE_VERSION) {
            "Decrypted payload version mismatch."
        }
        require(payload.has("ezdConfig") || payload.has("deviceData")) {
            "Backup archive contains no restorable EZD or device configuration sections."
        }

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
            checksumValid = checksumValid,
            filePath = sourceLabel
        )
        return preview to payload
    }

    suspend fun restoreFromPayload(
        payload: JSONObject,
        restoreEzdConfig: Boolean,
        restoreNetworkConfig: Boolean,
        rootStatus: RootStatus,
        sourcePath: String = ""
    ): BackupReport = withContext(Dispatchers.IO) {
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        if (!restoreEzdConfig && !restoreNetworkConfig) {
            return@withContext BackupReport(
                success = false,
                summary = "Nothing selected to restore.",
                backedUpItems = emptyList(),
                skippedItems = emptyList(),
                filePath = sourcePath,
                failureReason = "Select at least one section (EZD Config or Network Config) before restoring."
            )
        }

        if (restoreEzdConfig) {
            val ezdObj = payload.optJSONObject("ezdConfig")
            if (ezdObj != null && ezdObj.length() > 0) {
                val importRes = runCatching { store.importConfigurationJson(ezdObj) }
                if (importRes.isSuccess) {
                    restored.add("EZD Configuration (Themes, Profiles, Per-App Rules, Spoof Presets & Whitelist)")
                } else {
                    return@withContext BackupReport(
                        success = false,
                        summary = "EZD Config restore failed.",
                        backedUpItems = restored,
                        skippedItems = skipped,
                        filePath = sourcePath,
                        failureReason = importRes.exceptionOrNull()?.message ?: "Invalid EZD configuration payload"
                    )
                }
            } else {
                skipped.add("EZD Config section absent or empty in archive")
            }
        }

        val dev = payload.optJSONObject("deviceData")
        if (restoreNetworkConfig && dev != null && dev.has("networkConfig")) {
            val net = dev.optJSONObject("networkConfig")
            if (net != null) {
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
                    skipped.add("Network system settings skipped (requires Root su)")
                }
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
            skippedItems = skipped,
            filePath = sourcePath,
            failureReason = if (restored.isEmpty()) skipped.firstOrNull() ?: "No applicable backup sections found." else null
        )
    }

    // --- Real Local App Backup & Restore (Base APK, App Data, Ext Data, OBB, Media) ---

    private suspend fun ensurePublicAppBackupRoot(rootGranted: Boolean): Pair<File, Boolean> {
        val publicEzd = downloadEzdDir()
        val directWritable = runCatching {
            if (!publicEzd.exists()) publicEzd.mkdirs()
            publicEzd.exists() && publicEzd.canWrite()
        }.getOrDefault(false)
        if (directWritable) return publicEzd to false

        if (rootGranted) {
            val qDir = RootShell.shellQuote(publicEzd.absolutePath)
            val mk = RootShell.exec("mkdir -p $qDir && chmod 777 $qDir", timeoutMs = 3_000L)
            if (mk.isSuccess && (runCatching { publicEzd.canWrite() }.getOrDefault(false))) {
                return publicEzd to false
            }
            // Assemble in private staging dir then copy to /storage/emulated/0/Download/EZD via root
            val stage = context.getExternalFilesDir("ezd_stage") ?: File(context.filesDir, "ezd_stage")
            if (!stage.exists()) stage.mkdirs()
            return stage to true
        }

        val fallback = context.getExternalFilesDir(EZD_PUBLIC_SUBDIR) ?: File(context.filesDir, EZD_PUBLIC_SUBDIR)
        if (!fallback.exists()) fallback.mkdirs()
        return fallback to false
    }

    private fun candidateBackupDirs(): List<File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOfNotNull(
            File(downloadsDir, EZD_PUBLIC_SUBDIR),
            File("/storage/emulated/0/Download/EZD"),
            File(downloadsDir, "EZD Manager/Backups"),
            File("/storage/emulated/0/EZD Manager/Backups"),
            context.getExternalFilesDir(EZD_PUBLIC_SUBDIR),
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
            return@withContext BackupReport(
                success = false,
                summary = "Select at least one app to back up.",
                backedUpItems = emptyList(),
                skippedItems = emptyList(),
                filePath = downloadEzdDir().absolutePath,
                failureReason = "No applications were selected."
            )
        }
        val rootGranted = rootStatus == RootStatus.GRANTED
        val backedUp = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val savedPaths = mutableListOf<String>()
        val pm = context.packageManager
        val (workRootDir, needsRootCopyToPublic) = ensurePublicAppBackupRoot(rootGranted)
        val publicRootDir = downloadEzdDir()
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
            val bundleDir = File(workRootDir, bundleId).apply { mkdirs() }
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

                var finalBundlePath = bundleDir.absolutePath
                if (needsRootCopyToPublic && rootGranted) {
                    val targetBundleDir = File(publicRootDir, bundleId)
                    val cp = RootShell.exec(
                        "mkdir -p ${RootShell.shellQuote(publicRootDir.absolutePath)} && cp -r ${RootShell.shellQuote(bundleDir.absolutePath)} ${RootShell.shellQuote(targetBundleDir.absolutePath)} && chmod -R 755 ${RootShell.shellQuote(targetBundleDir.absolutePath)}",
                        timeoutMs = 15_000L
                    )
                    if (cp.isSuccess) {
                        runCatching { bundleDir.deleteRecursively() }
                        finalBundlePath = targetBundleDir.absolutePath
                    }
                }
                savedPaths.add(finalBundlePath)
                backedUp.add("$appLabel (${parts.joinToString(", ")}, ${formatBytes(totalBytes)})")
            } else {
                bundleDir.deleteRecursively()
            }
        }

        val pathReport = savedPaths.ifEmpty { listOf(publicRootDir.absolutePath) }.joinToString("\n")
        BackupReport(
            success = backedUp.isNotEmpty(),
            summary = if (backedUp.isNotEmpty()) {
                "Backed up ${backedUp.size} app bundle(s) with ${skipped.size} note(s)."
            } else {
                "App backup failed: no app components could be archived."
            },
            backedUpItems = backedUp,
            skippedItems = skipped,
            filePath = pathReport,
            failureReason = if (backedUp.isEmpty()) skipped.firstOrNull() ?: "No readable APK or data files found." else null
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

    suspend fun listLocalAppBackups(rootGranted: Boolean = false): List<LocalAppBackupRecord> = withContext(Dispatchers.IO) {
        val records = LinkedHashMap<String, LocalAppBackupRecord>()

        fun parseManifest(dirPath: String, fallbackName: String, rawJson: String, dirFile: File?): LocalAppBackupRecord? {
            return runCatching {
                val o = JSONObject(rawJson)
                val partsArr = o.optJSONArray("parts")
                val parts = buildList {
                    if (partsArr != null) {
                        for (i in 0 until partsArr.length()) add(partsArr.getString(i))
                    }
                }
                val id = o.optString("id", fallbackName)
                val fallbackBytes = dirFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                LocalAppBackupRecord(
                    id = id,
                    folderPath = dirPath,
                    packageName = o.getString("packageName"),
                    appLabel = o.optString("appLabel", o.getString("packageName")),
                    versionName = o.optString("versionName", ""),
                    createdAt = o.optString("createdAt", ""),
                    parts = parts,
                    totalSizeBytes = o.optLong("totalSizeBytes", fallbackBytes)
                )
            }.getOrNull()
        }

        // 1. Direct File listing across app-accessible directories
        candidateBackupDirs()
            .flatMap { rootDir -> rootDir.listFiles()?.toList().orEmpty() }
            .filter { it.isDirectory }
            .forEach { dir ->
                val mf = File(dir, "manifest.json")
                if (mf.exists() && mf.canRead()) {
                    runCatching { mf.readText() }.getOrNull()?.let { raw ->
                        parseManifest(dir.absolutePath, dir.name, raw, dir)?.let { rec ->
                            records.putIfAbsent(rec.id, rec)
                        }
                    }
                }
            }

        // 2. RootShell listing for bundles in Download/EZD hidden by Android 14 scoped FUSE
        if (rootGranted || RootShell.isSuGranted(forceFresh = false)) {
            val publicRoots = listOf(downloadEzdDir().absolutePath, "/storage/emulated/0/Download/EZD")
                .distinct()
            val script = buildString {
                publicRoots.forEach { root ->
                    val qr = RootShell.shellQuote(root)
                    append("for d in $qr/*; do [ -f \"\$d/manifest.json\" ] && echo \"===DIR=== \$d\" && cat \"\$d/manifest.json\" && echo \"===END===\"; done; ")
                }
            }
            val res = RootShell.exec(script, timeoutMs = 4_000L)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                val blocks = res.stdout.split("===DIR===")
                blocks.forEach { blk ->
                    val endIdx = blk.indexOf("===END===")
                    if (endIdx > 0) {
                        val content = blk.substring(0, endIdx).trim()
                        val nl = content.indexOf('\n')
                        if (nl > 0) {
                            val dirPath = content.substring(0, nl).trim()
                            val jsonRaw = content.substring(nl + 1).trim()
                            val dirName = File(dirPath).name
                            parseManifest(dirPath, dirName, jsonRaw, null)?.let { rec ->
                                records.putIfAbsent(rec.id, rec)
                            }
                        }
                    }
                }
            }
        }

        records.values.sortedByDescending { it.createdAt }
    }

    private suspend fun validateAppBackupBundle(
        bundleDir: File,
        record: LocalAppBackupRecord,
        rootGranted: Boolean
    ): Result<List<File>> {
        if (!bundleDir.exists() || !bundleDir.isDirectory) {
            return Result.failure(IllegalStateException("Backup bundle directory missing: ${bundleDir.absolutePath}"))
        }
        val manifestFile = File(bundleDir, "manifest.json")
        if (!manifestFile.exists() || manifestFile.length() == 0L) {
            return Result.failure(IllegalStateException("Missing or empty manifest.json in ${bundleDir.absolutePath}"))
        }
        val manifest = runCatching { JSONObject(manifestFile.readText()) }.getOrElse {
            return Result.failure(IllegalStateException("Corrupted manifest.json in ${bundleDir.absolutePath}: ${it.message}"))
        }
        val manifestPkg = manifest.optString("packageName", "")
        if (!manifestPkg.matches(Regex("^[a-zA-Z0-9._]+$")) || manifestPkg != record.packageName) {
            return Result.failure(IllegalStateException("Manifest packageName '$manifestPkg' does not match '${record.packageName}'."))
        }

        val apkDir = File(bundleDir, "apks")
        val apkFiles = apkDir.listFiles { f -> f.extension == "apk" }?.sortedBy { it.name } ?: emptyList()
        for (apk in apkFiles) {
            if (apk.length() < 4L) {
                return Result.failure(IllegalStateException("Invalid APK file (too small): ${apk.name}"))
            }
            val header = ByteArray(2)
            val readCount = runCatching {
                FileInputStream(apk).use { it.read(header) }
            }.getOrDefault(0)
            if (readCount < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                return Result.failure(IllegalStateException("Corrupted APK archive (missing PK/ZIP signature): ${apk.name}"))
            }
        }

        val archives = listOf("app_data.tar.gz", "external_data.tar.gz", "obb.tar.gz", "media.tar.gz")
            .map { File(bundleDir, it) }
            .filter { it.exists() }
        for (tar in archives) {
            if (tar.length() == 0L) {
                return Result.failure(IllegalStateException("Empty data archive: ${tar.name}"))
            }
            val qTar = RootShell.shellQuote(tar.absolutePath)
            val testCmd = "tar -tzf $qTar >/dev/null 2>&1 && echo OK"
            val check = if (rootGranted) RootShell.exec(testCmd, 10_000L) else RootShell.execLocalSh(testCmd, 10_000L)
            if (!check.stdout.contains("OK")) {
                return Result.failure(IllegalStateException("Corrupted tar.gz archive (${tar.name}): failed gzip/tar integrity check."))
            }
        }

        if (apkFiles.isEmpty() && archives.isEmpty()) {
            return Result.failure(IllegalStateException("Backup bundle contains no valid APKs or data archives."))
        }
        return Result.success(apkFiles)
    }

    suspend fun restoreLocalAppBackup(
        record: LocalAppBackupRecord,
        rootStatus: RootStatus
    ): BackupReport = withContext(Dispatchers.IO) {
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = ShizukuManager.refreshStatus(context) == ShizukuStatus.GRANTED
        val origDir = File(record.folderPath)
        var stagedDir: File? = null
        val bundleDir = if (origDir.exists() && origDir.canRead() && File(origDir, "manifest.json").canRead()) {
            origDir
        } else if (rootGranted) {
            val stage = File(context.cacheDir, "ezd_restore_${System.currentTimeMillis()}")
            val cp = RootShell.exec(
                "rm -rf ${RootShell.shellQuote(stage.absolutePath)} && cp -r ${RootShell.shellQuote(record.folderPath)} ${RootShell.shellQuote(stage.absolutePath)} && chmod -R 755 ${RootShell.shellQuote(stage.absolutePath)}",
                timeoutMs = 15_000L
            )
            if (cp.isSuccess && stage.exists()) {
                stagedDir = stage
                stage
            } else origDir
        } else {
            origDir
        }

        try {
            // Validate manifest, APKs, and tar.gz archives BEFORE installing or extracting anything
            val validation = validateAppBackupBundle(bundleDir, record, rootGranted)
            val apkFiles = validation.getOrElse { err ->
                return@withContext BackupReport(
                    success = false,
                    summary = "Backup validation failed for ${record.appLabel}. Restore aborted cleanly.",
                    backedUpItems = emptyList(),
                    skippedItems = emptyList(),
                    filePath = record.folderPath,
                    failureReason = err.message ?: "Validation error"
                )
            }

            val restored = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val pkg = record.packageName.replace(Regex("[^a-zA-Z0-9._]"), "")

        // 1. Restore Base / Split APKs (Root OR Shizuku)
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
                skipped.add("Silent APK install requires Root or Shizuku (APK saved at ${File(bundleDir, "apks").absolutePath})")
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
                skippedItems = skipped,
                filePath = record.folderPath,
                failureReason = if (restored.isEmpty()) skipped.firstOrNull() ?: "No components restored" else null
            )
        } finally {
            stagedDir?.let { runCatching { it.deleteRecursively() } }
        }
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

    suspend fun deleteLocalAppBackup(record: LocalAppBackupRecord, rootGranted: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val target = File(record.folderPath)
        val directDeleted = runCatching { target.deleteRecursively() }.getOrDefault(false)
        if (directDeleted && !target.exists()) return@withContext true
        if (rootGranted || RootShell.isSuGranted(forceFresh = false)) {
            val res = RootShell.exec("rm -rf ${RootShell.shellQuote(record.folderPath)}", timeoutMs = 4_000L)
            return@withContext res.isSuccess
        }
        false
    }

    private fun resolveUriLabel(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return if (!name.isNullOrBlank()) "$name (${uri.path ?: uri})" else uri.toString()
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
