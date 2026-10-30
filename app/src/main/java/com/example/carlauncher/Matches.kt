package com.example.carlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * المباريات: مواعيد ونتائج فريقك المفضل من بيانات ESPN العامة (بدون مفتاح ولا تسجيل).
 * - المواعيد تُحدَّث كل 6 ساعات فقط.
 * - أثناء المباراة فقط: النتيجة كل دقيقة، مع التنبيهات (قبل البداية، البداية، الأهداف، النهاية).
 * كل شيء يعمل من الخدمة، فتصل التنبيهات والخرائط مفتوحة.
 */
object Matches {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/"
    private fun sp(c: Context) = c.getSharedPreferences("matches", Context.MODE_PRIVATE)
    @Volatile private var busy = false
    @Volatile private var teamsBusy = false

    /** أسماء الأندية بالعربي (الباقي يظهر كما هو) */
    private val AR = linkedMapOf(
        "hilal" to "الهلال", "nassr" to "النصر", "ittihad" to "الاتحاد", "ahli" to "الأهلي", "shabab" to "الشباب",
        "ettifaq" to "الاتفاق", "taawoun" to "التعاون", "fateh" to "الفتح", "fayha" to "الفيحاء", "khaleej" to "الخليج",
        "riyadh" to "الرياض", "okhdood" to "الأخدود", "akhdoud" to "الأخدود", "damac" to "ضمك", "qadsiah" to "القادسية", "qadisiya" to "القادسية",
        "kholood" to "الخلود", "orobah" to "العروبة", "wehda" to "الوحدة", "raed" to "الرائد", "hazem" to "الحزم", "hazm" to "الحزم",
        "najma" to "النجمة", "neom" to "نيوم", "taee" to "الطائي", "abha" to "أبها", "faisaly" to "الفيصلي", "batin" to "الباطن",
        "saudi arabia" to "السعودية", "qatar" to "قطر", "united arab emirates" to "الإمارات", "kuwait" to "الكويت", "bahrain" to "البحرين",
        "oman" to "عُمان", "iraq" to "العراق", "jordan" to "الأردن", "japan" to "اليابان", "south korea" to "كوريا الجنوبية",
        "australia" to "أستراليا", "iran" to "إيران", "uzbekistan" to "أوزبكستان", "china" to "الصين", "egypt" to "مصر", "morocco" to "المغرب",
        "al ain" to "العين", "sharjah" to "الشارقة", "al sadd" to "السد", "al duhail" to "الدحيل", "al gharafa" to "الغرافة", "al wasl" to "الوصل",
        "real madrid" to "ريال مدريد", "barcelona" to "برشلونة", "manchester city" to "مانشستر سيتي", "manchester united" to "مانشستر يونايتد",
        "liverpool" to "ليفربول", "arsenal" to "آرسنال", "chelsea" to "تشيلسي", "bayern" to "بايرن ميونخ", "paris saint" to "باريس سان جيرمان")
    fun arName(en: String): String {
        val l = en.lowercase()
        for ((k, v) in AR) if (l.contains(k)) return v
        return en
    }

