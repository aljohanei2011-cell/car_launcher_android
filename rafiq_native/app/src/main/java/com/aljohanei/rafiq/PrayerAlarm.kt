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
 * تنبيهات الصلاة: منبّه واحد فقط في كل مرة (قبل الصلاة بالمدة المختارة ثم عند الأذان)،
 * ويُجدول التالي عند كل تنبيه. بدون خدمة دائمة.
 */
class PrayerAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Prefs.init(c)
        val idx = intent.getIntExtra("idx", -1)
        val early = intent.getBooleanExtra("early", false)
        if (idx in 0..5) {
            val name = Astro.PRAYER_NAMES[idx]
            if (early && Prefs.alertOn("pre")) notify(c, "بعد ${Prefs.preMin} دقائق: صلاة $name", null, true)
            if (!early && Prefs.alertOn("prayer")) notify(c, "حان وقت صلاة $name", "بعد الأذان: " + Duas.AFTER_ADHAN, false)
        }
        schedule(c)
    }

    companion object {
        fun schedule(c: Context) {
            Prefs.init(c)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(c, PrayerAlarm::class.java)
            val now = System.currentTimeMillis()
            val (idx, at) = Astro.nextPrayer(now, Loc.lat(), Loc.lon())
            val preMs = if (Prefs.alertOn("pre")) Prefs.preMin * 60000L else 0L
            val early = preMs > 0 && at - preMs > now + 1000
            i.putExtra("idx", idx).putExtra("early", early)
            val pi = PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (!Prefs.alertOn("prayer") && !Prefs.alertOn("pre")) { am.cancel(pi); return }
            val t = if (early) at - preMs else at
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } catch (e: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, t, pi)
            }
        }

        private fun notify(c: Context, text: String, more: String?, quiet: Boolean) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val silent = quiet || !Prefs.alertOn("sound")
            val ch = if (silent) "prayer_quiet" else "prayer"
            if (Build.VERSION.SDK_INT >= 26) {
                if (silent) {
                    val n = NotificationChannel(ch, "تنبيهات الصلاة بدون صوت", NotificationManager.IMPORTANCE_DEFAULT)
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
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle(text).setContentIntent(open).setAutoCancel(true)
            if (more != null) { b.setContentText(more); b.setStyle(Notification.BigTextStyle().bigText(more)) }
            nm.notify(if (quiet) 21 else 20, b.build())
        }
    }
}

/** الأدعية المدمجة */
object Duas {
    const val RIDE = "سبحان الذي سخّر لنا هذا وما كنا له مقرنين، وإنا إلى ربنا لمنقلبون"
    const val TRAVEL = "اللهم إنا نسألك في سفرنا هذا البر والتقوى، ومن العمل ما ترضى، اللهم هوّن علينا سفرنا هذا واطوِ عنا بُعده"
    const val AFTER_ADHAN = "اللهم رب هذه الدعوة التامة والصلاة القائمة، آتِ محمداً الوسيلة والفضيلة، وابعثه مقاماً محموداً الذي وعدته"
    const val FRIDAY = "قراءة سورة الكهف، والإكثار من الصلاة على النبي ﷺ"
    val DAILY = listOf(
        "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
        "اللهم إني أسألك العفو والعافية في الدنيا والآخرة",
        "يا مقلب القلوب ثبّت قلبي على دينك",
        "اللهم أعنّي على ذكرك وشكرك وحسن عبادتك",
        "ربِّ اغفر لي ولوالديّ",
        "اللهم إني أسألك علماً نافعاً، ورزقاً طيباً، وعملاً متقبلاً",
        "حسبنا الله ونعم الوكيل"
    )
    fun today(): String = DAILY[(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) % DAILY.size]
}
