package pl.fleet.monitor.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesja i ustawienia użytkownika.
 *
 * [secure] – zaszyfrowany magazyn platformy (Android: EncryptedSharedPreferences z kluczem
 * w Android Keystore, iOS: pęk kluczy). Trafiają tam login, hasło i skrót MD5.
 * Gdy go brak (uszkodzony magazyn kluczy), hasło nie jest zapamiętywane.
 * [prefs] – zwykłe ustawienia widoku (filtry, ukryte pojazdy, ikony, opisy).
 */
class SessionStore(private val prefs: Settings, secureSettings: Settings?) {

    // Błąd magazynu zaszyfrowanego (np. brak dostępu do pęku kluczy) nie może zamknąć aplikacji.
    private val secure: Settings? = secureSettings?.let(::SafeSettings)

    private val _credentials = MutableStateFlow(loadAutoLogin())
    val credentials: StateFlow<Credentials?> = _credentials.asStateFlow()

    /** Czy urządzenie pozwala bezpiecznie zapamiętać hasło. */
    val canRememberPassword: Boolean get() = secure != null

    val rememberEnabled: Boolean get() = prefs.getBoolean(KEY_REMEMBER, true)
    val savedLogin: String get() = secure?.getStringOrNull(KEY_LOGIN) ?: prefs.getString(KEY_LAST_LOGIN, "")
    val savedPassword: String get() = secure?.getStringOrNull(KEY_PASSWORD).orEmpty()

    var baseUrl: String
        get() = prefs.getStringOrNull(KEY_BASE_URL) ?: EServerApi.DEFAULT_BASE_URL
        set(value) = prefs.putString(KEY_BASE_URL, value.trim().ifBlank { EServerApi.DEFAULT_BASE_URL })

    var showDriving: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DRIVING, true)
        set(value) = prefs.putBoolean(KEY_SHOW_DRIVING, value)

    var showParked: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PARKED, true)
        set(value) = prefs.putBoolean(KEY_SHOW_PARKED, value)

    /** Pojazdy wyłączone w ustawieniach (niewidoczne na liście i mapie). */
    var hiddenVehicles: Set<String>
        get() = readList(KEY_HIDDEN).toSet()
        set(value) = writeList(KEY_HIDDEN, value)

    /** Ikony wybrane dla pojazdów: deviceId → nazwa ikony. */
    var vehicleIcons: Map<String, String>
        get() = readMap(KEY_ICONS)
        set(value) = writeMap(KEY_ICONS, value)

    /** Własne opisy pojazdów na mapie: deviceId → opis. */
    var vehicleLabels: Map<String, String>
        get() = readMap(KEY_LABELS)
        set(value) = writeMap(KEY_LABELS, value)

    fun login(c: Credentials, password: String, remember: Boolean) {
        prefs.putString(KEY_LAST_LOGIN, c.login)
        prefs.putBoolean(KEY_REMEMBER, remember)
        if (remember && secure != null) {
            secure.putString(KEY_LOGIN, c.login)
            secure.putString(KEY_PASSWORD, password)
            secure.putString(KEY_HASH, c.passwordMd5)
        } else {
            secure?.clear()
        }
        _credentials.value = c
    }

    /**
     * Kończy sesję (bez automatycznego logowania przy następnym starcie).
     * Zapamiętany login i hasło zostają, żeby formularz logowania był od razu wypełniony.
     */
    fun logout() {
        secure?.remove(KEY_HASH)
        _credentials.value = null
    }

    private fun loadAutoLogin(): Credentials? {
        val s = secure ?: return null
        val login = s.getStringOrNull(KEY_LOGIN) ?: return null
        val hash = s.getStringOrNull(KEY_HASH) ?: return null
        return Credentials(login, hash)
    }

    // Listy i mapy zapisujemy jako tekst – wspólny format dla Androida i iOS.
    private fun readList(key: String): List<String> =
        prefs.getStringOrNull(key)?.split(SEP)?.filter { it.isNotEmpty() }.orEmpty()

    private fun writeList(key: String, values: Collection<String>) =
        prefs.putString(key, values.joinToString(SEP))

    private fun readMap(key: String): Map<String, String> =
        readList(key).mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    private fun writeMap(key: String, value: Map<String, String>) =
        writeList(key, value.map { "${it.key}=${it.value}" })

    companion object {
        private const val SEP = "\u001F"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"
        const val KEY_HASH = "password_md5"
        const val KEY_REMEMBER = "remember"
        const val KEY_LAST_LOGIN = "last_login"
        const val KEY_BASE_URL = "base_url"
        const val KEY_SHOW_DRIVING = "show_driving"
        const val KEY_SHOW_PARKED = "show_parked"
        // Wersja 2: tekst zamiast zbiorów SharedPreferences (Android migruje stare wartości przy starcie).
        const val KEY_HIDDEN = "hidden_vehicles_v2"
        const val KEY_ICONS = "vehicle_icons_v2"
        const val KEY_LABELS = "vehicle_labels_v2"

        /** Stare klucze Androida (zbiory tekstów) → nowe klucze tekstowe. */
        val LEGACY_SET_KEYS = mapOf(
            "hidden_vehicles" to KEY_HIDDEN,
            "vehicle_icons" to KEY_ICONS,
            "vehicle_labels" to KEY_LABELS,
        )

        /** Format zapisu zbioru – używany przez migrację na Androidzie. */
        fun encodeList(values: Collection<String>): String = values.joinToString(SEP)
    }
}

