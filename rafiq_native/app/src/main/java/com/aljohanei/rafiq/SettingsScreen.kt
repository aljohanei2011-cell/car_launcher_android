package com.aljohanei.rafiq

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * شاشة الإعدادات الكاملة بألوان الواجهة: أقسام على اليمين وخياراتها على اليسار.
 * كل تغيير يُطبَّق فوراً. تُنشأ عند فتحها فقط وتُحذف عند إغلاقها.
 */
class SettingsScreen(private val a: MainActivity, start: Int = 0) {
    private val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
    private var pal = a.palNow()
    private var section = start
    private val sections = arrayOf("المظهر", "العداد", "البطاقات", "القائمة السفلية", "الموقع والمدينة", "الصلاة", "التنبيهات", "المباريات", "عام")
    private lateinit var content: LinearLayout
    private lateinit var side: LinearLayout
    private val T = android.R.style.Theme_DeviceDefault_Dialog_Alert

    fun show() { render(); d.show() }

    private var scroll: ScrollView? = null
    private var keep = false

    /** تطبيق فوري مع البقاء في نفس موضع التمرير */
    private fun changed() { a.applySettings(); pal = a.palNow(); keep = true; render() }

    private fun render() {
        val (root, design) = Ui.scaled(a, pal.bg)
        val frame = Ui.row(a, Gravity.TOP)
        design.addView(frame, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))

