package com.example.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * خدمة في المقدمة تبقى تعمل أثناء فتح الخرائط أو الموسيقى:
 * - تتبع الرحلة بالـ GPS (المسافة، مدة القيادة، السرعة، سجل الرحلات)
 * - تنبيه الصلاة قبل الوقت وعند دخوله، مع خفض صوت الموسيقى
 * - تنبيه الاستراحة بعد القيادة المتواصلة، ودعاء السفر بعد 80 كم
 */
class TripService : Service() {

    companion object {
        const val CH_TRIP = "trip"
        const val CH_ALERT = "alerts"
        private const val ID_FG = 1
        private const val ID_ALERT = 2

        @Volatile private var instance: TripService? = null
        fun testSound(ctx: Context) {
            val i = instance
            if (i != null) i.main.post { i.playAlertSound(true, true) }
        }

        fun start(ctx: Context) {
            val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TripService::class.java))
            } catch (e: Exception) { /* بعض الأنظمة تمنع التشغيل من الخلفية؛ سيبدأ عند فتح الواجهة */ }
        }
    }

    val main = Handler(Looper.getMainLooper())
    private var player: android.media.MediaPlayer? = null
    private var overCount = 0
    private var lastSpeedAlert = 0L
    private var lm: LocationManager? = null
    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) { TripStore.onFix(this@TripService, loc); checkSpeed(loc); checkTripAlerts() }
        @Deprecated("") override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    // فحص دوري كل 20 ثانية: مواعيد الصلاة + إغلاق الرحلة عند التوقف الطويل
    private val ticker = object : Runnable {
        override fun run() {
            TripStore.closeIfIdle(this@TripService)
            checkPrayerAlarms()
            checkTripAlerts()
            main.postDelayed(this, 20_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        startForeground(ID_FG, buildFgNotification())
        main.postDelayed(ticker, 5_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startGps()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        try { player?.release() } catch (e: Exception) {}
        main.removeCallbacks(ticker)
        try { lm?.removeUpdates(listener) } catch (e: Exception) {}
        listening = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (listening) return
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            listening = true
        } catch (e: Exception) { listening = false }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRIP, "الرحلة", NotificationManager.IMPORTANCE_MIN))
        val alert = NotificationChannel(CH_ALERT, "تنبيهات الصلاة والاستراحة", NotificationManager.IMPORTANCE_HIGH)
        alert.setSound(null, null) // الصوت نشغّله بأنفسنا مع خفض الموسيقى
        nm.createNotificationChannel(alert)
    }

    private fun openAppIntent(page: String? = null): PendingIntent {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (page != null) i.putExtra("page", page)
        return PendingIntent.getActivity(this, page?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildFgNotification(): Notification =
        Notification.Builder(this, CH_TRIP)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("رفيق الطريق")
            .setContentText("متابعة الرحلة ومواقيت الصلاة")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()

    /** تنبيه: إشعار منبثق + صوت قصير + خفض صوت الموسيقى مؤقتاً */
    private fun alert(title: String, text: String, page: String?, custom: Boolean = false) {
        val cfg = Prefs.config(this)
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(page))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
        if (cfg.optBoolean("sound", true)) playAlertSound(cfg.optBoolean("duck", true), custom && cfg.optBoolean("customSound", true))
    }

    /** الصوت المرفوع من الجوال (مثل الأذان) عند دخول الوقت، وإلا نغمة قصيرة */
    fun playAlertSound(duck: Boolean, custom: Boolean) {
        val f = if (custom) Sounds.current(this) else null
        if (f == null) { playChime(duck); return }
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val req = AudioFocusRequest.Builder(if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs).build()
        try {
            player?.release()
            am.requestAudioFocus(req)
            val mp = android.media.MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { am.abandonAudioFocusRequest(req); it.release(); if (player === it) player = null }
            mp.prepare(); mp.start()
            player = mp
            // حد أقصى 6 دقائق
            main.postDelayed({ if (player === mp) { try { mp.stop(); mp.release() } catch (e: Exception) {}; player = null; am.abandonAudioFocusRequest(req) } }, 6 * 60_000L)
        } catch (e: Exception) {
            am.abandonAudioFocusRequest(req)
            playChime(duck)
        }
    }

    /** تنبيه السرعة: بعد 3 قراءات متتالية فوق الحد، ومرة كل دقيقة كحد أقصى */
    private fun checkSpeed(loc: Location) {
        val cfg = Prefs.config(this)
        if (!cfg.optBoolean("speedOn", false) || !loc.hasSpeed()) return
        val limit = cfg.optInt("speedLimit", 120)
        val kmh = loc.speed * 3.6
        if (kmh > limit) overCount++ else overCount = 0
        val now = System.currentTimeMillis()
        if (overCount >= 3 && now - lastSpeedAlert > 60_000) {
            lastSpeedAlert = now
            alert("تجاوزت السرعة", "سرعتك " + Math.round(kmh) + " كم/س والحد " + limit + " كم/س", null)
        }
    }

    private fun playChime(duck: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        var req: AudioFocusRequest? = null
        if (duck) {
            req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build()
            am.requestAudioFocus(req)
        }
        try {
            val r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            r?.audioAttributes = attrs
            r?.play()
        } catch (e: Exception) {}
        val held = req
        main.postDelayed({ if (held != null) am.abandonAudioFocusRequest(held) }, 6_000)
    }

    // ===== الصلاة: الواجهة تحسب المواقيت وترسلها هنا، والخدمة تنبّه حتى لو كانت الخرائط مفتوحة =====
    private fun checkPrayerAlarms() {
        val cfg = Prefs.config(this)
        if (!cfg.optBoolean("adhan", true)) return
        val pre = cfg.optInt("preMin", 10)
        val list = Prefs.prayers(this)
        val now = System.currentTimeMillis()
        val sp = Prefs.sp(this)
        for (i in 0 until list.length()) {
            val p = list.getJSONObject(i)
            val at = p.getLong("at")
            val name = p.getString("name")
            if (pre > 0) {
                val preAt = at - pre * 60_000L
                val k = "pre_$preAt"
                if (now >= preAt && now < at && !sp.getBoolean(k, false)) {
                    sp.edit().putBoolean(k, true).apply()
                    alert("صلاة $name بعد $pre دقائق", "استعد لصلاة $name", "prayer.html")
                }
            }
            val k2 = "at_$at"
            if (now >= at && now < at + 5 * 60_000L && !sp.getBoolean(k2, false)) {
                sp.edit().putBoolean(k2, true).apply()
                alert("حان وقت صلاة $name", "حان الآن وقت صلاة $name", "prayer.html", custom = true)
            }
        }
        Prefs.cleanupAlarmKeys(this, now)
    }

    private fun checkTripAlerts() {
        val cfg = Prefs.config(this)
        val st = TripStore.state(this)
        val restMin = cfg.optInt("restMin", 120)
        if (cfg.optBoolean("rest", true) && st.optLong("driveSec") >= restMin * 60L) {
            val k = "rest_" + st.optLong("driveStart")
            val sp = Prefs.sp(this)
            if (!sp.getBoolean(k, false)) {
                sp.edit().putBoolean(k, true).apply()
                alert("وقت الاستراحة", "تقود منذ " + (restMin / 60) + " ساعة متواصلة. خذ استراحة قصيرة.", "rest.html")
            }
        }
        if (cfg.optBoolean("travel", true) && st.optDouble("km") >= cfg.optDouble("travelKm", 80.0)) {
            val k = "travel_" + st.optLong("tripId")
            val sp = Prefs.sp(this)
            if (!sp.getBoolean(k, false)) {
                sp.edit().putBoolean(k, true).apply()
                alert("دعاء السفر", "سبحان الذي سخّر لنا هذا وما كنا له مقرنين، وإنا إلى ربنا لمنقلبون", "azkar.html?g=travel")
            }
        }
    }
}

