package com.pulseroute.vpn.routing

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Absolute paths to the geo databases used by the engine.
 *
 * sing-box consumes the sing-geoip/sing-geosite `.db` format. When only the
 * legacy V2Ray `.dat` files are bundled, [hasLegacyDatFallback] is true and the
 * caller should either switch to an Xray-based engine or download the `.db`
 * assets. The installer still copies the `.dat` files so they can be reused.
 */
data class GeoAssets(
    val geoIpPath: String? = null,
    val geoSitePath: String? = null,
    val hasLegacyDatFallback: Boolean = false,
)

class GeoAssetInstaller(private val context: Context) {

    suspend fun ensureInstalled(): GeoAssets = withContext(Dispatchers.IO) {
        val geoDir = File(context.filesDir, "geo").apply { mkdirs() }

        val geoIpDb = copyAsset(geoDir, "geoip.db")
        val geoSiteDb = copyAsset(geoDir, "geosite.db")
        val geoIpDat = copyAsset(geoDir, "geoip.dat")
        val geoSiteDat = copyAsset(geoDir, "geosite.dat")

        GeoAssets(
            geoIpPath = geoIpDb?.absolutePath,
            geoSitePath = geoSiteDb?.absolutePath,
            hasLegacyDatFallback = (geoIpDb == null && geoIpDat != null) ||
                (geoSiteDb == null && geoSiteDat != null),
        )
    }

    private fun copyAsset(targetDir: File, fileName: String): File? {
        val assetPath = ASSET_CANDIDATES
            .asSequence()
            .map { prefix -> if (prefix.isEmpty()) fileName else "$prefix/$fileName" }
            .firstOrNull(::assetExists)
            ?: return null

        val target = File(targetDir, fileName)
        if (target.exists() && target.length() > 0L) return target

        return try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.length() > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        val ASSET_CANDIDATES = listOf("geo", "")
    }
}
