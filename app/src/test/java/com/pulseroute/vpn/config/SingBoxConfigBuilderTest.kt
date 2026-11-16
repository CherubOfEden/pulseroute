package com.pulseroute.vpn.config

import com.pulseroute.vpn.data.model.Protocol
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.model.TlsOptions
import com.pulseroute.vpn.data.model.TransportOptions
import com.pulseroute.vpn.data.model.TransportType
import com.pulseroute.vpn.data.model.VlessOptions
import com.pulseroute.vpn.routing.GeoAssets
import com.pulseroute.vpn.routing.RoutingOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigBuilderTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun buildsVlessWebSocketTlsOutbound() {
        val server = ServerEntry(
            id = "id",
            name = "Tokyo",
            protocol = Protocol.VLESS,
            address = "tokyo.example.com",
            port = 443,
            vless = VlessOptions(
                uuid = "uuid",
                flow = "xtls-rprx-vision",
                tls = TlsOptions(
                    enabled = true,
                    serverName = "tokyo.example.com",
                    alpn = listOf("h2", "http/1.1"),
                    fingerprint = "chrome",
                ),
                transport = TransportOptions(
                    type = TransportType.WS,
                    path = "/ws",
                    host = "cdn.example.com",
                ),
            ),
        )

        val config = build(server, RoutingOptions())
        val outbounds = config["outbounds"]!!.jsonArray
        val proxy = outbounds.first().jsonObject

        assertEquals("vless", proxy["type"]!!.jsonPrimitive.content)
        assertEquals("tokyo.example.com", proxy["server"]!!.jsonPrimitive.content)
        assertEquals(443, proxy["server_port"]!!.jsonPrimitive.content.toInt())
        assertEquals("uuid", proxy["uuid"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", proxy["flow"]!!.jsonPrimitive.content)
        assertEquals("ws", proxy["transport"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "cdn.example.com",
            proxy["transport"]!!.jsonObject["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "tokyo.example.com",
            proxy["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun emitsGeoRulesWhenAssetsExist() {
        val config = build(
            server = vlessServer(),
            routing = RoutingOptions(
                bypassLan = true,
                bypassCountry = true,
                countryCode = "IR",
                blockAds = true,
            ),
            geo = GeoAssets(
                geoIpPath = "/data/geo/geoip.db",
                geoSitePath = "/data/geo/geosite.db",
            ),
        )

        val route = config["route"]!!.jsonObject
        assertEquals("proxy", route["final"]!!.jsonPrimitive.content)
        assertEquals(
            "/data/geo/geoip.db",
            route["geoip"]!!.jsonObject["path"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "/data/geo/geosite.db",
            route["geosite"]!!.jsonObject["path"]!!.jsonPrimitive.content,
        )

        val rules = route["rules"]!!.jsonArray.map { it.jsonObject }
        assertTrue(rules.any { it["geosite"]?.toString()?.contains("category-ads-all") == true })
        assertTrue(rules.any { it["geoip"]?.toString()?.contains("\"ir\"") == true })
        assertTrue(rules.any { it["ip_is_private"]?.jsonPrimitive?.content == "true" })
    }

    @Test
    fun omitsGeoRulesWhenOnlyLegacyAssetsExist() {
        val config = build(
            server = vlessServer(),
            routing = RoutingOptions(
                bypassLan = true,
                bypassCountry = true,
                countryCode = "IR",
                blockAds = true,
            ),
            geo = GeoAssets(hasLegacyDatFallback = true),
        )

        val route = config["route"]!!.jsonObject
        val rules = route["rules"]!!.jsonArray.map { it.jsonObject }

        assertFalse(rules.any { it.containsKey("geosite") })
        assertFalse(rules.any { it.containsKey("geoip") })
        assertTrue(rules.any { it["ip_is_private"]?.jsonPrimitive?.content == "true" })
        assertTrue(route["geoip"] == null)
    }

    @Test
    fun buildsTunInboundWithConfiguredMtu() {
        val config = build(vlessServer(), RoutingOptions(), mtu = 1500)
        val tun = config["inbounds"]!!.jsonArray.first().jsonObject

        assertEquals("tun", tun["type"]!!.jsonPrimitive.content)
        assertEquals(1500, tun["mtu"]!!.jsonPrimitive.content.toInt())
        assertEquals("false", tun["strict_route"]!!.jsonPrimitive.content)
        assertEquals(
            "local",
            config["dns"]!!.jsonObject["servers"]!!.jsonArray.first()
                .jsonObject["address"]!!.jsonPrimitive.content,
        )
    }

    private fun build(
        server: ServerEntry,
        routing: RoutingOptions,
        geo: GeoAssets = GeoAssets(),
        mtu: Int = 9000,
    ): JsonObject {
        val text = SingBoxConfigBuilder.build(server, routing, geo, mtu)
        return json.parseToJsonElement(text).jsonObject
    }

    private fun vlessServer() = ServerEntry(
        id = "id",
        name = "Tokyo",
        protocol = Protocol.VLESS,
        address = "tokyo.example.com",
        port = 443,
        vless = VlessOptions(uuid = "uuid"),
    )
}
