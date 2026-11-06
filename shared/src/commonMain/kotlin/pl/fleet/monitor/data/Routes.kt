package pl.fleet.monitor.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant

/** Punkt trasy z funkcji `routes` (pos_h = szerokość, pos_v = długość geograficzna). */
data class RoutePoint(
    val time: Instant,
    val lat: Double,
    val lon: Double,
    /** Prędkość [km/h]; gdy brak w danych – liczona z sąsiednich pozycji. */
    val speed: Double,
    val ignition: Boolean?,
    val fuel: Int?,
    val address: String,
)

/** Postój: punkty [a]..[b], pozycja to średnia z tych punktów. */
data class RouteStop(
    val a: Int,
    val b: Int,
    val start: Instant,
    val end: Instant,
    val lat: Double,
    val lon: Double,
    /** Czas postoju z włączonym zapłonem (praca silnika na postoju) [s]. */
    val idleSeconds: Long,
    val address: String,
) {
    val seconds: Long get() = (end - start).inWholeSeconds
}

/** Przejazd: punkty [s]..[e]. */
data class RouteTrip(
    val s: Int,
    val e: Int,
    val start: Instant,
    val end: Instant,
    val km: Double,
    val maxSpeed: Double,
    val avgSpeed: Double,
    val from: String,
    val to: String,
) {
    val seconds: Long get() = (end - start).inWholeSeconds
}

/** Przerwa w danych GPS między punktami [index] i [index]+1 (np. prom, brak sygnału). */
data class RouteGap(val index: Int, val km: Double)

