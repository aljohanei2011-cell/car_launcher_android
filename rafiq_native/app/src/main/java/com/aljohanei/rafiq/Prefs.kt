package com.aljohanei.rafiq

import android.content.Context
import android.content.SharedPreferences

/** كل الإعدادات في ملف واحد صغير */
object Prefs {
    private var sp: SharedPreferences? = null
    fun init(c: Context) { if (sp == null) sp = c.applicationContext.getSharedPreferences("rafiq", Context.MODE_PRIVATE) }
    private fun p(): SharedPreferences = sp!!

    val CARD_IDS = listOf("time", "weather", "trip", "matches", "music", "prayer", "azkar", "nav",
        "dest", "compass", "occasions", "ayah", "dial", "shortcuts")
    val CARD_NAMES = listOf("الوقت والتاريخ", "الطقس", "كمبيوتر الرحلة", "المباريات", "الموسيقى", "الصلاة", "الأذكار", "الملاحة",
        "وجهاتي", "البوصلة", "المناسبات", "آية اليوم", "اتصال سريع", "اختصارات")
    /** البطاقات الجديدة تبدأ مخفية حتى لا يزدحم العمود؛ تُظهرها من الإعدادات */
    val NEW_CARDS = listOf("dest", "compass", "occasions", "ayah", "dial", "shortcuts")
    fun knownCards(): Set<String> = p().getStringSet("known", emptySet()) ?: emptySet()
    fun setKnownCards(s: Set<String>) { p().edit().putStringSet("known", HashSet(s)).apply() }
    fun cardName(id: String): String = CARD_NAMES.getOrElse(CARD_IDS.indexOf(id)) { id }

    /** auto = ثيم للنهار وثيم لليل، fixed = ثيم واحد. (القيم القديمة dark/light/amber تُحوَّل تلقائياً) */
    var theme: String
        get() = str("theme", "auto")
        set(v) = putStr("theme", v)
    // الخلفية: dark / black / light ، لون التمييز: cyan / red / green / amber / blue / silver
    // الافتراضي يطابق الشكل الحالي (داكن سماوي ليلاً، فاتح نهاراً) حتى لا يتغير شيء بعد التحديث
    fun base(slot: String): String = str("base_$slot", when (slot) { "day" -> "light"; else -> "dark" })
    fun accent(slot: String): String = str("acc_$slot", if (slot == "day") "blue" else "cyan")

    /** التخطيط: big = بطاقات كبيرة (4)، classic = الكلاسيكي (حتى 8) */
    var layout: String
        get() = str("layout", "big")
        set(v) = putStr("layout", v)
    val BIG_TYPES = listOf("timewx", "prayer", "music", "matches", "azkar", "dest")
    val BIG_NAMES = listOf("الوقت والطقس", "الصلاة", "الموسيقى", "المباريات", "الأذكار", "وجهاتي")
    val BIG_POS = listOf("اليمين أعلى", "اليمين أسفل", "اليسار أعلى", "اليسار أسفل")
    fun bigSlot(i: Int): String = str("big$i", listOf("timewx", "prayer", "music", "matches")[i])
    fun setBigSlot(i: Int, v: String) = putStr("big$i", v)
    fun setBase(slot: String, v: String) = putStr("base_$slot", v)
    fun setAccent(slot: String, v: String) = putStr("acc_$slot", v)

    /** نظام الساعة: 12 افتراضياً */
    var clock24: Boolean
        get() = bool("clock24", false)
        set(v) = putBool("clock24", v)

    /** حجم القائمة السفلية: 0 عادي، 1 كبير، 2 أيقونات فقط */
    var barSize: Int
        get() = int("barSize", 1)
        set(v) = putInt("barSize", v)

    var showCar: Boolean
        get() = bool("showCar", true)
        set(v) = putBool("showCar", v)
    var maxMark: Boolean
        get() = bool("maxMark", true)
        set(v) = putBool("maxMark", v)
    var keepOn: Boolean
        get() = bool("keepOn", false)
        set(v) = putBool("keepOn", v)
    /** رقم سري اختياري لفتح الإعدادات */
    var pin: String
        get() = str("pin")
        set(v) = putStr("pin", v)

    fun remove(keys: Collection<String>) { val e = p().edit(); for (k in keys) e.remove(k); e.apply() }
    fun removePrefix(prefixes: Collection<String>) {
        val e = p().edit()
        for (k in p().all.keys) if (prefixes.any { k.startsWith(it) }) e.remove(k)
        e.apply()
    }
    /** استعادة كل الإعدادات مع إبقاء الموقع وسجل الرحلات وإحصاءات الاستخدام */
    fun resetAll() {
        val keep = setOf("lat", "lon", "tripLog", "ride", "locMin", "hint1", "updChecked", "updTried")
        val e = p().edit()
        for (k in p().all.keys) if (k !in keep && !k.startsWith("use_")) e.remove(k)
        e.apply()
    }

