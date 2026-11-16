package com.pulseroute.vpn.data.repository

import android.content.Context
import com.pulseroute.vpn.data.model.ServerCache
import com.pulseroute.vpn.data.model.ServerEntry
import com.pulseroute.vpn.data.parser.ServerListParser
import java.io.File
import java.io.IOException
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
            val url = remoteUrl.trim()
            require(url.isNotEmpty()) { "Remote URL is empty" }
            require(url.startsWith("https://")) {
                "Remote URL must use HTTPS"
            }

            val request = Request.Builder()
                .url(url)
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

                val parsed = ServerListParser.parse(payload)
                _servers.value = parsed
                _loaded.value = true
                persist(parsed)
                parsed.size
            }
        }
    }

    suspend fun import(payload: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = ServerListParser.parse(payload)
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
        const val CACHE_FILE = "servers_cache.json"
        const val MAX_PAYLOAD_BYTES = 5 * 1024 * 1024
        const val USER_AGENT = "PulseRoute/0.1 (Android)"
    }
}
