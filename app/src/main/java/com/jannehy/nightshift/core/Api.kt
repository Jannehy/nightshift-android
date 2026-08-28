package com.jannehy.nightshift.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ApiException(message: String) : Exception(message) {
    object Unauthorized : ApiException("Not signed in")
    object Forbidden : ApiException("Administrators only")
    class Conflict(message: String) : ApiException(message)
    class Server(message: String) : ApiException(message)

    /** Network-level failure. The [kind] is what the UI turns into a sentence –
     *  "Failed to connect to /192.168.178.24:8765" is accurate but unhelpful. */
    class Transport(val kind: Kind, cause: Throwable) :
        ApiException(cause.message ?: "Network error") {

        enum class Kind { UNKNOWN_HOST, REFUSED, TIMEOUT, TLS, OTHER }

        companion object {
            fun of(cause: Throwable): Transport = Transport(
                when (cause) {
                    is java.net.UnknownHostException -> Kind.UNKNOWN_HOST
                    is java.net.ConnectException -> Kind.REFUSED
                    is java.net.SocketTimeoutException -> Kind.TIMEOUT
                    is javax.net.ssl.SSLException -> Kind.TLS
                    else -> Kind.OTHER
                },
                cause,
            )
        }
    }
}

/**
 * Thin wrapper over the Nightshift HTTP API.
 *
 * Auth is the Flask session cookie, persisted so a restart does not require a
 * new login; [Session] re-authenticates from the encrypted store when the
 * cookie has expired server-side.
 */
