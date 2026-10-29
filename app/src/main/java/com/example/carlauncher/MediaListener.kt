package com.example.carlauncher

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * الوصول إلى الإشعارات مطلوب لعرض «ما يُشغَّل الآن» والتحكم به.
 * ونستخدمه أيضاً لشيئين فقط، ولا نقرأ نص أي رسالة:
 *  - المكالمة الواردة: اسم المتصل وأزرار الرد/الرفض.
 *  - الرسائل: اسم المرسل واسم التطبيق فقط (للسلامة أثناء القيادة).
 * لا يُحفظ شيء على القرص؛ كل ذلك في الذاكرة ويُمسح عند انتهاء الإشعار.
 */
class MediaListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) { try { Notifs.posted(this, sbn ?: return) } catch (e: Exception) {} }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { try { Notifs.removed(sbn ?: return) } catch (e: Exception) {} }
}

object Notifs {
    private val MSG_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging",
        "com.android.mms", "com.samsung.android.messaging", "com.facebook.orca", "com.snapchat.android")
    private class Call(val key: String, val name: String, val app: String, val actions: List<Pair<String, PendingIntent>>, val at: Long)
    @Volatile private var call: Call? = null
    @Volatile private var msg: JSONObject? = null

    private fun isCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL) return true
        val p = sbn.packageName.lowercase()
        return (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 && (p.contains("dialer") || p.contains("incall") || p.contains("telecom") || (p.contains("bt") && p.contains("phone")))
    }

    fun posted(ctx: android.content.Context, sbn: StatusBarNotification) {
        val n = sbn.notification
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(60) ?: ""
        val app = try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        if (isCall(sbn)) {
            val acts = (n.actions ?: emptyArray()).mapNotNull { a -> a?.actionIntent?.let { (a.title?.toString() ?: "") to it } }.take(3)
            call = Call(sbn.key, title, app, acts, System.currentTimeMillis())
            return
        }
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in MSG_APPS) {
            if (title.isNotEmpty()) msg = JSONObject().put("from", title).put("app", app).put("at", System.currentTimeMillis())
        }
    }

    fun removed(sbn: StatusBarNotification) { if (call?.key == sbn.key) call = null }

    fun json(): String {
        val o = JSONObject()
        call?.let { c ->
            if (System.currentTimeMillis() - c.at < 10 * 60_000) {
                val a = JSONArray(); c.actions.forEach { a.put(it.first) }
                o.put("call", JSONObject().put("name", c.name).put("app", c.app).put("actions", a).put("at", c.at))
            } else call = null
        }
        msg?.let { o.put("msg", it) }
        return o.toString()
    }

    fun action(i: Int): Boolean = try { call?.actions?.getOrNull(i)?.second?.send(); true } catch (e: Exception) { false }
}