/** Ustawienia, które przy błędzie zapisu/odczytu zachowują się jak puste zamiast rzucać wyjątek. */
private class SafeSettings(private val d: Settings) : Settings {
    override val keys: Set<String> get() = runCatching { d.keys }.getOrDefault(emptySet())
    override val size: Int get() = runCatching { d.size }.getOrDefault(0)
    override fun clear() { runCatching { d.clear() } }
    override fun remove(key: String) { runCatching { d.remove(key) } }
    override fun hasKey(key: String) = runCatching { d.hasKey(key) }.getOrDefault(false)
    override fun putInt(key: String, value: Int) { runCatching { d.putInt(key, value) } }
    override fun getInt(key: String, defaultValue: Int) = runCatching { d.getInt(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getIntOrNull(key: String) = runCatching { d.getIntOrNull(key) }.getOrNull()
    override fun putLong(key: String, value: Long) { runCatching { d.putLong(key, value) } }
    override fun getLong(key: String, defaultValue: Long) = runCatching { d.getLong(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getLongOrNull(key: String) = runCatching { d.getLongOrNull(key) }.getOrNull()
    override fun putString(key: String, value: String) { runCatching { d.putString(key, value) } }
    override fun getString(key: String, defaultValue: String) = runCatching { d.getString(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getStringOrNull(key: String) = runCatching { d.getStringOrNull(key) }.getOrNull()
    override fun putFloat(key: String, value: Float) { runCatching { d.putFloat(key, value) } }
    override fun getFloat(key: String, defaultValue: Float) = runCatching { d.getFloat(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getFloatOrNull(key: String) = runCatching { d.getFloatOrNull(key) }.getOrNull()
    override fun putDouble(key: String, value: Double) { runCatching { d.putDouble(key, value) } }
    override fun getDouble(key: String, defaultValue: Double) = runCatching { d.getDouble(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getDoubleOrNull(key: String) = runCatching { d.getDoubleOrNull(key) }.getOrNull()
    override fun putBoolean(key: String, value: Boolean) { runCatching { d.putBoolean(key, value) } }
    override fun getBoolean(key: String, defaultValue: Boolean) = runCatching { d.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)
    override fun getBooleanOrNull(key: String) = runCatching { d.getBooleanOrNull(key) }.getOrNull()
}
