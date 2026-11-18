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
    val onboardingCompleted: Boolean = false,
    val userName: String = "Evorez User",
    val userTagline: String = "Device Tuner",
    val userPhotoPath: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themeStyle: EzdThemeStyle = EzdThemeStyle.AMOLED_OBSIDIAN,
    val animStyle: AppAnimStyle = AppAnimStyle.IOS_CARD_MORPH,
    val workMode: WorkMode = WorkMode.AUTO,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val activeProfileId: String = "balanced",
    val daemonEnabled: Boolean = true,
    val savedAudioPreset: String = "Dynamic",
    val audioBassEnabled: Boolean = true,
    val audioEqEnabled: Boolean = true,
    val audioVisualStyle: AudioVisualStyle = AudioVisualStyle.BASS_PULSE,
    val audioBassFocus: String = "Punch 60Hz",
    val bootMemory: BootMemoryConfig = BootMemoryConfig(),
    val accentPreset: AccentColorPreset = AccentColorPreset.THEME_DEFAULT,
    val blurIntensity: Float = 0.78f,
    val reduceAnimations: Boolean = false,
    val floatingPillOpaque: Boolean = false,
    val beastBatteryTempLimitC: Float = 43f,
    val beastSocTempLimitC: Float = 75f,
    val beastIgnoreThermalLimits: Boolean = false,
    val audioIncreaseLoudness: Boolean = false,
    val audioLoudnessGainMb: Int = 600,
    val backgroundTexture: BackgroundTexturePreset = BackgroundTexturePreset.PLAIN,
    val backgroundTextureOpacity: Float = 0.35f,
    val appFontStyle: AppFontStyle = AppFontStyle.SYSTEM,
    val pinnedPillTools: List<ToolId> = emptyList()
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_TAGLINE = stringPreferencesKey("user_tagline")
        val USER_PHOTO_PATH = stringPreferencesKey("user_photo_path")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ANIM_STYLE = stringPreferencesKey("anim_style")
        val WORK_MODE = stringPreferencesKey("work_mode")
        val NOTIF_DAEMON = booleanPreferencesKey("notif_daemon")
        val NOTIF_BATTERY = booleanPreferencesKey("notif_battery")
        val NOTIF_UNREAD = intPreferencesKey("notif_unread")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val DAEMON_ENABLED = booleanPreferencesKey("daemon_enabled")
        val AUDIO_PRESET = stringPreferencesKey("audio_preset")
        val AUDIO_BASS_ENABLED = booleanPreferencesKey("audio_bass_enabled")
        val AUDIO_EQ_ENABLED = booleanPreferencesKey("audio_eq_enabled")
        val AUDIO_VISUAL_STYLE = stringPreferencesKey("audio_visual_style")
        val AUDIO_BASS_FOCUS = stringPreferencesKey("audio_bass_focus")
        val BOOT_MEMORY_JSON = stringPreferencesKey("boot_memory_json")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val BLUR_INTENSITY = floatPreferencesKey("blur_intensity")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val FLOATING_PILL_OPAQUE = booleanPreferencesKey("floating_pill_opaque")
        val CUSTOM_PROFILES = stringPreferencesKey("custom_profiles_json")
        val PER_APP_CONFIGS = stringPreferencesKey("per_app_configs_json")
        val CUSTOM_SPOOF_DEVICES = stringPreferencesKey("custom_spoof_devices_json")
        val BATTERY_WHITELIST = stringPreferencesKey("battery_whitelist_json")
        val BEAST_BATTERY_LIMIT_C = floatPreferencesKey("beast_battery_limit_c")
        val BEAST_SOC_LIMIT_C = floatPreferencesKey("beast_soc_limit_c")
        val BEAST_IGNORE_THERMAL = booleanPreferencesKey("beast_ignore_thermal")
        val AUDIO_INCREASE_LOUDNESS = booleanPreferencesKey("audio_increase_loudness")
        val AUDIO_LOUDNESS_GAIN_MB = intPreferencesKey("audio_loudness_gain_mb")
        val BG_TEXTURE = stringPreferencesKey("background_texture")
        val BG_TEXTURE_OPACITY = floatPreferencesKey("background_texture_opacity")
        val APP_FONT_STYLE = stringPreferencesKey("app_font_style")
        val PINNED_PILL_TOOLS = stringPreferencesKey("pinned_pill_tools_json")
    }

    val settingsFlow: Flow<UserSettings> = context.ezdDataStore.data.map { prefs ->
        val savedProfile = prefs[Keys.ACTIVE_PROFILE] ?: "balanced"
        val normalizedProfile = when (savedProfile) {
            "dynamic" -> "balanced"
            "max_performance" -> "performance"
            else -> savedProfile
        }
        UserSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            userName = prefs[Keys.USER_NAME] ?: "Evorez User",
            userTagline = prefs[Keys.USER_TAGLINE] ?: "Device Tuner",
            userPhotoPath = prefs[Keys.USER_PHOTO_PATH] ?: "",
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            themeStyle = enumOrDefault(prefs[Keys.THEME_STYLE], EzdThemeStyle.AMOLED_OBSIDIAN),
            animStyle = enumOrDefault(prefs[Keys.ANIM_STYLE], AppAnimStyle.IOS_CARD_MORPH),
            workMode = enumOrDefault(prefs[Keys.WORK_MODE], WorkMode.AUTO),
            notifications = NotificationPrefs(
                daemonAlerts = prefs[Keys.NOTIF_DAEMON] ?: true,
                batteryAlerts = prefs[Keys.NOTIF_BATTERY] ?: false,
                unreadCount = prefs[Keys.NOTIF_UNREAD] ?: 0
            ),
            activeProfileId = normalizedProfile,
            daemonEnabled = prefs[Keys.DAEMON_ENABLED] ?: true,
            savedAudioPreset = prefs[Keys.AUDIO_PRESET] ?: "Dynamic",
            audioBassEnabled = prefs[Keys.AUDIO_BASS_ENABLED] ?: true,
            audioEqEnabled = prefs[Keys.AUDIO_EQ_ENABLED] ?: true,
            audioVisualStyle = enumOrDefault(prefs[Keys.AUDIO_VISUAL_STYLE], AudioVisualStyle.BASS_PULSE),
            audioBassFocus = prefs[Keys.AUDIO_BASS_FOCUS] ?: "Punch 60Hz",
            bootMemory = parseBootMemory(prefs[Keys.BOOT_MEMORY_JSON].orEmpty()),
            accentPreset = enumOrDefault(prefs[Keys.ACCENT_PRESET], AccentColorPreset.THEME_DEFAULT),
            blurIntensity = (prefs[Keys.BLUR_INTENSITY] ?: 0.78f).coerceIn(0f, 1f),
            reduceAnimations = prefs[Keys.REDUCE_ANIMATIONS] ?: false,
            floatingPillOpaque = prefs[Keys.FLOATING_PILL_OPAQUE] ?: false,
            beastBatteryTempLimitC = (prefs[Keys.BEAST_BATTERY_LIMIT_C] ?: 43f).coerceIn(36f, 55f),
            beastSocTempLimitC = (prefs[Keys.BEAST_SOC_LIMIT_C] ?: 75f).coerceIn(55f, 95f),
            beastIgnoreThermalLimits = prefs[Keys.BEAST_IGNORE_THERMAL] ?: false,
            audioIncreaseLoudness = prefs[Keys.AUDIO_INCREASE_LOUDNESS] ?: false,
            audioLoudnessGainMb = (prefs[Keys.AUDIO_LOUDNESS_GAIN_MB] ?: 600).coerceIn(0, 2400),
            backgroundTexture = enumOrDefault(prefs[Keys.BG_TEXTURE], BackgroundTexturePreset.PLAIN),
            backgroundTextureOpacity = (prefs[Keys.BG_TEXTURE_OPACITY] ?: 0.35f).coerceIn(0f, 1f),
            appFontStyle = enumOrDefault(prefs[Keys.APP_FONT_STYLE], AppFontStyle.SYSTEM),
            pinnedPillTools = parsePinnedTools(prefs[Keys.PINNED_PILL_TOOLS].orEmpty())
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

    suspend fun saveUserProfile(
        name: String,
        tagline: String,
        photoPath: String? = null,
        markOnboardingDone: Boolean? = null
    ) {
        context.ezdDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name.trim().ifBlank { "Evorez User" }
            prefs[Keys.USER_TAGLINE] = tagline.trim().ifBlank { "Device Tuner" }
            if (photoPath != null) prefs[Keys.USER_PHOTO_PATH] = photoPath
            if (markOnboardingDone != null) prefs[Keys.ONBOARDING_DONE] = markOnboardingDone
        }
    }

    suspend fun setAnimStyle(style: AppAnimStyle) {
        context.ezdDataStore.edit { it[Keys.ANIM_STYLE] = style.name }
    }

    suspend fun setWorkMode(mode: WorkMode) {
        context.ezdDataStore.edit { it[Keys.WORK_MODE] = mode.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.ezdDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setThemeStyle(style: EzdThemeStyle) {
        context.ezdDataStore.edit {
            it[Keys.THEME_STYLE] = style.name
            val currentMode = enumOrDefault(it[Keys.THEME_MODE], ThemeMode.DARK)
            if (currentMode != ThemeMode.LITE) {
                it[Keys.THEME_MODE] = if (style.prefersDark) ThemeMode.DARK.name else ThemeMode.LIGHT.name
            }
        }
    }

    suspend fun setFloatingPillOpaque(opaque: Boolean) {
        context.ezdDataStore.edit { it[Keys.FLOATING_PILL_OPAQUE] = opaque }
    }

    suspend fun setNotificationPrefs(prefsValue: NotificationPrefs) {
        context.ezdDataStore.edit {
            it[Keys.NOTIF_DAEMON] = prefsValue.daemonAlerts
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

    suspend fun setAudioFlags(
        bassEnabled: Boolean? = null,
        eqEnabled: Boolean? = null,
        visualStyle: AudioVisualStyle? = null,
        bassFocus: String? = null,
        increaseLoudness: Boolean? = null,
        loudnessGainMb: Int? = null
    ) {
        context.ezdDataStore.edit { prefs ->
            bassEnabled?.let { prefs[Keys.AUDIO_BASS_ENABLED] = it }
            eqEnabled?.let { prefs[Keys.AUDIO_EQ_ENABLED] = it }
            visualStyle?.let { prefs[Keys.AUDIO_VISUAL_STYLE] = it.name }
            bassFocus?.let { prefs[Keys.AUDIO_BASS_FOCUS] = it }
            increaseLoudness?.let { prefs[Keys.AUDIO_INCREASE_LOUDNESS] = it }
            loudnessGainMb?.let { prefs[Keys.AUDIO_LOUDNESS_GAIN_MB] = it.coerceIn(0, 2400) }
        }
    }

    suspend fun setBeastThermalConfig(
        batteryLimitC: Float? = null,
        socLimitC: Float? = null,
        ignoreLimits: Boolean? = null
    ) {
        context.ezdDataStore.edit { prefs ->
            batteryLimitC?.let { prefs[Keys.BEAST_BATTERY_LIMIT_C] = it.coerceIn(36f, 55f) }
            socLimitC?.let { prefs[Keys.BEAST_SOC_LIMIT_C] = it.coerceIn(55f, 95f) }
            ignoreLimits?.let { prefs[Keys.BEAST_IGNORE_THERMAL] = it }
        }
    }

    suspend fun saveBootMemory(config: BootMemoryConfig) {
        val obj = JSONObject().apply {
            put("applyOnBoot", config.applyOnBoot)
            put("savedProfileId", config.savedProfileId ?: "")
            put("savedRefreshHz", config.savedRefreshHz?.toDouble() ?: -1.0)
            put("savedDisableHwOverlays", config.savedDisableHwOverlays)
            put("savedDnsHost", config.savedDnsHost ?: "")
            put("savedDnsName", config.savedDnsName ?: "")
            put("savedAudioPreset", config.savedAudioPreset ?: "")
            put("lastCapturedAt", config.lastCapturedAt)
            put("summary", config.summary)
        }
        context.ezdDataStore.edit { it[Keys.BOOT_MEMORY_JSON] = obj.toString() }
    }

    suspend fun setAccentPreset(preset: AccentColorPreset) {
        context.ezdDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setBlurIntensity(intensity: Float) {
        context.ezdDataStore.edit { it[Keys.BLUR_INTENSITY] = intensity.coerceIn(0f, 1f) }
    }

    suspend fun setReduceAnimations(reduce: Boolean) {
        context.ezdDataStore.edit { it[Keys.REDUCE_ANIMATIONS] = reduce }
    }

    suspend fun setBackgroundTexture(preset: BackgroundTexturePreset) {
        context.ezdDataStore.edit { it[Keys.BG_TEXTURE] = preset.name }
    }

    suspend fun setBackgroundTextureOpacity(opacity: Float) {
        context.ezdDataStore.edit { it[Keys.BG_TEXTURE_OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    suspend fun setAppFontStyle(fontStyle: AppFontStyle) {
        context.ezdDataStore.edit { it[Keys.APP_FONT_STYLE] = fontStyle.name }
    }

    suspend fun setPinnedPillTools(tools: List<ToolId>) {
        val arr = JSONArray()
        tools.distinct().forEach { arr.put(it.name) }
        context.ezdDataStore.edit { it[Keys.PINNED_PILL_TOOLS] = arr.toString() }
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
            val photoPath = p[Keys.USER_PHOTO_PATH].orEmpty()
            val photoB64 = runCatching {
                if (photoPath.isNotBlank()) {
                    val f = java.io.File(photoPath)
                    if (f.exists() && f.length() in 1L..2_000_000L) {
                        android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
                    } else ""
                } else ""
            }.getOrDefault("")
            JSONObject().apply {
                put("onboardingCompleted", p[Keys.ONBOARDING_DONE] ?: false)
                put("userName", p[Keys.USER_NAME] ?: "Evorez User")
                put("userTagline", p[Keys.USER_TAGLINE] ?: "Device Tuner")
                put("userPhotoBase64", photoB64)
                put("themeMode", p[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
                put("themeStyle", p[Keys.THEME_STYLE] ?: EzdThemeStyle.AMOLED_OBSIDIAN.name)
                put("animStyle", p[Keys.ANIM_STYLE] ?: AppAnimStyle.IOS_CARD_MORPH.name)
                put("workMode", p[Keys.WORK_MODE] ?: WorkMode.AUTO.name)
                put("activeProfile", p[Keys.ACTIVE_PROFILE] ?: "balanced")
                put("daemonEnabled", p[Keys.DAEMON_ENABLED] ?: true)
                put("audioPreset", p[Keys.AUDIO_PRESET] ?: "Dynamic")
                put("bootMemory", p[Keys.BOOT_MEMORY_JSON] ?: "")
                put("accentPreset", p[Keys.ACCENT_PRESET] ?: AccentColorPreset.THEME_DEFAULT.name)
                put("blurIntensity", (p[Keys.BLUR_INTENSITY] ?: 0.78f).toDouble())
                put("reduceAnimations", p[Keys.REDUCE_ANIMATIONS] ?: false)
                put("floatingPillOpaque", p[Keys.FLOATING_PILL_OPAQUE] ?: false)
                put("notifDaemon", p[Keys.NOTIF_DAEMON] ?: true)
                put("notifBattery", p[Keys.NOTIF_BATTERY] ?: false)
                put("customProfiles", p[Keys.CUSTOM_PROFILES] ?: "[]")
                put("perAppConfigs", p[Keys.PER_APP_CONFIGS] ?: "[]")
                put("customSpoofDevices", p[Keys.CUSTOM_SPOOF_DEVICES] ?: "[]")
                put("batteryWhitelist", p[Keys.BATTERY_WHITELIST] ?: "[]")
                put("beastBatteryLimitC", (p[Keys.BEAST_BATTERY_LIMIT_C] ?: 43f).toDouble())
                put("beastSocLimitC", (p[Keys.BEAST_SOC_LIMIT_C] ?: 75f).toDouble())
                put("beastIgnoreThermal", p[Keys.BEAST_IGNORE_THERMAL] ?: false)
                put("audioIncreaseLoudness", p[Keys.AUDIO_INCREASE_LOUDNESS] ?: false)
                put("audioLoudnessGainMb", p[Keys.AUDIO_LOUDNESS_GAIN_MB] ?: 600)
                put("backgroundTexture", p[Keys.BG_TEXTURE] ?: BackgroundTexturePreset.PLAIN.name)
                put("backgroundTextureOpacity", (p[Keys.BG_TEXTURE_OPACITY] ?: 0.35f).toDouble())
                put("appFontStyle", p[Keys.APP_FONT_STYLE] ?: AppFontStyle.SYSTEM.name)
                put("pinnedPillTools", p[Keys.PINNED_PILL_TOOLS] ?: "[]")
            }
        }.first()
    }

    suspend fun importConfigurationJson(obj: JSONObject) {
        val photoB64 = obj.optString("userPhotoBase64", "")
        var restoredPhotoPath: String? = null
        if (photoB64.isNotBlank()) {
            runCatching {
                val bytes = android.util.Base64.decode(photoB64, android.util.Base64.NO_WRAP)
                if (bytes.isNotEmpty()) {
                    val out = java.io.File(context.filesDir, "user_profile.jpg")
                    out.writeBytes(bytes)
                    restoredPhotoPath = out.absolutePath
                }
            }
        }
        context.ezdDataStore.edit { prefs ->
            if (obj.has("onboardingCompleted")) {
                prefs[Keys.ONBOARDING_DONE] = obj.optBoolean("onboardingCompleted", true)
            }
            obj.optString("userName").takeIf { it.isNotBlank() }?.let { prefs[Keys.USER_NAME] = it }
            obj.optString("userTagline").takeIf { it.isNotBlank() }?.let { prefs[Keys.USER_TAGLINE] = it }
            restoredPhotoPath?.let { prefs[Keys.USER_PHOTO_PATH] = it }
            obj.optString("themeMode").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME_MODE] = it }
            obj.optString("themeStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.THEME_STYLE] = it }
            obj.optString("animStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.ANIM_STYLE] = it }
            obj.optString("workMode").takeIf { it.isNotBlank() }?.let { prefs[Keys.WORK_MODE] = it }
            obj.optString("activeProfile").takeIf { it.isNotBlank() }?.let { prefs[Keys.ACTIVE_PROFILE] = it }
            if (obj.has("daemonEnabled")) prefs[Keys.DAEMON_ENABLED] = obj.optBoolean("daemonEnabled", true)
            obj.optString("audioPreset").takeIf { it.isNotBlank() }?.let { prefs[Keys.AUDIO_PRESET] = it }
            obj.optString("bootMemory").takeIf { it.isNotBlank() }?.let { prefs[Keys.BOOT_MEMORY_JSON] = it }
            obj.optString("accentPreset").takeIf { it.isNotBlank() }?.let { prefs[Keys.ACCENT_PRESET] = it }
            if (obj.has("blurIntensity")) prefs[Keys.BLUR_INTENSITY] = obj.optDouble("blurIntensity", 0.78).toFloat().coerceIn(0f, 1f)
            if (obj.has("reduceAnimations")) prefs[Keys.REDUCE_ANIMATIONS] = obj.optBoolean("reduceAnimations", false)
            if (obj.has("floatingPillOpaque")) prefs[Keys.FLOATING_PILL_OPAQUE] = obj.optBoolean("floatingPillOpaque", false)
            obj.optString("customProfiles").takeIf { it.isNotBlank() }?.let { prefs[Keys.CUSTOM_PROFILES] = it }
            obj.optString("perAppConfigs").takeIf { it.isNotBlank() }?.let { prefs[Keys.PER_APP_CONFIGS] = it }
            obj.optString("customSpoofDevices").takeIf { it.isNotBlank() }?.let { prefs[Keys.CUSTOM_SPOOF_DEVICES] = it }
            obj.optString("batteryWhitelist").takeIf { it.isNotBlank() }?.let { prefs[Keys.BATTERY_WHITELIST] = it }
            if (obj.has("beastBatteryLimitC")) prefs[Keys.BEAST_BATTERY_LIMIT_C] = obj.optDouble("beastBatteryLimitC", 43.0).toFloat().coerceIn(36f, 55f)
            if (obj.has("beastSocLimitC")) prefs[Keys.BEAST_SOC_LIMIT_C] = obj.optDouble("beastSocLimitC", 75.0).toFloat().coerceIn(55f, 95f)
            if (obj.has("beastIgnoreThermal")) prefs[Keys.BEAST_IGNORE_THERMAL] = obj.optBoolean("beastIgnoreThermal", false)
            if (obj.has("audioIncreaseLoudness")) prefs[Keys.AUDIO_INCREASE_LOUDNESS] = obj.optBoolean("audioIncreaseLoudness", false)
            if (obj.has("audioLoudnessGainMb")) prefs[Keys.AUDIO_LOUDNESS_GAIN_MB] = obj.optInt("audioLoudnessGainMb", 600).coerceIn(0, 2400)
            obj.optString("backgroundTexture").takeIf { it.isNotBlank() }?.let { prefs[Keys.BG_TEXTURE] = it }
            if (obj.has("backgroundTextureOpacity")) prefs[Keys.BG_TEXTURE_OPACITY] = obj.optDouble("backgroundTextureOpacity", 0.35).toFloat().coerceIn(0f, 1f)
            obj.optString("appFontStyle").takeIf { it.isNotBlank() }?.let { prefs[Keys.APP_FONT_STYLE] = it }
            obj.optString("pinnedPillTools").takeIf { it.isNotBlank() }?.let { prefs[Keys.PINNED_PILL_TOOLS] = it }
        }
    }

    private fun parseBootMemory(raw: String): BootMemoryConfig {
        if (raw.isBlank()) return BootMemoryConfig()
        return runCatching {
            val o = JSONObject(raw)
            val hz = o.optDouble("savedRefreshHz", -1.0)
            BootMemoryConfig(
                applyOnBoot = o.optBoolean("applyOnBoot", false),
                savedProfileId = o.optString("savedProfileId", "").ifBlank { null },
                savedRefreshHz = if (hz > 0.0) hz.toFloat() else null,
                savedDisableHwOverlays = o.optBoolean("savedDisableHwOverlays", false),
                savedDnsHost = o.optString("savedDnsHost", "").ifBlank { null },
                savedDnsName = o.optString("savedDnsName", "").ifBlank { null },
                savedAudioPreset = o.optString("savedAudioPreset", "").ifBlank { null },
                lastCapturedAt = o.optLong("lastCapturedAt", 0L),
                summary = o.optString("summary", "No boot snapshot saved yet")
            )
        }.getOrDefault(BootMemoryConfig())
    }

    private fun parseStringSet(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(emptySet())
    }

    private fun parsePinnedTools(raw: String): List<ToolId> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    runCatching { enumValueOf<ToolId>(arr.getString(i)) }.getOrNull()?.let { add(it) }
                }
            }.distinct()
        }.getOrDefault(emptyList())
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
