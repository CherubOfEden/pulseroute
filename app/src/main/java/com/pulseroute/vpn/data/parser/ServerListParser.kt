package com.pulseroute.vpn.data.parser

import com.pulseroute.vpn.data.model.Protocol
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.model.TlsOptions
import com.pulseroute.vpn.data.model.TransportOptions
import com.pulseroute.vpn.data.model.TransportType
import com.pulseroute.vpn.data.model.VlessOptions
import com.pulseroute.vpn.data.model.VmessOptions
import com.pulseroute.vpn.data.model.WireGuardOptions
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses plain JSON server lists and JavaScript wrappers such as Servers.js.
 *
 * The accepted schema is intentionally permissive because community lists use
 * inconsistent names (name/remark/remarks, host/address/server, ...).
 */
object ServerListParser {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val arrayContainers = listOf(
        "servers",
        "data",
        "result",
        "configs",
        "list",
        "proxies",
        "nodes",
    )

    fun parse(payload: String): List<ServerEntry> {
        val extracted = JsConfigExtractor.extract(payload)
        val element = try {
            json.parseToJsonElement(extracted)
        } catch (exception: Exception) {
            throw ServerParseException("Invalid JSON: ${exception.message}", exception)
        }

        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> findServerArray(element)
                ?: throw ServerParseException("JSON object does not contain a server array")

            else -> throw ServerParseException("Expected a JSON array or object")
        }

        val servers = array
            .mapNotNull { it as? JsonObject }
            .mapNotNull(::mapServer)
            .distinctBy { it.id }

        if (servers.isEmpty()) {
            throw ServerParseException("No valid servers were found in the payload")
        }
        return servers
    }

    private fun findServerArray(element: JsonObject): JsonArray? {
        for (key in arrayContainers) {
            when (val value = element[key]) {
                is JsonArray -> return value
                is JsonObject -> findServerArray(value)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun mapServer(source: JsonObject): ServerEntry? {
        val stream = source.obj("streamSettings")
        val ws = source.obj("wsSettings") ?: stream?.obj("wsSettings")
        val grpc = source.obj("grpcSettings") ?: stream?.obj("grpcSettings")
        val tlsSettings = source.obj("tlsSettings") ?: stream?.obj("tlsSettings")
        val realitySettings = source.obj("realitySettings")
            ?: stream?.obj("realitySettings")
            ?: tlsSettings?.obj("reality")

        val host = source.string(
            "address",
            "server",
            "serverAddress",
            "server_address",
            "ip",
            "domain",
            "host",
        )?.trim()
            ?: source.obj("server")?.string("address", "host")
        if (host.isNullOrBlank()) return null

        val port = source.int(
            "port",
            "server_port",
            "serverPort",
            "portInt",
        ) ?: return null
        if (port !in 1..65535) return null

        val explicitProtocol = source.string("protocol", "proto", "scheme")
            ?.lowercase()
            ?.let(::protocolFromText)
        val typeProtocol = source.string("type")
            ?.lowercase()
            ?.let(::protocolFromText)
        val uuid = source.string("uuid", "id", "userId", "user_id", "user")
            ?.trim()

        val protocol = explicitProtocol
            ?: typeProtocol
            ?: inferProtocol(source, uuid)
            ?: return null

        val network = (
            source.string("network", "net", "transport")
                ?: stream?.string("network")
                ?: source.string("type")?.takeIf { it.lowercase() in transportNames }
            )?.lowercase()

        val transport = TransportOptions(
            type = transportType(network),
            path = ws?.string("path") ?: source.string("path", "wsPath"),
            host = ws?.obj("headers")?.string("Host", "host", "HostHeader")
                ?: source.string("hostHeader", "wsHost", "host"),
            serviceName = grpc?.string("serviceName", "service_name")
                ?: source.string("serviceName", "grpcServiceName"),
        )

        val tlsEnabled = (
            (source.string("tls", "tlsSecurity")?.lowercase() ?: "") in setOf("tls", "reality", "true", "1")
                || (stream?.string("security")?.lowercase() ?: "") in setOf("tls", "reality")
                || tlsSettings != null
                || realitySettings != null
            )

        val tls = if (tlsEnabled) {
            TlsOptions(
                enabled = true,
                serverName = tlsSettings?.string("serverName", "server_name")
                    ?: source.string("sni", "serverName", "servername"),
                allowInsecure = source.boolean("allowInsecure", "skipCertVerify", "insecure")
                    ?: false,
                alpn = tlsSettings?.stringList("alpn")
                    ?: source.stringList("alpn"),
                fingerprint = tlsSettings?.string("fingerprint")
                    ?: source.string("fingerprint", "utlsFingerprint"),
                realityPublicKey = realitySettings?.string(
                    "publicKey",
                    "public_key",
                    "pbk",
                ) ?: source.string("publicKey", "pbk"),
                realityShortId = realitySettings?.string(
                    "shortId",
                    "short_id",
                    "sid",
                ) ?: source.string("shortId", "sid"),
            )
        } else {
            null
        }

        val name = source.string("name", "remark", "remarks", "title", "label", "ps")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "$host:$port"

        val countryCode = source.string("countryCode", "country_code", "cc", "country")
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 2 && it.all { char -> char.isLetter() } }

        val group = source.string("group", "category", "serverGroup", "networkGroup")
        val latency = source.int("latency", "latencyMs", "ping", "delay")

        val idSeed = buildString {
            append(protocol.name)
            append('|')
            append(host)
            append('|')
            append(port)
            append('|')
            append(uuid ?: source.string("publicKey", "peerPublicKey") ?: name)
        }

        return when (protocol) {
            Protocol.VMESS -> {
                if (uuid.isNullOrBlank()) return null
                ServerEntry(
                    id = stableId(idSeed),
                    name = name,
                    protocol = protocol,
                    address = host,
                    port = port,
                    countryCode = countryCode,
                    group = group,
                    latencyMs = latency,
                    vmess = VmessOptions(
                        uuid = uuid,
                        alterId = source.int("alterId", "alter_id", "aid") ?: 0,
                        security = source.string("security", "cipher", "encryption")
                            ?.lowercase()
                            ?.takeIf { it.isNotBlank() && it !in setOf("tls", "reality", "none") }
                            ?: "auto",
                        tls = tls,
                        transport = transport,
                    ),
                )
            }

            Protocol.VLESS -> {
                if (uuid.isNullOrBlank()) return null
                ServerEntry(
                    id = stableId(idSeed),
                    name = name,
                    protocol = protocol,
                    address = host,
                    port = port,
                    countryCode = countryCode,
                    group = group,
                    latencyMs = latency,
                    vless = VlessOptions(
                        uuid = uuid,
                        flow = source.string("flow"),
                        tls = tls,
                        transport = transport,
                    ),
                )
            }

            Protocol.WIREGUARD -> {
                val wireGuardObject = source.obj("wireguard") ?: source
                val privateKey = wireGuardObject.string(
                    "privateKey",
                    "private_key",
                    "secretKey",
                )?.trim()
                val peerPublicKey = wireGuardObject.string(
                    "peerPublicKey",
                    "peer_public_key",
                    "publicKey",
                    "public_key",
                )?.trim()
                if (privateKey.isNullOrBlank() || peerPublicKey.isNullOrBlank()) return null

                ServerEntry(
                    id = stableId(idSeed),
                    name = name,
                    protocol = protocol,
                    address = host,
                    port = port,
                    countryCode = countryCode,
                    group = group,
                    latencyMs = latency,
                    wireGuard = WireGuardOptions(
                        privateKey = privateKey,
                        peerPublicKey = peerPublicKey,
                        presharedKey = wireGuardObject.string(
                            "presharedKey",
                            "preshared_key",
                            "psk",
                        ),
                        localAddresses = wireGuardObject.stringList(
                            "localAddress",
                            "local_address",
                            "address",
                            "addresses",
                        ).ifEmpty { listOf("172.16.0.2/32") },
                        mtu = wireGuardObject.int("mtu") ?: 1420,
                        reserved = wireGuardObject.intList("reserved"),
                        dns = wireGuardObject.stringList("dns"),
                    ),
                )
            }
        }
    }

    private fun inferProtocol(source: JsonObject, uuid: String?): Protocol? = when {
        source.containsKey("peerPublicKey")
            || source.containsKey("privateKey")
            || source.containsKey("private_key")
            || source.containsKey("publicKey") && source.containsKey("reserved") -> Protocol.WIREGUARD

        !uuid.isNullOrBlank() && (
            source.containsKey("flow") || source.containsKey("vless")
            ) -> Protocol.VLESS

        !uuid.isNullOrBlank() && (
            source.containsKey("alterId") || source.containsKey("aid") || source.containsKey("security")
            ) -> Protocol.VMESS

        !uuid.isNullOrBlank() -> Protocol.VMESS
        else -> null
    }

    private fun protocolFromText(value: String): Protocol? = when {
        value.contains("vmess") -> Protocol.VMESS
        value.contains("vless") -> Protocol.VLESS
        value.contains("wireguard") || value == "wg" -> Protocol.WIREGUARD
        else -> null
    }

    private fun transportType(value: String?): TransportType = when (value) {
        "ws", "websocket" -> TransportType.WS
        "grpc", "gun" -> TransportType.GRPC
        "http" -> TransportType.HTTP
        "httpupgrade" -> TransportType.HTTPUPGRADE
        "h2", "http2" -> TransportType.H2
        else -> TransportType.TCP
    }

    private val transportNames = setOf("tcp", "ws", "websocket", "grpc", "gun", "http", "h2", "http2")

    private fun stableId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                append("%02x".format(byte.toInt() and 0xFF))
            }
        }.take(16)
    }
}

