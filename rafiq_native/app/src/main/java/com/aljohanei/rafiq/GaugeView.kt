package com.aljohanei.rafiq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * عداد السرعة: يُرسم فقط عند تغيّر الرقم أو الاتجاه (بدون حركة مستمرة)، وبدون صور.
 * الأشكال: 0 قوس، 1 حلقة، 2 شريط، 3 إبرة كلاسيكية، 4 قوس مقطّع، 5 نصف دائرة، 6 رقمي، 7 عداد وبوصلة.
 * مقاس الرسم 528×544 ويُصغَّر ليناسب المساحة المتاحة.
 */
class GaugeView(c: Context) : View(c) {
    var pal: Palette = Palette.DARK
    var mode = 0
    var max = 240
    var alert = 120
    var showMax = true
    private var speed = 0
    private var gps = false
    private var heading = -1f
    private var topSpeed = 0

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val flat = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 20f }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 22f }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val r = RectF()

    private var cx = 264f
    private var cy = 272f
    private var rad = 220f
    private var a0 = 135f
    private var sweep = 270f

    fun set(newSpeed: Int, hasGps: Boolean, newHeading: Float, newTop: Int) {
        val h = if (mode == 7) newHeading else -1f
        val top = if (showMax) newTop else 0
        if (newSpeed == speed && hasGps == gps && Math.abs(h - heading) < 3f && top == topSpeed) return
        speed = newSpeed; gps = hasGps; heading = h; topSpeed = top
        invalidate()
    }

    private fun ang(v: Float) = a0 + sweep * (v / max)
    private fun px(radius: Float, deg: Float) = cx + radius * cos(Math.toRadians(deg.toDouble())).toFloat()
    private fun py(radius: Float, deg: Float) = cy + radius * sin(Math.toRadians(deg.toDouble())).toFloat()

    override fun onDraw(canvas: Canvas) {
        // مقاس الرسم ثابت ويُصغَّر إن قلّت المساحة (مثل القائمة السفلية الكبيرة)
        val s = min(width / 528f, height / 544f)
        canvas.save()
        canvas.translate((width - 528f * s) / 2f, 0f)
        canvas.scale(s, s)
        cx = 264f; cy = 272f; rad = 220f; a0 = 135f; sweep = 270f
        val ratio = (speed.toFloat() / max).coerceIn(0f, 1f)
        val danger = max * 0.8f
        val fillColor = if (speed > danger) pal.danger else pal.accent
        var numY = 214f; var numSize = 124f; var chipTop = 266f

        when (mode) {
            1 -> {
                oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
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
            3 -> {
                // إبرة كلاسيكية مثل عداد الكورولا
                oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
                stroke.strokeWidth = 4f
                stroke.color = pal.line; canvas.drawArc(oval, a0, sweep, false, stroke)
                stroke.strokeWidth = 8f
                stroke.color = pal.danger; canvas.drawArc(oval, ang(danger), sweep * 0.2f, false, stroke)
                var v = 0
                while (v <= max) {
                    val d = ang(v.toFloat())
                    val major = v % 20 == 0
                    val col = if (v > danger) pal.danger else if (major) pal.text else pal.muted
                    tick.color = col; tick.strokeWidth = if (major) 4f else 2f
                    val inner = if (major) rad - 30f else rad - 16f
                    canvas.drawLine(px(inner, d), py(inner, d), px(rad - 4f, d), py(rad - 4f, d), tick)
                    if (major) { label.color = col; canvas.drawText(v.toString(), px(rad - 54f, d), py(rad - 54f, d) + 7f, label) }
                    v += 10
                }
                drawAlertDot(canvas); drawMaxMark(canvas)
                numY = 176f; numSize = 84f
                val d = ang(speed.toFloat().coerceIn(0f, max.toFloat()))
                tick.color = fillColor; tick.strokeWidth = 7f
                canvas.drawLine(px(-26f, d), py(-26f, d), px(rad - 22f, d), py(rad - 22f, d), tick)
                fill.color = pal.card; canvas.drawCircle(cx, cy, 16f, fill)
                chip.color = fillColor; chip.strokeWidth = 4f; canvas.drawCircle(cx, cy, 16f, chip); chip.strokeWidth = 1.5f
                chipTop = 300f
            }
            4 -> {
                // قوس مقطّع: شرائح تضيء بالتدريج
                oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
                flat.strokeWidth = 24f
                val n = 48
                val seg = sweep / n
                for (i in 0 until n) {
                    val start = a0 + i * seg
                    val lit = (i + 0.5f) / n <= ratio
                    val inDanger = (i + 0.5f) / n > 0.8f
                    flat.color = when {
                        lit && inDanger -> pal.danger
                        lit -> pal.accent
                        inDanger -> pal.dangerDim
                        else -> pal.line
                    }
                    canvas.drawArc(oval, start + 0.8f, seg - 1.6f, false, flat)
                }
                var v = 0
                while (v <= max) {
                    val d = ang(v.toFloat())
                    label.color = if (v > danger) pal.danger else pal.muted
                    canvas.drawText(v.toString(), px(rad - 44f, d), py(rad - 44f, d) + 7f, label)
                    v += 40
                }
                drawAlertDot(canvas); drawMaxMark(canvas)
            }
            5 -> {
                // نصف دائرة في الأعلى
                cy = 300f; rad = 228f; a0 = 180f; sweep = 180f
                oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
                stroke.strokeWidth = 16f
                stroke.color = pal.line; canvas.drawArc(oval, a0, sweep, false, stroke)
                stroke.color = pal.dangerDim; canvas.drawArc(oval, ang(danger), sweep * 0.2f, false, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawArc(oval, a0, sweep * ratio, false, stroke)
                var v = 0
                while (v <= max) {
                    val d = ang(v.toFloat())
                    val col = if (v > danger) pal.danger else pal.muted
                    tick.color = col; tick.strokeWidth = 2f; label.color = col
                    canvas.drawLine(px(rad - 30f, d), py(rad - 30f, d), px(rad - 14f, d), py(rad - 14f, d), tick)
                    canvas.drawText(v.toString(), px(rad - 52f, d), py(rad - 52f, d) + 7f, label)
                    v += 40
                }
                drawAlertDot(canvas); drawMaxMark(canvas)
                numY = 250f; numSize = 110f; chipTop = 280f
            }
            6 -> {
                // رقمي: أوضح شكل أثناء القيادة
                numY = 250f; numSize = 220f; chipTop = 30f
                val y = 304f; val x0 = 70f; val x1 = 458f
                stroke.strokeWidth = 10f
                stroke.color = pal.line; canvas.drawLine(x0, y, x1, y, stroke)
                stroke.color = pal.dangerDim; canvas.drawLine(x0 + (x1 - x0) * 0.8f, y, x1, y, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawLine(x0, y, x0 + (x1 - x0) * ratio, y, stroke)
            }
            else -> {
                oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
                stroke.strokeWidth = 14f
                stroke.color = pal.line; canvas.drawArc(oval, a0, sweep, false, stroke)
                stroke.color = pal.dangerDim; canvas.drawArc(oval, ang(danger), sweep * 0.2f, false, stroke)
                stroke.color = fillColor; if (ratio > 0f) canvas.drawArc(oval, a0, sweep * ratio, false, stroke)
                var v = 0
                while (v <= max) {
                    val d = ang(v.toFloat())
                    val col = if (v > danger) pal.danger else pal.muted
                    tick.color = col; tick.strokeWidth = 2f; label.color = col
                    canvas.drawLine(px(rad - 26f, d), py(rad - 26f, d), px(rad - 12f, d), py(rad - 12f, d), tick)
                    canvas.drawText(v.toString(), px(rad - 48f, d), py(rad - 48f, d) + 7f, label)
                    v += 20
                }
                drawAlertDot(canvas); drawMaxMark(canvas)
                if (mode == 7) drawCompass(canvas)
            }
        }

        big.color = pal.text; big.textSize = numSize
        canvas.drawText(speed.toString(), cx, numY, big)
        small.color = pal.muted; small.textSize = 22f
        canvas.drawText("كم/س", cx, numY + 36f, small)
        drawChips(canvas, chipTop)
        canvas.restore()
    }

    private fun drawAlertDot(canvas: Canvas) {
        if (alert in 1..max) {
            fill.color = pal.amber
            val d = ang(alert.toFloat())
            canvas.drawCircle(px(rad + 14f, d), py(rad + 14f, d), 5f, fill)
        }
    }

    /** علامة أعلى سرعة في الرحلة */
    private fun drawMaxMark(canvas: Canvas) {
        if (topSpeed <= 0) return
        val d = ang(topSpeed.toFloat().coerceAtMost(max.toFloat()))
        tick.color = pal.text; tick.strokeWidth = 3f
        canvas.drawLine(px(rad + 6f, d), py(rad + 6f, d), px(rad + 20f, d), py(rad + 20f, d), tick)
    }

    /** حلقة الاتجاهات حول العداد تدور مع اتجاه السيارة (الأعلى = اتجاه السير) */
    private fun drawCompass(canvas: Canvas) {
        val ring = rad + 30f
        chip.color = pal.line; canvas.drawCircle(cx, cy, ring, chip)
        val names = arrayOf("ش", "ق", "ج", "غ")
        val h = if (heading >= 0f) heading else 0f
        for (i in 0..3) {
            val d = i * 90f - 90f - h
            val x = px(ring, d); val y = py(ring, d)
            fill.color = pal.bg; canvas.drawCircle(x, y, 15f, fill)
            label.color = if (i == 0) pal.danger else pal.text
            canvas.drawText(names[i], x, y + 7f, label)
        }
    }

    private fun drawChips(canvas: Canvas, top: Float) {
        small.textSize = 16f
        val gpsText = if (gps) "● GPS متصل" else "○ GPS غير متصل"
        val alertText = if (alert > 0) "تنبيه عند $alert" else "التنبيه متوقف"
        val w1 = small.measureText(gpsText) + 28f
        val w2 = small.measureText(alertText) + 28f
        var x = cx - (w1 + w2 + 10f) / 2
        chip.color = pal.line
        r.set(x, top, x + w1, top + 30f); canvas.drawRoundRect(r, 15f, 15f, chip)
        small.color = if (gps) pal.accent else pal.muted
        canvas.drawText(gpsText, x + w1 / 2, top + 21f, small)
        x += w1 + 10f
        r.set(x, top, x + w2, top + 30f); canvas.drawRoundRect(r, 15f, 15f, chip)
        small.color = pal.muted
        canvas.drawText(alertText, x + w2 / 2, top + 21f, small)
    }
}
