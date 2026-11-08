package com.aljohanei.rafiq

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** الطلبات الوحيدة على الإنترنت: الطقس والمباريات، وكلها تعمل فقط والشاشة ظاهرة */
object Net {
    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "RafiqNative/1")
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP " + conn.responseCode)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    // ---------------- الطقس (Open-Meteo بدون مفتاح) ----------------
    class Weather(val temp: Int, val desc: String, val city: String, val severe: Boolean)
    class Day(val max: Int, val min: Int, val desc: String)

    fun desc(code: Int): String = when (code) {
        0 -> "صافٍ"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        in 51..57 -> "رذاذ"
        in 61..67 -> "مطر"
        in 71..77 -> "ثلج"
        in 80..82 -> "زخات مطر"
        in 95..99 -> "عاصفة رعدية"
        else -> ""
    }
    private fun severe(code: Int) = code == 65 || code == 67 || code == 82 || code >= 95

    fun weather(lat: Double, lon: Double, city: String): Weather? {
        return try {
            val j = get(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon))
            val cur = j.getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m")).toInt()
            val code = cur.optInt("weather_code", -1)
            Weather(t, desc(code), city, severe(code))
        } catch (e: Exception) { null }
    }

    /** توقعات 3 أيام: طلب واحد عند الضغط على بطاقة الطقس فقط */
    fun forecast(lat: Double, lon: Double): List<Day> {
        return try {
            val j = get(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=3&timezone=auto", lat, lon))
            val d = j.getJSONObject("daily")
            val mx = d.getJSONArray("temperature_2m_max"); val mn = d.getJSONArray("temperature_2m_min"); val wc = d.getJSONArray("weather_code")
            List(mx.length()) { Day(Math.round(mx.getDouble(it)).toInt(), Math.round(mn.getDouble(it)).toInt(), desc(wc.optInt(it, -1))) }
        } catch (e: Exception) { emptyList() }
    }

    // ---------------- المباريات (بيانات ESPN العامة) ----------------
    class Match(val league: String, val home: String, val away: String, val hs: String, val aws: String,
                val state: String, val clock: String, val time: Long)

    class MatchCard(val main: Match?, val next: Match?, val all: List<Match>) {
        fun anyLive() = main?.state == "in"
    }

    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/"
    // الترتيب يحدد الأولوية: الدوري السعودي أولاً ثم الأوروبية
    private val LEAGUES = linkedMapOf(
        "ksa.1" to "الدوري السعودي", "uefa.champions" to "دوري أبطال أوروبا", "eng.1" to "الدوري الإنجليزي",
        "esp.1" to "الدوري الإسباني", "ita.1" to "الدوري الإيطالي", "ger.1" to "الدوري الألماني", "fra.1" to "الدوري الفرنسي"
    )

    private val AR = linkedMapOf(
        "hilal" to "الهلال", "nassr" to "النصر", "ittihad" to "الاتحاد", "ahli" to "الأهلي", "shabab" to "الشباب",
        "ettifaq" to "الاتفاق", "taawoun" to "التعاون", "fateh" to "الفتح", "fayha" to "الفيحاء", "khaleej" to "الخليج",
        "riyadh" to "الرياض", "okhdood" to "الأخدود", "akhdoud" to "الأخدود", "damac" to "ضمك", "qadsiah" to "القادسية",
        "qadisiya" to "القادسية", "kholood" to "الخلود", "orobah" to "العروبة", "wehda" to "الوحدة", "raed" to "الرائد",
        "hazem" to "الحزم", "hazm" to "الحزم", "najma" to "النجمة", "neom" to "نيوم",
        "real madrid" to "ريال مدريد", "atletico" to "أتلتيكو مدريد", "barcelona" to "برشلونة", "sevilla" to "إشبيلية",
        "manchester city" to "مانشستر سيتي", "manchester united" to "مانشستر يونايتد", "liverpool" to "ليفربول",
        "arsenal" to "آرسنال", "chelsea" to "تشيلسي", "tottenham" to "توتنهام", "newcastle" to "نيوكاسل",
        "bayern" to "بايرن ميونخ", "dortmund" to "دورتموند", "leverkusen" to "ليفركوزن",
        "paris saint" to "باريس سان جيرمان", "marseille" to "مارسيليا", "juventus" to "يوفنتوس", "inter" to "إنتر",
        "ac milan" to "ميلان", "napoli" to "نابولي", "roma" to "روما"
    )
    /** أسماء الفرق التي يمكن اختيارها مفضّلة */
    val TEAM_CHOICES: List<String> get() = AR.values.distinct()

    private fun ar(en: String): String {
        val l = en.lowercase(Locale.US)
        for ((k, v) in AR) if (l.contains(k)) return v
        return en
    }

    private fun parseDate(s: String): Long {
        for (f in arrayOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val df = SimpleDateFormat(f, Locale.US); df.timeZone = TimeZone.getTimeZone("UTC")
                val d = df.parse(s)
                if (d != null) return d.time
            } catch (e: Exception) {}
        }
        return 0L
    }

    fun matches(): MatchCard {
        val all = ArrayList<Match>()
        for ((code, name) in LEAGUES) {
            try {
                val evs = get(BASE + code + "/scoreboard").optJSONArray("events") ?: continue
                for (i in 0 until evs.length()) {
                    val e = evs.optJSONObject(i) ?: continue
                    val st = e.optJSONObject("status")
                    val state = st?.optJSONObject("type")?.optString("state") ?: "pre"
                    val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                    val cs = comp.optJSONArray("competitors") ?: continue
                    var h = ""; var a = ""; var hs = ""; var aws = ""
                    for (k in 0 until cs.length()) {
                        val t = cs.optJSONObject(k) ?: continue
                        val n = ar(t.optJSONObject("team")?.optString("displayName") ?: "")
                        if (t.optString("homeAway") == "home") { h = n; hs = t.optString("score") } else { a = n; aws = t.optString("score") }
                    }
                    all.add(Match(name, h, a, hs, aws, state, st?.optString("displayClock") ?: "", parseDate(e.optString("date"))))
                }
            } catch (e: Exception) {}
        }
        val now = System.currentTimeMillis()
        val live = all.firstOrNull { it.state == "in" }
        val upcoming = all.filter { it.state == "pre" && it.time > now - 600000 }.sortedBy { it.time }
        val recent = all.filter { it.state == "post" && now - it.time < 4 * 3600000L }.sortedByDescending { it.time }
        val main = live ?: recent.firstOrNull() ?: upcoming.firstOrNull()
        val next = upcoming.firstOrNull { it !== main }
        return MatchCard(main, next, all)
    }

    fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
}
