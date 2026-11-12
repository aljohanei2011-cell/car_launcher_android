package com.aljohanei.rafiq

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * الإعدادات: الصفحة الأولى فيها مفاتيح سريعة و8 بلاطات للأقسام، مع بحث.
 * كل تغيير يُطبَّق فوراً. تُنشأ عند الفتح وتُحذف عند الإغلاق.
 */
class SettingsScreen(private val a: MainActivity, start: Int = -1) {
    private val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
    private var pal = a.palNow()
    private var section = start          // -1 = الصفحة الأولى
    private var query = ""
    private lateinit var content: LinearLayout
    private var scroll: ScrollView? = null
    private var keep = false
    private val T = android.R.style.Theme_DeviceDefault_Dialog_Alert

    private val NAMES = listOf("المظهر", "العداد", "الشاشة الرئيسية", "الصلاة والمدينة", "التنبيهات", "المباريات", "القيادة والأمان", "عام وحول")
    private val DESC = listOf("الثيم، الألوان، الساعة، الخط", "الشكل، الحد، التنبيه", "البطاقات والقائمة السفلية",
        "المدينة، الضبط، التنبيه", "الأذان، السرعة، الأدعية", "الفرق والبطولات", "القفل، الانتظار، الاستراحة", "التحديث، النسخ، الذاكرة")
    private val ICONS = intArrayOf(R.drawable.g_palette, R.drawable.g_gauge, R.drawable.g_layout, R.drawable.g_moon,
        R.drawable.g_bell, R.drawable.g_star, R.drawable.g_wheel, R.drawable.g_info)

    /** فهرس البحث: كلمات كل قسم */
    private val INDEX = listOf(
        0 to "الثيم الخلفية لون التمييز النهار الليل داكن أسود فاتح الساعة 12 24 حجم الخط الشعار تويوتا كورولا السطوع",
        1 to "العداد الشكل الإبرة رقمي نصف دائرة مقطع البوصلة الحد الأقصى تنبيه السرعة صورة السيارة أعلى سرعة صفارة",
        2 to "البطاقات ترتيب إخفاء القائمة السفلية الخانات الحجم الأسماء وجهاتي المنزل العمل المسجد الاتصال السريع الاختصارات",
        3 to "الصلاة المدينة ينبع القبلة الأذان ضبط الدقائق قبل الصلاة أم القرى الموقع",
        4 to "التنبيهات الأذان السرعة الاستراحة الأدعية الجمعة الصيام الغبار الحرارة الطقس الصوت",
        5 to "المباريات الفرق المفضلة البطولات الدوري الأهداف تحديث",
        6 to "القيادة القفل وضع الانتظار إبقاء الشاشة الاستراحة السرعة الكبيرة الرقم السري سجل الرحلات تصفير",
        7 to "التحديث الإصدار النسخ الاحتياطي استرجاع الذاكرة الواجهة الرئيسية الإشعارات التشغيل التلقائي استعادة الافتراضي"
    )

    fun show() { render(); d.show() }

    /** تطبيق فوري مع البقاء في نفس موضع التمرير */
    private fun changed() { a.applySettings(); pal = a.palNow(); keep = true; render() }

    private fun render() {
        val (root, design) = Ui.scaled(a, pal.bg)
        // الشريط العلوي
        val top = FrameLayout(a)
        design.addView(top, Ui.abs(1280, 72, 0, 0))
        top.addView(View(a).apply { setBackgroundColor(pal.accent) }, Ui.abs(1280, 2, 0, 70))
        val titleRow = Ui.row(a, Gravity.CENTER_VERTICAL)
        if (section >= 0 || query.isNotEmpty()) {
            titleRow.addView(Ui.tv(a, 22f, pal.accent, true, Gravity.CENTER).apply {
                text = "رجوع"; setPadding(18, 0, 18, 0)
                background = Ui.pressable(Ui.rounded(pal.card, 12f, 2, pal.accent), Ui.rounded(pal.line, 12f))
                setOnClickListener { section = -1; query = ""; render() }
            }, Ui.lin(Ui.WRAP, 52).apply { rightMargin = 16 })
        }
        titleRow.addView(Ui.tv(a, 26f, pal.accent, true).apply {
            text = when { query.isNotEmpty() -> "نتائج البحث"; section >= 0 -> NAMES[section]; else -> "الإعدادات" }
        }, Ui.lin(Ui.WRAP, 72))
        top.addView(titleRow, Ui.at(Ui.WRAP, 72, Gravity.RIGHT).apply { rightMargin = 24 })
        val search = EditText(a).apply {
            hint = "ابحث عن إعداد… مثل «الساعة» أو «القبلة»"; setSingleLine(); setText(query)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f); setTextColor(pal.text); setHintTextColor(pal.muted)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL; setPadding(18, 0, 18, 0)
            background = Ui.rounded(pal.card, 12f, 2, pal.line)
        }
        top.addView(search, Ui.at(440, 50, Gravity.CENTER))
        top.addView(Ui.tv(a, 20f, pal.accent, true, Gravity.CENTER).apply {
            text = "إغلاق"; setPadding(28, 0, 28, 0)
            background = Ui.pressable(Ui.rounded(pal.card, 12f, 2, pal.accent), Ui.rounded(pal.line, 12f))
            setOnClickListener { d.dismiss() }
        }, Ui.at(Ui.WRAP, 52, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = 24 })

