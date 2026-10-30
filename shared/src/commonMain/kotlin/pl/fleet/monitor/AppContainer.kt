package pl.fleet.monitor

import com.russhwolf.settings.Settings
import pl.fleet.monitor.data.EServerApi
import pl.fleet.monitor.data.FleetRepository
import pl.fleet.monitor.data.SessionStore

/**
 * Zależności aplikacji, tworzone raz przy starcie przez platformę
 * (Android: FleetApp, iOS: MainViewController) i dostępne przez [AppGraph].
 */
class AppContainer(
    settings: Settings,
    secureSettings: Settings?,
    val appVersion: String,
    /** Katalog pamięci podręcznej systemu (kafelki mapy); null = bez zapisu na dysk. */
    val cacheDir: String? = null,
) {
    val session = SessionStore(settings, secureSettings)

    private var api: EServerApi? = null
    private var apiUrl: String? = null

    val repository = FleetRepository({ currentApi() }) { session.credentials.value }

    /** Klient API odtwarzany przy zmianie adresu serwera. */
    fun currentApi(): EServerApi {
        val url = session.baseUrl
        api?.takeIf { apiUrl == url }?.let { return it }
        return EServerApi(url).also { api = it; apiUrl = url }
    }
}

object AppGraph {
    lateinit var container: AppContainer
        private set

    val isInitialized: Boolean get() = ::container.isInitialized

    fun init(container: AppContainer) {
        if (!isInitialized) this.container = container
    }
}
