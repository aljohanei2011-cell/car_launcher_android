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

    /** auto / dark / light / amber */
    var theme: String
        get() = p().getString("theme", "auto") ?: "auto"
        set(v) { p().edit().putString("theme", v).apply() }

    /** 0 قوس، 1 حلقة، 2 شريط */
    var gauge: Int
        get() = p().getInt("gauge", 0)
        set(v) { p().edit().putInt("gauge", v).apply() }

    var maxSpeed: Int
        get() = p().getInt("max", 240)
        set(v) { p().edit().putInt("max", v).apply() }

    /** 0 = التنبيه متوقف */
    var alertSpeed: Int
        get() = p().getInt("alert", 120)
        set(v) { p().edit().putInt("alert", v).apply() }

    var brand: String
        get() = p().getString("brand", "COROLLA 2015") ?: "COROLLA 2015"
        set(v) { p().edit().putString("brand", v).apply() }

    var brandLeft: Boolean
        get() = p().getBoolean("brandLeft", true)
        set(v) { p().edit().putBoolean("brandLeft", v).apply() }

    var nightDim: Boolean
        get() = p().getBoolean("nightDim", true)
        set(v) { p().edit().putBoolean("nightDim", v).apply() }

    var prayerAlerts: Boolean
        get() = p().getBoolean("prayerAlerts", true)
        set(v) { p().edit().putBoolean("prayerAlerts", v).apply() }

    fun hidden(): Set<String> = p().getStringSet("hidden", emptySet()) ?: emptySet()
    fun setHidden(s: Set<String>) { p().edit().putStringSet("hidden", HashSet(s)).apply() }

    /** آخر موقع معروف، والرياض قبل أول إشارة GPS */
    var lat: Double
        get() = p().getFloat("lat", 24.7136f).toDouble()
        set(v) { p().edit().putFloat("lat", v.toFloat()).apply() }
    var lon: Double
        get() = p().getFloat("lon", 46.6753f).toDouble()
        set(v) { p().edit().putFloat("lon", v.toFloat()).apply() }

    /** القائمة السفلية: حزمة التطبيق المختار لكل خانة، أو فارغ للافتراضي */
    fun slot(i: Int): String = p().getString("slot$i", "") ?: ""
    fun setSlot(i: Int, pkg: String) { p().edit().putString("slot$i", pkg).apply() }

    fun str(k: String, d: String = ""): String = p().getString(k, d) ?: d
    fun putStr(k: String, v: String) { p().edit().putString(k, v).apply() }
    fun int(k: String, d: Int = 0): Int = p().getInt(k, d)
    fun putInt(k: String, v: Int) { p().edit().putInt(k, v).apply() }
}
