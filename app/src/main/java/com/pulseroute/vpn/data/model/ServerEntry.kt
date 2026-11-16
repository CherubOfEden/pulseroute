package com.pulseroute.vpn.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Protocol {
    @SerialName("vmess")
    VMESS,

    @SerialName("vless")
    VLESS,

    @SerialName("wireguard")
    WIREGUARD,
}

@Serializable
enum class TransportType {
    @SerialName("tcp")
    TCP,

    @SerialName("ws")
    WS,

    @SerialName("grpc")
    GRPC,

    @SerialName("http")
    HTTP,

    @SerialName("httpupgrade")
    HTTPUPGRADE,

    @SerialName("h2")
    H2,
}

@Serializable
data class TlsOptions(
    val enabled: Boolean = true,
    val serverName: String? = null,
    val allowInsecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val fingerprint: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
)

@Serializable
data class TransportOptions(
    val type: TransportType = TransportType.TCP,
    val path: String? = null,
    val host: String? = null,
    val serviceName: String? = null,
)

@Serializable
data class VmessOptions(
    val uuid: String,
    val alterId: Int = 0,
    val security: String = "auto",
    val tls: TlsOptions? = null,
    val transport: TransportOptions = TransportOptions(),
)

@Serializable
data class VlessOptions(
    val uuid: String,
    val flow: String? = null,
    val tls: TlsOptions? = null,
    val transport: TransportOptions = TransportOptions(),
)

@Serializable
data class WireGuardOptions(
    val privateKey: String,
    val peerPublicKey: String,
    val presharedKey: String? = null,
    val localAddresses: List<String> = listOf("172.16.0.2/32"),
    val mtu: Int = 1420,
    val reserved: List<Int> = emptyList(),
    val dns: List<String> = emptyList(),
)

@Serializable
data class ServerEntry(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val countryCode: String? = null,
    val group: String? = null,
    val latencyMs: Int? = null,
    val vmess: VmessOptions? = null,
    val vless: VlessOptions? = null,
    val wireGuard: WireGuardOptions? = null,
) {
    val endpoint: String
        get() = "$address:$port"

    fun hasRequiredCredentials(): Boolean = when (protocol) {
        Protocol.VMESS -> !vmess?.uuid.isNullOrBlank()
        Protocol.VLESS -> !vless?.uuid.isNullOrBlank()
        Protocol.WIREGUARD -> {
            val wireGuard = wireGuard
            wireGuard != null &&
                wireGuard.privateKey.isNotBlank() &&
                wireGuard.peerPublicKey.isNotBlank()
        }
    }
}

@Serializable
data class ServerCache(
    val schemaVersion: Int = 1,
    val updatedAtEpochMs: Long = 0L,
    val servers: List<ServerEntry> = emptyList(),
)