    /** 0 قوس، 1 حلقة، 2 شريط، 3 إبرة، 4 قوس مقطّع، 5 نصف دائرة، 6 رقمي، 7 عداد وبوصلة */
    var gauge: Int
        get() = int("gauge", 0)
        set(v) = putInt("gauge", v)

    var maxSpeed: Int
        get() = int("max", 240)
        set(v) = putInt("max", v)

    /** 0 = التنبيه متوقف */
    var alertSpeed: Int
        get() = int("alert", 120)
        set(v) = putInt("alert", v)

    var brand: String
        get() = str("brand", "COROLLA 2015")
        set(v) = putStr("brand", v)

    var brandLeft: Boolean
        get() = bool("brandLeft", true)
        set(v) = putBool("brandLeft", v)

    var nightDim: Boolean
        get() = bool("nightDim", true)
        set(v) = putBool("nightDim", v)

    var lockDriving: Boolean
        get() = bool("lockDriving", true)
        set(v) = putBool("lockDriving", v)

    var slotLabels: Boolean
        get() = bool("slotLabels", true)
        set(v) = putBool("slotLabels", v)

    fun hidden(): Set<String> = p().getStringSet("hidden", emptySet()) ?: emptySet()
    fun setHidden(s: Set<String>) { p().edit().putStringSet("hidden", HashSet(s)).apply() }

    /** ترتيب البطاقات في العمودين */
    fun column(right: Boolean): MutableList<String> {
        val def = if (right) "time,weather,trip,matches,compass,occasions,ayah" else "music,prayer,azkar,nav,dest,dial,shortcuts"
        val l = str(if (right) "colR" else "colL", def).split(",").filter { it in CARD_IDS }.toMutableList()
        return l
    }
    fun setColumns(r: List<String>, l: List<String>) { putStr("colR", r.joinToString(",")); putStr("colL", l.joinToString(",")) }

    /** آخر موقع GPS، وينبع قبل أول إشارة */
    var lat: Double
        get() = p().getFloat("lat", 24.0895f).toDouble()
        set(v) { p().edit().putFloat("lat", v.toFloat()).apply() }
    var lon: Double
        get() = p().getFloat("lon", 38.0637f).toDouble()
        set(v) { p().edit().putFloat("lon", v.toFloat()).apply() }

    /** المدينة: auto يتبع GPS، manual مدينة ثابتة */
    var cityMode: String
        get() = str("cityMode", "auto")
        set(v) = putStr("cityMode", v)
    var city: String
        get() = str("city", "ينبع")
        set(v) = putStr("city", v)

    /** ضبط دقائق كل صلاة (0 الفجر … 5 العشاء) */
    fun adj(i: Int): Int = int("adj$i", 0)
    fun setAdj(i: Int, v: Int) = putInt("adj$i", v.coerceIn(-30, 30))

    /** دقائق التنبيه قبل الصلاة (0 = بدون) */
    var preMin: Int
        get() = int("preMin", 10)
        set(v) = putInt("preMin", v)

    // مفاتيح أنواع التنبيهات
    val ALERTS = listOf("prayer", "pre", "speed", "rest", "match", "weather", "dust", "heat", "dua", "friday", "fast", "sound")
    val ALERT_NAMES = listOf("الأذان", "قبل الصلاة", "تجاوز السرعة", "الاستراحة", "المباريات (فرقك المفضلة)",
        "الطقس الشديد", "الغبار الشديد", "الحرارة فوق 45°", "الأدعية (الركوب والسفر)", "تذكير الجمعة", "الصيام والمناسبات", "صوت التنبيهات")

