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
    /** سبب آخر فشل بلغة مفهومة (يظهر في البطاقات واختبار الاتصال) */
    @Volatile var lastError = ""

    fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "لا يوجد اتصال بالإنترنت"
        is java.net.SocketTimeoutException -> "الاتصال بطيء (انتهت المهلة)"
        is java.net.ConnectException -> "تعذّر الوصول للخادم"
        is javax.net.ssl.SSLException, is java.security.cert.CertificateException -> "خطأ في شهادة الأمان (تأكد من تاريخ ووقت الشاشة)"
        else -> e.message?.takeIf { it.startsWith("HTTP") }?.let { "رد الخادم: $it" } ?: "خطأ في الاتصال"
    }

    fun get(url: String): JSONObject {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "RafiqNative/1")
            try {
                if (conn.responseCode != 200) throw java.io.IOException("HTTP " + conn.responseCode)
                return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            lastError = describe(e)
            throw e
        }
    }

    /** هل لدى الشاشة شبكة متصلة بالإنترنت الآن؟ */
    fun online(c: android.content.Context): Boolean = try {
        val cm = c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = cm.activeNetwork
        n != null && cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) { true }

    /** اختبار الاتصال من الإعدادات: يعيد سطراً لكل خدمة */
    fun test(c: android.content.Context): String {
        val sb = StringBuilder()
        sb.append(if (online(c)) "الشبكة: متصلة\n" else "الشبكة: غير متصلة (فعّل الواي فاي أو نقطة اتصال الجوال)\n")
        for ((name, url) in listOf(
            "الطقس" to "https://api.open-meteo.com/v1/forecast?latitude=24.09&longitude=38.06&current=temperature_2m",
            "المباريات" to "https://site.api.espn.com/apis/site/v2/sports/soccer/ksa.1/scoreboard",
            "التحديثات" to "https://api.github.com/repos/aljohanei2011-cell/car_launcher_android/releases/tags/rafiq-native")) {
            val t0 = System.currentTimeMillis()
            val r = try { get(url); "يعمل (${System.currentTimeMillis() - t0} ملي ثانية)" } catch (e: Exception) { describe(e) }
            sb.append("$name: $r\n")
        }
        return sb.toString().trim()
    }

    // ============================ الطقس (Open-Meteo بدون مفتاح) ============================
    class Weather(val temp: Int, val desc: String, val city: String, val severe: Boolean, val pm10: Int,
                  val max: Int = -999, val min: Int = -999, val code: Int = -1)

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

    /** توفير البيانات: مفعّل يدوياً، أو تلقائياً عند اتصال محدود (نقطة اتصال الجوال) */
    fun saver(c: android.content.Context): Boolean = when (Prefs.dataSaver) {
        "on" -> true
        "off" -> false
        else -> try { (c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered } catch (e: Exception) { false }
    }

    /** الغبار يتغير ببطء: نطلبه كل ساعتين فقط */
    private fun pm10Cached(lat: Double, lon: Double): Int {
        if (System.currentTimeMillis() - Prefs.long("wxPmAt") < 2 * 3600000L) return Prefs.int("wxPm", -1)
        val v = pm10(lat, lon)
        if (v >= 0) { Prefs.putInt("wxPm", v); Prefs.putLong("wxPmAt", System.currentTimeMillis()) }
        return v
    }

    /**
     * طقس البطاقة: طلب واحد كامل يُحفظ، فتفتح شاشة التفاصيل منه بدون طلب ثانٍ.
     * إن تعطّل المصدر الأول يُجرَّب مصدر احتياطي (met.no) للحرارة والحالة.
     */
    fun weather(lat: Double, lon: Double, city: String): Weather? {
        try {
            val j = fetchFull(lat, lon)
            Prefs.putStr("wxFullJson", j.toString()); Prefs.putStr("wxFullKey", String.format(Locale.US, "%.2f,%.2f", lat, lon))
            Prefs.putLong("wxFullAt", System.currentTimeMillis())
            val cur = j.getJSONObject("current")
            val code = cur.optInt("weather_code", -1)
            val dl = j.optJSONObject("daily")
            val mx = dl?.optJSONArray("temperature_2m_max")?.optDouble(0, Double.NaN) ?: Double.NaN
            val mn = dl?.optJSONArray("temperature_2m_min")?.optDouble(0, Double.NaN) ?: Double.NaN
            return Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), desc(code), city, severe(code), pm10Cached(lat, lon),
                if (mx.isNaN()) -999 else Math.round(mx).toInt(), if (mn.isNaN()) -999 else Math.round(mn).toInt(), code)
        } catch (e: Exception) {}
        val firstError = lastError
        return try {
            val j = get(String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f", lat, lon))
            val d = j.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
            val t = Math.round(d.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")).toInt()
            val sym = d.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
            val ds = when {
                sym.startsWith("clearsky") || sym.startsWith("fair") -> "صافٍ"
                sym.startsWith("partlycloudy") -> "غائم جزئياً"
                sym.startsWith("cloudy") -> "غائم"
                sym.startsWith("fog") -> "ضباب"
                sym.contains("thunder") -> "عاصفة رعدية"
                sym.contains("rain") || sym.contains("sleet") -> "مطر"
                sym.contains("snow") -> "ثلج"
                else -> ""
            }
            Weather(t, ds, city, sym.contains("thunder") || sym.startsWith("heavyrain"), Prefs.int("wxPm", -1))
        } catch (e: Exception) { lastError = firstError; null }
    }

    class Hour(val time: String, val temp: Int, val rain: Int)
    class Day(val day: String, val max: Int, val min: Int, val desc: String, val rain: Int)
    class Full(
        val cachedAt: Long,
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
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        var cachedAt = 0L
        return try {
            // محفوظ حديثاً من طلب البطاقة (أقل من 30 دقيقة): نعرضه بدون طلب جديد
            val fresh = Prefs.str("wxFullKey") == key && System.currentTimeMillis() - Prefs.long("wxFullAt") < 30 * 60000L
            // بدون اتصال: آخر توقعات محفوظة لنفس المكان (حتى 24 ساعة)
            val j = if (fresh) JSONObject(Prefs.str("wxFullJson")) else try {
                fetchFull(lat, lon).also {
                    Prefs.putStr("wxFullJson", it.toString()); Prefs.putStr("wxFullKey", key); Prefs.putLong("wxFullAt", System.currentTimeMillis())
                }
            } catch (e: Exception) {
                val at = Prefs.long("wxFullAt")
                if (Prefs.str("wxFullKey") != key || System.currentTimeMillis() - at > 24 * 3600000L) return null
                cachedAt = at
                JSONObject(Prefs.str("wxFullJson"))
            }
            parseFull(j, if (cachedAt == 0L) pm10Cached(lat, lon) else Prefs.int("wxPm", -1), if (cachedAt == 0L && coastal) wave(lat, lon) else -1.0, cachedAt)
        } catch (e: Exception) { null }
    }

    private fun wave(lat: Double, lon: Double): Double = try {
        val m = get(String.format(Locale.US, "https://marine-api.open-meteo.com/v1/marine?latitude=%.3f&longitude=%.3f&current=wave_height", lat, lon))
        m.getJSONObject("current").optDouble("wave_height", -1.0).let { if (it.isNaN()) -1.0 else it }
    } catch (e: Exception) { -1.0 }

    private fun fetchFull(lat: Double, lon: Double): JSONObject = get(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code" +
                    "&hourly=temperature_2m,precipitation_probability&forecast_hours=12" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,sunrise,sunset" +
                    "&forecast_days=5&timezone=auto", lat, lon))

    private fun parseFull(j: JSONObject, pm: Int, wv: Double, cachedAt: Long): Full = run {
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
            Full(
                cachedAt,
                Math.round(c.getDouble("temperature_2m")).toInt(), Math.round(c.getDouble("apparent_temperature")).toInt(),
                c.optInt("relative_humidity_2m"), Math.round(c.optDouble("wind_speed_10m")).toInt(), dir(c.optInt("wind_direction_10m")),
                desc(c.optInt("weather_code", -1)), hours, days,
                d.getJSONArray("sunrise").getString(0).takeLast(5), d.getJSONArray("sunset").getString(0).takeLast(5),
                Math.round(d.getJSONArray("uv_index_max").optDouble(0, 0.0)).toInt(), pm, wv
            )
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
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val t = System.currentTimeMillis()
        // المصدر يرفض نطاق التواريخ، فنطلب كل يوم وحده (اليوم، وغداً إن طُلب)
        val days = if (withTomorrow) listOf(f.format(Date(t)), f.format(Date(t + 86400000L))) else listOf(f.format(Date(t)))
        val jobs = ArrayList<Pair<String, String>>()
        for (code in Prefs.LEAGUE_IDS) if (code in leagues) for (day in days) jobs.add(Pair(code, day))
        if (jobs.isEmpty()) return emptyList()
        // 3 طلبات معاً بدل واحد بعد الآخر: أسرع بنحو 3 مرات، وتنتهي الخيوط فور الانتهاء
        val results = arrayOfNulls<JSONObject>(jobs.size)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(3, jobs.size))
        try {
            val futures = jobs.mapIndexed { idx, (code, day) ->
                pool.submit(Runnable {
                    val r = try { get(BASE + code + "/scoreboard?limit=100&dates=" + day) } catch (e: Exception) { null }
                    results[idx] = r
                })
            }
            for (fu in futures) try { fu.get(40, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) {}
        } finally { pool.shutdownNow() }
        if (results.all { it == null }) return null
        val all = ArrayList<Match>()
        val seen = HashSet<String>()
        for (idx in jobs.indices) {
            val code = jobs[idx].first
            val evs = results[idx]?.optJSONArray("events") ?: continue
            for (i in 0 until evs.length()) {
                val e = evs.optJSONObject(i) ?: continue
                val st = e.optJSONObject("status")
                val state = st?.optJSONObject("type")?.optString("state") ?: "pre"
                val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val cs = comp.optJSONArray("competitors") ?: continue
                var h = ""; var a = ""; var hs = ""; var aws = ""
                for (k in 0 until cs.length()) {
                    val tm = cs.optJSONObject(k) ?: continue
                    val n = ar(tm.optJSONObject("team")?.optString("displayName") ?: "")
                    if (tm.optString("homeAway") == "home") { h = n; hs = tm.optString("score") } else { a = n; aws = tm.optString("score") }
                }
                if (!seen.add(e.optString("id", h + a + e.optString("date")))) continue
                all.add(Match(code, leagueName(code), h, a, hs, aws, state, st?.optString("displayClock") ?: "", parseDate(e.optString("date"))))
            }
        }
        return all
    }

    /** البطاقة: مباراة فريقك المباشرة، ثم أي مباراة مباشرة، ثم مباراة فريقك القادمة */
    /**
     * only: أثناء المباريات المباشرة نحدّث بطولاتها فقط (بيانات أقل)، ونُبقي بقية المباريات من prev.
     * تعيد null عند انقطاع الاتصال.
     */
    fun matches(only: Set<String>? = null, prev: List<Match>? = null, withTomorrow: Boolean = true): MatchCard? {
        val fav = Prefs.favTeams()
        val favOnly = Prefs.matchMode == "fav"
        val all: List<Match> = if (only != null && prev != null) {
            val part = fetchAll(only, false) ?: return null
            (prev.filter { it.code !in only } + part).sortedBy { Prefs.LEAGUE_IDS.indexOf(it.code) }
        } else {
            val got = fetchAll(Prefs.leagues(), withTomorrow) ?: return null
            if (withTomorrow || prev == null) got else {
                // مباريات الغد تُطلب كل 3 ساعات فقط: نُبقي المعروف منها
                val cal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                }
                val keys = got.map { it.home + "|" + it.away + "|" + it.time }.toSet()
                got + prev.filter { it.time >= cal.timeInMillis && (it.home + "|" + it.away + "|" + it.time) !in keys && it.code in Prefs.leagues() }
            }
        }
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

    fun hhmm(t: Long): String = Ui.hm(t)
}
