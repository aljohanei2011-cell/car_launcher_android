package com.aljohanei.rafiq

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** أذكار الصباح والمساء: نصوص مدمجة صغيرة، وعدّاد يُحفظ لكل فترة */
object Azkar {
    class Zikr(val text: String, val count: Int)

    private val COMMON_END = listOf(
        Zikr("رضيت بالله ربًا، وبالإسلام دينًا، وبمحمد ﷺ نبيًا", 3),
        Zikr("بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", 3),
        Zikr("حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم", 7),
        Zikr("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", 10),
        Zikr("سبحان الله وبحمده", 100),
        Zikr("أستغفر الله وأتوب إليه", 100)
    )
    val MORNING = listOf(
        Zikr("أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له", 1),
        Zikr("اللهم بك أصبحنا، وبك أمسينا، وبك نحيا، وبك نموت، وإليك النشور", 1)
    ) + COMMON_END
    val EVENING = listOf(
        Zikr("أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له", 1),
        Zikr("اللهم بك أمسينا، وبك أصبحنا، وبك نحيا، وبك نموت، وإليك المصير", 1),
        Zikr("أعوذ بكلمات الله التامات من شر ما خلق", 3)
    ) + COMMON_END

    /** الصباح من الفجر حتى العصر، والمساء بعده */
    fun isMorning(now: Long): Boolean {
        val t = Astro.prayerMillis(java.util.Calendar.getInstance().apply { timeInMillis = now }, Prefs.lat, Prefs.lon)
        return now >= t[0] && now < t[3]
    }

    private fun key(now: Long): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now - 4 * 3600000L))
        return (if (isMorning(now)) "m" else "e") + day
    }

    fun list(now: Long) = if (isMorning(now)) MORNING else EVENING

    /** [رقم الذكر، العدد الحالي] */
    fun state(now: Long): IntArray {
        val k = key(now)
        if (Prefs.str("azKey") != k) { Prefs.putStr("azKey", k); Prefs.putInt("azIdx", 0); Prefs.putInt("azCnt", 0) }
        return intArrayOf(Prefs.int("azIdx"), Prefs.int("azCnt"))
    }

    /** ضغطة: يزيد العدّاد، وعند الاكتمال ينتقل للذكر التالي */
    fun tap(now: Long) {
        val s = state(now); val l = list(now)
        if (s[0] >= l.size) return
        val c = s[1] + 1
        if (c >= l[s[0]].count) { Prefs.putInt("azIdx", s[0] + 1); Prefs.putInt("azCnt", 0) } else Prefs.putInt("azCnt", c)
    }

    /** ضغطة طويلة: تخطّي إلى الذكر التالي (أو البدء من جديد بعد الانتهاء) */
    fun skip(now: Long) {
        val s = state(now); val l = list(now)
        Prefs.putInt("azIdx", if (s[0] >= l.size) 0 else s[0] + 1); Prefs.putInt("azCnt", 0)
    }
}
