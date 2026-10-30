package pl.fleet.monitor.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureRequest {
            setTimeoutInterval(90.0)
        }
    }
}

/** Klient do kafelków mapy: więcej równoczesnych pobrań z jednego serwera. */
actual fun platformTileHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            setHTTPMaximumConnectionsPerHost(8)
            setTimeoutIntervalForRequest(20.0)
        }
    }
}
