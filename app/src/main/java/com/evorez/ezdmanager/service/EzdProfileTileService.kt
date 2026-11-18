package com.evorez.ezdmanager.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile allowing users to cycle the 3 performance profiles
 * (Balanced -> Performance -> Powersave) directly from Quick Settings.
 */
class EzdProfileTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        syncTileUi()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val store = SettingsDataStore(applicationContext)
            val currentId = store.settingsFlow.first().activeProfileId
            val builtIn = ProfileRepository.builtInProfiles()
            val curIdx = builtIn.indexOfFirst { it.id == currentId }
            val nextProfile = builtIn[(curIdx + 1) % builtIn.size]

            store.setActiveProfileId(nextProfile.id)
            updateTileAppearance(nextProfile.name, nextProfile.id)

            val intent = Intent(applicationContext, RootDaemonService::class.java).apply {
                action = RootDaemonService.ACTION_SWITCH_PROFILE
                putExtra(RootDaemonService.EXTRA_PROFILE_ID, nextProfile.id)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            }
        }
    }

    private fun syncTileUi() {
        scope.launch {
            val activeId = runCatching {
                SettingsDataStore(applicationContext).settingsFlow.first().activeProfileId
            }.getOrDefault("balanced")
            val name = ProfileRepository.builtInProfiles().find { it.id == activeId }?.name ?: "Balanced"
            updateTileAppearance(name, activeId)
        }
    }

    private fun updateTileAppearance(profileName: String, activeId: String) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_ezd_stat)
        tile.label = "EZD: $profileName"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (activeId) {
                "beast" -> "100% Hard Lock"
                "performance" -> "70% Boost"
                "powersave" -> "Battery Save"
                else -> "Balanced"
            }
        }
        tile.state = if (activeId != "balanced") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        runCatching { tile.updateTile() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
