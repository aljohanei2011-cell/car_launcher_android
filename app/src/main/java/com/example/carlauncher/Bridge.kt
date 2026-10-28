package com.example.carlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * الجسر بين الواجهة (HTML) والنظام. الواجهة تناديه باسم Native.
 * كل الدوال تعمل بدون إنترنت.
 */
class Bridge(private val act: MainActivity) {

    companion object {
        fun drawableToDataUrl(d: Drawable, size: Int): String {
            val bmp = if (d is BitmapDrawable && d.bitmap != null) Bitmap.createScaledBitmap(d.bitmap, size, size, true)
            else Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it); d.setBounds(0, 0, size, size); d.draw(c)
            }
            val o = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, o)
            return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
        }
    }

    private val ctx: Context get() = act.applicationContext
    private val iconCache = HashMap<String, String>()
    private var server: UploadServer? = null

    // ===== التطبيقات =====
    @JavascriptInterface
    fun apps(): String {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
        val arr = JSONArray()
        list.filter { it.activityInfo.packageName != ctx.packageName }
            .map { JSONObject().put("pkg", it.activityInfo.packageName).put("label", it.loadLabel(pm).toString()) }
            .distinctBy { it.getString("pkg") }
            .sortedBy { it.getString("label") }
            .forEach { arr.put(it) }
        return arr.toString()
    }

    @JavascriptInterface
    fun appIcon(pkg: String): String {
        iconCache[pkg]?.let { return it }
        return try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val url = drawableToDataUrl(d, 96)
            iconCache[pkg] = url
            url
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun launch(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { act.runOnUiThread { act.startActivity(i) }; true } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun installed(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    // ===== الموسيقى: ما يُشغَّل الآن =====
    private fun controller(): MediaController? {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }

    @JavascriptInterface
    fun media(): String {
        val o = JSONObject()
        try {
            val c = controller()
            o.put("access", true)
            if (c != null) {
                val md = c.metadata
                o.put("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
                o.put("title", md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
                o.put("artist", md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
                o.put("pkg", c.packageName)
                o.put("app", try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString() } catch (e: Exception) { "" })
            }
        } catch (e: SecurityException) {
            o.put("access", false)
        } catch (e: Exception) {
            o.put("access", true)
        }
        return o.toString()
    }

    @JavascriptInterface
    fun mediaCmd(cmd: String) {
        try {
            val t = controller()?.transportControls ?: return
            when (cmd) {
                "play" -> t.play(); "pause" -> t.pause()
                "next" -> t.skipToNext(); "prev" -> t.skipToPrevious()
            }
        } catch (e: Exception) {}
    }

    @JavascriptInterface
    fun openNotificationAccess() {
        act.runOnUiThread {
            try { act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) {}
        }
    }

    // ===== الصور =====
    @JavascriptInterface fun images(): String = Images.list(ctx)
    @JavascriptInterface fun saveImage(slot: String, dataUrl: String): String = Images.saveDataUrl(ctx, slot, dataUrl) ?: ""
    @JavascriptInterface fun deleteImage(name: String) = Images.delete(ctx, name)

    // ===== الرفع من الجوال =====
    @JavascriptInterface
    fun serverStart(): String {
        val dm = act.resources.displayMetrics
        val w = maxOf(dm.widthPixels, dm.heightPixels); val h = minOf(dm.widthPixels, dm.heightPixels)
        val s = server?.takeIf { it.isRunning } ?: UploadServer(ctx, w, h).also { server = it }
        val ok = s.start()
        return JSONObject()
            .put("ok", ok)
            .put("port", UploadServer.PORT)
            .put("token", s.token)
            .put("ips", JSONArray(UploadServer.localIps()))
            .toString()
    }

    @JavascriptInterface fun serverStop() { server?.stop(); server = null }
    @JavascriptInterface fun uploadCount(): Int = server?.uploads ?: 0
    @JavascriptInterface fun uploadLast(): String {
        val s = server ?: return "{}"
        return JSONObject().put("count", s.uploads).put("type", s.lastType).put("name", s.lastName).toString()
    }

    // ===== الشاشة =====
    @JavascriptInterface
    fun screen(): String {
        val dm = act.resources.displayMetrics
        return JSONObject().put("w", maxOf(dm.widthPixels, dm.heightPixels)).put("h", minOf(dm.widthPixels, dm.heightPixels)).toString()
    }

    // ===== الرحلة والتنبيهات =====
    @JavascriptInterface fun trip(): String = TripStore.state(ctx).toString()
    @JavascriptInterface fun tripLog(): String = TripStore.log(ctx)
    @JavascriptInterface fun tripReset() = TripStore.reset(ctx)
    @JavascriptInterface fun setOdometer(km: Double) = TripStore.setOdometer(ctx, km)
    @JavascriptInterface fun setConfig(json: String) = Prefs.setConfig(ctx, json)
    @JavascriptInterface fun setPrayers(json: String) = Prefs.setPrayers(ctx, json)
    @JavascriptInterface fun hasLocation(): Boolean = act.hasLocationPermission()

    // ===== الإصدار والتحديث =====
    @JavascriptInterface
    fun version(): String = JSONObject().put("code", BuildConfig.VERSION_CODE).put("name", BuildConfig.VERSION_NAME).toString()
    @JavascriptInterface fun hasToken(): Boolean = Updater.hasToken(ctx)
    @JavascriptInterface fun setToken(t: String): Boolean {
        if (!t.trim().matches(Regex("^[A-Za-z0-9_]{20,255}$"))) return false
        Updater.setToken(ctx, t); return true
    }
    @JavascriptInterface fun clearToken() = Updater.clearToken(ctx)
    @JavascriptInterface fun updateCheck() = Updater.check(ctx)
    @JavascriptInterface fun updateDownload() = Updater.download(ctx)
    @JavascriptInterface fun updateState(): String = Updater.state()

    // ===== تثبيت التطبيقات المرفوعة =====
    @JavascriptInterface fun apks(): String = Apks.list(ctx)
    @JavascriptInterface fun installApk(name: String): Boolean {
        val f = Apks.file(ctx, name) ?: return false
        Installer.install(ctx, f); return true
    }
    @JavascriptInterface fun deleteApk(name: String) { Apks.file(ctx, name)?.delete() }
    @JavascriptInterface fun installStatus(): String = Installer.status
    @JavascriptInterface fun clearInstallStatus() { Installer.status = "" }
    @JavascriptInterface fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()
    @JavascriptInterface fun openInstallSettings() {
        act.runOnUiThread {
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName)))
            } catch (e: Exception) {}
        }
    }

    // ===== النسخ الاحتياطي =====
    @JavascriptInterface fun backupPrepare(webJson: String): Boolean = Backup.prepare(ctx, webJson)
    @JavascriptInterface fun pendingRestore(): String = Backup.takePendingWeb(ctx)

    // ===== صوت التنبيه =====
    @JavascriptInterface fun hasSound(): Boolean = Sounds.current(ctx) != null
    @JavascriptInterface fun deleteSound() { Sounds.dir(ctx).listFiles()?.forEach { it.delete() } }
    @JavascriptInterface fun testSound() = TripService.testSound(ctx)

    // ===== التشغيل =====
    /** رقم ثابت لكل تشغيل للشاشة: لتنفيذ «عند التشغيل» مرة واحدة فقط */
    @JavascriptInterface fun bootId(): Long = (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()) / 120_000L
    @JavascriptInterface fun mediaKey(cmd: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val code = when (cmd) { "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY; "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE; else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE }
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    }

    // ===== الإضاءة الليلية =====
    @JavascriptInterface fun canWriteSettings(): Boolean = Settings.System.canWrite(ctx)
    @JavascriptInterface fun openWriteSettings() {
        act.runOnUiThread {
            try { act.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))) } catch (e: Exception) {}
        }
    }
    /** ليلاً: نحفظ إضاءة النهار ثم نخفضها. نهاراً: نعيدها كما كانت */
    @JavascriptInterface
    fun applyNight(night: Boolean, nightPct: Int) {
        if (!Settings.System.canWrite(ctx)) return
        val sp = Prefs.sp(ctx)
        val cr = ctx.contentResolver
        val was = sp.getBoolean("isNightBright", false)
        try {
            if (night && !was) {
                sp.edit().putInt("dayBright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 200))
                    .putInt("dayMode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0))
                    .putBoolean("isNightBright", true).apply()
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (nightPct.coerceIn(5, 100) * 255 / 100))
            } else if (night && was) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (nightPct.coerceIn(5, 100) * 255 / 100))
            } else if (!night && was) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("dayBright", 200))
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("dayMode", 0))
                sp.edit().putBoolean("isNightBright", false).apply()
            }
        } catch (e: Exception) {}
    }
}
