package com.aljohanei.rafiq

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** شاشات تُفتح بالضغط على البطاقات. تُنشأ عند الفتح وتُحذف عند الإغلاق */
object Panels {

    /** إطار شاشة كاملة بمقاس التصميم: عنوان وزر إغلاق، ومحتوى */
    private fun frame(a: MainActivity, pal: Palette, title: String, extra: Pair<String, () -> Unit>? = null,
                      onClose: () -> Unit = {}): Pair<Dialog, FrameLayout> {
        val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val (root, design) = Ui.scaled(a, pal.bg)
        val top = FrameLayout(a)
        design.addView(top, Ui.abs(1280, 72, 0, 0))
        top.addView(View(a).apply { setBackgroundColor(pal.accent) }, Ui.abs(1280, 2, 0, 70))
        top.addView(Ui.tv(a, 26f, pal.accent, true).apply { text = title }, Ui.at(Ui.WRAP, 72, Gravity.RIGHT).apply { rightMargin = 24 })
        top.addView(Ui.tv(a, 20f, pal.accent, true, Gravity.CENTER).apply {
            text = "إغلاق"; background = Ui.rounded(pal.card, 12f, 2, pal.accent); setPadding(28, 0, 28, 0)
            setOnClickListener { d.dismiss() }
        }, Ui.at(Ui.WRAP, 52, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = 24 })
        if (extra != null) top.addView(Ui.tv(a, 20f, pal.onAccent, true, Gravity.CENTER).apply {
            text = extra.first; background = Ui.rounded(pal.accent, 12f); setPadding(28, 0, 28, 0)
            setOnClickListener { d.dismiss(); extra.second() }
        }, Ui.at(Ui.WRAP, 52, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = 190 })
        val body = FrameLayout(a)
        design.addView(body, Ui.abs(1280, 648, 0, 72))
        d.setOnDismissListener { onClose() }
        d.setContentView(root)
        return Pair(d, body)
    }

    private fun box(a: Context, pal: Palette): LinearLayout = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply {
        background = Ui.cardBg(pal); setPadding(18, 14, 18, 14)
    }

    private fun t(a: Context, size: Float, color: Int, s: String, bold: Boolean = false, g: Int = Gravity.RIGHT): TextView =
        Ui.tv(a, size, color, bold, g).apply { text = s }

    // ============================ الطقس ============================
    private val COASTAL = setOf("ينبع", "جدة", "الدمام", "الخبر", "الظهران", "الجبيل", "القطيف", "رابغ", "الليث",
        "القنفذة", "جازان", "أملج", "الوجه", "ضباء")

