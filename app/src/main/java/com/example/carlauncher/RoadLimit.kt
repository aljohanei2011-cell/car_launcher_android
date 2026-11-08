package com.example.carlauncher

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * حد السرعة للطريق الحالي من OpenStreetMap (خدمة Overpass العامة، بدون مفتاح).
 * طلب صغير كل 20 ثانية على الأكثر وعند التحرك 250 متراً، ويُرسل الموقع التقريبي فقط لخدمة Overpass.
 * إن لم يكن للطريق حد مسجّل في OpenStreetMap يبقى الحد «غير معروف» (0) ولا يُنبَّه.
 */
object RoadLimit {
    @Volatile var limit = 0
    @Volatile var road = ""
    @Volatile var at = 0L
    @Volatile private var busy = false
    private var qLat = 0.0
    private var qLng = 0.0
    private var qAt = 0L
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    // كلما كان الطريق أعلى درجة فضّلناه عند وجود أكثر من طريق قريب (طريق خدمة بجانب سريع مثلاً)
    private val RANK = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "motorway_link", "trunk_link", "primary_link",
        "secondary_link", "unclassified", "residential", "living_street", "service")

    fun on(c: Context) = Prefs.config(c).optBoolean("roadLimitOn", false)
    fun fresh() = limit > 0 && System.currentTimeMillis() - at < 3 * 60_000L

    fun json(): String = JSONObject().put("limit", if (fresh()) limit else 0).put("road", road).put("at", at).toString()

    fun onFix(c: Context, loc: Location) {
        if (!on(c) || busy) return
        val now = System.currentTimeMillis()
        val moved = FloatArray(1).also { Location.distanceBetween(qLat, qLng, loc.latitude, loc.longitude, it) }[0]
        if (now - qAt < 20_000L || (moved < 250f && now - qAt < 120_000L)) return
        if (!online(c)) return
        busy = true; qAt = now; qLat = loc.latitude; qLng = loc.longitude
        val lat = loc.latitude; val lng = loc.longitude
        thread(name = "roadlimit") {
            try {
                val q = "[out:json][timeout:10];way(around:30,%.6f,%.6f)[highway][maxspeed];out tags 8;".format(java.util.Locale.US, lat, lng)
                var res: JSONObject? = null
                for (ep in ENDPOINTS) { res = try { post(ep, q) } catch (e: Exception) { null }; if (res != null) break }
                if (res == null) return@thread
                val els = res.optJSONArray("elements")
                var best = 0; var bestRank = Int.MAX_VALUE; var bestName = ""
                if (els != null) for (i in 0 until els.length()) {
                    val t = els.optJSONObject(i)?.optJSONObject("tags") ?: continue
                    val v = parse(t.optString("maxspeed")); if (v <= 0) continue
                    val r = RANK.indexOf(t.optString("highway")).let { if (it < 0) RANK.size else it }
                    if (r < bestRank) { bestRank = r; best = v; bestName = t.optString("name:ar", t.optString("name")) }
                }
                limit = best; road = bestName; at = System.currentTimeMillis()
            } catch (e: Exception) {
            } finally { busy = false }
        }
    }

    /** «120» أو «120 km/h» أو «75 mph» */
    private fun parse(s: String): Int {
        val n = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: return 0
        return if (s.contains("mph")) Math.round(n * 1.609f) else n
    }

    private fun post(ep: String, q: String): JSONObject {
        val conn = URL(ep).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 12000
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("User-Agent", "RafiqAltariq/" + BuildConfig.VERSION_CODE)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) throw java.io.IOException("HTTP " + conn.responseCode)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun online(c: Context) = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) { true }
}
