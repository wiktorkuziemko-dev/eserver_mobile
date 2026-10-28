package pl.fleet.monitor.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64

class EServerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: EServerApi
    private val creds = Credentials.fromPlain("jan", "tajne")

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = EServerApi(server.url("/api/").toString(), enforceModernTls = false)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `authorization header uses md5 of password`() {
        assertEquals(32, creds.passwordMd5.length)
        assertEquals("0cc175b9c0f1b6a831c399e269772661", Credentials.md5("a"))
        val decoded = String(Base64.getDecoder().decode(creds.authorizationHeader.removePrefix("Basic ")))
        assertEquals("jan:${Credentials.md5("tajne")}", decoded)
    }

    @Test fun `devices and positions are merged`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"deviceList":[{"deviceId":"bc6de239df34a8cabfbecb21fe90f3c8","deviceName":"Ford Focus MK IV 36001039 ZS 514PV","serial":"36001039","plateNo":"ZS 514PV"},
                           {"deviceId":"x2","deviceName":"Bez pozycji","serial":"1","plateNo":"ZS 1"}]}
        """))
        server.enqueue(MockResponse().setBody("""
            {"positionList":[{"deviceId":"bc6de239df34a8cabfbecb21fe90f3c8","coordinate":{"latitude":52.3715675,"longitude":16.8624941667},
              "speed":37,"ignitionState":"ON","dateTime":{"year":2024,"month":1,"day":23,"hour":17,"minute":24,"seconds":13,"timezone":"UTC"},
              "serial":"36001039","heading":319,"vehicleMileage":211873}]}
        """))
        val vehicles = mergeVehicles(api.devices(creds), api.positions(creds))
        assertEquals(2, vehicles.size)
        val ford = vehicles.first { it.serial == "36001039" }
        assertEquals("ZS 514PV", ford.label)
        assertEquals(VehicleState.DRIVING, ford.state)
        assertEquals(319, ford.heading)
        assertEquals(211873L, ford.mileage)
        assertEquals(ZonedDateTime.of(2024, 1, 23, 17, 24, 13, 0, ZoneOffset.UTC).toInstant(), ford.lastPositionTime!!.toInstant())
        assertEquals(VehicleState.UNKNOWN, vehicles.first { it.deviceId == "x2" }.state)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/devices", req.path)
        assertEquals(creds.authorizationHeader, req.getHeader("Authorization"))
    }

    @Test fun `403 maps to Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            api.devices(creds); fail()
        } catch (e: ApiException.Unauthorized) { /* ok */ }
    }

    @Test fun `status sends form params and parses dates`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":"OK","data":{"motion":true,"begin":"2023-05-29 10:26:28","end":"2023-05-29 12:36:00"}}"""))
        val at = ZonedDateTime.of(2023, 5, 29, 13, 44, 0, 0, ZoneOffset.ofHours(2))
        val s = api.status(creds, "36001039", at)
        assertTrue(s.motion)
        assertEquals(ZonedDateTime.of(2023, 5, 29, 10, 26, 28, 0, ZoneOffset.UTC), s.begin)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("serial=36001039"))
        assertTrue(body, body.contains("date=2023-05-29%2011%3A44%3A00") || body.contains("date=2023-05-29+11%3A44%3A00"))
        assertTrue(body, body.contains("date_format=utc"))
    }

    @Test fun `history with details`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"result":"OK","data":{"distance":25349.8,"driving_time":1648817,"parking_time":20559224,"details":[
              {"motion":false,"begin":"2022-12-31 22:35:25","end":"2023-01-01 00:20:46"},
              {"motion":true,"begin":"2023-01-01 00:20:46","end":"2023-01-01 01:02:57","distance":24.6}]}}
        """))
        val h = api.history(creds, "36011882", ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), details = true)
        assertEquals(25349.8, h.distanceKm, 0.001)
        assertEquals(1648817L, h.drivingSeconds)
        assertEquals(2, h.entries.size)
        assertFalse(h.entries[0].motion)
        assertNull(h.entries[0].distanceKm)
        assertEquals(24.6, h.entries[1].distanceKm!!, 0.001)
        assertEquals(2531L, h.entries[1].durationSeconds)
        assertTrue(server.takeRequest().body.readUtf8().contains("details=1"))
    }

    @Test fun `history error result raises exception`() = runTest {
        server.enqueue(MockResponse().setBody("""{"result":"ERROR","message":"Nieprawidłowy numer"}"""))
        try {
            api.history(creds, "x", ZonedDateTime.now(), ZonedDateTime.now(), true); fail()
        } catch (e: ApiException.Server) {
            assertTrue(e.message!!.contains("Nieprawidłowy numer"))
        }
    }

    @Test fun `last reading date accepts both documented shapes`() = runTest {
        server.enqueue(MockResponse().setBody("""{"utc":"2024-01-23 17:24:13"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"OK","data":{"utc":"2024-01-23 17:24:13"}}"""))
        server.enqueue(MockResponse().setBody("""{"result":"OK","data":{"timestamp":1706030653}}"""))
        val expected = ZonedDateTime.of(2024, 1, 23, 17, 24, 13, 0, ZoneOffset.UTC).toInstant()
        assertEquals(expected, api.lastReadingDate(creds, "1")!!.toInstant())
        assertEquals(expected, api.lastReadingDate(creds, "1")!!.toInstant())
        assertEquals(expected, api.lastReadingDate(creds, "1")!!.toInstant())
    }

    @Test fun `vehicle state rules`() {
        fun v(ign: Ignition, speed: Int) = Vehicle("i", "", "", "", 1.0, 1.0, 0, speed, ign, null, null)
        assertEquals(VehicleState.DRIVING, v(Ignition.ON, 10).state)
        assertEquals(VehicleState.IDLE, v(Ignition.ON, 0).state)
        assertEquals(VehicleState.PARKED, v(Ignition.OFF, 0).state)
        assertEquals(VehicleState.UNKNOWN, v(Ignition.UNKNOWN, 0).state)
    }
}
