package pl.fleet.monitor

import android.app.Application
import org.osmdroid.config.Configuration
import pl.fleet.monitor.data.EServerApi
import pl.fleet.monitor.data.FleetRepository
import pl.fleet.monitor.data.SessionStore
import java.io.File

class FleetApp : Application() {

    lateinit var session: SessionStore
        private set

    @Volatile
    private var api: EServerApi? = null
    private var apiUrl: String? = null

    lateinit var repository: FleetRepository
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
        repository = FleetRepository({ currentApi() }) { session.credentials.value }

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }

    /** Klient API odtwarzany przy zmianie adresu serwera w ustawieniach. */
    @Synchronized
    fun currentApi(): EServerApi {
        val url = session.baseUrl
        val existing = api
        if (existing != null && apiUrl == url) return existing
        return EServerApi(url).also { api = it; apiUrl = url }
    }
}