    // المباريات
    val LEAGUE_IDS = listOf("ksa.1", "ksa.kings.cup", "afc.champions", "uefa.champions", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1")
    val LEAGUE_NAMES = listOf("الدوري السعودي", "كأس الملك", "أبطال آسيا", "أبطال أوروبا", "الدوري الإنجليزي",
        "الدوري الإسباني", "الدوري الإيطالي", "الدوري الألماني", "الدوري الفرنسي")
    fun leagues(): Set<String> = p().getStringSet("leagues", null) ?: LEAGUE_IDS.toSet()
    fun setLeagues(s: Set<String>) { p().edit().putStringSet("leagues", HashSet(s)).apply() }
    /** all = كل المباريات، fav = فرقي المفضلة فقط */
    var matchMode: String
        get() = str("matchMode", "all")
        set(v) = putStr("matchMode", v)
    val MATCH_ALERTS = listOf("m_pre", "m_start", "m_goal", "m_final")
    val MATCH_ALERT_NAMES = listOf("قبل المباراة بربع ساعة", "البداية", "الأهداف", "النتيجة النهائية")

    // التحديث
    var autoUpdate: Boolean
        get() = bool("autoUpdate", true)
        set(v) = putBool("autoUpdate", v)
    var wifiOnly: Boolean
        get() = bool("wifiOnly", false)
        set(v) = putBool("wifiOnly", v)
    /** خط كبير لقراءة أسهل */
    var bigFont: Boolean
        get() = bool("bigFont", false)
        set(v) = putBool("bigFont", v)
    /** وضع الانتظار بعد التوقف 10 دقائق */
    var standby: Boolean
        get() = bool("standby", true)
        set(v) = putBool("standby", v)
    /** تشغيل الموسيقى تلقائياً عند تشغيل الشاشة */
    var autoPlay: Boolean
        get() = bool("autoPlay", false)
        set(v) = putBool("autoPlay", v)
    /** السطوع اليدوي من اللوحة السريعة (-1 = تلقائي) */
    var bright: Float
        get() = p().getFloat("bright", -1f)
        set(v) { p().edit().putFloat("bright", v).apply() }

    /** النسخ الاحتياطي: كل الإعدادات كنص JSON */
    fun exportJson(): String {
        val o = org.json.JSONObject()
        for ((k, v) in p().all) {
            val e = org.json.JSONObject()
            when (v) {
                is String -> e.put("s", v)
                is Int -> e.put("i", v)
                is Boolean -> e.put("b", v)
                is Float -> e.put("f", v.toDouble())
                is Long -> e.put("l", v)
                is Set<*> -> e.put("set", org.json.JSONArray(v.map { it.toString() }))
                else -> continue
            }
            o.put(k, e)
        }
        return o.toString()
    }
    fun importJson(json: String) {
        val o = org.json.JSONObject(json)
        val ed = p().edit()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next(); val e = o.getJSONObject(k)
            when {
                e.has("s") -> ed.putString(k, e.getString("s"))
                e.has("i") -> ed.putInt(k, e.getInt("i"))
                e.has("b") -> ed.putBoolean(k, e.getBoolean("b"))
                e.has("f") -> ed.putFloat(k, e.getDouble("f").toFloat())
                e.has("l") -> ed.putLong(k, e.getLong("l"))
                e.has("set") -> { val a = e.getJSONArray("set"); ed.putStringSet(k, HashSet((0 until a.length()).map { i -> a.getString(i) })) }
            }
        }
        ed.apply()
    }

    fun long(k: String): Long = p().getLong(k, 0L)
    fun putLong(k: String, v: Long) { p().edit().putLong(k, v).apply() }
    fun alertOn(k: String): Boolean = bool("a_$k", true)
    fun setAlert(k: String, v: Boolean) = putBool("a_$k", v)
    var prayerAlerts: Boolean
        get() = alertOn("prayer")
        set(v) = setAlert("prayer", v)

    fun favTeams(): Set<String> = p().getStringSet("fav", emptySet()) ?: emptySet()
    fun setFavTeams(s: Set<String>) { p().edit().putStringSet("fav", HashSet(s)).apply() }

    // القائمة السفلية: لكل خانة نوع (fn أو app) وقيمة واسم اختياري
    val SLOT_FN = listOf("home", "nav", "music", "apps", "car", "settings")
    fun slotType(i: Int): String {
        val t = str("slotT$i")
        if (t.isNotEmpty()) return t
        return if (str("slot$i").isNotEmpty()) "app" else "fn"
    }
    fun slotValue(i: Int): String {
        val v = str("slotV$i")
        if (v.isNotEmpty()) return v
        val old = str("slot$i")
        return if (old.isNotEmpty()) old else SLOT_FN[i]
    }
    fun setSlot(i: Int, type: String, value: String) { putStr("slotT$i", type); putStr("slotV$i", value); putStr("slot$i", "") }
    fun slotLabel(i: Int): String = str("slotL$i")
    fun setSlotLabel(i: Int, s: String) = putStr("slotL$i", s)
    fun swapSlots(a: Int, b: Int) {
        val ta = slotType(a); val va = slotValue(a); val la = slotLabel(a)
        setSlot(a, slotType(b), slotValue(b)); setSlotLabel(a, slotLabel(b))
        setSlot(b, ta, va); setSlotLabel(b, la)
    }
    fun resetSlot(i: Int) { setSlot(i, "fn", SLOT_FN[i]); setSlotLabel(i, "") }

    fun used(pkg: String) = putInt("use_$pkg", int("use_$pkg") + 1)
    fun usage(pkg: String) = int("use_$pkg")

    fun str(k: String, d: String = ""): String = p().getString(k, d) ?: d
    fun putStr(k: String, v: String) { p().edit().putString(k, v).apply() }
    fun int(k: String, d: Int = 0): Int = p().getInt(k, d)
    fun putInt(k: String, v: Int) { p().edit().putInt(k, v).apply() }
    fun bool(k: String, d: Boolean): Boolean = p().getBoolean(k, d)
    fun putBool(k: String, v: Boolean) { p().edit().putBoolean(k, v).apply() }
}
