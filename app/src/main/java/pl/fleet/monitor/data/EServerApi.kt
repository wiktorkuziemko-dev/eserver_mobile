package pl.fleet.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.ConnectionSpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import java.io.IOException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Dane logowania – hasło przechowujemy wyłącznie jako skrót MD5, tak jak wymaga API. */
data class Credentials(val login: String, val passwordMd5: String) {
    val authorizationHeader: String
        get() = "Basic " + Base64.getEncoder().encodeToString("$login:$passwordMd5".toByteArray(Charsets.UTF_8))

    companion object {
        fun fromPlain(login: String, password: String) = Credentials(login.trim(), md5(password))

        fun md5(text: String): String = MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized : ApiException("Błędny login lub hasło albo brak aktywnego dostępu do API (HTTP 403).")
    class Http(val code: Int) : ApiException("Serwer zwrócił błąd HTTP $code.")
    class Network(cause: Throwable) : ApiException("Brak połączenia z serwerem. Sprawdź dostęp do internetu.", cause)
    class Parse(cause: Throwable) : ApiException("Nieoczekiwany format odpowiedzi serwera.", cause)
    class Server(msg: String) : ApiException(msg)
}

/**
 * Klient API platformy E-SERVER (specyfikacja v2.0.0).
 * Wszystkie funkcje wywoływane są metodą POST, parametry przekazywane jako formularz.
 */
class EServerApi(
    baseUrl: String = DEFAULT_BASE_URL,
    client: OkHttpClient? = null,
    private val enforceModernTls: Boolean = true,
) {
    private val baseUrl = baseUrl.trimEnd('/') + "/"

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (enforceModernTls) {
                // Wymagany TLS 1.2 lub nowszy (serwer akceptuje wyłącznie szyfry AEAD: AES-GCM, AES-CCM, CHACHA20).
                connectionSpecs(listOf(
                    ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                        .build()
                ))
            }
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun devices(c: Credentials): List<DeviceDto> =
        json.decodeFromJsonElement(DeviceListResponse.serializer(), post(c, "devices")).deviceList

    suspend fun positions(c: Credentials): List<PositionDto> =
        json.decodeFromJsonElement(PositionListResponse.serializer(), post(c, "positions")).positionList

    suspend fun status(c: Credentials, serial: String, at: ZonedDateTime): MotionStatus {
        val data = dataOf(post(c, "status", mapOf(
            "serial" to serial,
            "date" to ApiDates.format(at),
            "date_format" to ApiDates.FORMAT,
        )))
        return MotionStatus(
            motion = data["motion"].asBoolean(),
            begin = ApiDates.parse(data["begin"]),
            end = ApiDates.parse(data["end"]),
        )
    }

    suspend fun history(
        c: Credentials,
        serial: String,
        from: ZonedDateTime,
        to: ZonedDateTime,
        details: Boolean,
    ): HistorySummary {
        val data = dataOf(post(c, "history", mapOf(
            "serial" to serial,
            "date_from" to ApiDates.format(from),
            "date_to" to ApiDates.format(to),
            "details" to if (details) "1" else "0",
            "date_format" to ApiDates.FORMAT,
        )))
        val entries = (data["details"] as? JsonArray).orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            HistoryEntry(
                motion = o["motion"].asBoolean(),
                begin = ApiDates.parse(o["begin"]),
                end = ApiDates.parse(o["end"]),
                distanceKm = o["distance"].asDouble(),
            )
        }
        return HistorySummary(
            distanceKm = data["distance"].asDouble() ?: 0.0,
            drivingSeconds = data["driving_time"].asLong() ?: 0,
            parkingSeconds = data["parking_time"].asLong() ?: 0,
            entries = entries,
        )
    }

    suspend fun lastReadingDate(c: Credentials, serial: String): ZonedDateTime? {
        val root = post(c, "last_reading_date", mapOf("serial" to serial, "date_format" to ApiDates.FORMAT))
            as? JsonObject ?: return null
        // Dokumentacja podaje {"data": {"<format>": "<data>"}}, przykład pokazuje {"<format>": "<data>"}.
        val container = (root["data"] as? JsonObject) ?: root
        val value = container[ApiDates.FORMAT]
            ?: container.entries.firstOrNull { it.key != "result" && it.value is JsonPrimitive }?.value
        return ApiDates.parse(value)
    }

    private fun dataOf(root: JsonElement): JsonObject {
        val obj = root as? JsonObject ?: throw ApiException.Parse(IllegalStateException("not an object"))
        val result = (obj["result"] as? JsonPrimitive)?.contentOrNull
        if (result != null && !result.equals("OK", ignoreCase = true)) {
            val msg = (obj["message"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["error"] as? JsonPrimitive)?.contentOrNull
                ?: result
            throw ApiException.Server("Serwer zwrócił błąd: $msg")
        }
        return (obj["data"] as? JsonObject) ?: obj
    }

    private suspend fun post(c: Credentials, endpoint: String, params: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder(Charsets.UTF_8).apply { params.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(baseUrl + endpoint)
                .header("Authorization", c.authorizationHeader)
                .header("Accept", "application/json")
                .post(body)
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw ApiException.Network(e)
            }
            response.use { r ->
                when {
                    r.code == 401 || r.code == 403 -> throw ApiException.Unauthorized()
                    !r.isSuccessful -> throw ApiException.Http(r.code)
                }
                val text = try { r.body?.string().orEmpty() } catch (e: IOException) { throw ApiException.Network(e) }
                try {
                    json.parseToJsonElement(text).also { it.jsonObject }
                } catch (e: Exception) {
                    throw ApiException.Parse(e)
                }
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://e-server.com.pl/api/"
    }
}
