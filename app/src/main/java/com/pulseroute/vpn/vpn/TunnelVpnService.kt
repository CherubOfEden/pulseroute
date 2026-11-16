package com.pulseroute.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import com.pulseroute.vpn.MainActivity
import com.pulseroute.vpn.PulseRouteApp
import com.pulseroute.vpn.R
import com.pulseroute.vpn.config.SingBoxConfigBuilder
import com.pulseroute.vpn.engine.LibboxProxyEngine
import com.pulseroute.vpn.engine.ProxyEngine
import com.pulseroute.vpn.engine.VpnPlatform
import com.pulseroute.vpn.routing.GeoAssetInstaller
import com.pulseroute.vpn.routing.RoutingOptions
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunnelVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prepareJob: Job? = null
    private var trafficJob: Job? = null
    private var engine: ProxyEngine? = null
    private var tunInterface: ParcelFileDescriptor? = null

    private val container by lazy { (application as PulseRouteApp).container }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            VpnController.ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }

            VpnController.ACTION_START -> {
                val serverId = intent.getStringExtra(VpnController.EXTRA_SERVER_ID)
                if (serverId.isNullOrBlank()) {
                    fail("No server was selected")
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, notification("Connecting…", connecting = true))
                prepareJob?.cancel()
                prepareJob = serviceScope.launch { connect(serverId) }
                return START_NOT_STICKY
            }

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopTunnel("VPN permission was revoked")
        super.onRevoke()
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        prepareJob = null
        trafficJob?.cancel()
        trafficJob = null
        engine?.stop()
        engine = null
        closeTun()
        serviceScope.cancel()
        if (VpnStateHolder.status.value !is VpnStatus.Error) {
            VpnStateHolder.updateStatus(VpnStatus.Disconnected)
        }
        super.onDestroy()
    }

    private suspend fun connect(serverId: String) {
        try {
            VpnStateHolder.updateStatus(VpnStatus.Connecting)
            engine?.stop()
            engine = null
            closeTun()

            if (VpnService.prepare(this) != null) {
                fail("VPN permission was not granted")
                return
            }

            val settings = container.settingsRepository.snapshot()
            val server = container.serverRepository.findServer(serverId)
                ?: error("Selected server is no longer available")
            val geoAssets = GeoAssetInstaller(this).ensureInstalled()
            val routing = RoutingOptions(
                bypassLan = settings.bypassLan,
                bypassCountry = settings.bypassCountry,
                countryCode = settings.countryCode,
                blockAds = settings.blockAds,
            )
            val configJson = SingBoxConfigBuilder.build(
                server = server,
                routing = routing,
                geoAssets = geoAssets,
                mtu = settings.mtu,
            )

            val mtu = settings.mtu
            val vpnEngine = LibboxProxyEngine()
            engine = vpnEngine

            vpnEngine.start(
                configJson = configJson,
                platform = object : VpnPlatform {
                    override fun establishTun(): ParcelFileDescriptor = establishTunInterface(mtu)

                    override fun protectSocket(fd: Int): Boolean = protect(fd)
                },
            )

            VpnStateHolder.updateStatus(
                VpnStatus.Connected(
                    serverId = server.id,
                    serverName = server.name,
                    startedAtMillis = System.currentTimeMillis(),
                ),
            )
            startTrafficMonitor()
            updateNotification("Connected · ${server.name}")
        } catch (error: CancellationException) {
            engine?.stop()
            engine = null
            closeTun()
            throw error
        } catch (error: Exception) {
            fail(error.message ?: "Could not start the tunnel")
        }
    }


    private fun startTrafficMonitor() {
        trafficJob?.cancel()
        VpnStateHolder.updateTraffic(TrafficSnapshot())

        trafficJob = serviceScope.launch {
            var lastTx = uidTxBytes()
            var lastRx = uidRxBytes()
            var totalTx = 0L
            var totalRx = 0L

            while (isActive) {
                delay(1000L)
                val currentTx = uidTxBytes()
                val currentRx = uidRxBytes()
                val deltaTx = (currentTx - lastTx).coerceAtLeast(0L)
                val deltaRx = (currentRx - lastRx).coerceAtLeast(0L)
                lastTx = currentTx
                lastRx = currentRx
                totalTx += deltaTx
                totalRx += deltaRx

                VpnStateHolder.updateTraffic(
                    TrafficSnapshot(
                        uploadBps = deltaTx,
                        downloadBps = deltaRx,
                        totalUploadBytes = totalTx,
                        totalDownloadBytes = totalRx,
                    ),
                )
            }
        }
    }

    private fun uidTxBytes(): Long =
        TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0L)

    private fun uidRxBytes(): Long =
        TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0L)

    private fun establishTunInterface(mtu: Int): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("PulseRoute")
            .setMtu(mtu.coerceIn(1280, 9000))
            .addAddress("172.19.0.1", 30)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .setBlocking(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            // Should not happen, but the tunnel is still usable.
        }

        val descriptor = builder.establish()
            ?: throw IOException("Android refused to create the TUN interface")

        synchronized(this) {
            tunInterface?.close()
            tunInterface = descriptor
        }
        return descriptor
    }

    private fun fail(message: String) {
        VpnStateHolder.updateStatus(VpnStatus.Error(message))
        stopTunnel()
    }

    private fun stopTunnel() {
        prepareJob?.cancel()
        prepareJob = null
        trafficJob?.cancel()
        trafficJob = null
        engine?.stop()
        engine = null
        closeTun()
        VpnStateHolder.updateTraffic(TrafficSnapshot())
        if (VpnStateHolder.status.value !is VpnStatus.Error) {
            VpnStateHolder.updateStatus(VpnStatus.Disconnected)
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // The service may not have reached foreground state.
        }
        stopSelf()
    }

    private fun closeTun() {
        synchronized(this) {
            try {
                tunInterface?.close()
            } catch (_: IOException) {
                // Ignore descriptor shutdown errors.
            }
            tunInterface = null
        }
    }

    private fun notification(text: String, connecting: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelVpnService::class.java).setAction(VpnController.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PulseRoute")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setProgress(0, 0, connecting)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the state of the PulseRoute tunnel"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "pulse_route_vpn"
        const val NOTIFICATION_ID = 1001
    }
}
