package com.aljohanei.rafiq

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * التحديث من داخل اللانشر: فحص صغير للإصدار التجريبي الثابت rafiq-native،
 * ثم تحميل الملف وتثبيته بـ PackageInstaller (أندرويد يطلب تأكيدك مرة واحدة).
 */
object Updater {
    private const val API = "https://api.github.com/repos/aljohanei2011-cell/car_launcher_android/releases/tags/rafiq-native"

    class Info(val build: Int, val notes: String, val size: Long, val url: String)

    fun current(c: Context): Int = try {
        @Suppress("DEPRECATION")
        c.packageManager.getPackageInfo(c.packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    fun onWifi(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (e: Exception) { false }

    /** يعيد معلومات الإصدار إن كان أحدث من المثبّت، وإلا null */
    fun check(c: Context): Info? {
        return try {
            val j = Net.get(API)
            val body = j.optString("body")
            val m = Regex("(\\d+)").find(body.substringAfter("آخر بناء:", "")) ?: return null
            val build = m.groupValues[1].toInt()
            val assets = j.optJSONArray("assets") ?: return null
            var url = ""; var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); size = a.optLong("size") }
            }
            Prefs.putLong("updChecked", System.currentTimeMillis())
            if (url.isEmpty() || build <= current(c)) null
            else Info(build, body.substringAfter("—", "").trim(), size, url)
        } catch (e: Exception) { null }
    }

    /** يحتاج سماح «تثبيت التطبيقات غير المعروفة» لهذا التطبيق مرة واحدة */
    fun canInstall(c: Context): Boolean = Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun askPermission(c: Context) {
        try {
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {}
    }

    /** تحميل ثم تثبيت. يُستدعى من خيط خلفي. يعيد رسالة خطأ أو null */
    fun install(c: Context, info: Info): String? {
        val f = File(c.cacheDir, "update.apk")
        try {
            var conn = URL(info.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(loc).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                hops++
            }
            if (conn.responseCode != 200) return "تعذّر التحميل (${conn.responseCode})"
            conn.inputStream.use { inp -> f.outputStream().use { out -> inp.copyTo(out) } }
            conn.disconnect()

            val pi = c.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(c.packageName)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                f.inputStream().use { inp ->
                    s.openWrite("rafiq.apk", 0, f.length()).use { out -> inp.copyTo(out); s.fsync(out) }
                }
                val cb = PendingIntent.getBroadcast(c, 9, Intent(c, InstallReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
                s.commit(cb.intentSender)
            }
            return null
        } catch (e: Exception) {
            return "تعذّر التحديث: " + (e.message ?: "")
        } finally {
            try { f.delete() } catch (e: Exception) {}
        }
    }
}

/** نتيجة التثبيت: عند الحاجة لتأكيدك يفتح أندرويد نافذة التثبيت */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            try { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }
    }
}