/** إعدادات مشتركة بين الواجهة والخدمة */
object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun config(c: Context): JSONObject = try { JSONObject(sp(c).getString("config", "{}")!!) } catch (e: Exception) { JSONObject() }
    fun setConfig(c: Context, json: String) { try { JSONObject(json); sp(c).edit().putString("config", json).apply() } catch (e: Exception) {} }
    fun prayers(c: Context): JSONArray = try { JSONArray(sp(c).getString("prayers", "[]")!!) } catch (e: Exception) { JSONArray() }
    fun setPrayers(c: Context, json: String) { try { JSONArray(json); sp(c).edit().putString("prayers", json).apply() } catch (e: Exception) {} }
    /** حذف مفاتيح التنبيهات القديمة (أكثر من يومين) */
    fun cleanupAlarmKeys(c: Context, now: Long) {
        val s = sp(c); val e = s.edit(); var changed = false
        for (k in s.all.keys) {
            val t = when {
                k.startsWith("pre_") -> k.removePrefix("pre_").toLongOrNull()
                k.startsWith("at_") -> k.removePrefix("at_").toLongOrNull()
                else -> null
            }
            if (t != null && now - t > 2 * 86_400_000L) { e.remove(k); changed = true }
        }
        if (changed) e.apply()
    }
}
