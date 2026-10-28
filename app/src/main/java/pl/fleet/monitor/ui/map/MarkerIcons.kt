package pl.fleet.monitor.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.color

/**
 * Rysuje znacznik pojazdu: samochód (widok z przodu) w kolorze stanu – zielony w ruchu,
 * pomarańczowy na postoju – z białym obrysem oraz etykietę z nazwą pojazdu pod spodem.
 */
class MarkerIcons(private val context: Context) {

    data class Icon(val drawable: BitmapDrawable, val anchorY: Float)

    private val cache = HashMap<String, Icon>()

    /** Kolor zaznaczenia – granat, dobrze odróżnia się od zielonego i pomarańczowego. */
    private val SELECTED = 0xFF1E3A8A.toInt()
    private val density = context.resources.displayMetrics.density

    /** Sylwetka samochodu (Material „directions_car”), układ 24×24. */
    private val carPath: Path = PathParser.createPathFromPathData(
        "M18.92,6.01C18.72,5.42 18.16,5 17.5,5h-11c-0.66,0 -1.21,0.42 -1.42,1.01L3,12v8c0,0.55 0.45,1 1,1h1" +
            "c0.55,0 1,-0.45 1,-1v-1h12v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-8l-2.08,-5.99z",
    )
    private val windshieldPath: Path = PathParser.createPathFromPathData("M5,11l1.5,-4.5h11L19,11z")

    fun get(v: Vehicle, selected: Boolean): Icon {
        val label = v.displayName.ifBlank { v.label }
        val key = "$label|${v.state}|$selected"
        return cache.getOrPut(key) { draw(label, v.state, selected) }
    }

    private fun draw(label: String, state: VehicleState, selected: Boolean): Icon {
        val d = density
        val carSize = (if (selected) 38f else 32f) * d
        val outline = 2.5f * d
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (selected) android.graphics.Color.WHITE else 0xFF212121.toInt()
        }
        val textW = textPaint.measureText(label)
        val padH = 7f * d
        val labelH = 20f * d
        val gap = 1f * d
        val carArea = carSize + outline * 2
        val width = maxOf(carArea, textW + padH * 2 + 2 * d).toInt() + 2
        val height = (carArea + gap + labelH + 2 * d).toInt() + 2
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = width / 2f
        val carTop = outline
        val base = state.color.toArgb()

        // Skalowanie ścieżek 24×24 do rozmiaru znacznika
        val scale = carSize / 24f
        val m = Matrix().apply { setScale(scale, scale); postTranslate(cx - carSize / 2, carTop) }
        val car = Path(carPath).apply { transform(m) }
        val glass = Path(windshieldPath).apply { transform(m) }

        // Cień, biały obrys, wypełnienie gradientem
        c.save(); c.translate(0f, 1.5f * d)
        c.drawPath(car, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = outline * 2; strokeJoin = Paint.Join.ROUND; color = 0x40000000
        })
        c.restore()
        c.drawPath(car, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = outline * 2; strokeJoin = Paint.Join.ROUND
            color = if (selected) SELECTED else android.graphics.Color.WHITE
        })
        c.drawPath(car, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, carTop, 0f, carTop + carSize,
                ColorUtils.blendARGB(base, android.graphics.Color.WHITE, 0.25f),
                ColorUtils.blendARGB(base, android.graphics.Color.BLACK, 0.15f),
                Shader.TileMode.CLAMP,
            )
        })
        // Szyba i reflektory
        c.drawPath(glass, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val ly = carTop + 14.5f * scale
        c.drawCircle(cx - carSize / 2 + 6.5f * scale, ly, 1.5f * scale, lightPaint)
        c.drawCircle(cx - carSize / 2 + 17.5f * scale, ly, 1.5f * scale, lightPaint)

        // Etykieta z nazwą pojazdu
        val top = carArea + gap
        val rect = RectF(cx - textW / 2 - padH, top, cx + textW / 2 + padH, top + labelH)
        val r = 6f * d
        c.drawRoundRect(RectF(rect).apply { offset(0f, 1f * d) }, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) SELECTED else android.graphics.Color.WHITE
        })
        if (!selected) {
            c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * d; color = 0x22000000
            })
        }
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        c.drawText(label, rect.left + padH, baseline, textPaint)

        // Kotwica: środek samochodu – tam też rysowane jest pulsowanie
        return Icon(BitmapDrawable(context.resources, bmp), (carTop + carSize / 2) / height)
    }
}
