package com.pulseroute.vpn.routing

data class RoutingOptions(
    val bypassLan: Boolean = true,
    val bypassCountry: Boolean = false,
    val countryCode: String? = null,
    val blockAds: Boolean = false,
)
