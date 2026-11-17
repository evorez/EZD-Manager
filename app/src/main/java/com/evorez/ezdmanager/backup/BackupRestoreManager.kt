package com.evorez.ezdmanager.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import android.util.Base64
import androidx.core.content.ContextCompat
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.model.BackupPreview
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.root.RootShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
            backedUp.add("EZD Settings, Themes, Profiles, Per-App Tuning, Spoof Presets, Game Space & Whitelist")
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
            val preview = BackupPreview(
                archiveVersion = version,
                createdAt = payload.optString("createdAt", wrapper.optString("createdAt", "Unknown")),
                includesEzdConfig = payload.has("ezdConfig"),
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
            restored.add("EZD Configuration (Themes, Profiles, Per-App Tuning, Spoof Presets, Game Space, Whitelist)")
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
