package com.pulseroute.vpn.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object VpnController {
    const val ACTION_START = "com.pulseroute.vpn.action.START"
    const val ACTION_STOP = "com.pulseroute.vpn.action.STOP"
    const val EXTRA_SERVER_ID = "com.pulseroute.vpn.extra.SERVER_ID"

    fun start(context: Context, serverId: String) {
        val intent = Intent(context, TunnelVpnService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_SERVER_ID, serverId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, TunnelVpnService::class.java)
            .setAction(ACTION_STOP)
        context.startService(intent)
    }
}
