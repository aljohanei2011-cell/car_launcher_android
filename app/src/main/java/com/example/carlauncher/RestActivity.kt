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
        @Volatile var inst: RestActivity? = null
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
    private lateinit var root: FrameLayout
    private lateinit var col: LinearLayout
    private lateinit var brandView: TextView
    private var wx: TextView? = null
    private var mediaRow: LinearLayout? = null
    private var mediaText: TextView? = null
    private var mediaPlay: TextView? = null
    private var alertView: View? = null
    private var fastCount = 0

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
        Events.add(this, "فتح الاستراحة الخفيفة")

        inst = this
        root = FrameLayout(this); root.setBackgroundColor(Color.BLACK)
        bgImg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(bgImg, FrameLayout.LayoutParams(-1, -1))
        loadBackground(root)
        shade = View(this); shade.setBackgroundColor(Color.BLACK); shade.alpha = (cfg.optInt("shade", 20).coerceIn(0, 70)) / 100f
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        // الشريط العلوي: اسم السيارة
        val brand = tv(26f, Color.WHITE, bold = true); brand.text = cfg.optString("brand", ""); brand.letterSpacing = 0.12f
        val bl = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT); bl.topMargin = px(28f).toInt(); bl.rightMargin = px(40f).toInt()
        root.addView(brand, bl); brandView = brand

        // المحتوى
        col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER_HORIZONTAL
        val style = cfg.optString("style", "classic")
        val look = cfg.optString("look", if (style == "lux") "thin" else "bold")
        clock = tv(if (style == "clock") 230f else if (style == "lux") 180f else 150f, Color.WHITE, bold = look == "bold", light = look != "bold")
        clock.includeFontPadding = false
        if (style == "lux") clock.setTextColor(Color.parseColor("#F6EBD3"))
        col.addView(clock)
        dates = tv(22f, Color.parseColor("#E4E8F2")); col.addView(dates, lp(top = 6f))
        if (cfg.optBoolean("weather", true) && style != "clock") { val w = tv(20f, Color.parseColor("#D8DEEA")); wx = w; col.addView(w, lp(top = 4f)) }
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
        if (cfg.optBoolean("media", true)) buildMedia()
        setContentView(root)
        // سطوع أقل ليلاً أثناء الاستراحة فقط (يعود وحده عند الخروج، بدون أي إذن)
        applyBrightness()

        tick(); rotateDua()
        h.post(object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } })
        val ds = cfg.optInt("duaSec", 15).coerceIn(5, 120) * 1000L
        if (showDua) h.postDelayed(object : Runnable { override fun run() { rotateDua(); h.postDelayed(this, ds) } }, ds)
        h.postDelayed(object : Runnable { override fun run() { mediaState(); mediaInfo(); driveExit(); h.postDelayed(this, 3000) } }, 1500)
        // حماية الشاشة: إزاحة بسيطة غير ملحوظة كل 3 دقائق حتى لا تحترق الصورة الثابتة
        h.postDelayed(object : Runnable { override fun run() { shift(); h.postDelayed(this, 180_000) } }, 180_000)
    }

    private fun shift() {
        val r = java.util.Random()
        val dx = px((r.nextInt(17) - 8).toFloat()); val dy = px((r.nextInt(13) - 6).toFloat())
        listOf<View?>(col, brandView, mediaRow).forEach { v -> v?.animate()?.translationX(dx)?.translationY(dy)?.setDuration(2000)?.start() }
    }

    private fun applyBrightness() {
        try {
            val lpw = window.attributes
            lpw.screenBrightness = if (cfg.optBoolean("nightBright", false) && isNight()) 0.12f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lpw
        } catch (e: Exception) {}
    }

    // ===== الطقس من آخر تحديث محفوظ (بدون اتصال جديد) =====
    private fun weatherLine() {
        val w = wx ?: return
        try {
            val o = JSONObject(Weather.get(this))
            if (!o.optBoolean("ok") || System.currentTimeMillis() - o.optLong("at") > 3 * 3600_000L) { w.visibility = View.GONE; return }
            val code = o.optInt("code"); val t = Math.round(o.optDouble("temp"))
            val d = when (code) { 0 -> "صحو"; 1, 2 -> "غائم جزئياً"; 3 -> "غائم"; 45, 48 -> "ضباب"; in 51..57 -> "رذاذ"; in 61..67 -> "مطر"; in 71..77 -> "ثلج"; in 80..82 -> "زخات مطر"; in 95..99 -> "عاصفة رعدية"; else -> "" }
            w.text = "$t°" + (if (d.isNotEmpty()) "  ·  $d" else ""); w.visibility = View.VISIBLE
        } catch (e: Exception) { w.visibility = View.GONE }
    }

    // ===== الموسيقى: اسم المقطع مع تشغيل/إيقاف والتالي =====
    private fun buildMedia() {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        row.background = GradientDrawable().apply { cornerRadius = px(30f); setColor(Color.argb(90, 0, 0, 0)); setStroke(px(1.2f).toInt(), Color.argb(70, 255, 255, 255)) }
        row.setPadding(px(18f).toInt(), 0, px(8f).toInt(), 0)
        val t = tv(19f, Color.WHITE); t.maxLines = 1; t.ellipsize = android.text.TextUtils.TruncateAt.END; t.maxWidth = px(330f).toInt(); t.setShadowLayer(0f, 0f, 0f, 0)
        row.addView(t)
        fun btn(label: String, cmd: String): TextView = TextView(this).apply {
            text = label; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24f)); gravity = Gravity.CENTER
            setOnClickListener { mediaCmd(cmd) }
        }
        val play = btn("⏸", "toggle"); val nx = btn("⏭", "next")
        row.addView(play, LinearLayout.LayoutParams(px(60f).toInt(), px(60f).toInt()))
        row.addView(nx, LinearLayout.LayoutParams(px(60f).toInt(), px(60f).toInt()))
        val l = FrameLayout.LayoutParams(-2, px(60f).toInt(), Gravity.BOTTOM or Gravity.RIGHT); l.bottomMargin = px(26f).toInt(); l.rightMargin = px(30f).toInt()
        row.visibility = View.GONE
        root.addView(row, l)
        mediaRow = row; mediaText = t; mediaPlay = play
    }

    private fun controller(): android.media.session.MediaController? = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val list = msm.getActiveSessions(android.content.ComponentName(this, MediaListener::class.java))
        list.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    } catch (e: Exception) { null }

    private fun mediaInfo() {
        val row = mediaRow ?: return
        val c = controller()
        val title = c?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
        if (c == null || title.isEmpty()) { row.visibility = View.GONE; return }
        val artist = c.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        mediaText?.text = "♪  " + title + (if (artist.isNotEmpty()) " — $artist" else "")
        mediaPlay?.text = if (playing) "⏸" else "▶"
        row.visibility = View.VISIBLE
    }

    private fun mediaCmd(cmd: String) {
        try {
            val c = controller() ?: return; val t = c.transportControls
            when (cmd) {
                "next" -> t.skipToNext()
                else -> if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) t.pause() else t.play()
            }
            h.postDelayed({ mediaInfo() }, 600)
        } catch (e: Exception) {}
    }

    // ===== التنبيهات داخل الاستراحة (التذكيرات، الصلاة، المباريات) =====
    fun showAlert(type: String, title: String, sub: String, page: String?) {
        try {
            alertView?.let { root.removeView(it) }
            val colors = mapOf("rem" to "#F2C879", "note" to "#7FB8FF", "match" to "#5AD1A0", "prayer" to "#C9B6FF", "drive" to "#FF9E80")
            val icons = mapOf("rem" to "🔔", "note" to "📝", "match" to "⚽", "prayer" to "🕌", "drive" to "🚗")
            val c = try { Color.parseColor(colors[type] ?: "#F2C879") } catch (e: Exception) { accent }
            val box = LinearLayout(this); box.orientation = LinearLayout.HORIZONTAL; box.gravity = Gravity.CENTER_VERTICAL
            box.layoutDirection = View.LAYOUT_DIRECTION_RTL
            box.background = GradientDrawable().apply { cornerRadius = px(26f); setColor(Color.argb(215, 14, 20, 34)); setStroke(px(1.5f).toInt(), c) }
            box.setPadding(px(22f).toInt(), px(14f).toInt(), px(14f).toInt(), px(14f).toInt())
            val ic = tv(34f); ic.text = icons[type] ?: "🔔"; ic.setShadowLayer(0f, 0f, 0f, 0)
            box.addView(ic)
            val txt = LinearLayout(this); txt.orientation = LinearLayout.VERTICAL
            val a = tv(24f, Color.WHITE, bold = true); a.text = title; a.gravity = Gravity.RIGHT; a.maxLines = 2; a.setShadowLayer(0f, 0f, 0f, 0)
            val b = tv(19f, Color.parseColor("#C9D2E3")); b.text = sub; b.gravity = Gravity.RIGHT; b.maxLines = 2; b.setShadowLayer(0f, 0f, 0f, 0)
            txt.addView(a); if (sub.isNotEmpty() && sub != title) txt.addView(b)
            val tl = LinearLayout.LayoutParams(0, -2, 1f); tl.marginStart = px(14f).toInt(); tl.marginEnd = px(14f).toInt()
            box.addView(txt, tl)
            val ok = TextView(this).apply {
                text = if (page.isNullOrEmpty() || type == "prayer") "حسناً" else "فتح"; setTextColor(Color.parseColor("#0A0F1F")); setTextSize(TypedValue.COMPLEX_UNIT_PX, px(20f)); gravity = Gravity.CENTER
                background = GradientDrawable().apply { cornerRadius = px(20f); setColor(c) }
                setPadding(px(24f).toInt(), 0, px(24f).toInt(), 0)
                setOnClickListener { hideAlert(); if (!page.isNullOrEmpty() && type != "prayer") leave(true, page) }
            }
            box.addView(ok, LinearLayout.LayoutParams(-2, px(54f).toInt()))
            val l = FrameLayout.LayoutParams(px(720f).toInt(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL); l.topMargin = px(24f).toInt()
            box.alpha = 0f; box.translationY = -px(30f)
            root.addView(box, l); alertView = box
            box.animate().alpha(1f).translationY(0f).setDuration(300).start()
            box.setOnClickListener { hideAlert() }
            // التذكير يبقى حتى تلمسه، والباقي يختفي بعد 20 ثانية
            if (type != "rem") h.postDelayed({ if (alertView === box) hideAlert() }, 20_000)
            openedAt = System.currentTimeMillis()
        } catch (e: Exception) {}
    }

    private fun hideAlert() {
        val v = alertView ?: return; alertView = null
        v.animate().alpha(0f).setDuration(250).withEndAction { try { root.removeView(v) } catch (e: Exception) {} }.start()
    }

    private fun lp(top: Float): LinearLayout.LayoutParams { val p = LinearLayout.LayoutParams(-2, -2); p.topMargin = px(top).toInt(); p.gravity = Gravity.CENTER_HORIZONTAL; return p }

    /** الخلفية: فيديو (إن اختير والذاكرة تسمح) وإلا صورة */
    private fun loadBackground(root: FrameLayout) {
        val night = isNight()
        val v = (if (night) cfg.optString("videoNight", "") else "").ifEmpty { cfg.optString("video", "") }
        if (v.isNotEmpty() && File(v).exists()) {
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
            val opts = BitmapFactory.Options().apply { inSampleSize = 1; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
            val bm = when {
                b.startsWith("file://") -> BitmapFactory.decodeFile(b.removePrefix("file://"), opts)
                b.isNotEmpty() -> assets.open(b).use { BitmapFactory.decodeStream(it, null, opts) }
                else -> null
            }
            if (bm != null) bgImg.setImageBitmap(bm)
        } catch (e: Exception) {}
    }

    private fun prayersToday(): List<Pair<String, Long>> {
        val arr = Prefs.prayers(this); val out = ArrayList<Pair<String, Long>>()
        for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; out.add(o.optString("name") to o.optLong("at")) }
        return out.sortedBy { it.second }
    }

    private fun isNight(): Boolean {
        val now = System.currentTimeMillis()
        val p = prayersToday(); if (p.isEmpty()) { val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return hr >= 19 || hr < 5 }
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
        val c = Calendar.getInstance(); val m = c.get(Calendar.MINUTE)
        if (m != lastMin) {
            lastMin = m
            try {
                val hij = android.icu.text.SimpleDateFormat("EEEE، d MMMM y هـ", android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn")).format(java.util.Date())
                val greg = android.icu.text.SimpleDateFormat("d MMMM y م", android.icu.util.ULocale("ar@numbers=latn")).format(java.util.Date())
                dates.text = if (cfg.optBoolean("date", true)) "$hij  |  $greg" else ""
            } catch (e: Exception) {}
            val p = prayersToday(); val nx = p.firstOrNull { it.second > now }
            if (nx != null) {
                val diff = (nx.second - now) / 60000L
                next.text = "الصلاة القادمة: " + nx.first + "  " + fmtTime(nx.second) + "  ·  بعد " + (if (diff >= 60) "${diff / 60} س ${diff % 60} د" else "$diff د")
                next.setTextColor(Color.WHITE)
            }
            renderStrip(p, nx?.second ?: 0L)
            applyDim(); weatherLine(); applyBrightness()
        }
        // العودة للرئيسية تلقائياً بعد مدة (إن اختيرت)
        val r = cfg.optInt("restReturn", 0)
        if (r > 0 && now - openedAt > r * 60_000L) leave(true)
    }

    private fun renderStrip(p: List<Pair<String, Long>>, nextAt: Long) {
        if (cfg.optString("style") != "lux") return
        strip.removeAllViews()
        val dayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
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

    /** الرجوع للرئيسية وحده عند بدء القيادة (إن اختير): سرعة أعلى من الحد مرتين متتاليتين */
    private fun driveExit() {
        val lim = cfg.optInt("exitSpeed", 0); if (lim <= 0) return
        val speed = try { TripStore.state(this).optDouble("speed", 0.0) } catch (e: Exception) { 0.0 }
        fastCount = if (speed > lim) fastCount + 1 else 0
        if (fastCount >= 2) { Events.add(this, "خروج من الاستراحة: بدء القيادة"); leave(true) }
    }

    private fun leave(user: Boolean, page: String? = null) {
        if (isFinishing) return
        if (user) { RestGuard.restOpen = false; RestGuard.nativeRest = false; Events.add(this, "خروج من الاستراحة الخفيفة") }
        try {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (!page.isNullOrEmpty()) i.putExtra("page", page)
            startActivity(i)
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

    override fun onResume() { super.onResume(); visible = true; hideBars(); try { video?.start() } catch (e: Exception) {} }
    override fun onPause() {
        super.onPause(); visible = false
        RestGuard.pausedAt = System.currentTimeMillis()
        try { video?.pause() } catch (e: Exception) {}
        if (!isFinishing) Events.add(this, "اختفت الاستراحة الخفيفة" + (if (System.currentTimeMillis() - RestGuard.lastTouch < 4000) " بعد لمس" else " بدون لمس"))
    }
    override fun onDestroy() { if (inst === this) inst = null; h.removeCallbacksAndMessages(null); try { video?.stopPlayback() } catch (e: Exception) {}; super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
}
