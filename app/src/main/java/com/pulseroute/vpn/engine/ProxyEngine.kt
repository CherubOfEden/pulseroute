package com.pulseroute.vpn.engine

import android.os.ParcelFileDescriptor

interface VpnPlatform {
    fun establishTun(): ParcelFileDescriptor
    fun protectSocket(fd: Int): Boolean
}

interface ProxyEngine {
    suspend fun start(configJson: String, platform: VpnPlatform)
    fun stop()
}

class EngineUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
