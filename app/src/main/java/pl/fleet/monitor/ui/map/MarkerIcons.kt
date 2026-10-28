package pl.fleet.monitor.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.toArgb
import pl.fleet.monitor.data.Vehicle
import pl.fleet.monitor.data.VehicleState
import pl.fleet.monitor.ui.color

/**
 * Rysuje ikonę pojazdu: kółko w kolorze stanu (ze strzałką kierunku podczas jazdy)
 * oraz etykietę z numerem rejestracyjnym pod spodem.
 */
class MarkerIcons(private val context: Context) {

    data class Icon(val drawable: BitmapDrawable, val anchorY: Float)

    private val cache = HashMap<String, Icon>()
    private val density = context.resources.displayMetrics.density

    fun get(v: Vehicle, selected: Boolean): Icon {
        val heading = (v.heading / 10) * 10
        val key = "${v.label}|${v.state}|$heading|$selected"
        return cache.getOrPut(key) { draw(v.label, v.state, heading, selected) }
    }

    private fun draw(label: String, state: VehicleState, heading: Int, selected: Boolean): Icon {
        val d = density
        val radius = (if (selected) 17f else 14f) * d
        val stroke = 2.5f * d
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
        }
        val textW = textPaint.measureText(label)
        val padH = 6f * d
        val labelH = 18f * d
        val gap = 3f * d
        val width = maxOf(radius * 2 + stroke * 2, textW + padH * 2).toInt() + 2
        val circleArea = radius * 2 + stroke * 2
        val height = (circleArea + gap + labelH).toInt() + 2
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = width / 2f
        val cy = stroke + radius
        val stateColor = state.color.toArgb()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stateColor }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke
            color = if (selected) 0xFF0D47A1.toInt() else android.graphics.Color.WHITE
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        c.drawCircle(cx, cy + d, radius + stroke / 2, shadow)
        c.drawCircle(cx, cy, radius, fill)
        c.drawCircle(cx, cy, radius, border)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        if (state == VehicleState.DRIVING) {
            c.save()
            c.rotate(heading.toFloat(), cx, cy)
            val r = radius * 0.62f
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.7f, cy + r * 0.75f)
                lineTo(cx, cy + r * 0.35f)
                lineTo(cx - r * 0.7f, cy + r * 0.75f)
                close()
            }
            c.drawPath(path, white)
            c.restore()
        } else {
            val letter = when (state) {
                VehicleState.IDLE -> "Z"
                VehicleState.PARKED -> "P"
                else -> "?"
            }
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = radius * 1.05f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(letter, cx, cy - (lp.descent() + lp.ascent()) / 2, lp)
        }

        val top = circleArea + gap
        val rect = RectF(cx - textW / 2 - padH, top, cx + textW / 2 + padH, top + labelH)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (selected) 0xFF0D47A1.toInt() else 0xE6263238.toInt() }
        c.drawRoundRect(rect, labelH / 2, labelH / 2, bg)
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        c.drawText(label, rect.left + padH, baseline, textPaint)

        return Icon(BitmapDrawable(context.resources, bmp), cy / height)
    }
}
