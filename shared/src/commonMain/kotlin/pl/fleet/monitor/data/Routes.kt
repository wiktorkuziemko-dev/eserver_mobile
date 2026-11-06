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
    /** Paliwo [l]. */
    val fuel: Double?,
    val address: String,
    /** Zdarzenie wykryte przez nadajnik (pole i_event): 'S' – start trasy, 'T' – koniec trasy. */
    val event: Char? = null,
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
    // Nadajnik sam wykrywa start (S) i koniec (T) trasy – z zapłonu albo żyroskopu. Gdy podaje te zdarzenia,
    // to one wyznaczają trasy i postoje; analiza prędkości zostaje dla nadajników bez i_event.
    if (points.any { it.event == 'S' || it.event == 'T' }) return analyzeByEvents(points)
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

/** Stan pojazdu w danej chwili odtwarzania przejazdu. */
data class RouteSample(
    val time: Instant,
    val lat: Double,
    val lon: Double,
    val speed: Double,
    /** Kierunek jazdy [°], 0 = północ. */
    val bearing: Double,
    val ignition: Boolean?,
    /** Paliwo [l]. */
    val fuel: Double?,
    /** Dystans przejechany od początku okresu [km] (bez przerw w danych). */
    val km: Double,
    val address: String,
    /** Indeks bieżącej trasy albo postoju (gdy pojazd stoi). */
    val trip: Int?,
    val stop: Int?,
)

/** Kierunek z punktu a do b [°]. */
fun bearingDeg(a: RoutePoint, b: RoutePoint): Double {
    val r = PI / 180
    val y = sin((b.lon - a.lon) * r) * cos(b.lat * r)
    val x = cos(a.lat * r) * sin(b.lat * r) - sin(a.lat * r) * cos(b.lat * r) * cos((b.lon - a.lon) * r)
    return (kotlin.math.atan2(y, x) / r + 360) % 360
}

/** Odtwarzanie przejazdu: pozycja, prędkość i kierunek interpolowane między punktami. */
class RoutePlayback(val analysis: RouteAnalysis) {
    private val p = analysis.points
    private val gapIdx = analysis.gaps.map { it.index }.toSet()

    /** Dystans narastająco w punktach (przerwy w danych się nie liczą). */
    private val cumKm = DoubleArray(p.size).also { c ->
        for (k in 1 until p.size) c[k] = c[k - 1] + if ((k - 1) in gapIdx) 0.0 else dist(p[k - 1], p[k])
    }

    /** Kierunek dla każdego odcinka: z najbliższego ruchu (> 5 m), żeby strzałka nie wracała na północ na postoju. */
    private val bearings = DoubleArray(max(0, p.size - 1)).also { b ->
        var last = 0.0
        for (k in 0 until p.size - 1) {
            if (dist(p[k], p[k + 1]) > 0.005) last = bearingDeg(p[k], p[k + 1])
            b[k] = last
        }
    }

    val start: Instant? get() = p.firstOrNull()?.time
    val end: Instant? get() = p.lastOrNull()?.time

    /** Postój obejmujący chwilę [t] (do pomijania postojów podczas odtwarzania). */
    fun stopAt(t: Instant): RouteStop? = analysis.stops.firstOrNull { t >= it.start && t < it.end }

    fun sampleAt(t: Instant): RouteSample? {
        if (p.isEmpty()) return null
        val time = t.coerceIn(p.first().time, p.last().time)
        // ostatni punkt z czasem <= t
        var lo = 0
        var hi = p.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (p[mid].time <= time) lo = mid else hi = mid - 1
        }
        val k = lo
        val a = p[k]
        val b = p.getOrNull(k + 1)
        val total = b?.let { (it.time - a.time).inWholeMilliseconds } ?: 0
        val f = if (b == null || total <= 0 || k in gapIdx) 0.0 else (time - a.time).inWholeMilliseconds.toDouble() / total
        val lat = if (b == null) a.lat else a.lat + (b.lat - a.lat) * f
        val lon = if (b == null) a.lon else a.lon + (b.lon - a.lon) * f
        val speed = if (b == null) a.speed else a.speed + (b.speed - a.speed) * f
        val km = cumKm[k] + if (b == null) 0.0 else (cumKm[k + 1] - cumKm[k]) * f
        val trip = analysis.trips.indexOfFirst { k >= it.s && k < it.e }.takeIf { it >= 0 }
        val stop = if (trip == null) analysis.stops.indexOfFirst { k >= it.a && k < it.b }.takeIf { it >= 0 } else null
        return RouteSample(
            time = time, lat = lat, lon = lon, speed = speed,
            bearing = bearings.getOrElse(min(k, bearings.size - 1)) { 0.0 },
            ignition = a.ignition, fuel = a.fuel, km = km,
            address = a.address.ifEmpty { b?.address.orEmpty() },
            trip = trip, stop = stop,
        )
    }
}

/** Krótki postój w trakcie trasy (np. korek, wysiadka), krótszy niż postój dzielący trasy. */
data class RoutePause(
    val a: Int,
    val b: Int,
    val start: Instant,
    val end: Instant,
    val lat: Double,
    val lon: Double,
    val address: String,
) {
    val seconds: Long get() = (end - start).inWholeSeconds
}

/**
 * Postoje wewnątrz trasy: ciągi punktów z prędkością < [stillKmh] i przesunięciem < 250 m
 * trwające co najmniej [minSeconds] (krótsze, np. światła, pomijamy).
 */
