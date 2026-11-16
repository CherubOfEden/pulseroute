package com.pulseroute.vpn

import android.app.Application
import com.pulseroute.vpn.core.AppContainer

class PulseRouteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
