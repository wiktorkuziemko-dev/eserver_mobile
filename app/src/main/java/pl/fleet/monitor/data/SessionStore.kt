package pl.fleet.monitor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesja i ustawienia użytkownika.
 *
 * Dane logowania (login, hasło, skrót MD5) trafiają do zaszyfrowanych SharedPreferences
 * (klucz w Android Keystore). Ustawienia widoku (filtry, ukryte pojazdy, odświeżanie)
 * przechowywane są w zwykłych SharedPreferences.
 */
class SessionStore(private val context: Context) {

    private val secure: SharedPreferences? = openSecure()

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _credentials = MutableStateFlow(loadAutoLogin())
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    /** Czy urządzenie pozwala bezpiecznie zapamiętać hasło. */
    val canRememberPassword: Boolean get() = secure != null

    val rememberEnabled: Boolean get() = prefs.getBoolean(KEY_REMEMBER, true)
    val savedLogin: String get() = secure?.getString(KEY_LOGIN, null) ?: prefs.getString(KEY_LAST_LOGIN, "").orEmpty()
    val savedPassword: String get() = secure?.getString(KEY_PASSWORD, null).orEmpty()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: EServerApi.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().ifBlank { EServerApi.DEFAULT_BASE_URL }).apply()

    var refreshIntervalSec: Int
        get() = prefs.getInt(KEY_REFRESH, 30)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    var showDriving: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DRIVING, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DRIVING, value).apply()

    var showParked: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PARKED, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_PARKED, value).apply()

    /** Pojazdy wyłączone z mapy przełącznikiem na liście. */
    var hiddenVehicles: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN, value.toSet()).apply()

    /** Ikony wybrane dla pojazdów: deviceId → nazwa ikony. */
    var vehicleIcons: Map<String, String>
        get() = prefs.getStringSet(KEY_ICONS, emptySet())!!.mapNotNull { e ->
            e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        set(value) = prefs.edit().putStringSet(KEY_ICONS, value.map { "${it.key}=${it.value}" }.toSet()).apply()

    fun login(c: Credentials, password: String, remember: Boolean) {
        prefs.edit().putString(KEY_LAST_LOGIN, c.login).putBoolean(KEY_REMEMBER, remember).apply()
        if (remember && secure != null) {
            secure.edit()
                .putString(KEY_LOGIN, c.login)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_HASH, c.passwordMd5)
                .apply()
        } else {
            secure?.edit()?.clear()?.apply()
        }
        _credentials.value = c
    }

    /**
     * Kończy sesję (bez automatycznego logowania przy następnym starcie).
     * Zapamiętany login i hasło zostają, żeby formularz logowania był od razu wypełniony.
     */
    fun logout() {
        secure?.edit()?.remove(KEY_HASH)?.apply()
        _credentials.value = null
    }

    private fun loadAutoLogin(): Credentials? {
        val s = secure ?: return null
        val login = s.getString(KEY_LOGIN, null) ?: return null
        val hash = s.getString(KEY_HASH, null) ?: return null
        return Credentials(login, hash)
    }

    private fun openSecure(): SharedPreferences? {
        fun create(): SharedPreferences {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context, SECURE_FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return runCatching { create() }.recoverCatching {
            // Uszkodzony magazyn (np. po przywróceniu kopii zapasowej) – zakładamy go od nowa.
            context.deleteSharedPreferences(SECURE_FILE)
            create()
        }.getOrNull()
    }

    private companion object {
        const val SECURE_FILE = "secure_session"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"
        const val KEY_HASH = "password_md5"
        const val KEY_REMEMBER = "remember"
        const val KEY_LAST_LOGIN = "last_login"
        const val KEY_BASE_URL = "base_url"
        const val KEY_REFRESH = "refresh_interval"
        const val KEY_SHOW_DRIVING = "show_driving"
        const val KEY_SHOW_PARKED = "show_parked"
        const val KEY_HIDDEN = "hidden_vehicles"
        const val KEY_ICONS = "vehicle_icons"
    }
}