data class RouteAnalysis(
    val points: List<RoutePoint>,
    val trips: List<RouteTrip>,
    val stops: List<RouteStop>,
    val gaps: List<RouteGap>,
) {
    val km: Double get() = trips.sumOf { it.km }
    val driveSeconds: Long get() = trips.sumOf { it.seconds }
    val stopSeconds: Long get() = stops.sumOf { it.seconds }
    val maxSpeed: Double get() = points.maxOfOrNull { it.speed } ?: 0.0

    companion object {
        val EMPTY = RouteAnalysis(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Przedziały prędkości do kolorowania trasy: <30, <60, <90, <120, ≥120 km/h. */
fun speedBucket(kmh: Double): Int = when {
    kmh < 30 -> 0
    kmh < 60 -> 1
    kmh < 90 -> 2
    kmh < 120 -> 3
    else -> 4
}

/** Odległość po kole wielkim [km]. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = PI / 180
    val dLa = (lat2 - lat1) * r
    val dLo = (lon2 - lon1) * r
    val x = sin(dLa / 2).let { it * it } + cos(lat1 * r) * cos(lat2 * r) * sin(dLo / 2).let { it * it }
    return 2 * 6371.0 * asin(min(1.0, sqrt(x)))
}

private fun dist(a: RoutePoint, b: RoutePoint) = haversineKm(a.lat, a.lon, b.lat, b.lon)

/**
 * Porządkuje surowe punkty: odrzuca pozycje 0,0 i spoza zakresu, sortuje po czasie,
 * usuwa duplikaty czasu i uzupełnia brakującą prędkość wyliczoną z pozycji.
 */
fun cleanRoutePoints(raw: List<RoutePoint>): List<RoutePoint> {
    val sorted = raw
        .filter { it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 && !(it.lat == 0.0 && it.lon == 0.0) }
        .sortedBy { it.time }
    val p = sorted.filterIndexed { i, x -> i == 0 || x.time != sorted[i - 1].time }
    return p.mapIndexed { i, x ->
        if (!x.speed.isNaN()) return@mapIndexed x
        val a = p[max(0, i - 1)]
        val b = p[min(p.size - 1, i + 1)]
        val hours = (b.time - a.time).inWholeMilliseconds / 3_600_000.0
        x.copy(speed = if (hours > 0) min(250.0, dist(a, b) / hours) else 0.0)
    }
}

/**
 * Podział punktów na przejazdy i postoje – ta sama metoda co w analizatorze CSV:
 *  - odcinek „stoi”, gdy obie prędkości < [stillKmh] i przesunięcie < 250 m,
 *    albo gdy między punktami minęło co najmniej [minStopMinutes] przy przesunięciu < 300 m;
 *  - postój to ciąg takich odcinków trwający co najmniej [minStopMinutes];
 *  - przerwa w danych to skok > 1 km niemożliwy do przejechania (> 220 km/h)
 *    albo luka > 15 min z przesunięciem > 2 km – dzieli przejazd i jest rysowana linią przerywaną;
 *  - przejazdy krótsze niż 100 m są pomijane.
 */
fun analyzeRoute(points: List<RoutePoint>, minStopMinutes: Int = 5, stillKmh: Double = 3.0): RouteAnalysis {
    val p = points
    val n = p.size
    if (n == 0) return RouteAnalysis.EMPTY
    val minMs = minStopMinutes * 60_000L

    val d = DoubleArray(max(0, n - 1))
    val dt = LongArray(max(0, n - 1))
    val still = BooleanArray(max(0, n - 1))
    val gap = BooleanArray(max(0, n - 1))
    for (i in 0 until n - 1) {
        d[i] = dist(p[i], p[i + 1])
        dt[i] = (p[i + 1].time - p[i].time).inWholeMilliseconds
        still[i] = (p[i].speed < stillKmh && p[i + 1].speed < stillKmh && d[i] < 0.25) || (dt[i] >= minMs && d[i] < 0.3)
        val hours = (dt[i] / 3_600_000.0).takeIf { it > 0 } ?: 1e-9
        gap[i] = !still[i] && d[i] > 1 && ((dt[i] > 15 * 60_000L && d[i] > 2) || d[i] / hours > 220)
    }

    val stops = mutableListOf<RouteStop>()
    var i = 0
    while (i < n - 1) {
        if (!still[i]) { i++; continue }
        val a = i
        while (i < n - 1 && still[i]) i++
        val b = i
        if ((p[b].time - p[a].time).inWholeMilliseconds >= minMs) {
            var la = 0.0; var lo = 0.0; var addr = ""
            for (k in a..b) {
                la += p[k].lat; lo += p[k].lon
                if (addr.isEmpty() && p[k].address.isNotEmpty()) addr = p[k].address
            }
            var idle = 0L
            for (k in a until b) if (p[k].ignition == true && p[k + 1].ignition == true) idle += dt[k]
            val cnt = b - a + 1
            stops += RouteStop(a, b, p[a].time, p[b].time, la / cnt, lo / cnt, idle / 1000, addr)
        }
    }

    val trips = mutableListOf<RouteTrip>()
    val gaps = mutableListOf<RouteGap>()
    fun mkTrip(s: Int, e: Int) {
        var km = 0.0
        for (k in s until e) km += d[k]
        if (km < 0.1) return
        var mx = 0.0
        for (k in s..e) mx = max(mx, p[k].speed)
        val hours = (p[e].time - p[s].time).inWholeMilliseconds / 3_600_000.0
        trips += RouteTrip(s, e, p[s].time, p[e].time, km, mx, if (hours > 0) km / hours else 0.0, p[s].address, p[e].address)
    }
    fun mkTrips(s: Int, e: Int) {
        var a = s
        for (k in s until e) if (gap[k]) {
            gaps += RouteGap(k, d[k])
            if (k > a) mkTrip(a, k)
            a = k + 1
        }
        if (e > a) mkTrip(a, e)
    }
    var s = 0
    for (st in stops) {
        if (st.a > s) mkTrips(s, st.a)
        s = st.b
    }
    if (s < n - 1) mkTrips(s, n - 1)
    return RouteAnalysis(p, trips, stops, gaps)
}
