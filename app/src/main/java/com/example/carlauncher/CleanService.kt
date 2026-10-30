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
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
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

    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            startActivity(android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) {}
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
