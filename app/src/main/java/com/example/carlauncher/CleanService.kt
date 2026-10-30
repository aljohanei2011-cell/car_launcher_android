package com.example.carlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * خدمة اختيارية (إمكانية الوصول) لمسح قائمة التطبيقات الأخيرة بضغطة واحدة مثل الجوال:
 * تفتح «الأخيرة» وتضغط «مسح الكل» ثم ترجع للواجهة. لا تقرأ ولا تحفظ أي محتوى،
 * ولا تستقبل أي أحداث؛ تعمل فقط عند الضغط على زر «إغلاق التطبيقات».
 */
class CleanService : AccessibilityService() {
    companion object {
        @Volatile var instance: CleanService? = null
        /** idle | busy | done | notfound */
        @Volatile var result = "idle"
        private val LABELS = listOf("مسح الكل", "إغلاق الكل", "اغلاق الكل", "مسح الجميع", "إزالة الكل", "حذف الكل", "تنظيف الكل",
            "clear all", "close all", "dismiss all", "remove all", "clear", "مسح", "تنظيف")

        fun enabled(c: Context): Boolean {
            if (instance != null) return true
            val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.contains(c.packageName + "/")
        }
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    private var lastWin = ""
    /** نسجّل ما يظهر فوق الواجهة (اسم التطبيق فقط) لمعرفة سبب خروج الاستراحة، ونعيدها إن ظهرت قائمة الأخيرة بدون لمس */
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = (e.packageName ?: "").toString(); val cls = (e.className ?: "").toString()
        if (pkg.isEmpty() || pkg == packageName) return
        val w = "$pkg/$cls"
        if (w == lastWin || result == "busy") return
        lastWin = w
        if (cls.startsWith("android.widget") || cls.startsWith("android.view")) return
        Events.add(this, "ظهر: " + pkg.substringAfterLast('.') + " (" + cls.substringAfterLast('.') + ")")
        if (RestGuard.isSystemShell(pkg, cls) && RestGuard.shouldReturn(this)) {
            h.postDelayed({ if (RestGuard.shouldReturn(this)) RestGuard.bringBack(this, "ظهرت قائمة النظام بدون لمس") }, 1500)
        }
    }
    override fun onInterrupt() {}

    fun clearRecents() {
        result = "busy"
        h.post {
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) { result = "notfound"; return@post }
            tryClear(0)
        }
    }

    private fun tryClear(attempt: Int) {
        h.postDelayed({
            val root = try { rootInActiveWindow } catch (e: Exception) { null }
            val btn = root?.let { find(it, 0) }
            if (btn != null && clickUp(btn)) {
                result = "done"
                h.postDelayed({ goHome() }, 900)
            } else if (attempt < 3) {
                // في بعض الشاشات يظهر الزر بعد السحب لآخر القائمة
                root?.let { scrollAll(it, 0) }
                tryClear(attempt + 1)
            } else {
                result = "notfound"
                goHome()
            }
        }, if (attempt == 0) 900L else 600L)
    }

    /** نرجع للواجهة ونتأكد أنها ظهرت فعلاً، وإلا نعيد المحاولة */
    private fun goHome(attempt: Int = 0) {
        if (attempt == 0) Events.add(this, "إغلاق التطبيقات: " + (if (result == "done") "مُسحت القائمة" else "لم يوجد زر مسح الكل"))
        if (attempt == 0) performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            startActivity(android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {}
        h.postDelayed({
            if (!MainActivity.visible && attempt < 2) goHome(attempt + 1)
            else if (!MainActivity.visible) Events.add(this, "تعذّر الرجوع للواجهة بعد الإغلاق")
        }, 1500)
    }

    private fun label(n: AccessibilityNodeInfo): String =
        ((n.text ?: "").toString() + " " + (n.contentDescription ?: "").toString()).trim().lowercase()

    /** نبحث أولاً عن العبارة الكاملة، ثم بمعرّف الزر (clear_all) */
    private fun find(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 25) return null
        val t = label(n)
        val id = (n.viewIdResourceName ?: "").lowercase()
        if (t.isNotEmpty() && LABELS.any { t == it || (it.length > 5 && t.contains(it)) }) return n
        if (id.contains("clear_all") || id.contains("clearall") || id.contains("clear_recent") || id.contains("dismiss_all")) return n
        for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; find(c, depth + 1)?.let { return it } }
        return null
    }

    private fun clickUp(n: AccessibilityNodeInfo?): Boolean {
        var x = n; var k = 0
        while (x != null && k < 5) { if (x.isClickable && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true; x = x.parent; k++ }
        return false
    }

    private fun scrollAll(n: AccessibilityNodeInfo, depth: Int) {
        if (depth > 20) return
        if (n.isScrollable) { n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        for (i in 0 until n.childCount) n.getChild(i)?.let { scrollAll(it, depth + 1) }
    }
}
