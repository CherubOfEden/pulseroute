package com.pulseroute.vpn.data.repository

import android.content.Context
import android.util.Log
import com.pulseroute.vpn.data.model.ServerCache
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.parser.ServerListParser
import com.pulseroute.vpn.data.parser.ServerParseException
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class ServerRepository(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    scope: CoroutineScope,
) {
    private val cacheFile = File(context.applicationContext.filesDir, CACHE_FILE)
    private val _servers = MutableStateFlow<List<ServerEntry>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    val servers: StateFlow<List<ServerEntry>> = _servers.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val loadJob = scope.launch(Dispatchers.IO) { loadCache() }

    suspend fun awaitLoaded() {
        loadJob.join()
    }

    suspend fun currentServers(): List<ServerEntry> {
        awaitLoaded()
        return _servers.value
    }

    suspend fun findServer(serverId: String): ServerEntry? {
        awaitLoaded()
        return _servers.value.firstOrNull { it.id == serverId }
    }

    suspend fun refresh(remoteUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = fetchAndParseRemoteList(remoteUrl.trim())
            _servers.value = parsed
            _loaded.value = true
            persist(parsed)
            parsed.size
        }
    }

    /**
     * Fetches [remoteUrl] and parses the response in three progressively looser passes:
     * JSON, Base64-encoded proxy links, then plain-text proxy links.
     */
    private fun fetchAndParseRemoteList(remoteUrl: String): List<ServerEntry> {
        val raw = try {
            require(remoteUrl.isNotEmpty()) { "Remote URL is empty" }
            require(remoteUrl.startsWith("https://")) {
                "Remote URL must use HTTPS"
            }

            val request = Request.Builder()
                .url(remoteUrl)
                .header("Accept", "application/json, text/javascript, text/plain, */*")
                .header("User-Agent", USER_AGENT)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_PAYLOAD_BYTES) {
                    throw IOException("Response is too large")
                }
                val payload = body.string()
                if (payload.length > MAX_PAYLOAD_BYTES) {
                    throw IOException("Response is too large")
                }
                payload
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to fetch remote server list from $remoteUrl", error)
            throw error
        }

        return try {
            parseRemoteListPayload(raw)
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Failed to parse remote server list from $remoteUrl. Raw response:\n$raw",
                error,
            )
            throw error
        }
    }

    private fun parseRemoteListPayload(raw: String): List<ServerEntry> {
        val trimmed = raw.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) {
            throw ServerParseException("The remote response is empty")
        }

        var jsonFailure: Exception? = null
        try {
            return ServerListParser.parseJson(trimmed)
        } catch (error: Exception) {
            jsonFailure = error
        }

        val decoded = decodeBase64Payload(trimmed)
        if (decoded != null) {
            val decodedServers = runCatching {
                try {
                    ServerListParser.parseJson(decoded)
                } catch (_: Exception) {
                    ServerListParser.parseProxyLinks(decoded)
                }
            }.getOrNull().orEmpty()
            if (decodedServers.isNotEmpty()) return decodedServers
        }

        val plainTextServers = ServerListParser.parseProxyLinks(trimmed)
        if (plainTextServers.isNotEmpty()) return plainTextServers

        val preview = raw.take(100)
        throw ServerParseException(
            "Unable to parse remote server list. First 100 characters: \"$preview\"",
            jsonFailure,
        )
    }

    private fun decodeBase64Payload(raw: String): String? {
        val candidate = raw
            .substringAfter("base64,", raw)
            .filterNot { it.isWhitespace() }
        if (candidate.length < 16 || !BASE64_PATTERN.matches(candidate)) return null

        val padded = candidate + "=".repeat((4 - candidate.length % 4) % 4)
        val bytes = runCatching { Base64.getDecoder().decode(padded) }
            .recoverCatching { Base64.getUrlDecoder().decode(padded) }
            .getOrNull()
            ?: return null
        val decoded = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        if (decoded.contains('\uFFFD')) return null

        val normalized = decoded.removePrefix("\uFEFF").trim()
        return decoded.takeIf {
            normalized.contains("://") ||
                normalized.startsWith("[") ||
                normalized.startsWith("{")
        }
    }

    suspend fun import(payload: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = try {
                ServerListParser.parse(payload)
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Failed to parse imported server list. Raw payload:\n$payload",
                    error,
                )
                throw error
            }
            val merged = LinkedHashMap<String, ServerEntry>(_servers.value.size + parsed.size)
            _servers.value.forEach { merged[it.id] = it }
            parsed.forEach { merged[it.id] = it }
            val result = merged.values.toList()
            _servers.value = result
            _loaded.value = true
            persist(result)
            parsed.size
        }
    }

    private fun loadCache() {
        try {
            if (!cacheFile.exists()) return
            val cache = json.decodeFromString(ServerCache.serializer(), cacheFile.readText())
            _servers.value = cache.servers
        } catch (_: Exception) {
            // A corrupt cache is not fatal; the next refresh/import replaces it.
        } finally {
            _loaded.value = true
        }
    }

    private fun persist(servers: List<ServerEntry>) {
        try {
            val cache = ServerCache(
                updatedAtEpochMs = System.currentTimeMillis(),
                servers = servers,
            )
            val temp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            temp.writeText(json.encodeToString(ServerCache.serializer(), cache))
            if (!temp.renameTo(cacheFile)) {
                cacheFile.writeText(temp.readText())
                temp.delete()
            }
        } catch (_: Exception) {
            // Cache persistence is best-effort; the in-memory list is still usable.
        }
    }

    private companion object {
        const val TAG = "ServerRepository"
        const val CACHE_FILE = "servers_cache.json"
        const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
        const val USER_AGENT = "PulseRoute/0.1 (Android)"
        val BASE64_PATTERN = Regex("""^[A-Za-z0-9+/=_-]+$""")
    }
}
