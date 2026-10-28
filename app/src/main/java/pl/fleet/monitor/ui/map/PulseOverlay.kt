package pl.fleet.monitor.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Pulsujące fale wokół znaczników jadących pojazdów (sugerują ruch).
 * Fala startuje tuż przy krawędzi ikony samochodu i rozchodzi się na zewnątrz, zanikając.
 * Rysowane pod znacznikami; mapa odświeża się co ~33 ms tylko wtedy, gdy są jadące pojazdy.
 */
class PulseOverlay(density: Float, color: Int) : Overlay() {
    var points: List<GeoPoint> = emptyList()

    private val startRadius = 17f * density
    private val maxGrow = 22f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.5f * density
    }
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val projection = mapView.projection
        // Dwie fale przesunięte o pół okresu – płynne, ciągłe pulsowanie.
        for (phaseOffset in longArrayOf(0L, PERIOD / 2)) {
            val t = ((now + phaseOffset) % PERIOD) / PERIOD.toFloat()
            val eased = 1f - (1f - t) * (1f - t) // szybki start, łagodne wyhamowanie
            val r = startRadius + maxGrow * eased
            fill.alpha = ((1f - t) * 70).toInt()
            ring.alpha = ((1f - t) * 200).toInt()
            for (p in points) {
                projection.toPixels(p, point)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, fill)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, ring)
            }
        }
        mapView.postInvalidateDelayed(33)
    }

    private companion object {
        const val PERIOD = 1600L
    }
}
