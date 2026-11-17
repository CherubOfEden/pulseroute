package com.pulseroute.vpn.data.settings

data class UserSettings(
    val selectedServerId: String? = null,
    val selectedNetwork: String? = null,
    val bypassLan: Boolean = true,
    val bypassCountry: Boolean = false,
    val countryCode: String = "IR",
    val blockAds: Boolean = false,
    val remoteServersUrl: String = "",
    val mtu: Int = 1280,
)
