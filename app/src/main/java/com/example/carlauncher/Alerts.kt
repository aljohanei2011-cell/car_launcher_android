package com.example.carlauncher

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/**
 * التنبيهات الزجاجية:
 * - والواجهة ظاهرة: تُرسل للواجهة لتعرضها بالتصميم الزجاجي في المكان المختار.
 * - وتطبيق آخر مفتوح (الخرائط): نافذة صغيرة شبه شفافة فوقه (تحتاج إذن «الظهور فوق التطبيقات»)،
 *   وإن لم يتوفر الإذن: إشعار عادي.
 * type: rem | note | match | prayer | drive
 */
object Alerts {
    private val main = Handler(Looper.getMainLooper())
    private val queue = JSONArray()
    private var view: View? = null
    private var wm: WindowManager? = null
    private const val ID_ALERT = 2
    const val CH_ALERT = "alerts"

    private val COLORS = mapOf("rem" to "#F2C879", "note" to "#7FB8FF", "match" to "#5AD1A0", "prayer" to "#C9B6FF", "drive" to "#FF9E80")
    private val ICONS = mapOf("rem" to "🔔", "note" to "📝", "match" to "⚽", "prayer" to "🕌", "drive" to "🚗")

    @Synchronized fun take(): String { val s = queue.toString(); while (queue.length() > 0) queue.remove(0); return s }
    @Synchronized private fun push(o: JSONObject) { queue.put(o); while (queue.length() > 10) queue.remove(0) }

    fun show(c: Context, type: String, title: String, sub: String, page: String?, extra: JSONObject? = null) {
        val o = JSONObject().put("type", type).put("title", title).put("sub", sub).put("page", page ?: "").put("at", System.currentTimeMillis())
        if (extra != null) o.put("extra", extra)
        if (MainActivity.visible) { push(o); return }
        val cfg = Prefs.config(c)
        if (cfg.optBoolean("glassOverlay", true) && Settings.canDrawOverlays(c)) {
            main.post { try { overlay(c.applicationContext, o, cfg) } catch (e: Exception) { notify(c, title, sub, page) } }
        } else notify(c, title, sub, page)
    }

    fun notify(c: Context, title: String, text: String, page: String?) {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (page != null) i.putExtra("page", page)
        val pi = PendingIntent.getActivity(c, page?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER).build()
        c.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    fun hideOverlay() { main.post { try { view?.let { wm?.removeView(it) } } catch (e: Exception) {}; view = null } }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun overlay(c: Context, o: JSONObject, cfg: JSONObject) {
        hideNow()
        val type = o.optString("type")
        val color = Color.parseColor(COLORS[type] ?: "#7FB8FF")
        val pos = cfg.optString("alertPos_$type", "").ifEmpty { cfg.optString("alertPos", "tc") }
        val sticky = type == "rem"
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(c, 14), dp(c, 10), dp(c, 10), dp(c, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 22).toFloat()
                setColor(Color.argb(205, 18, 28, 48))
                setStroke(dp(c, 1), Color.argb(110, 255, 255, 255))
            }
            elevation = dp(c, 6).toFloat()
        }
        val ic = TextView(c).apply {
            text = ICONS[type] ?: "🔔"; textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))) }
        }
        root.addView(ic, LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)))
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 12), 0, dp(c, 12), 0) }
        col.addView(TextView(c).apply { text = o.optString("title"); setTextColor(Color.WHITE); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        val sub = o.optString("sub")
        if (sub.isNotEmpty()) col.addView(TextView(c).apply { text = sub; setTextColor(Color.argb(200, 255, 255, 255)); textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        root.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun btn(label: String, act: () -> Unit) {
            val b = TextView(c).apply {
                text = label; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
                setPadding(dp(c, 16), 0, dp(c, 16), 0)
                background = GradientDrawable().apply { cornerRadius = dp(c, 14).toFloat(); setColor(Color.argb(50, 255, 255, 255)); setStroke(dp(c, 1), Color.argb(90, 255, 255, 255)) }
                setOnClickListener { act(); hideNow() }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 44)); lp.marginStart = dp(c, 8)
            root.addView(b, lp)
        }
        if (sticky) btn("10 د") { snooze(c, o, 10) }
        btn("تم") { if (sticky) push(JSONObject(o.toString()).put("done", true)) }
        root.setOnClickListener {
            hideNow()
            val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            o.optString("page").takeIf { it.isNotEmpty() }?.let { i.putExtra("page", it) }
            try { c.startActivity(i) } catch (e: Exception) {}
        }
        val w = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val small = pos == "cl" || pos == "tl"
        val lp = WindowManager.LayoutParams(
            if (small) dp(c, 380) else dp(c, 520), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT)
        when (pos) {
            "bc" -> { lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = dp(c, 90) }
            "tl", "cl" -> { lp.gravity = Gravity.TOP or Gravity.LEFT; lp.x = dp(c, 24); lp.y = dp(c, 16) }
            else -> { lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.y = dp(c, 20) }
        }
        root.alpha = 0f; root.translationY = if (pos == "bc") dp(c, 30).toFloat() else -dp(c, 30).toFloat()
        w.addView(root, lp)
        root.animate().alpha(1f).translationY(0f).setDuration(260).start()
        view = root; wm = w
        val ms = (cfg.optInt("alertSec", 6) * 1000L).coerceIn(3000L, 30000L) + (if (sticky) 4000L else 0L)
        main.postDelayed({ if (view === root) hideNow() }, ms)
    }

    private fun hideNow() {
        val v = view ?: return
        view = null
        try { v.animate().alpha(0f).setDuration(200).withEndAction { try { wm?.removeView(v) } catch (e: Exception) {} }.start() } catch (e: Exception) { try { wm?.removeView(v) } catch (x: Exception) {} }
    }

    /** تأجيل تذكير من النافذة فوق الخرائط: نضيف تنبيهاً مؤقتاً للخدمة ونخبر الواجهة عند رجوعها */
    private fun snooze(c: Context, o: JSONObject, min: Int) {
        val at = System.currentTimeMillis() + min * 60_000L
        val arr = Prefs.reminders(c); arr.put(JSONObject().put("at", at).put("title", o.optString("sub").ifEmpty { o.optString("title") }))
        Prefs.setReminders(c, arr.toString())
        push(JSONObject(o.toString()).put("snooze", at))
    }
}
