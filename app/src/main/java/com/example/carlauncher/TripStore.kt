package com.example.carlauncher

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/**
 * حالة الرحلة محفوظة على الجهاز. رحلة جديدة تبدأ عند التحرك بعد توقف أطول من 10 دقائق.
 * «القيادة المتواصلة» تبدأ من جديد بعد توقف 15 دقيقة (للاستراحة).
 */
object TripStore {
    private const val IDLE_NEW_TRIP = 10 * 60_000L
    private const val IDLE_REST = 15 * 60_000L
    private const val MAX_LOG = 300

    private fun sp(c: Context) = c.getSharedPreferences("trip", Context.MODE_PRIVATE)

    // آخر قراءة حيّة (لا تُحفظ)
    @Volatile private var speedKmh = 0.0
    @Volatile private var heading = -1.0
    @Volatile private var lat = Double.NaN
    @Volatile private var lng = Double.NaN
    @Volatile private var acc = 9999.0
    @Volatile private var fixAt = 0L

    @Synchronized
    fun onFix(c: Context, loc: Location) {
        val now = System.currentTimeMillis()
        val s = sp(c)
        val e = s.edit()
        speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
        heading = if (loc.hasBearing() && speedKmh > 5) loc.bearing.toDouble() else heading
        lat = loc.latitude; lng = loc.longitude; acc = loc.accuracy.toDouble()
        val lastFix = s.getLong("lastFix", 0L)
        val dtSec = if (lastFix == 0L) 0.0 else ((now - lastFix) / 1000.0).coerceIn(0.0, 5.0)
        fixAt = now
        e.putLong("lastFix", now)
        e.putString("lastLat", lat.toString()); e.putString("lastLng", lng.toString())

        // المسافة: فقط بدقة جيدة وحركة حقيقية حتى لا يتراكم الانجراف وأنت واقف
        var d = 0.0
        if (loc.accuracy <= 30f) {
            val pLat = s.getString("ptLat", null)?.toDoubleOrNull()
            val pLng = s.getString("ptLng", null)?.toDoubleOrNull()
            if (pLat == null || pLng == null) {
                e.putString("ptLat", lat.toString()); e.putString("ptLng", lng.toString())
            } else {
                val r = FloatArray(1)
                Location.distanceBetween(pLat, pLng, lat, lng, r)
                val m = r[0].toDouble()
                if (m >= 20 || (m >= 5 && speedKmh > 3)) {
                    d = m / 1000.0
                    e.putString("ptLat", lat.toString()); e.putString("ptLng", lng.toString())
                }
            }
        }

        val moving = speedKmh > 5
        if (moving) {
            val lastMove = s.getLong("lastMove", 0L)
            if (s.getLong("tripId", 0L) == 0L || now - lastMove > IDLE_NEW_TRIP) {
                finishTrip(c, e, s)
                e.putLong("tripId", now); e.putLong("tripStart", now)
                e.putFloat("km", 0f); e.putLong("movingSec", 0L); e.putFloat("maxSpeed", 0f)
            }
            if (lastMove == 0L || now - lastMove > IDLE_REST) e.putLong("driveStart", now)
            e.putLong("lastMove", now)
            // آخر نقطة تحركت فيها السيارة = مكان الوقوف
            e.putString("parkLat", lat.toString()); e.putString("parkLng", lng.toString()); e.putLong("parkAt", now)
            e.putLong("movingSec", s.getLong("movingSec", 0L) + dtSec.toLong().coerceAtLeast(if (dtSec > 0) 1 else 0))
            if (speedKmh > s.getFloat("maxSpeed", 0f)) e.putFloat("maxSpeed", speedKmh.toFloat())
        }
        if (d > 0) {
            e.putFloat("km", (s.getFloat("km", 0f) + d).toFloat())
            e.putFloat("odo", (s.getFloat("odo", 0f) + d).toFloat())
        }
        e.apply()
    }

