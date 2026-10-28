package com.example.carlauncher

import android.content.Context
import android.content.Intent
import android.os.Build
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
