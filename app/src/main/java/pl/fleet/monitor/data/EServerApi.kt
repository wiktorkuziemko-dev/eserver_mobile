package pl.fleet.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    class Network(cause: Throwable) :
        ApiException("Brak połączenia z serwerem lub przekroczony czas odpowiedzi (${cause.javaClass.simpleName}).", cause)
    class Parse(cause: Throwable, snippet: String = "") :
        ApiException("Nieoczekiwany format odpowiedzi serwera." + if (snippet.isNotBlank()) " Odpowiedź: ${snippet.take(160)}" else "", cause)
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
        .readTimeout(90, TimeUnit.SECONDS)
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
        // PHP potrafi zakodować listę jako obiekt {"0": {...}, "2": {...}} – obsługujemy obie formy.
        val detailItems = when (val d = data["details"]) {
            is JsonArray -> d
            is JsonObject -> d.values.toList()
            else -> emptyList()
        }
        val entries = detailItems.mapNotNull { item ->
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

    /**
     * Surowa odpowiedź serwera – na potrzeby ekranu diagnostyki.
     * Zwraca kod HTTP i treść (bez interpretacji), nigdy nie rzuca wyjątku.
     */
    suspend fun raw(c: Credentials, endpoint: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder(Charsets.UTF_8).apply { params.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(baseUrl + endpoint)
                .header("Authorization", c.authorizationHeader)
                .post(body)
                .build()
            try {
                mutex.withLock {
                    http.newCall(request).execute().use { r -> "HTTP ${r.code}\n" + r.body?.string().orEmpty().take(3000) }
                }
            } catch (e: Exception) {
                "BŁĄD: ${e.javaClass.simpleName}: ${e.message}"
            }
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
        return when (val data = obj["data"]) {
            is JsonObject -> data
            is JsonArray -> JsonObject(emptyMap()) // pusta tablica z PHP = brak danych
            else -> obj
        }
    }

    /**
     * Zapytania wykonujemy pojedynczo (serwer bywa wrażliwy na równoległe zapytania z jednego konta)
     * i ponawiamy przy błędach przejściowych: brak sieci, timeout, HTTP 5xx/429, uszkodzona odpowiedź.
     */
    private suspend fun post(c: Credentials, endpoint: String, params: Map<String, String> = emptyMap()): JsonElement {
        var attempt = 0
        while (true) {
            try {
                return mutex.withLock { postOnce(c, endpoint, params) }
            } catch (e: ApiException) {
                val transient = e is ApiException.Network || e is ApiException.Parse ||
                    (e is ApiException.Http && (e.code >= 500 || e.code == 429))
                if (!transient || ++attempt >= MAX_ATTEMPTS) throw e
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun postOnce(c: Credentials, endpoint: String, params: Map<String, String>): JsonElement =
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
                    json.parseToJsonElement(text.trim().removePrefix("\uFEFF")).also { it.jsonObject }
                } catch (e: Exception) {
                    throw ApiException.Parse(e, text)
                }
            }
        }

    private val mutex = Mutex()

    companion object {
        const val DEFAULT_BASE_URL = "https://e-server.com.pl/api/"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 800L
    }
}
