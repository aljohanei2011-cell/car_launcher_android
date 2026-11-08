package com.aljohanei.rafiq

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** درج التطبيقات واختيار تطبيق لخانة في القائمة السفلية */
object Drawer {
    private fun apps(a: Activity): List<ResolveInfo> {
        val pm = a.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != a.packageName }
        return list.sortedBy { it.loadLabel(pm).toString() }
    }

    fun show(a: Activity, pal: Palette, onLaunch: (String) -> Unit) {
        val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val pm = a.packageManager
        val items = apps(a)
        val root = Ui.col(a, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setBackgroundColor(pal.bg); setPadding(24, 16, 24, 16) }
        val top = Ui.row(a, Gravity.CENTER_VERTICAL)
        top.addView(Ui.tv(a, 22f, pal.accent, true, Gravity.CENTER).apply {
            text = "إغلاق"; background = Ui.rounded(pal.card, 12f, 2, pal.accent)
            setPadding(24, 0, 24, 0); setOnClickListener { d.dismiss() }
        }, Ui.lin(Ui.WRAP, 56))
        top.addView(Ui.tv(a, 26f, pal.text, true).apply { text = "التطبيقات" }, Ui.lin(0, 56, 1f))
        root.addView(top, Ui.lin(Ui.MATCH, 64))
        val grid = GridView(a).apply {
            numColumns = 6; verticalSpacing = 12; horizontalSpacing = 12
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            adapter = object : BaseAdapter() {
                override fun getCount() = items.size
                override fun getItem(p: Int): Any = items[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                    val cell = (convert as? LinearLayout) ?: Ui.col(a, Gravity.CENTER).apply {
                        background = Ui.rounded(pal.card, 14f, 2, pal.line)
                        setPadding(6, 10, 6, 8)
                        addView(ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, Ui.lin(64, 64))
                        addView(Ui.tv(a, 16f, pal.text, false, Gravity.CENTER), Ui.lin(Ui.MATCH, 26).apply { topMargin = 6 })
                        layoutParams = android.widget.AbsListView.LayoutParams(Ui.MATCH, 124)
                    }
                    val ri = items[p]
                    (cell.getChildAt(0) as ImageView).setImageDrawable(ri.loadIcon(pm))
                    (cell.getChildAt(1) as TextView).text = ri.loadLabel(pm)
                    return cell
                }
            }
            setOnItemClickListener { _, _, p, _ -> d.dismiss(); onLaunch(items[p].activityInfo.packageName) }
        }
        root.addView(grid, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 12 })
        d.setContentView(root)
        d.show()
    }

    fun pick(a: Activity, title: String, onPick: (String) -> Unit) {
        val pm = a.packageManager
        val items = apps(a)
        val labels = ArrayList<String>()
        labels.add("↺ الافتراضي")
        for (ri in items) labels.add(ri.loadLabel(pm).toString())
        AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, w -> onPick(if (w == 0) "" else items[w - 1].activityInfo.packageName) }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}

/** الإعدادات: قائمة واحدة، وكل خيار يفتح اختياراً بسيطاً */
object SettingsDialog {
    private const val T = android.R.style.Theme_DeviceDefault_Dialog_Alert

    fun show(a: Activity, pal: Palette, onChange: () -> Unit) {
        val themes = arrayOf("auto", "dark", "light", "amber")
        val themeNames = arrayOf("تلقائي حسب الشروق والغروب", "داكن", "فاتح", "كهرماني")
        val gauges = arrayOf("قوس", "حلقة", "شريط")
        val alerts = intArrayOf(0, 80, 100, 110, 120, 140)
        val brands = arrayOf("COROLLA 2015", "TOYOTA", "Corolla", "كورولا 2015", "")
        val items = arrayOf(
            "الثيم: " + themeNames[themes.indexOf(Prefs.theme).coerceAtLeast(0)],
            "شكل العداد: " + gauges[Prefs.gauge.coerceIn(0, 2)],
            "الحد الأقصى للعداد: ${Prefs.maxSpeed}",
            "تنبيه السرعة: " + (if (Prefs.alertSpeed == 0) "متوقف" else "${Prefs.alertSpeed} كم/س"),
            "الشعار: " + (if (Prefs.brand.isEmpty()) "بدون" else Prefs.brand),
            "موضع الشعار: " + (if (Prefs.brandLeft) "يسار" else "يمين"),
            "البطاقات الظاهرة",
            "خفض السطوع ليلاً: " + (if (Prefs.nightDim) "تشغيل" else "إيقاف"),
            "تنبيهات الصلاة: " + (if (Prefs.prayerAlerts) "تشغيل" else "إيقاف"),
            "عرض اسم المقطع (إذن الإشعارات)",
            "اجعله الواجهة الرئيسية",
            "تصفير الرحلة"
        )
        AlertDialog.Builder(a, T).setTitle("الإعدادات").setItems(items) { _, w ->
            when (w) {
                0 -> choose(a, "الثيم", themeNames, themes.indexOf(Prefs.theme)) { Prefs.theme = themes[it]; onChange() }
                1 -> choose(a, "شكل العداد", gauges, Prefs.gauge) { Prefs.gauge = it; onChange() }
                2 -> choose(a, "الحد الأقصى", arrayOf("200", "240"), if (Prefs.maxSpeed == 200) 0 else 1) { Prefs.maxSpeed = if (it == 0) 200 else 240; onChange() }
                3 -> choose(a, "تنبيه السرعة", Array(alerts.size) { if (alerts[it] == 0) "متوقف" else "${alerts[it]} كم/س" }, alerts.indexOf(Prefs.alertSpeed)) {
                    Prefs.alertSpeed = alerts[it]; onChange()
                }
                4 -> choose(a, "الشعار", Array(brands.size) { if (brands[it].isEmpty()) "بدون شعار" else brands[it] }, brands.indexOf(Prefs.brand)) {
                    Prefs.brand = brands[it]; onChange()
                }
                5 -> choose(a, "موضع الشعار", arrayOf("يسار", "يمين"), if (Prefs.brandLeft) 0 else 1) { Prefs.brandLeft = it == 0; onChange() }
                6 -> cards(a, onChange)
                7 -> { Prefs.nightDim = !Prefs.nightDim; onChange() }
                8 -> { Prefs.prayerAlerts = !Prefs.prayerAlerts; onChange() }
                9 -> open(a, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                10 -> open(a, Settings.ACTION_HOME_SETTINGS)
                11 -> { Trip.reset(); onChange() }
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun choose(a: Activity, title: String, names: Array<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(a, T).setTitle(title)
            .setSingleChoiceItems(names, current) { d, w -> d.dismiss(); onPick(w) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun cards(a: Activity, onChange: () -> Unit) {
        val hidden = HashSet(Prefs.hidden())
        val names = Prefs.CARD_NAMES.toTypedArray()
        val checked = BooleanArray(names.size) { Prefs.CARD_IDS[it] !in hidden }
        AlertDialog.Builder(a, T).setTitle("البطاقات الظاهرة")
            .setMultiChoiceItems(names, checked) { _, w, on -> checked[w] = on }
            .setPositiveButton("حفظ") { _, _ ->
                val h = HashSet<String>()
                for (i in checked.indices) if (!checked[i]) h.add(Prefs.CARD_IDS[i])
                Prefs.setHidden(h); onChange()
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun open(a: Activity, action: String) {
        try { a.startActivity(Intent(action)) } catch (e: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) {}
        }
    }
}
