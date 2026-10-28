package pl.fleet.monitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Przechowuje sesję użytkownika. Zapisywany jest wyłącznie login i skrót MD5 hasła,
 * w zaszyfrowanych SharedPreferences (klucz w Android Keystore).
 */
class SessionStore(context: Context) {

    private val securePrefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Uszkodzony magazyn kluczy (np. po przywróceniu kopii zapasowej) – czyścimy i nie zapamiętujemy sesji.
        context.deleteSharedPreferences("secure_session")
        context.getSharedPreferences("session_fallback", Context.MODE_PRIVATE).also { it.edit().clear().apply() }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _credentials = MutableStateFlow(loadSaved())
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    val lastLogin: String get() = prefs.getString(KEY_LAST_LOGIN, "").orEmpty()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: EServerApi.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().ifBlank { EServerApi.DEFAULT_BASE_URL }).apply()

    var refreshIntervalSec: Int
        get() = prefs.getInt(KEY_REFRESH, 30)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    fun login(c: Credentials, remember: Boolean) {
        prefs.edit().putString(KEY_LAST_LOGIN, c.login).apply()
        if (remember) {
            securePrefs.edit().putString(KEY_LOGIN, c.login).putString(KEY_HASH, c.passwordMd5).apply()
        } else {
            securePrefs.edit().clear().apply()
        }
        _credentials.value = c
    }

    fun logout() {
        securePrefs.edit().clear().apply()
        _credentials.value = null
    }

    private fun loadSaved(): Credentials? {
        val login = securePrefs.getString(KEY_LOGIN, null) ?: return null
        val hash = securePrefs.getString(KEY_HASH, null) ?: return null
        return Credentials(login, hash)
    }

    private companion object {
        const val KEY_LOGIN = "login"
        const val KEY_HASH = "password_md5"
        const val KEY_LAST_LOGIN = "last_login"
        const val KEY_BASE_URL = "base_url"
        const val KEY_REFRESH = "refresh_interval"
    }
}
