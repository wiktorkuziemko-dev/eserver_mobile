package pl.fleet.monitor.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Delikatnie pulsujące kręgi pod znacznikami jadących pojazdów.
 * Rysowane pod znacznikami; mapa jest odświeżana co ~33 ms tylko wtedy, gdy są jadące pojazdy.
 */
class PulseOverlay(density: Float, color: Int) : Overlay() {
    var points: List<GeoPoint> = emptyList()

    private val baseRadius = 14f * density
    private val maxGrow = 16f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val projection = mapView.projection
        // Dwie fale przesunięte o pół okresu – efekt płynnego pulsowania.
        for (phaseOffset in longArrayOf(0L, PERIOD / 2)) {
            val t = ((now + phaseOffset) % PERIOD) / PERIOD.toFloat()
            paint.alpha = ((1f - t) * 90).toInt()
            val r = baseRadius + maxGrow * t
            for (p in points) {
                projection.toPixels(p, point)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), r, paint)
            }
        }
        mapView.postInvalidateDelayed(33)
    }

    private companion object {
        const val PERIOD = 1800L
    }
}