    fun weather(a: MainActivity, pal: Palette) {
        val city = Loc.name()
        val (d, body) = frame(a, pal, "طقس $city", Pair("تحديث") { weather(a, pal) })
        val wait = t(a, 22f, pal.muted, "جارٍ التحميل…", false, Gravity.CENTER)
        body.addView(wait, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.CENTER))
        d.show()
        val lat = Loc.lat(); val lon = Loc.lon()
        val coastal = Cities.nearest(lat, lon).let { it.second < 30 && it.first.name in COASTAL }
        val h = Handler(Looper.getMainLooper())
        Thread {
            val f = Net.full(lat, lon, coastal)
            h.post {
                if (!d.isShowing) return@post
                if (f == null) { wait.text = "تعذّر التحميل. تحقق من الاتصال بالإنترنت"; return@post }
                body.removeAllViews()
                buildWeather(a, pal, body, f)
            }
        }.start()
    }

    private fun buildWeather(a: MainActivity, pal: Palette, body: FrameLayout, f: Net.Full) {
        // يمين: الآن
        val now = box(a, pal)
        now.addView(t(a, 18f, pal.muted, "الآن · آخر تحديث " + Ui.hm(System.currentTimeMillis())), Ui.lin(Ui.MATCH, 26))
        now.addView(t(a, 84f, pal.text, "${f.temp}°", true), Ui.lin(Ui.MATCH, 96))
        now.addView(t(a, 24f, pal.text, f.desc, true), Ui.lin(Ui.MATCH, 34))
        now.addView(t(a, 18f, pal.muted, "الإحساس الفعلي ${f.feels}°"), Ui.lin(Ui.MATCH, 30).apply { topMargin = 10 })
        now.addView(t(a, 18f, pal.muted, "الرطوبة ${f.humidity}%"), Ui.lin(Ui.MATCH, 30))
        now.addView(t(a, 18f, pal.muted, "الرياح ${f.wind} كم/س ${f.windDir}"), Ui.lin(Ui.MATCH, 30))
        now.addView(t(a, 18f, pal.muted, "الشروق ${Ui.hmStr(f.sunrise)} · الغروب ${Ui.hmStr(f.sunset)}"), Ui.lin(Ui.MATCH, 30))
        now.addView(t(a, 18f, pal.muted, "الأشعة فوق البنفسجية ${f.uv}"), Ui.lin(Ui.MATCH, 30))
        if (f.pm10 >= 0) now.addView(t(a, 18f, if (f.pm10 >= 150) pal.amber else pal.muted, "الغبار: ${Net.dustLevel(f.pm10)} (${f.pm10})"), Ui.lin(Ui.MATCH, 30))
        if (f.wave >= 0) now.addView(t(a, 18f, pal.accent, String.format(Locale.US, "البحر: ارتفاع الموج %.1f م", f.wave)), Ui.lin(Ui.MATCH, 30))
        body.addView(now, Ui.abs(380, 600, 876, 24))

        // وسط: الساعات القادمة
        val hrs = box(a, pal)
        hrs.addView(t(a, 18f, pal.muted, "الساعات القادمة"), Ui.lin(Ui.MATCH, 30))
        for (x in f.hours) {
            val r = Ui.row(a, Gravity.CENTER_VERTICAL)
            r.addView(t(a, 17f, pal.accent, if (x.rain > 0) "مطر ${x.rain}%" else "", false, Gravity.LEFT), Ui.lin(0, 40, 1f))
            r.addView(t(a, 22f, pal.text, "${x.temp}°", true, Gravity.CENTER), Ui.lin(80, 40))
            r.addView(t(a, 18f, pal.muted, Ui.hmStr(x.time), false, Gravity.RIGHT), Ui.lin(100, 40))
            hrs.addView(r, Ui.lin(Ui.MATCH, 44))
        }
        body.addView(hrs, Ui.abs(380, 600, 482, 24))

        // يسار: الأيام
        val days = box(a, pal)
        days.addView(t(a, 18f, pal.muted, "الأيام القادمة"), Ui.lin(Ui.MATCH, 30))
        for (x in f.days) {
            val r = Ui.col(a, Gravity.RIGHT)
            r.addView(t(a, 20f, pal.text, "${x.day}   ${x.max}° / ${x.min}°", true), Ui.lin(Ui.MATCH, 32))
            r.addView(t(a, 16f, pal.muted, x.desc + (if (x.rain > 0) " · احتمال المطر ${x.rain}%" else "")), Ui.lin(Ui.MATCH, 26))
            days.addView(r, Ui.lin(Ui.MATCH, Ui.WRAP).apply { topMargin = 14 })
        }
        body.addView(days, Ui.abs(434, 600, 24, 24))
    }

    // ============================ الصلاة ============================
    fun prayer(a: MainActivity, pal: Palette, openAdjust: () -> Unit) {
        val h = Handler(Looper.getMainLooper())
        var tick: Runnable? = null
        val (d, body) = frame(a, pal, "الصلاة · ${Loc.name()}", null) { tick?.let { h.removeCallbacks(it) } }
        val lat = Loc.lat(); val lon = Loc.lon()
        val nowMs = System.currentTimeMillis()
        val today = Calendar.getInstance()
        val pt = Astro.prayerMillis(today, lat, lon)
        val (nextIdx, nextAt) = Astro.nextPrayer(nowMs, lat, lon)
        val ramadan = Astro.hijri(nowMs)[1] == 8
        val friday = today.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

        // يمين: أوقات اليوم
        val list = box(a, pal)
        list.addView(t(a, 18f, pal.muted, "اليوم · ${Astro.hijriText(nowMs)}"), Ui.lin(Ui.MATCH, 30))
        val countdown = t(a, 20f, pal.accent, "", true)
        for (i in 0..5) {
            val isNext = i == nextIdx && nextAt == pt[i]
            val name = if (i == 2 && friday) "الجمعة (الخطبة)" else Astro.PRAYER_NAMES[i]
            val r = Ui.row(a, Gravity.CENTER_VERTICAL).apply {
                background = if (isNext) Ui.rounded(pal.accent, 12f) else null
                setPadding(14, 0, 14, 0)
            }
            r.addView(t(a, 24f, if (isNext) pal.onAccent else pal.text, Ui.hm(pt[i]), true, Gravity.LEFT), Ui.lin(0, 56, 1f))
            r.addView(t(a, 22f, if (isNext) pal.onAccent else pal.text, name, isNext), Ui.lin(Ui.WRAP, 56))
            list.addView(r, Ui.lin(Ui.MATCH, 58).apply { topMargin = 4 })
        }
        list.addView(countdown, Ui.lin(Ui.MATCH, 34).apply { topMargin = 10 })
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        list.addView(t(a, 18f, pal.muted, "فجر الغد " + Ui.hm(Astro.prayerMillis(tomorrow, lat, lon)[0])), Ui.lin(Ui.MATCH, 30))
        if (ramadan) list.addView(t(a, 18f, pal.amber, "الإمساك " + Ui.hm(pt[0] - 10 * 60000L)), Ui.lin(Ui.MATCH, 30))
        for (o in Astro.occasions(nowMs)) list.addView(t(a, 17f, pal.amber, o), Ui.lin(Ui.MATCH, 28))
        body.addView(list, Ui.abs(400, 600, 856, 24))

        // وسط: بوصلة القبلة والأزرار
        val mid = box(a, pal).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP }
        val q = Astro.qibla(lat, lon).toFloat()
        val compass = QiblaView(a, pal)
        mid.addView(t(a, 18f, pal.muted, "القبلة ${Math.round(q)}° من الشمال", false, Gravity.CENTER), Ui.lin(Ui.MATCH, 30))
        mid.addView(compass, Ui.lin(320, 320).apply { topMargin = 8 })
        val hint = t(a, 16f, pal.muted, "", false, Gravity.CENTER)
        mid.addView(hint, Ui.lin(Ui.MATCH, 26))
        mid.addView(t(a, 20f, pal.onAccent, "أقرب مسجد", true, Gravity.CENTER).apply {
            background = Ui.rounded(pal.accent, 12f)
            setOnClickListener {
                try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("مسجد"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { a.toast("لا يوجد تطبيق خرائط") }
            }
        }, Ui.lin(Ui.MATCH, 56).apply { topMargin = 12 })
        mid.addView(t(a, 20f, pal.accent, "ضبط الدقائق", true, Gravity.CENTER).apply {
            background = Ui.rounded(pal.card, 12f, 2, pal.accent)
            setOnClickListener { d.dismiss(); openAdjust() }
        }, Ui.lin(Ui.MATCH, 56).apply { topMargin = 10 })
        body.addView(mid, Ui.abs(400, 600, 440, 24))

        // يسار: جدول الأسبوع
        val week = box(a, pal)
        week.addView(t(a, 18f, pal.muted, "الأسبوع"), Ui.lin(Ui.MATCH, 30))
        val hdr = Ui.row(a, Gravity.CENTER_VERTICAL)
        for (n in arrayOf("العشاء", "المغرب", "العصر", "الظهر", "الفجر")) hdr.addView(t(a, 14f, pal.muted, n, false, Gravity.CENTER), Ui.lin(0, 28, 1f))
        hdr.addView(t(a, 14f, pal.muted, "اليوم", false, Gravity.CENTER), Ui.lin(70, 28))
        week.addView(hdr, Ui.lin(Ui.MATCH, 30))
        val dn = arrayOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
        for (k in 0..6) {
            val day = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, k) }
            val m = Astro.prayerMillis(day, lat, lon)
            val r = Ui.row(a, Gravity.CENTER_VERTICAL)
            for (i in intArrayOf(5, 4, 3, 2, 0)) r.addView(t(a, 14f, if (k == 0) pal.text else pal.muted, Ui.hm(m[i]), k == 0, Gravity.CENTER), Ui.lin(0, 40, 1f))
            r.addView(t(a, 16f, if (k == 0) pal.accent else pal.text, dn[day.get(Calendar.DAY_OF_WEEK) - 1], true, Gravity.CENTER), Ui.lin(70, 40))
            week.addView(r, Ui.lin(Ui.MATCH, 44).apply { topMargin = 6 })
        }
        body.addView(week, Ui.abs(392, 600, 24, 24))

        val r = object : Runnable {
            override fun run() {
                val n = System.currentTimeMillis()
                val mins = ((nextAt - n) / 60000L).coerceAtLeast(0)
                countdown.text = "صلاة ${Astro.PRAYER_NAMES[nextIdx]} بعد ${mins / 60} س ${mins % 60} د"
                val hd = Trip.bearing
                if (hd >= 0f && Trip.gpsLive()) { compass.set(q, hd); hint.text = "السهم بالنسبة لاتجاه السيارة" }
                else { compass.set(q, 0f); hint.text = "الأعلى = الشمال (عند التوقف)" }
                h.postDelayed(this, 1000)
            }
        }
        tick = r
        d.show()
        h.post(r)
    }

    /** بوصلة القبلة: حلقة، علامة الشمال، وسهم نحو الكعبة */
    class QiblaView(c: Context, private val pal: Palette) : View(c) {
        private var qibla = 0f
        private var heading = 0f
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 24f }
        private val path = Path()

        fun set(q: Float, h: Float) { if (q != qibla || h != heading) { qibla = q; heading = h; invalidate() } }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 10f
            ring.color = pal.line; canvas.drawCircle(cx, cy, r, ring)
            // الشمال يدور عكس اتجاه السيارة
            val nA = Math.toRadians((-heading - 90).toDouble())
            txt.color = pal.danger
            canvas.drawText("ش", cx + (r - 26) * cos(nA).toFloat(), cy + (r - 26) * sin(nA).toFloat() + 8f, txt)
            val qa = Math.toRadians((qibla - heading - 90).toDouble())
            val tipX = cx + (r - 40) * cos(qa).toFloat(); val tipY = cy + (r - 40) * sin(qa).toFloat()
            val side = Math.toRadians(90.0)
            path.reset()
            path.moveTo(tipX, tipY)
            path.lineTo(cx + 18 * cos(qa + side).toFloat(), cy + 18 * sin(qa + side).toFloat())
            path.lineTo(cx - 30 * cos(qa).toFloat(), cy - 30 * sin(qa).toFloat())
            path.lineTo(cx + 18 * cos(qa - side).toFloat(), cy + 18 * sin(qa - side).toFloat())
            path.close()
            fill.color = pal.amber; canvas.drawPath(path, fill)
            fill.color = pal.text; canvas.drawCircle(cx, cy, 8f, fill)
            // الكعبة عند رأس السهم
            fill.color = pal.text
            canvas.drawRect(tipX - 12f, tipY - 12f, tipX + 12f, tipY + 12f, fill)
            fill.color = pal.amber
            canvas.drawRect(tipX - 12f, tipY - 5f, tipX + 12f, tipY - 2f, fill)
        }
    }

    // ============================ المباريات ============================
    fun matchesDay(a: MainActivity, pal: Palette) {
        val (d, body) = frame(a, pal, "مباريات اليوم وغداً", Pair("تحديث") { a.forceMatches(); matchesDay(a, pal) })
        val wait = t(a, 22f, pal.muted, "جارٍ التحميل…", false, Gravity.CENTER)
        body.addView(wait, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.CENTER))
        d.show()
        val h = Handler(Looper.getMainLooper())
        val leagues = Prefs.leagues()
        Thread {
            val all = Net.fetchAll(leagues)
            h.post {
                if (!d.isShowing) return@post
                if (all == null) { wait.text = "لا يوجد اتصال بالإنترنت"; return@post }
                if (all.isEmpty()) { wait.text = "لا توجد مباريات اليوم أو غداً في البطولات المختارة"; return@post }
                val dayF = SimpleDateFormat("yyyyMMdd", Locale.US)
                val todayKey = dayF.format(Date())
                body.removeAllViews()
                val fav = Prefs.favTeams()
                val sv = ScrollView(a)
                val col = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply { setPadding(24, 16, 24, 24) }
                sv.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
                col.addView(t(a, 16f, pal.muted, "آخر تحديث " + Ui.hm(System.currentTimeMillis())), Ui.lin(Ui.MATCH, 26))
                for ((code, list) in all.groupBy { it.code }) {
                    val hr = Ui.row(a, Gravity.CENTER_VERTICAL)
                    hr.addView(t(a, 18f, pal.accent, "الترتيب", true, Gravity.CENTER).apply {
                        background = Ui.rounded(pal.card, 12f, 2, pal.accent); setPadding(20, 0, 20, 0)
                        setOnClickListener { standings(a, pal, code) }
                    }, Ui.lin(Ui.WRAP, 48))
                    hr.addView(t(a, 22f, pal.text, Net.leagueName(code), true), Ui.lin(0, 48, 1f))
                    col.addView(hr, Ui.lin(Ui.MATCH, 56).apply { topMargin = 14 })
                    for (m in list.sortedBy { it.time }) {
                        val isFav = m.home in fav || m.away in fav
                        val r = Ui.row(a, Gravity.CENTER_VERTICAL).apply {
                            background = Ui.rounded(pal.card, 12f, 2, if (isFav) pal.accent else pal.line); setPadding(18, 0, 18, 0)
                        }
                        val status = when (m.state) {
                            "in" -> "مباشر ${m.clock}"
                            "post" -> "انتهت"
                            else -> (if (dayF.format(Date(m.time)) == todayKey) "" else "غداً ") + Ui.hm(m.time)
                        }
                        r.addView(t(a, 18f, if (m.state == "in") pal.ok else pal.muted, status, m.state == "in", Gravity.LEFT), Ui.lin(160, 52))
                        val line = if (m.state == "pre") "${m.home} × ${m.away}" else "${m.home} ${m.hs} — ${m.aws} ${m.away}"
                        r.addView(t(a, 20f, pal.text, line, isFav), Ui.lin(0, 52, 1f))
                        col.addView(r, Ui.lin(Ui.MATCH, 56).apply { topMargin = 6 })
                    }
                }
                body.addView(sv, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
            }
        }.start()
    }

    private fun standings(a: MainActivity, pal: Palette, code: String) {
        val (d, body) = frame(a, pal, "ترتيب ${Net.leagueName(code)}")
        val wait = t(a, 22f, pal.muted, "جارٍ التحميل…", false, Gravity.CENTER)
        body.addView(wait, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.CENTER))
        d.show()
        val h = Handler(Looper.getMainLooper())
        Thread {
            val rows = Net.standings(code)
            h.post {
                if (!d.isShowing) return@post
                if (rows.isEmpty()) { wait.text = "الترتيب غير متاح لهذه البطولة"; return@post }
                body.removeAllViews()
                val fav = Prefs.favTeams()
                val sv = ScrollView(a)
                val col = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply { setPadding(140, 12, 140, 24) }
                sv.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
                val hr = Ui.row(a, Gravity.CENTER_VERTICAL)
                hr.addView(t(a, 16f, pal.muted, "النقاط", false, Gravity.CENTER), Ui.lin(110, 36))
                hr.addView(t(a, 16f, pal.muted, "لعب", false, Gravity.CENTER), Ui.lin(110, 36))
                hr.addView(t(a, 16f, pal.muted, "الفريق"), Ui.lin(0, 36, 1f))
                hr.addView(t(a, 16f, pal.muted, "#", false, Gravity.CENTER), Ui.lin(60, 36))
                col.addView(hr, Ui.lin(Ui.MATCH, 40))
                for (x in rows) {
                    val isFav = x.team in fav
                    val r = Ui.row(a, Gravity.CENTER_VERTICAL).apply {
                        background = Ui.rounded(pal.card, 10f, 2, if (isFav) pal.accent else pal.line)
                    }
                    r.addView(t(a, 20f, pal.text, "${x.points}", true, Gravity.CENTER), Ui.lin(110, 48))
                    r.addView(t(a, 18f, pal.muted, "${x.played}", false, Gravity.CENTER), Ui.lin(110, 48))
                    r.addView(t(a, 19f, pal.text, x.team, isFav), Ui.lin(0, 48, 1f))
                    r.addView(t(a, 18f, pal.accent, "${x.rank}", true, Gravity.CENTER), Ui.lin(60, 48))
                    col.addView(r, Ui.lin(Ui.MATCH, 52).apply { topMargin = 4 })
                }
                body.addView(sv, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
            }
        }.start()
    }

    // ============================ شاشة السرعة الكبيرة ============================
    /** شاشة سوداء فيها السرعة فقط بخط كبير جداً (مريحة ليلاً). لمسة تغلقها */
    fun bigSpeed(a: MainActivity) {
        val pal = Palette.DARK
        val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val (root, design) = Ui.scaled(a, 0xFF000000.toInt())
        val sp = Ui.tv(a, 360f / Ui.fontScale, pal.accent, true, Gravity.CENTER).apply { text = "0" }
        design.addView(sp, Ui.at(Ui.MATCH, 460, Gravity.CENTER))
        design.addView(t(a, 36f, pal.muted, "كم/س · المس الشاشة للرجوع", false, Gravity.CENTER),
            Ui.at(Ui.MATCH, 60, Gravity.BOTTOM).apply { bottomMargin = 50 })
        root.setOnClickListener { d.dismiss() }
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                val v = Math.round(Trip.kmh())
                Ui.setT(sp, v.toString())
                sp.setTextColor(if (Prefs.alertSpeed in 1 until v) pal.danger else pal.accent)
                h.postDelayed(this, 1000)
            }
        }
        d.setOnDismissListener { h.removeCallbacks(r) }
        d.setContentView(root)
        d.show(); h.post(r)
    }

    // ============================ سجل الرحلات ============================
    fun tripLog(a: MainActivity) {
        val rows = Prefs.str("tripLog").split(";").filter { it.isNotEmpty() }
        val f = SimpleDateFormat("EEE d/M", Locale("ar"))
        val items = rows.mapNotNull {
            val p = it.split("|")
            if (p.size < 5) null else try {
                val km = String.format(Locale.US, "%.1f", p[1].toDouble() / 1000.0)
                "${f.format(Date(p[0].toLong()))} ${Ui.hm(p[0].toLong())}\n$km كم · ${p[2].toLong() / 60000} د · متوسط ${p[3]} · أعلى ${p[4]}"
            } catch (e: Exception) { null }
        }
        android.app.AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("سجل الرحلات (آخر 20)")
            .setItems(if (items.isEmpty()) arrayOf("لا توجد رحلات بعد") else items.toTypedArray(), null)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("مسح السجل") { _, _ -> Prefs.putStr("tripLog", "") }
            .show()
    }
}