    /** إغلاق الرحلة وحفظها في السجل بعد توقف طويل */
    @Synchronized
    fun closeIfIdle(c: Context) {
        val s = sp(c)
        val id = s.getLong("tripId", 0L)
        if (id != 0L && System.currentTimeMillis() - s.getLong("lastMove", 0L) > IDLE_NEW_TRIP) {
            val e = s.edit()
            finishTrip(c, e, s)
            e.putLong("tripId", 0L)
            e.apply()
        }
    }

    private fun finishTrip(c: Context, e: android.content.SharedPreferences.Editor, s: android.content.SharedPreferences) {
        val id = s.getLong("tripId", 0L)
        val km = s.getFloat("km", 0f)
        if (id == 0L || km < 0.3f) return
        val log = try { JSONArray(s.getString("log", "[]")) } catch (x: Exception) { JSONArray() }
        val o = JSONObject()
        o.put("start", s.getLong("tripStart", id))
        o.put("end", s.getLong("lastMove", id))
        o.put("km", Math.round(km * 10) / 10.0)
        o.put("movingSec", s.getLong("movingSec", 0L))
        o.put("maxSpeed", Math.round(s.getFloat("maxSpeed", 0f)))
        log.put(o)
        val trimmed = JSONArray()
        val from = (log.length() - MAX_LOG).coerceAtLeast(0)
        for (i in from until log.length()) trimmed.put(log.get(i))
        e.putString("log", trimmed.toString())
    }

    @Synchronized
    fun reset(c: Context) {
        val s = sp(c); val e = s.edit()
        finishTrip(c, e, s)
        e.putLong("tripId", 0L); e.putFloat("km", 0f); e.putLong("movingSec", 0L); e.putFloat("maxSpeed", 0f)
        e.remove("ptLat"); e.remove("ptLng")
        e.apply()
    }

    fun setOdometer(c: Context, km: Double) { sp(c).edit().putFloat("odo", km.toFloat()).apply() }

    fun state(c: Context): JSONObject {
        val s = sp(c)
        val now = System.currentTimeMillis()
        val id = s.getLong("tripId", 0L)
        val lastMove = s.getLong("lastMove", 0L)
        val drivingNow = lastMove != 0L && now - lastMove <= IDLE_REST
        val o = JSONObject()
        o.put("gps", fixAt != 0L && now - fixAt < 20_000)
        o.put("tripId", id)
        o.put("km", if (id == 0L) 0.0 else s.getFloat("km", 0f).toDouble())
        o.put("movingSec", if (id == 0L) 0L else s.getLong("movingSec", 0L))
        o.put("tripStart", if (id == 0L) 0L else s.getLong("tripStart", 0L))
        o.put("maxSpeed", if (id == 0L) 0.0 else s.getFloat("maxSpeed", 0f).toDouble())
        o.put("driveStart", if (drivingNow) s.getLong("driveStart", 0L) else 0L)
        o.put("driveSec", if (drivingNow) (lastMove - s.getLong("driveStart", lastMove)) / 1000 else 0L)
        o.put("speed", if (fixAt != 0L && now - fixAt < 5_000) speedKmh else 0.0)
        o.put("heading", heading)
        val la = if (!lat.isNaN()) lat else s.getString("lastLat", null)?.toDoubleOrNull()
        val ln = if (!lng.isNaN()) lng else s.getString("lastLng", null)?.toDoubleOrNull()
        if (la != null && ln != null) { o.put("lat", la); o.put("lng", ln) }
        o.put("acc", acc)
        o.put("odo", s.getFloat("odo", 0f).toDouble())
        return o
    }

    fun park(c: Context): String {
        val s = sp(c)
        val la = s.getString("parkLat", null)?.toDoubleOrNull() ?: return "{}"
        val ln = s.getString("parkLng", null)?.toDoubleOrNull() ?: return "{}"
        return JSONObject().put("lat", la).put("lng", ln).put("at", s.getLong("parkAt", 0L)).toString()
    }

    fun log(c: Context): String = sp(c).getString("log", "[]") ?: "[]"
}
