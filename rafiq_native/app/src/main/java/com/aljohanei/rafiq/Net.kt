package com.aljohanei.rafiq

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** الطلبات الوحيدة على الإنترنت: الطقس والمباريات والتحديث، وكلها قصيرة وعند الحاجة فقط */
object Net {
    fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "RafiqNative/1")
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP " + conn.responseCode)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    // ============================ الطقس (Open-Meteo بدون مفتاح) ============================
    class Weather(val temp: Int, val desc: String, val city: String, val severe: Boolean, val pm10: Int)

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

    fun dustLevel(pm10: Int): String = when {
        pm10 < 0 -> ""
        pm10 < 50 -> "جيد"
        pm10 < 150 -> "متوسط"
        pm10 < 300 -> "غبار"
        else -> "غبار شديد"
    }

    private fun pm10(lat: Double, lon: Double): Int = try {
        val j = get(String.format(Locale.US,
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=pm10", lat, lon))
        Math.round(j.getJSONObject("current").getDouble("pm10")).toInt()
    } catch (e: Exception) { -1 }

    fun weather(lat: Double, lon: Double, city: String): Weather? {
        return try {
            val j = get(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon))
            val cur = j.getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m")).toInt()
            val code = cur.optInt("weather_code", -1)
            Weather(t, desc(code), city, severe(code), pm10(lat, lon))
        } catch (e: Exception) { null }
    }

    class Hour(val time: String, val temp: Int, val rain: Int)
    class Day(val day: String, val max: Int, val min: Int, val desc: String, val rain: Int)
    class Full(
        val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val windDir: String, val desc: String,
        val hours: List<Hour>, val days: List<Day>, val sunrise: String, val sunset: String, val uv: Int,
        val pm10: Int, val wave: Double
    )

    private fun dir(deg: Int): String {
        val n = arrayOf("شمالية", "شمالية شرقية", "شرقية", "جنوبية شرقية", "جنوبية", "جنوبية غربية", "غربية", "شمالية غربية")
        return n[((deg + 22) % 360) / 45]
    }

    /** شاشة الطقس: طلب واحد يجمع الآن والساعات والأيام، وطلب صغير للغبار، وللبحر في المدن الساحلية فقط */
    fun full(lat: Double, lon: Double, coastal: Boolean): Full? {
        return try {
            val j = get(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code" +
                    "&hourly=temperature_2m,precipitation_probability&forecast_hours=12" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,sunrise,sunset" +
                    "&forecast_days=5&timezone=auto", lat, lon))
            val c = j.getJSONObject("current")
            val hr = j.getJSONObject("hourly")
            val ht = hr.getJSONArray("time"); val htemp = hr.getJSONArray("temperature_2m"); val hrain = hr.optJSONArray("precipitation_probability")
            val hours = List(ht.length()) {
                Hour(ht.getString(it).takeLast(5), Math.round(htemp.getDouble(it)).toInt(), hrain?.optInt(it, 0) ?: 0)
            }
            val d = j.getJSONObject("daily")
            val dt = d.getJSONArray("time"); val mx = d.getJSONArray("temperature_2m_max"); val mn = d.getJSONArray("temperature_2m_min")
            val wc = d.getJSONArray("weather_code"); val pr = d.optJSONArray("precipitation_probability_max")
            val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
            val inF = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val days = List(dt.length()) {
                val cal = java.util.Calendar.getInstance()
                try { cal.time = inF.parse(dt.getString(it)) ?: Date() } catch (e: Exception) {}
                val name = when (it) { 0 -> "اليوم"; 1 -> "غداً"; else -> dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1] }
                Day(name, Math.round(mx.getDouble(it)).toInt(), Math.round(mn.getDouble(it)).toInt(), desc(wc.optInt(it, -1)), pr?.optInt(it, 0) ?: 0)
            }
            var wave = -1.0
            if (coastal) {
                try {
                    val m = get(String.format(Locale.US, "https://marine-api.open-meteo.com/v1/marine?latitude=%.3f&longitude=%.3f&current=wave_height", lat, lon))
                    wave = m.getJSONObject("current").optDouble("wave_height", -1.0)
                } catch (e: Exception) {}
            }
            Full(
                Math.round(c.getDouble("temperature_2m")).toInt(), Math.round(c.getDouble("apparent_temperature")).toInt(),
                c.optInt("relative_humidity_2m"), Math.round(c.optDouble("wind_speed_10m")).toInt(), dir(c.optInt("wind_direction_10m")),
                desc(c.optInt("weather_code", -1)), hours, days,
                d.getJSONArray("sunrise").getString(0).takeLast(5), d.getJSONArray("sunset").getString(0).takeLast(5),
                Math.round(d.getJSONArray("uv_index_max").optDouble(0, 0.0)).toInt(), pm10(lat, lon), if (wave.isNaN()) -1.0 else wave
            )
        } catch (e: Exception) { null }
    }

    // ============================ المباريات (بيانات ESPN العامة) ============================
    class Match(val code: String, val league: String, val home: String, val away: String, val hs: String, val aws: String,
                val state: String, val clock: String, val time: Long)

    class MatchCard(val main: Match?, val next: Match?, val all: List<Match>) {
        fun anyLive() = main?.state == "in"
    }

    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/"

    private val AR = linkedMapOf(
        "hilal" to "الهلال", "nassr" to "النصر", "ittihad" to "الاتحاد", "ahli" to "الأهلي", "shabab" to "الشباب",
        "ettifaq" to "الاتفاق", "taawoun" to "التعاون", "fateh" to "الفتح", "fayha" to "الفيحاء", "khaleej" to "الخليج",
        "riyadh" to "الرياض", "okhdood" to "الأخدود", "akhdoud" to "الأخدود", "damac" to "ضمك", "qadsiah" to "القادسية",
        "qadisiya" to "القادسية", "kholood" to "الخلود", "orobah" to "العروبة", "wehda" to "الوحدة", "raed" to "الرائد",
        "hazem" to "الحزم", "hazm" to "الحزم", "najma" to "النجمة", "neom" to "نيوم", "jeddah" to "جدة", "abha" to "أبها",
        "real madrid" to "ريال مدريد", "atletico" to "أتلتيكو مدريد", "barcelona" to "برشلونة", "sevilla" to "إشبيلية",
        "villarreal" to "فياريال", "real sociedad" to "ريال سوسيداد", "athletic" to "أتلتيك بلباو", "girona" to "جيرونا",
        "manchester city" to "مانشستر سيتي", "manchester united" to "مانشستر يونايتد", "liverpool" to "ليفربول",
        "arsenal" to "آرسنال", "chelsea" to "تشيلسي", "tottenham" to "توتنهام", "newcastle" to "نيوكاسل",
        "aston villa" to "أستون فيلا", "west ham" to "وست هام", "everton" to "إيفرتون",
        "bayern" to "بايرن ميونخ", "dortmund" to "دورتموند", "leverkusen" to "ليفركوزن", "leipzig" to "لايبزيغ",
        "paris saint" to "باريس سان جيرمان", "marseille" to "مارسيليا", "monaco" to "موناكو", "lyon" to "ليون", "lille" to "ليل",
        "juventus" to "يوفنتوس", "inter" to "إنتر", "ac milan" to "ميلان", "napoli" to "نابولي", "roma" to "روما",
        "lazio" to "لاتسيو", "atalanta" to "أتالانتا", "benfica" to "بنفيكا", "porto" to "بورتو", "sporting" to "سبورتنغ",
        "ajax" to "أياكس", "psv" to "آيندهوفن", "celtic" to "سيلتك", "galatasaray" to "غلطة سراي"
    )

    /** أسماء الفرق التي يمكن اختيارها مفضّلة */
    val TEAM_CHOICES: List<String> get() = AR.values.distinct()

    fun ar(en: String): String {
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

    fun leagueName(code: String): String = Prefs.LEAGUE_NAMES.getOrElse(Prefs.LEAGUE_IDS.indexOf(code)) { code }

    /**
     * مباريات اليوم وغداً للبطولات المطلوبة (الترتيب يحدد الأولوية: السعودي أولاً).
     * تعيد null إن فشلت كل الطلبات (لا يوجد اتصال) حتى لا تُمسح النتائج المعروضة.
     */
    fun fetchAll(leagues: Collection<String>, withTomorrow: Boolean = true): List<Match>? {
        val all = ArrayList<Match>()
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val t = System.currentTimeMillis()
        // المصدر يرفض نطاق التواريخ، فنطلب كل يوم وحده (اليوم، وغداً إن طُلب)
        val days = if (withTomorrow) listOf(f.format(Date(t)), f.format(Date(t + 86400000L))) else listOf(f.format(Date(t)))
        var ok = 0; var tried = 0
        val seen = HashSet<String>()
        for (code in Prefs.LEAGUE_IDS) {
            if (code !in leagues) continue
            for (day in days) {
            tried++
            try {
                val j = get(BASE + code + "/scoreboard?limit=100&dates=" + day)
                ok++
                val evs = j.optJSONArray("events") ?: continue
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
                    if (!seen.add(e.optString("id", h + a + e.optString("date")))) continue
                    all.add(Match(code, leagueName(code), h, a, hs, aws, state, st?.optString("displayClock") ?: "", parseDate(e.optString("date"))))
                }
            } catch (e: Exception) {}
            }
        }
        return if (tried > 0 && ok == 0) null else all
    }

    /** البطاقة: مباراة فريقك المباشرة، ثم أي مباراة مباشرة، ثم مباراة فريقك القادمة */
    /**
     * only: أثناء المباريات المباشرة نحدّث بطولاتها فقط (بيانات أقل)، ونُبقي بقية المباريات من prev.
     * تعيد null عند انقطاع الاتصال.
     */
    fun matches(only: Set<String>? = null, prev: List<Match>? = null): MatchCard? {
        val fav = Prefs.favTeams()
        val favOnly = Prefs.matchMode == "fav"
        val all: List<Match> = if (only != null && prev != null) {
            val part = fetchAll(only, false) ?: return null
            (prev.filter { it.code !in only } + part).sortedBy { Prefs.LEAGUE_IDS.indexOf(it.code) }
        } else fetchAll(Prefs.leagues()) ?: return null
        fun isFav(m: Match) = m.home in fav || m.away in fav
        val pool = if (favOnly) all.filter { isFav(it) } else all
        val now = System.currentTimeMillis()
        val upcoming = pool.filter { it.state == "pre" && it.time > now - 600000 }.sortedBy { it.time }
        val recent = pool.filter { it.state == "post" && now - it.time < 4 * 3600000L }.sortedByDescending { it.time }
        val main = pool.firstOrNull { it.state == "in" && isFav(it) }
            ?: pool.firstOrNull { it.state == "in" }
            ?: upcoming.firstOrNull { isFav(it) }
            ?: recent.firstOrNull { isFav(it) }
            ?: recent.firstOrNull() ?: upcoming.firstOrNull()
        val next = upcoming.firstOrNull { it !== main }
        return MatchCard(main, next, all)
    }

    class Row(val rank: Int, val team: String, val played: Int, val points: Int)

    /** ترتيب الدوري: طلب واحد عند الطلب فقط */
    fun standings(code: String): List<Row> {
        return try {
            val j = get("https://site.api.espn.com/apis/v2/sports/soccer/$code/standings")
            val ch = j.optJSONArray("children")?.optJSONObject(0) ?: j
            val en = ch.optJSONObject("standings")?.optJSONArray("entries") ?: return emptyList()
            val out = ArrayList<Row>()
            for (i in 0 until en.length()) {
                val e = en.optJSONObject(i) ?: continue
                var pts = 0; var gp = 0; var rank = i + 1
                val st = e.optJSONArray("stats")
                if (st != null) for (k in 0 until st.length()) {
                    val s = st.optJSONObject(k) ?: continue
                    when (s.optString("name")) {
                        "points" -> pts = s.optInt("value")
                        "gamesPlayed" -> gp = s.optInt("value")
                        "rank" -> rank = s.optInt("value", rank)
                    }
                }
                out.add(Row(rank, ar(e.optJSONObject("team")?.optString("displayName") ?: ""), gp, pts))
            }
            out.sortedBy { it.rank }
        } catch (e: Exception) { emptyList() }
    }

    fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))
}
