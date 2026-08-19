package com.jannehy.nightshift.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-local settings. The password lands in an encrypted store – it exists
 * only so the app can sign in again silently once the session cookie expires.
 */
class Prefs(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("nightshift", Context.MODE_PRIVATE)

    val cookies: SharedPreferences =
        context.getSharedPreferences("nightshift-cookies", Context.MODE_PRIVATE)

    private val secret: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "nightshift-secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse {
        // A device with a broken keystore should still be usable – it just has
        // to be signed in by hand after the cookie expires.
        context.getSharedPreferences("nightshift-secret-plain", Context.MODE_PRIVATE)
    }

    var serverAddress: String
        get() = plain.getString("server", "") ?: ""
        set(value) = plain.edit().putString("server", value).apply()

    var username: String?
        get() = plain.getString("username", null)
        set(value) = plain.edit().putString("username", value).apply()

    var accentHex: String
        get() = plain.getString("accent", Accents.DEFAULT_HEX) ?: Accents.DEFAULT_HEX
        set(value) = plain.edit().putString("accent", value).apply()

    fun password(account: String): String? = secret.getString("password:$account", null)

    fun savePassword(account: String, password: String) {
        secret.edit().putString("password:$account", password).apply()
    }

    fun clearCredentials() {
        username?.let { secret.edit().remove("password:$it").apply() }
        plain.edit().remove("username").apply()
        cookies.edit().clear().apply()
    }
}

/** Accent presets, mirroring the iOS client. */
object Accents {
    /** The web UI's night-shift accent (`--accent` in static/style.css). */
    const val DEFAULT_HEX = "FFB03A"

    /** Day-shift accent, used where the background is light. */
    const val DEFAULT_LIGHT_HEX = "E8930C"

    val swatches: List<Pair<String, String>> = listOf(
        "Nightshift" to DEFAULT_HEX,
        "Indigo" to "5856D6",
        "Blue" to "0A84FF",
        "Teal" to "30B0C7",
        "Green" to "34C759",
        "Red" to "FF3B30",
        "Pink" to "FF375F",
        "Purple" to "AF52DE",
    )
}
