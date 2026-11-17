package com.pulseroute.vpn.config

import com.pulseroute.vpn.data.model.Protocol
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.model.TlsOptions
import com.pulseroute.vpn.data.model.TransportOptions
import com.pulseroute.vpn.data.model.TransportType
import com.pulseroute.vpn.routing.GeoAssets
import com.pulseroute.vpn.routing.RoutingOptions
import com.pulseroute.vpn.routing.SingBoxRouting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SingBoxConfigBuilder {

    private val outputJson = Json { prettyPrint = true }

    fun build(
        server: ServerEntry,
        routing: RoutingOptions,
        geoAssets: GeoAssets,
        mtu: Int,
    ): String {
        validate(server)

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("level", "warn")
                    put("timestamp", true)
                },
            )
            put(
                "dns",
                dns(
                    routing = routing,
                    hasGeoIp = geoAssets.geoIpPath != null,
                    hasGeoSite = geoAssets.geoSitePath != null,
                ),
            )
            putJsonArray("inbounds") { add(tunInbound(mtu)) }
            putJsonArray("outbounds") {
                add(proxyOutbound(server))
                add(
                    buildJsonObject {
                        put("type", "direct")
                        put("tag", "direct")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "block")
                        put("tag", "block")
                    },
                )
            }
            put("route", route(routing, geoAssets))
        }

        return outputJson.encodeToString(JsonObject.serializer(), config)
    }

    private fun tunInbound(mtu: Int): JsonObject = buildJsonObject {
        put("type", "tun")
        put("tag", "tun-in")
        putJsonArray("address") {
            add("172.19.0.1/30")
            add("fdfe:dcba:9876::1/126")
        }
        put("mtu", mtu.coerceIn(1280, 9000))
        put("auto_route", true)
        put("strict_route", false)
        put("stack", "mixed")
    }

    private fun dns(
        routing: RoutingOptions,
        hasGeoIp: Boolean,
        hasGeoSite: Boolean,
    ): JsonObject = buildJsonObject {
        putJsonArray("servers") {
            add(
                buildJsonObject {
                    put("tag", "dns-direct")
                    put("address", "local")
                },
            )
            add(
                buildJsonObject {
                    put("tag", "dns-remote")
                    put("address", "https://1.1.1.1/dns-query")
                    put("detour", "proxy")
                },
            )
        }
        put("rules", SingBoxRouting.dnsRules(routing, hasGeoIp, hasGeoSite))
        put("final", "dns-remote")
        put("strategy", "prefer_ipv4")
    }

    private fun route(routing: RoutingOptions, geoAssets: GeoAssets): JsonObject = buildJsonObject {
        put("rules", SingBoxRouting.rules(routing, geoAssets.geoIpPath != null, geoAssets.geoSitePath != null))
        put("final", "proxy")
        put("auto_detect_interface", true)
        geoAssets.geoIpPath?.let { path ->
            putJsonObject("geoip") { put("path", path) }
        }
        geoAssets.geoSitePath?.let { path ->
            putJsonObject("geosite") { put("path", path) }
        }
    }

    private fun proxyOutbound(server: ServerEntry): JsonObject = when (server.protocol) {
        Protocol.VMESS -> vmessOutbound(server)
        Protocol.VLESS -> vlessOutbound(server)
        Protocol.WIREGUARD -> wireGuardOutbound(server)
    }

    private fun vmessOutbound(server: ServerEntry): JsonObject {
        val options = requireNotNull(server.vmess) { "VMess settings are missing" }
        return buildJsonObject {
            put("type", "vmess")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("uuid", options.uuid)
            put("security", options.security.ifBlank { "auto" })
            put("alter_id", options.alterId.coerceAtLeast(0))
            transport(options.transport)?.let { put("transport", it) }
            tls(options.tls)?.let { put("tls", it) }
            putJsonObject("mux") {
                put("enabled", true)
                put("concurrency", 4)
            }
        }
    }

    private fun vlessOutbound(server: ServerEntry): JsonObject {
        val options = requireNotNull(server.vless) { "VLESS settings are missing" }
        return buildJsonObject {
            put("type", "vless")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("uuid", options.uuid)
            options.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            transport(options.transport)?.let { put("transport", it) }
            tls(options.tls)?.let { put("tls", it) }
            putJsonObject("mux") {
                put("enabled", true)
                put("concurrency", 4)
            }
        }
    }

    private fun wireGuardOutbound(server: ServerEntry): JsonObject {
        val options = requireNotNull(server.wireGuard) { "WireGuard settings are missing" }
        return buildJsonObject {
            put("type", "wireguard")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("private_key", options.privateKey)
            put("peer_public_key", options.peerPublicKey)
            options.presharedKey
                ?.takeIf { it.isNotBlank() }
                ?.let { put("pre_shared_key", it) }
            putJsonArray("local_address") {
                options.localAddresses.forEach(::add)
            }
            put("mtu", options.mtu.coerceIn(1280, 9000))
            if (options.reserved.isNotEmpty()) {
                putJsonArray("reserved") {
                    options.reserved.forEach(::add)
                }
            }
        }
    }

    private fun tls(options: TlsOptions?): JsonObject? {
        if (options == null || !options.enabled) return null

        return buildJsonObject {
            put("enabled", true)
            options.serverName
                ?.takeIf { it.isNotBlank() }
                ?.let { put("server_name", it) }
            if (options.allowInsecure) put("insecure", true)
            if (options.alpn.isNotEmpty()) {
                putJsonArray("alpn") { options.alpn.forEach(::add) }
            }

            val hasReality = !options.realityPublicKey.isNullOrBlank()
            if (hasReality) {
                putJsonObject("reality") {
                    put("enabled", true)
                    put("public_key", options.realityPublicKey)
                    options.realityShortId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put("short_id", it) }
                }
            }

            if (hasReality || !options.fingerprint.isNullOrBlank()) {
                putJsonObject("utls") {
                    put("enabled", true)
                    put("fingerprint", options.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                }
            }
        }
    }

    private fun transport(options: TransportOptions?): JsonObject? {
        if (options == null) return null

        return when (options.type) {
            TransportType.TCP -> null

            TransportType.WS -> buildJsonObject {
                put("type", "ws")
                put("path", options.path?.takeIf { it.isNotBlank() } ?: "/")
                options.host?.takeIf { it.isNotBlank() }?.let { host ->
                    putJsonObject("headers") { put("Host", host) }
                }
            }

            TransportType.GRPC -> buildJsonObject {
                put("type", "grpc")
                options.serviceName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("service_name", it) }
            }

            TransportType.HTTP -> buildJsonObject {
                put("type", "http")
                options.host?.takeIf { it.isNotBlank() }?.let { host ->
                    putJsonArray("host") { add(host) }
                }
                put("path", options.path?.takeIf { it.isNotBlank() } ?: "/")
            }

            TransportType.HTTPUPGRADE -> buildJsonObject {
                put("type", "httpupgrade")
                options.host?.takeIf { it.isNotBlank() }?.let { put("host", it) }
                put("path", options.path?.takeIf { it.isNotBlank() } ?: "/")
            }

            TransportType.H2 -> buildJsonObject {
                put("type", "http")
                options.host?.takeIf { it.isNotBlank() }?.let { host ->
                    putJsonArray("host") { add(host) }
                }
                put("path", options.path?.takeIf { it.isNotBlank() } ?: "/")
            }
        }
    }

    private fun validate(server: ServerEntry) {
        require(server.address.isNotBlank()) { "Server address is empty" }
        require(server.port in 1..65535) { "Server port is invalid" }

        when (server.protocol) {
            Protocol.VMESS -> require(!server.vmess?.uuid.isNullOrBlank()) {
                "VMess UUID is missing"
            }

            Protocol.VLESS -> require(!server.vless?.uuid.isNullOrBlank()) {
                "VLESS UUID is missing"
            }

            Protocol.WIREGUARD -> {
                val wireGuard = requireNotNull(server.wireGuard) {
                    "WireGuard settings are missing"
                }
                require(wireGuard.privateKey.isNotBlank()) { "WireGuard private key is missing" }
                require(wireGuard.peerPublicKey.isNotBlank()) {
                    "WireGuard peer public key is missing"
                }
                require(wireGuard.localAddresses.isNotEmpty()) {
                    "WireGuard local address is missing"
                }
            }
        }
    }
}
