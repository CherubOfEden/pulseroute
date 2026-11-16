package com.pulseroute.vpn.core

import android.content.Context
import com.pulseroute.vpn.data.repository.ServerRepository
import com.pulseroute.vpn.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val settingsRepository = SettingsRepository(applicationContext)

    val serverRepository = ServerRepository(
        context = applicationContext,
        okHttpClient = okHttpClient,
        json = json,
        scope = appScope,
    )
}
