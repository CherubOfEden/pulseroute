package com.pulseroute.vpn.engine

import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.TunOptions

interface VpnPlatform {
    fun establishTun(options: TunOptions): ParcelFileDescriptor
    fun protectSocket(fd: Int): Boolean
}

interface ProxyEngine {
    suspend fun start(configJson: String, platform: VpnPlatform)
    fun stop()
}

class EngineUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
