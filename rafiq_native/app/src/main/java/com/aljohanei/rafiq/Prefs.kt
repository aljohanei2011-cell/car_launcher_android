package com.aljohanei.rafiq

import android.content.Context
import android.content.SharedPreferences

/** كل الإعدادات في ملف واحد صغير */
object Prefs {
    private var sp: SharedPreferences? = null
    fun init(c: Context) { if (sp == null) sp = c.applicationContext.getSharedPreferences("rafiq", Context.MODE_PRIVATE) }
    private fun p(): SharedPreferences = sp!!

    val CARD_IDS = listOf("time", "weather", "trip", "matches", "music", "prayer", "azkar", "nav")
    val CARD_NAMES = listOf("الوقت والتاريخ", "الطقس", "كمبيوتر الرحلة", "المباريات", "الموسيقى", "الصلاة", "الأذكار", "الملاحة")
    fun cardName(id: String): String = CARD_NAMES.getOrElse(CARD_IDS.indexOf(id)) { id }

    /** auto / dark / light / amber */
    var theme: String
        get() = str("theme", "auto")
        set(v) = putStr("theme", v)

    /** 0 قوس، 1 حلقة، 2 شريط */
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
        val def = if (right) "time,weather,trip,matches" else "music,prayer,azkar,nav"
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
    val ALERTS = listOf("prayer", "pre", "speed", "rest", "match", "weather", "dua", "friday", "sound")
    val ALERT_NAMES = listOf("الأذان", "قبل الصلاة", "تجاوز السرعة", "الاستراحة", "المباريات (فرقك المفضلة)",
        "الطقس الشديد", "الأدعية (الركوب والسفر)", "تذكير الجمعة", "صوت التنبيهات")
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
