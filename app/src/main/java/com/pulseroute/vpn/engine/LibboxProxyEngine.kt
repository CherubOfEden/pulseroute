package com.pulseroute.vpn.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import android.util.Log
import go.Seq
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.io.File
import java.io.IOException
import java.net.Inet6Address
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class LibboxProxyEngine(context: Context) : ProxyEngine {

    private val appContext = context.applicationContext ?: context

    @Volatile
    private var commandServer: CommandServer? = null

    override suspend fun start(configJson: String, platform: VpnPlatform) {
        withContext(Dispatchers.IO) {
            check(commandServer == null) { "Engine is already running" }

            try {
                ensureLibboxSetup(appContext)
            } catch (error: Throwable) {
                Log.e("LibboxProxyEngine", "Failed to load libbox", error)
                throw EngineUnavailableException(
                    "libbox native library is unavailable: ${error.message}",
                    error,
                )
            }

            Log.d("LibboxProxyEngine", "Starting libbox proxy engine")
            val handler = object : CommandServerHandler {
                override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus()

                override fun serviceReload() = Unit

                override fun serviceStop() = Unit

                override fun setSystemProxyEnabled(enabled: Boolean) = Unit

                override fun writeDebugMessage(message: String) = Log.d("LibboxProxyEngine", message)
            }
            val platformInterface = AndroidPlatformInterface(appContext, platform)

            val server = try {
                Libbox.newCommandServer(handler, platformInterface)
            } catch (error: Throwable) {
                throw EngineUnavailableException(
                    "libbox 1.13.11 could not create the command server: ${error.message}",
                    error,
                )
            } ?: throw EngineUnavailableException("libbox returned a null command server")

            try {
                server.startOrReloadService(
                    configJson,
                    OverrideOptions().apply { autoRedirect = false },
                )
            } catch (error: Throwable) {
                runCatching { server.closeService() }
                runCatching { server.close() }
                throw EngineUnavailableException(
                    "libbox failed to start: ${error.message}",
                    error,
                )
            }

            commandServer = server
        }
    }

    override fun stop() {
        val current = commandServer ?: return
        commandServer = null
        runCatching { current.closeService() }
        runCatching { current.close() }
    }

    private class AndroidPlatformInterface(
        private val context: Context,
        private val platform: VpnPlatform,
    ) : PlatformInterface {

        private val connectivityManager =
            context.getSystemService(ConnectivityManager::class.java)

        private var interfaceListener: InterfaceUpdateListener? = null
        private var networkCallback: ConnectivityManager.NetworkCallback? = null

        override fun autoDetectInterfaceControl(fd: Int) {
            if (!platform.protectSocket(fd)) {
                throw IOException("Could not protect socket $fd")
            }
        }

        override fun clearDNSCache() = Unit

        @Synchronized
        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            interfaceListener = null
            val callback = networkCallback
            networkCallback = null
            if (callback != null) {
                runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            }
        }

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String,
            sourcePort: Int,
            destinationAddress: String,
            destinationPort: Int,
        ): ConnectionOwner {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw UnsupportedOperationException("Connection owners require Android 10")
            }

            val manager = connectivityManager
                ?: throw IOException("Connectivity service is unavailable")
            val uid = manager.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort),
            )
            if (uid == Process.INVALID_UID) {
                throw IOException("Connection owner was not found")
            }

            val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
            return ConnectionOwner().apply {
                userId = uid
                userName = packages.firstOrNull().orEmpty()
                setAndroidPackageNames(StringArray(packages.iterator()))
            }
        }

        override fun getInterfaces(): NetworkInterfaceIterator {
            val interfaces = mutableListOf<LibboxNetworkInterface>()
            val enumeration = runCatching {
                java.net.NetworkInterface.getNetworkInterfaces()
            }.getOrNull()

            while (enumeration != null && enumeration.hasMoreElements()) {
                val networkInterface = enumeration.nextElement()
                if (!networkInterface.isUp) continue

                val item = LibboxNetworkInterface().apply {
                    name = networkInterface.name
                    index = networkInterface.safeIndex()
                    mtu = runCatching { networkInterface.mtu }.getOrDefault(1500)
                    flags = networkInterface.linkFlags()
                    type = networkInterface.interfaceType()
                    addresses = StringArray(
                        networkInterface.interfaceAddresses
                            .mapNotNull { it.toPrefix() }
                            .iterator(),
                    )
                    dnsServer = StringArray(emptyList<String>().iterator())
                    metered = false
                }
                interfaces += item
            }

            return NetworkInterfaceArray(interfaces.iterator())
        }

        override fun includeAllNetworks(): Boolean = false

        override fun localDNSTransport(): LocalDNSTransport? = null

        override fun openTun(options: TunOptions): Int = platform.establishTun(options).fd

        override fun readWIFIState(): WIFIState? = null

        override fun sendNotification(notification: Notification) = Unit

        @Synchronized
        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            interfaceListener = listener
            val manager = connectivityManager ?: return

            networkCallback?.let { previous ->
                runCatching { manager.unregisterNetworkCallback(previous) }
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    notifyDefaultInterface(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    notifyDefaultInterface(network)
                }
            }
            networkCallback = callback
            runCatching { manager.registerDefaultNetworkCallback(callback) }
            runCatching { manager.activeNetwork?.let(::notifyDefaultInterface) }
        }

        override fun systemCertificates(): StringIterator =
            StringArray(emptyList<String>().iterator())

        override fun underNetworkExtension(): Boolean = false

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun useProcFS(): Boolean = false

        @Synchronized
        private fun notifyDefaultInterface(network: Network) {
            val listener = interfaceListener ?: return
            val manager = connectivityManager ?: return
            val linkProperties = runCatching {
                manager.getLinkProperties(network)
            }.getOrNull() ?: return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val name = linkProperties.interfaceName ?: return
            val capabilities = runCatching {
                manager.getNetworkCapabilities(network)
            }.getOrNull()
            val expensive = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            ) == false
            val index = java.net.NetworkInterface.getByName(name)?.safeIndex() ?: 0

            runCatching {
                listener.updateDefaultInterface(name, index, expensive, false)
            }
        }

        private fun java.net.NetworkInterface.safeIndex(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { index }.getOrDefault(0)
            } else {
                0
            }

        private fun java.net.NetworkInterface.linkFlags(): Int {
            var flags = 0
            if (isUp) flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            if (isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (runCatching { supportsMulticast() }.getOrDefault(false)) {
                flags = flags or OsConstants.IFF_MULTICAST
            }
            return flags
        }

        private fun java.net.NetworkInterface.interfaceType(): Int = when {
            name.startsWith("wlan") || name.startsWith("wifi") -> Libbox.InterfaceTypeWIFI
            name.startsWith("rmnet") ||
                name.startsWith("ccmni") ||
                name.startsWith("pdp") ||
                name.startsWith("wwan") -> Libbox.InterfaceTypeCellular

            name.startsWith("eth") -> Libbox.InterfaceTypeEthernet
            else -> Libbox.InterfaceTypeOther
        }

        private fun java.net.InterfaceAddress.toPrefix(): String? {
            val host = when (val value = address) {
                is Inet6Address -> runCatching {
                    Inet6Address.getByAddress(value.address).hostAddress
                }.getOrNull()

                else -> value.hostAddress
            } ?: return null
            return "$host/$networkPrefixLength"
        }
    }

    private class StringArray(
        private val iterator: Iterator<String>,
    ) : StringIterator {
        override fun len(): Int = 0

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): String = iterator.next()
    }

    private class NetworkInterfaceArray(
        private val iterator: Iterator<LibboxNetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    private companion object {
        @Volatile
        private var setupCompleted = false

        private val setupLock = Any()

        fun ensureLibboxSetup(context: Context) {
            if (setupCompleted) return
            synchronized(setupLock) {
                if (setupCompleted) return

                val baseDir = File(context.filesDir, "libbox").apply { mkdirs() }
                val workingDir = File(context.filesDir, "libbox-work").apply { mkdirs() }
                val tempDir = File(context.cacheDir, "libbox").apply { mkdirs() }

                Seq.setContext(context)
                Libbox.setup(
                    SetupOptions().apply {
                        basePath = baseDir.absolutePath
                        workingPath = workingDir.absolutePath
                        tempPath = tempDir.absolutePath
                        fixAndroidStack = true
                        commandServerListenPort = 0
                        commandServerSecret = ""
                        logMaxLines = 0L
                        debug = false
                    },
                )
                setupCompleted = true
            }
        }
    }
}
