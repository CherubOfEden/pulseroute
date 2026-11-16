package com.pulseroute.vpn.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val networkOptions by container.serverRepository.networkOptions.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())
    val status by VpnStateHolder.status.collectAsStateWithLifecycle()
    val traffic by VpnStateHolder.traffic.collectAsStateWithLifecycle()

    var showServerPicker by rememberSaveable { mutableStateOf(false) }
    var showNetworkPicker by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val selectedNetwork = settings.selectedNetwork
    val filteredServers = remember(servers, selectedNetwork) {
        if (selectedNetwork == null) {
            servers
        } else {
            servers.filter { it.network.equals(selectedNetwork, ignoreCase = true) }
        }
    }
    val selected = remember(filteredServers, settings.selectedServerId) {
        filteredServers.firstOrNull { it.id == settings.selectedServerId }
    }
    val availableNetworks = remember(networkOptions, selectedNetwork) {
        if (selectedNetwork != null &&
            networkOptions.none { it.equals(selectedNetwork, ignoreCase = true) }
        ) {
            listOf(selectedNetwork) + networkOptions
        } else {
            networkOptions
        }
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

        TunnelRouteCard(
            status = status,
            selected = selected,
            selectedNetwork = selectedNetwork,
            serverCount = filteredServers.size,
            onChooseServer = { showServerPicker = true },
            onChooseNetwork = { showNetworkPicker = true },
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

    if (showServerPicker) {
        ServerPickerDialog(
            servers = filteredServers,
            selectedId = selected?.id,
            selectedNetwork = selectedNetwork,
            onSelect = { server ->
                showServerPicker = false
                scope.launch { container.settingsRepository.setSelectedServerId(server.id) }
            },
            onManageServers = {
                showServerPicker = false
                onManageServers()
            },
            onDismiss = { showServerPicker = false },
        )
    }

    if (showNetworkPicker) {
        NetworkPickerDialog(
            networks = availableNetworks,
            selectedNetwork = selectedNetwork,
            onSelect = { network ->
                showNetworkPicker = false
                scope.launch { container.settingsRepository.setSelectedNetwork(network) }
            },
            onDismiss = { showNetworkPicker = false },
        )
    }
}

@Composable
private fun TunnelRouteCard(
    status: VpnStatus,
    selected: ServerEntry?,
    selectedNetwork: String?,
    serverCount: Int,
    onChooseServer: () -> Unit,
    onChooseNetwork: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onManageServers: () -> Unit,
) {
    val connected = status is VpnStatus.Connected
    val busy = status is VpnStatus.Connecting || status is VpnStatus.Disconnecting
    val editable = !connected && !busy
    val networkName = selectedNetwork ?: stringResource(R.string.all_networks)
    val statusColor = when (status) {
        is VpnStatus.Connected -> Color(0xFF6EE7B7)
        is VpnStatus.Connecting, is VpnStatus.Disconnecting -> Color(0xFFFFD48A)
        is VpnStatus.Error -> MaterialTheme.colorScheme.error
        VpnStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusTitle = when (status) {
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(10.dp),
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (status is VpnStatus.Error) {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            RouteSelector(
                label = stringResource(R.string.target_server),
                value = selected?.name ?: if (serverCount == 0) {
                    stringResource(R.string.no_servers_for_network)
                } else {
                    stringResource(R.string.choose_target_server)
                },
                detail = selected?.routeDetail() ?: stringResource(R.string.target_server_desc),
                icon = Icons.Outlined.Public,
                enabled = editable,
                onClick = onChooseServer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
                Text(
                    text = stringResource(R.string.route_via),
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            }

            RouteSelector(
                label = stringResource(R.string.network_provider),
                value = networkName,
                detail = stringResource(R.string.network_summary, networkName, serverCount),
                icon = Icons.Outlined.SimCard,
                enabled = editable,
                onClick = onChooseNetwork,
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
                            busy -> statusTitle
                            else -> stringResource(R.string.connect)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSelector(
    label: String,
    value: String,
    detail: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.65f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerPickerDialog(
    servers: List<ServerEntry>,
    selectedId: String?,
    selectedNetwork: String?,
    onSelect: (ServerEntry) -> Unit,
    onManageServers: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.target_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = selectedNetwork ?: stringResource(R.string.all_networks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_servers_for_network),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onManageServers) {
                        Text(stringResource(R.string.manage_servers))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        items(servers, key = { it.id }) { server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(server) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = server.id == selectedId,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = server.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = server.routeDetail(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun NetworkPickerDialog(
    networks: List<String>,
    selectedNetwork: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(networks) {
        buildList<String?> {
            add(null)
            addAll(networks)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_provider)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(choices, key = { it ?: "__all_networks__" }) { network ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(network) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = if (network == null) {
                                selectedNetwork == null
                            } else {
                                network.equals(selectedNetwork, ignoreCase = true)
                            },
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = network ?: stringResource(R.string.all_networks),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (network == null) {
                                Text(
                                    text = stringResource(R.string.all_networks_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

private fun ServerEntry.routeDetail(): String = buildList {
    add(endpoint)
    networkLabel?.let(::add)
    locationLabel?.let(::add)
}.distinct().joinToString(" · ")

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
    icon: ImageVector,
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