        // يسار: محتوى القسم
        val leftCol = Ui.col(a, Gravity.TOP or Gravity.RIGHT)
        val oldY = if (keep) scroll?.scrollY ?: 0 else 0
        keep = false
        val sv = ScrollView(a).apply { isVerticalScrollBarEnabled = true }
        scroll = sv
        if (oldY > 0) sv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                sv.removeOnLayoutChangeListener(this); sv.scrollTo(0, oldY)
            }
        })
        content = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply { setPadding(24, 8, 24, 24) }
        sv.addView(content, android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        leftCol.addView(sv, Ui.lin(Ui.MATCH, Ui.MATCH))
        frame.addView(leftCol, Ui.lin(0, Ui.MATCH, 1f))

        // يمين: الأقسام
        side = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply { setPadding(12, 16, 16, 16); setBackgroundColor(pal.card) }
        side.addView(Ui.tv(a, 26f, pal.accent, true).apply { text = "الإعدادات" }, Ui.lin(Ui.MATCH, 52))
        for (i in sections.indices) {
            val on = i == section
            side.addView(Ui.tv(a, 19f, if (on) pal.onAccent else pal.text, on).apply {
                text = sections[i]
                background = if (on) Ui.rounded(pal.accent, 12f) else Ui.rounded(pal.card, 12f)
                setPadding(16, 0, 16, 0)
                setOnClickListener { section = i; render() }
            }, Ui.lin(Ui.MATCH, 56).apply { topMargin = 6 })
        }
        side.addView(View(a), Ui.lin(Ui.MATCH, 0, 1f))
        side.addView(Ui.tv(a, 20f, pal.accent, true, Gravity.CENTER).apply {
            text = "إغلاق"; background = Ui.rounded(pal.bg, 12f, 2, pal.accent)
            setOnClickListener { d.dismiss() }
        }, Ui.lin(Ui.MATCH, 56))
        frame.addView(side, Ui.lin(300, Ui.MATCH))

        when (section) {
            0 -> looks()
            1 -> gaugeSec()
            2 -> cards()
            3 -> slots()
            4 -> city()
            5 -> prayer()
            6 -> alerts()
            7 -> matches()
            else -> general()
        }
        d.setContentView(root)
    }

    // ---------------- عناصر ----------------
    private fun title(s: String) = content.addView(Ui.tv(a, 22f, pal.text, true).apply { text = s }, Ui.lin(Ui.MATCH, 44).apply { topMargin = 14 })
    private fun note(s: String) = content.addView(Ui.tv(a, 16f, pal.muted).apply { text = s; maxLines = 3 }, Ui.lin(Ui.MATCH, Ui.WRAP).apply { topMargin = 4 })

    private fun btn(s: String, on: Boolean, onClick: () -> Unit): TextView = Ui.tv(a, 18f, if (on) pal.onAccent else pal.text, on, Gravity.CENTER).apply {
        text = s; setPadding(16, 0, 16, 0)
        background = if (on) Ui.rounded(pal.accent, 12f) else Ui.rounded(pal.card, 12f, 2, pal.line)
        setOnClickListener { onClick() }
    }

    /** صف: الاسم يميناً والأزرار يساره */
    private fun line(label: String, vararg views: View) {
        val r = Ui.row(a, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
        for (v in views) r.addView(v, Ui.lin(Ui.WRAP, 52).apply { leftMargin = 8 })
        r.addView(Ui.tv(a, 18f, pal.text).apply { text = label }, Ui.lin(0, 52, 1f).apply { leftMargin = 16 })
        content.addView(r, Ui.lin(Ui.MATCH, 60).apply { topMargin = 6 })
    }

    private fun seg(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val views = options.indices.map { i -> btn(options[i], i == selected) { onPick(i); changed() } }
        line(label, *views.toTypedArray())
    }

    private fun toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
        line(label, btn(if (on) "تشغيل" else "إيقاف", on) { onChange(!on); changed() })

    private fun action(label: String, b: String, onClick: () -> Unit) = line(label, btn(b, false, onClick))

    // ---------------- الأقسام ----------------
    private fun looks() {
        title("المظهر")
        val themes = listOf("auto", "dark", "light", "amber")
        seg("الثيم", listOf("تلقائي", "داكن", "فاتح", "كهرماني"), themes.indexOf(Prefs.theme)) { Prefs.theme = themes[it] }
        note("التلقائي: داكن بعد الغروب وفاتح بعد الشروق، محسوب محلياً.")
        toggle("خفض السطوع ليلاً", Prefs.nightDim) { Prefs.nightDim = it }
        val brands = listOf("COROLLA 2015", "TOYOTA", "Corolla", "كورولا 2015", "")
        seg("الشعار", listOf("COROLLA 2015", "TOYOTA", "Corolla", "كورولا 2015", "بدون"), brands.indexOf(Prefs.brand)) { Prefs.brand = brands[it] }
        seg("موضع الشعار", listOf("يسار", "يمين"), if (Prefs.brandLeft) 0 else 1) { Prefs.brandLeft = it == 0 }
        seg("حجم الخط", listOf("عادي", "كبير"), if (Prefs.bigFont) 1 else 0) { Prefs.bigFont = it == 1 }
        toggle("وضع الانتظار بعد التوقف 10 دقائق", Prefs.standby) { Prefs.standby = it }
        note("وضع الانتظار: شاشة داكنة فيها الساعة والصلاة القادمة، وتعود الواجهة عند اللمس أو الحركة.")
    }

    private fun gaugeSec() {
        title("العداد")
        seg("الشكل", listOf("قوس", "حلقة", "شريط"), Prefs.gauge) { Prefs.gauge = it }
        seg("الحد الأقصى", listOf("200", "240"), if (Prefs.maxSpeed == 200) 0 else 1) { Prefs.maxSpeed = if (it == 0) 200 else 240 }
        val alerts = listOf(0, 80, 100, 110, 120, 140)
        seg("تنبيه السرعة", alerts.map { if (it == 0) "إيقاف" else "$it" }, alerts.indexOf(Prefs.alertSpeed)) { Prefs.alertSpeed = alerts[it] }
        note("منطقة الخطر (آخر 20% من العداد) تظهر دائماً بلون خافت.")
    }

    private fun cards() {
        title("البطاقات")
        note("أظهر أو أخفِ أي بطاقة، ورتّبها داخل العمود. «نقل» يبدّلها مع البطاقة المقابلة في العمود الآخر.")
        val r = Prefs.column(true); val l = Prefs.column(false)
        val hidden = Prefs.hidden()
        for (right in listOf(true, false)) {
            title(if (right) "العمود الأيمن" else "العمود الأيسر")
            val col = if (right) r else l
            for (idx in col.indices) {
                val id = col[idx]
                val vis = id !in hidden
                line(Prefs.cardName(id),
                    btn("نقل ⇄", false) {
                        // تبديل مع البطاقة المقابلة في العمود الآخر، فيبقى في كل عمود 4 بطاقات ولا تنضغط
                        val other = if (right) l else r
                        if (idx < other.size) { val o = other[idx]; other[idx] = id; col[idx] = o }
                        else { col.removeAt(idx); other.add(id) }
                        Prefs.setColumns(r, l); changed()
                    },
                    btn("▼", false) {
                        if (idx < col.size - 1) { col[idx] = col[idx + 1]; col[idx + 1] = id; Prefs.setColumns(r, l); changed() }
                    },
                    btn("▲", false) {
                        if (idx > 0) { col[idx] = col[idx - 1]; col[idx - 1] = id; Prefs.setColumns(r, l); changed() }
                    },
                    btn(if (vis) "ظاهرة" else "مخفية", vis) {
                        val h = HashSet(Prefs.hidden()); if (vis) h.add(id) else h.remove(id); Prefs.setHidden(h); changed()
                    })
            }
        }
    }

    private fun slots() {
        title("القائمة السفلية")
        note("الخانة 1 في أقصى اليمين. يمكنك أيضاً الضغط مطولاً على القائمة نفسها لدخول وضع التعديل.")
        toggle("إظهار أسماء الخانات", Prefs.slotLabels) { Prefs.slotLabels = it }
        for (i in 0..5) {
            line("${i + 1}. ${a.slotTitle(i)}",
                btn("افتراضي", false) { Prefs.resetSlot(i); changed() },
                btn("←", false) { if (i < 5) { Prefs.swapSlots(i, i + 1); changed() } },
                btn("→", false) { if (i > 0) { Prefs.swapSlots(i, i - 1); changed() } },
                btn("تسمية", false) { rename(i) },
                btn("تغيير", true) { a.editSlot(i) { changed() } })
        }
    }

    private fun rename(i: Int) {
        val e = EditText(a).apply { setText(Prefs.slotLabel(i)); hint = a.slotTitle(i); setSingleLine() }
        AlertDialog.Builder(a, T).setTitle("اسم الخانة").setView(e)
            .setPositiveButton("حفظ") { _, _ -> Prefs.setSlotLabel(i, e.text.toString().trim()); changed() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun city() {
        title("الموقع والمدينة")
        seg("طريقة التحديد", listOf("تلقائي (GPS)", "يدوي"), if (Loc.manual()) 1 else 0) { Prefs.cityMode = if (it == 1) "manual" else "auto" }
        line("المدينة الحالية: ${Loc.name()}")
        action("المدينة المختارة: ${Prefs.city}", "اختيار مدينة") { pickCity() }
        note("تُستخدم المدينة نفسها للصلاة والطقس. في الوضع اليدوي، إذا ابتعدت أكثر من 50 كم يُقترح عليك التحويل.")
    }

    private fun pickCity() {
        val all = Cities.ALL.map { it.name }
        var shown = all
        val box = Ui.col(a, Gravity.TOP)
        val search = EditText(a).apply { hint = "بحث عن مدينة"; setSingleLine(); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) }
        val list = ListView(a)
        val ad = ArrayAdapter(a, android.R.layout.simple_list_item_1, ArrayList(all))
        list.adapter = ad
        box.addView(search, Ui.lin(Ui.MATCH, Ui.WRAP)); box.addView(list, Ui.lin(Ui.MATCH, 600))
        val dlg = AlertDialog.Builder(a, T).setTitle("اختيار مدينة").setView(box).setNegativeButton("إلغاء", null).create()
        list.setOnItemClickListener { _, _, p, _ ->
            Prefs.city = shown[p]; Prefs.cityMode = "manual"; dlg.dismiss(); a.forceWeather(); changed()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                shown = if (q.isEmpty()) all else all.filter { it.contains(q) }
                ad.clear(); ad.addAll(shown)
            }
        })
        dlg.show()
    }

    private fun prayer() {
        title("الصلاة")
        line("طريقة الحساب: أم القرى")
        val pre = listOf(0, 5, 10, 15)
        seg("التنبيه قبل الصلاة", pre.map { if (it == 0) "بدون" else "$it د" }, pre.indexOf(Prefs.preMin).coerceAtLeast(0)) {
            Prefs.preMin = pre[it]; Prefs.setAlert("pre", pre[it] > 0)
        }
        title("ضبط الدقائق ليطابق مسجد الحي")
        val t = Astro.prayerMillis(Calendar.getInstance(), Loc.lat(), Loc.lon())
        val f = SimpleDateFormat("HH:mm", Locale.US)
        for (i in 0..5) {
            val v = Prefs.adj(i)
            line("${Astro.PRAYER_NAMES[i]}  ${f.format(Date(t[i]))}",
                btn("−", false) { Prefs.setAdj(i, v - 1); changed() },
                Ui.tv(a, 18f, pal.text, true, Gravity.CENTER).apply { text = (if (v > 0) "+" else "") + v; minWidth = 56 },
                btn("+", false) { Prefs.setAdj(i, v + 1); changed() })
        }
        note("المدينة: ${Loc.name()}")
    }

    private fun alerts() {
        title("التنبيهات")
        note("تظهر كشريط أعلى الشاشة يختفي بعد 15 ثانية، ولا تظهر نوافذ أثناء الحركة.")
        for (k in Prefs.ALERTS.indices) {
            val key = Prefs.ALERTS[k]
            toggle(Prefs.ALERT_NAMES[k], Prefs.alertOn(key)) { Prefs.setAlert(key, it) }
        }
        note("تنبيهات المباريات وفرقك المفضلة في قسم «المباريات».")
    }

    private fun matches() {
        title("المباريات")
        action("آخر تحديث: " + a.matchesUpdatedText(), "تحديث الآن") { a.forceMatches(); a.toast("جارٍ تحديث المباريات…") }
        val fav = Prefs.favTeams()
        action("الفرق المفضلة: " + (if (fav.isEmpty()) "لم تُختر" else fav.joinToString("، ")), "اختيار") { pickTeams() }
        seg("العرض في البطاقة", listOf("كل المباريات", "فرقي فقط"), if (Prefs.matchMode == "fav") 1 else 0) {
            Prefs.matchMode = if (it == 1) "fav" else "all"; a.forceMatches()
        }
        note("الأولوية: مباراة فريقك المباشرة، ثم أي مباراة مباشرة، ثم مباراة فريقك القادمة.")
        title("البطولات")
        note("كل بطولة تلغيها تعني طلبات أقل على الإنترنت.")
        val on = Prefs.leagues()
        for (i in Prefs.LEAGUE_IDS.indices) {
            val id = Prefs.LEAGUE_IDS[i]
            toggle(Prefs.LEAGUE_NAMES[i], id in on) { v ->
                val s = HashSet(Prefs.leagues()); if (v) s.add(id) else s.remove(id); Prefs.setLeagues(s); a.forceMatches()
            }
        }
        title("تنبيهات فرقك المفضلة")
        toggle("تنبيهات المباريات", Prefs.alertOn("match")) { Prefs.setAlert("match", it) }
        for (i in Prefs.MATCH_ALERTS.indices) {
            val k = Prefs.MATCH_ALERTS[i]
            toggle(Prefs.MATCH_ALERT_NAMES[i], Prefs.bool(k, true)) { Prefs.putBool(k, it) }
        }
    }

    private fun pickTeams() {
        val names = Net.TEAM_CHOICES.toTypedArray()
        val fav = Prefs.favTeams()
        val checked = BooleanArray(names.size) { names[it] in fav }
        AlertDialog.Builder(a, T).setTitle("الفرق المفضلة")
            .setMultiChoiceItems(names, checked) { _, w, on -> checked[w] = on }
            .setPositiveButton("حفظ") { _, _ ->
                val s = HashSet<String>(); for (i in names.indices) if (checked[i]) s.add(names[i])
                Prefs.setFavTeams(s); changed()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun general() {
        title("عام")
        toggle("قفل التعديل أثناء القيادة", Prefs.lockDriving) { Prefs.lockDriving = it }
        action("عرض اسم المقطع في بطاقة الموسيقى", "إذن الإشعارات") { open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }
        action("اجعله الواجهة الرئيسية للشاشة", "فتح") { open(Settings.ACTION_HOME_SETTINGS) }
        action("كمبيوتر الرحلة", "تصفير") { Trip.reset(); changed() }
        toggle("تشغيل الموسيقى تلقائياً عند تشغيل الشاشة", Prefs.autoPlay) { Prefs.autoPlay = it }
        action("شاشة السرعة الكبيرة (مريحة ليلاً)", "تشغيل") { d.dismiss(); Panels.bigSpeed(a) }
        action("سجل الرحلات", "عرض") { Panels.tripLog(a) }
        title("النسخ الاحتياطي")
        action("حفظ كل الإعدادات في ملف", "نسخ احتياطي") { a.backup(true) }
        action("استرجاع الإعدادات من ملف", "استرجاع") { d.dismiss(); a.backup(false) }
        title("التحديث")
        val ver = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (e: Exception) { "" }
        line("الإصدار المثبّت: $ver")
        toggle("الفحص اليومي عن تحديث", Prefs.autoUpdate) { Prefs.autoUpdate = it }
        toggle("التحميل عبر الواي فاي فقط", Prefs.wifiOnly) { Prefs.wifiOnly = it }
        action("التحقق من وجود تحديث", "تحقق الآن") { a.checkUpdate(true) }
    }

    private fun open(action: String) {
        try { a.startActivity(Intent(action)) } catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) {}
        }
    }
}