        // المحتوى
        val oldY = if (keep) scroll?.scrollY ?: 0 else 0
        keep = false
        val sv = ScrollView(a)
        scroll = sv
        content = Ui.col(a, Gravity.TOP or Gravity.RIGHT).apply { setPadding(24, 12, 24, 30) }
        sv.addView(content, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        design.addView(sv, Ui.abs(1280, 648, 0, 72))
        if (oldY > 0) sv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                sv.removeOnLayoutChangeListener(this); sv.scrollTo(0, oldY)
            }
        })

        when {
            query.isNotEmpty() -> results()
            section < 0 -> home()
            else -> {
                when (section) {
                    0 -> looks()
                    1 -> gaugeSec()
                    2 -> homeScreen()
                    3 -> prayer()
                    4 -> alerts()
                    5 -> matches()
                    6 -> driving()
                    else -> general()
                }
                resetButton()
            }
        }
        d.setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                if (q == query) return
                query = q
                content.removeAllViews()
                if (q.isEmpty()) { if (section < 0) home() else render() } else results()
            }
        })
        if (query.isNotEmpty()) { search.requestFocus(); search.setSelection(search.text.length) }
    }

    // ---------------- الصفحة الأولى ----------------
    private fun home() {
        // مفاتيح سريعة
        val q = Ui.row(a, Gravity.CENTER)
        fun quick(label: String, value: String, on: Boolean?, onTap: () -> Unit) {
            val box = Ui.col(a, Gravity.CENTER or Gravity.RIGHT).apply {
                background = Ui.pressable(Ui.rounded(pal.card, 14f, 2, pal.line), Ui.rounded(pal.line, 14f))
                setPadding(16, 0, 16, 0); setOnClickListener { onTap() }
            }
            box.addView(Ui.tv(a, 16f, pal.muted).apply { text = label }, Ui.lin(Ui.MATCH, 26))
            val r = Ui.row(a, Gravity.CENTER_VERTICAL)
            if (on != null) r.addView(Ui.switchView(a, on, pal), Ui.lin(56, 30))
            r.addView(Ui.tv(a, 20f, pal.text, true).apply { text = value }, Ui.lin(0, 36, 1f))
            box.addView(r, Ui.lin(Ui.MATCH, 38))
            q.addView(box, Ui.lin(0, 96, 1f).apply { leftMargin = 6; rightMargin = 6 })
        }
        quick("الثيم", if (Prefs.theme == "fixed") "ثابت" else "تلقائي", null) { section = 0; render() }
        quick("نظام الساعة", if (Prefs.clock24) "24 ساعة" else "12 ساعة", null) { Prefs.clock24 = !Prefs.clock24; changed() }
        quick("خفض السطوع ليلاً", if (Prefs.nightDim) "مفعّل" else "متوقف", Prefs.nightDim) { Prefs.nightDim = !Prefs.nightDim; changed() }
        quick("صوت التنبيهات", if (Prefs.alertOn("sound")) "مفعّل" else "متوقف", Prefs.alertOn("sound")) { Prefs.setAlert("sound", !Prefs.alertOn("sound")); changed() }
        quick("إبقاء الشاشة تعمل", if (Prefs.keepOn) "مفعّل" else "متوقف", Prefs.keepOn) { Prefs.keepOn = !Prefs.keepOn; changed() }
        content.addView(q, Ui.lin(Ui.MATCH, 100))

        // البلاطات 4×2
        for (rowI in 0..1) {
            val r = Ui.row(a, Gravity.CENTER)
            for (k in 0..3) {
                val i = rowI * 4 + k
                val t = Ui.row(a, Gravity.CENTER_VERTICAL).apply {
                    background = Ui.pressable(Ui.cardBg(pal), Ui.rounded(pal.line, 14f))
                    setPadding(16, 0, 16, 0); setOnClickListener { section = i; render() }
                }
                val tx = Ui.col(a, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
                tx.addView(Ui.tv(a, 22f, pal.text, true).apply { text = NAMES[i] }, Ui.lin(Ui.MATCH, 32))
                tx.addView(Ui.tv(a, 15f, pal.muted).apply { text = DESC[i] }, Ui.lin(Ui.MATCH, 24))
                t.addView(tx, Ui.lin(0, Ui.MATCH, 1f))
                t.addView(FrameLayout(a).apply {
                    background = Ui.rounded(pal.tile, 16f, 2, pal.tileLine)
                    addView(Ui.icon(a, ICONS[i], pal.accent), Ui.at(40, 40, Gravity.CENTER))
                }, Ui.lin(68, 68).apply { leftMargin = 12 })
                r.addView(t, Ui.lin(0, 150, 1f).apply { leftMargin = 6; rightMargin = 6 })
            }
            content.addView(r, Ui.lin(Ui.MATCH, 162).apply { topMargin = 14 })
        }
        note("نصيحة: اضغط مطولاً على الساعة في الشاشة الرئيسية للوحة السريعة، وعلى «رفيق الطريق» لفتح الإعدادات دائماً.")
    }

    private fun results() {
        val words = query.split(" ").filter { it.isNotEmpty() }
        val hits = INDEX.filter { (_, kw) -> words.all { w -> kw.contains(w) || NAMES[INDEX.indexOfFirst { it.second == kw }].contains(w) } }
        if (hits.isEmpty()) { note("لا توجد نتائج لـ«$query»"); return }
        for ((sec, kw) in hits) {
            val matched = kw.split(" ").filter { k -> words.any { k.contains(it) } }.distinct().take(4).joinToString("، ")
            line(NAMES[sec], if (matched.isEmpty()) DESC[sec] else matched, btn("فتح", true) { query = ""; section = sec; render() })
        }
    }

    // ---------------- عناصر ----------------
    private fun title(s: String) = content.addView(Ui.tv(a, 22f, pal.accent, true).apply { text = s }, Ui.lin(Ui.MATCH, 46).apply { topMargin = 16 })
    private fun note(s: String) = content.addView(Ui.tv(a, 16f, pal.muted).apply { text = s; maxLines = 3 }, Ui.lin(Ui.MATCH, Ui.WRAP).apply { topMargin = 6 })

    private fun btn(s: String, on: Boolean, onClick: () -> Unit): TextView = Ui.tv(a, 18f, if (on) pal.onAccent else pal.text, on, Gravity.CENTER).apply {
        text = s; setPadding(18, 0, 18, 0)
        background = Ui.pressable(if (on) Ui.rounded(pal.accent, 12f) else Ui.rounded(pal.card, 12f, 2, pal.line), Ui.rounded(pal.line, 12f))
        setOnClickListener { onClick() }
    }

    /** صف: الاسم والشرح يميناً، والعناصر يساره */
    private fun line(label: String, desc: String?, vararg views: View, onRow: (() -> Unit)? = null) {
        val r = Ui.row(a, Gravity.CENTER_VERTICAL).apply {
            background = Ui.pressable(Ui.rounded(pal.card, 14f, 2, pal.line), Ui.rounded(pal.line, 14f))
            setPadding(16, 0, 16, 0)
            if (onRow != null) setOnClickListener { onRow() }
        }
        for (v in views) {
            val lp = (v.layoutParams as? LinearLayout.LayoutParams) ?: Ui.lin(Ui.WRAP, 50)
            lp.leftMargin = 8
            r.addView(v, lp)
        }
        val tx = Ui.col(a, Gravity.CENTER_VERTICAL or Gravity.RIGHT)
        tx.addView(Ui.tv(a, 19f, pal.text).apply { text = label }, Ui.lin(Ui.MATCH, 28))
        if (!desc.isNullOrEmpty()) tx.addView(Ui.tv(a, 14f, pal.muted).apply { text = desc }, Ui.lin(Ui.MATCH, 22))
        r.addView(tx, Ui.lin(0, Ui.MATCH, 1f).apply { leftMargin = 16 })
        content.addView(r, Ui.lin(Ui.MATCH, if (desc.isNullOrEmpty()) 64 else 74).apply { topMargin = 8 })
    }

    private fun seg(label: String, desc: String?, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val views = options.indices.map { i -> btn(options[i], i == selected) { onPick(i); changed() } }
        line(label, desc, *views.toTypedArray())
    }

    /** مفتاح: الصف كله قابل للضغط */
    private fun toggle(label: String, desc: String?, on: Boolean, onChange: (Boolean) -> Unit) =
        line(label, desc, Ui.switchView(a, on, pal).apply { layoutParams = LinearLayout.LayoutParams(60, 32) }) { onChange(!on); changed() }

    private fun action(label: String, desc: String?, b: String, onClick: () -> Unit) = line(label, desc, btn(b, false, onClick))

    /** شبكة اختيارات (مثل أشكال العداد) */
    private fun grid(options: List<String>, selected: Int, perRow: Int, onPick: (Int) -> Unit) {
        var i = 0
        while (i < options.size) {
            val r = Ui.row(a, Gravity.CENTER)
            for (k in 0 until perRow) {
                val idx = i + k
                if (idx >= options.size) { r.addView(View(a), Ui.lin(0, 60, 1f)); continue }
                r.addView(btn(options[idx], idx == selected) { onPick(idx); changed() }, Ui.lin(0, 60, 1f).apply { leftMargin = 5; rightMargin = 5 })
            }
            content.addView(r, Ui.lin(Ui.MATCH, 66).apply { topMargin = 6 })
            i += perRow
        }
    }

    private fun swatches(label: String, slot: String) {
        val cur = Prefs.accent(slot)
        val views = Palette.ACCENTS.map { id ->
            FrameLayout(a).apply {
                background = Ui.rounded(Palette.swatch(id), 22f, if (id == cur) 4 else 0, pal.text)
                setOnClickListener { Prefs.setAccent(slot, id); changed() }
                layoutParams = LinearLayout.LayoutParams(44, 44)
            }
        }
        line(label, Palette.ACCENT_NAMES[Palette.ACCENTS.indexOf(cur).coerceAtLeast(0)], *views.toTypedArray())
    }

    private fun resetButton() {
        content.addView(View(a), Ui.lin(Ui.MATCH, 20))
        action("استعادة الافتراضي لهذا القسم", "تُعاد خيارات «${NAMES[section]}» فقط", "استعادة") {
            AlertDialog.Builder(a, T).setTitle("استعادة الافتراضي؟").setMessage("ستعود خيارات «${NAMES[section]}» كما كانت أول مرة.")
                .setPositiveButton("استعادة") { _, _ -> resetSection(section); changed() }
                .setNegativeButton("إلغاء", null).show()
        }
    }

    private fun resetSection(s: Int) {
        when (s) {
            0 -> { Prefs.remove(listOf("theme", "clock24", "bigFont", "brand", "brandLeft", "nightDim")); Prefs.removePrefix(listOf("base_", "acc_")) }
            1 -> Prefs.remove(listOf("gauge", "max", "alert", "showCar", "maxMark"))
            2 -> { Prefs.remove(listOf("hidden", "colR", "colL", "known", "barSize")); Prefs.removePrefix(listOf("slot", "dest_", "dialN", "dialP", "sc")) }
            3 -> { Prefs.remove(listOf("cityMode", "city", "preMin")); Prefs.removePrefix(listOf("adj")) }
            4 -> Prefs.remove(Prefs.ALERTS.filter { it != "match" }.map { "a_$it" })
            5 -> Prefs.remove(listOf("leagues", "matchMode", "fav", "a_match") + Prefs.MATCH_ALERTS)
            6 -> Prefs.remove(listOf("lockDriving", "standby", "keepOn", "pin", "a_rest"))
            else -> Prefs.remove(listOf("autoUpdate", "wifiOnly", "autoPlay"))
        }
    }

    // ---------------- الأقسام ----------------
    private fun looks() {
        title("ثيمات جاهزة")
        fun preset(day: Pair<String, String>?, night: Pair<String, String>) {
            if (day == null) { Prefs.theme = "fixed"; Prefs.setBase("fixed", night.first); Prefs.setAccent("fixed", night.second) }
            else {
                Prefs.theme = "auto"
                Prefs.setBase("day", day.first); Prefs.setAccent("day", day.second)
                Prefs.setBase("night", night.first); Prefs.setAccent("night", night.second)
            }
            changed()
        }
        line("نهار فاتح أزرق · ليل داكن سماوي", "الموصى به", btn("اختيار", false) { preset("light" to "blue", "dark" to "cyan") })
        line("نهار فاتح أزرق · ليل أسود كهرماني", "الأريح للعين ليلاً", btn("اختيار", false) { preset("light" to "blue", "black" to "amber") })
        line("داكن سماوي دائماً", "إن كان الفاتح يعكس الضوء على الشاشة", btn("اختيار", false) { preset(null, "dark" to "cyan") })
        title("تخصيص")
        val auto = Prefs.theme != "fixed"
        seg("طريقة الثيم", "التلقائي يبدّل بين ثيم النهار وثيم الليل عند الشروق والغروب",
            listOf("تلقائي", "ثابت"), if (auto) 0 else 1) {
            if (it == 0) Prefs.theme = "auto" else {
                if (Prefs.theme != "fixed") { Prefs.setBase("fixed", Prefs.base("night")); Prefs.setAccent("fixed", Prefs.accent("night")) }
                Prefs.theme = "fixed"
            }
        }
        val slots = if (auto) listOf("day" to "ثيم النهار", "night" to "ثيم الليل") else listOf("fixed" to "الثيم")
        for ((slot, name) in slots) {
            title(name)
            seg("الخلفية", if (slot == "night") "الأسود الخالص أريح للعين ليلاً" else null,
                Palette.BASE_NAMES, Palette.BASES.indexOf(Prefs.base(slot))) { Prefs.setBase(slot, Palette.BASES[it]) }
            swatches("لون التمييز", slot)
        }
        title("الساعة والخط")
        seg("نظام الساعة", "يُطبّق على كل الأوقات: الساعة، الصلاة، المباريات", listOf("12 ساعة", "24 ساعة"), if (Prefs.clock24) 1 else 0) { Prefs.clock24 = it == 1 }
        seg("حجم الخط", null, listOf("عادي", "كبير"), if (Prefs.bigFont) 1 else 0) { Prefs.bigFont = it == 1 }
        toggle("خفض السطوع ليلاً", "يخفّض إضاءة الواجهة بعد الغروب", Prefs.nightDim) { Prefs.nightDim = it }
        title("الشعار")
        val brands = listOf("COROLLA 2015", "TOYOTA", "Corolla", "كورولا 2015", "")
        seg("النص", null, listOf("COROLLA 2015", "TOYOTA", "Corolla", "كورولا 2015", "بدون"), brands.indexOf(Prefs.brand)) { Prefs.brand = brands[it] }
        seg("الموضع", null, listOf("يسار", "يمين"), if (Prefs.brandLeft) 0 else 1) { Prefs.brandLeft = it == 0 }
    }

    private fun gaugeSec() {
        title("شكل العداد")
        grid(listOf("قوس", "إبرة كلاسيكية", "قوس مقطّع", "نصف دائرة", "رقمي", "عداد وبوصلة", "حلقة", "شريط"),
            listOf(0, 3, 4, 5, 6, 7, 1, 2).indexOf(Prefs.gauge), 4) { Prefs.gauge = listOf(0, 3, 4, 5, 6, 7, 1, 2)[it] }
        note("الرقمي هو الأوضح أثناء القيادة. «عداد وبوصلة» تدور حلقته مع اتجاه السيارة.")
        title("خيارات")
        seg("الحد الأقصى", null, listOf("200", "240"), if (Prefs.maxSpeed == 200) 0 else 1) { Prefs.maxSpeed = if (it == 0) 200 else 240 }
        val alerts = listOf(0, 80, 100, 110, 120, 140)
        seg("تنبيه السرعة", "صفارة وشريط عند التجاوز", alerts.map { if (it == 0) "إيقاف" else "$it" }, alerts.indexOf(Prefs.alertSpeed)) { Prefs.alertSpeed = alerts[it] }
        toggle("صورة السيارة", "تختفي تلقائياً في العداد الرقمي", Prefs.showCar) { Prefs.showCar = it }
        toggle("علامة أعلى سرعة", "خط صغير على العداد عند أعلى سرعة في الرحلة", Prefs.maxMark) { Prefs.maxMark = it }
        action("تجربة صفارة السرعة", null, "تشغيل") { a.testSound(false) }
        title("معايرة السرعة")
        note("عدادات السيارات تعرض عادة سرعة أعلى من الحقيقية بـ3–7%. قراءة GPS هي الحقيقية التي تقيسها الكاميرات.")
        seg("طريقة العرض", "التنبيه وكمبيوتر الرحلة يبقيان على السرعة الحقيقية دائماً",
            listOf("GPS الحقيقية", "مطابقة عداد السيارة"), if (Prefs.speedMode == "car") 1 else 0) { Prefs.speedMode = if (it == 1) "car" else "gps" }
        if (Prefs.speedMode == "car") {
            val pcts = listOf(3, 5, 6, 8)
            val cur = Math.round((Prefs.speedFactor - 1f) * 100f)
            seg("الزيادة", "الحالية ${String.format(java.util.Locale.US, "%+.1f", (Prefs.speedFactor - 1f) * 100f)}%",
                pcts.map { "+$it%" }, pcts.indexOf(cur)) { Prefs.speedFactor = 1f + pcts[it] / 100f }
            action("معايرة دقيقة", "أثناء القيادة (للراكب) من اللوحة السريعة، أو أدخل الرقمين يدوياً", "معايرة") { d.dismiss(); a.calibrate() }
        }
    }

    private fun homeScreen() {
        seg("التخطيط", "البطاقات الكبيرة: 4 بطاقات بخط أكبر وأوضح أثناء القيادة", listOf("بطاقات كبيرة", "الكلاسيكي"),
            if (Prefs.layout == "big") 0 else 1) { Prefs.layout = if (it == 0) "big" else "classic" }
        if (Prefs.layout == "big") {
            title("البطاقات الكبيرة")
            for (i in 0..3) {
                val cur = Prefs.bigSlot(i)
                action(Prefs.BIG_POS[i], Prefs.BIG_NAMES[Prefs.BIG_TYPES.indexOf(cur).coerceAtLeast(0)], "تغيير") {
                    AlertDialog.Builder(a, T).setTitle(Prefs.BIG_POS[i])
                        .setSingleChoiceItems(Prefs.BIG_NAMES.toTypedArray(), Prefs.BIG_TYPES.indexOf(cur)) { dlg, w ->
                            dlg.dismiss(); Prefs.setBigSlot(i, Prefs.BIG_TYPES[w]); changed()
                        }.setNegativeButton("إلغاء", null).show()
                }
            }
            bottomBar(); return
        }
        title("البطاقات")
        note("أظهر حتى 4 بطاقات في كل عمود، ورتّبها. «نقل» يبدّلها مع المقابلة في العمود الآخر.")
        val r = Prefs.column(true); val l = Prefs.column(false)
        val hidden = Prefs.hidden()
        for (right in listOf(true, false)) {
            title(if (right) "العمود الأيمن" else "العمود الأيسر")
            val col = if (right) r else l
            for (idx in col.indices) {
                val id = col[idx]
                val vis = id !in hidden
                line(Prefs.cardName(id), null,
                    btn("نقل ⇄", false) {
                        val other = if (right) l else r
                        if (idx < other.size) { val o = other[idx]; other[idx] = id; col[idx] = o } else { col.removeAt(idx); other.add(id) }
                        Prefs.setColumns(r, l); changed()
                    },
                    btn("▼", false) { if (idx < col.size - 1) { col[idx] = col[idx + 1]; col[idx + 1] = id; Prefs.setColumns(r, l); changed() } },
                    btn("▲", false) { if (idx > 0) { col[idx] = col[idx - 1]; col[idx - 1] = id; Prefs.setColumns(r, l); changed() } },
                    Ui.switchView(a, vis, pal).apply {
                        layoutParams = LinearLayout.LayoutParams(60, 32)
                        setOnClickListener {
                            val h = HashSet(Prefs.hidden())
                            if (vis) h.add(id) else {
                                if (col.count { it !in h } >= 4) { a.toast("العمود ممتلئ (4 بطاقات). أخفِ بطاقة أو انقلها أولاً"); return@setOnClickListener }
                                h.remove(id)
                            }
                            Prefs.setHidden(h); changed()
                        }
                    })
            }
        }

        bottomBar()
    }

    private fun bottomBar() {
        title("القائمة السفلية")
        seg("الحجم", null, listOf("عادي", "كبير", "أيقونات فقط"), Prefs.barSize) { Prefs.barSize = it }
        if (Prefs.barSize != 2) toggle("أسماء الخانات", null, Prefs.slotLabels) { Prefs.slotLabels = it }
        note("الخانة 1 في أقصى اليمين. يمكنك أيضاً الضغط مطولاً على القائمة نفسها.")
        for (i in 0..5) {
            line("${i + 1}. ${a.slotTitle(i)}", null,
                btn("افتراضي", false) { Prefs.resetSlot(i); changed() },
                btn("←", false) { if (i < 5) { Prefs.swapSlots(i, i + 1); changed() } },
                btn("→", false) { if (i > 0) { Prefs.swapSlots(i, i - 1); changed() } },
                btn("تسمية", false) { rename(i) },
                btn("تغيير", true) { a.editSlot(i) { changed() } })
        }

        title("وجهاتي")
        note("احفظ الموقع وأنت فيه. المسجد بدون حفظ يبحث عن أقرب مسجد.")
        for ((k, n) in listOf("home" to "المنزل", "work" to "العمل", "mosque" to "المسجد")) {
            val saved = Prefs.str("dest_$k").isNotEmpty()
            line(n, if (saved) "محفوظ" else "غير محفوظ",
                btn("مسح", false) { Prefs.putStr("dest_$k", ""); changed() },
                btn("حفظ موقعي الآن", true) { a.saveDest(k); changed() })
        }

        title("الاتصال السريع")
        for (i in 0..2) {
            val nm = Prefs.str("dialN$i")
            action(if (nm.isEmpty()) "رقم ${i + 1}: فارغ" else nm, Prefs.str("dialP$i").ifEmpty { null }, "تعديل") { a.editDial(i) }
        }

        title("الاختصارات")
        for (i in 0..3) {
            val pkg = Prefs.str("sc$i")
            val label = if (pkg.isEmpty()) "اختصار ${i + 1}: فارغ" else try {
                a.packageManager.getApplicationLabel(a.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            line(label, null, btn("مسح", false) { Prefs.putStr("sc$i", ""); changed() }, btn("اختيار", true) { a.pickShortcut(i) })
        }
    }

    private fun rename(i: Int) {
        val e = EditText(a).apply { setText(Prefs.slotLabel(i)); hint = a.slotTitle(i); setSingleLine() }
        AlertDialog.Builder(a, T).setTitle("اسم الخانة").setView(e)
            .setPositiveButton("حفظ") { _, _ -> Prefs.setSlotLabel(i, e.text.toString().trim()); changed() }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun prayer() {
        title("المدينة")
        seg("طريقة التحديد", "التلقائي يتبع GPS، واليدوي مدينة ثابتة", listOf("تلقائي (GPS)", "يدوي"), if (Loc.manual()) 1 else 0) {
            Prefs.cityMode = if (it == 1) "manual" else "auto"; a.forceWeather()
        }
        line("المدينة الآن: ${Loc.name()}", "تُستخدم للصلاة والطقس معاً")
        action("المدينة المختارة: ${Prefs.city}", "للوضع اليدوي", "اختيار") { pickCity() }

        title("الصلاة")
        line("طريقة الحساب: أم القرى", "محسوبة على الجهاز بدون إنترنت")
        val pre = listOf(0, 5, 10, 15)
        seg("التنبيه قبل الصلاة", null, pre.map { if (it == 0) "بدون" else "$it د" }, pre.indexOf(Prefs.preMin).coerceAtLeast(0)) {
            Prefs.preMin = pre[it]; Prefs.setAlert("pre", pre[it] > 0)
        }
        action("تجربة صوت الأذان", "يستخدم صوت إشعار الجهاز", "تشغيل") { a.testSound(true) }

        title("ضبط الدقائق ليطابق مسجد الحي")
        val t = Astro.prayerMillis(Calendar.getInstance(), Loc.lat(), Loc.lon())
        for (i in 0..5) {
            val v = Prefs.adj(i)
            line("${Astro.PRAYER_NAMES[i]}  ${Ui.hm(t[i])}", if (v == 0) null else "معدّل ${if (v > 0) "+" else ""}$v دقيقة",
                btn("−", false) { Prefs.setAdj(i, v - 1); changed() },
                Ui.tv(a, 20f, pal.text, true, Gravity.CENTER).apply { text = (if (v > 0) "+" else "") + v; minWidth = 56 },
                btn("+", false) { Prefs.setAdj(i, v + 1); changed() })
        }
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

    private fun alerts() {
        note("تظهر كشريط أعلى الشاشة يختفي بعد 15 ثانية، ولا تظهر نوافذ أثناء الحركة.")
        val desc = mapOf("pre" to "مدته من قسم الصلاة", "speed" to "الحد من قسم العداد", "rest" to "كل ساعتين قيادة متواصلة",
            "dua" to "الركوب بعد توقف طويل، والسفر بعد 50 كم", "fast" to "بعد المغرب: الاثنين والخميس والأيام البيض وعرفة")
        for (k in Prefs.ALERTS.indices) {
            val key = Prefs.ALERTS[k]
            if (key == "match") continue
            toggle(Prefs.ALERT_NAMES[k], desc[key], Prefs.alertOn(key)) { Prefs.setAlert(key, it) }
        }
        title("تجربة")
        action("صوت الأذان", null, "تشغيل") { a.testSound(true) }
        action("صفارة السرعة", null, "تشغيل") { a.testSound(false) }
    }

    private fun matches() {
        val fav = Prefs.favTeams()
        action("آخر تحديث: " + a.matchesUpdatedText(), null, "تحديث الآن") { a.forceMatches(); a.toast("جارٍ تحديث المباريات…") }
        action("الفرق المفضلة", if (fav.isEmpty()) "لم تُختر بعد" else fav.joinToString("، "), "اختيار") { pickTeams() }
        seg("العرض في البطاقة", "الأولوية لمباراة فريقك المباشرة", listOf("كل المباريات", "فرقي فقط"), if (Prefs.matchMode == "fav") 1 else 0) {
            Prefs.matchMode = if (it == 1) "fav" else "all"; a.forceMatches()
        }
        title("البطولات")
        note("كل بطولة تلغيها تعني طلبات أقل على الإنترنت.")
        val on = Prefs.leagues()
        for (i in Prefs.LEAGUE_IDS.indices) {
            val id = Prefs.LEAGUE_IDS[i]
            toggle(Prefs.LEAGUE_NAMES[i], null, id in on) { v ->
                val s = HashSet(Prefs.leagues()); if (v) s.add(id) else s.remove(id); Prefs.setLeagues(s); a.forceMatches()
            }
        }
        title("تنبيهات فرقك المفضلة")
        toggle("تنبيهات المباريات", null, Prefs.alertOn("match")) { Prefs.setAlert("match", it) }
        for (i in Prefs.MATCH_ALERTS.indices) {
            val k = Prefs.MATCH_ALERTS[i]
            toggle(Prefs.MATCH_ALERT_NAMES[i], null, Prefs.bool(k, true)) { Prefs.putBool(k, it) }
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

    private fun driving() {
        toggle("قفل التعديل أثناء القيادة", "يمنع فتح الإعدادات وتعديل القائمة فوق 10 كم/س", Prefs.lockDriving) { Prefs.lockDriving = it }
        toggle("وضع الانتظار", "شاشة داكنة بساعة كبيرة بعد التوقف 10 دقائق", Prefs.standby) { Prefs.standby = it }
        toggle("إبقاء الشاشة تعمل", "يمنع انطفاء الشاشة ما دامت الواجهة ظاهرة", Prefs.keepOn) { Prefs.keepOn = it }
        toggle("تنبيه الاستراحة", "كل ساعتين قيادة متواصلة", Prefs.alertOn("rest")) { Prefs.setAlert("rest", it) }
        action("شاشة السرعة الكبيرة", "مريحة ليلاً، ولمسة تغلقها", "تشغيل") { d.dismiss(); Panels.bigSpeed(a) }
        title("الرقم السري")
        if (Prefs.pin.isEmpty()) action("قفل الإعدادات برقم سري", "اختياري، حتى لا يغيّرها غيرك", "تعيين") { setPin() }
        else action("الإعدادات مقفلة برقم سري", null, "إزالة") { Prefs.pin = ""; changed() }
        title("الرحلة")
        action("سجل الرحلات", "آخر 20 رحلة", "عرض") { Panels.tripLog(a) }
        action("كمبيوتر الرحلة", null, "تصفير") { Trip.reset(); changed() }
    }

    private fun setPin() {
        val e = EditText(a).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "4 أرقام أو أكثر" }
        AlertDialog.Builder(a, T).setTitle("الرقم السري").setView(e)
            .setPositiveButton("حفظ") { _, _ ->
                val p = e.text.toString().trim()
                if (p.length < 4) a.toast("الرقم قصير: 4 أرقام على الأقل") else { Prefs.pin = p; a.toast("تم قفل الإعدادات"); changed() }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun general() {
        val ver = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (e: Exception) { "" }
        val mem = a.memoryMb()
        line("الإصدار المثبّت: $ver", if (mem > 0) "يستهلك اللانشر الآن نحو $mem ميجا من الذاكرة" else null)
        title("التحديث")
        toggle("الفحص اليومي عن تحديث", null, Prefs.autoUpdate) { Prefs.autoUpdate = it }
        toggle("التحميل عبر الواي فاي فقط", "لتوفير باقة الجوال", Prefs.wifiOnly) { Prefs.wifiOnly = it }
        action("التحقق من وجود تحديث", null, "تحقق الآن") { a.checkUpdate(true) }
        action("اختبار الاتصال", "يفحص الشبكة والطقس والمباريات ويوضح سبب أي مشكلة", "اختبار") { a.netTest() }
        title("الشاشة")
        action("اجعله الواجهة الرئيسية للشاشة", null, "فتح") { open(Settings.ACTION_HOME_SETTINGS) }
        action("عرض اسم المقطع في بطاقة الموسيقى", "يحتاج إذن الوصول إلى الإشعارات", "السماح") { open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }
        toggle("تشغيل الموسيقى تلقائياً عند تشغيل الشاشة", null, Prefs.autoPlay) { Prefs.autoPlay = it }
        title("النسخ الاحتياطي")
        action("حفظ كل الإعدادات في ملف", null, "نسخ احتياطي") { a.backup(true) }
        action("استرجاع الإعدادات من ملف", null, "استرجاع") { d.dismiss(); a.backup(false) }
        title("استعادة")
        action("استعادة كل الإعدادات الافتراضية", "يبقى سجل الرحلات والموقع", "استعادة الكل") {
            AlertDialog.Builder(a, T).setTitle("استعادة كل الإعدادات؟").setMessage("ستعود كل الخيارات كما كانت أول مرة.")
                .setPositiveButton("استعادة") { _, _ -> Prefs.resetAll(); changed() }
                .setNegativeButton("إلغاء", null).show()
        }
    }

    private fun open(action: String) {
        try { a.startActivity(Intent(action)) } catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) {}
        }
    }
}
