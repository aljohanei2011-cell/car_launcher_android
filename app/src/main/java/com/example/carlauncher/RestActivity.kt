package com.example.carlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * شاشة الاستراحة الأصلية الخفيفة: بدون محرك الصفحات إطلاقاً.
 * أثناءها يُغلق محرك الصفحات ويُفرَّغ من الذاكرة، فتأخذ الاستراحة جزءاً صغيراً من الذاكرة
 * ولا يغلقها النظام. الإعدادات تأتي من الواجهة عند الفتح (rest_cfg).
 */
class RestActivity : Activity() {
    companion object {
        @Volatile var visible = false
        fun start(c: Context, cfg: String) {
            c.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("rest_cfg", cfg).apply()
            RestGuard.nativeRest = true
            c.startActivity(Intent(c, RestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var cfg = JSONObject()
    private var k = 1f
    private lateinit var clock: TextView
    private lateinit var dates: TextView
    private lateinit var dua: TextView
    private lateinit var next: TextView
    private lateinit var strip: LinearLayout
    private lateinit var shade: View
    private lateinit var bgImg: ImageView
    private var video: VideoView? = null
    private var duas = JSONArray()
    private var duaIdx = 0
    private var openedAt = System.currentTimeMillis()
    private var accent = Color.parseColor("#F2C879")

    private fun px(v: Float) = v * k
    private fun tv(size: Float, color: Int = Color.WHITE, bold: Boolean = false, light: Boolean = false): TextView = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); setTextColor(color); gravity = Gravity.CENTER
        typeface = if (light) Typeface.create("sans-serif-light", Typeface.NORMAL) else if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.DEFAULT
        setShadowLayer(px(6f), 0f, px(2f), Color.argb(150, 0, 0, 0))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        cfg = try { JSONObject(getSharedPreferences("launcher", MODE_PRIVATE).getString("rest_cfg", "{}")!!) } catch (e: Exception) { JSONObject() }
        duas = cfg.optJSONArray("duas") ?: JSONArray()
        try { accent = Color.parseColor(cfg.optString("accent", "#F2C879")) } catch (e: Exception) {}
        val dm = resources.displayMetrics
        k = maxOf(dm.widthPixels, dm.heightPixels) / 1280f
        RestGuard.restOpen = true; RestGuard.nativeRest = true
        RestState.opened(this)
        Events.add(this, "فتح الاستراحة الخفيفة")

        val root = FrameLayout(this); root.setBackgroundColor(Color.BLACK)
        bgImg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(bgImg, FrameLayout.LayoutParams(-1, -1))
        loadBackground(root)
        shade = View(this); shade.setBackgroundColor(Color.BLACK); shade.alpha = (cfg.optInt("shade", 20).coerceIn(0, 70)) / 100f
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        // الشريط العلوي: اسم السيارة
        val brand = tv(26f, Color.WHITE, bold = true); brand.text = cfg.optString("brand", ""); brand.letterSpacing = 0.12f
        val bl = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT); bl.topMargin = px(28f).toInt(); bl.rightMargin = px(40f).toInt()
        root.addView(brand, bl)

        // المحتوى
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER_HORIZONTAL
        val style = cfg.optString("style", "classic")
        val look = cfg.optString("look", if (style == "lux") "thin" else "bold")
        clock = tv(if (style == "clock") 230f else if (style == "lux") 180f else 150f, Color.WHITE, bold = look == "bold", light = look != "bold")
        clock.includeFontPadding = false
        if (style == "lux") clock.setTextColor(Color.parseColor("#F6EBD3"))
        col.addView(clock)
        dates = tv(22f, Color.parseColor("#E4E8F2")); col.addView(dates, lp(top = 6f))
        dua = tv(30f, Color.WHITE); dua.maxLines = 1; dua.ellipsize = android.text.TextUtils.TruncateAt.END
        try { dua.typeface = Typeface.createFromAsset(assets, "fonts/alyamama-regular.ttf") } catch (e: Exception) {}
        val showDua = cfg.optBoolean("dua", true) && style == "classic" && duas.length() > 0
        if (showDua) col.addView(dua, lp(top = 22f))
        next = tv(28f, Color.WHITE, bold = true)
        if (cfg.optBoolean("prayer", true) && style == "classic") col.addView(next, lp(top = 18f))
        strip = LinearLayout(this); strip.orientation = LinearLayout.HORIZONTAL; strip.layoutDirection = View.LAYOUT_DIRECTION_RTL
        strip.background = GradientDrawable().apply { cornerRadius = px(26f); setColor(Color.argb(115, 10, 10, 12)); setStroke(px(1.2f).toInt(), Color.argb(110, 231, 199, 122)) }
        strip.setPadding(px(8f).toInt(), px(8f).toInt(), px(8f).toInt(), px(8f).toInt())
        if (style == "lux") col.addView(strip, lp(top = 30f))

        val pos = cfg.optString("pos", "c")
        val cl = FrameLayout.LayoutParams(-2, -2, when (pos) { "tl" -> Gravity.TOP or Gravity.LEFT; "bc" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; else -> Gravity.CENTER })
        if (pos == "tl") { cl.topMargin = px(90f).toInt(); cl.leftMargin = px(40f).toInt(); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(110f)); col.gravity = Gravity.LEFT }
        if (pos == "bc") cl.bottomMargin = px(110f).toInt()
        if (pos == "c") cl.bottomMargin = px(40f).toInt()
        root.addView(col, cl)

        // زر الرئيسية
        val home = TextView(this).apply {
            text = "الرئيسية"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_PX, px(21f)); gravity = Gravity.CENTER
            setPadding(px(30f).toInt(), 0, px(30f).toInt(), 0)
            background = GradientDrawable().apply { cornerRadius = px(30f); setColor(Color.argb(40, 255, 255, 255)); setStroke(px(1.5f).toInt(), Color.argb(100, 255, 255, 255)) }
            setOnClickListener { leave(true) }
        }
        val hl = FrameLayout.LayoutParams(-2, px(60f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL); hl.bottomMargin = px(26f).toInt()
        root.addView(home, hl)
        setContentView(root)

        tick(); rotateDua()
        h.post(object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } })
        val ds = cfg.optInt("duaSec", 15).coerceIn(5, 120) * 1000L
        if (showDua) h.postDelayed(object : Runnable { override fun run() { rotateDua(); h.postDelayed(this, ds) } }, ds)
        h.postDelayed(object : Runnable { override fun run() { mediaState(); h.postDelayed(this, 3000) } }, 3000)
        // نبضة كل دقيقة وهي ظاهرة: إن قتل النظام التطبيق نعرف ذلك ونعيد فتحها (RestState.check)
        h.postDelayed(object : Runnable { override fun run() { if (visible) RestState.beat(this@RestActivity); h.postDelayed(this, 60_000) } }, 60_000)
    }

    private fun lp(top: Float): LinearLayout.LayoutParams { val p = LinearLayout.LayoutParams(-2, -2); p.topMargin = px(top).toInt(); p.gravity = Gravity.CENTER_HORIZONTAL; return p }

    /** الخلفية: فيديو (إن اختير والذاكرة تسمح) وإلا صورة */
    private fun loadBackground(root: FrameLayout) {
        val night = isNight()
        val v = (if (night) cfg.optString("videoNight", "") else "").ifEmpty { cfg.optString("video", "") }
        // الشاشات الضعيفة: بعد قتل سابق أو مع قلة الذاكرة نعرض صورة بدل الفيديو
        val videoOk = !RestState.killedRecently(this) && !RestState.lowMemory(this)
        if (v.isNotEmpty() && videoOk && File(v).exists()) {
            try {
                val vv = VideoView(this)
                vv.setVideoPath(v)
                vv.setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); vv.start() }
                vv.setOnErrorListener { _, _, _ -> vv.visibility = View.GONE; showImage(); true }
                root.addView(vv, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                video = vv
                return
            } catch (e: Exception) {}
        }
        showImage()
    }

    private fun showImage() {
        val b = cfg.optString("bg", "")
        try {
            // نقيس الأبعاد أولاً ثم نصغّر حسب الشاشة، حتى لا تُحمَّل صورة ضخمة كاملة في الذاكرة
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            when {
                b.startsWith("file://") -> BitmapFactory.decodeFile(b.removePrefix("file://"), bounds)
                b.isNotEmpty() -> assets.open(b).use { BitmapFactory.decodeStream(it, null, bounds) }
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bm = when {
                b.startsWith("file://") -> BitmapFactory.decodeFile(b.removePrefix("file://"), opts)
                b.isNotEmpty() -> assets.open(b).use { BitmapFactory.decodeStream(it, null, opts) }
                else -> null
            }
            if (bm != null) bgImg.setImageBitmap(bm)
        } catch (e: Exception) {
        } catch (e: OutOfMemoryError) {
            CrashLog.add(this, "rest", "نفدت الذاكرة أثناء تحميل خلفية الاستراحة")
        }
    }

    /** أكبر قسمة على قوى 2 تُبقي الصورة بحجم الشاشة أو أكبر */
    private fun sampleFor(w: Int, h: Int): Int {
        if (w <= 0 || h <= 0) return 1
        val dm = resources.displayMetrics
        val tw = maxOf(dm.widthPixels, dm.heightPixels); val th = minOf(dm.widthPixels, dm.heightPixels)
        var s = 1
        while (w / (s * 2) >= tw && h / (s * 2) >= th) s *= 2
        return s
    }

    /** الساعة والتاريخ بتوقيت الرياض دائماً (شاشات السيارات كثيراً ما يكون توقيتها خاطئاً) */
    private fun riyadh(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"))

    private fun prayersToday(): List<Pair<String, Long>> {
        val arr = Prefs.prayers(this); val out = ArrayList<Pair<String, Long>>()
        for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; out.add(o.optString("name") to o.optLong("at")) }
        return out.sortedBy { it.second }
    }

    private fun isNight(): Boolean {
        val now = System.currentTimeMillis()
        val p = prayersToday(); if (p.isEmpty()) { val hr = riyadh().get(Calendar.HOUR_OF_DAY); return hr >= 19 || hr < 5 }
        val maghrib = p.lastOrNull { it.first.contains("المغرب") && it.second <= now }
        val fajr = p.firstOrNull { it.first.contains("الفجر") && it.second > now }
        return maghrib != null && (fajr == null || fajr.second > now) && now - maghrib.second < 12 * 3600_000L
    }

    private fun fmtTime(t: Long, withAmPm: Boolean = true): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh")); c.timeInMillis = t
        val hr = c.get(Calendar.HOUR_OF_DAY); val mi = c.get(Calendar.MINUTE)
        if (cfg.optBoolean("h24", false)) return String.format(java.util.Locale.US, "%d:%02d", hr, mi)
        val h12 = if (hr % 12 == 0) 12 else hr % 12
        return String.format(java.util.Locale.US, "%d:%02d", h12, mi) + if (withAmPm) (if (hr < 12) " ص" else " م") else ""
    }

    private var lastMin = -1
    private fun tick() {
        val now = System.currentTimeMillis()
        clock.text = fmtTime(now, false)
        val c = riyadh(); val m = c.get(Calendar.MINUTE)
        if (m != lastMin) {
            lastMin = m
            try {
                val tz = android.icu.util.TimeZone.getTimeZone("Asia/Riyadh")
                val fh = android.icu.text.SimpleDateFormat("EEEE، d MMMM y هـ", android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn"))
                val fg = android.icu.text.SimpleDateFormat("d MMMM y م", android.icu.util.ULocale("ar@numbers=latn"))
                fh.setTimeZone(tz); fg.setTimeZone(tz)
                val hij = fh.format(java.util.Date())
                val greg = fg.format(java.util.Date())
                dates.text = if (cfg.optBoolean("date", true)) "$hij  |  $greg" else ""
            } catch (e: Exception) {}
            val p = prayersToday(); val nx = p.firstOrNull { it.second > now }
            if (nx != null) {
                val diff = (nx.second - now) / 60000L
                next.text = "الصلاة القادمة: " + nx.first + "  " + fmtTime(nx.second) + "  ·  بعد " + (if (diff >= 60) "${diff / 60} س ${diff % 60} د" else "$diff د")
                next.setTextColor(Color.WHITE)
            }
            renderStrip(p, nx?.second ?: 0L)
            applyDim()
        }
        // العودة للرئيسية تلقائياً بعد مدة (إن اختيرت)
        val r = cfg.optInt("restReturn", 0)
        if (r > 0 && now - openedAt > r * 60_000L) leave(true)
    }

    private fun renderStrip(p: List<Pair<String, Long>>, nextAt: Long) {
        if (cfg.optString("style") != "lux") return
        strip.removeAllViews()
        val dayStart = riyadh().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        p.filter { it.second >= dayStart && it.second < dayStart + 86_400_000L }.take(5).forEach { (name, at) ->
            val on = at == nextAt
            val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.gravity = Gravity.CENTER
            box.setPadding(px(20f).toInt(), px(10f).toInt(), px(20f).toInt(), px(10f).toInt())
            if (on) box.background = GradientDrawable().apply { cornerRadius = px(18f); setColor(Color.argb(45, 231, 199, 122)); setStroke(px(1.2f).toInt(), Color.argb(180, 231, 199, 122)) }
            val a = tv(21f, if (on) accent else Color.parseColor("#E9E2D2"), bold = true); a.text = name; a.setShadowLayer(0f, 0f, 0f, 0)
            val b = tv(24f, if (on) accent else Color.WHITE); b.text = fmtTime(at); b.setShadowLayer(0f, 0f, 0f, 0)
            box.addView(a); box.addView(b)
            strip.addView(box, LinearLayout.LayoutParams(px(150f).toInt(), -2))
        }
    }

    private fun rotateDua() {
        if (duas.length() == 0) return
        dua.animate().alpha(0f).setDuration(250).withEndAction {
            dua.text = duas.optString(duaIdx++ % duas.length()); dua.animate().alpha(1f).setDuration(350).start()
        }.start()
    }

    /** «ساعة فقط» تخفت ليلاً، والفيديو يخفت أو يتوقف ليلاً حسب الإعداد */
    private fun applyDim() {
        val night = isNight()
        val dimMode = cfg.optString("dim", "night")
        val dim = cfg.optString("style") == "clock" && (dimMode == "always" || (dimMode == "night" && night))
        val gold = Color.parseColor("#9A6F2C")
        clock.setTextColor(if (dim) gold else if (cfg.optString("style") == "lux") Color.parseColor("#F6EBD3") else Color.WHITE)
        dates.alpha = if (dim) 0.6f else 1f
        bgImg.alpha = if (dim) 0f else 1f
        video?.alpha = if (dim) 0f else if (night && cfg.optString("nightVideo", "dim") == "dim") 0.45f else 1f
    }

    private fun mediaState() {
        val v = video ?: return
        val speed = try { TripStore.state(this).optDouble("speed", 0.0) } catch (e: Exception) { 0.0 }
        val stop = (cfg.optBoolean("pauseMove", true) && speed > 10) || (isNight() && cfg.optString("nightVideo", "dim") == "still")
        try { if (stop && v.isPlaying) v.pause() else if (!stop && !v.isPlaying) v.start() } catch (e: Exception) {}
    }

    private fun leave(user: Boolean) {
        if (isFinishing) return
        RestState.closed(this)
        if (user) { RestGuard.restOpen = false; RestGuard.nativeRest = false; Events.add(this, "خروج من الاستراحة الخفيفة") }
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {}
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { leave(true) }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { RestGuard.lastTouch = System.currentTimeMillis(); openedAt = System.currentTimeMillis() }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() { super.onResume(); visible = true; RestState.opened(this); hideBars(); try { video?.start() } catch (e: Exception) {} }
    override fun onPause() {
        super.onPause(); visible = false
        // اختفت عن الشاشة (قائمة الأخيرة، تطبيق آخر، سحبها من الأخيرة): لا نعيد فتحها إن قُتل التطبيق بعدها
        RestState.closed(this)
        RestGuard.pausedAt = System.currentTimeMillis()
        try { video?.pause() } catch (e: Exception) {}
        if (!isFinishing) Events.add(this, "اختفت الاستراحة الخفيفة" + (if (System.currentTimeMillis() - RestGuard.lastTouch < 4000) " بعد لمس" else " بدون لمس"))
    }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); try { video?.stopPlayback() } catch (e: Exception) {}; super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
}
