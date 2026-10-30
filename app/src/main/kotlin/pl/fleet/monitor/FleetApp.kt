package pl.fleet.monitor

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.SharedPreferencesSettings
import pl.fleet.monitor.data.SessionStore

class FleetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        migrateStringSets(prefs)
        AppGraph.init(
            AppContainer(
                settings = SharedPreferencesSettings(prefs),
                secureSettings = openSecure()?.let { SharedPreferencesSettings(it) },
                appVersion = BuildConfig.VERSION_NAME,
                cacheDir = cacheDir.absolutePath,
            ),
        )
    }

    /** Zaszyfrowane ustawienia (klucz w Android Keystore); null, gdy magazyn kluczy jest uszkodzony. */
    private fun openSecure(): SharedPreferences? {
        fun create(): SharedPreferences {
            val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                this, SECURE_FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return runCatching { create() }.recoverCatching {
            // Uszkodzony magazyn (np. po przywróceniu kopii zapasowej) – zakładamy go od nowa.
            deleteSharedPreferences(SECURE_FILE)
            create()
        }.getOrNull()
    }

    /** Wersje < 2.1 zapisywały listy jako zbiory SharedPreferences – przepisujemy je do formatu wspólnego z iOS. */
    private fun migrateStringSets(prefs: SharedPreferences) {
        val edit = prefs.edit()
        SessionStore.LEGACY_SET_KEYS.forEach { (old, new) ->
            val set = runCatching { prefs.getStringSet(old, null) }.getOrNull() ?: return@forEach
            if (!prefs.contains(new)) edit.putString(new, SessionStore.encodeList(set))
            edit.remove(old)
        }
        edit.apply()
    }

    private companion object {
        const val SECURE_FILE = "secure_session"
    }
}
