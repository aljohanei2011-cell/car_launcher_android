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
 * حالة الاستراحة الأصلية عبر قتل التطبيق: المتغيرات تضيع مع قتل العملية، فنحفظ العلامات في ملف.
 * - RestActivity تكتب: opened عند الفتح، وbeat كل دقيقة وهي ظاهرة، وclosed عند الخروج السليم.
 * - check: عند بدء عملية جديدة، إن وُجدت استراحة «مفتوحة» بلا خروج سليم (ولم يُعَد تشغيل الجهاز) فقد قتل النظام التطبيق.
 *   نسجّل ذلك (فيتوقف الفيديو ست ساعات)، ونعيد فتح الاستراحة إن كانت النبضة حديثة وطلب المستخدم «ابقَ في الاستراحة»،
 *   وبحد أقصى 3 مرات كل 10 دقائق حتى لا تتكرر الحلقة إن استمر القتل.
 */
object RestState {
    private const val FRESH_MS = 5 * 60_000L
    private fun sp(c: Context) = c.getSharedPreferences("restwatch", Context.MODE_PRIVATE)

    // الأوقات بساعة «منذ التشغيل» (elapsedRealtime) لأن ساعة شاشات السيارات تقفز عند مزامنة GPS؛ ورقم العملية يميّز العملية الحالية عن السابقة
    fun opened(c: Context) {
        sp(c).edit().putBoolean("open", true).putInt("pid", android.os.Process.myPid())
            .putLong("beatE", android.os.SystemClock.elapsedRealtime()).apply()
    }
    fun beat(c: Context) { sp(c).edit().putLong("beatE", android.os.SystemClock.elapsedRealtime()).apply() }
    fun closed(c: Context) { sp(c).edit().putBoolean("open", false).apply() }

    /** هل قُتل التطبيق أثناء الاستراحة خلال آخر 6 ساعات؟ (يوقف الفيديو لتخفيف الحمل) */
    fun killedRecently(c: Context): Boolean =
        System.currentTimeMillis() - c.getSharedPreferences("watchdog", Context.MODE_PRIVATE).getLong("restKill", 0L) < 6 * 3600_000L

    /** الذاكرة المتاحة قليلة: نعرض صورة بدل الفيديو (نفس حد استراحة الويب) */
    fun lowMemory(c: Context): Boolean = try {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        mi.lowMemory || mi.availMem < 380L * 1048576
    } catch (e: Exception) { false }

    fun check(c: Context, relaunch: Boolean) {
        try {
            val s = sp(c)
            if (!s.getBoolean("open", false)) return
            // العلامة كتبتها هذه العملية نفسها (ما زالت حية): لا شيء حدث، حتى لو اختفت الاستراحة من الشاشة
            if (s.getInt("pid", -1) == android.os.Process.myPid()) return
            val nowE = android.os.SystemClock.elapsedRealtime()
            val beatE = s.getLong("beatE", 0L)
            s.edit().putBoolean("open", false).apply()
            // إعادة تشغيل الجهاز (إطفاء السيارة) أو علامة قديمة جداً: ليست قتلاً حديثاً من النظام
            if (beatE <= 0L || nowE < beatE || nowE - beatE > 30 * 60_000L) return
            val ageMs = nowE - beatE
            val age = ageMs / 1000L
            c.getSharedPreferences("watchdog", Context.MODE_PRIVATE).edit().putLong("restKill", System.currentTimeMillis()).apply()
            CrashLog.add(c, "rest", "أغلق النظام التطبيق أثناء الاستراحة الخفيفة (بعد $age ث من آخر نبضة). سيُستبدل الفيديو بصورة لمدة 6 ساعات.")
            Events.add(c, "أغلق النظام التطبيق أثناء الاستراحة (بعد $age ث من آخر نبضة)")
            if (!relaunch || ageMs > FRESH_MS || !RestGuard.enabled(c) || MainActivity.alive) return
            val now = System.currentTimeMillis()
            var n = s.getInt("rl_n", 0)
            if (now - s.getLong("rl_since", 0L) > 10 * 60_000L) { n = 0; s.edit().putLong("rl_since", now).apply() }
            if (n >= 3) { Events.add(c, "تعذّرت إعادة فتح الاستراحة: تكرر القتل 3 مرات خلال 10 دقائق"); return }
            s.edit().putInt("rl_n", n + 1).apply()
            Events.add(c, "إعادة فتح الاستراحة بعد قتل التطبيق")
            c.startActivity(Intent(c, RestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {
            CrashLog.add(c, "rest", "RestState.check: " + e)
        }
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
    fun alive(c: Context) {
        val s = sp(c); val now = System.currentTimeMillis()
        val bootAt = now - android.os.SystemClock.elapsedRealtime()
        val last = s.getLong("alive", 0L)
        // أول تسجيل بعد التشغيل: نحفظ آخر وقت قبل الإطفاء
        val e = s.edit().putLong("alive", now)
        if (last in 1 until bootAt) e.putLong("aliveBefore", last)
        e.apply()
    }

    /** عند ظهور الواجهة: إن كانت الشاشة شُغّلت للتو وكانت الملاحة مفتوحة قبل الإطفاء، نفتحها */
    fun checkOnBoot(a: Activity): Boolean {
        if (checked) return false
        checked = true
        val s = sp(a); val pkg = s.getString("pkg", null) ?: return false
        if (android.os.SystemClock.elapsedRealtime() > 10 * 60_000L) return false
        // آخر وقت كانت الشاشة تعمل «قبل» هذا التشغيل (وقت الإطفاء)، لا الوقت الحالي
        val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val a1 = s.getLong("alive", 0L)
        val alive = if (a1 in 1 until bootAt) a1 else s.getLong("aliveBefore", 0L)
        if (alive == 0L || alive > bootAt || bootAt - alive > 30 * 60_000L) return false
        // لا نغطي الاستراحة أو صفحة طُلبت
        if (RestGuard.restOpen) return false
        if (!Prefs.config(a).optBoolean("resumeNav", true)) return false
        val i = a.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Handler(Looper.getMainLooper()).postDelayed({ try { a.startActivity(i); Events.add(a, "استكمال الملاحة بعد التشغيل: $pkg") } catch (e: Exception) {} }, 2500)
        return true
    }
}
