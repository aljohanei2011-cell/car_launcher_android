package com.aljohanei.rafiq

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** درج التطبيقات: شبكة أيقونات مع بحث، والأكثر استخداماً أولاً. يُستخدم للتشغيل ولاختيار تطبيق لخانة */
object Drawer {
    private class App(val label: String, val pkg: String, val ri: ResolveInfo)

    private fun apps(a: Activity): List<App> {
        val pm = a.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != a.packageName }
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it) }
            .sortedWith(compareByDescending<App> { Prefs.usage(it.pkg) }.thenBy { it.label })
    }

    fun show(a: Activity, pal: Palette, title: String, onSelect: (String) -> Unit) {
        val d = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val pm = a.packageManager
        val all = apps(a)
        var items: List<App> = all
        val root = Ui.col(a, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setBackgroundColor(pal.bg); setPadding(24, 16, 24, 16) }
        val top = Ui.row(a, Gravity.CENTER_VERTICAL)
        top.addView(Ui.tv(a, 22f, pal.accent, true, Gravity.CENTER).apply {
            text = "إغلاق"; background = Ui.rounded(pal.card, 12f, 2, pal.accent)
            setPadding(24, 0, 24, 0); setOnClickListener { d.dismiss() }
        }, Ui.lin(Ui.WRAP, 56))
        val search = EditText(a).apply {
            hint = "بحث"; setSingleLine(); setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f)
            setTextColor(pal.text); setHintTextColor(pal.muted); gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            background = Ui.rounded(pal.card, 12f, 2, pal.line); setPadding(16, 0, 16, 0)
        }
        top.addView(search, Ui.lin(0, 56, 1f).apply { leftMargin = 16; rightMargin = 16 })
        top.addView(Ui.tv(a, 24f, pal.text, true).apply { text = title }, Ui.lin(Ui.WRAP, 56))
        root.addView(top, Ui.lin(Ui.MATCH, 64))

        val adapter = object : BaseAdapter() {
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
                val it = items[p]
                (cell.getChildAt(0) as ImageView).setImageDrawable(it.ri.loadIcon(pm))
                (cell.getChildAt(1) as TextView).text = it.label
                return cell
            }
        }
        val grid = GridView(a).apply {
            numColumns = 6; verticalSpacing = 12; horizontalSpacing = 12
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            this.adapter = adapter
            setOnItemClickListener { _, _, p, _ -> d.dismiss(); onSelect(items[p].pkg) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                items = if (q.isEmpty()) all else all.filter { it.label.contains(q, true) }
                adapter.notifyDataSetChanged()
            }
        })
        root.addView(grid, Ui.lin(Ui.MATCH, 0, 1f).apply { topMargin = 12 })
        val (sr, sd) = Ui.scaled(a, pal.bg)
        sd.addView(root, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.NO_GRAVITY))
        d.setContentView(sr)
        d.show()
    }
}
