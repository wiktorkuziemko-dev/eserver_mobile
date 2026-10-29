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
