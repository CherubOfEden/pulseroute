package com.pulseroute.vpn.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.pulseroute.vpn.ui.util.protocolLabel
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(container: AppContainer) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val servers by container.serverRepository.servers.collectAsStateWithLifecycle()
    val networkOptions by container.serverRepository.networkOptions.collectAsStateWithLifecycle()
    val locationOptions by container.serverRepository.locationOptions.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())

    var query by rememberSaveable { mutableStateOf("") }
    var locationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    val selectedNetwork = settings.selectedNetwork
    val availableNetworks = remember(networkOptions, selectedNetwork) {
        if (selectedNetwork != null &&
            networkOptions.none { it.equals(selectedNetwork, ignoreCase = true) }
        ) {
            listOf(selectedNetwork) + networkOptions
        } else {
            networkOptions
        }
    }
    val filtered = remember(servers, query, selectedNetwork, locationFilter) {
        servers.filter { server ->
            val matchesNetwork = selectedNetwork == null ||
                server.network.equals(selectedNetwork, ignoreCase = true)
            val matchesLocation = locationFilter == null ||
                server.locationLabel.equals(locationFilter, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                server.name.contains(query, ignoreCase = true) ||
                server.address.contains(query, ignoreCase = true) ||
                server.networkLabel?.contains(query, ignoreCase = true) == true ||
                server.locationLabel?.contains(query, ignoreCase = true) == true ||
                server.group?.contains(query, ignoreCase = true) == true
            matchesNetwork && matchesLocation && matchesQuery
        }
    }

    val refreshList: () -> Unit = {
        val url = settings.remoteServersUrl
        if (url.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.remote_url_required),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            scope.launch {
                syncing = true
                val result = container.serverRepository.refresh(url)
                syncing = false
                Toast.makeText(
                    context,
                    result.fold(
                        onSuccess = { context.getString(R.string.fetch_success, it) },
                        onFailure = {
                            context.getString(R.string.fetch_failed, it.message ?: "unknown")
                        },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.servers_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.servers_count, servers.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            placeholder = { Text(stringResource(R.string.search_servers)) },
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.height(10.dp))

        FilterRow(
            label = stringResource(R.string.network_filter),
            options = availableNetworks,
            selected = selectedNetwork,
            allLabel = stringResource(R.string.all_networks),
            onSelect = { network ->
                scope.launch { container.settingsRepository.setSelectedNetwork(network) }
            },
        )

        if (locationOptions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FilterRow(
                label = stringResource(R.string.location_filter),
                options = locationOptions,
                selected = locationFilter,
                allLabel = stringResource(R.string.all_locations),
                onSelect = { locationFilter = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_servers))
            }
            OutlinedButton(
                onClick = refreshList,
                enabled = !syncing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fetch_remote))
            }
        }

        if (syncing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))

        when {
            servers.isEmpty() -> {
                EmptyServerState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            filtered.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.no_matches),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.search_servers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { server ->
                        ServerRow(
                            server = server,
                            selected = server.id == settings.selectedServerId,
                            onSelect = {
                                scope.launch {
                                    container.settingsRepository.setSelectedServerId(server.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!importBusy) showImportDialog = false },
            title = { Text(stringResource(R.string.import_servers)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 12,
                        placeholder = { Text(stringResource(R.string.paste_json_hint)) },
                    )
                    TextButton(
                        onClick = {
                            clipboard.getText()?.text?.let { importText = it }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.paste))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importText.isNotBlank() && !importBusy,
                    onClick = {
                        scope.launch {
                            importBusy = true
                            val result = container.serverRepository.import(importText)
                            importBusy = false
                            result.onSuccess { imported ->
                                val currentId = settings.selectedServerId
                                if (currentId == null) {
                                    container.serverRepository.currentServers()
                                        .firstOrNull()
                                        ?.let {
                                            container.settingsRepository.setSelectedServerId(it.id)
                                        }
                                }
                                showImportDialog = false
                                importText = ""
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.imported_count, imported),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.import_failed,
                                        it.message ?: "unknown",
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(
                        text = if (importBusy) {
                            stringResource(R.string.importing)
                        } else {
                            stringResource(R.string.import_servers)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !importBusy,
                    onClick = { showImportDialog = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    allLabel: String,
    onSelect: (String?) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(allLabel) },
            )
            options.forEach { option ->
                FilterChip(
                    selected = option.equals(selected, ignoreCase = true),
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerEntry,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = protocolLabel(server.protocol),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${server.address}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildList {
                    val transport = server.vmess?.transport ?: server.vless?.transport
                    val tls = server.vmess?.tls ?: server.vless?.tls
                    transport?.type?.name?.let(::add)
                    if (tls?.enabled == true) add("TLS")
                    server.networkLabel?.let(::add)
                    server.locationLabel?.let(::add)
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyServerState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.server_list_empty),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.server_list_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
