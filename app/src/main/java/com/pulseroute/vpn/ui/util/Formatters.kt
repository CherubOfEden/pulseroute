package com.pulseroute.vpn.ui.util

import com.pulseroute.vpn.data.model.Protocol
import java.util.Locale

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"

    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < UNITS.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, UNITS[unitIndex])
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatDuration(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

fun protocolLabel(protocol: Protocol): String = when (protocol) {
    Protocol.VMESS -> "VMess"
    Protocol.VLESS -> "VLESS"
    Protocol.WIREGUARD -> "WireGuard"
}
