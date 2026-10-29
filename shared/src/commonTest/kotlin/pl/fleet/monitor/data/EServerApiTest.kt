package pl.fleet.monitor.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.io.encoding.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Serwer testowy: kolejne odpowiedzi z kolejki, zapamiętuje otrzymane zapytania. */
private class FakeServer {
    val responses = ArrayDeque<Pair<HttpStatusCode, String>>()
    val requests = mutableListOf<HttpRequestData>()

    fun enqueue(body: String, status: HttpStatusCode = HttpStatusCode.OK) = responses.addLast(status to body)

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        requests += request
        val (status, body) = responses.removeFirst()
        return respond(body, status)
    }

    fun api() = EServerApi("https://test.local/api/", HttpClient(MockEngine { answer(it) }))
}

private fun HttpRequestData.form(name: String): String? = (body as FormDataContent).formData[name]

private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) = LocalDateTime(y, mo, d, h, mi, s).toInstant(TimeZone.UTC)

class EServerApiTest {
    private lateinit var server: FakeServer
    private lateinit var api: EServerApi
    private val creds = Credentials.fromPlain("jan", "tajne")

    @BeforeTest fun setUp() {
        ApiDates.zone = TimeZone.UTC
        server = FakeServer()
        api = server.api()
    }

    @Test fun authorizationHeaderUsesMd5OfPassword() {
        assertEquals("0cc175b9c0f1b6a831c399e269772661", Credentials.md5("a"))
        val decoded = Base64.decode(creds.authorizationHeader.removePrefix("Basic ")).decodeToString()
        assertEquals("jan:${Credentials.md5("tajne")}", decoded)
    }

    @Test fun devicesAndPositionsAreMerged() = runTest {
        server.enqueue("""
            {"deviceList":[{"deviceId":"bc6de239df34a8cabfbecb21fe90f3c8","deviceName":"Ford Focus MK IV 36001039 ZS 514PV","serial":"36001039","plateNo":"ZS 514PV"},
                           {"deviceId":"x2","deviceName":"Bez pozycji","serial":"1","plateNo":"ZS 1"}]}
        """)
        server.enqueue("""
            {"positionList":[{"deviceId":"bc6de239df34a8cabfbecb21fe90f3c8","coordinate":{"latitude":52.3715675,"longitude":16.8624941667},
              "speed":37,"ignitionState":"ON","dateTime":{"year":2024,"month":1,"day":23,"hour":17,"minute":24,"seconds":13,"timezone":"UTC"},
              "serial":"36001039","heading":319,"vehicleMileage":211873}]}
        """)
        val vehicles = mergeVehicles(api.devices(creds), api.positions(creds))
        assertEquals(2, vehicles.size)
        val ford = vehicles.first { it.serial == "36001039" }
        assertEquals("ZS 514PV", ford.label)
        assertEquals("Ford Focus MK IV ZS 514PV", ford.displayName)
        assertEquals(VehicleState.DRIVING, ford.state)
        assertEquals(211873L, ford.mileage)
        assertEquals(utc(2024, 1, 23, 17, 24, 13), ford.lastPositionTime)
        assertEquals(VehicleState.PARKED, vehicles.first { it.deviceId == "x2" }.state)

        val req = server.requests.first()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/api/devices", req.url.encodedPath)
        assertEquals(creds.authorizationHeader, req.headers["Authorization"])
    }

    @Test fun forbiddenMapsToUnauthorized() = runTest {
        server.enqueue("", HttpStatusCode.Forbidden)
        try {
            api.devices(creds); fail("brak wyjątku")
        } catch (e: ApiException.Unauthorized) { /* ok */ }
    }

    @Test fun statusSendsFormParamsAndParsesDates() = runTest {
        server.enqueue("""{"result":"OK","data":{"motion":true,"begin":"2023-05-29 10:26:28","end":null}}""")
        val s = api.status(creds, "02000130", utc(2023, 5, 29, 11, 44, 0))
        assertTrue(s.motion)
        assertEquals(utc(2023, 5, 29, 10, 26, 28), s.begin)
        assertNull(s.end)
        val req = server.requests.single()
        assertEquals("02000130", req.form("serial"))
        assertEquals("2023-05-29 11:44:00", req.form("date"))
        assertEquals("local", req.form("date_format"))
    }

