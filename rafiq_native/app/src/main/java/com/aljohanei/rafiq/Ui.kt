package com.aljohanei.rafiq

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** أدوات صغيرة لبناء الواجهة بالكود (بدون ملفات تخطيط ولا مكتبات). الأحجام بوحدات التصميم 1280×720 */
object Ui {
    /** مضاعف حجم الخط (1 عادي، 1.1 كبير) يُضبط قبل بناء الواجهة */
    var fontScale = 1f

    fun rounded(fill: Int, radius: Float, strokeW: Int = 0, strokeC: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = radius
            if (strokeW > 0) setStroke(strokeW, strokeC)
        }

    /** خلفية البطاقة: لون صلب، حدّ رفيع، وخط علوي بلون التمييز */
    fun cardBg(p: Palette): Drawable {
        val top = rounded(p.accent, 14f)
        val body = rounded(p.card, 14f, 2, p.line)
        return LayerDrawable(arrayOf<Drawable>(top, body)).apply { setLayerInset(1, 0, 3, 0, 0) }
    }

    fun tv(c: Context, size: Float, color: Int, bold: Boolean = false, gravity: Int = Gravity.RIGHT): TextView =
        TextView(c).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * fontScale)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

    fun icon(c: Context, res: Int, tint: Int? = null): ImageView = ImageView(c).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.FIT_CENTER
        if (tint != null) imageTintList = ColorStateList.valueOf(tint)
    }

    fun abs(w: Int, h: Int, left: Int, top: Int) = FrameLayout.LayoutParams(w, h).apply { leftMargin = left; topMargin = top }
    fun at(w: Int, h: Int, gravity: Int) = FrameLayout.LayoutParams(w, h, gravity)
    fun lin(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun row(c: Context, gravity: Int = Gravity.RIGHT or Gravity.CENTER_VERTICAL) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; this.gravity = gravity
    }
    fun col(c: Context, gravity: Int = Gravity.RIGHT or Gravity.CENTER_VERTICAL) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; this.gravity = gravity
    }

    fun gap(c: Context, w: Int, h: Int): View = View(c).apply { layoutParams = LinearLayout.LayoutParams(w, h) }

    fun setT(t: TextView, s: CharSequence) { if (t.text.toString() != s.toString()) t.text = s }

    /** إطار بمقاس التصميم 1280×720 يُكبَّر أو يُصغَّر ليملأ الشاشة. يعيد [الجذر، إطار التصميم] */
    fun scaled(c: Context, bg: Int): Pair<FrameLayout, FrameLayout> {
        val root = FrameLayout(c).apply { setBackgroundColor(bg) }
        val design = FrameLayout(c).apply { layoutParams = FrameLayout.LayoutParams(1280, 720); layoutDirection = View.LAYOUT_DIRECTION_LTR }
        root.addView(design)
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l; val hh = b - t
            if (w > 0 && hh > 0) {
                val s = minOf(w / 1280f, hh / 720f)
                design.pivotX = 0f; design.pivotY = 0f
                design.scaleX = s; design.scaleY = s
                design.translationX = (w - 1280 * s) / 2f
                design.translationY = (hh - 720 * s) / 2f
            }
        }
        return Pair(root, design)
    }

    // ---------------- الوقت بنظام 12 أو 24 ----------------
    /** الوقت نصاً: «11:21 م» أو «23:21» */
    fun hm(ms: Long): String {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return hm(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
    fun hm(h: Int, m: Int): String {
        if (Prefs.clock24) return String.format(java.util.Locale.US, "%02d:%02d", h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, if (h < 12) "ص" else "م")
    }
    /** من نص «06:38» القادم من الإنترنت */
    fun hmStr(s: String): String = try {
        val p = s.trim().split(":"); hm(p[0].toInt(), p[1].toInt())
    } catch (e: Exception) { s }
    /** للساعات الكبيرة: «ص/م» بخط أصغر */
    fun hmBig(ms: Long): CharSequence {
        val t = hm(ms)
        if (Prefs.clock24) return t
        val sp = android.text.SpannableString(t)
        sp.setSpan(android.text.style.RelativeSizeSpan(0.45f), t.length - 1, t.length, 0)
        return sp
    }

    // ---------------- عناصر الإعدادات ----------------
    /** مفتاح تشغيل مرسوم (بدون صور) */
    fun switchView(c: Context, on: Boolean, p: Palette): View {
        val track = FrameLayout(c).apply { background = rounded(if (on) p.accent else p.line, 16f) }
        val knob = View(c).apply { background = rounded(if (on) p.onAccent else p.muted, 12f) }
        track.addView(knob, FrameLayout.LayoutParams(24, 24, (if (on) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL).apply {
            leftMargin = 4; rightMargin = 4
        })
        return track
    }

    /** خلفية تضيء لحظة الضغط */
    fun pressable(normal: Drawable?, pressed: Drawable): Drawable =
        android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal ?: android.graphics.drawable.ColorDrawable(0))
        }
}
