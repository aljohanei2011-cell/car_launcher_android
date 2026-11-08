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
    var last: Location? = null

    fun reset() {
        distanceM = 0.0; movingMs = 0L; maxKmh = 0f; startedAt = System.currentTimeMillis(); last = null
    }
    fun avgKmh(): Float = if (movingMs < 5000) 0f else (distanceM / 1000.0 / (movingMs / 3600000.0)).toFloat()
    fun gpsLive(): Boolean = hasFix && System.currentTimeMillis() - lastFixAt < 10000
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
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        Trip.speedKmh = if (kmh < 2f) 0f else kmh
        Trip.hasFix = true
        Trip.lastFixAt = now
        if (loc.hasBearing() && kmh > 8f) Trip.bearing = loc.bearing
        val prev = Trip.last
        if (prev != null && kmh >= 3f && loc.accuracy < 40f) {
            val d = prev.distanceTo(loc)
            val dt = loc.time - prev.time
            if (d < 120f && dt in 1..5000) { Trip.distanceM += d; Trip.movingMs += dt }
        }
        Trip.last = loc
        if (Trip.speedKmh > Trip.maxKmh) Trip.maxKmh = Trip.speedKmh
        // حفظ الموقع لحساب الصلاة والطقس (مرة كل دقيقة تقريباً تكفي)
        if (now / 60000 != Prefs.int("locMin").toLong()) {
            Prefs.lat = loc.latitude; Prefs.lon = loc.longitude; Prefs.putInt("locMin", (now / 60000).toInt())
        }
        checkAlert()
    }

    private fun checkAlert() {
        val limit = Prefs.alertSpeed
        if (limit <= 0) return
        if (!alerted && Trip.speedKmh > limit) {
            alerted = true
            try {
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
