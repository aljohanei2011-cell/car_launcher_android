package com.aljohanei.rafiq

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * «رفيق الطريق الجديد»: شاشة واحدة بواجهات أندرويد الأصلية.
 * التصميم ثابت 1280×720 ويُكبَّر أو يُصغَّر ليملأ الشاشة.
 * التحديث: ثانية للساعة والسرعة، ونصف دقيقة للصلاة والأذكار، و30 دقيقة للطقس، والمباريات كل دقيقة أثناء اللعب فقط.
 */
class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var pal: Palette
    private var night = false
    private var started = false
    private lateinit var design: FrameLayout
    private var footer: FrameLayout? = null
    private lateinit var bannerHost: FrameLayout
    private var editMode = false

    // عناصر تتحدث
    private lateinit var clockTv: TextView
    private lateinit var dayIcon: ImageView
    private lateinit var btIv: ImageView
    private lateinit var gpsIv: ImageView
    private lateinit var wifiIv: ImageView
    private lateinit var gauge: GaugeView
    private lateinit var timeV: TextView
    private lateinit var timeS1: TextView
    private lateinit var timeS2: TextView
    private lateinit var wxV: TextView
    private lateinit var wxS: TextView
    private lateinit var tripV: TextView
    private lateinit var tripS: TextView
    private lateinit var mHead: TextView
    private lateinit var mBadge: TextView
    private lateinit var mV: TextView
    private lateinit var mS: TextView
    private lateinit var muHead: TextView
    private lateinit var muV: TextView
    private lateinit var playIv: ImageView
    private lateinit var prV: TextView
    private lateinit var prS: TextView
    private lateinit var qArrow: ImageView
    private lateinit var qTxt: TextView
    private lateinit var azHead: TextView
    private lateinit var azV: TextView
    private lateinit var azS: TextView

    // بيانات الشبكة (تبقى عند إعادة بناء الواجهة)
    private var weather: Net.Weather? = null
    private var weatherAt = 0L
    private var weatherBusy = false
    private var matches: Net.MatchCard? = null
    private var matchesAt = 0L
    private var matchesBusy = false
    private val lastScores = HashMap<String, String>()
    private var lastSlow = 0L
    private var lastStatus = 0L

    // حالة التنبيهات
    private var nextIdx = 0
    private var nextAt = 0L
    private var preShownFor = 0L
    private var adhanShownFor = 0L
    private var speedShown = false
    private var seenRide = 0
    private var seenTravel = 0
    private var restFor = 0L
    private var restCount = 0
    private var tone: ToneGenerator? = null
    private lateinit var wxHead: TextView
    private lateinit var headingTv: TextView
    private var standbyView: View? = null
    private var lastTouch = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        seenRide = Trip.rideEvent; seenTravel = Trip.travelEvent
        night = Astro.isNight(System.currentTimeMillis(), Loc.lat(), Loc.lon())
        build()
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            TripService.start(this)
        }
        PrayerAlarm.schedule(this)
        if (Prefs.autoPlay && !autoPlayed) {
            autoPlayed = true
            h.postDelayed({ Media.key(this, KeyEvent.KEYCODE_MEDIA_PLAY) }, 8000)
        }
    }

    companion object { private var autoPlayed = false }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        lastTouch = System.currentTimeMillis()
        if (standbyView != null) { hideStandby(); return true }
        return super.dispatchTouchEvent(ev)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) TripService.start(this)
    }

    override fun onStart() {
        super.onStart()
        started = true
        Media.attach(this)
        lastSlow = 0L; lastStatus = 0L
        h.removeCallbacks(tick); h.post(tick)
    }

    override fun onStop() {
        started = false
        h.removeCallbacks(tick)
        super.onStop()
    }

    override fun onDestroy() {
        try { tone?.release() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        immersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editMode) { editMode = false; rebuildFooter() }
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    fun palNow(): Palette = when (Prefs.theme) {
        "dark" -> Palette.DARK
        "light" -> Palette.LIGHT
        "amber" -> Palette.AMBER
        else -> if (night) Palette.DARK else Palette.LIGHT
    }

    /** تطبيق أي تغيير من الإعدادات فوراً */
    fun applySettings() {
        night = Astro.isNight(System.currentTimeMillis(), Loc.lat(), Loc.lon())
        build()
        PrayerAlarm.schedule(this)
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun moving() = Trip.speedKmh > 10f
    private fun locked(): Boolean {
        if (Prefs.lockDriving && moving()) { toast("التعديل مقفل أثناء القيادة"); return true }
        return false
    }

    // ============================== بناء الواجهة ==============================

    private fun build() {
        pal = palNow()
        Ui.fontScale = if (Prefs.bigFont) 1.1f else 1f
        standbyView = null
        val (root, d) = Ui.scaled(this, pal.bg)
        design = d
        buildHeader(d)
        buildBody(d)
        footer = null
        rebuildFooter()
        bannerHost = FrameLayout(this).apply { visibility = View.GONE }
        d.addView(bannerHost, Ui.abs(1232, 112, 24, 80))
        setContentView(root)
        current = null
        refreshAll()
        pumpBanner()
    }

    private fun buildHeader(d: FrameLayout) {
        val hdr = FrameLayout(this)
        d.addView(hdr, Ui.abs(1280, 72, 0, 0))
        hdr.addView(View(this).apply { setBackgroundColor(pal.accent) }, Ui.abs(1280, 2, 0, 70))

        val brand = Ui.tv(this, 18f, pal.text, true).apply { text = Prefs.brand; visibility = if (Prefs.brand.isEmpty()) View.GONE else View.VISIBLE }

        // يمين: اسم التطبيق (ضغطة مطولة عليه تفتح الإعدادات دائماً)
        val right = Ui.row(this, Gravity.CENTER_VERTICAL)
        if (!Prefs.brandLeft) right.addView(brand, Ui.lin(Ui.WRAP, 72).apply { rightMargin = 18 })
        right.addView(Ui.tv(this, 26f, pal.accent, true).apply {
            text = "رفيق الطريق"
            setOnLongClickListener { openSettingsScreen(); true }
        }, Ui.lin(Ui.WRAP, 72))
        hdr.addView(right, Ui.at(Ui.WRAP, 72, Gravity.RIGHT or Gravity.TOP).apply { rightMargin = 20 })

        val mid = Ui.row(this, Gravity.CENTER)
        clockTv = Ui.tv(this, 32f, pal.text, true, Gravity.CENTER)
        dayIcon = Ui.icon(this, R.drawable.g_sun, pal.amber)
        clockTv.setOnLongClickListener { quickPanel(); true }
        headingTv = Ui.tv(this, 16f, pal.muted, false, Gravity.CENTER)
        mid.addView(headingTv, Ui.lin(Ui.WRAP, 72).apply { rightMargin = 12 })
        mid.addView(clockTv, Ui.lin(Ui.WRAP, 72))
        mid.addView(dayIcon, Ui.lin(22, 22).apply { leftMargin = 10 })
        hdr.addView(mid, Ui.at(Ui.WRAP, 72, Gravity.CENTER_HORIZONTAL or Gravity.TOP))

        val left = Ui.row(this, Gravity.CENTER_VERTICAL)
        if (Prefs.brandLeft) left.addView(brand, Ui.lin(Ui.WRAP, 72).apply { rightMargin = 12 })
        val grp = Ui.row(this, Gravity.CENTER).apply { background = Ui.rounded(pal.bg, 12f, 2, pal.line) }
        btIv = statusIcon(R.drawable.g_bt) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
        gpsIv = statusIcon(R.drawable.g_loc) { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
        wifiIv = statusIcon(R.drawable.g_wifi) { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        grp.addView(btIv, Ui.lin(48, 46))
        grp.addView(View(this).apply { setBackgroundColor(pal.line) }, Ui.lin(2, 46))
        grp.addView(gpsIv, Ui.lin(48, 46))
        grp.addView(View(this).apply { setBackgroundColor(pal.line) }, Ui.lin(2, 46))
        grp.addView(wifiIv, Ui.lin(48, 46))
        left.addView(grp, Ui.lin(Ui.WRAP, 50))
        val close = Ui.row(this, Gravity.CENTER).apply {
            background = Ui.rounded(pal.bg, 12f, 2, pal.accent)
            setPadding(14, 0, 14, 0)
            addView(Ui.icon(context, R.drawable.g_x, pal.accent), Ui.lin(20, 20))
            addView(Ui.tv(context, 17f, pal.accent, true).apply { text = "إغلاق الكل" }, Ui.lin(Ui.WRAP, 48).apply { leftMargin = 8 })
            setOnClickListener { closeAll() }
        }
        left.addView(close, Ui.lin(Ui.WRAP, 50).apply { leftMargin = 10 })
        hdr.addView(left, Ui.at(Ui.WRAP, 72, Gravity.LEFT or Gravity.TOP).apply { leftMargin = 20 })
    }

    private fun statusIcon(res: Int, onClick: () -> Unit): ImageView =
        Ui.icon(this, res, pal.accent).apply { setPadding(11, 10, 11, 10); setOnClickListener { onClick() } }

    private fun buildBody(d: FrameLayout) {
        val hidden = Prefs.hidden()
        val rightStack = Ui.col(this)
        val leftStack = Ui.col(this)
        d.addView(rightStack, Ui.abs(340, 556, 916, 78))
        d.addView(leftStack, Ui.abs(340, 556, 24, 78))
        // تُنشأ كل البطاقات (لتبقى المراجع صالحة) وتُضاف حسب الترتيب المحفوظ
        val made = HashMap<String, View>()
        for (id in Prefs.CARD_IDS) made[id] = makeCard(id, hidden)
        val r = Prefs.column(true); val l = Prefs.column(false)
        for (id in Prefs.CARD_IDS) if (id !in r && id !in l) r.add(id)
        for (id in r) rightStack.addView(made[id], Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6; bottomMargin = 6 })
        for (id in l) leftStack.addView(made[id], Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6; bottomMargin = 6 })

        val g = FrameLayout(this)
        d.addView(g, Ui.abs(528, 544, 376, 84))
        gauge = GaugeView(this).apply { pal = this@MainActivity.pal; mode = Prefs.gauge; max = Prefs.maxSpeed; alert = Prefs.alertSpeed }
        g.addView(gauge, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
        g.addView(ImageView(this).apply { setImageResource(R.drawable.car_rear); scaleType = ImageView.ScaleType.FIT_CENTER },
            Ui.at(260, 213, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    private fun makeCard(id: String, hidden: Set<String>): View {
        val c = Ui.col(this)
        val card: View
        when (id) {
            "time" -> {
                c.addView(head("الوقت والتاريخ"))
                timeV = Ui.tv(this, 38f, pal.text, true); c.addView(timeV, Ui.lin(Ui.MATCH, 44))
                timeS1 = Ui.tv(this, 15f, pal.muted); c.addView(timeS1, Ui.lin(Ui.MATCH, 20))
                timeS2 = Ui.tv(this, 15f, pal.amber); c.addView(timeS2, Ui.lin(Ui.MATCH, 20))
                card = shell(c, R.drawable.ic_clock, false)
            }
            "weather" -> {
                wxHead = head("حالة الطقس · اضغط للتفاصيل"); c.addView(wxHead)
                wxV = Ui.tv(this, 38f, pal.text, true); c.addView(wxV, Ui.lin(Ui.MATCH, 44))
                wxS = Ui.tv(this, 15f, pal.muted); c.addView(wxS, Ui.lin(Ui.MATCH, 22))
                card = shell(c, R.drawable.ic_weather, true)
                card.setOnClickListener { Panels.weather(this, pal) }
            }
            "trip" -> {
                c.addView(head("كمبيوتر الرحلة"))
                tripV = Ui.tv(this, 26f, pal.text, true); c.addView(tripV, Ui.lin(Ui.MATCH, 36))
                tripS = Ui.tv(this, 15f, pal.muted); c.addView(tripS, Ui.lin(Ui.MATCH, 22))
                c.addView(Ui.tv(this, 13f, pal.muted).apply { text = "اضغط مطولاً للتصفير" }, Ui.lin(Ui.MATCH, 18))
                card = shell(c, R.drawable.ic_trip, false)
                card.setOnLongClickListener {
                    Trip.reset(); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); toast("تم تصفير الرحلة"); refreshTrip(); true
                }
            }
            "matches" -> {
                val mrow = Ui.row(this)
                mBadge = Ui.tv(this, 14f, pal.ok, false, Gravity.CENTER).apply {
                    background = Ui.rounded(pal.card, 8f, 2, pal.ok); setPadding(8, 0, 8, 0)
                }
                mHead = Ui.tv(this, 16f, pal.muted)
                mrow.addView(mBadge, Ui.lin(Ui.WRAP, 22).apply { rightMargin = 8 })
                mrow.addView(mHead, Ui.lin(Ui.WRAP, 24))
                c.addView(mrow, Ui.lin(Ui.MATCH, 26))
                mV = Ui.tv(this, 23f, pal.text, true); c.addView(mV, Ui.lin(Ui.MATCH, 36))
                mS = Ui.tv(this, 15f, pal.muted); c.addView(mS, Ui.lin(Ui.MATCH, 22))
                card = shell(c, R.drawable.ic_ball, false)
                card.setOnClickListener { Panels.matchesDay(this, pal) }
                card.setOnLongClickListener { forceMatches(); toast("جارٍ تحديث المباريات…"); true }
            }
            "music" -> {
                muHead = head("الموسيقى"); c.addView(muHead)
                muV = Ui.tv(this, 22f, pal.text, true); c.addView(muV, Ui.lin(Ui.MATCH, 32))
                val ctr = Ui.row(this)
                playIv = Ui.icon(this, R.drawable.g_play, pal.onAccent)
                ctr.addView(mediaBtn(R.drawable.g_prev) { Media.key(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Ui.lin(48, 48))
                ctr.addView(FrameLayout(this).apply {
                    background = Ui.rounded(pal.accent, 24f)
                    addView(playIv, Ui.at(22, 22, Gravity.CENTER))
                    setOnClickListener { Media.key(this@MainActivity, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); refreshMusic() }
                }, Ui.lin(48, 48).apply { leftMargin = 10; rightMargin = 10 })
                ctr.addView(mediaBtn(R.drawable.g_next) { Media.key(this, KeyEvent.KEYCODE_MEDIA_NEXT) }, Ui.lin(48, 48))
                c.addView(ctr, Ui.lin(Ui.MATCH, 52).apply { topMargin = 4 })
                card = shell(c, R.drawable.ic_music, false)
                card.setOnClickListener {
                    if (!Media.granted(this)) openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    else if (Media.app.isNotEmpty()) launch(Media.app)
                }
            }
            "prayer" -> {
                c.addView(head("الصلاة القادمة"))
                prV = Ui.tv(this, 24f, pal.text, true); c.addView(prV, Ui.lin(Ui.MATCH, 32))
                prS = Ui.tv(this, 15f, pal.muted); c.addView(prS, Ui.lin(Ui.MATCH, 20))
                val qr = Ui.row(this)
                qTxt = Ui.tv(this, 15f, pal.muted)
                qArrow = Ui.icon(this, R.drawable.g_arrow, pal.amber)
                qr.addView(qTxt, Ui.lin(Ui.WRAP, 24))
                qr.addView(qArrow, Ui.lin(22, 22).apply { leftMargin = 6; rightMargin = 6 })
                qr.addView(Ui.tv(this, 15f, pal.muted).apply { text = "القبلة" }, Ui.lin(Ui.WRAP, 24))
                c.addView(qr, Ui.lin(Ui.MATCH, 26))
                card = shell(c, R.drawable.ic_prayer, false)
                card.setOnClickListener { Panels.prayer(this, pal) { if (!locked()) SettingsScreen(this, 5).show() } }
            }
            "azkar" -> {
                azHead = head("الأذكار"); c.addView(azHead)
                azV = Ui.tv(this, 36f, pal.text, true); c.addView(azV, Ui.lin(Ui.MATCH, 42))
                azS = Ui.tv(this, 15f, pal.muted); c.addView(azS, Ui.lin(Ui.MATCH, 22))
                card = shell(c, R.drawable.ic_azkar, false)
                card.setOnClickListener { Azkar.tap(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); refreshAzkar() }
                card.setOnLongClickListener { Azkar.skip(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); refreshAzkar(); true }
            }
            else -> {
                c.addView(Ui.tv(this, 22f, pal.onAccent, true, Gravity.CENTER).apply {
                    text = "ابدأ الرحلة"; background = Ui.rounded(pal.accent, 12f)
                    setOnClickListener { openMaps() }
                }, Ui.lin(Ui.MATCH, 56))
                c.addView(Ui.tv(this, 15f, pal.muted).apply { text = "يفتح تطبيق الخرائط" }, Ui.lin(Ui.MATCH, 22).apply { topMargin = 6 })
                card = shell(c, R.drawable.ic_pin, false)
            }
        }
        card.visibility = if (id in hidden) View.GONE else View.VISIBLE
        return card
    }

    private fun head(s: String): TextView = Ui.tv(this, 16f, pal.muted).apply { text = s; layoutParams = Ui.lin(Ui.MATCH, 22) }

    private fun mediaBtn(res: Int, onClick: () -> Unit): View = FrameLayout(this).apply {
        background = Ui.rounded(pal.card, 24f, 2, pal.line)
        addView(Ui.icon(context, res, pal.text), Ui.at(20, 20, Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    /** إطار البطاقة: نص وبلاطة أيقونة في أقصى اليمين */
    private fun shell(content: LinearLayout, icon: Int, warm: Boolean): View {
        val card = Ui.row(this, Gravity.CENTER_VERTICAL).apply {
            background = Ui.cardBg(pal)
            setPadding(14, 10, 14, 8)
        }
        card.addView(content, Ui.lin(0, Ui.MATCH, 1f))
        val tile = FrameLayout(this).apply {
            background = if (warm) Ui.rounded(pal.warmTile, 18f, 2, pal.warmLine) else Ui.rounded(pal.tile, 18f, 2, pal.tileLine)
            addView(Ui.icon(context, icon), Ui.at(58, 58, Gravity.CENTER))
        }
        card.addView(tile, Ui.lin(76, 76).apply { leftMargin = 14 })
        return card
    }

    // ============================== القائمة السفلية ==============================

    private val fnIds = listOf("home", "nav", "music", "apps", "car", "settings")
    private val fnNames = listOf("الرئيسية", "الملاحة", "الموسيقى", "التطبيقات", "معلومات السيارة", "الإعدادات")
    private val fnIcons = intArrayOf(R.drawable.g_home, R.drawable.g_nav, R.drawable.g_music, R.drawable.g_apps, R.drawable.g_car, R.drawable.g_gear)

    private fun appInfo(pkg: String): Pair<String, Drawable>? = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        Pair(packageManager.getApplicationLabel(ai).toString(), packageManager.getApplicationIcon(ai))
    } catch (e: Exception) { null }

    fun slotTitle(i: Int): String {
        val custom = Prefs.slotLabel(i)
        if (custom.isNotEmpty()) return custom
        val v = Prefs.slotValue(i)
        return if (Prefs.slotType(i) == "app") appInfo(v)?.first ?: v
        else fnNames.getOrElse(fnIds.indexOf(v)) { v }
    }

    private fun rebuildFooter() {
        footer?.let { design.removeView(it) }
        val f = FrameLayout(this)
        design.addView(f, 0, Ui.abs(1280, 80, 0, 640))
        footer = f
        f.addView(View(this).apply { setBackgroundColor(if (editMode) pal.accent else pal.line) }, Ui.abs(1280, 2, 0, 0))
        val bar = Ui.row(this, Gravity.CENTER)
        f.addView(bar, Ui.abs(1280, 78, 0, 2))
        for (i in 5 downTo 0) bar.addView(slotView(i), Ui.lin(0, Ui.MATCH, 1f).apply { leftMargin = 4; rightMargin = 4 })
    }

    private fun slotView(i: Int): View {
        val v = Ui.col(this, Gravity.CENTER)
        val type = Prefs.slotType(i); val value = Prefs.slotValue(i)
        val active = type == "fn" && value == "home"
        val color = if (active) pal.accent else pal.muted
        if (editMode) v.background = Ui.rounded(pal.card, 12f, 2, pal.accent)
        val top = Ui.row(this, Gravity.CENTER)
        top.addView(View(this).apply { setBackgroundColor(if (active && !editMode) pal.accent else 0) }, Ui.lin(120, 3))
        v.addView(top, Ui.lin(Ui.MATCH, 3).apply { bottomMargin = 7 })
        val icRow = Ui.row(this, Gravity.CENTER)
        val iv: ImageView = if (type == "app") {
            ImageView(this).apply { setImageDrawable(appInfo(value)?.second); scaleType = ImageView.ScaleType.FIT_CENTER }
        } else {
            Ui.icon(this, fnIcons[fnIds.indexOf(value).coerceAtLeast(0)], color)
        }
        icRow.addView(iv, Ui.lin(30, 30))
        // نقطة صغيرة عندما يعمل تطبيق الموسيقى
        val playing = Media.playing && ((type == "app" && value == Media.app) || (type == "fn" && value == "music"))
        if (playing) icRow.addView(View(this).apply { background = Ui.rounded(pal.ok, 4f) }, Ui.lin(8, 8).apply { leftMargin = 6 })
        v.addView(icRow, Ui.lin(Ui.MATCH, 30))
        val label = if (editMode) "✎ تعديل" else slotTitle(i)
        if (Prefs.slotLabels || editMode) {
            v.addView(Ui.tv(this, 16f, if (editMode) pal.accent else color, active, Gravity.CENTER).apply { text = label }, Ui.lin(Ui.MATCH, 26).apply { topMargin = 4 })
        }
        v.setOnClickListener { if (editMode) editSlot(i) { rebuildFooter() } else openSlot(i) }
        v.setOnLongClickListener {
            if (!locked()) {
                editMode = !editMode
                if (editMode) { toast("وضع التعديل: اضغط أي خانة لتغييرها، واضغط مطولاً للخروج"); h.removeCallbacks(exitEdit); h.postDelayed(exitEdit, 30000) }
                rebuildFooter()
            }
            true
        }
        return v
    }

    private val exitEdit = Runnable { if (editMode) { editMode = false; rebuildFooter() } }

    /** تغيير خانة: تطبيق من الجهاز أو وظيفة داخلية */
    fun editSlot(i: Int, done: () -> Unit) {
        if (locked()) return
        h.removeCallbacks(exitEdit); h.postDelayed(exitEdit, 30000)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("الخانة ${i + 1}: ${slotTitle(i)}")
            .setItems(arrayOf("تطبيق من الجهاز", "وظيفة داخلية", "الافتراضي")) { _, w ->
                when (w) {
                    0 -> Drawer.show(this, pal, "اختر تطبيقاً للخانة") { pkg -> Prefs.setSlot(i, "app", pkg); Prefs.setSlotLabel(i, ""); done() }
                    1 -> AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("وظيفة")
                        .setItems(fnNames.toTypedArray()) { _, f -> Prefs.setSlot(i, "fn", fnIds[f]); Prefs.setSlotLabel(i, ""); done() }
                        .show()
                    else -> { Prefs.resetSlot(i); done() }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun openSlot(i: Int) {
        val value = Prefs.slotValue(i)
        if (Prefs.slotType(i) == "app") { if (!launch(value)) toast("التطبيق غير موجود"); return }
        when (value) {
            "nav" -> openMaps()
            "music" -> openMusic()
            "apps" -> Drawer.show(this, pal, "التطبيقات") { launch(it) }
            "car" -> carInfo()
            "settings" -> openSettingsScreen()
        }
    }

    private fun openSettingsScreen() {
        if (locked()) return
        SettingsScreen(this).show()
    }

    fun launch(pkg: String): Boolean {
        val it = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Prefs.used(pkg); true } catch (e: Exception) { false }
    }

    private fun openMaps() {
        for (i in 0..5) if (Prefs.slotType(i) == "app" && Prefs.slotValue(i) != "" && slotIsNav(i) && launch(Prefs.slotValue(i))) return
        for (p in arrayOf("com.waze", "com.google.android.apps.maps", "com.here.app.maps", "ru.yandex.yandexnavi")) if (launch(p)) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast("لا يوجد تطبيق خرائط") }
    }

    private fun slotIsNav(i: Int): Boolean {
        val p = Prefs.slotValue(i)
        return p.contains("maps") || p.contains("waze") || p.contains("navi")
    }

    private fun openMusic() {
        if (Media.app.isNotEmpty() && launch(Media.app)) return
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("اضغط مطولاً على القائمة لاختيار تطبيق الموسيقى") }
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
        }
    }

    private fun carInfo() {
        val dist = String.format(Locale.US, "%.1f", Trip.distanceM / 1000.0)
        val msg = "المسافة: $dist كم\nمدة الحركة: ${Trip.movingMs / 60000} دقيقة\nمتوسط السرعة: ${Trip.avgKmh().roundToInt()} كم/س\n" +
            "أعلى سرعة: ${Trip.maxKmh.roundToInt()} كم/س\nالسرعة الآن: ${Trip.speedKmh.roundToInt()} كم/س\n" +
            "GPS: " + (if (Trip.gpsLive()) "متصل" else "غير متصل") + "\nالمدينة: ${Loc.name()}"
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("معلومات السيارة والرحلة").setMessage(msg)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("تصفير الرحلة") { _, _ -> Trip.reset(); refreshTrip() }
            .setNegativeButton("سجل الرحلات") { _, _ -> Panels.tripLog(this) }
            .show()
    }

    /** إغلاق التطبيقات التي تعمل في الخلفية لتحرير الذاكرة */
    private fun closeAll() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        Thread {
            try {
                val list = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                for (ri in list) {
                    val p = ri.activityInfo.packageName
                    if (p == packageName) continue
                    try { am.killBackgroundProcesses(p) } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
            Thread.sleep(600)
            val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val freed = ((after - before) / (1024 * 1024)).coerceAtLeast(0)
            h.post { toast(if (freed > 0) "تم إغلاق تطبيقات الخلفية وتحرير $freed ميجا" else "تم إغلاق تطبيقات الخلفية") }
        }.start()
    }

    fun forceWeather() { weatherAt = 0L }
    fun forceMatches() { matchesAt = 0L }
    fun matchesUpdatedText(): String = if (matchesAt == 0L) "—" else SimpleDateFormat("HH:mm", Locale.US).format(Date(matchesAt))

    // ============================== اللوحة السريعة ==============================
    /** ضغطة مطولة على الساعة: السطوع، الصوت، الثيم، العرض على الزجاج */
    private fun quickPanel() {
        val box = Ui.col(this, Gravity.TOP or Gravity.RIGHT).apply { setPadding(20, 12, 20, 12) }
        lateinit var dlg: AlertDialog
        fun row(label: String, vararg b: Pair<String, () -> Unit>) {
            val r = Ui.row(this, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
            for ((txt, fn) in b) r.addView(Ui.tv(this, 18f, pal.text, true, Gravity.CENTER).apply {
                text = txt; background = Ui.rounded(pal.card, 12f, 2, pal.line); setPadding(18, 0, 18, 0)
                setOnClickListener { fn() }
            }, Ui.lin(Ui.WRAP, 52).apply { leftMargin = 8 })
            r.addView(Ui.tv(this, 18f, pal.text).apply { text = label }, Ui.lin(0, 52, 1f).apply { leftMargin = 16 })
            box.addView(r, Ui.lin(Ui.MATCH, 60).apply { topMargin = 6 })
        }
        fun bright(v: Float) { Prefs.bright = v; lastSlow = 0L; refreshSlow() }
        row("السطوع", Pair("تلقائي") { bright(-1f) }, Pair("25%") { bright(0.25f) }, Pair("50%") { bright(0.5f) },
            Pair("75%") { bright(0.75f) }, Pair("100%") { bright(1f) })
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        row("الصوت", Pair("−") { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) },
            Pair("+") { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) })
        val themes = listOf("auto", "dark", "light", "amber")
        row("الثيم", Pair("تلقائي") { Prefs.theme = themes[0]; dlg.dismiss(); applySettings() },
            Pair("داكن") { Prefs.theme = themes[1]; dlg.dismiss(); applySettings() },
            Pair("فاتح") { Prefs.theme = themes[2]; dlg.dismiss(); applySettings() },
            Pair("كهرماني") { Prefs.theme = themes[3]; dlg.dismiss(); applySettings() })
        row("شاشات", Pair("على الزجاج") { dlg.dismiss(); Panels.hud(this, pal) }, Pair("الانتظار") { dlg.dismiss(); showStandby() })
        dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("لوحة سريعة").setView(box).setPositiveButton("إغلاق", null).create()
        dlg.show()
    }

    // ============================== وضع الانتظار ==============================
    /** شاشة داكنة خفيفة: ساعة كبيرة والصلاة القادمة. تختفي باللمس أو الحركة */
    private fun showStandby() {
        if (standbyView != null) return
        val v = Ui.col(this, Gravity.CENTER).apply { setBackgroundColor(0xFF000000.toInt()) }
        val now = System.currentTimeMillis()
        v.addView(Ui.tv(this, 200f, pal.accent, true, Gravity.CENTER).apply { text = SimpleDateFormat("HH:mm", Locale.US).format(Date(now)) }, Ui.lin(Ui.MATCH, 230))
        v.addView(Ui.tv(this, 28f, 0xFF9AA7B4.toInt(), false, Gravity.CENTER).apply { text = toLatinDigits(SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date(now))) + " · " + Astro.hijriText(now) }, Ui.lin(Ui.MATCH, 44))
        val (idx, at) = Astro.nextPrayer(now, Loc.lat(), Loc.lon())
        v.addView(Ui.tv(this, 30f, 0xFFF5B841.toInt(), true, Gravity.CENTER).apply { text = "${Astro.PRAYER_NAMES[idx]} " + SimpleDateFormat("HH:mm", Locale.US).format(Date(at)) }, Ui.lin(Ui.MATCH, 50).apply { topMargin = 20 })
        design.addView(v, Ui.abs(1280, 720, 0, 0))
        standbyView = v
        refreshSlow()
    }

    private fun hideStandby() {
        standbyView?.let { design.removeView(it) }
        standbyView = null
        lastTouch = System.currentTimeMillis()
        refreshSlow()
    }

    // ============================== النسخ الاحتياطي ==============================
    fun backup(save: Boolean) {
        try {
            if (save) startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "rafiq-settings.json"), 41)
            else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 42)
        } catch (e: Exception) { toast("مدير الملفات غير متوفر على هذه الشاشة") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            if (requestCode == 41) {
                contentResolver.openOutputStream(uri)?.use { it.write(Prefs.exportJson().toByteArray()) }
                toast("تم حفظ الإعدادات")
            } else if (requestCode == 42) {
                val txt = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return
                Prefs.importJson(txt); applySettings(); toast("تم استرجاع الإعدادات")
            }
        } catch (e: Exception) { toast("تعذّر: الملف غير صالح") }
    }

    // ============================== التحديث ==============================
    private var updBusy = false

    /** فحص التحديث: يدوي من الإعدادات، أو يومي تلقائي */
    fun checkUpdate(manual: Boolean) {
        if (updBusy) return
        if (!manual && (moving() || (Prefs.wifiOnly && !Updater.onWifi(this)))) return
        updBusy = true
        if (manual) toast("جارٍ التحقق…")
        Thread {
            val info = Updater.check(applicationContext)
            h.post {
                updBusy = false
                if (info == null) { if (manual) toast("لديك آخر إصدار"); return@post }
                val mb = String.format(Locale.US, "%.1f", info.size / 1048576.0)
                alert(Alert(pal.accent, R.drawable.g_bell, true, "تحديث متاح: الإصدار ${info.build} ($mb ميجا)",
                    "اضغط للتحديث الآن · " + info.notes, false) { installUpdate(info) })
            }
        }.start()
    }

    private fun installUpdate(info: Updater.Info) {
        if (moving()) { toast("التحديث غير متاح أثناء القيادة"); return }
        if (Prefs.wifiOnly && !Updater.onWifi(this)) { toast("التحميل عبر الواي فاي فقط مفعّل في الإعدادات"); return }
        if (!Updater.canInstall(this)) { toast("اسمح للتطبيق بتثبيت التحديثات ثم اضغط التحديث مرة أخرى"); Updater.askPermission(this); return }
        toast("جارٍ تحميل التحديث…")
        Thread {
            val err = Updater.install(applicationContext, info)
            h.post { if (err != null) toast(err) }
        }.start()
    }

    // ============================== شريط التنبيهات ==============================

    private class Alert(val color: Int, val icon: Int, val tint: Boolean, val title: String, val text: String,
                        val sound: Boolean, val onTap: (() -> Unit)? = null)

    private val queue = ArrayList<Alert>()
    private var current: Alert? = null
    private val hideBanner = Runnable { current = null; bannerHost.visibility = View.GONE; bannerHost.removeAllViews(); pumpBanner() }

    private fun alert(a: Alert) {
        queue.add(a)
        if (current == null) pumpBanner()
    }

    private fun pumpBanner() {
        if (!::bannerHost.isInitialized) return
        val a = current ?: (if (queue.isEmpty()) null else queue.removeAt(0))
        if (a == null) return
        val fresh = current == null
        current = a
        bannerHost.removeAllViews()
        val row = Ui.row(this, Gravity.CENTER_VERTICAL).apply {
            val edge = Ui.rounded(a.color, 14f)
            val body = Ui.rounded(pal.card, 14f, 2, pal.line)
            background = LayerDrawable(arrayOf<Drawable>(edge, body)).apply { setLayerInset(1, 0, 0, 7, 0) }
            setPadding(12, 8, 18, 8)
        }
        row.addView(Ui.icon(this, R.drawable.g_x, pal.muted).apply {
            setPadding(12, 12, 12, 12); background = Ui.rounded(pal.bg, 12f, 2, pal.line)
            setOnClickListener { h.removeCallbacks(hideBanner); hideBanner.run() }
        }, Ui.lin(48, 48))
        val col = Ui.col(this)
        col.addView(Ui.tv(this, 19f, pal.text, true).apply { text = a.title }, Ui.lin(Ui.MATCH, 30))
        col.addView(Ui.tv(this, 16f, pal.muted).apply { text = a.text; maxLines = 2 }, Ui.lin(Ui.MATCH, Ui.WRAP))
        row.addView(col, Ui.lin(0, Ui.WRAP, 1f).apply { leftMargin = 14; rightMargin = 14 })
        row.addView(FrameLayout(this).apply {
            background = Ui.rounded(pal.tile, 14f, 2, a.color)
            addView(Ui.icon(context, a.icon, if (a.tint) a.color else null), Ui.at(40, 40, Gravity.CENTER))
        }, Ui.lin(60, 60))
        if (a.onTap != null) row.setOnClickListener { a.onTap?.invoke(); h.removeCallbacks(hideBanner); hideBanner.run() }
        bannerHost.addView(row, Ui.at(Ui.MATCH, Ui.WRAP, Gravity.TOP))
        bannerHost.visibility = View.VISIBLE
        if (fresh) {
            if (a.sound && Prefs.alertOn("sound")) beep()
            h.removeCallbacks(hideBanner); h.postDelayed(hideBanner, 15000)
        }
    }

    private fun beep() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        } catch (e: Exception) {}
    }

    private fun checkAlerts(now: Long) {
        // تجاوز السرعة (الصفارة من الخدمة، هنا الشريط فقط)
        val lim = Prefs.alertSpeed
        if (lim > 0 && Prefs.alertOn("speed")) {
            if (!speedShown && Trip.speedKmh > lim) {
                speedShown = true
                alert(Alert(pal.danger, R.drawable.g_bell, true, "تجاوزت حد التنبيه $lim كم/س", "السرعة الآن ${Trip.speedKmh.roundToInt()} كم/س", false))
            } else if (speedShown && Trip.speedKmh < lim - 5) speedShown = false
        }
        // الصلاة: قبلها ثم عند الأذان
        if (nextAt > 0) {
            val preMs = Prefs.preMin * 60000L
            if (Prefs.alertOn("pre") && preMs > 0 && now >= nextAt - preMs && now < nextAt && preShownFor != nextAt) {
                preShownFor = nextAt
                alert(Alert(pal.accent, R.drawable.ic_prayer, false, "بعد ${Prefs.preMin} دقائق: صلاة ${Astro.PRAYER_NAMES[nextIdx]}", Loc.name(), false))
            }
            if (now >= nextAt && adhanShownFor != nextAt) {
                adhanShownFor = nextAt
                if (Prefs.alertOn("prayer")) alert(Alert(pal.accent, R.drawable.ic_prayer, false,
                    "حان وقت صلاة ${Astro.PRAYER_NAMES[nextIdx]}", "بعد الأذان: " + Duas.AFTER_ADHAN, false))
                refreshPrayer(now)
            }
        }
        // دعاء الركوب والسفر
        if (Trip.rideEvent != seenRide) {
            seenRide = Trip.rideEvent
            if (Prefs.alertOn("dua")) alert(Alert(pal.amber, R.drawable.g_car, true, "دعاء الركوب", Duas.RIDE, false))
        }
        if (Trip.travelEvent != seenTravel) {
            seenTravel = Trip.travelEvent
            if (Prefs.alertOn("dua")) alert(Alert(pal.amber, R.drawable.g_nav, true, "دعاء السفر", Duas.TRAVEL, false))
        }
        // الاستراحة: كل ساعتين قيادة متواصلة
        if (Prefs.alertOn("rest") && Trip.driveStart > 0 && now - Trip.lastMovingAt < 60000) {
            if (restFor != Trip.driveStart) { restFor = Trip.driveStart; restCount = 0 }
            val hrs = (now - Trip.driveStart) / 3600000L
            if (hrs >= 2L * (restCount + 1)) {
                restCount++
                alert(Alert(pal.amber, R.drawable.g_car, true, "مضى ${hrs} ساعات على القيادة", "خذ استراحة قصيرة لسلامتك", true))
            }
        }
    }

    /** فحوص بطيئة: الجمعة، والابتعاد عن المدينة المختارة */
    private fun dirName(b: Float): String {
        val n = arrayOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")
        return n[(((b + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }

    private fun checkSlowAlerts(now: Long) {
        // الصيام والمناسبات: تذكير بعد المغرب بصيام الغد، وتهنئة العيد صباحاً
        if (Prefs.alertOn("fast")) {
            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
            val pt = Astro.prayerMillis(Calendar.getInstance(), Loc.lat(), Loc.lon())
            if (now >= pt[4] && Prefs.str("fastDay") != dayKey) {
                val tmr = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                val hj = Astro.hijri(tmr.timeInMillis)
                val dow = tmr.get(Calendar.DAY_OF_WEEK)
                val why = when {
                    hj[1] == 8 -> ""
                    hj[1] == 9 && hj[0] == 1 -> ""
                    hj[1] == 11 && hj[0] == 9 -> "غداً يوم عرفة"
                    hj[0] in 13..15 && !(hj[1] == 11 && hj[0] == 13) -> "غداً من الأيام البيض"
                    dow == Calendar.MONDAY -> "غداً الاثنين"
                    dow == Calendar.THURSDAY -> "غداً الخميس"
                    else -> ""
                }
                Prefs.putStr("fastDay", dayKey)
                if (why.isNotEmpty()) alert(Alert(pal.amber, R.drawable.ic_prayer, false, why, "تذكير بسنة الصيام لمن أراد", false))
            }
            val hj = Astro.hijri(now)
            val eid = (hj[1] == 9 && hj[0] == 1) || (hj[1] == 11 && hj[0] == 10)
            if (eid && Prefs.str("eidDay") != dayKey && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 5..14) {
                Prefs.putStr("eidDay", dayKey)
                alert(Alert(pal.amber, R.drawable.ic_prayer, false, "عيدكم مبارك", "تقبّل الله منا ومنكم", false))
            }
        }
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val cal = Calendar.getInstance()
        if (Prefs.alertOn("friday") && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && cal.get(Calendar.HOUR_OF_DAY) in 6..12
            && Prefs.str("friDay") != today) {
            Prefs.putStr("friDay", today)
            alert(Alert(pal.amber, R.drawable.ic_azkar, false, "يوم الجمعة", Duas.FRIDAY, false))
        }
        if (Loc.manual() && Trip.gpsLive() && Prefs.str("farDay") != today) {
            val c = Cities.byName(Prefs.city)
            if (Cities.km(Prefs.lat, Prefs.lon, c.lat, c.lon) > 50) {
                Prefs.putStr("farDay", today)
                alert(Alert(pal.accent, R.drawable.g_loc, true, "أنت بعيد عن ${c.name}",
                    "اضغط هنا لاستخدام موقعك الحالي (${Cities.nearName(Prefs.lat, Prefs.lon)}) للصلاة والطقس", false) {
                    Prefs.cityMode = "auto"; forceWeather(); applySettings()
                })
            }
        }
    }

    // ============================== التحديث ==============================

    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            val now = System.currentTimeMillis()
            Ui.setT(clockTv, SimpleDateFormat("HH:mm", Locale.US).format(Date(now)))
            gauge.set(Trip.speedKmh.roundToInt(), Trip.gpsLive())
            refreshTrip()
            refreshMusic()
            checkAlerts(now)
            // وضع الانتظار: توقف 10 دقائق بلا لمس، ويخرج عند الحركة
            if (standbyView != null && Trip.speedKmh > 5f) hideStandby()
            else if (standbyView == null && Prefs.standby && Trip.speedKmh < 1f &&
                now - maxOf(Trip.lastMovingAt, lastTouch) > 10 * 60000L) showStandby()
            Ui.setT(headingTv, if (Trip.bearing >= 0f && Trip.speedKmh > 8f) dirName(Trip.bearing) else "")
            if (now - lastStatus > 5000) { lastStatus = now; refreshStatus() }
            if (now - lastSlow > 30000) {
                lastSlow = now
                val n = Astro.isNight(now, Loc.lat(), Loc.lon())
                if (n != night) {
                    night = n
                    if (Prefs.theme == "auto") { build(); h.postDelayed(this, 1000); return }
                }
                refreshSlow()
                checkSlowAlerts(now)
            }
            maybeFetch(now)
            h.postDelayed(this, 1000)
        }
    }

    private fun refreshAll() {
        Ui.setT(clockTv, SimpleDateFormat("HH:mm", Locale.US).format(Date()))
        gauge.set(Trip.speedKmh.roundToInt(), Trip.gpsLive())
        refreshTrip(); refreshMusic(); refreshStatus(); refreshSlow(); refreshWeather(); refreshMatches()
    }

    private fun refreshSlow() {
        val now = System.currentTimeMillis()
        val df = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar"))
        Ui.setT(timeV, SimpleDateFormat("HH:mm", Locale.US).format(Date(now)))
        Ui.setT(timeS1, toLatinDigits(df.format(Date(now))))
        Ui.setT(timeS2, Astro.hijriText(now))
        dayIcon.setImageResource(if (night) R.drawable.g_moon else R.drawable.g_sun)
        val lp = window.attributes
        val want = when {
            standbyView != null -> 0.15f
            Prefs.bright >= 0f -> Prefs.bright
            Prefs.nightDim && night -> 0.35f
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (lp.screenBrightness != want) { lp.screenBrightness = want; window.attributes = lp }
        refreshPrayer(now)
        refreshAzkar()
    }

    private fun toLatinDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '٠'..'٩') ('0' + (ch - '٠')) else ch)
        return sb.toString()
    }

    private fun refreshPrayer(now: Long) {
        val (idx, at) = Astro.nextPrayer(now, Loc.lat(), Loc.lon())
        // لا نفوّت أذاناً لم يُعرض بعد
        if (!(nextAt in 1..now && adhanShownFor != nextAt)) { nextIdx = idx; nextAt = at }
        val mins = ((at - now) / 60000L).toInt()
        val left = if (mins >= 60) "بعد ${mins / 60} س ${mins % 60} د" else "بعد $mins د"
        Ui.setT(prV, "${Astro.PRAYER_NAMES[idx]} · $left")
        Ui.setT(prS, "الأذان " + SimpleDateFormat("HH:mm", Locale.US).format(Date(at)) + " · " + Loc.name())
        val q = Astro.qibla(Loc.lat(), Loc.lon()).toFloat()
        val heading = Trip.bearing
        if (heading >= 0f && Trip.gpsLive()) {
            qArrow.rotation = q - heading
            Ui.setT(qTxt, "بالنسبة لاتجاه السيارة")
        } else {
            qArrow.rotation = q
            Ui.setT(qTxt, "${q.roundToInt()}° من الشمال")
        }
    }

    private fun refreshAzkar() {
        val now = System.currentTimeMillis()
        val list = Azkar.list(now)
        val s = Azkar.state(now)
        Ui.setT(azHead, if (Azkar.isMorning(now)) "أذكار الصباح" else "أذكار المساء")
        if (s[0] >= list.size) {
            Ui.setT(azHead, "دعاء اليوم · تمت الأذكار ✓")
            Ui.setT(azV, "تقبّل الله")
            Ui.setT(azS, Duas.today())
        } else {
            Ui.setT(azV, "${s[1]} / ${list[s[0]].count}")
            Ui.setT(azS, list[s[0]].text)
        }
    }

    private fun refreshTrip() {
        val dist = String.format(Locale.US, "%.1f", Trip.distanceM / 1000.0)
        Ui.setT(tripV, "$dist كم · ${Trip.movingMs / 60000} د")
        Ui.setT(tripS, "متوسط ${Trip.avgKmh().roundToInt()} · أعلى ${Trip.maxKmh.roundToInt()} كم/س")
    }

    private var lastPlaying = false
    private fun refreshMusic() {
        if (Media.title.isNotEmpty()) {
            val app = appInfo(Media.app)?.first ?: ""
            Ui.setT(muHead, if (app.isEmpty()) "يتم التشغيل الآن" else "يتم التشغيل الآن · $app")
            Ui.setT(muV, if (Media.artist.isEmpty()) Media.title else "${Media.title} · ${Media.artist}")
        } else if (!Media.granted(this)) {
            Ui.setT(muHead, "الموسيقى")
            Ui.setT(muV, "اضغط لعرض اسم المقطع")
        } else {
            Ui.setT(muHead, "الموسيقى")
            Ui.setT(muV, "لا يوجد تشغيل")
        }
        val want = if (Media.playing) R.drawable.g_pause else R.drawable.g_play
        if (playIv.tag != want) { playIv.tag = want; playIv.setImageResource(want) }
        if (Media.playing != lastPlaying) { lastPlaying = Media.playing; if (!editMode) rebuildFooter() }
    }

    @Suppress("DEPRECATION")
    private fun refreshStatus() {
        val bt = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }
        val gps = try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val wifi = try { (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (e: Exception) { false }
        btIv.imageTintList = ColorStateList.valueOf(if (bt) pal.accent else pal.muted)
        gpsIv.imageTintList = ColorStateList.valueOf(if (gps) pal.accent else pal.muted)
        wifiIv.imageTintList = ColorStateList.valueOf(if (wifi) pal.accent else pal.muted)
    }

    private fun refreshWeather() {
        val w = weather
        if (w == null) { Ui.setT(wxV, "--°"); Ui.setT(wxS, Loc.name() + " · بانتظار الاتصال"); return }
        Ui.setT(wxV, "${w.temp}°")
        Ui.setT(wxHead, "حالة الطقس · " + SimpleDateFormat("HH:mm", Locale.US).format(Date(weatherAt)))
        Ui.setT(wxS, if (w.desc.isEmpty()) w.city else "${w.city} · ${w.desc}")
    }

    private fun refreshMatches() {
        val m = matches?.main
        if (m == null && matches == null && Prefs.str("mcV").isNotEmpty()) {
            // آخر نتيجة محفوظة تظهر فوراً قبل الاتصال
            Ui.setT(mHead, Prefs.str("mcH")); Ui.setT(mV, Prefs.str("mcV")); Ui.setT(mS, Prefs.str("mcS"))
            mBadge.visibility = View.VISIBLE; Ui.setT(mBadge, "آخر تحديث"); mBadge.setTextColor(pal.muted)
            return
        }
        if (m == null) {
            Ui.setT(mHead, "المباريات"); mBadge.visibility = View.GONE
            Ui.setT(mV, if (matchesAt == 0L) "جارٍ التحميل…" else "لا توجد مباريات قريبة")
            Ui.setT(mS, "السعودي والأوروبي"); return
        }
        Ui.setT(mHead, m.league)
        mBadge.visibility = View.VISIBLE
        when (m.state) {
            "in" -> { Ui.setT(mBadge, "مباشر ${m.clock}"); mBadge.setTextColor(pal.ok) }
            "post" -> { Ui.setT(mBadge, "انتهت"); mBadge.setTextColor(pal.muted) }
            else -> { Ui.setT(mBadge, "قادم ${Net.hhmm(m.time)}"); mBadge.setTextColor(pal.muted) }
        }
        Ui.setT(mV, if (m.state == "pre") "${m.home} × ${m.away}" else "${m.home} ${m.hs} — ${m.aws} ${m.away}")
        val n = matches?.next
        Ui.setT(mS, if (n != null) "قادم: ${n.home} × ${n.away} ${Net.hhmm(n.time)}" else "")
        if (n == null) Ui.setT(mS, "آخر تحديث " + matchesUpdatedText())
        Prefs.putStr("mcH", mHead.text.toString()); Prefs.putStr("mcV", mV.text.toString()); Prefs.putStr("mcS", mS.text.toString())
    }

    /** تنبيهات فرقك المفضلة: قبل ربع ساعة، البداية، الأهداف، النهاية */
    private val preMatchShown = HashSet<String>()
    private fun matchAlerts(card: Net.MatchCard) {
        val fav = Prefs.favTeams()
        if (fav.isEmpty() || !Prefs.alertOn("match")) return
        val t = System.currentTimeMillis()
        for (m in card.all) {
            if (m.home !in fav && m.away !in fav) continue
            val key = m.home + "|" + m.away
            val cur = m.state + ":" + m.hs + "-" + m.aws
            if (m.state == "pre" && m.time - t in 1..(15 * 60000L) && Prefs.bool("m_pre", true) && preMatchShown.add(key)) {
                alert(Alert(pal.ok, R.drawable.ic_ball, false, "بعد ربع ساعة: ${m.home} × ${m.away}", m.league + " · " + Net.hhmm(m.time), true))
            }
            val before = lastScores[key]
            lastScores[key] = cur
            if (before == null || before == cur) continue
            if (before.startsWith("pre") && m.state == "in") {
                if (Prefs.bool("m_start", true)) alert(Alert(pal.ok, R.drawable.ic_ball, false, "بدأت المباراة", "${m.home} × ${m.away} · ${m.league}", true))
            } else if (m.state == "post" && !before.startsWith("post")) {
                if (Prefs.bool("m_final", true)) alert(Alert(pal.ok, R.drawable.ic_ball, false, "النتيجة النهائية: ${m.home} ${m.hs} — ${m.aws} ${m.away}", m.league, true))
            } else if (m.state == "in") {
                if (Prefs.bool("m_goal", true)) alert(Alert(pal.ok, R.drawable.ic_ball, false, "هدف! ${m.home} ${m.hs} — ${m.aws} ${m.away}", "الدقيقة ${m.clock} · ${m.league}", true))
            }
        }
    }

    private fun favLive(): Boolean {
        val fav = Prefs.favTeams()
        return matches?.all?.any { it.state == "in" && (it.home in fav || it.away in fav) } == true
    }

    private fun maybeFetch(now: Long) {
        if (Prefs.autoUpdate && now - Prefs.long("updChecked") > 24 * 3600000L && now - Prefs.long("updTried") > 3600000L) {
            Prefs.putLong("updTried", now)
            checkUpdate(false)
        }
        if (!weatherBusy && now - weatherAt > 30 * 60000L) {
            weatherBusy = true
            val lat = Loc.lat(); val lon = Loc.lon(); val city = Loc.name()
            Thread {
                val w = Net.weather(lat, lon, city)
                h.post {
                    weatherBusy = false
                    weatherAt = if (w != null) System.currentTimeMillis() else System.currentTimeMillis() - 25 * 60000L
                    if (w != null) {
                        weather = w
                        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        if (w.severe && Prefs.alertOn("weather") && Prefs.str("wxDay") != today) {
                            Prefs.putStr("wxDay", today)
                            alert(Alert(pal.amber, R.drawable.ic_weather, false, "تنبيه طقس: ${w.desc}", "${w.city} · قُد بحذر", true))
                        }
                        if (w.pm10 >= 300 && Prefs.alertOn("dust") && Prefs.str("dustDay") != today) {
                            Prefs.putStr("dustDay", today)
                            alert(Alert(pal.amber, R.drawable.ic_weather, false, "غبار شديد", "${w.city} · الرؤية قد تنخفض، قُد بحذر", true))
                        }
                        if (w.temp >= 45 && Prefs.alertOn("heat") && Prefs.str("heatDay") != today) {
                            Prefs.putStr("heatDay", today)
                            alert(Alert(pal.danger, R.drawable.ic_weather, false, "حرارة عالية ${w.temp}°", "${w.city} · اشرب الماء وتجنّب ترك أحد في السيارة", true))
                        }
                    }
                    refreshWeather()
                }
            }.start()
        }
        val live = matches?.anyLive() == true || favLive()
        val interval = if (live) 60000L else 20 * 60000L
        val wanted = "matches" !in Prefs.hidden() || (Prefs.favTeams().isNotEmpty() && Prefs.alertOn("match"))
        if (wanted && !matchesBusy && now - matchesAt > interval) {
            matchesBusy = true
            Thread {
                val m = Net.matches()
                h.post {
                    matchesBusy = false
                    matchesAt = System.currentTimeMillis()
                    if (m.main != null || matches == null) matches = m
                    matchAlerts(m)
                    refreshMatches()
                }
            }.start()
        }
    }
}
