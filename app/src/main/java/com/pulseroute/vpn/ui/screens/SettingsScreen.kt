package com.pulseroute.vpn.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulseroute.vpn.R
import com.pulseroute.vpn.core.AppContainer
import com.pulseroute.vpn.data.settings.UserSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenRouting: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())

    var remoteUrl by rememberSaveable(settings.remoteServersUrl) {
        mutableStateOf(settings.remoteServersUrl)
    }
    var syncing by remember { mutableStateOf(false) }

    val engineReady = remember {
        runCatching { Class.forName("io.nekohasekai.libbox.Libbox") }.isSuccess
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.remote_list_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.remote_list_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.remote_url)) },
                    placeholder = { Text(stringResource(R.string.remote_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                container.settingsRepository.setRemoteServersUrl(remoteUrl)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.saved),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    OutlinedButton(
                        onClick = {
                            val url = remoteUrl.trim()
                            if (url.isBlank()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.remote_url_required),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                scope.launch {
                                    syncing = true
                                    container.settingsRepository.setRemoteServersUrl(url)
                                    val result = container.serverRepository.refresh(url)
                                    syncing = false
                                    Toast.makeText(
                                        context,
                                        result.fold(
                                            onSuccess = {
                                                context.getString(R.string.fetch_success, it)
                                            },
                                            onFailure = {
                                                context.getString(
                                                    R.string.fetch_failed,
                                                    it.message ?: "unknown",
                                                )
                                            },
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.fetch_remote))
                    }
                }
                if (syncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.engine_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (engineReady) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.WarningAmber
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (engineReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (engineReady) R.string.engine_ready else R.string.engine_missing,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenRouting) {
                    Text(stringResource(R.string.nav_routing))
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.version_label, versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "com.pulseroute.vpn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
