package com.pulseroute.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pulseroute.vpn.ui.PulseRouteRoot
import com.pulseroute.vpn.ui.theme.PulseRouteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseRouteTheme {
                PulseRouteRoot()
            }
        }
    }
}