class Api(private val baseUrl: HttpUrl, cookieStore: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMedia = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(cookieStore))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** A long read timeout would stall every normal call, so the SSE stream
     *  gets its own client instead. */
    private val streamClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val server: HttpUrl get() = baseUrl

    // ---------------------------------------------------------------- plumbing

    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        path.trim('/').split('/').forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun body(fields: Map<String, Any?>) =
        JSONObject(fields.filterValues { it != null }).toString().toRequestBody(jsonMedia)

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException.Transport.of(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            // Routes outside /api/ answer a browser redirect to the login page
            // instead of a 401, and OkHttp follows it – a 200 whose final URL
            // is the login screen means the session is gone.
            val path = it.request.url.encodedPath
            if (!path.contains("/api/") && (path.endsWith("/login") || path.endsWith("/setup"))) {
                throw ApiException.Unauthorized
            }
            when {
                it.isSuccessful -> text
                it.code == 401 -> throw ApiException.Unauthorized
                it.code == 403 -> throw ApiException.Forbidden
                it.code == 409 -> throw ApiException.Conflict(errorMessage(text) ?: "Already running")
                else -> throw ApiException.Server(errorMessage(text) ?: "HTTP ${it.code}")
            }
        }
    }

    private fun errorMessage(text: String): String? = runCatching {
        JSONObject(text).optString("error").takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T {
        val text = call(Request.Builder().url(url(path, query)).header("Accept", "application/json").build())
        return json.decodeFromString(text)
    }

    private suspend fun send(path: String, method: String, fields: Map<String, Any?> = emptyMap()): String =
        call(Request.Builder().url(url(path)).method(method, body(fields))
            .header("Accept", "application/json").build())

    private suspend inline fun <reified T> sendFor(
        path: String, method: String, fields: Map<String, Any?> = emptyMap(),
    ): T = json.decodeFromString(send(path, method, fields))

    // ------------------------------------------------------------------- auth

    suspend fun health(): HealthInfo = get("health")

    /** Servers before 1.3 have no version endpoint; there a 404 is the answer,
     *  not a failure. */
    suspend fun version(): VersionInfo? = try {
        get<VersionInfo>("api/version")
    } catch (e: ApiException.Server) {
        null
    } catch (e: Exception) {
        null
    }

    suspend fun login(username: String, password: String): NightshiftUser =
        sendFor<LoginResponse>("api/login", "POST",
            mapOf("username" to username, "password" to password)).user

    suspend fun logout() { send("api/logout", "POST") }

    suspend fun me(): MeInfo = get("api/me")

    // -------------------------------------------------------------- downloads

    suspend fun startDownload(link: String, ownerId: String?, sync: Boolean): JobRef =
        sendFor("download", "POST",
            mapOf("url" to link, "owner_id" to (ownerId ?: ""), "sync" to sync))

    suspend fun downloadFromQuery(query: String): JobRef =
        sendFor("api/download-from-query", "POST", mapOf("query" to query))

    suspend fun downloadAlbum(albumId: Long): JobRef =
        sendFor("api/download-album", "POST", mapOf("itunes_album_id" to albumId))

    suspend fun downloadLog(): LogState = get("download-log")

    suspend fun queue(): QueueStatus = get("api/queue")

    suspend fun cookieStatus(): CookieStatusResponse = get("api/cookies/status")

    // ---------------------------------------------------------------- nightly

    suspend fun startNightly(): JobRef = sendFor("nightly", "POST")

    suspend fun nightlyRunning(): Boolean = get<NightlyStatus>("nightly-status").running

    suspend fun nightlyLog(): LogState = get("nightly-log")

    // ----------------------------------------------------------------- search

    suspend fun search(term: String, entity: SearchEntity): List<SearchResult> {
        val response: SearchResponse =
            get("api/search", mapOf("q" to term, "entity" to entity.value))
        response.error?.let { throw ApiException.Server(it) }
        return response.results
    }

    // ------------------------------------------------------------------- sync

    suspend fun syncPlaylists(): SyncListResponse = get("api/sync-playlists")

    suspend fun updateSyncMeta(url: String?, file: String?, owner: String?, isPublic: Boolean) {
        send("api/sync-playlists", "PATCH", mapOf(
            "url" to (url ?: ""), "file" to (file ?: ""),
            "owner" to (owner ?: ""), "public" to isPublic))
    }

    suspend fun removeSyncItem(url: String?, file: String?) {
        send("api/sync-playlists", "DELETE",
            mapOf("url" to (url ?: ""), "file" to (file ?: "")))
    }

    suspend fun navidromeUsers(): NavidromeUsersResponse = get("nd-users")

    // ----------------------------------------------------------------- config

    /** The config API hands out whatever is in config.yaml, so the client keeps
     *  it as loosely typed JSON and renders a control per value kind. Only
     *  scalars are editable; nested structures are left alone. */
    suspend fun config(): Map<String, Map<String, JsonPrimitive>> {
        val root = json.parseToJsonElement(
            call(Request.Builder().url(url("api/config")).build())).jsonObject
        return root.entries
            .filter { !it.key.startsWith("_") }
            .mapNotNull { (section, value) ->
                val fields = (value as? JsonObject)
                    ?.mapNotNull { (key, raw) -> (raw as? JsonPrimitive)?.let { key to it } }
                    ?.toMap()
                    ?: return@mapNotNull null
                if (fields.isEmpty()) null else section to fields
            }
            .toMap()
    }

    suspend fun saveConfig(updates: Map<String, Map<String, JsonPrimitive>>) {
        val payload = buildJsonObject {
            updates.forEach { (section, fields) ->
                put(section, buildJsonObject { fields.forEach { (k, v) -> put(k, v) } })
            }
        }
        call(Request.Builder().url(url("api/config"))
            .post(payload.toString().toRequestBody(jsonMedia)).build())
    }

    suspend fun resetConfig() { send("api/config/reset", "POST") }

    // -------------------------------------------------------------- user mgmt

    suspend fun users(): List<NightshiftUser> = get<UsersResponse>("api/users").users

    suspend fun createUser(username: String, password: String, role: String) {
        send("api/users", "POST",
            mapOf("username" to username, "password" to password, "role" to role))
    }

    suspend fun deleteUser(username: String) {
        send("api/users", "DELETE", mapOf("username" to username))
    }

    suspend fun changePassword(username: String?, newPassword: String) {
        send("api/users/password", "POST",
            mapOf("username" to (username ?: ""), "password" to newPassword))
    }

    // ------------------------------------------------------------ live stream

    /** Server-sent events for one job. Ends after `done`/`error`; the server's
     *  `: ping` comments keep the connection alive and are skipped. */
    fun events(jobId: String): Flow<JobEvent> = flow {
        val request = Request.Builder()
            .url(url("stream/$jobId"))
            .header("Accept", "text/event-stream")
            .build()
        val response = try {
            streamClient.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException.Transport.of(e)
        }
        response.use {
            if (it.code == 401) throw ApiException.Unauthorized
            if (!it.isSuccessful) throw ApiException.Server("HTTP ${it.code}")
            val source = it.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                val event = runCatching { json.decodeFromString<JobEvent>(payload) }.getOrNull()
                    ?: continue
                emit(event)
                if (event.isTerminal) break
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /**
         * Turns whatever the user typed into a base URL. A bare host gets
         * http:// and Nightshift's default port; anything more explicit is
         * taken as typed, so a reverse proxy on port 80 or 443 is not pushed
         * to 8765, and a path prefix survives.
         */
        fun normalize(address: String): HttpUrl? {
            var text = address.trim()
            if (text.isEmpty()) return null
            val hadScheme = text.contains("://")
            val hadPort = Regex(":[0-9]+(/|${'$'})").containsMatchIn(text)
            if (!hadScheme) text = "http://" + text
            text = text.trimEnd('/')
            val parsed = text.toHttpUrlOrNull() ?: return null
            if (parsed.host.isEmpty()) return null
            // Only a bare host gets the default port: an explicit scheme or
            // port means the user knows where the server listens.
            return if (!hadScheme && !hadPort) parsed.newBuilder().port(8765).build() else parsed
        }
    }
}

/** Cookie storage that survives process death, so the session outlives a restart. */
private class PersistentCookieJar(private val prefs: SharedPreferences) : CookieJar {

    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    init {
        prefs.getStringSet(KEY, emptySet())?.forEach { entry ->
            val (host, raw) = entry.split(SEPARATOR, limit = 2).let {
                if (it.size == 2) it[0] to it[1] else return@forEach
            }
            val url = HttpUrl.Builder().scheme("http").host(host).build()
            Cookie.parse(url, raw)?.let { cookies.getOrPut(host) { mutableListOf() }.add(it) }
        }
    }

    override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
        val host = url.host
        val stored = cookies.getOrPut(host) { mutableListOf() }
        list.forEach { cookie ->
            stored.removeAll { it.name == cookie.name }
            stored.add(cookie)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val stored = cookies[url.host] ?: return emptyList()
        val valid = stored.filter { it.expiresAt > now }
        if (valid.size != stored.size) {
            cookies[url.host] = valid.toMutableList()
            persist()
        }
        return valid
    }

    private fun persist() {
        val flat = cookies.flatMap { (host, list) -> list.map { "$host$SEPARATOR$it" } }.toSet()
        prefs.edit().putStringSet(KEY, flat).apply()
    }

    companion object {
        private const val KEY = "cookies"
        private const val SEPARATOR = "|"
    }
}