private fun JsonObject.string(vararg keys: String): String? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        if (primitive is JsonNull) continue
        val content = primitive.content
        if (content.isNotBlank() && !content.equals("null", ignoreCase = true)) {
            return content
        }
    }
    return null
}

private fun JsonObject.int(vararg keys: String): Int? {
    val raw = string(*keys) ?: return null
    return raw.toDoubleOrNull()?.toInt() ?: raw.trim().toIntOrNull()
}

private fun JsonObject.boolean(vararg keys: String): Boolean? {
    val raw = string(*keys)?.lowercase() ?: return null
    return when (raw) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> null
    }
}

private fun JsonObject.stringList(vararg keys: String): List<String> {
    for (key in keys) {
        when (val element = this[key]) {
            is JsonArray -> {
                val values = element.mapNotNull { item ->
                    (item as? JsonPrimitive)
                        ?.takeUnless { it is JsonNull }
                        ?.content
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
                if (values.isNotEmpty()) return values
            }

            is JsonPrimitive -> {
                if (element is JsonNull) continue
                val values = element.content
                    .split(',', ';', ' ')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (values.isNotEmpty()) return values
            }

            else -> Unit
        }
    }
    return emptyList()
}

private fun JsonObject.intList(vararg keys: String): List<Int> =
    stringList(*keys).mapNotNull { it.toIntOrNull() }

private fun JsonObject.obj(vararg path: String): JsonObject? {
    var current: JsonObject = this
    for (key in path) {
        current = current[key] as? JsonObject ?: return null
    }
    return current
}
