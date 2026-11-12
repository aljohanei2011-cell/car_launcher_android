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
    private val labelCache = HashMap<String, String>()
    private var standbyClock: TextView? = null
    private var qiblaDeg = 0f
    private var matchesFail = false
    private var matchesTry = 0L
    private var matchesFullAt = 0L
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
    private var cmpV: TextView? = null
    private var cmpS: TextView? = null
    private var occV: TextView? = null
    private var occS: TextView? = null
    private var barH = 104

    // بيانات الشبكة (تبقى عند إعادة بناء الواجهة)
    private var weather: Net.Weather? = null
    private var weatherAt = 0L
    private var weatherFrom = 0L        // وقت آخر طقس ناجح (للعرض)
    private var weatherFail = false
    private var netCb: android.net.ConnectivityManager.NetworkCallback? = null
    private var lastNetKick = 0L
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
        loadCachedWeather()
        build()
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            TripService.start(this)
        }
        PrayerAlarm.schedule(this)
        if (Prefs.autoPlay && !autoPlayed) {
            autoPlayed = true
            h.postDelayed({
                // لا نرسل «تشغيل» إن كان الصوت يعمل أصلاً
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (!am.isMusicActive) Media.key(this, KeyEvent.KEYCODE_MEDIA_PLAY)
            }, 8000)
        }
        if (!Prefs.bool("hint1", false)) {
            Prefs.putBool("hint1", true)
            alert(Alert(pal.accent, R.drawable.g_gear, true, "مرحباً بك في رفيق الطريق",
                "الإعدادات: آخر خانة في القائمة السفلية · اضغط أي بطاقة لتفاصيلها · اضغط مطولاً على الساعة للوحة السريعة", false))
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
        // إن مُنح إذن الموقع لاحقاً من إعدادات النظام نبدأ خدمة GPS هنا
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) TripService.start(this)
        lastSlow = 0L; lastStatus = 0L
        h.removeCallbacks(tick); h.post(tick)
        // عند عودة الإنترنت نحدّث الطقس والمباريات فوراً بدل الانتظار
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    h.post {
                        val t = System.currentTimeMillis()
                        if (t - lastNetKick > 15000) {
                            lastNetKick = t
                            if (weatherFail || weather == null) forceWeather()
                            if (matchesFail || matches == null) forceMatches()
                            lastStatus = 0L
                        }
                    }
                }
                override fun onLost(network: android.net.Network) { h.post { lastStatus = 0L } }
            }
            cm.registerDefaultNetworkCallback(cb); netCb = cb
        } catch (e: Exception) {}
    }

    override fun onStop() {
        started = false
        h.removeCallbacks(tick)
        try {
            netCb?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        netCb = null
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

    fun palNow(): Palette = Palette.current(night)

    /** تطبيق أي تغيير من الإعدادات فوراً */
    fun applySettings() {
        night = Astro.isNight(System.currentTimeMillis(), Loc.lat(), Loc.lon())
        build()
        PrayerAlarm.schedule(this)
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun moving() = Trip.kmh() > 10f
    private fun locked(): Boolean {
        if (Prefs.lockDriving && moving()) { toast("التعديل مقفل أثناء القيادة"); return true }
        return false
    }

    // ============================== بناء الواجهة ==============================

    private fun build() {
        pal = palNow()
        Ui.fontScale = if (Prefs.bigFont) 1.1f else 1f
        standbyView = null; standbyClock = null
        barH = when (Prefs.barSize) { 0 -> 80; 2 -> 92; else -> 104 }
        if (Prefs.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val (root, d) = Ui.scaled(this, pal.bg)
        design = d
        buildHeader(d)
        buildBody(d)
        footer = null
        rebuildFooter()
        bannerHost = FrameLayout(this).apply { visibility = View.GONE }
        d.addView(bannerHost, Ui.abs(1232, 112, 24, 80))
        setContentView(root)
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
        big = null
        if (Prefs.layout == "big") { buildBig(d); return }
        val rightStack = Ui.col(this)
        val leftStack = Ui.col(this)
        // المساحة بين الشريط العلوي والقائمة السفلية (تقل قليلاً مع القائمة الكبيرة)
        val avail = 720 - 72 - barH
        d.addView(rightStack, Ui.abs(340, avail - 12, 916, 78))
        d.addView(leftStack, Ui.abs(340, avail - 12, 24, 78))
        var hidden = Prefs.hidden()
        val r = Prefs.column(true); val l = Prefs.column(false)
        // البطاقات الجديدة تبدأ مخفية مرة واحدة، وتوضع في العمود الأقصر
        val known = Prefs.knownCards()
        val newOnes = Prefs.NEW_CARDS.filter { it !in known }
        if (newOnes.isNotEmpty()) {
            hidden = hidden + newOnes
            Prefs.setHidden(hidden); Prefs.setKnownCards(known + newOnes)
        }
        for (id in Prefs.CARD_IDS) if (id !in r && id !in l) {
            val rv = r.count { it !in hidden }; val lv = l.count { it !in hidden }
            if (rv <= lv) r.add(id) else l.add(id)
        }
        Prefs.setColumns(r, l)
        // تُنشأ البطاقات الظاهرة فقط (المخفية لا تأخذ ذاكرة)
        val made = HashMap<String, View>()
        for (id in Prefs.CARD_IDS) if (id !in hidden || id in BASE_CARDS) made[id] = makeCard(id, hidden)
        for (id in r) made[id]?.let { rightStack.addView(it, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6; bottomMargin = 6 }) }
        for (id in l) made[id]?.let { leftStack.addView(it, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6; bottomMargin = 6 }) }

        val gh = avail - 24
        val g = FrameLayout(this)
        d.addView(g, Ui.abs(528, gh, 376, 84))
        gauge = GaugeView(this).apply {
            pal = this@MainActivity.pal; mode = Prefs.gauge; max = Prefs.maxSpeed; alert = Prefs.alertSpeed; showMax = Prefs.maxMark
            unitLabel = if (Prefs.speedMode == "car") "كم/س · معايَر" else "كم/س"
        }
        g.addView(gauge, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
        // صورة السيارة (اختيارية، وتختفي في العداد الرقمي لأنه يحتاج المساحة)
        if (Prefs.showCar && Prefs.gauge != 6) {
            val sc = gh / 544f
            g.addView(ImageView(this).apply { setImageResource(R.drawable.car_rear); scaleType = ImageView.ScaleType.FIT_CENTER },
                Ui.at((260 * sc).toInt(), (213 * sc).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
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
                card.setOnClickListener { Panels.prayer(this, pal) { openSettingsScreen(3) } }
            }
            "azkar" -> {
                azHead = head("الأذكار"); c.addView(azHead)
                azV = Ui.tv(this, 36f, pal.text, true); c.addView(azV, Ui.lin(Ui.MATCH, 42))
                azS = Ui.tv(this, 15f, pal.muted); c.addView(azS, Ui.lin(Ui.MATCH, 22))
                card = shell(c, R.drawable.ic_azkar, false)
                card.setOnClickListener { Azkar.tap(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); refreshAzkar() }
                card.setOnLongClickListener { Azkar.skip(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); refreshAzkar(); true }
            }
            "dest" -> {
                c.addView(head("وجهاتي · اضغط مطولاً لحفظ موقعك"))
                val row = Ui.row(this, Gravity.CENTER)
                for (k in listOf("mosque", "work", "home")) {
                    row.addView(Ui.tv(this, 19f, pal.text, true, Gravity.CENTER).apply {
                        text = destName(k)
                        background = Ui.pressable(Ui.rounded(pal.card, 12f, 2, if (Prefs.str("dest_$k").isEmpty()) pal.line else pal.accent),
                            Ui.rounded(pal.line, 12f))
                        setOnClickListener { goDest(k) }
                        setOnLongClickListener { saveDest(k); true }
                    }, Ui.lin(0, 64, 1f).apply { leftMargin = 4; rightMargin = 4 })
                }
                c.addView(row, Ui.lin(Ui.MATCH, 70).apply { topMargin = 4 })
                card = shell(c, R.drawable.ic_pin, false)
            }
            "compass" -> {
                c.addView(head("البوصلة"))
                cmpV = Ui.tv(this, 30f, pal.text, true).also { c.addView(it, Ui.lin(Ui.MATCH, 40)) }
                cmpS = Ui.tv(this, 15f, pal.muted).also { c.addView(it, Ui.lin(Ui.MATCH, 22)) }
                card = shell(c, R.drawable.g_compass, false, pal.accent)
            }
            "occasions" -> {
                c.addView(head("المناسبات"))
                occV = Ui.tv(this, 24f, pal.text, true).also { c.addView(it, Ui.lin(Ui.MATCH, 34)) }
                occS = Ui.tv(this, 15f, pal.amber).also { c.addView(it, Ui.lin(Ui.MATCH, 22)) }
                card = shell(c, R.drawable.g_star, false, pal.amber)
                card.setOnClickListener { Panels.prayer(this, pal) { if (!locked()) openSettingsScreen(3) } }
            }
            "ayah" -> {
                val a = Ayat.today()
                c.addView(head("آية اليوم"))
                c.addView(Ui.tv(this, 21f, pal.text, true).apply { text = "﴿ ${a.text} ﴾"; maxLines = 2; setLineSpacing(4f, 1f) },
                    Ui.lin(Ui.MATCH, Ui.WRAP))
                c.addView(Ui.tv(this, 15f, pal.amber).apply { text = a.ref }, Ui.lin(Ui.MATCH, 22).apply { topMargin = 4 })
                card = shell(c, R.drawable.g_book, false, pal.accent)
            }
            "dial" -> {
                c.addView(head("اتصال سريع · اضغط مطولاً للتعديل"))
                val row = Ui.row(this, Gravity.CENTER)
                for (i in 2 downTo 0) {
                    val name = Prefs.str("dialN$i")
                    row.addView(Ui.tv(this, 18f, if (name.isEmpty()) pal.muted else pal.text, true, Gravity.CENTER).apply {
                        text = if (name.isEmpty()) "+ إضافة" else name
                        background = Ui.pressable(Ui.rounded(pal.card, 12f, 2, if (name.isEmpty()) pal.line else pal.accent), Ui.rounded(pal.line, 12f))
                        setOnClickListener { dial(i) }
                        setOnLongClickListener { editDial(i); true }
                    }, Ui.lin(0, 64, 1f).apply { leftMargin = 4; rightMargin = 4 })
                }
                c.addView(row, Ui.lin(Ui.MATCH, 70).apply { topMargin = 4 })
                card = shell(c, R.drawable.g_phone, false, pal.accent)
            }
            "shortcuts" -> {
                c.addView(head("اختصارات · اضغط مطولاً للتغيير"))
                val row = Ui.row(this, Gravity.CENTER)
                for (i in 3 downTo 0) {
                    val pkg = Prefs.str("sc$i")
                    val info = if (pkg.isEmpty()) null else appInfo(pkg)
                    val cell = FrameLayout(this).apply {
                        background = Ui.pressable(Ui.rounded(pal.tile, 14f, 2, pal.tileLine), Ui.rounded(pal.line, 14f))
                        if (info != null) addView(ImageView(context).apply { setImageDrawable(info.second); scaleType = ImageView.ScaleType.FIT_CENTER },
                            Ui.at(46, 46, Gravity.CENTER))
                        else addView(Ui.icon(context, R.drawable.g_plus, pal.muted), Ui.at(28, 28, Gravity.CENTER))
                        setOnClickListener { if (pkg.isEmpty() || !launch(pkg)) pickShortcut(i) }
                        setOnLongClickListener { pickShortcut(i); true }
                    }
                    row.addView(cell, Ui.lin(64, 64).apply { leftMargin = 6; rightMargin = 6 })
                }
                c.addView(row, Ui.lin(Ui.MATCH, 70).apply { topMargin = 4 })
                card = shell(c, R.drawable.g_apps, false, pal.accent)
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

    // ============================== تخطيط البطاقات الكبيرة ==============================
    /** مراجع عناصر البطاقات الكبيرة (null إن لم تكن ظاهرة) */
    private class Big {
        var clock: TextView? = null; var date: TextView? = null; var hijri: TextView? = null
        var wxIcon: ImageView? = null; var wxTemp: TextView? = null; var wxDesc: TextView? = null
        var wxCity: TextView? = null; var wxMinMax: TextView? = null; var wxHead: TextView? = null
        var prName: TextView? = null; var prLeft: TextView? = null; var prAdhan: TextView? = null
        var qArrow: ImageView? = null; var qDeg: TextView? = null
        var muSrc: TextView? = null; var muTitle: TextView? = null; var muArtist: TextView? = null
        var muPos: TextView? = null; var muDur: TextView? = null; var muFill: View? = null; var muBar: View? = null
        var muPlay: ImageView? = null
        var mHead: TextView? = null; var mBadge: TextView? = null; var mHome: TextView? = null
        var mAway: TextView? = null; var mScore: TextView? = null; var mNext: TextView? = null
        var azHead: TextView? = null; var azCount: TextView? = null; var azText: TextView? = null
    }
    private var big: Big? = null

    private fun buildBig(d: FrameLayout) {
        // البطاقات الكلاسيكية تُنشأ دون عرض حتى تبقى مراجعها صالحة (عناصر صغيرة لا تُرسم)
        for (id in BASE_CARDS) makeCard(id, emptySet())
        val b = Big(); big = b
        val avail = 720 - 72 - barH
        val ch = (avail - 24 - 12) / 2
        val pos = listOf(intArrayOf(872, 84), intArrayOf(872, 84 + ch + 12), intArrayOf(16, 84), intArrayOf(16, 84 + ch + 12))
        for (i in 0..3) {
            val v = bigCard(Prefs.bigSlot(i), b)
            d.addView(v, Ui.abs(392, ch, pos[i][0], pos[i][1]))
        }
        val gh = avail - 12
        val g = FrameLayout(this)
        d.addView(g, Ui.abs(440, gh, 420, 78))
        gauge = GaugeView(this).apply {
            pal = this@MainActivity.pal; mode = Prefs.gauge; max = Prefs.maxSpeed; alert = Prefs.alertSpeed; showMax = Prefs.maxMark
            unitLabel = if (Prefs.speedMode == "car") "كم/س · معايَر" else "كم/س"
        }
        g.addView(gauge, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
        if (Prefs.showCar && Prefs.gauge != 6) {
            val sc = minOf(440 / 528f, gh / 544f)
            g.addView(ImageView(this).apply { setImageResource(R.drawable.car_rear); scaleType = ImageView.ScaleType.FIT_CENTER },
                Ui.at((300 * sc).toInt(), (246 * sc).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
    }

    /** إطار بطاقة كبيرة: عنوان بأيقونة وخط فاصل، ثم المحتوى */
    private fun bigFrame(icon: Int, title: String, extra: View?): Pair<LinearLayout, LinearLayout> {
        val card = Ui.col(this, Gravity.TOP).apply { background = Ui.cardBg(pal); setPadding(20, 14, 20, 12) }
        val hd = Ui.row(this, Gravity.CENTER_VERTICAL)
        if (extra != null) hd.addView(extra, Ui.lin(Ui.WRAP, 30))
        hd.addView(View(this), Ui.lin(0, 1, 1f))
        val tt = Ui.tv(this, 20f, pal.muted).apply { text = title }
        hd.addView(tt, Ui.lin(Ui.WRAP, 34))
        hd.addView(Ui.icon(this, icon, pal.accent), Ui.lin(26, 26).apply { leftMargin = 10 })
        card.addView(hd, Ui.lin(Ui.MATCH, 36))
        card.addView(View(this).apply { setBackgroundColor(pal.line) }, Ui.lin(Ui.MATCH, 1).apply { topMargin = 6 })
        val body = Ui.col(this, Gravity.TOP)
        card.addView(body, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 8 })
        card.tag = tt
        return Pair(card, body)
    }

    private fun vline(): View = View(this).apply { setBackgroundColor(pal.line) }

    private fun bigCard(type: String, b: Big): View {
        when (type) {
            "timewx" -> {
                val (card, body) = bigFrame(R.drawable.g_sun, "الوقت والطقس", null)
                b.wxHead = card.tag as TextView
                val row = Ui.row(this, Gravity.CENTER_VERTICAL)
                val wx = Ui.col(this, Gravity.CENTER)
                b.wxIcon = Ui.icon(this, R.drawable.g_sun, pal.amber).also { wx.addView(it, Ui.lin(36, 36)) }
                b.wxTemp = Ui.tv(this, 46f, pal.text, true, Gravity.CENTER).also { wx.addView(it, Ui.lin(Ui.MATCH, 54)) }
                b.wxDesc = Ui.tv(this, 18f, pal.muted, false, Gravity.CENTER).also { wx.addView(it, Ui.lin(Ui.MATCH, 24)) }
                row.addView(wx, Ui.lin(118, Ui.MATCH))
                row.addView(vline(), Ui.lin(1, Ui.MATCH).apply { leftMargin = 8; rightMargin = 14 })
                val tc = Ui.col(this, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
                b.clock = Ui.tv(this, 70f, pal.text, true).also { tc.addView(it, Ui.lin(Ui.MATCH, 80)) }
                b.date = Ui.tv(this, 18f, pal.muted).also { tc.addView(it, Ui.lin(Ui.MATCH, 26)) }
                b.hijri = Ui.tv(this, 18f, pal.amber).also { tc.addView(it, Ui.lin(Ui.MATCH, 26)) }
                row.addView(tc, Ui.lin(0, Ui.MATCH, 1f))
                body.addView(row, Ui.lin(Ui.MATCH, 0, 1f))
                val bot = Ui.row(this, Gravity.CENTER_VERTICAL)
                b.wxMinMax = Ui.tv(this, 17f, pal.muted, false, Gravity.LEFT).also { bot.addView(it, Ui.lin(0, 28, 1f)) }
                b.wxCity = Ui.tv(this, 17f, pal.muted).also { bot.addView(it, Ui.lin(Ui.WRAP, 28)) }
                body.addView(bot, Ui.lin(Ui.MATCH, 30))
                card.setOnClickListener { Panels.weather(this, pal) }
                return card
            }
            "prayer" -> {
                val (card, body) = bigFrame(R.drawable.g_moon, "الصلاة القادمة", null)
                val row = Ui.row(this, Gravity.CENTER_VERTICAL)
                val q = Ui.col(this, Gravity.CENTER)
                b.qArrow = Ui.icon(this, R.drawable.g_arrow, pal.amber).also { q.addView(it, Ui.lin(40, 40)) }
                b.qDeg = Ui.tv(this, 26f, pal.amber, true, Gravity.CENTER).also { q.addView(it, Ui.lin(Ui.MATCH, 34)) }
                q.addView(Ui.tv(this, 17f, pal.muted, false, Gravity.CENTER).apply { text = "القبلة" }, Ui.lin(Ui.MATCH, 24))
                row.addView(q, Ui.lin(108, Ui.MATCH))
                row.addView(vline(), Ui.lin(1, Ui.MATCH).apply { leftMargin = 8; rightMargin = 14 })
                val pc = Ui.col(this, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
                b.prName = Ui.tv(this, 52f, pal.text, true).also { pc.addView(it, Ui.lin(Ui.MATCH, 64)) }
                b.prLeft = Ui.tv(this, 26f, pal.text).also { pc.addView(it, Ui.lin(Ui.MATCH, 36)) }
                row.addView(pc, Ui.lin(0, Ui.MATCH, 1f))
                body.addView(row, Ui.lin(Ui.MATCH, 0, 1f))
                val ad = Ui.row(this, Gravity.CENTER_VERTICAL).apply { background = Ui.rounded(pal.card, 12f, 2, pal.line); setPadding(14, 0, 14, 0) }
                b.prAdhan = Ui.tv(this, 20f, pal.amber, true, Gravity.LEFT).also { ad.addView(it, Ui.lin(0, 42, 1f)) }
                ad.addView(Ui.tv(this, 19f, pal.text).apply { text = "وقت الأذان" }, Ui.lin(Ui.WRAP, 42))
                ad.addView(Ui.icon(this, R.drawable.g_bell, pal.amber), Ui.lin(22, 22).apply { leftMargin = 8 })
                body.addView(ad, Ui.lin(Ui.MATCH, 46).apply { topMargin = 4 })
                card.setOnClickListener { Panels.prayer(this, pal) { openSettingsScreen(3) } }
                return card
            }
            "music" -> {
                val src = Ui.tv(this, 16f, pal.muted, false, Gravity.LEFT)
                b.muSrc = src
                val (card, body) = bigFrame(R.drawable.g_music, "يتم التشغيل الآن", src)
                val row = Ui.row(this, Gravity.CENTER_VERTICAL)
                val tc = Ui.col(this, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
                b.muTitle = Ui.tv(this, 28f, pal.text, true).also { tc.addView(it, Ui.lin(Ui.MATCH, 38)) }
                b.muArtist = Ui.tv(this, 18f, pal.muted).also { tc.addView(it, Ui.lin(Ui.MATCH, 26)) }
                row.addView(tc, Ui.lin(0, Ui.MATCH, 1f))
                row.addView(FrameLayout(this).apply {
                    background = Ui.rounded(pal.tile, 14f, 1, pal.line)
                    addView(Ui.icon(context, R.drawable.g_music, pal.accent), Ui.at(36, 36, Gravity.CENTER))
                }, Ui.lin(66, 66).apply { leftMargin = 14 })
                body.addView(row, Ui.lin(Ui.MATCH, 70))
                val pr = Ui.row(this, Gravity.CENTER_VERTICAL)
                b.muPos = Ui.tv(this, 15f, pal.muted, false, Gravity.CENTER).also { pr.addView(it, Ui.lin(52, 24)) }
                val bar = FrameLayout(this).apply { background = Ui.rounded(pal.line, 3f) }
                val fill = View(this).apply { background = Ui.rounded(pal.accent, 3f) }
                bar.addView(fill, FrameLayout.LayoutParams(0, Ui.MATCH))
                b.muFill = fill; b.muBar = bar
                pr.addView(bar, Ui.lin(0, 6, 1f).apply { leftMargin = 6; rightMargin = 6 })
                b.muDur = Ui.tv(this, 15f, pal.muted, false, Gravity.CENTER).also { pr.addView(it, Ui.lin(52, 24)) }
                body.addView(pr, Ui.lin(Ui.MATCH, 26).apply { topMargin = 4 })
                val ctr = Ui.row(this, Gravity.CENTER)
                fun circle(res: Int, filled: Boolean, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
                    background = Ui.pressable(if (filled) Ui.rounded(pal.accent, 30f) else Ui.rounded(pal.card, 30f, 2, pal.line), Ui.rounded(pal.line, 30f))
                    setOnClickListener { onClick() }
                }
                ctr.addView(circle(R.drawable.g_prev, false) { Media.key(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }.apply {
                    addView(Ui.icon(context, R.drawable.g_prev, pal.text), Ui.at(24, 24, Gravity.CENTER)) }, Ui.lin(60, 60))
                val play = Ui.icon(this, R.drawable.g_play, pal.onAccent)
                b.muPlay = play
                ctr.addView(circle(R.drawable.g_play, true) { Media.key(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); refreshMusic() }.apply {
                    addView(play, Ui.at(26, 26, Gravity.CENTER)) }, Ui.lin(60, 60).apply { leftMargin = 22; rightMargin = 22 })
                ctr.addView(circle(R.drawable.g_next, false) { Media.key(this, KeyEvent.KEYCODE_MEDIA_NEXT) }.apply {
                    addView(Ui.icon(context, R.drawable.g_next, pal.text), Ui.at(24, 24, Gravity.CENTER)) }, Ui.lin(60, 60))
                body.addView(ctr, Ui.lin(Ui.MATCH, 0, 1f))
                card.setOnClickListener {
                    if (!Media.granted(this)) openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    else if (Media.app.isNotEmpty()) launch(Media.app)
                }
                return card
            }
            "matches" -> {
                val badge = Ui.tv(this, 16f, 0xFFFFFFFF.toInt(), true, Gravity.CENTER).apply { setPadding(12, 0, 12, 0) }
                b.mBadge = badge
                val (card, body) = bigFrame(R.drawable.g_star, "المباريات", badge)
                b.mHead = card.tag as TextView
                val row = Ui.row(this, Gravity.CENTER_VERTICAL)
                b.mAway = Ui.tv(this, 26f, pal.text, true, Gravity.CENTER).apply { maxLines = 2 }.also { row.addView(it, Ui.lin(112, Ui.MATCH)) }
                b.mScore = Ui.tv(this, 58f, pal.text, true, Gravity.CENTER).also { row.addView(it, Ui.lin(0, Ui.MATCH, 1f)) }
                b.mHome = Ui.tv(this, 26f, pal.text, true, Gravity.CENTER).apply { maxLines = 2 }.also { row.addView(it, Ui.lin(112, Ui.MATCH)) }
                body.addView(row, Ui.lin(Ui.MATCH, 0, 1f))
                body.addView(View(this).apply { setBackgroundColor(pal.line) }, Ui.lin(Ui.MATCH, 1))
                b.mNext = Ui.tv(this, 17f, pal.muted).also { body.addView(it, Ui.lin(Ui.MATCH, 34)) }
                card.setOnClickListener { Panels.matchesDay(this, pal) }
                card.setOnLongClickListener { forceMatches(); toast("جارٍ تحديث المباريات…"); true }
                return card
            }
            "azkar" -> {
                val (card, body) = bigFrame(R.drawable.g_book, "الأذكار", null)
                b.azHead = card.tag as TextView
                b.azCount = Ui.tv(this, 58f, pal.text, true).also { body.addView(it, Ui.lin(Ui.MATCH, 70)) }
                b.azText = Ui.tv(this, 20f, pal.muted).apply { maxLines = 3; setLineSpacing(4f, 1f) }.also { body.addView(it, Ui.lin(Ui.MATCH, Ui.WRAP)) }
                body.addView(Ui.tv(this, 15f, pal.muted).apply { text = "اضغط للعدّ · اضغط مطولاً للتالي" }, Ui.lin(Ui.MATCH, 24))
                card.setOnClickListener { Azkar.tap(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); refreshAzkar() }
                card.setOnLongClickListener { Azkar.skip(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); refreshAzkar(); true }
                return card
            }
            else -> {
                val (card, body) = bigFrame(R.drawable.ic_pin.let { R.drawable.g_loc }, "وجهاتي · اضغط مطولاً لحفظ موقعك", null)
                for (k in listOf("home", "work", "mosque")) {
                    body.addView(Ui.tv(this, 22f, pal.text, true, Gravity.CENTER).apply {
                        text = destName(k)
                        background = Ui.pressable(Ui.rounded(pal.card, 12f, 2, if (Prefs.str("dest_$k").isEmpty()) pal.line else pal.accent), Ui.rounded(pal.line, 12f))
                        setOnClickListener { goDest(k) }
                        setOnLongClickListener { saveDest(k); true }
                    }, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6 })
                }
                return card
            }
        }
    }

    /** تحديث البطاقات الكبيرة: السريع كل ثانية (الموسيقى)، والبطيء مع الصلاة والوقت */
    private fun refreshBigFast() {
        val b = big ?: return
        b.clock?.let { Ui.setT(it, Ui.hmBig(System.currentTimeMillis())) }
        if (b.muTitle != null) {
            val t = b.muTitle!!
            if (Media.title.isNotEmpty()) { Ui.setT(t, Media.title); Ui.setT(b.muArtist!!, Media.artist) }
            else { Ui.setT(t, if (!Media.granted(this)) "اضغط لعرض اسم المقطع" else "لا يوجد تشغيل"); Ui.setT(b.muArtist!!, "") }
            val src = appLabel(Media.app)
            b.muSrc?.let { Ui.setT(it, src) }
            val want = if (Media.playing) R.drawable.g_pause else R.drawable.g_play
            b.muPlay?.let { if (it.tag != want) { it.tag = want; it.setImageResource(want) } }
            val p = Media.progress()
            fun mmss(ms: Long) = String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)
            Ui.setT(b.muPos!!, if (p == null) "" else mmss(p[0]))
            Ui.setT(b.muDur!!, if (p == null) "" else mmss(p[1]))
            val bar = b.muBar!!; val fill = b.muFill!!
            val w = if (p == null || bar.width == 0) 0 else (bar.width * p[0] / p[1]).toInt()
            if (fill.layoutParams.width != w) { fill.layoutParams.width = w; fill.requestLayout() }
        }
        if (Trip.gpsLive() && Trip.distanceM > 50) {
            val dist = String.format(Locale.US, "%.1f", Trip.distanceM / 1000.0)
            gauge.setTrip("$dist كم · ${Trip.movingMs / 60000} د · متوسط ${Trip.avgKmh().roundToInt()}")
        } else gauge.setTrip(if (Trip.gpsLive()) "GPS متصل · تنبيه عند ${Prefs.alertSpeed}" else "GPS غير متصل")
    }

    private fun refreshBigSlow(now: Long) {
        val b = big ?: return
        b.date?.let { Ui.setT(it, toLatinDigits(SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date(now)))) }
        b.hijri?.let { Ui.setT(it, Astro.hijriText(now)) }
        b.wxIcon?.setImageResource(if (night) R.drawable.g_moon else R.drawable.g_sun)
        if (b.prName != null) {
            val (idx, at) = Astro.nextPrayer(now, Loc.lat(), Loc.lon())
            val mins = ((at - now) / 60000L).toInt()
            Ui.setT(b.prName!!, Astro.PRAYER_NAMES[idx])
            Ui.setT(b.prLeft!!, if (mins >= 60) "بعد ${mins / 60} س ${mins % 60} د" else "بعد $mins د")
            Ui.setT(b.prAdhan!!, Ui.hm(at))
            Ui.setT(b.qDeg!!, "${qiblaDeg.roundToInt()}°")
        }
        b.qArrow?.let {
            val h = Trip.bearing
            val rot = if (h >= 0f && Trip.gpsLive()) qiblaDeg - h else qiblaDeg
            if (Math.abs(it.rotation - rot) > 2f) it.rotation = rot
        }
        if (b.azCount != null) {
            val list = Azkar.list(now); val s = Azkar.state(now)
            if (s[0] >= list.size) {
                Ui.setT(b.azHead!!, "دعاء اليوم · تمت الأذكار ✓"); Ui.setT(b.azCount!!, "تقبّل الله"); Ui.setT(b.azText!!, Duas.today())
            } else {
                Ui.setT(b.azHead!!, if (Azkar.isMorning(now)) "أذكار الصباح" else "أذكار المساء")
                Ui.setT(b.azCount!!, "${s[1]} / ${list[s[0]].count}"); Ui.setT(b.azText!!, list[s[0]].text)
            }
        }
    }

    private fun refreshBigWeather() {
        val b = big ?: return
        val w = weather
        if (b.wxTemp == null) return
        if (w == null) {
            Ui.setT(b.wxTemp!!, "--°"); Ui.setT(b.wxDesc!!, if (!Net.online(this)) "لا إنترنت" else "")
            Ui.setT(b.wxCity!!, Loc.name()); Ui.setT(b.wxMinMax!!, ""); return
        }
        Ui.setT(b.wxTemp!!, "${w.temp}°"); Ui.setT(b.wxDesc!!, w.desc)
        Ui.setT(b.wxCity!!, w.city + if (weatherFail) " · محفوظ ${Ui.hm(weatherFrom)}" else "")
        Ui.setT(b.wxMinMax!!, if (w.max > -999) "العظمى ${w.max}° · الصغرى ${w.min}°" else "")
    }

    private fun refreshBigMatches() {
        val b = big ?: return
        if (b.mScore == null) return
        val m = matches?.main
        val badge = b.mBadge!!
        if (m == null) {
            Ui.setT(b.mHead!!, "المباريات"); badge.visibility = View.GONE
            Ui.setT(b.mHome!!, ""); Ui.setT(b.mAway!!, "")
            Ui.setT(b.mScore!!, "—")
            Ui.setT(b.mNext!!, if (matches == null) (if (matchesFail) (if (!Net.online(this)) "لا يوجد إنترنت" else Net.lastError.ifEmpty { "تعذّر التحميل" }) else "جارٍ التحميل…") else "لا توجد مباريات اليوم أو غداً")
            return
        }
        Ui.setT(b.mHead!!, m.league)
        badge.visibility = View.VISIBLE
        when (m.state) {
            "in" -> { Ui.setT(badge, "● مباشر ${m.clock}"); badge.background = Ui.rounded(0xFF15803D.toInt(), 10f) }
            "post" -> { Ui.setT(badge, "انتهت"); badge.background = Ui.rounded(pal.muted, 10f) }
            else -> { Ui.setT(badge, "قادم ${Ui.hm(m.time)}"); badge.background = Ui.rounded(pal.accent, 10f) }
        }
        badge.setTextColor(if (m.state == "pre") pal.onAccent else 0xFFFFFFFF.toInt())
        Ui.setT(b.mHome!!, m.home); Ui.setT(b.mAway!!, m.away)
        Ui.setT(b.mScore!!, if (m.state == "pre") "×" else "${m.aws} - ${m.hs}")
        val n = matches?.next
        Ui.setT(b.mNext!!, if (n != null) "القادمة: ${n.home} × ${n.away} · ${Ui.hm(n.time)}" else "آخر تحديث " + matchesUpdatedText())
    }

    private val BASE_CARDS = setOf("time", "weather", "trip", "matches", "music", "prayer", "azkar", "nav")

    /** إطار البطاقة: نص وبلاطة أيقونة في أقصى اليمين (tint للأيقونات البسيطة) */
    private fun shell(content: LinearLayout, icon: Int, warm: Boolean, tint: Int? = null): View {
        val card = Ui.row(this, Gravity.CENTER_VERTICAL).apply {
            background = Ui.cardBg(pal)
            setPadding(14, 10, 14, 8)
        }
        card.addView(content, Ui.lin(0, Ui.MATCH, 1f))
        val tile = FrameLayout(this).apply {
            background = if (warm) Ui.rounded(pal.warmTile, 18f, 2, pal.warmLine) else Ui.rounded(pal.tile, 18f, 2, pal.tileLine)
            addView(Ui.icon(context, icon, tint), Ui.at(if (tint != null) 44 else 58, if (tint != null) 44 else 58, Gravity.CENTER))
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
        design.addView(f, 0, Ui.abs(1280, barH, 0, 720 - barH))
        footer = f
        f.addView(View(this).apply { setBackgroundColor(if (editMode) pal.accent else pal.line) }, Ui.abs(1280, 2, 0, 0))
        val bar = Ui.row(this, Gravity.CENTER)
        f.addView(bar, Ui.abs(1256, barH - 10, 12, 6))
        for (i in 5 downTo 0) bar.addView(slotView(i), Ui.lin(0, Ui.MATCH, 1f).apply { leftMargin = 5; rightMargin = 5 })
    }

    /** خانة القائمة: أيقونة داخل بلاطة موحّدة، وإطار للخانة النشطة، وتضيء لحظة الضغط */
    private fun slotView(i: Int): View {
        val size = Prefs.barSize
        val tileSz = when (size) { 0 -> 40; 2 -> 64; else -> 58 }
        val iconSz = when (size) { 0 -> 28; 2 -> 46; else -> 40 }
        val showLabel = size != 2 && (Prefs.slotLabels || editMode)
        val v = Ui.col(this, Gravity.CENTER)
        val type = Prefs.slotType(i); val value = Prefs.slotValue(i)
        val active = type == "fn" && value == "home"
        val boxed = Prefs.layout == "big"
        // في البطاقات الكبيرة: الخانة النشطة مملوءة باللون ونصها بلون معاكس
        val color = if (active) (if (boxed) pal.onAccent else pal.accent) else pal.text
        val normal = when {
            editMode -> Ui.rounded(pal.card, 14f, 2, pal.accent)
            boxed && active -> Ui.rounded(pal.accent, 14f)
            active -> Ui.rounded(pal.card, 14f, 2, pal.accent)
            boxed -> Ui.rounded(pal.card, 14f, 1, pal.line)
            else -> null
        }
        v.background = Ui.pressable(normal, Ui.rounded(pal.line, 14f))
        val tile = FrameLayout(this).apply { if (!boxed) background = Ui.rounded(pal.tile, 14f, 2, if (active) pal.accent else pal.tileLine) }
        if (type == "app") {
            tile.addView(ImageView(this).apply { setImageDrawable(appInfo(value)?.second); scaleType = ImageView.ScaleType.FIT_CENTER },
                Ui.at(iconSz, iconSz, Gravity.CENTER))
        } else {
            val tint = if (boxed) color else if (active) pal.accent else 0xFFE6EAEE.toInt()
            val isz = if (boxed) iconSz * 4 / 5 else iconSz * 3 / 4
            tile.addView(Ui.icon(this, fnIcons[fnIds.indexOf(value).coerceAtLeast(0)], tint), Ui.at(isz, isz, Gravity.CENTER))
        }
        val icRow = Ui.row(this, Gravity.CENTER)
        icRow.addView(tile, Ui.lin(tileSz, tileSz))
        // نقطة خضراء عندما يعمل تطبيق الموسيقى
        val playing = Media.playing && ((type == "app" && value == Media.app) || (type == "fn" && value == "music"))
        if (playing) icRow.addView(View(this).apply { background = Ui.rounded(pal.ok, 5f) }, Ui.lin(10, 10).apply { leftMargin = 6 })
        v.addView(icRow, Ui.lin(Ui.MATCH, tileSz))
        if (showLabel) {
            val ls = if (size == 0) 15f else 18f
            v.addView(Ui.tv(this, ls, if (editMode) pal.accent else color, active, Gravity.CENTER).apply {
                text = if (editMode) "✎ تعديل" else slotTitle(i)
            }, Ui.lin(Ui.MATCH, if (size == 0) 22 else 26).apply { topMargin = 3 })
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

    fun openSettingsScreen(section: Int = -1) {
        if (locked()) return
        val pin = Prefs.pin
        if (pin.isEmpty()) { SettingsScreen(this, section).show(); return }
        val e = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "الرقم السري"
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("الإعدادات مقفلة").setView(e)
            .setPositiveButton("فتح") { _, _ -> if (e.text.toString() == pin) SettingsScreen(this, section).show() else toast("الرقم غير صحيح") }
            .setNegativeButton("إلغاء", null).show()
    }

    // ============================== وجهاتي، الاتصال، الاختصارات ==============================
    private fun destName(k: String) = when (k) { "home" -> "المنزل"; "work" -> "العمل"; else -> "المسجد" }

    private fun goDest(k: String) {
        val v = Prefs.str("dest_$k")
        val uri = when {
            v.isNotEmpty() -> "geo:$v?q=$v(" + Uri.encode(destName(k)) + ")"
            k == "mosque" -> "geo:0,0?q=" + Uri.encode("مسجد")
            else -> { toast("اضغط مطولاً على «${destName(k)}» وأنت فيه لحفظ موقعه"); return }
        }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast("لا يوجد تطبيق خرائط") }
    }

    fun saveDest(k: String) {
        if (!Trip.gpsLive()) { toast("انتظر إشارة GPS ثم أعد المحاولة"); return }
        Prefs.putStr("dest_$k", String.format(Locale.US, "%.6f,%.6f", Prefs.lat, Prefs.lon))
        toast("تم حفظ موقعك الحالي كـ«${destName(k)}»"); build()
    }

    private fun dial(i: Int) {
        val num = Prefs.str("dialP$i")
        if (num.isEmpty()) { editDial(i); return }
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast("لا يوجد تطبيق هاتف على الشاشة") }
    }

    fun editDial(i: Int) {
        val box = Ui.col(this, Gravity.TOP).apply { setPadding(32, 8, 32, 8) }
        val n = android.widget.EditText(this).apply { hint = "الاسم"; setText(Prefs.str("dialN$i")); setSingleLine() }
        val p = android.widget.EditText(this).apply { hint = "رقم الجوال"; setText(Prefs.str("dialP$i")); inputType = android.text.InputType.TYPE_CLASS_PHONE }
        box.addView(n); box.addView(p)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("اتصال سريع ${i + 1}").setView(box)
            .setPositiveButton("حفظ") { _, _ -> Prefs.putStr("dialN$i", n.text.toString().trim()); Prefs.putStr("dialP$i", p.text.toString().trim()); build() }
            .setNeutralButton("حذف") { _, _ -> Prefs.putStr("dialN$i", ""); Prefs.putStr("dialP$i", ""); build() }
            .setNegativeButton("إلغاء", null).show()
    }

    fun pickShortcut(i: Int) {
        if (locked()) return
        Drawer.show(this, pal, "اختر تطبيقاً للاختصار") { pkg -> Prefs.putStr("sc$i", pkg); build() }
    }

    /**
     * معايرة العداد ليطابق عداد السيارة: أثناء القيادة بسرعة ثابتة تُؤخذ قراءة GPS الآن،
     * وتُدخل ما يعرضه عداد السيارة. ويمكن إدخال الرقمين يدوياً والسيارة واقفة.
     */
    fun calibrate() {
        val now = Trip.kmh()
        val box = Ui.col(this, Gravity.TOP).apply { setPadding(32, 8, 32, 8) }
        val hint = android.widget.TextView(this).apply {
            text = if (now >= 30f) "قراءة GPS الآن مأخوذة تلقائياً. اكتب ما يعرضه عداد السيارة في نفس اللحظة."
                   else "قُد بسرعة ثابتة فوق 40 كم/س ليأخذ القراءة تلقائياً، أو أدخل الرقمين يدوياً."
            setPadding(0, 0, 0, 12)
        }
        val gps = android.widget.EditText(this).apply {
            hint = "سرعة GPS"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (now >= 30f) now.roundToInt().toString() else "100")
        }
        val car = android.widget.EditText(this).apply { hint = "ما يعرضه عداد السيارة"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        box.addView(hint)
        box.addView(android.widget.TextView(this).apply { text = "سرعة GPS (الحقيقية)" }); box.addView(gps)
        box.addView(android.widget.TextView(this).apply { text = "عداد السيارة يعرض" }); box.addView(car)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("معايرة العداد").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val g = gps.text.toString().toFloatOrNull() ?: 0f
                val c = car.text.toString().toFloatOrNull() ?: 0f
                if (g < 30f || c < 30f) { toast("أدخل سرعتين فوق 30 كم/س"); return@setPositiveButton }
                val f = c / g
                if (f < 0.9f || f > 1.15f) { toast("الفرق كبير جداً، تأكد من الرقمين"); return@setPositiveButton }
                Prefs.speedFactor = f; Prefs.speedMode = "car"
                toast(String.format(Locale.US, "تمت المعايرة: عداد السيارة يزيد %+.1f%%", (f - 1f) * 100f)); applySettings()
            }
            .setNeutralButton("إلغاء المعايرة") { _, _ -> Prefs.speedMode = "gps"; applySettings() }
            .setNegativeButton("إغلاق", null).show()
    }

    /** تجربة الأصوات من الإعدادات */
    fun testSound(adhan: Boolean) {
        try {
            if (adhan) {
                val u = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                android.media.RingtoneManager.getRingtone(this, u)?.play()
            } else {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            }
        } catch (e: Exception) { toast("تعذّر تشغيل الصوت") }
    }

    /** ذاكرة اللانشر الآن بالميجا */
    fun memoryMb(): Int = try {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))[0].totalPss / 1024
    } catch (e: Exception) { -1 }

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
            "أعلى سرعة: ${Trip.maxKmh.roundToInt()} كم/س\nالسرعة الآن: ${Trip.kmh().roundToInt()} كم/س\n" +
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

    /** آخر طقس محفوظ (حتى 12 ساعة) يظهر فوراً عند التشغيل */
    private fun loadCachedWeather() {
        val at = Prefs.long("wxCacheAt")
        val p = Prefs.str("wxCache").split("|")
        if (p.size >= 4 && System.currentTimeMillis() - at < 12 * 3600000L) try {
            weather = Net.Weather(p[0].toInt(), p[1], p[2], false, p[3].toInt(),
                p.getOrNull(4)?.toIntOrNull() ?: -999, p.getOrNull(5)?.toIntOrNull() ?: -999); weatherFrom = at
        } catch (e: Exception) {}
    }

    /** اختبار الاتصال من الإعدادات */
    fun netTest() {
        toast("جارٍ اختبار الاتصال…")
        Thread {
            val r = Net.test(applicationContext)
            h.post {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("اختبار الاتصال").setMessage(r).setPositiveButton("إغلاق", null).show()
            }
        }.start()
    }
    fun forceMatches() { matchesAt = 0L; matchesTry = 0L; matchesFullAt = 0L }
    fun matchesUpdatedText(): String = if (matchesAt == 0L) "—" else Ui.hm(matchesAt)

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
        fun fixed(b: String) { Prefs.theme = "fixed"; Prefs.setBase("fixed", b); dlg.dismiss(); applySettings() }
        row("الثيم", Pair("تلقائي") { Prefs.theme = "auto"; dlg.dismiss(); applySettings() },
            Pair("داكن") { fixed("dark") }, Pair("أسود") { fixed("black") }, Pair("فاتح") { fixed("light") })
        row("الساعة", Pair("12") { Prefs.clock24 = false; dlg.dismiss(); applySettings() },
            Pair("24") { Prefs.clock24 = true; dlg.dismiss(); applySettings() })
        row("شاشات", Pair("سرعة كبيرة") { dlg.dismiss(); Panels.bigSpeed(this) }, Pair("الانتظار") { dlg.dismiss(); showStandby() })
        // متاحة أثناء القيادة (للراكب): مطابقة عداد السيارة
        row("السرعة", Pair("معايرة") { dlg.dismiss(); calibrate() },
            Pair(if (Prefs.speedMode == "car") "GPS الحقيقية" else "مطابقة السيارة") {
                Prefs.speedMode = if (Prefs.speedMode == "car") "gps" else "car"; dlg.dismiss(); applySettings()
            })
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
        val clock = Ui.tv(this, 200f, 0xFF2EE6F0.toInt(), true, Gravity.CENTER).apply { text = Ui.hmBig(now) }
        standbyClock = clock
        v.addView(clock, Ui.lin(Ui.MATCH, 230))
        v.addView(Ui.tv(this, 28f, 0xFF9AA7B4.toInt(), false, Gravity.CENTER).apply { text = toLatinDigits(SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date(now))) + " · " + Astro.hijriText(now) }, Ui.lin(Ui.MATCH, 44))
        val (idx, at) = Astro.nextPrayer(now, Loc.lat(), Loc.lon())
        v.addView(Ui.tv(this, 30f, 0xFFF5B841.toInt(), true, Gravity.CENTER).apply { text = "${Astro.PRAYER_NAMES[idx]} " + Ui.hm(at) }, Ui.lin(Ui.MATCH, 50).apply { topMargin = 20 })
        design.addView(v, Ui.abs(1280, 720, 0, 0))
        standbyView = v
        refreshSlow()
    }

    private fun hideStandby() {
        standbyView?.let { design.removeView(it) }
        standbyView = null; standbyClock = null
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
            if (!speedShown && Trip.kmh() > lim) {
                speedShown = true
                alert(Alert(pal.danger, R.drawable.g_bell, true, "تجاوزت حد التنبيه $lim كم/س", "السرعة الآن ${Trip.kmh().roundToInt()} كم/س", false))
            } else if (speedShown && Trip.kmh() < lim - 5) speedShown = false
        }
        // الصلاة: قبلها ثم عند الأذان
        if (nextAt > 0) {
            val preMs = Prefs.preMin * 60000L
            if (Prefs.alertOn("pre") && preMs > 0 && now >= nextAt - preMs && now < nextAt && preShownFor != nextAt) {
                preShownFor = nextAt
                alert(Alert(pal.accent, R.drawable.ic_prayer, false, "بعد ${Duas.mins(((nextAt - now + 59999L) / 60000L).toInt())}: صلاة ${Astro.PRAYER_NAMES[nextIdx]}", Loc.name(), false))
            }
            if (now >= nextAt && adhanShownFor != nextAt) {
                adhanShownFor = nextAt
                // إن عادت الواجهة بعد غياب طويل لا نعرض أذاناً فات وقته
                if (now - nextAt < 10 * 60000L && Prefs.alertOn("prayer")) alert(Alert(pal.accent, R.drawable.ic_prayer, false,
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
                alert(Alert(pal.amber, R.drawable.g_car, true, "مضى على القيادة ${Duas.hours(hrs)}", "خذ استراحة قصيرة لسلامتك", true))
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
                    hj[1] == 11 && hj[0] in 10..13 -> ""   // العيد وأيام التشريق: لا صيام
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
        // قبل مباراة فريقك بربع ساعة: من الموعد المعروف مباشرة، بدون انتظار تحديث
        val favT = Prefs.favTeams()
        if (favT.isNotEmpty() && Prefs.alertOn("match") && Prefs.bool("m_pre", true)) {
            val list = matches?.all
            if (list != null) for (m in list) {
                if (m.state == "pre" && (m.home in favT || m.away in favT) && m.time - now in 1..(15 * 60000L)
                    && preMatchShown.add(m.home + "|" + m.away + "|" + m.time)) {
                    alert(Alert(pal.ok, R.drawable.ic_ball, false,
                        "بعد ${Duas.mins(((m.time - now + 59999L) / 60000L).toInt())}: ${m.home} × ${m.away}", m.league + " · " + Ui.hm(m.time), true))
                }
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
            Ui.setT(clockTv, Ui.hmBig(now))
            gauge.set(Trip.shown().roundToInt(), Trip.gpsLive(), Trip.bearing, (Trip.maxKmh * Prefs.shownFactor()).roundToInt())
            refreshTrip()
            refreshMusic()
            refreshBigFast()
            checkAlerts(now)
            // وضع الانتظار: توقف 10 دقائق بلا لمس، ويخرج عند الحركة
            // بدون إشارة GPS لا نعرف هل السيارة واقفة، فلا يظهر تلقائياً
            if (standbyView != null && Trip.kmh() > 5f) hideStandby()
            else if (standbyView == null && Prefs.standby && Trip.gpsLive() && Trip.kmh() < 1f &&
                now - maxOf(Trip.lastMovingAt, lastTouch) > 10 * 60000L) showStandby()
            standbyClock?.let { Ui.setT(it, Ui.hmBig(now)) }
            updateQibla()
            refreshCompass()
            Ui.setT(headingTv, if (Trip.bearing >= 0f && Trip.kmh() > 8f) dirName(Trip.bearing) else "")
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
        Ui.setT(clockTv, Ui.hmBig(System.currentTimeMillis()))
        gauge.set(Trip.shown().roundToInt(), Trip.gpsLive(), Trip.bearing, (Trip.maxKmh * Prefs.shownFactor()).roundToInt())
        refreshTrip(); refreshMusic(); refreshStatus(); refreshSlow(); refreshWeather(); refreshMatches()
    }

    private fun refreshSlow() {
        val now = System.currentTimeMillis()
        refreshBigSlow(now)
        val df = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar"))
        Ui.setT(timeV, Ui.hmBig(now))
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
        refreshOccasions(now)
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
        Ui.setT(prS, "الأذان " + Ui.hm(at) + " · " + Loc.name())
        qiblaDeg = Astro.qibla(Loc.lat(), Loc.lon()).toFloat()
        updateQibla()
    }

    /** سهم القبلة يتبع اتجاه السيارة كل ثانية (تغيير خاصية فقط، بدون إعادة رسم إن لم يتغير) */
    private fun updateQibla() {
        val heading = Trip.bearing
        val rel = heading >= 0f && Trip.gpsLive()
        val rot = if (rel) qiblaDeg - heading else qiblaDeg
        if (Math.abs(qArrow.rotation - rot) > 2f) qArrow.rotation = rot
        Ui.setT(qTxt, if (rel) "بالنسبة لاتجاه السيارة" else "${qiblaDeg.roundToInt()}° من الشمال")
    }

    private fun refreshCompass() {
        val v = cmpV ?: return
        val b = Trip.bearing
        Ui.setT(v, if (b >= 0f && Trip.gpsLive()) "${dirName(b)} · ${b.roundToInt()}°" else "متوقف")
        val alt = Trip.altitude
        cmpS?.let { Ui.setT(it, if (alt.isNaN()) "الارتفاع: بانتظار GPS" else "الارتفاع ${alt.roundToInt()} م عن سطح البحر") }
    }

    private fun refreshOccasions(now: Long) {
        val v = occV ?: return
        val o = Astro.occasions(now)
        Ui.setT(v, o.getOrElse(0) { "لا مناسبات خلال 4 أشهر" })
        occS?.let { Ui.setT(it, o.getOrElse(1) { Astro.hijriText(now) }) }
    }

    private fun refreshAzkar() {
        refreshBigSlow(System.currentTimeMillis())
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

    /** اسم التطبيق من الذاكرة المؤقتة (بدون تحميل أيقونته كل ثانية) */
    private fun appLabel(pkg: String): String {
        if (pkg.isEmpty()) return ""
        return labelCache.getOrPut(pkg) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { "" }
        }
    }

    private var lastPlaying = false
    private fun refreshMusic() {
        if (Media.title.isNotEmpty()) {
            val app = appLabel(Media.app)
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
        val online = Net.online(this)
        btIv.imageTintList = ColorStateList.valueOf(if (bt) pal.accent else pal.muted)
        gpsIv.imageTintList = ColorStateList.valueOf(if (gps) pal.accent else pal.muted)
        // الواي فاي: سماوي متصل بالإنترنت، كهرماني مفعّل بلا إنترنت، رمادي متوقف
        wifiIv.imageTintList = ColorStateList.valueOf(when { wifi && online -> pal.accent; wifi -> pal.amber; else -> pal.muted })
    }

    private fun refreshWeather() {
        refreshBigWeather()
        val w = weather
        if (w == null) {
            Ui.setT(wxV, "--°"); Ui.setT(wxHead, "حالة الطقس")
            Ui.setT(wxS, Loc.name() + " · " + (if (!Net.online(this)) "لا يوجد إنترنت" else if (weatherFail) Net.lastError.ifEmpty { "تعذّر التحميل" } else "جارٍ التحميل…"))
            return
        }
        Ui.setT(wxV, "${w.temp}°")
        // آخر قراءة محفوظة تبقى ظاهرة مع وقتها، حتى بدون إنترنت
        Ui.setT(wxHead, "حالة الطقس · " + (if (weatherFail) "محفوظ " else "") + Ui.hm(weatherFrom))
        Ui.setT(wxS, (if (w.desc.isEmpty()) w.city else "${w.city} · ${w.desc}") + if (weatherFail) " · بدون اتصال" else "")
    }

    private fun refreshMatches() {
        refreshBigMatches()
        val m = matches?.main
        if (matches == null && Prefs.str("mcV").isNotEmpty()) {
            // آخر نتيجة محفوظة تظهر فوراً قبل الاتصال، وتبقى إن لم يوجد إنترنت
            Ui.setT(mHead, Prefs.str("mcH")); Ui.setT(mV, Prefs.str("mcV")); Ui.setT(mS, Prefs.str("mcS"))
            mBadge.visibility = View.VISIBLE; Ui.setT(mBadge, if (matchesFail) "بدون اتصال" else "محفوظ"); mBadge.setTextColor(pal.muted)
            return
        }
        if (m == null) {
            Ui.setT(mHead, "المباريات"); mBadge.visibility = View.GONE
            Ui.setT(mV, if (matches == null) (if (matchesFail) (if (!Net.online(this)) "لا يوجد إنترنت" else Net.lastError.ifEmpty { "تعذّر التحميل" }) else "جارٍ التحميل…") else "لا توجد مباريات اليوم أو غداً")
            Ui.setT(mS, "اضغط للقائمة · اضغط مطولاً للتحديث"); return
        }
        Ui.setT(mHead, m.league)
        mBadge.visibility = View.VISIBLE
        when (m.state) {
            "in" -> { Ui.setT(mBadge, "مباشر ${m.clock}"); mBadge.setTextColor(pal.ok) }
            "post" -> { Ui.setT(mBadge, "انتهت"); mBadge.setTextColor(pal.muted) }
            else -> { Ui.setT(mBadge, "قادم ${Ui.hm(m.time)}"); mBadge.setTextColor(pal.muted) }
        }
        Ui.setT(mV, if (m.state == "pre") "${m.home} × ${m.away}" else "${m.home} ${m.hs} — ${m.aws} ${m.away}")
        val n = matches?.next
        Ui.setT(mS, if (n != null) "قادم: ${n.home} × ${n.away} ${Ui.hm(n.time)}" else "")
        if (n == null) Ui.setT(mS, "آخر تحديث " + matchesUpdatedText())
        Prefs.putStr("mcH", mHead.text.toString()); Prefs.putStr("mcV", mV.text.toString()); Prefs.putStr("mcS", mS.text.toString())
    }

    /** تنبيهات فرقك المفضلة: قبل ربع ساعة، البداية، الأهداف، النهاية */
    private val preMatchShown = HashSet<String>()
    private fun matchAlerts(card: Net.MatchCard) {
        val fav = Prefs.favTeams()
        if (fav.isEmpty() || !Prefs.alertOn("match")) return
        for (m in card.all) {
            if (m.home !in fav && m.away !in fav) continue
            val key = m.home + "|" + m.away
            val cur = m.state + ":" + m.hs + "-" + m.aws
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

    private fun maybeFetch(now: Long) {
        if (Prefs.autoUpdate && now - Prefs.long("updChecked") > 24 * 3600000L && now - Prefs.long("updTried") > 3600000L) {
            Prefs.putLong("updTried", now)
            checkUpdate(false)
        }
        if (!weatherBusy && now - weatherAt > 30 * 60000L && !Net.online(this)) {
            // بدون شبكة لا نحاول، وننتظر عودتها (يُحدَّث تلقائياً عند الاتصال)
            weatherFail = true; weatherAt = now - 25 * 60000L; refreshWeather()
        } else if (!weatherBusy && now - weatherAt > 30 * 60000L) {
            weatherBusy = true
            val lat = Loc.lat(); val lon = Loc.lon(); val city = Loc.name()
            Thread {
                val w = Net.weather(lat, lon, city)
                h.post {
                    weatherBusy = false
                    weatherAt = if (w != null) System.currentTimeMillis() else System.currentTimeMillis() - 25 * 60000L
                    weatherFail = w == null
                    if (w != null) {
                        weather = w; weatherFrom = weatherAt
                        Prefs.putStr("wxCache", "${w.temp}|${w.desc}|${w.city}|${w.pm10}|${w.max}|${w.min}"); Prefs.putLong("wxCacheAt", weatherAt)
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
        // أثناء مباراة مباشرة (أو مباراة فريقك التي حان موعدها) نحدّث بطولتها فقط كل دقيقة؛ والتحديث الكامل كل 20 دقيقة
        val fav = Prefs.favTeams()
        val hot = HashSet<String>()
        matches?.all?.forEach { m ->
            val mine = m.home in fav || m.away in fav
            val due = m.state == "pre" && m.time - now < 2 * 60000L && now - m.time < 3 * 3600000L
            if ((m.state == "in" && (mine || m === matches?.main)) || (due && mine)) hot.add(m.code)
        }
        val full = now - matchesFullAt > 20 * 60000L
        val wanted = "matches" !in Prefs.hidden() || (fav.isNotEmpty() && Prefs.alertOn("match"))
        val due = wanted && !matchesBusy && now >= matchesTry && (full || (hot.isNotEmpty() && now - matchesAt > 60000L))
        if (due && !Net.online(this)) {
            matchesFail = true; matchesTry = now + 60000L; refreshMatches()
        } else if (due) {
            matchesBusy = true
            val prev = if (full) null else matches?.all
            Thread {
                val m = if (prev != null) Net.matches(hot, prev) else Net.matches()
                h.post {
                    matchesBusy = false
                    val t = System.currentTimeMillis()
                    if (m == null) {
                        // لا اتصال: نُبقي المعروض ونعيد المحاولة بعد دقيقتين
                        matchesFail = true; matchesTry = t + 2 * 60000L
                    } else {
                        matchesFail = false; matchesAt = t
                        if (prev == null) matchesFullAt = t
                        matches = m
                        matchAlerts(m)
                    }
                    refreshMatches()
                }
            }.start()
        }
    }
}
