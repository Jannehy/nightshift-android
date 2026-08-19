package com.jannehy.nightshift.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * App-wide auth and connection state: which server, who is signed in, and which
 * optional server features the UI may show. Mirrors the server's access gate:
 * no server -> setup -> login -> app.
 */
class Session(context: Context) {

    enum class Phase { LAUNCHING, NEEDS_SERVER, NEEDS_SETUP, NEEDS_LOGIN, READY }

    val prefs = Prefs(context.applicationContext)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var phase by mutableStateOf(Phase.LAUNCHING)
        private set
    var me by mutableStateOf<MeInfo?>(null)
        private set
    var serverVersion by mutableStateOf<VersionInfo?>(null)
        private set
    var serverAddress by mutableStateOf(prefs.serverAddress)
    var errorMessage by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var accentHex by mutableStateOf(prefs.accentHex)

    var api: Api? = null
        private set

    val isAdmin: Boolean get() = me?.isAdmin == true
    val syncEnabled: Boolean get() = me?.syncEnabled == true
    val navidromeEnabled: Boolean get() = me?.navidromeEnabled == true

    fun setAccent(hex: String) {
        accentHex = hex
        prefs.accentHex = hex
    }

    suspend fun bootstrap() {
        val url = Api.normalize(serverAddress)
        if (url == null) {
            phase = Phase.NEEDS_SERVER
            return
        }
        api = Api(url, prefs.cookies)
        refreshPhase()
    }

    suspend fun connect(address: String): Boolean {
        val url = Api.normalize(address)
        if (url == null) {
            errorMessage = "invalid"
            return false
        }
        busy = true
        try {
            val candidate = Api(url, prefs.cookies)
            // A successful /health proves the server is reachable. Everything
            // after this point can only fail for auth reasons, so it must never
            // send the user back to the server screen with "unreachable".
            val health = candidate.health()
            serverAddress = address
            prefs.serverAddress = address
            api = candidate
            errorMessage = null
            serverVersion = candidate.version()
            phase = when {
                !health.configured -> Phase.NEEDS_SETUP
                restoreSession() -> Phase.READY
                else -> Phase.NEEDS_LOGIN
            }
            return true
        } catch (e: Exception) {
            errorMessage = describe(e)
            return false
        } finally {
            busy = false
        }
    }

    private suspend fun refreshPhase() {
        val client = api ?: run { phase = Phase.NEEDS_SERVER; return }
        serverVersion = client.version()
        try {
            me = client.me()
            phase = Phase.READY
        } catch (e: ApiException.Unauthorized) {
            if (!attemptStoredLogin()) determineSetupOrLogin(client)
        } catch (e: Exception) {
            determineSetupOrLogin(client)
        }
    }

    private suspend fun determineSetupOrLogin(client: Api) {
        val health = runCatching { client.health() }.getOrNull()
        if (health == null) {
            phase = Phase.NEEDS_SERVER
            errorMessage = "unreachable"
        } else {
            phase = if (health.configured) Phase.NEEDS_LOGIN else Phase.NEEDS_SETUP
        }
    }

    /** Picks up an existing session, or signs in again from the stored
     *  credentials. Never touches [phase] – the caller decides. */
    private suspend fun restoreSession(): Boolean {
        val client = api ?: return false
        return try {
            me = client.me()
            true
        } catch (e: Exception) {
            attemptStoredLogin()
        }
    }

    private suspend fun attemptStoredLogin(): Boolean {
        val client = api ?: return false
        val name = prefs.username ?: return false
        val password = prefs.password(name) ?: return false
        return try {
            client.login(name, password)
            me = client.me()
            phase = Phase.READY
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun login(username: String, password: String) {
        val client = api ?: return
        busy = true
        errorMessage = null
        try {
            client.login(username, password)
            me = client.me()
            prefs.username = username
            prefs.savePassword(username, password)
            phase = Phase.READY
        } catch (e: Exception) {
            errorMessage = describe(e)
        } finally {
            busy = false
        }
    }

    /** Turns an exception into a key the UI can phrase in the user's language.
     *  Server-sent messages are passed through as they are already meaningful. */
    private fun describe(e: Throwable): String = when (e) {
        is ApiException.Unauthorized -> "auth"
        is ApiException.Transport -> when (e.kind) {
            ApiException.Transport.Kind.UNKNOWN_HOST -> "host"
            ApiException.Transport.Kind.REFUSED -> "refused"
            ApiException.Transport.Kind.TIMEOUT -> "timeout"
            ApiException.Transport.Kind.TLS -> "tls"
            ApiException.Transport.Kind.OTHER -> "unreachable"
        }
        else -> e.message ?: "unreachable"
    }

    suspend fun logout(forgetServer: Boolean = false) {
        runCatching { api?.logout() }
        prefs.clearCredentials()
        me = null
        if (forgetServer) {
            serverAddress = ""
            prefs.serverAddress = ""
            api = null
            phase = Phase.NEEDS_SERVER
        } else {
            phase = Phase.NEEDS_LOGIN
        }
    }

    suspend fun refreshMe() {
        runCatching { api?.me() }.getOrNull()?.let { me = it }
    }

    /** Any screen that hits a 401 routes through here, so the whole app agrees
     *  on the auth state instead of each showing its own error. */
    suspend fun handleUnauthorized() {
        if (attemptStoredLogin()) return
        me = null
        phase = Phase.NEEDS_LOGIN
    }
}
