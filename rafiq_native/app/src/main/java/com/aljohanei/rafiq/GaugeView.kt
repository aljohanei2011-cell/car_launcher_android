package com.aljohanei.rafiq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * عداد السرعة: يُرسم فقط عند تغيّر الرقم (بدون حركة مستمرة).
 * ثلاثة أشكال: قوس، حلقة، شريط. منطقة الخطر (آخر 20%) ظاهرة دائماً بلون خافت.
 * مقاس التصميم 528×544.
 */
class GaugeView(c: Context) : View(c) {
    var pal: Palette = Palette.DARK
    var mode = 0
    var max = 240
    var alert = 120
    private var speed = 0
    private var gps = false

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 20f }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 124f; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 22f }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val r = RectF()

    private val cx = 264f
    private val cy = 272f
    private val rad = 220f
    private val a0 = 135f
    private val sweep = 270f

    fun set(newSpeed: Int, hasGps: Boolean) {
        if (newSpeed == speed && hasGps == gps) return
        speed = newSpeed; gps = hasGps
        invalidate()
    }

    private fun ang(v: Float) = a0 + sweep * (v / max)
    private fun px(radius: Float, deg: Float) = cx + radius * cos(Math.toRadians(deg.toDouble())).toFloat()
    private fun py(radius: Float, deg: Float) = cy + radius * sin(Math.toRadians(deg.toDouble())).toFloat()

    override fun onDraw(canvas: Canvas) {
        val ratio = (speed.toFloat() / max).coerceIn(0f, 1f)
        val danger = max * 0.8f
        val fillColor = if (speed > danger) pal.danger else pal.accent
        oval.set(cx - rad, cy - rad, cx + rad, cy + rad)

        when (mode) {
            1 -> {
                stroke.strokeWidth = 18f
                stroke.color = pal.line; canvas.drawArc(oval, 0f, 360f, false, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawArc(oval, -90f, 360f * ratio, false, stroke)
            }
            2 -> {
                stroke.strokeWidth = 18f
                val y = 318f; val x0 = 60f; val x1 = 468f
                stroke.color = pal.line; canvas.drawLine(x0, y, x1, y, stroke)
                stroke.color = pal.dangerDim; canvas.drawLine(x0 + (x1 - x0) * 0.8f, y, x1, y, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawLine(x0, y, x0 + (x1 - x0) * ratio, y, stroke)
                label.color = pal.muted
                canvas.drawText("0", x0, y - 20f, label)
                canvas.drawText(max.toString(), x1, y - 20f, label)
            }
            else -> {
                stroke.strokeWidth = 14f
                stroke.color = pal.line; canvas.drawArc(oval, a0, sweep, false, stroke)
                stroke.color = pal.dangerDim; canvas.drawArc(oval, ang(danger), sweep * 0.2f, false, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawArc(oval, a0, sweep * ratio, false, stroke)
                var v = 0
                while (v <= max) {
                    val d = ang(v.toFloat())
                    val col = if (v > danger) pal.danger else pal.muted
                    tick.color = col; label.color = col
                    canvas.drawLine(px(rad - 26f, d), py(rad - 26f, d), px(rad - 12f, d), py(rad - 12f, d), tick)
                    canvas.drawText(v.toString(), px(rad - 48f, d), py(rad - 48f, d) + 7f, label)
                    v += 20
                }
                if (alert in 1..max) {
                    dot.color = pal.amber
                    val d = ang(alert.toFloat())
                    canvas.drawCircle(px(rad + 14f, d), py(rad + 14f, d), 5f, dot)
                }
            }
        }

        big.color = pal.text
        canvas.drawText(speed.toString(), cx, 214f, big)
        small.color = pal.muted
        canvas.drawText("كم/س", cx, 250f, small)

        // شارتان: حالة GPS وسرعة التنبيه
        small.textSize = 16f
        val gpsText = if (gps) "● GPS متصل" else "○ GPS غير متصل"
        val alertText = if (alert > 0) "تنبيه عند $alert" else "التنبيه متوقف"
        val w1 = small.measureText(gpsText) + 28f
        val w2 = small.measureText(alertText) + 28f
        val total = w1 + w2 + 10f
        var x = cx - total / 2
        val top = 266f
        chip.color = pal.line
        r.set(x, top, x + w1, top + 30f); canvas.drawRoundRect(r, 15f, 15f, chip)
        small.color = if (gps) pal.accent else pal.muted
        canvas.drawText(gpsText, x + w1 / 2, top + 21f, small)
        x += w1 + 10f
        r.set(x, top, x + w2, top + 30f); canvas.drawRoundRect(r, 15f, 15f, chip)
        small.color = pal.muted
        canvas.drawText(alertText, x + w2 / 2, top + 21f, small)
        small.textSize = 22f
    }
}
