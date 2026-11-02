package com.example.carlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** سجل الأعطال: يحفظ أي خطأ مع وقته لنعرف السبب بدقة */
object CrashLog {
    private const val MAX = 60 * 1024
    fun file(c: Context) = File(c.filesDir, "crash.log")

    @Synchronized
    fun add(c: Context, source: String, msg: String) {
        try {
            val f = file(c)
            val line = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) +
                " [" + source + "] v" + BuildConfig.VERSION_CODE + " / Android " + Build.VERSION.RELEASE + "\n" + msg.take(4000) + "\n\n"
            f.appendText(line)
            if (f.length() > MAX) { val t = f.readText(); f.writeText(t.takeLast(MAX / 2)) }
        } catch (e: Exception) {}
    }
    fun read(c: Context): String = try { file(c).takeIf { it.exists() }?.readText() ?: "" } catch (e: Exception) { "" }
    fun clear(c: Context) { file(c).delete() }
    /** عدد مرات إغلاق محرك الصفحات لنقص الذاكرة خلال آخر 3 أيام */
    fun memKills(c: Context, days: Int = 3): Int {
        val t = read(c); if (t.isEmpty()) return 0
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val since = System.currentTimeMillis() - days * 86_400_000L
        return t.split("\n\n").count { e ->
            e.contains("[webview]") && e.contains("الذاكرة") &&
                (try { fmt.parse(e.take(19))?.time ?: 0L } catch (x: Exception) { 0L }) > since
        }
    }

    /** تسجيل الأخطاء غير المتوقعة في التطبيق كله */
    fun install(c: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(c.applicationContext, prev))
    }
    private class Handler(val c: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            add(c, "crash:" + t.name, android.util.Log.getStackTraceString(e))
            prev?.uncaughtException(t, e)
        }
    }
}

/**
 * مراقب الإغلاق: نحفظ أن الواجهة كانت ظاهرة. إذا أُغلق التطبيق وهي ظاهرة (نقص ذاكرة أو تنظيف من الجهاز)
 * وأعاد النظام تشغيل الخدمة، نعيد فتح الواجهة.
 */
object Watchdog {
    private fun sp(c: Context) = c.getSharedPreferences("watchdog", Context.MODE_PRIVATE)
    fun setForeground(c: Context, fg: Boolean) { sp(c).edit().putBoolean("fg", fg).putLong("at", System.currentTimeMillis()).commit() }
    fun enabled(c: Context) = Prefs.config(c).optBoolean("watchdog", true)

    fun relaunchIfNeeded(c: Context) {
        val s = sp(c)
        if (!enabled(c) || !s.getBoolean("fg", false) || MainActivity.alive) return
        if (System.currentTimeMillis() - s.getLong("at", 0L) > 30 * 60_000L) return // لا نفتحها فجأة بعد وقت طويل
        CrashLog.add(c, "watchdog", "أُغلقت الواجهة وهي ظاهرة، إعادة فتحها")
        s.edit().putBoolean("fg", false).apply()
        try {
            c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: Exception) {
            CrashLog.add(c, "watchdog", "تعذّرت إعادة الفتح: " + e.message)
        }
    }
    /** الإذن «الظهور فوق التطبيقات» يسمح لأندرويد الحديث بإعادة فتح الواجهة من الخلفية */
    fun canOverlay(c: Context) = Settings.canDrawOverlays(c)
}

/** سجل أحداث قصير (آخر 80 حدثاً): متى ظهرت الواجهة واختفت، وما الذي فُتح فوقها، ولماذا خرجت الاستراحة */
object Events {
    private const val MAX = 80
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private fun sp(c: Context) = c.getSharedPreferences("events", Context.MODE_PRIVATE)
    @Synchronized
    fun add(c: Context, msg: String) {
        try {
            val s = sp(c); val old = s.getString("log", "") ?: ""
            val lines = (old.split("\n").filter { it.isNotEmpty() } + (fmt.format(Date()) + "  " + msg.take(160))).takeLast(MAX)
            s.edit().putString("log", lines.joinToString("\n")).apply()
        } catch (e: Exception) {}
    }
    fun read(c: Context): String = sp(c).getString("log", "") ?: ""
    fun clear(c: Context) { sp(c).edit().remove("log").apply() }
}

