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
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
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
}