fun RouteAnalysis.pausesIn(trip: RouteTrip, minSeconds: Long = 120, stillKmh: Double = 3.0): List<RoutePause> {
    val p = points
    val out = mutableListOf<RoutePause>()
    var k = trip.s
    while (k < trip.e) {
        val still = p[k].speed < stillKmh && p[k + 1].speed < stillKmh && dist(p[k], p[k + 1]) < 0.25
        if (!still) { k++; continue }
        val a = k
        while (k < trip.e && p[k].speed < stillKmh && p[k + 1].speed < stillKmh && dist(p[k], p[k + 1]) < 0.25) k++
        val b = k
        if ((p[b].time - p[a].time).inWholeSeconds >= minSeconds) {
            val cnt = b - a + 1
            out += RoutePause(
                a, b, p[a].time, p[b].time,
                (a..b).sumOf { p[it].lat } / cnt, (a..b).sumOf { p[it].lon } / cnt,
                (a..b).firstNotNullOfOrNull { p[it].address.takeIf(String::isNotEmpty) }.orEmpty(),
            )
        }
    }
    return out
}

/** Odległości i przerwy w danych między kolejnymi punktami (wspólne dla obu metod). */
private class Edges(p: List<RoutePoint>) {
    val d = DoubleArray(max(0, p.size - 1)) { dist(p[it], p[it + 1]) }
    val gap = BooleanArray(d.size) { i ->
        val dtMs = (p[i + 1].time - p[i].time).inWholeMilliseconds
        val hours = (dtMs / 3_600_000.0).takeIf { it > 0 } ?: 1e-9
        d[i] > 1 && ((dtMs > 15 * 60_000L && d[i] > 2) || d[i] / hours > 220)
    }
}

/**
 * Trasy według zdarzeń nadajnika: od punktu „S” do najbliższego „T”.
 *  - start bez końca (trasa trwa na końcu okresu) – do ostatniego punktu;
 *  - koniec bez startu (okres zaczyna się w trakcie jazdy) – od pierwszego punktu;
 *  - trasy krótsze niż 100 m (np. samo uruchomienie silnika) są pomijane i stają się częścią postoju;
 *  - trasy rozdzielone postojem krótszym niż [minStopSeconds] są łączone;
 *  - postój to wszystko pomiędzy trasami (także przed pierwszą i po ostatniej).
 */
fun analyzeByEvents(points: List<RoutePoint>, minStopSeconds: Long = 60): RouteAnalysis {
    val p = points
    val n = p.size
    if (n == 0) return RouteAnalysis.EMPTY
    val edges = Edges(p)
    fun km(s: Int, e: Int): Double {
        var x = 0.0
        for (k in s until e) if (!edges.gap[k]) x += edges.d[k]
        return x
    }

    // Pary S…T
    val raw = mutableListOf<Pair<Int, Int>>()
    var open: Int? = null
    for (k in 0 until n) {
        when (p[k].event) {
            'S' -> if (open == null) open = k
            'T' -> {
                val s = open ?: if (raw.isEmpty()) 0 else null
                if (s != null && k > s) raw += s to k
                open = null
            }
        }
    }
    open?.let { if (n - 1 > it) raw += it to n - 1 }

    // Odrzucenie tras < 100 m i łączenie tras rozdzielonych bardzo krótkim postojem
    val merged = mutableListOf<Pair<Int, Int>>()
    for ((s, e) in raw.filter { (s, e) -> km(s, e) >= 0.1 }) {
        val last = merged.lastOrNull()
        if (last != null && (p[s].time - p[last.second].time).inWholeSeconds < minStopSeconds) {
            merged[merged.size - 1] = last.first to e
        } else {
            merged += s to e
        }
    }

    val trips = merged.map { (s, e) ->
        var mx = 0.0
        for (k in s..e) mx = max(mx, p[k].speed)
        val dist = km(s, e)
        val hours = (p[e].time - p[s].time).inWholeMilliseconds / 3_600_000.0
        RouteTrip(s, e, p[s].time, p[e].time, dist, mx, if (hours > 0) dist / hours else 0.0, p[s].address, p[e].address)
    }

    val stops = mutableListOf<RouteStop>()
    fun addStop(a: Int, b: Int) {
        if (b <= a || p[b].time <= p[a].time) return
        // Pozycja postoju: miejsce zakończenia trasy (punkt „T”), a gdy go brak – średnia z punktów postoju.
        val cnt = b - a + 1
        val lat = if (a > 0) p[a].lat else (a..b).sumOf { p[it].lat } / cnt
        val lon = if (a > 0) p[a].lon else (a..b).sumOf { p[it].lon } / cnt
        var idle = 0L
        for (k in a until b) if (p[k].ignition == true && p[k + 1].ignition == true) idle += (p[k + 1].time - p[k].time).inWholeMilliseconds
        val addr = (a..b).firstNotNullOfOrNull { p[it].address.takeIf(String::isNotEmpty) }.orEmpty()
        stops += RouteStop(a, b, p[a].time, p[b].time, lat, lon, idle / 1000, addr)
    }
    if (trips.isEmpty()) {
        addStop(0, n - 1)
    } else {
        if (trips.first().s > 0) addStop(0, trips.first().s)
        trips.zipWithNext().forEach { (x, y) -> addStop(x.e, y.s) }
        if (trips.last().e < n - 1) addStop(trips.last().e, n - 1)
    }

    val gaps = trips.flatMap { tr -> (tr.s until tr.e).filter { edges.gap[it] }.map { RouteGap(it, edges.d[it]) } }
    return RouteAnalysis(p, trips, stops, gaps)
}
