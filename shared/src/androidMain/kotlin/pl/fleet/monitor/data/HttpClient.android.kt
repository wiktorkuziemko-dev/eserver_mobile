package pl.fleet.monitor.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(20, TimeUnit.SECONDS)
            readTimeout(90, TimeUnit.SECONDS)
            // Wymagany TLS 1.2 lub nowszy (serwer akceptuje wyłącznie szyfry AEAD: AES-GCM, AES-CCM, CHACHA20).
            connectionSpecs(
                listOf(
                    ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                        .build(),
                ),
            )
        }
    }
}
