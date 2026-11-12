package com.aljohanei.rafiq

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
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper

/** حالة الرحلة في الذاكرة: تكتبها الخدمة وتقرؤها الواجهة كل ثانية */
object Trip {
    @Volatile var speedKmh = 0f
    @Volatile var hasFix = false
    @Volatile var lastFixAt = 0L
    @Volatile var bearing = -1f
    @Volatile var distanceM = 0.0
    @Volatile var movingMs = 0L
    @Volatile var maxKmh = 0f
    @Volatile var startedAt = System.currentTimeMillis()
    @Volatile var altitude = Double.NaN
    var last: Location? = null
    // القيادة المتواصلة (للاستراحة) وبداية الركوب (للدعاء) والابتعاد (لدعاء السفر)
    @Volatile var driveStart = 0L
    @Volatile var lastMovingAt = 0L
    @Volatile var rideEvent = 0
    @Volatile var travelEvent = 0
    var rideLat = 0.0
    var rideLon = 0.0
    var travelShown = false
    // عدّادات الرحلة الحالية لسجل الرحلات (تُحفظ كل دقيقة)
    @Volatile var rideStartAt = 0L
    @Volatile var rideDistM = 0.0
    @Volatile var rideMovingMs = 0L
    @Volatile var rideMaxKmh = 0f

    /** إغلاق الرحلة الحالية وإضافتها للسجل (آخر 20 رحلة)، إن تجاوزت نصف كيلومتر */
    fun closeRide() {
        if (rideDistM >= 500 && rideStartAt > 0) {
            val avg = if (rideMovingMs < 5000) 0 else (rideDistM / 1000.0 / (rideMovingMs / 3600000.0)).toInt()
            val e = "$rideStartAt|${rideDistM.toInt()}|$rideMovingMs|$avg|${rideMaxKmh.toInt()}"
            val old = Prefs.str("tripLog").split(";").filter { it.isNotEmpty() }
            Prefs.putStr("tripLog", (listOf(e) + old).take(20).joinToString(";"))
        }
        rideStartAt = 0L; rideDistM = 0.0; rideMovingMs = 0L; rideMaxKmh = 0f
        Prefs.putStr("ride", "")
    }
    fun saveRide() { Prefs.putStr("ride", "$rideStartAt|$rideDistM|$rideMovingMs|$rideMaxKmh|$lastMovingAt|$rideLat|$rideLon|$travelShown") }
    /** عند تشغيل الخدمة: استرجاع رحلة لم تُغلق، وإغلاقها إن مضى عليها أكثر من ساعة */
    fun restoreRide() {
        val p = Prefs.str("ride").split("|")
        if (p.size < 5) return
        try {
            rideStartAt = p[0].toLong(); rideDistM = p[1].toDouble(); rideMovingMs = p[2].toLong(); rideMaxKmh = p[3].toFloat()
            val last = p[4].toLong()
            // بدون نقطة بداية محفوظة لا نحسب «الابتعاد» حتى لا يظهر دعاء السفر خطأً
            if (p.size >= 8) { rideLat = p[5].toDouble(); rideLon = p[6].toDouble(); travelShown = p[7] == "true" } else travelShown = true
            if (System.currentTimeMillis() - last > 3600000L) closeRide() else lastMovingAt = last
        } catch (e: Exception) { Prefs.putStr("ride", "") }
    }

    fun reset() {
        distanceM = 0.0; movingMs = 0L; maxKmh = 0f; startedAt = System.currentTimeMillis(); last = null
    }
    fun avgKmh(): Float = if (movingMs < 5000) 0f else (distanceM / 1000.0 / (movingMs / 3600000.0)).toFloat()
    fun gpsLive(): Boolean = hasFix && System.currentTimeMillis() - lastFixAt < 10000
    /** السرعة الحالية، وصفر إن انقطعت إشارة GPS (حتى لا تبقى آخر سرعة عالقة) */
    fun kmh(): Float = if (gpsLive()) speedKmh else 0f
    /** السرعة المعروضة: الحقيقية، أو المعايَرة لتطابق عداد السيارة */
    fun shown(): Float = kmh() * Prefs.shownFactor()
}

