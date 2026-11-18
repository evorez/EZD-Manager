package com.evorez.ezdmanager.hardware

import android.content.Context
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.RootShellManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RootModulesAndPropRepository(private val context: Context) {

    private val backupDir: File
        get() = File(context.filesDir, "prop_backups").apply { mkdirs() }

    // ========================================================================
    // 1. MAGISK / KERNELSU / APATCH MODULE MANAGER
    // ========================================================================

    suspend fun scanModules(): Pair<String, List<RootModuleInfo>> {
        val script = """
            MGR="Unknown"
            if command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then MGR="KernelSU"; fi
            if command -v apd >/dev/null 2>&1 || [ -d /data/adb/ap ]; then MGR="APatch"; fi
            if command -v magisk >/dev/null 2>&1 || [ -d /data/adb/magisk ]; then MGR="Magisk"; fi
            echo "MGR|${'$'}MGR"
            for dir in /data/adb/modules/*; do
              [ -d "${'$'}dir" ] || continue
              ID=$(basename "${'$'}dir")
              PROP="${'$'}dir/module.prop"
              NAME="${'$'}ID"
              VER=""
              AUTH=""
              DESC=""
              if [ -f "${'$'}PROP" ]; then
                NAME=$(grep -m1 '^name=' "${'$'}PROP" | cut -d'=' -f2-)
                VER=$(grep -m1 '^version=' "${'$'}PROP" | cut -d'=' -f2-)
                AUTH=$(grep -m1 '^author=' "${'$'}PROP" | cut -d'=' -f2-)
                DESC=$(grep -m1 '^description=' "${'$'}PROP" | cut -d'=' -f2-)
              fi
              DIS=0; [ -e "${'$'}dir/disable" ] && DIS=1
              REM=0; [ -e "${'$'}dir/remove" ] && REM=1
              WEB=0; [ -f "${'$'}dir/webroot/index.html" ] && WEB=1
              echo "MOD|${'$'}ID|${'$'}NAME|${'$'}VER|${'$'}AUTH|${'$'}DESC|${'$'}DIS|${'$'}REM|${'$'}MGR|${'$'}WEB"
            done
        """.trimIndent()

        val res = RootShellManager.exec(script, timeoutMs = 4_500L)
        if (!res.success) return "No Root" to emptyList()

        var mgr = "Magisk/KSU"
        val list = mutableListOf<RootModuleInfo>()
        res.stdout.forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "MGR" -> mgr = parts.getOrNull(1)?.ifBlank { mgr } ?: mgr
                "MOD" -> {
                    val id = parts.getOrNull(1).orEmpty()
                    if (id.isNotBlank()) {
                        list.add(
                            RootModuleInfo(
                                id = id,
                                name = parts.getOrNull(2)?.ifBlank { id } ?: id,
                                version = parts.getOrNull(3).orEmpty(),
                                author = parts.getOrNull(4).orEmpty(),
                                description = parts.getOrNull(5).orEmpty(),
                                enabled = parts.getOrNull(6) != "1",
                                pendingRemove = parts.getOrNull(7) == "1",
                                managerType = parts.getOrNull(8) ?: mgr,
                                hasWebUi = parts.getOrNull(9) == "1"
                            )
                        )
                    }
                }
            }
        }
        return mgr to list.sortedBy { it.name.lowercase() }
    }

    suspend fun toggleModuleEnabled(module: RootModuleInfo, enable: Boolean): Pair<Boolean, String> {
        val safeId = module.id.replace(Regex("[^a-zA-Z0-9._-]"), "")
        if (safeId.isBlank()) return false to "Invalid module id"
        val path = "/data/adb/modules/$safeId/disable"
        val cmd = if (enable) "rm -f ${RootShellManager.shellQuote(path)}" else "touch ${RootShellManager.shellQuote(path)}"
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to "${module.name} ${if (enable) "enabled" else "disabled"} (reboot to apply fully)"
        } else {
            false to "Module update requires root access"
        }
    }

    suspend fun toggleModuleRemove(module: RootModuleInfo, remove: Boolean): Pair<Boolean, String> {
        val safeId = module.id.replace(Regex("[^a-zA-Z0-9._-]"), "")
        if (safeId.isBlank()) return false to "Invalid module id"
        val path = "/data/adb/modules/$safeId/remove"
        val cmd = if (remove) "touch ${RootShellManager.shellQuote(path)}" else "rm -f ${RootShellManager.shellQuote(path)}"
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to if (remove) "${module.name} marked for removal on next reboot" else "Cancelled removal of ${module.name}"
        } else {
            false to "Module update requires root access"
        }
    }

    suspend fun disableAllModules(): Pair<Boolean, String> {
        val script = """
            COUNT=0
            for dir in /data/adb/modules/*; do
              [ -d "${'$'}dir" ] || continue
              touch "${'$'}dir/disable" 2>/dev/null && COUNT=${'$'}((COUNT + 1))
            done
            echo "${'$'}COUNT"
        """.trimIndent()
        val res = RootShellManager.exec(script, timeoutMs = 4_500L)
        return if (res.success) {
            val count = res.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            true to "Deactivated $count Magisk/KernelSU modules into Safe Mode"
        } else {
            false to "Could not disable modules (requires root)"
        }
    }

    // ========================================================================
    // 2. BUILD.PROP EDITOR WITH AUTOMATIC SAFETY BACKUP
    // ========================================================================

    suspend fun loadBuildProps(rootGranted: Boolean): List<BuildPropEntry> {
        val script = """
            for f in /system/build.prop /vendor/build.prop /product/etc/build.prop; do
              if [ -r "${'$'}f" ]; then
                grep -E '^[a-zA-Z0-9_.-]+=' "${'$'}f" | head -n 180 | while IFS='=' read -r k v; do
                  echo "FILE|${'$'}f|${'$'}k|${'$'}v"
                done
              fi
            done
        """.trimIndent()
        val res = if (rootGranted) {
            RootShellManager.exec(script, timeoutMs = 4_500L)
        } else {
            RootShellManager.execUnprivileged(script, timeoutMs = 3_000L)
        }
        val map = LinkedHashMap<String, BuildPropEntry>()
        res.stdout.forEach { line ->
            val p = line.split('|', limit = 4)
            if (p.firstOrNull() == "FILE") {
                val src = p.getOrNull(1) ?: "build.prop"
                val key = p.getOrNull(2)?.trim().orEmpty()
                val value = p.getOrNull(3)?.trim().orEmpty()
                if (key.isNotBlank()) {
                    map[key] = BuildPropEntry(key, value, src.substringAfterLast('/'))
                }
            }
        }
        if (map.isEmpty()) {
            // Safe unprivileged getprop fallback
            val gp = RootShellManager.execUnprivileged("getprop | head -n 120", timeoutMs = 2_500L)
            val regex = Regex("^\\[([^\\]]+)\\]: \\[(.*)\\]$")
            gp.stdout.forEach { l ->
                val m = regex.find(l.trim())
                if (m != null) {
                    val k = m.groupValues[1]
                    val v = m.groupValues[2]
                    map[k] = BuildPropEntry(k, v, "runtime")
                }
            }
        }
        return map.values.sortedBy { it.key }
    }

    fun listPropBackups(): List<BuildPropBackupInfo> {
        return backupDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    BuildPropBackupInfo(
                        id = file.nameWithoutExtension,
                        fileName = file.name,
                        fullPath = file.absolutePath,
                        createdAt = json.optString("createdAt", ""),
                        entryCount = json.optInt("count", 0),
                        sizeBytes = file.length()
                    )
                }.getOrNull()
            } ?: emptyList()
    }

    suspend fun createPropBackup(entries: List<BuildPropEntry>, label: String = "manual"): BuildPropBackupInfo? {
        val snapshot = if (entries.isNotEmpty()) entries else loadBuildProps(true)
        if (snapshot.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val file = File(backupDir, "buildprop_${stamp}_${safeLabel}.json")
        val arr = JSONArray()
        snapshot.forEach { e ->
            arr.put(JSONObject().apply {
                put("key", e.key)
                put("value", e.value)
                put("sourceFile", e.sourceFile)
            })
        }
        val payload = JSONObject().apply {
            put("createdAt", created)
            put("count", snapshot.size)
            put("entries", arr)
        }
        file.writeText(payload.toString())
        return BuildPropBackupInfo(
            id = file.nameWithoutExtension,
            fileName = file.name,
            fullPath = file.absolutePath,
            createdAt = created,
            entryCount = snapshot.size,
            sizeBytes = file.length()
        )
    }

    suspend fun updateBuildProp(
        key: String,
        value: String,
        currentEntries: List<BuildPropEntry>
    ): Pair<Boolean, String> {
        val cleanKey = key.trim()
        if (!cleanKey.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
            return false to "Invalid property key syntax"
        }
        // Always auto-backup before modifying any property to prevent bootloops
        createPropBackup(currentEntries, "auto_before_${cleanKey.takeLast(14)}")

        val qK = RootShellManager.shellQuote(cleanKey)
        val qV = RootShellManager.shellQuote(value.trim())
        val cmd = "(command -v resetprop >/dev/null 2>&1 && resetprop $qK $qV) || setprop $qK $qV"
        val res = RootShellManager.exec(cmd, timeoutMs = 4_000L)
        return if (res.success) {
            true to "Auto-backup saved & updated $cleanKey safely (runtime resetprop)"
        } else {
            false to "Editing properties requires root access (auto-backup was preserved)"
        }
    }

    suspend fun restorePropBackup(backup: BuildPropBackupInfo): Pair<Boolean, String> {
        val file = File(backup.fullPath)
        if (!file.exists()) return false to "Backup file no longer exists"
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return false to "Corrupt backup JSON"
        val arr = json.optJSONArray("entries") ?: return false to "Backup contains no entries"
        val cmds = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val k = o.optString("key")
                val v = o.optString("value")
                if (k.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                    val qK = RootShellManager.shellQuote(k)
                    val qV = RootShellManager.shellQuote(v)
                    add("(command -v resetprop >/dev/null 2>&1 && resetprop $qK $qV) || setprop $qK $qV 2>/dev/null")
                }
            }
        }
        if (cmds.isEmpty()) return false to "No valid properties in backup"
        // Apply in chunks to keep shell line safe
        var applied = 0
        cmds.chunked(40).forEach { batch ->
            val res = RootShellManager.exec(batch.joinToString("; "), timeoutMs = 8_000L)
            if (res.success) applied += batch.size
        }
        return if (applied > 0) true to "Restored $applied properties from ${backup.createdAt} backup"
        else false to "Restore requires root access"
    }

    // ========================================================================
    // 3. GITHUB RELEASES UPDATE CHECKER & CHANGELOG
    // ========================================================================

    fun builtInChangelog(): List<Pair<String, List<String>>> = listOf(
        "v1.0.0 — Evorez EZD Refactor" to listOf(
            "Unified CPU & GPU live control card and combined engine screen",
            "Liquid-glass floating pill with backdrop blur, saturate(160%) tint & bottom fade mask",
            "iOS-style horizontal push/pop card transitions (350ms, prefers-reduced-motion aware)",
            "Boot-Loop Safe Mode Guard (auto-disables tweaks after 2 failed boots)",
            "ZRAM size & algorithm, VM swappiness/vfs_cache, I/O scheduler & read-ahead controls",
            "Thermal throttle profile presets, Animation Scale & GPU HWUI renderer (skiavk/skiagl)",
            "Quick Settings Tile & Home Screen Widget for one-tap profile switching",
            "Magisk / KernelSU Module Manager, Build.prop Editor with automatic backups, and OTA Checker"
        ),
        "v0.9.0 — Deep Hardware & Audio Studio" to listOf(
            "Independent Bass Enhancer (Sub 40Hz / Punch 60Hz / Warm 90Hz) + 4 animated visualizers",
            "Granular App Backup & Restore (Base APK, App Data, Ext Data, OBB & Media)",
            "Boot Memory startup snapshot restoration & custom DNS brand logos",
            "Redesigned Per-App tuning bottom sheet & 14 dark/light/special themes + Lite Mode"
        )
    )

    fun fetchLatestGitHubRelease(currentVersion: String): GitHubReleaseInfo {
        return runCatching {
            val url = URL("https://api.github.com/repos/evorez/EZD-Manager/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EZD-Manager-Android")
            }
            val code = conn.responseCode
            if (code == 404) {
                return GitHubReleaseInfo(
                    currentVersion = currentVersion,
                    latestTag = currentVersion,
                    statusMessage = "You are on the latest release ($currentVersion · no newer GitHub tag published yet)."
                )
            }
            if (code !in 200..299) {
                return GitHubReleaseInfo(
                    currentVersion = currentVersion,
                    statusMessage = "GitHub API returned HTTP $code — showing built-in changelog."
                )
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").ifBlank { json.optString("name", "") }
            val name = json.optString("name", tag)
            val notes = json.optString("body", "No release notes provided.")
            val pub = json.optString("published_at", "").take(10)
            val htmlUrl = json.optString("html_url", "https://github.com/evorez/EZD-Manager/releases")
            val newer = isVersionNewer(tag, currentVersion)
            GitHubReleaseInfo(
                currentVersion = currentVersion,
                latestTag = tag,
                latestName = name,
                publishedAt = pub,
                changelogMarkdown = notes,
                downloadUrl = htmlUrl,
                updateAvailable = newer,
                statusMessage = if (newer) "Update available: $tag" else "Up to date ($currentVersion)"
            )
        }.getOrElse { err ->
            GitHubReleaseInfo(
                currentVersion = currentVersion,
                statusMessage = "Offline or GitHub unreachable (${err.javaClass.simpleName}) — showing local changelog."
            )
        }
    }

    private fun isVersionNewer(remoteTag: String, current: String): Boolean {
        val r = remoteTag.removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val c = current.removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val max = maxOf(r.size, c.size)
        for (i in 0 until max) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
