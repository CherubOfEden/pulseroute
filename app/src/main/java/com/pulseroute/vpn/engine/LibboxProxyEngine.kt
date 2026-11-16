package com.pulseroute.vpn.engine

import android.os.ParcelFileDescriptor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin reflective adapter around sing-box's `libbox.aar`.
 *
 * The AAR is optional at build time. This keeps the repository compilable and
 * makes the engine version explicit at integration time. Drop a matching
 * libbox.aar into app/libs to enable the engine.
 */
class LibboxProxyEngine : ProxyEngine {

    @Volatile
    private var service: Any? = null

    override suspend fun start(configJson: String, platform: VpnPlatform) {
        withContext(Dispatchers.IO) {
            check(service == null) { "Engine is already running" }

            val libboxClass = loadClass(LIBBOX_CLASS)
            val platformInterface = loadClass(PLATFORM_CLASS)
            require(platformInterface.isInterface) { "$PLATFORM_CLASS is not an interface" }

            val platformProxy = Proxy.newProxyInstance(
                platformInterface.classLoader,
                arrayOf(platformInterface),
                PlatformInvocationHandler(platform),
            )

            val factory = libboxClass.methods.firstOrNull {
                (it.name == "newService" || it.name == "newCommandServer") &&
                    it.parameterCount == 2 &&
                    it.parameterTypes.any { type -> type == String::class.java } &&
                    it.parameterTypes.any { type -> type.isInstance(platformProxy) }
            } ?: throw EngineUnavailableException(
                "Unsupported libbox version: no newService/newCommandServer(String, PlatformInterface)",
            )

            val libboxInstance = runCatching {
                libboxClass.getField("INSTANCE").get(null)
            }.getOrNull()

            val arguments = if (factory.parameterTypes[0] == String::class.java) {
                arrayOf(configJson, platformProxy)
            } else {
                arrayOf(platformProxy, configJson)
            }

            val created = try {
                factory.invoke(libboxInstance, *arguments)
            } catch (error: InvocationTargetException) {
                throw EngineUnavailableException(
                    "libbox rejected the configuration: ${error.targetException.message}",
                    error.targetException,
                )
            } ?: throw EngineUnavailableException("libbox returned a null service")

            val startMethod = created.javaClass.methods.firstOrNull {
                it.name == "start" && it.parameterCount == 0
            } ?: created.javaClass.methods.firstOrNull {
                it.name == "start" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0].isInstance(platformProxy)
            } ?: throw EngineUnavailableException("libbox service has no supported start() method")

            try {
                if (startMethod.parameterCount == 0) {
                    startMethod.invoke(created)
                } else {
                    startMethod.invoke(created, platformProxy)
                }
            } catch (error: InvocationTargetException) {
                runCatching { closeService(created) }
                throw EngineUnavailableException(
                    "libbox failed to start: ${error.targetException.message}",
                    error.targetException,
                )
            }

            service = created
        }
    }

    override fun stop() {
        val current = service ?: return
        service = null
        closeService(current)
    }

    private fun closeService(target: Any) {
        val closeMethod = target.javaClass.methods.firstOrNull {
            (it.name == "close" || it.name == "stop") && it.parameterCount == 0
        } ?: return
        try {
            closeMethod.invoke(target)
        } catch (_: Exception) {
            // Best-effort shutdown; the TUN descriptor is closed by the service.
        }
    }

    private fun loadClass(name: String): Class<*> = try {
        Class.forName(name)
    } catch (error: ClassNotFoundException) {
        throw EngineUnavailableException(
            "$name is missing. Add a compatible libbox.aar to app/libs.",
            error,
        )
    }

    private class PlatformInvocationHandler(
        private val platform: VpnPlatform,
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "openTun" -> {
                    val descriptor = platform.establishTun()
                    if (method.returnType == Int::class.javaPrimitiveType) {
                        descriptor.fd
                    } else {
                        descriptor
                    }
                }

                "autoDetectInterfaceControl" -> {
                    val fd = (args?.firstOrNull() as? Number)?.toInt()
                    val protected = fd != null && platform.protectSocket(fd)
                    if (method.returnType == java.lang.Boolean.TYPE) protected else null
                }

                "usePlatformAutoDetectInterfaceControl",
                "useProcFS",
                -> true

                "underNetworkExtension",
                "includeAllNetworks",
                -> false

                "packageNameByUid" -> ""

                "uidByPackageName" -> -1

                "toString" -> "PulseRouteVpnPlatform"

                "hashCode" -> System.identityHashCode(proxy)

                "equals" -> proxy === args?.firstOrNull()

                else -> defaultValue(method.returnType)
            }
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            ParcelFileDescriptor::class.java -> null
            else -> null
        }
    }

    private companion object {
        const val LIBBOX_CLASS = "io.nekohasekai.libbox.Libbox"
        const val PLATFORM_CLASS = "io.nekohasekai.libbox.PlatformInterface"
    }
}
