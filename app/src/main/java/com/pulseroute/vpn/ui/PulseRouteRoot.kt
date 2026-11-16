package com.pulseroute.vpn.ui

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulseroute.vpn.PulseRouteApp
import com.pulseroute.vpn.R
import com.pulseroute.vpn.ui.screens.DashboardScreen
import com.pulseroute.vpn.ui.screens.RoutingScreen
import com.pulseroute.vpn.ui.screens.ServersScreen
import com.pulseroute.vpn.ui.screens.SettingsScreen
import com.pulseroute.vpn.vpn.VpnController

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem("dashboard", R.string.nav_dashboard, Icons.Outlined.Dashboard),
    NavItem("servers", R.string.nav_servers, Icons.Outlined.Dns),
    NavItem("routing", R.string.nav_routing, Icons.Outlined.Route),
    NavItem("settings", R.string.nav_settings, Icons.Outlined.Settings),
)

@Composable
fun PulseRouteRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as PulseRouteApp).container
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var pendingServerId by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val serverId = pendingServerId
        pendingServerId = null
        if (serverId != null) {
            val granted = result.resultCode == Activity.RESULT_OK ||
                VpnService.prepare(context) == null
            if (granted) {
                VpnController.start(context, serverId)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.vpn_permission_denied),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val connect: (String) -> Unit = { serverId ->
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingServerId = serverId
            permissionLauncher.launch(prepareIntent)
        } else {
            VpnController.start(context, serverId)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { navigateTo(item.route) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("dashboard") {
                DashboardScreen(
                    container = container,
                    onConnect = connect,
                    onDisconnect = { VpnController.stop(context) },
                    onManageServers = { navigateTo("servers") },
                )
            }
            composable("servers") {
                ServersScreen(container = container)
            }
            composable("routing") {
                RoutingScreen(container = container)
            }
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onOpenRouting = { navigateTo("routing") },
                )
            }
        }
    }
}