/**
 * «ابقَ في الاستراحة»: إذا كانت الاستراحة مفتوحة واختفت الواجهة بدون لمس منك
 * (وظهر مكانها قائمة التطبيقات الأخيرة أو واجهة النظام)، نعيد فتحها.
 */
object RestGuard {
    @Volatile var restOpen = false
    @Volatile var nativeRest = false
    @Volatile var lastTouch = 0L
    @Volatile var pausedAt = 0L
    fun enabled(c: Context) = Prefs.config(c).optBoolean("stayRest", true)
    /** نافذة النظام التي ظهرت: هل هي قائمة الأخيرة أو واجهة النظام؟ */
    fun isSystemShell(pkg: String, cls: String): Boolean {
        val p = pkg.lowercase(); val k = cls.lowercase()
        return k.contains("recents") || p.contains("launcher") || p.contains("quickstep") || p == "com.android.systemui" && k.contains("recent")
    }
    fun shouldReturn(c: Context): Boolean {
        val now = System.currentTimeMillis()
        return enabled(c) && restOpen && !MainActivity.visible && !RestActivity.visible && now - pausedAt < 15_000 && now - lastTouch > 4_000
    }
    fun bringBack(c: Context, why: String) {
        Events.add(c, "إعادة فتح الاستراحة ($why)")
        try {
            c.startActivity(Intent(c, if (nativeRest) RestActivity::class.java else MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) { Events.add(c, "تعذّرت إعادة الفتح: " + e.message) }
    }
}

/**
 * استكمال الملاحة: إذا أُطفئت السيارة والخرائط مفتوحة ثم شُغّلت خلال 30 دقيقة،
 * نفتح تطبيق الملاحة نفسه ليكمل الطريق (بعد تشغيل الشاشة فقط، مرة واحدة).
 */
object NavResume {
    private val PKGS = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "ru.yandex.yandexnavi",
        "com.sygic.aura", "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro", "com.tomtom.gplay.navapp")
    @Volatile private var checked = false
    private fun sp(c: Context) = c.getSharedPreferences("navresume", Context.MODE_PRIVATE)
    fun isNav(pkg: String) = pkg in PKGS || pkg.contains("waze") || pkg.contains("navi") || pkg.endsWith(".maps")
    fun mark(c: Context, pkg: String?) { if (!pkg.isNullOrEmpty() && isNav(pkg)) sp(c).edit().putString("pkg", pkg).putLong("at", System.currentTimeMillis()).apply() }
    fun clear(c: Context) { sp(c).edit().remove("pkg").apply() }
    /** تُستدعى دورياً من الخدمة: آخر وقت كانت فيه الشاشة تعمل */
    fun alive(c: Context) { sp(c).edit().putLong("alive", System.currentTimeMillis()).apply() }

    /** عند ظهور الواجهة: إن كانت الشاشة شُغّلت للتو وكانت الملاحة مفتوحة قبل الإطفاء، نفتحها */
    fun checkOnBoot(a: Activity): Boolean {
        if (checked) return false
        checked = true
        val s = sp(a); val pkg = s.getString("pkg", null) ?: return false
        if (android.os.SystemClock.elapsedRealtime() > 10 * 60_000L) return false
        val alive = s.getLong("alive", 0L)
        if (alive == 0L || System.currentTimeMillis() - alive > 30 * 60_000L || System.currentTimeMillis() < alive) return false
        if (!Prefs.config(a).optBoolean("resumeNav", true)) return false
        val i = a.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Handler(Looper.getMainLooper()).postDelayed({ try { a.startActivity(i); Events.add(a, "استكمال الملاحة بعد التشغيل: $pkg") } catch (e: Exception) {} }, 2500)
        return true
    }
}