    fun config(c: Context): JSONObject = try { JSONObject(sp(c).getString("cfg", "{}")!!) } catch (e: Exception) { JSONObject() }
    fun setConfig(c: Context, json: String) {
        try { JSONObject(json); sp(c).edit().putString("cfg", json).putLong("schedAt", 0L).apply() } catch (e: Exception) {}
    }
    fun state(c: Context): String = sp(c).getString("state", "{}") ?: "{}"
    fun teams(c: Context, league: String): String = sp(c).getString("teams_$league", "") ?: ""

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "RafiqAltariq/" + BuildConfig.VERSION_CODE)
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP " + conn.responseCode)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun online(c: Context) = try { StatusInfo.read(c).optBoolean("internet") } catch (e: Exception) { false }

    /** قائمة فرق بطولة لاختيار فريقك */
    fun fetchTeams(c: Context, league: String) {
        if (teamsBusy) return
        teamsBusy = true
        thread {
            try {
                val j = get(BASE + league + "/teams")
                val arr = j.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("teams") ?: JSONArray()
                val out = JSONArray()
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i)?.optJSONObject("team") ?: continue
                    val name = t.optString("displayName")
                    out.put(JSONObject().put("id", t.optString("id")).put("name", name).put("ar", arName(name))
                        .put("color", t.optString("color")).put("abbr", t.optString("abbreviation")))
                }
                sp(c).edit().putString("teams_$league", JSONObject().put("at", System.currentTimeMillis()).put("teams", out).toString()).apply()
            } catch (e: Exception) {
                sp(c).edit().putString("teams_$league", JSONObject().put("at", System.currentTimeMillis()).put("error", e.message ?: "تعذّر التحميل").toString()).apply()
            } finally { teamsBusy = false }
        }
    }

    private val isoFmts = listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private fun parseDate(s: String): Long {
        for (f in isoFmts) try { val d = SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s); if (d != null) return d.time } catch (e: Exception) {}
        return 0L
    }
    private fun ymd(t: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))

    /** يحوّل مباراة ESPN (من القائمة أو من التفاصيل) إلى شكل مختصر */
    private fun parseEvent(ev: JSONObject, comp: JSONObject, league: String): JSONObject? {
        val cs = comp.optJSONArray("competitors") ?: return null
        var home: JSONObject? = null; var away: JSONObject? = null
        for (i in 0 until cs.length()) {
            val x = cs.optJSONObject(i) ?: continue
            val t = x.optJSONObject("team") ?: continue
            val name = t.optString("displayName", t.optString("name"))
            val sc = x.opt("score")
            val score = when (sc) { is JSONObject -> sc.optString("displayValue", sc.optString("value")); null -> ""; else -> sc.toString() }
            val o = JSONObject().put("id", t.optString("id")).put("name", name).put("ar", arName(name))
                .put("abbr", t.optString("abbreviation")).put("color", t.optString("color")).put("score", score.substringBefore('.'))
            if (x.optString("homeAway") == "away") away = o else home = o
        }
        if (home == null || away == null) return null
        val st = (comp.optJSONObject("status") ?: ev.optJSONObject("status")) ?: JSONObject()
        val type = st.optJSONObject("type") ?: JSONObject()
        var tv = ""
        comp.optJSONArray("broadcasts")?.let { b -> if (b.length() > 0) tv = b.optJSONObject(0)?.optJSONArray("names")?.optString(0) ?: "" }
        return JSONObject().put("id", ev.optString("id", comp.optString("id"))).put("league", league)
            .put("date", parseDate(ev.optString("date", comp.optString("date"))))
            .put("state", type.optString("state", "pre")).put("clock", st.optString("displayClock"))
            .put("detail", type.optString("shortDetail")).put("home", home).put("away", away).put("tv", tv)
    }

    private fun mine(e: JSONObject, ids: Set<String>) = e.optJSONObject("home")?.optString("id") in ids || e.optJSONObject("away")?.optString("id") in ids

    /** المواعيد: من أمس حتى 3 أسابيع قادمة */
    private fun fetchSchedule(c: Context, cfg: JSONObject) {
        val ids = teamIds(cfg); val leagues = leagues(cfg)
        val now = System.currentTimeMillis()
        val range = ymd(now - 86_400_000L) + "-" + ymd(now + 21 * 86_400_000L)
        val out = ArrayList<JSONObject>(); var err = ""
        for (lg in leagues) {
            try {
                val j = get(BASE + lg + "/scoreboard?limit=300&dates=" + range)
                val evs = j.optJSONArray("events") ?: JSONArray()
                for (i in 0 until evs.length()) {
                    val ev = evs.optJSONObject(i) ?: continue
                    val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                    val e = parseEvent(ev, comp, lg) ?: continue
                    if (mine(e, ids)) out.add(e)
                }
            } catch (e: Exception) { err = e.message ?: "تعذّر التحميل" }
        }
        out.sortBy { it.optLong("date") }
        val old = try { JSONObject(state(c)) } catch (e: Exception) { JSONObject() }
        val arr = JSONArray(); out.forEach { arr.put(it) }
        // نحتفظ بالنتائج المباشرة الأحدث إن وُجدت
        val prev = old.optJSONArray("events") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            for (k in 0 until prev.length()) { val p = prev.getJSONObject(k); if (p.optString("id") == e.optString("id") && p.optString("state") == "in" && e.optString("state") == "pre") arr.put(i, p) }
        }
        val st = JSONObject().put("at", now).put("events", arr)
        if (out.isEmpty() && err.isNotEmpty()) st.put("error", err)
        sp(c).edit().putString("state", st.toString()).putLong("schedAt", now).apply()
    }

    private fun teamIds(cfg: JSONObject): Set<String> {
        val s = HashSet<String>(); val t = cfg.optJSONArray("teams") ?: return s
        for (i in 0 until t.length()) t.optJSONObject(i)?.optString("id")?.let { if (it.isNotEmpty()) s.add(it) }
        return s
    }
    private fun leagues(cfg: JSONObject): List<String> {
        val l = ArrayList<String>(); val a = cfg.optJSONArray("leagues")
        if (a != null) for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() && it.matches(Regex("[a-z0-9._]+")) }?.let { l.add(it) }
        if (l.isEmpty()) l.add("ksa.1")
        return l
    }

    /** تفاصيل مباراة جارية */
    private fun fetchLive(c: Context, cfg: JSONObject, e: JSONObject): JSONObject? {
        val j = get(BASE + e.optString("league") + "/summary?event=" + e.optString("id"))
        val comp = j.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val ev = JSONObject().put("id", e.optString("id")).put("date", comp.optString("date"))
        return parseEvent(ev, comp, e.optString("league"))?.put("tv", e.optString("tv"))
    }

    fun tick(c: Context) {
        val cfg = config(c)
        if (!cfg.optBoolean("on", false) || teamIds(cfg).isEmpty() || busy) return
        val now = System.currentTimeMillis()
        val s = sp(c)
        val st = try { JSONObject(state(c)) } catch (e: Exception) { JSONObject() }
        val evs = st.optJSONArray("events") ?: JSONArray()
        // تنبيه قبل المباراة (بدون إنترنت أيضاً)
        val pre = cfg.optInt("pre", 30)
        for (i in 0 until evs.length()) {
            val e = evs.getJSONObject(i); val d = e.optLong("date")
            if (pre > 0 && e.optString("state") == "pre" && now >= d - pre * 60_000L && now < d && !s.getBoolean("pre_" + e.optString("id"), false)) {
                s.edit().putBoolean("pre_" + e.optString("id"), true).apply()
                emit(c, cfg, "مباراة " + title(e) + " بعد " + Math.max(1, (d - now) / 60_000L) + " دقيقة", timeText(d) + (if (e.optString("tv").isNotEmpty()) " · " + e.optString("tv") else ""), false)
            }
        }
        val live = (0 until evs.length()).map { evs.getJSONObject(it) }.firstOrNull {
            val d = it.optLong("date"); it.optString("state") != "post" && now >= d - 5 * 60_000L && now <= d + 180 * 60_000L
        }
        val schedDue = now - s.getLong("schedAt", 0L) > 6 * 3600_000L
        val liveDue = live != null && now - s.getLong("liveAt", 0L) > 55_000L
        if (!schedDue && !liveDue) return
        if (!online(c)) return
        busy = true
        thread {
            try {
                if (schedDue) fetchSchedule(c, cfg)
                if (live != null && liveDue) {
                    s.edit().putLong("liveAt", now).apply()
                    val n = fetchLive(c, cfg, live)
                    if (n != null) { compare(c, cfg, live, n); replace(c, n) }
                }
            } catch (e: Exception) {
            } finally { busy = false }
        }
    }

    private fun replace(c: Context, n: JSONObject) {
        val st = try { JSONObject(state(c)) } catch (e: Exception) { JSONObject() }
        val evs = st.optJSONArray("events") ?: JSONArray()
        for (i in 0 until evs.length()) if (evs.getJSONObject(i).optString("id") == n.optString("id")) evs.put(i, n)
        sp(c).edit().putString("state", st.put("events", evs).put("liveAt", System.currentTimeMillis()).toString()).apply()
    }

    /** «للهلال» و«لنيوم» */
    private fun lam(n: String) = if (n.startsWith("ال")) "لل" + n.substring(2) else "لـ" + n
    private fun title(e: JSONObject) = e.optJSONObject("home")!!.optString("ar") + " و" + e.optJSONObject("away")!!.optString("ar")
    /** «الهلال 2، النصر 1» بدل «2 - 1» حتى لا تنعكس الأرقام في النص العربي */
    private fun score(e: JSONObject) = e.optJSONObject("home")!!.optString("ar") + " " + e.optJSONObject("home")!!.optString("score", "0").ifEmpty { "0" } +
        "، " + e.optJSONObject("away")!!.optString("ar") + " " + e.optJSONObject("away")!!.optString("score", "0").ifEmpty { "0" }
    private fun timeText(t: Long) = SimpleDateFormat("h:mm a", Locale("ar")).apply { timeZone = TimeZone.getTimeZone("Asia/Riyadh") }.format(Date(t))
        .replace("ص", "ص").replace("م", "م").let { it.map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.joinToString("") }

    /** نقارن الحالة الجديدة بالسابقة لنعرف: بدأت؟ هدف؟ انتهت؟ */
    private fun compare(c: Context, cfg: JSONObject, old: JSONObject, n: JSONObject) {
        val ids = teamIds(cfg); val s = sp(c); val id = n.optString("id")
        val os = old.optString("state"); val ns = n.optString("state")
        if (os == "pre" && ns == "in" && cfg.optBoolean("start", true) && !s.getBoolean("start_$id", false)) {
            s.edit().putBoolean("start_$id", true).apply()
            emit(c, cfg, "بدأت المباراة", score(n), false)
        }
        if (ns == "in" || ns == "post") {
            for (side in listOf("home", "away")) {
                val a = old.optJSONObject(side)?.optString("score")?.toIntOrNull() ?: 0
                val b = n.optJSONObject(side)?.optString("score")?.toIntOrNull() ?: 0
                val team = n.optJSONObject(side)!!
                val ours = team.optString("id") in ids
                if (b > a && cfg.optBoolean("goal", true) && (ours || cfg.optBoolean("goalAll", false))) {
                    val k = "goal_${id}_${side}_$b"
                    if (!s.getBoolean(k, false)) {
                        s.edit().putBoolean(k, true).apply()
                        val clock = n.optString("clock").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
                        emit(c, cfg, "هدف " + lam(team.optString("ar")) + clock, score(n), true)
                    }
                }
            }
        }
        if (os != "post" && ns == "post" && cfg.optBoolean("end", true) && !s.getBoolean("end_$id", false)) {
            s.edit().putBoolean("end_$id", true).apply()
            emit(c, cfg, "انتهت المباراة", score(n), false)
        }
    }

    private fun emit(c: Context, cfg: JSONObject, title: String, sub: String, goal: Boolean) {
        Events.add(c, "مباريات: $title — $sub")
        val svc = TripService.instance
        val sound = cfg.optBoolean("sound", goal)
        if (svc != null) svc.alertFrom("match", title, sub, "matches.html", sound) else Alerts.show(c, "match", title, sub, "matches.html")
        if (cfg.optBoolean("speak", false)) try { Voice.speak(c, "$title. $sub") } catch (e: Exception) {}
    }
}