/**
 * خدمة واحدة في المقدمة: تستقبل GPS كل ثانية حتى أثناء فتح الخرائط أو الموسيقى،
 * فتبقى السرعة وكمبيوتر الرحلة وتنبيه السرعة صحيحة. لا تعمل شيئاً آخر.
 */
class TripService : Service(), LocationListener {
    private var lm: LocationManager? = null
    private var tone: ToneGenerator? = null
    private var alerted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Trip.restoreRide()
        startForeground(1, notification())
        startGps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (lm == null) startGps()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            lm = m
        } catch (e: Exception) { lm = null }
    }

    override fun onLocationChanged(loc: Location) {
        val now = System.currentTimeMillis()
        var kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val prev = Trip.last
        // بعض الشاشات لا ترسل السرعة مع الموقع: نحسبها من المسافة والزمن
        if (!loc.hasSpeed() && prev != null && loc.accuracy < 25f) {
            val dtMs = loc.time - prev.time
            if (dtMs in 500..5000) kmh = prev.distanceTo(loc) / (dtMs / 1000f) * 3.6f
        }
        // ارتجاف GPS عند الوقوف قد يعطي 2–4 كم/س: نعتبره صفراً
        Trip.speedKmh = if (kmh < 4f || (loc.accuracy > 30f && kmh < 8f)) 0f else kmh
        Trip.hasFix = true
        if (loc.hasAltitude()) Trip.altitude = loc.altitude
        Trip.lastFixAt = now
        if (loc.hasBearing() && kmh > 8f) Trip.bearing = loc.bearing
        if (prev != null && kmh >= 3f && loc.accuracy < 40f) {
            val d = prev.distanceTo(loc)
            val dt = loc.time - prev.time
            if (d < 120f && dt in 1..5000) {
                Trip.distanceM += d; Trip.movingMs += dt
                Trip.rideDistM += d; Trip.rideMovingMs += dt
            }
        }
        Trip.last = loc
        if (Trip.speedKmh > Trip.maxKmh) Trip.maxKmh = Trip.speedKmh
        if (kmh >= 8f) {
            val idle = now - Trip.lastMovingAt
            // بعد توقف ساعة أو أكثر: ركوب جديد
            if (Trip.lastMovingAt == 0L || idle > 3600000L) {
                Trip.closeRide(); Trip.rideStartAt = now
                Trip.rideEvent++; Trip.rideLat = loc.latitude; Trip.rideLon = loc.longitude; Trip.travelShown = false
            }
            // توقف ربع ساعة يبدأ عدّ القيادة المتواصلة من جديد
            if (Trip.lastMovingAt == 0L || idle > 15 * 60000L) Trip.driveStart = now
            Trip.lastMovingAt = now
            if (Trip.speedKmh > Trip.rideMaxKmh) Trip.rideMaxKmh = Trip.speedKmh
            if (!Trip.travelShown && Cities.km(Trip.rideLat, Trip.rideLon, loc.latitude, loc.longitude) > 50.0) {
                Trip.travelShown = true; Trip.travelEvent++
            }
        }
        // حفظ الموقع لحساب الصلاة والطقس (مرة كل دقيقة تقريباً تكفي)
        if (now / 60000 != Prefs.int("locMin").toLong()) {
            Prefs.lat = loc.latitude; Prefs.lon = loc.longitude; Prefs.putInt("locMin", (now / 60000).toInt())
            if (Trip.rideStartAt > 0) Trip.saveRide()
        }
        checkAlert()
    }

    private fun checkAlert() {
        val limit = Prefs.alertSpeed
        if (limit <= 0 || !Prefs.alertOn("speed")) return
        if (!alerted && Trip.speedKmh > limit) {
            alerted = true
            if (Prefs.alertOn("sound")) try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            } catch (e: Exception) {}
        } else if (alerted && Trip.speedKmh < limit - 5) {
            alerted = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { Trip.hasFix = false; Trip.speedKmh = 0f }

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (e: Exception) {}
        try { tone?.release() } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("trip", "الرحلة", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "trip") else Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("رفيق الطريق")
            .setContentText("السرعة والرحلة تعمل")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        fun start(c: Context) {
            try {
                val i = Intent(c, TripService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {}
        }
    }
}
