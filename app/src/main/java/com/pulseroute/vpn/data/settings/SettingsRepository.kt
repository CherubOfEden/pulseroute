package com.pulseroute.vpn.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "pulse_route_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<UserSettings> = context.settingsDataStore.data
        .map { preferences ->
            UserSettings(
                selectedServerId = preferences[Keys.SELECTED_SERVER_ID],
                bypassLan = preferences[Keys.BYPASS_LAN] ?: true,
                bypassCountry = preferences[Keys.BYPASS_COUNTRY] ?: false,
                countryCode = preferences[Keys.COUNTRY_CODE] ?: "IR",
                blockAds = preferences[Keys.BLOCK_ADS] ?: false,
                remoteServersUrl = preferences[Keys.REMOTE_SERVERS_URL] ?: "",
                mtu = preferences[Keys.MTU] ?: 9000,
            )
        }
        .distinctUntilChanged()

    suspend fun snapshot(): UserSettings = settings.first()

    suspend fun setSelectedServerId(serverId: String?) {
        context.settingsDataStore.edit { preferences ->
            if (serverId == null) {
                preferences.remove(Keys.SELECTED_SERVER_ID)
            } else {
                preferences[Keys.SELECTED_SERVER_ID] = serverId
            }
        }
    }

    suspend fun setBypassLan(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BYPASS_LAN] = enabled }
    }

    suspend fun setBypassCountry(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BYPASS_COUNTRY] = enabled }
    }

    suspend fun setCountryCode(countryCode: String) {
        context.settingsDataStore.edit {
            it[Keys.COUNTRY_CODE] = countryCode.uppercase()
        }
    }

    suspend fun setBlockAds(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BLOCK_ADS] = enabled }
    }

    suspend fun setRemoteServersUrl(url: String) {
        context.settingsDataStore.edit { it[Keys.REMOTE_SERVERS_URL] = url.trim() }
    }

    suspend fun setMtu(mtu: Int) {
        context.settingsDataStore.edit {
            it[Keys.MTU] = mtu.coerceIn(1280, 9000)
        }
    }

    private object Keys {
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val BYPASS_COUNTRY = booleanPreferencesKey("bypass_country")
        val COUNTRY_CODE = stringPreferencesKey("country_code")
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val REMOTE_SERVERS_URL = stringPreferencesKey("remote_servers_url")
        val MTU = intPreferencesKey("mtu")
    }
}
