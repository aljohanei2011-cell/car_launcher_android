package com.aljohanei.rafiq

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * حسابات محلية بالكامل بدون إنترنت:
 * أوقات الصلاة بطريقة أم القرى (الفجر 18.5°، العشاء بعد المغرب بـ90 دقيقة و120 في رمضان)،
 * الشروق والغروب للثيم التلقائي، اتجاه القبلة، والتاريخ الهجري.
 */
object Astro {
    val PRAYER_NAMES = arrayOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    private val HIJRI_MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1.0 / x))
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** [declination, equationOfTime] */
    private fun sunPos(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0, 24.0)
        val eqt = q / 15.0 - ra
        val decl = darcsin(dsin(e) * dsin(l))
        return doubleArrayOf(decl, eqt)
    }

    /**
     * أوقات اليوم بالساعات المحلية العشرية: الفجر، الشروق، الظهر، العصر، المغرب، العشاء
     */
    fun prayerHours(cal: Calendar, lat: Double, lon: Double, ramadan: Boolean): DoubleArray {
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0
        val jDate = julian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) - lon / (15.0 * 24.0)

        fun midDay(t: Double): Double { val eqt = sunPos(jDate + t)[1]; return fix(12 - eqt, 24.0) }
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPos(jDate + t)[0]
            val noon = midDay(t)
            val tt = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15.0
            return noon + if (ccw) -tt else tt
        }
        fun asr(t: Double): Double {
            val decl = sunPos(jDate + t)[0]
            val angle = -darccot(1.0 + dtan(abs(lat - decl)))
            return angleTime(angle, t, false)
        }

        val fajr = angleTime(18.5, 5 / 24.0, true)
        val sunrise = angleTime(0.833, 6 / 24.0, true)
        val dhuhr = midDay(12 / 24.0)
        val asr = asr(13 / 24.0)
        val maghrib = angleTime(0.833, 18 / 24.0, false)
        val adj = tz - lon / 15.0
        val out = doubleArrayOf(fajr, sunrise, dhuhr, asr, maghrib, 0.0)
        for (i in 0..4) out[i] = out[i] + adj
        out[2] += 1 / 60.0
        out[5] = out[4] + (if (ramadan) 120 else 90) / 60.0
        return out
    }

    /** وقت كل صلاة اليوم بالمللي ثانية */
    fun prayerMillis(day: Calendar, lat: Double, lon: Double): LongArray {
        val h = prayerHours(day, lat, lon, hijri(day.timeInMillis)[1] == 8)
        val base = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // ضبط الدقائق يدوياً ليطابق مسجد الحي
        return LongArray(6) { base + (h[it] * 3600000.0).toLong() + Prefs.adj(it) * 60000L }
    }

    /** الصلاة القادمة (بدون الشروق): [index, millis] */
    fun nextPrayer(now: Long, lat: Double, lon: Double): Pair<Int, Long> {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val t = prayerMillis(today, lat, lon)
        for (i in intArrayOf(0, 2, 3, 4, 5)) if (t[i] > now) return Pair(i, t[i])
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return Pair(0, prayerMillis(tomorrow, lat, lon)[0])
    }

    /** ليل إن كان الوقت قبل الشروق أو بعد المغرب */
    fun isNight(now: Long, lat: Double, lon: Double): Boolean {
        val t = prayerMillis(Calendar.getInstance().apply { timeInMillis = now }, lat, lon)
        return now < t[1] || now >= t[4]
    }

    /** اتجاه القبلة بالدرجات من الشمال الجغرافي */
    fun qibla(lat: Double, lon: Double): Double {
        val kLat = 21.4225; val kLon = 39.8262
        val dl = Math.toRadians(kLon - lon)
        val p1 = Math.toRadians(lat); val p2 = Math.toRadians(kLat)
        val b = Math.toDegrees(atan2(sin(dl), cos(p1) * tan(p2) - sin(p1) * cos(dl)))
        return fix(b, 360.0)
    }

    /** [يوم، شهر (0-11)، سنة] بتقويم أم القرى */
    fun hijri(millis: Long): IntArray {
        return try {
            val ic = android.icu.util.IslamicCalendar()
            ic.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            ic.timeInMillis = millis
            intArrayOf(
                ic.get(android.icu.util.Calendar.DAY_OF_MONTH),
                ic.get(android.icu.util.Calendar.MONTH),
                ic.get(android.icu.util.Calendar.YEAR)
            )
        } catch (e: Throwable) { intArrayOf(0, -1, 0) }
    }

    fun hijriText(millis: Long): String {
        val h = hijri(millis)
        if (h[1] !in 0..11) return ""
        return "${h[0]} ${HIJRI_MONTHS[h[1]]} ${h[2]}هـ"
    }
}
