package com.aljohanei.rafiq

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
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
    private var lastSlow = 0L
    private var lastStatus = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        night = Astro.isNight(System.currentTimeMillis(), Prefs.lat, Prefs.lon)
        build()
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            TripService.start(this)
        }
        PrayerAlarm.schedule(this)
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

    override fun onResume() {
        super.onResume()
        immersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* واجهة رئيسية: زر الرجوع لا يغلقها */ }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private fun palette(): Palette = when (Prefs.theme) {
        "dark" -> Palette.DARK
        "light" -> Palette.LIGHT
        "amber" -> Palette.AMBER
        else -> if (night) Palette.DARK else Palette.LIGHT
    }

    // ============================== بناء الواجهة ==============================

    private fun build() {
        pal = palette()
        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        val design = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(1280, 720); layoutDirection = View.LAYOUT_DIRECTION_LTR }
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
        buildHeader(design)
        buildBody(design)
        buildFooter(design)
        setContentView(root)
        refreshAll()
    }

    private fun buildHeader(d: FrameLayout) {
        val hdr = FrameLayout(this)
        d.addView(hdr, Ui.abs(1280, 72, 0, 0))
        hdr.addView(View(this).apply { setBackgroundColor(pal.accent) }, Ui.abs(1280, 2, 0, 70))

        val brand = Ui.tv(this, 18f, pal.text, true).apply { text = Prefs.brand; visibility = if (Prefs.brand.isEmpty()) View.GONE else View.VISIBLE }

        // يمين: اسم التطبيق (والشعار إن اختير يميناً)
        val right = Ui.row(this, Gravity.CENTER_VERTICAL)
        if (!Prefs.brandLeft) right.addView(brand, Ui.lin(Ui.WRAP, 72).apply { rightMargin = 18 })
        right.addView(Ui.tv(this, 26f, pal.accent, true).apply { text = "رفيق الطريق" }, Ui.lin(Ui.WRAP, 72))
        hdr.addView(right, Ui.at(Ui.WRAP, 72, Gravity.RIGHT or Gravity.TOP).apply { rightMargin = 20 })

        // الوسط: الساعة وأيقونة النهار أو الليل
        val mid = Ui.row(this, Gravity.CENTER)
        clockTv = Ui.tv(this, 32f, pal.text, true, Gravity.CENTER)
        dayIcon = Ui.icon(this, R.drawable.g_sun, pal.amber)
        mid.addView(clockTv, Ui.lin(Ui.WRAP, 72))
        mid.addView(dayIcon, Ui.lin(22, 22).apply { leftMargin = 10 })
        hdr.addView(mid, Ui.at(Ui.WRAP, 72, Gravity.CENTER_HORIZONTAL or Gravity.TOP))

        // يسار: الشعار، أيقونات الحالة، إغلاق الكل
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
        // العمود الأيمن: الوقت، الطقس، الرحلة، المباريات
        val rightStack = Ui.col(this)
        // العمود الأيسر: الموسيقى، الصلاة، الأذكار، الملاحة
        val leftStack = Ui.col(this)
        d.addView(rightStack, Ui.abs(340, 556, 916, 78))
        d.addView(leftStack, Ui.abs(340, 556, 24, 78))

        // الوقت
        val tc = Ui.col(this)
        tc.addView(head("الوقت والتاريخ"))
        timeV = Ui.tv(this, 38f, pal.text, true); tc.addView(timeV, Ui.lin(Ui.MATCH, 44))
        timeS1 = Ui.tv(this, 15f, pal.muted); tc.addView(timeS1, Ui.lin(Ui.MATCH, 20))
        timeS2 = Ui.tv(this, 15f, pal.amber); tc.addView(timeS2, Ui.lin(Ui.MATCH, 20))
        addCard(rightStack, "time", R.drawable.ic_clock, false, tc, hidden)

        // الطقس
        val wc = Ui.col(this)
        wc.addView(head("حالة الطقس"))
        wxV = Ui.tv(this, 38f, pal.text, true); wc.addView(wxV, Ui.lin(Ui.MATCH, 44))
        wxS = Ui.tv(this, 15f, pal.muted); wc.addView(wxS, Ui.lin(Ui.MATCH, 22))
        addCard(rightStack, "weather", R.drawable.ic_weather, true, wc, hidden)

        // كمبيوتر الرحلة
        val rc = Ui.col(this)
        rc.addView(head("كمبيوتر الرحلة"))
        tripV = Ui.tv(this, 26f, pal.text, true); rc.addView(tripV, Ui.lin(Ui.MATCH, 36))
        tripS = Ui.tv(this, 15f, pal.muted); rc.addView(tripS, Ui.lin(Ui.MATCH, 22))
        rc.addView(Ui.tv(this, 13f, pal.muted).apply { text = "اضغط مطولاً للتصفير" }, Ui.lin(Ui.MATCH, 18))
        addCard(rightStack, "trip", R.drawable.ic_trip, false, rc, hidden).setOnLongClickListener {
            Trip.reset(); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); toast("تم تصفير الرحلة"); refreshTrip(); true
        }

        // المباريات
        val mc = Ui.col(this)
        val mrow = Ui.row(this)
        mBadge = Ui.tv(this, 14f, pal.ok, false, Gravity.CENTER).apply {
            background = Ui.rounded(pal.card, 8f, 2, pal.ok); setPadding(8, 0, 8, 0)
        }
        mHead = Ui.tv(this, 16f, pal.muted)
        mrow.addView(mBadge, Ui.lin(Ui.WRAP, 22).apply { rightMargin = 8 })
        mrow.addView(mHead, Ui.lin(Ui.WRAP, 24))
        mc.addView(mrow, Ui.lin(Ui.MATCH, 26))
        mV = Ui.tv(this, 23f, pal.text, true); mc.addView(mV, Ui.lin(Ui.MATCH, 36))
        mS = Ui.tv(this, 15f, pal.muted); mc.addView(mS, Ui.lin(Ui.MATCH, 22))
        addCard(rightStack, "matches", R.drawable.ic_ball, false, mc, hidden)

        // الموسيقى
        val uc = Ui.col(this)
        muHead = head("الموسيقى"); uc.addView(muHead)
        muV = Ui.tv(this, 22f, pal.text, true); uc.addView(muV, Ui.lin(Ui.MATCH, 32))
        val ctr = Ui.row(this)
        playIv = Ui.icon(this, R.drawable.g_play, pal.onAccent)
        ctr.addView(mediaBtn(R.drawable.g_prev, false) { Media.key(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Ui.lin(48, 48))
        ctr.addView(FrameLayout(this).apply {
            background = Ui.rounded(pal.accent, 24f)
            addView(playIv, Ui.at(22, 22, Gravity.CENTER))
            setOnClickListener { Media.key(this@MainActivity, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); refreshMusic() }
        }, Ui.lin(48, 48).apply { leftMargin = 10; rightMargin = 10 })
        ctr.addView(mediaBtn(R.drawable.g_next, false) { Media.key(this, KeyEvent.KEYCODE_MEDIA_NEXT) }, Ui.lin(48, 48))
        uc.addView(ctr, Ui.lin(Ui.MATCH, 52).apply { topMargin = 4 })
        addCard(leftStack, "music", R.drawable.ic_music, false, uc, hidden).setOnClickListener {
            if (!Media.granted(this)) openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            else if (Media.app.isNotEmpty()) launch(Media.app)
        }

        // الصلاة والقبلة
        val pc = Ui.col(this)
        pc.addView(head("الصلاة القادمة"))
        prV = Ui.tv(this, 24f, pal.text, true); pc.addView(prV, Ui.lin(Ui.MATCH, 32))
        prS = Ui.tv(this, 15f, pal.muted); pc.addView(prS, Ui.lin(Ui.MATCH, 20))
        val qr = Ui.row(this)
        qTxt = Ui.tv(this, 15f, pal.muted)
        qArrow = Ui.icon(this, R.drawable.g_arrow, pal.amber)
        qr.addView(qTxt, Ui.lin(Ui.WRAP, 24))
        qr.addView(qArrow, Ui.lin(22, 22).apply { leftMargin = 6; rightMargin = 6 })
        qr.addView(Ui.tv(this, 15f, pal.muted).apply { text = "القبلة" }, Ui.lin(Ui.WRAP, 24))
        pc.addView(qr, Ui.lin(Ui.MATCH, 26))
        addCard(leftStack, "prayer", R.drawable.ic_prayer, false, pc, hidden)

        // الأذكار
        val ac = Ui.col(this)
        azHead = head("الأذكار"); ac.addView(azHead)
        azV = Ui.tv(this, 36f, pal.text, true); ac.addView(azV, Ui.lin(Ui.MATCH, 42))
        azS = Ui.tv(this, 15f, pal.muted); ac.addView(azS, Ui.lin(Ui.MATCH, 22))
        val azCard = addCard(leftStack, "azkar", R.drawable.ic_azkar, false, ac, hidden)
        azCard.setOnClickListener { Azkar.tap(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); refreshAzkar() }
        azCard.setOnLongClickListener { Azkar.skip(System.currentTimeMillis()); it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); refreshAzkar(); true }

        // الملاحة
        val nc = Ui.col(this)
        nc.addView(Ui.tv(this, 22f, pal.onAccent, true, Gravity.CENTER).apply {
            text = "ابدأ الرحلة"; background = Ui.rounded(pal.accent, 12f)
            setOnClickListener { openSlot(1) }
        }, Ui.lin(Ui.MATCH, 56))
        nc.addView(Ui.tv(this, 15f, pal.muted).apply { text = "يفتح تطبيق الخرائط" }, Ui.lin(Ui.MATCH, 22).apply { topMargin = 6 })
        addCard(leftStack, "nav", R.drawable.ic_pin, false, nc, hidden)

        // العداد وصورة السيارة
        val g = FrameLayout(this)
        d.addView(g, Ui.abs(528, 544, 376, 84))
        gauge = GaugeView(this).apply { pal = this@MainActivity.pal; mode = Prefs.gauge; max = Prefs.maxSpeed; alert = Prefs.alertSpeed }
        g.addView(gauge, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
        g.addView(ImageView(this).apply { setImageResource(R.drawable.car_rear); scaleType = ImageView.ScaleType.FIT_CENTER },
            Ui.at(260, 213, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    private fun head(s: String): TextView = Ui.tv(this, 16f, pal.muted).apply { text = s; layoutParams = Ui.lin(Ui.MATCH, 22) }

    private fun mediaBtn(res: Int, filled: Boolean, onClick: () -> Unit): View = FrameLayout(this).apply {
        background = if (filled) Ui.rounded(pal.accent, 24f) else Ui.rounded(pal.card, 24f, 2, pal.line)
        addView(Ui.icon(context, res, pal.text), Ui.at(20, 20, Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    /** بطاقة: نص على اليمين وبلاطة الأيقونة في أقصى اليمين */
    private fun addCard(stack: LinearLayout, id: String, icon: Int, warm: Boolean, content: LinearLayout, hidden: Set<String>): View {
        val card = Ui.row(this, Gravity.CENTER_VERTICAL).apply {
            background = Ui.cardBg(pal)
            setPadding(14, 10, 14, 8)
            visibility = if (id in hidden) View.GONE else View.VISIBLE
        }
        card.addView(content, Ui.lin(0, Ui.MATCH, 1f))
        val tile = FrameLayout(this).apply {
            background = if (warm) Ui.rounded(pal.warmTile, 18f, 2, pal.warmLine) else Ui.rounded(pal.tile, 18f, 2, pal.tileLine)
            addView(Ui.icon(context, icon), Ui.at(58, 58, Gravity.CENTER))
        }
        card.addView(tile, Ui.lin(76, 76).apply { leftMargin = 14 })
        stack.addView(card, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 6; bottomMargin = 6 })
        return card
    }

    // ============================== القائمة السفلية ==============================

    private val slotNames = arrayOf("الرئيسية", "الملاحة", "الموسيقى", "التطبيقات", "معلومات السيارة", "الإعدادات")
    private val slotIcons = intArrayOf(R.drawable.g_home, R.drawable.g_nav, R.drawable.g_music, R.drawable.g_apps, R.drawable.g_car, R.drawable.g_gear)

    private fun buildFooter(d: FrameLayout) {
        val f = FrameLayout(this)
        d.addView(f, Ui.abs(1280, 80, 0, 640))
        f.addView(View(this).apply { setBackgroundColor(pal.line) }, Ui.abs(1280, 2, 0, 0))
        val bar = Ui.row(this, Gravity.CENTER)
        f.addView(bar, Ui.abs(1280, 78, 0, 2))
        // من اليمين إلى اليسار: الرئيسية أولاً
        for (i in 5 downTo 0) bar.addView(slotView(i), Ui.lin(0, Ui.MATCH, 1f))
    }

    private fun slotView(i: Int): View {
        val v = Ui.col(this, Gravity.CENTER)
        val active = i == 0
        val color = if (active) pal.accent else pal.muted
        v.addView(View(this).apply { setBackgroundColor(if (active) pal.accent else 0) }, Ui.lin(150, 3).apply { bottomMargin = 8 })
        val pkg = Prefs.slot(i)
        var label = slotNames[i]
        val iv: ImageView
        val custom = if (pkg.isNotEmpty()) appInfo(pkg) else null
        if (custom != null) {
            iv = ImageView(this).apply { setImageDrawable(custom.second); scaleType = ImageView.ScaleType.FIT_CENTER }
            label = custom.first
        } else {
            iv = Ui.icon(this, slotIcons[i], color)
        }
        v.addView(iv, Ui.lin(30, 30))
        v.addView(Ui.tv(this, 16f, color, active, Gravity.CENTER).apply { text = label }, Ui.lin(Ui.MATCH, 26).apply { topMargin = 4 })
        v.setOnClickListener { openSlot(i) }
        if (i in 1..4) v.setOnLongClickListener { pickApp(i); true }
        return v
    }

    private fun openSlot(i: Int) {
        val pkg = Prefs.slot(i)
        if (i in 1..4 && pkg.isNotEmpty() && launch(pkg)) return
        when (i) {
            1 -> openMaps()
            2 -> openMusic()
            3 -> Drawer.show(this, pal) { launch(it) }
            4 -> carInfo()
            5 -> SettingsDialog.show(this, pal) { rebuildKeepData() }
        }
    }

    private fun appInfo(pkg: String): Pair<String, android.graphics.drawable.Drawable>? = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        Pair(packageManager.getApplicationLabel(ai).toString(), packageManager.getApplicationIcon(ai))
    } catch (e: Exception) { null }

    private fun pickApp(i: Int) {
        Drawer.pick(this, "اختر تطبيقاً لخانة «${slotNames[i]}»") { pkg ->
            Prefs.setSlot(i, pkg)
            rebuildKeepData()
        }
    }

    fun launch(pkg: String): Boolean {
        val it = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    private fun openMaps() {
        for (p in arrayOf("com.waze", "com.google.android.apps.maps", "com.here.app.maps", "ru.yandex.yandexnavi")) if (launch(p)) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { toast("لا يوجد تطبيق خرائط. اضغط مطولاً على «الملاحة» لاختيار تطبيق") }
    }

    private fun openMusic() {
        if (Media.app.isNotEmpty() && launch(Media.app)) return
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("اضغط مطولاً على «الموسيقى» لاختيار تطبيق") }
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
        }
    }

    private fun carInfo() {
        val dist = String.format(Locale.US, "%.1f", Trip.distanceM / 1000.0)
        val mins = Trip.movingMs / 60000
        val msg = "المسافة: $dist كم\nمدة الحركة: $mins دقيقة\nمتوسط السرعة: ${Trip.avgKmh().roundToInt()} كم/س\n" +
            "أعلى سرعة: ${Trip.maxKmh.roundToInt()} كم/س\nالسرعة الآن: ${Trip.speedKmh.roundToInt()} كم/س\n" +
            "GPS: " + (if (Trip.gpsLive()) "متصل" else "غير متصل")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("معلومات السيارة والرحلة").setMessage(msg)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("تصفير الرحلة") { _, _ -> Trip.reset(); refreshTrip() }
            .show()
    }

    /** إغلاق التطبيقات التي تعمل في الخلفية لتحرير الذاكرة */
    private fun closeAll() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        Thread {
            var n = 0
            try {
                val list = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                for (ri in list) {
                    val p = ri.activityInfo.packageName
                    if (p == packageName) continue
                    try { am.killBackgroundProcesses(p); n++ } catch (e: Exception) {}
                }
            } catch (e: Exception) {}
            Thread.sleep(600)
            val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val freed = ((after - before) / (1024 * 1024)).coerceAtLeast(0)
            h.post { toast(if (freed > 0) "تم إغلاق تطبيقات الخلفية وتحرير $freed ميجا" else "تم إغلاق تطبيقات الخلفية") }
        }.start()
    }

    private fun rebuildKeepData() {
        night = Astro.isNight(System.currentTimeMillis(), Prefs.lat, Prefs.lon)
        build()
        PrayerAlarm.schedule(this)
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ============================== التحديث ==============================

    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            val now = System.currentTimeMillis()
            Ui.setT(clockTv, SimpleDateFormat("HH:mm", Locale.US).format(Date(now)))
            gauge.set(Trip.speedKmh.roundToInt(), Trip.gpsLive())
            refreshTrip()
            refreshMusic()
            if (now - lastStatus > 5000) { lastStatus = now; refreshStatus() }
            if (now - lastSlow > 30000) {
                lastSlow = now
                val n = Astro.isNight(now, Prefs.lat, Prefs.lon)
                if (n != night) {
                    night = n
                    if (Prefs.theme == "auto") { build(); h.postDelayed(this, 1000); return }
                }
                refreshSlow()
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
        // خفض الإضاءة ليلاً لنافذة الواجهة فقط (بدون صلاحيات النظام)
        val lp = window.attributes
        val want = if (Prefs.nightDim && night) 0.35f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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
        val (idx, at) = Astro.nextPrayer(now, Prefs.lat, Prefs.lon)
        val mins = ((at - now) / 60000L).toInt()
        val left = if (mins >= 60) "بعد ${mins / 60} س ${mins % 60} د" else "بعد $mins د"
        Ui.setT(prV, "${Astro.PRAYER_NAMES[idx]} · $left")
        Ui.setT(prS, "الأذان " + SimpleDateFormat("HH:mm", Locale.US).format(Date(at)))
        val q = Astro.qibla(Prefs.lat, Prefs.lon).toFloat()
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
        val morning = Azkar.isMorning(now)
        val list = Azkar.list(now)
        val s = Azkar.state(now)
        Ui.setT(azHead, if (morning) "أذكار الصباح" else "أذكار المساء")
        if (s[0] >= list.size) {
            Ui.setT(azV, "تمت ✓"); Ui.setT(azS, "تقبّل الله · اضغط مطولاً للبدء من جديد")
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
    }

    @Suppress("DEPRECATION")
    private fun refreshStatus() {
        val bt = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }
        val gps = try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val wifi = try { (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (e: Exception) { false }
        btIv.imageTintList = android.content.res.ColorStateList.valueOf(if (bt) pal.accent else pal.muted)
        gpsIv.imageTintList = android.content.res.ColorStateList.valueOf(if (gps) pal.accent else pal.muted)
        wifiIv.imageTintList = android.content.res.ColorStateList.valueOf(if (wifi) pal.accent else pal.muted)
    }

    private fun refreshWeather() {
        val w = weather
        if (w == null) { Ui.setT(wxV, "--°"); Ui.setT(wxS, "بانتظار الاتصال"); return }
        Ui.setT(wxV, "${w.temp}°")
        Ui.setT(wxS, if (w.desc.isEmpty()) w.city else "${w.city} · ${w.desc}")
    }

    private fun refreshMatches() {
        val m = matches?.main
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
    }

    private fun maybeFetch(now: Long) {
        if (!weatherBusy && now - weatherAt > 30 * 60000L) {
            weatherBusy = true
            val lat = Prefs.lat; val lon = Prefs.lon
            Thread {
                val w = Net.weather(applicationContext, lat, lon)
                h.post {
                    weatherBusy = false
                    // عند الفشل نعيد المحاولة بعد 5 دقائق
                    weatherAt = if (w != null) System.currentTimeMillis() else System.currentTimeMillis() - 25 * 60000L
                    if (w != null) weather = w
                    refreshWeather()
                }
            }.start()
        }
        val interval = if (matches?.anyLive() == true) 60000L else 20 * 60000L
        if (Prefs.CARD_IDS.indexOf("matches") >= 0 && "matches" !in Prefs.hidden() && !matchesBusy && now - matchesAt > interval) {
            matchesBusy = true
            Thread {
                val m = Net.matches()
                h.post {
                    matchesBusy = false
                    matchesAt = System.currentTimeMillis()
                    if (m.main != null || matches == null) matches = m
                    refreshMatches()
                }
            }.start()
        }
    }
}
