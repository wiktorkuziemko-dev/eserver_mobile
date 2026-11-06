package pl.fleet.monitor.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val T0: Instant = LocalDateTime(2026, 11, 1, 8, 0, 0).toInstant(TimeZone.UTC)

private fun pt(sec: Long, lat: Double, lon: Double, speed: Double, ign: Boolean? = true, addr: String = "") =
    RoutePoint(T0 + sec.seconds, lat, lon, speed, ign, null, addr)

class RoutesTest {
    @BeforeTest fun setUp() {
        ApiDates.zone = TimeZone.UTC
    }

    @Test fun parsesRoutesResponseAndSendsParameters() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val body = """
            {"result":"OK","data":[
              {"serial":"36110001","date":"2026-11-01 10:05:03","speed":12,"ignition":1,"fuel":49,"pos_h":50.30413,"pos_v":18.82012,"address":" Zabrze "},
              {"serial":"36110001","date":"2026-11-01 10:05:01","speed":null,"ignition":0,"fuel":null,"pos_h":"50,3041","pos_v":"18,8201","address":null},
              {"serial":"36110001","date":"2026-11-01 10:05:02","speed":0,"ignition":1,"pos_h":0,"pos_v":0},
              {"serial":"36110001","date":"2026-11-01 10:05:03","speed":99,"ignition":1,"pos_h":50.5,"pos_v":18.9}
            ]}
        """.trimIndent()
        val api = EServerApi("https://test.local/api/", HttpClient(MockEngine { r -> requests += r; respond(body) }))
        val from = LocalDateTime(2026, 11, 1, 0, 0, 0).toInstant(TimeZone.UTC)
        val to = LocalDateTime(2026, 11, 1, 23, 59, 59).toInstant(TimeZone.UTC)

        val points = api.routes(Credentials.fromPlain("jan", "x"), "36110001", from, to)

        val form = (requests.single().body as FormDataContent).formData
        assertTrue(requests.single().url.toString().endsWith("/api/routes"))
        assertEquals("36110001", form["serial"])
        assertEquals("2026-11-01 00:00:00", form["date_from"])
        assertEquals("2026-11-01 23:59:59", form["date_to"])
        assertEquals("local", form["date_format"])
        assertNull(form["details"])

        // 0,0 odrzucone, posortowane po czasie, duplikat czasu usunięty, przecinek dziesiętny obsłużony
        assertEquals(2, points.size)
        assertEquals(50.3041, points[0].lat, 1e-9)
        assertEquals(false, points[0].ignition)
        assertEquals("", points[0].address)
        assertEquals("Zabrze", points[1].address)
        assertEquals(49, points[1].fuel)
        assertTrue(!points[0].speed.isNaN()) // brakująca prędkość wyliczona z pozycji
    }

    @Test fun splitsIntoStopsAndTrips() {
        val points = buildList {
            // postój 10 min w punkcie A (zapłon wyłączony)
            for (k in 0..10) add(pt(k * 60L, 50.0, 19.0, 0.0, ign = false, addr = "Baza"))
            // przejazd ok. 5,6 km na północ w 6 min
            for (k in 1..6) add(pt(600L + k * 60L, 50.0 + k * 0.0083, 19.0, 56.0))
            // postój 20 min w punkcie B, przez 6 min z pracującym silnikiem
            for (k in 1..20) add(pt(960L + k * 60L, 50.0498, 19.0, 0.0, ign = k <= 7, addr = "Klient"))
        }
        val a = analyzeRoute(points)

        assertEquals(2, a.stops.size)
        assertEquals(1, a.trips.size)
        assertEquals("Baza", a.stops[0].address)
        assertEquals(10.minutes.inWholeSeconds, a.stops[0].seconds)
        assertEquals("Klient", a.stops[1].address)
        assertEquals(6.minutes.inWholeSeconds, a.stops[1].idleSeconds)
        val trip = a.trips.single()
        assertEquals(5.5, trip.km, 0.2)
        assertEquals(56.0, trip.maxSpeed)
        assertTrue(a.gaps.isEmpty())
    }

    @Test fun detectsGapInGpsData() {
        val points = listOf(
            pt(0, 50.0, 19.0, 50.0),
            pt(60, 50.007, 19.0, 50.0),
            pt(120, 50.5, 19.5, 50.0), // 65 km w minutę – niemożliwe, przerwa w danych
            pt(180, 50.507, 19.5, 50.0),
        )
        val a = analyzeRoute(points)
        assertEquals(1, a.gaps.size)
        assertEquals(1, a.gaps.single().index)
        assertEquals(2, a.trips.size)
    }

    @Test fun speedBuckets() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(10.0, 45.0, 75.0, 105.0, 130.0).map(::speedBucket))
    }
}

class RoutePlaybackTest {
    @Test fun interpolatesPositionSpeedAndDistance() {
        val points = listOf(
            pt(0, 50.0, 19.0, 0.0),
            pt(60, 50.01, 19.0, 60.0, addr = "A"),
            pt(120, 50.02, 19.0, 40.0, addr = "B"),
        )
        val pb = RoutePlayback(analyzeRoute(points))
        val s = pb.sampleAt(T0 + 90.seconds)!!
        assertEquals(50.015, s.lat, 1e-9)
        assertEquals(50.0, s.speed, 1e-9)
        assertEquals(1.67, s.km, 0.01)
        assertEquals(0.0, s.bearing, 0.5) // na północ
        assertEquals("A", s.address)
        // poza zakresem – przycięte do końców
        assertEquals(50.02, pb.sampleAt(T0 + 1000.seconds)!!.lat, 1e-9)
        assertEquals(50.0, pb.sampleAt(T0 - 10.seconds)!!.lat, 1e-9)
    }
}
