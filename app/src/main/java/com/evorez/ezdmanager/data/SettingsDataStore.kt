package com.evorez.ezdmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.evorez.ezdmanager.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.ezdDataStore: DataStore<Preferences> by preferencesDataStore(name = "ezd_settings")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val floatingPill: FloatingPillConfig = FloatingPillConfig(),
    val notifications: NotificationPrefs = NotificationPrefs(),
    val activeProfileId: String = "balanced",
    val daemonEnabled: Boolean = true,
    val savedAudioPreset: String = "Dynamic",
    val gameSpace: GameSpaceConfig = GameSpaceConfig(),
    val edlSupportedVerified: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PILL_ENABLED = booleanPreferencesKey("floating_pill_enabled")
        val PILL_POSITION = stringPreferencesKey("floating_pill_position")
        val PILL_OPACITY = floatPreferencesKey("floating_pill_opacity")
        val PILL_SHORTCUTS = stringPreferencesKey("floating_pill_shortcuts")
        val NOTIF_DAEMON = booleanPreferencesKey("notif_daemon")
        val NOTIF_GAME = booleanPreferencesKey("notif_game")
        val NOTIF_BATTERY = booleanPreferencesKey("notif_battery")
        val NOTIF_UNREAD = intPreferencesKey("notif_unread")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val DAEMON_ENABLED = booleanPreferencesKey("daemon_enabled")
        val AUDIO_PRESET = stringPreferencesKey("audio_preset")
        val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles_json")
        val PER_APP_CONFIGS = stringPreferencesKey("per_app_configs_json")
        val CUSTOM_SPOOF_DEVICES = stringPreferencesKey("custom_spoof_devices_json")
        val GAME_SPACE_JSON = stringPreferencesKey("game_space_json")
        val BATTERY_WHITELIST = stringPreferencesKey("battery_whitelist_json")
    }

    val settingsFlow: Flow<UserSettings> = context.ezdDataStore.data.map { prefs ->
        val shortcutsRaw = prefs[Keys.PILL_SHORTCUTS].orEmpty()
        val shortcuts = if (shortcutsRaw.isBlank()) listOf("Game Space", "Power", "Network")
        else shortcutsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }

        UserSettings(
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            floatingPill = FloatingPillConfig(
                enabled = prefs[Keys.PILL_ENABLED] ?: false,
                position = enumOrDefault(prefs[Keys.PILL_POSITION], PillPosition.TOP_CENTER),
                compactOpacity = prefs[Keys.PILL_OPACITY] ?: 0.88f,
                shortcuts = shortcuts
            ),
            notifications = NotificationPrefs(
                daemonAlerts = prefs[Keys.NOTIF_DAEMON] ?: true,
                gameModeAlerts = prefs[Keys.NOTIF_GAME] ?: true,
                batteryAlerts = prefs[Keys.NOTIF_BATTERY] ?: false,
                unreadCount = prefs[Keys.NOTIF_UNREAD] ?: 0
            ),
            activeProfileId = prefs[Keys.ACTIVE_PROFILE] ?: "balanced",
            daemonEnabled = prefs[Keys.DAEMON_ENABLED] ?: true,
            savedAudioPreset = prefs[Keys.AUDIO_PRESET] ?: "Dynamic",
            gameSpace = parseGameSpace(prefs[Keys.GAME_SPACE_JSON].orEmpty())
        )
    }

    val customProfilesFlow: Flow<List<ProfileConfig>> = context.ezdDataStore.data.map { prefs ->
        parseProfiles(prefs[Keys.CUSTOM_PROFILES].orEmpty())
    }

    val perAppConfigsFlow: Flow<Map<String, PerAppConfig>> = context.ezdDataStore.data.map { prefs ->
        parsePerAppConfigs(prefs[Keys.PER_APP_CONFIGS].orEmpty())
    }

    val customSpoofPresetsFlow: Flow<List<SpoofPreset>> = context.ezdDataStore.data.map { prefs ->
        parseSpoofPresets(prefs[Keys.CUSTOM_SPOOF_DEVICES].orEmpty())
    }

    val batteryWhitelistFlow: Flow<Set<String>> = context.ezdDataStore.data.map { prefs ->
        parseStringSet(prefs[Keys.BATTERY_WHITELIST].orEmpty())
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.ezdDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFloatingPill(config: FloatingPillConfig) {
        context.ezdDataStore.edit {
            it[Keys.PILL_ENABLED] = config.enabled
            it[Keys.PILL_POSITION] = config.position.name
            it[Keys.PILL_OPACITY] = config.compactOpacity
            it[Keys.PILL_SHORTCUTS] = config.shortcuts.joinToString(",")
        }
    }

    suspend fun setNotificationPrefs(prefsValue: NotificationPrefs) {
        context.ezdDataStore.edit {
            it[Keys.NOTIF_DAEMON] = prefsValue.daemonAlerts
            it[Keys.NOTIF_GAME] = prefsValue.gameModeAlerts
            it[Keys.NOTIF_BATTERY] = prefsValue.batteryAlerts
            it[Keys.NOTIF_UNREAD] = prefsValue.unreadCount
        }
    }

    suspend fun setActiveProfileId(id: String) {
        context.ezdDataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    }

    suspend fun setDaemonEnabled(enabled: Boolean) {
        context.ezdDataStore.edit { it[Keys.DAEMON_ENABLED] = enabled }
    }

    suspend fun setAudioPreset(preset: String) {
        context.ezdDataStore.edit { it[Keys.AUDIO_PRESET] = preset }
    }

    suspend fun saveGameSpace(cfg: GameSpaceConfig) {
        val obj = JSONObject().apply {
            put("enabled", cfg.enabled)
            put("activeProfile", cfg.activeProfile.name)
            put("cpuLockEnabled", cfg.cpuLockEnabled)
            put("touchBoostEnabled", cfg.touchBoostEnabled)
            put("foregroundPriorityEnabled", cfg.foregroundPriorityEnabled)
            put("hbmBoostEnabled", cfg.hbmBoostEnabled)
            put("disableHwOverlays", cfg.disableHwOverlays)
            put("dndEnabled", cfg.dndEnabled)
            put("sidePanelEnabled", cfg.sidePanelEnabled)
            put("sidePanelRightSide", cfg.sidePanelRightSide)
            put("sidePanelShortcuts", JSONArray(cfg.sidePanelShortcuts.map { it.name }))
            put("selectedGames", JSONArray(cfg.selectedGames.toList()))
        }
        context.ezdDataStore.edit { it[Keys.GAME_SPACE_JSON] = obj.toString() }
    }

    suspend fun saveBatteryWhitelist(pkgs: Set<String>) {
        context.ezdDataStore.edit { it[Keys.BATTERY_WHITELIST] = JSONArray(pkgs.toList()).toString() }
    }

    suspend fun saveCustomProfiles(profiles: List<ProfileConfig>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("description", p.description)
                put("cpuGovernor", p.cpuGovernor)
                put("cpuMaxScalePercent", p.cpuMaxScalePercent)
                put("cpuMinScalePercent", p.cpuMinScalePercent)
                put("gpuGovernor", p.gpuGovernor)
                put("targetRefreshHz", p.targetRefreshHz?.toDouble() ?: -1.0)
                put("touchBoost", p.touchBoost)
            })
        }
        context.ezdDataStore.edit { it[Keys.CUSTOM_PROFILES] = arr.toString() }
    }

    suspend fun savePerAppConfigs(configs: Map<String, PerAppConfig>) {
        val arr = JSONArray()
        configs.values.forEach { c ->
            arr.put(JSONObject().apply {
                put("packageName", c.packageName)
                put("enabled", c.enabled)
                put("profileId", c.profileId)
                put("gpuProfile", c.gpuProfile)
                put("refreshRateHz", c.refreshRateHz?.toDouble() ?: -1.0)
                put("gameModeAssigned", c.gameModeAssigned)
                put("spoofPresetId", c.spoofPresetId ?: "")
                put("dnsMode", c.dnsMode.name)
                put("customDnsServer", c.customDnsServer)
                put("batteryRestricted", c.batteryRestricted)
                put("whitelisted", c.whitelisted)
                put("touchBoost", c.touchBoost)
                put("disableHwOverlays", c.disableHwOverlays)
            })
        }
        context.ezdDataStore.edit { it[Keys.PER_APP_CONFIGS] = arr.toString() }
    }

    suspend fun saveCustomSpoofPresets(presets: List<SpoofPreset>) {
        val arr = JSONArray()
        presets.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("manufacturer", s.manufacturer)
                put("brand", s.brand)
                put("model", s.model)
                put("device", s.device)
            })
        }
        context.ezdDataStore.edit { it[Keys.CUSTOM_SPOOF_DEVICES] = arr.toString() }
    }

    suspend fun exportConfigurationJson(): JSONObject {
        return context.ezdDataStore.data.map { p ->
            JSONObject().apply {
                put("themeMode", p[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
                put("activeProfile", p[Keys.ACTIVE_PROFILE] ?: "balanced")
                put("daemonEnabled", p[Keys.DAEMON_ENABLED] ?: true)
                put("audioPreset", p[Keys.AUDIO_PRESET] ?: "Dynamic")
                put("pillEnabled", p[Keys.PILL_ENABLED] ?: false)
                put("pillPosition", p[Keys.PILL_POSITION] ?: PillPosition.TOP_CENTER.name)
                put("pillOpacity", p[Keys.PILL_OPACITY]?.toDouble() ?: 0.88)
                put("pillShortcuts", p[Keys.PILL_SHORTCUTS] ?: "Game Space,Power,Network")
                put("notifDaemon", p[Keys.NOTIF_DAEMON] ?: true)
                put("notifGame", p[Keys.NOTIF_GAME] ?: true)
                put("notifBattery", p[Keys.NOTIF_BATTERY] ?: false)
                put("customProfiles", p[Keys.CUSTOM_PROFILES] ?: "[]")
                put("perAppConfigs", p[Keys.PER_APP_CONFIGS] ?: "[]")
                put("customSpoofDevices", p[Keys.CUSTOM_SPOOF_DEVICES] ?: "[]")
                put("gameSpace", p[Keys.GAME_SPACE_JSON] ?: "{}")
                put("batteryWhitelist", p[Keys.BATTERY_WHITELIST] ?: "[]")
            }
        }.first()
    }

    suspend fun importConfigurationJson(obj: JSONObject) {
        context.ezdDataStore.edit { prefs ->
            obj.optString("themeMode").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME_MODE] = it }
            obj.optString("activeProfile").takeIf { it.isNotBlank() }?.let { prefs[Keys.ACTIVE_PROFILE] = it }
            if (obj.has("daemonEnabled")) prefs[Keys.DAEMON_ENABLED] = obj.optBoolean("daemonEnabled", true)
            obj.optString("audioPreset").takeIf { it.isNotBlank() }?.let { prefs[Keys.AUDIO_PRESET] = it }
            if (obj.has("pillEnabled")) prefs[Keys.PILL_ENABLED] = obj.optBoolean("pillEnabled", false)
            obj.optString("pillPosition").takeIf { it.isNotBlank() }?.let { prefs[Keys.PILL_POSITION] = it }
            obj.optString("customProfiles").takeIf { it.isNotBlank() }?.let { prefs[Keys.CUSTOM_PROFILES] = it }
            obj.optString("perAppConfigs").takeIf { it.isNotBlank() }?.let { prefs[Keys.PER_APP_CONFIGS] = it }
            obj.optString("customSpoofDevices").takeIf { it.isNotBlank() }?.let { prefs[Keys.CUSTOM_SPOOF_DEVICES] = it }
            obj.optString("gameSpace").takeIf { it.isNotBlank() }?.let { prefs[Keys.GAME_SPACE_JSON] = it }
            obj.optString("batteryWhitelist").takeIf { it.isNotBlank() }?.let { prefs[Keys.BATTERY_WHITELIST] = it }
        }
    }

    private fun parseGameSpace(raw: String): GameSpaceConfig {
        if (raw.isBlank()) return GameSpaceConfig()
        return runCatching {
            val o = JSONObject(raw)
            val shortcutsArr = o.optJSONArray("sidePanelShortcuts")
            val shortcuts = if (shortcutsArr != null) {
                buildSet {
                    for (i in 0 until shortcutsArr.length()) {
                        runCatching { enumValueOf<GamePanelShortcut>(shortcutsArr.getString(i)) }.getOrNull()?.let { add(it) }
                    }
                }.ifEmpty { GameSpaceConfig().sidePanelShortcuts }
            } else GameSpaceConfig().sidePanelShortcuts

            val gamesArr = o.optJSONArray("selectedGames")
            val games = if (gamesArr != null) {
                buildSet {
                    for (i in 0 until gamesArr.length()) add(gamesArr.getString(i))
                }
            } else emptySet()

            GameSpaceConfig(
                enabled = o.optBoolean("enabled", true),
                activeProfile = enumOrDefault(o.optString("activeProfile"), GameProfileId.BALANCED),
                cpuLockEnabled = o.optBoolean("cpuLockEnabled", false),
                touchBoostEnabled = o.optBoolean("touchBoostEnabled", false),
                foregroundPriorityEnabled = o.optBoolean("foregroundPriorityEnabled", false),
                hbmBoostEnabled = o.optBoolean("hbmBoostEnabled", false),
                disableHwOverlays = o.optBoolean("disableHwOverlays", false),
                dndEnabled = o.optBoolean("dndEnabled", false),
                sidePanelEnabled = o.optBoolean("sidePanelEnabled", false),
                sidePanelRightSide = o.optBoolean("sidePanelRightSide", true),
                sidePanelShortcuts = shortcuts,
                selectedGames = games
            )
        }.getOrDefault(GameSpaceConfig())
    }

    private fun parseStringSet(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(emptySet())
    }

    private fun parseProfiles(raw: String): List<ProfileConfig> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val hz = o.optDouble("targetRefreshHz", -1.0)
                    add(
                        ProfileConfig(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            description = o.optString("description", "Custom tuning profile"),
                            isBuiltIn = false,
                            cpuGovernor = o.optString("cpuGovernor", "schedutil"),
                            cpuMaxScalePercent = o.optInt("cpuMaxScalePercent", 100),
                            cpuMinScalePercent = o.optInt("cpuMinScalePercent", 20),
                            gpuGovernor = o.optString("gpuGovernor", ""),
                            targetRefreshHz = if (hz > 0.0) hz.toFloat() else null,
                            touchBoost = o.optBoolean("touchBoost", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parsePerAppConfigs(raw: String): Map<String, PerAppConfig> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pkg = o.getString("packageName")
                    val hz = o.optDouble("refreshRateHz", -1.0)
                    put(
                        pkg,
                        PerAppConfig(
                            packageName = pkg,
                            enabled = o.optBoolean("enabled", false),
                            profileId = o.optString("profileId", "balanced"),
                            gpuProfile = o.optString("gpuProfile", ""),
                            refreshRateHz = if (hz > 0.0) hz.toFloat() else null,
                            gameModeAssigned = o.optBoolean("gameModeAssigned", false),
                            spoofPresetId = o.optString("spoofPresetId", "").ifBlank { null },
                            dnsMode = enumOrDefault(o.optString("dnsMode"), PerAppDnsMode.SYSTEM),
                            customDnsServer = o.optString("customDnsServer", "1.1.1.1"),
                            batteryRestricted = o.optBoolean("batteryRestricted", false),
                            whitelisted = o.optBoolean("whitelisted", false),
                            touchBoost = o.optBoolean("touchBoost", false),
                            disableHwOverlays = o.optBoolean("disableHwOverlays", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun parseSpoofPresets(raw: String): List<SpoofPreset> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SpoofPreset(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            manufacturer = o.getString("manufacturer"),
                            brand = o.getString("brand"),
                            model = o.getString("model"),
                            device = o.getString("device"),
                            isCustom = true
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
