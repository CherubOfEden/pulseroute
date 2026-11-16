package com.pulseroute.vpn.routing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object SingBoxRouting {

    fun rules(
        options: RoutingOptions,
        hasGeoIp: Boolean = true,
        hasGeoSite: Boolean = true,
    ): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("action", "sniff")
            },
        )

        add(
            buildJsonObject {
                put("protocol", "dns")
                put("action", "hijack-dns")
            },
        )

        if (options.blockAds && hasGeoSite) {
            add(
                routeRule(
                    outbound = "block",
                    geosite = listOf("category-ads-all"),
                ),
            )
        }

        if (options.bypassLan) {
            add(routeRule(outbound = "direct", ipIsPrivate = true))
            if (hasGeoSite) {
                add(routeRule(outbound = "direct", geosite = listOf("private")))
            }
        }

        val country = options.countryCode
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length == 2 && it.all { char -> char.isLetter() } }

        if (options.bypassCountry && country != null) {
            if (hasGeoIp) add(routeRule(outbound = "direct", geoip = listOf(country)))
            if (hasGeoSite) add(routeRule(outbound = "direct", geosite = listOf(country)))
        }
    }

    fun dnsRules(
        options: RoutingOptions,
        hasGeoIp: Boolean = true,
        hasGeoSite: Boolean = true,
    ): JsonArray = buildJsonArray {
        val country = options.countryCode
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length == 2 && it.all { char -> char.isLetter() } }

        if (options.bypassLan) {
            if (hasGeoSite) {
                add(
                    buildJsonObject {
                        put("server", "dns-direct")
                        putJsonArray("geosite") { add("private") }
                    },
                )
            }
        }

        if (options.bypassCountry && country != null) {
            if (hasGeoSite) {
                add(
                    buildJsonObject {
                        put("server", "dns-direct")
                        putJsonArray("geosite") { add(country) }
                    },
                )
            }
            if (hasGeoIp) {
                add(
                    buildJsonObject {
                        put("server", "dns-direct")
                        putJsonArray("geoip") { add(country) }
                    },
                )
            }
        }

        add(
            buildJsonObject {
                put("server", "dns-remote")
            },
        )
    }

    private fun routeRule(
        outbound: String,
        geosite: List<String>? = null,
        geoip: List<String>? = null,
        ipIsPrivate: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("action", "route")
        put("outbound", outbound)
        if (ipIsPrivate) put("ip_is_private", true)
        if (!geosite.isNullOrEmpty()) {
            putJsonArray("geosite") { geosite.forEach(::add) }
        }
        if (!geoip.isNullOrEmpty()) {
            putJsonArray("geoip") { geoip.forEach(::add) }
        }
    }
}
