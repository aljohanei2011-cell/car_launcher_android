package com.aljohanei.rafiq

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * تنبيهات الصلاة: منبّه واحد فقط في كل مرة (قبل الصلاة بعشر دقائق ثم عند الأذان)،
 * ويُجدول التالي عند كل تنبيه. بدون خدمة دائمة.
 */
class PrayerAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Prefs.init(c)
        val idx = intent.getIntExtra("idx", -1)
        val early = intent.getBooleanExtra("early", false)
        if (idx in 0..5 && Prefs.prayerAlerts) {
            val name = Astro.PRAYER_NAMES[idx]
            notify(c, if (early) "بعد 10 دقائق: صلاة $name" else "حان وقت صلاة $name", early)
        }
        schedule(c)
    }

    companion object {
        private const val EARLY_MS = 10 * 60 * 1000L

        fun schedule(c: Context) {
            Prefs.init(c)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(c, PrayerAlarm::class.java)
            val now = System.currentTimeMillis()
            val (idx, at) = Astro.nextPrayer(now, Prefs.lat, Prefs.lon)
            val early = at - EARLY_MS > now + 1000
            i.putExtra("idx", idx).putExtra("early", early)
            val pi = PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (!Prefs.prayerAlerts) { am.cancel(pi); return }
            val t = if (early) at - EARLY_MS else at
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } catch (e: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, t, pi)
            }
        }

        private fun notify(c: Context, text: String, quiet: Boolean) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = if (quiet) "prayer_soon" else "prayer"
            if (Build.VERSION.SDK_INT >= 26) {
                if (quiet) {
                    val n = NotificationChannel(ch, "قبل الصلاة", NotificationManager.IMPORTANCE_DEFAULT)
                    n.setSound(null, null)
                    nm.createNotificationChannel(n)
                } else {
                    val n = NotificationChannel(ch, "الأذان", NotificationManager.IMPORTANCE_HIGH)
                    n.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                    nm.createNotificationChannel(n)
                }
            }
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, ch) else Notification.Builder(c)
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle("رفيق الطريق").setContentText(text)
                .setContentIntent(open).setAutoCancel(true)
            nm.notify(if (quiet) 21 else 20, b.build())
        }
    }
}