    @Test fun historyWithDetails() = runTest {
        server.enqueue("""
            {"result":"OK","data":{"distance":25349.8,"driving_time":1648817,"parking_time":20559224,"details":[
              {"motion":false,"begin":"2022-12-31 22:35:25","end":"2023-01-01 00:20:46"},
              {"motion":true,"begin":"2023-01-01 00:20:46","end":"2023-01-01 01:02:57","distance":24.6}]}}
        """)
        val h = api.history(creds, "36011882", now(), now(), details = true)
        assertEquals(25349.8, h.distanceKm, 0.001)
        assertEquals(1648817L, h.drivingSeconds)
        assertEquals(2, h.entries.size)
        assertFalse(h.entries[0].motion)
        assertNull(h.entries[0].distanceKm)
        assertEquals(24.6, h.entries[1].distanceKm!!, 0.001)
        assertEquals(2531L, h.entries[1].durationSeconds)
        assertEquals("1", server.requests.single().form("details"))
    }

    @Test fun historyDetailsAsObjectAndEmptyDataArray() = runTest {
        server.enqueue("""
            {"result":"OK","data":{"distance":"12,5","driving_time":"600","parking_time":0,"details":{
              "0":{"motion":"1","begin":"2023-01-01 00:20:46","end":"2023-01-01 00:30:46","distance":"12.5"},
              "2":{"motion":0,"begin":"2023-01-01 00:30:46","end":"2023-01-01 01:00:00"}}}}
        """)
        server.enqueue("""{"result":"OK","data":[]}""")
        val h = api.history(creds, "1", now(), now(), true)
        assertEquals(12.5, h.distanceKm, 0.001)
        assertEquals(2, h.entries.size)
        assertTrue(h.entries[0].motion)
        assertFalse(h.entries[1].motion)
        assertEquals(0, api.history(creds, "1", now(), now(), true).entries.size)
    }

    @Test fun historyErrorResultRaisesException() = runTest {
        server.enqueue("""{"result":"ERROR","message":"No data"}""")
        val e = runCatching { api.history(creds, "x", now(), now(), true) }.exceptionOrNull()
        assertIs<ApiException.Server>(e)
        assertTrue(e.message!!.contains("No data"))
    }

    @Test fun transientServerErrorIsRetried() = runTest {
        server.enqueue("busy", HttpStatusCode.BadGateway)
        server.enqueue("<html>busy</html>")
        server.enqueue("""{"result":"OK","data":{"distance":1,"driving_time":2,"parking_time":3}}""")
        val h = api.history(creds, "1", now(), now(), false)
        assertEquals(2L, h.drivingSeconds)
        assertEquals(3, server.requests.size)
    }

    @Test fun lastReadingDateAcceptsAllShapes() = runTest {
        server.enqueue("""{"local":"2024-01-23 17:24:13"}""")
        server.enqueue("""{"result":"OK","data":{"local":"2024-01-23 17:24:13"}}""")
        server.enqueue("""{"timestamp":1706030653}""")
        val expected = utc(2024, 1, 23, 17, 24, 13)
        repeat(3) { assertEquals(expected, api.lastReadingDate(creds, "1")) }
    }

    @Test fun repositoryPadsSerialToEightDigits() = runTest {
        server.enqueue("""{"result":"OK","data":{"distance":5,"driving_time":60,"parking_time":0,"details":[]}}""")
        val repo = FleetRepository({ api }) { creds }
        repo.history("2000130", now(), now())
        assertEquals("02000130", server.requests.single().form("serial"))
    }

    @Test fun vehicleStateDependsOnSpeedOnly() {
        fun v(ign: Ignition, speed: Int) = Vehicle("i", "", "", "", 1.0, 1.0, 0, speed, ign, null, null)
        assertEquals(VehicleState.DRIVING, v(Ignition.ON, 10).state)
        assertEquals(VehicleState.DRIVING, v(Ignition.OFF, 30).state)
        assertEquals(VehicleState.PARKED, v(Ignition.ON, 0).state)
        assertEquals("02000130", normalizeSerial("2000130"))
        assertEquals("ABC123", normalizeSerial("ABC123"))
    }
}
