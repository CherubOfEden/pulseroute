package com.pulseroute.vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulseroute.vpn.R
import com.pulseroute.vpn.core.AppContainer
import com.pulseroute.vpn.data.settings.UserSettings
import kotlinx.coroutines.launch

@Composable
fun RoutingScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())

    var country by rememberSaveable(settings.countryCode) {
        mutableStateOf(settings.countryCode)
    }
    var mtuText by rememberSaveable(settings.mtu) {
        mutableStateOf(settings.mtu.toString())
    }

    val normalizedCountry = country.trim().uppercase()
    val countryValid = normalizedCountry.length == 2 && normalizedCountry.all { it.isLetter() }
    val mtuValue = mtuText.toIntOrNull()
    val mtuValid = mtuValue != null && mtuValue in 1280..9000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.routing_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.routing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SwitchRow(
                title = stringResource(R.string.bypass_lan),
                description = stringResource(R.string.bypass_lan_desc),
                checked = settings.bypassLan,
                onCheckedChange = {
                    scope.launch { container.settingsRepository.setBypassLan(it) }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SwitchRow(
                title = stringResource(R.string.bypass_country),
                description = stringResource(R.string.bypass_country_desc),
                checked = settings.bypassCountry,
                onCheckedChange = {
                    scope.launch { container.settingsRepository.setBypassCountry(it) }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { value ->
                        country = value.uppercase().filter { it.isLetter() }.take(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.country)) },
                    supportingText = { Text(stringResource(R.string.country_hint)) },
                    isError = country.isNotBlank() && !countryValid,
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            container.settingsRepository.setCountryCode(normalizedCountry)
                            country = normalizedCountry
                        }
                    },
                    enabled = countryValid && normalizedCountry != settings.countryCode,
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }

        SectionCard {
            SwitchRow(
                title = stringResource(R.string.block_ads),
                description = stringResource(R.string.block_ads_desc),
                checked = settings.blockAds,
                onCheckedChange = {
                    scope.launch { container.settingsRepository.setBlockAds(it) }
                },
            )
        }

        SectionCard {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.mtu),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.mtu_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = mtuText,
                    onValueChange = { mtuText = it.filter { char -> char.isDigit() }.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = mtuText.isNotBlank() && !mtuValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        mtuValue?.let { value ->
                            scope.launch {
                                container.settingsRepository.setMtu(value)
                                mtuText = value.toString()
                            }
                        }
                    },
                    enabled = mtuValid && mtuValue != settings.mtu,
                ) {
                    Text(stringResource(R.string.save_mtu))
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        ) {
            Text(
                text = stringResource(R.string.geo_assets_info),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
