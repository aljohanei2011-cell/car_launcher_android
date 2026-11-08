package com.aljohanei.rafiq

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** مدن المملكة الرئيسية بإحداثياتها: مدمجة بدون إنترنت */
object Cities {
    class City(val name: String, val lat: Double, val lon: Double)

    val ALL = listOf(
        City("ينبع", 24.0895, 38.0637), City("الرياض", 24.7136, 46.6753), City("جدة", 21.4858, 39.1925),
        City("مكة المكرمة", 21.3891, 39.8579), City("المدينة المنورة", 24.5247, 39.5692), City("الدمام", 26.4207, 50.0888),
        City("الخبر", 26.2172, 50.1971), City("الظهران", 26.2361, 50.0393), City("الأحساء", 25.3830, 49.5860),
        City("الجبيل", 27.0046, 49.6460), City("القطيف", 26.5196, 50.0115), City("حفر الباطن", 28.4328, 45.9708),
        City("الطائف", 21.2703, 40.4158), City("تبوك", 28.3835, 36.5662), City("بريدة", 26.3260, 43.9750),
        City("عنيزة", 26.0840, 43.9930), City("الرس", 25.8694, 43.4973), City("حائل", 27.5114, 41.6900),
        City("سكاكا", 29.9697, 40.2064), City("عرعر", 30.9753, 41.0381), City("القريات", 31.3317, 37.3428),
        City("طريف", 31.6725, 38.6637), City("أبها", 18.2164, 42.5053), City("خميس مشيط", 18.3000, 42.7333),
        City("نجران", 17.5656, 44.2289), City("جازان", 16.8892, 42.5511), City("صبيا", 17.1495, 42.6254),
        City("الباحة", 20.0129, 41.4677), City("بيشة", 19.9839, 42.6052), City("القنفذة", 19.1264, 41.0789),
        City("الليث", 20.1500, 40.2667), City("رابغ", 22.7986, 39.0349), City("العلا", 26.6080, 37.9232),
        City("الوجه", 26.2455, 36.4525), City("أملج", 25.0213, 37.2685), City("ضباء", 27.3510, 35.6901),
        City("بدر", 23.7800, 38.7900), City("خيبر", 25.6950, 39.2920), City("الخرج", 24.1556, 47.3347),
        City("المجمعة", 25.9039, 45.3456), City("الزلفي", 26.2994, 44.8156), City("الدوادمي", 24.5077, 44.3924),
        City("وادي الدواسر", 20.4607, 44.7790), City("شرورة", 17.4867, 47.1053)
    )

    fun byName(n: String): City = ALL.firstOrNull { it.name == n } ?: ALL[0]

    /** المسافة بالكيلومتر */
    fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    fun nearest(lat: Double, lon: Double): Pair<City, Double> {
        var best = ALL[0]; var d = Double.MAX_VALUE
        for (c in ALL) { val k = km(lat, lon, c.lat, c.lon); if (k < d) { d = k; best = c } }
        return Pair(best, d)
    }

    /** اسم يُعرض في الوضع التلقائي: المدينة، أو «قرب المدينة» إن كنت خارجها */
    fun nearName(lat: Double, lon: Double): String {
        val (c, d) = nearest(lat, lon)
        return if (d <= 35) c.name else "قرب ${c.name}"
    }
}

/** الموقع الفعّال للصلاة والطقس: GPS في الوضع التلقائي، أو المدينة المختارة */
object Loc {
    fun manual() = Prefs.cityMode == "manual"
    fun lat(): Double = if (manual()) Cities.byName(Prefs.city).lat else Prefs.lat
    fun lon(): Double = if (manual()) Cities.byName(Prefs.city).lon else Prefs.lon
    fun name(): String = if (manual()) Prefs.city else Cities.nearName(Prefs.lat, Prefs.lon)
}
