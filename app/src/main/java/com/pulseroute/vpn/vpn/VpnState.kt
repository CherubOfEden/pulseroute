package com.pulseroute.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnStatus {
    data object Disconnected : VpnStatus
    data object Connecting : VpnStatus
    data object Disconnecting : VpnStatus
    data class Connected(
        val serverId: String,
        val serverName: String,
        val startedAtMillis: Long,
    ) : VpnStatus

    data class Error(val message: String) : VpnStatus
}

data class TrafficSnapshot(
    val uploadBps: Long = 0L,
    val downloadBps: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
)

data class VpnUiState(
    val status: VpnStatus = VpnStatus.Disconnected,
    val traffic: TrafficSnapshot = TrafficSnapshot(),
)

object VpnStateHolder {
    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    private val _traffic = MutableStateFlow(TrafficSnapshot())

    val status: StateFlow<VpnStatus> = _status.asStateFlow()
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    fun updateStatus(status: VpnStatus) {
        _status.value = status
    }

    fun updateTraffic(traffic: TrafficSnapshot) {
        _traffic.value = traffic
    }

    fun reset() {
        _status.value = VpnStatus.Disconnected
        _traffic.value = TrafficSnapshot()
    }
}
