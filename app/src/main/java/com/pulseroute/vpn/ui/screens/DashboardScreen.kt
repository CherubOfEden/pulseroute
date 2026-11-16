package com.pulseroute.vpn.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulseroute.vpn.R
import com.pulseroute.vpn.core.AppContainer
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.settings.UserSettings
import com.pulseroute.vpn.ui.util.formatDuration
import com.pulseroute.vpn.ui.util.formatSpeed
import com.pulseroute.vpn.ui.util.protocolLabel
import com.pulseroute.vpn.vpn.TrafficSnapshot
import com.pulseroute.vpn.vpn.VpnStateHolder
import com.pulseroute.vpn.vpn.VpnStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    container: AppContainer,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onManageServers: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val servers by container.serverRepository.servers.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())
    val status by VpnStateHolder.status.collectAsStateWithLifecycle()
    val traffic by VpnStateHolder.traffic.collectAsStateWithLifecycle()

    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val selected = remember(servers, settings.selectedServerId) {
        servers.firstOrNull { it.id == settings.selectedServerId }
    }

    LaunchedEffect(status) {
        if (status is VpnStatus.Connected) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val refreshingCall: () -> Unit = {
        val url = settings.remoteServersUrl
        if (url.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.remote_url_required),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            scope.launch {
                refreshing = true
                val result = container.serverRepository.refresh(url)
                refreshing = false
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { context.getString(R.string.fetch_success, it) },
                        onFailure = {
                            context.getString(
                                R.string.fetch_failed,
                                it.message ?: "unknown error",
                            )
                        },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ConnectionHero(
            status = status,
            selected = selected,
            onConnect = { selected?.let { onConnect(it.id) } },
            onDisconnect = onDisconnect,
            onManageServers = onManageServers,
        )

        val currentStatus = status

        TrafficCard(
            status = currentStatus,
            traffic = traffic,
            elapsedMillis = if (currentStatus is VpnStatus.Connected) {
                (now - currentStatus.startedAtMillis).coerceAtLeast(0L)
            } else {
                0L
            },
        )

        ActiveServerCard(
            server = selected,
            onManageServers = onManageServers,
        )

        Text(
            text = stringResource(R.string.quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onManageServers,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.manage_servers))
            }
            OutlinedButton(
                onClick = refreshingCall,
                enabled = !refreshing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refresh_list))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConnectionHero(
    status: VpnStatus,
    selected: ServerEntry?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onManageServers: () -> Unit,
) {
    val connected = status is VpnStatus.Connected
    val busy = status is VpnStatus.Connecting || status is VpnStatus.Disconnecting
    val statusColor = when (status) {
        is VpnStatus.Connected -> Color(0xFF6EE7B7)
        is VpnStatus.Connecting, is VpnStatus.Disconnecting -> Color(0xFFFFD48A)
        is VpnStatus.Error -> MaterialTheme.colorScheme.error
        VpnStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val title = when (status) {
        is VpnStatus.Connected -> stringResource(R.string.status_connected)
        is VpnStatus.Connecting -> stringResource(R.string.status_connecting)
        is VpnStatus.Disconnecting -> stringResource(R.string.status_disconnecting)
        is VpnStatus.Error -> stringResource(R.string.status_error)
        VpnStatus.Disconnected -> stringResource(R.string.status_disconnected)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(10.dp),
                    ) {}
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = when (status) {
                    is VpnStatus.Connected -> status.serverName
                    is VpnStatus.Error -> status.message
                    else -> selected?.name ?: stringResource(R.string.no_server_selected)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = when (status) {
                    is VpnStatus.Connected -> stringResource(
                        R.string.connected_via,
                        status.serverName,
                    )
                    is VpnStatus.Connecting -> if (selected != null) {
                        stringResource(R.string.connecting_via, selected.name)
                    } else {
                        stringResource(R.string.status_connecting)
                    }
                    is VpnStatus.Error -> stringResource(R.string.no_server_selected_desc)
                    else -> selected?.let { "${it.address}:${it.port}" }
                        ?: stringResource(R.string.no_server_selected_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selected == null) {
                Button(
                    onClick = onManageServers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(stringResource(R.string.choose_server))
                }
            } else {
                Button(
                    onClick = if (connected) onDisconnect else onConnect,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (connected) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (connected) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = when {
                            connected -> stringResource(R.string.disconnect)
                            busy -> title
                            else -> stringResource(R.string.connect)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficCard(
    status: VpnStatus,
    traffic: TrafficSnapshot,
    elapsedMillis: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.session),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(
                    icon = Icons.Outlined.ArrowDownward,
                    label = stringResource(R.string.download),
                    value = formatSpeed(traffic.downloadBps),
                )
                Metric(
                    icon = Icons.Outlined.ArrowUpward,
                    label = stringResource(R.string.upload),
                    value = formatSpeed(traffic.uploadBps),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(elapsedMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (status !is VpnStatus.Connected) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.status_disconnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Metric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActiveServerCard(
    server: ServerEntry?,
    onManageServers: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.active_server),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (server == null) {
                Text(
                    text = stringResource(R.string.no_server_selected_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onManageServers) {
                    Text(stringResource(R.string.choose_server))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = protocolLabel(server.protocol),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${server.address}:${server.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onManageServers) {
                        Text(stringResource(R.string.servers_title))
                    }
                }
            }
        }
    }
}
