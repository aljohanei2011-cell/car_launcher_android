package com.example.carlauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/** تثبيت ملف APK عبر مثبّت النظام. أندرويد يطلب تأكيدك في كل مرة. */
object Installer {
    @Volatile var status: String = ""   // installing | need_user | ok | fail:<سبب>

    fun install(ctx: Context, f: File) {
        status = "installing"
        thread {
            try {
                val pi = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    s.openWrite("base.apk", 0, f.length()).use { o ->
                        f.inputStream().use { it.copyTo(o) }
                        s.fsync(o)
                    }
                    var flags = PendingIntent.FLAG_UPDATE_CURRENT
                    if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
                    val pend = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
                    s.commit(pend.intentSender)
                }
            } catch (e: Exception) {
                status = "fail:" + (e.message ?: "تعذّر بدء التثبيت")
            }
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Installer.status = "need_user"
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(confirm) } catch (e: Exception) { Installer.status = "fail:تعذّر فتح نافذة التثبيت" }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.status = "ok"
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installer.status = "fail:أُلغي التثبيت"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> Installer.status = "fail:تعارض مع نسخة مثبتة بتوقيع مختلف. احذف النسخة القديمة أولاً"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Installer.status = "fail:التطبيق غير متوافق مع هذه الشاشة"
            PackageInstaller.STATUS_FAILURE_STORAGE -> Installer.status = "fail:المساحة غير كافية"
            else -> Installer.status = "fail:" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "فشل التثبيت")
        }
    }
}

/** ملفات APK المرفوعة من الجوال */
object Apks {
    fun dir(c: Context) = File(c.filesDir, "apks").apply { mkdirs() }

    fun looksLikeApk(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
    } catch (e: Exception) { false }

    private fun certHash(sigs: Array<android.content.pm.Signature>?): String? {
        if (sigs.isNullOrEmpty()) return null
        val md = MessageDigest.getInstance("SHA-256")
        sigs.map { it.toByteArray() }.sortedBy { Base64.encodeToString(it, Base64.NO_WRAP) }.forEach { md.update(it) }
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }

    @Suppress("DEPRECATION")
    fun inspect(c: Context, f: File, withIcon: Boolean): JSONObject {
        val o = JSONObject().put("name", f.name).put("size", f.length())
        val pm = c.packageManager
        val info = pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_SIGNATURES)
        if (info == null) return o.put("valid", false)
        val ai = info.applicationInfo
        if (ai != null) {
            ai.sourceDir = f.absolutePath; ai.publicSourceDir = f.absolutePath
            o.put("label", ai.loadLabel(pm).toString())
            o.put("minSdk", ai.minSdkVersion)
            if (withIcon) try { o.put("icon", Bridge.drawableToDataUrl(ai.loadIcon(pm), 96)) } catch (e: Exception) {}
        }
        o.put("valid", true).put("pkg", info.packageName).put("version", info.versionName ?: "").put("code", info.longVersionCode)
        o.put("self", info.packageName == c.packageName)
        o.put("compatible", (ai?.minSdkVersion ?: 0) <= Build.VERSION.SDK_INT)
        val newSig = certHash(info.signingInfo?.apkContentsSigners ?: info.signatures)
        try {
            val cur = pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            o.put("installed", true).put("installedVersion", cur.versionName ?: "").put("installedCode", cur.longVersionCode)
            val curSig = certHash(cur.signingInfo?.apkContentsSigners)
            o.put("sameSigner", if (newSig == null || curSig == null) JSONObject.NULL else newSig == curSig)
        } catch (e: PackageManager.NameNotFoundException) {
            o.put("installed", false)
        }
        return o
    }

    fun list(c: Context): String {
        val a = JSONArray()
        dir(c).listFiles()?.filter { it.name.endsWith(".apk") }?.sortedByDescending { it.lastModified() }?.forEach {
            try { a.put(inspect(c, it, true)) } catch (e: Exception) { a.put(JSONObject().put("name", it.name).put("valid", false)) }
        }
        return a.toString()
    }

    fun file(c: Context, name: String): File? {
        if (name.contains('/') || name.contains("..")) return null
        return File(dir(c), name).takeIf { it.exists() }
    }
}

/** صوت تنبيه الصلاة الذي يرفعه المستخدم */
object Sounds {
    fun dir(c: Context) = File(c.filesDir, "sounds").apply { mkdirs() }
    fun current(c: Context): File? = dir(c).listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
}

/**
 * التحديث من الشاشة: يقرأ آخر إصدار من GitHub (المستودع خاص، لذلك يحتاج رمز قراءة فقط)،
 * يحمّل ملف APK، ويتأكد أنه رفيق الطريق نفسه وبنفس التوقيع، ثم يفتح نافذة التثبيت.
 */
object Updater {
    private fun sp(c: Context) = c.getSharedPreferences("secure", Context.MODE_PRIVATE)
    fun setToken(c: Context, t: String) { sp(c).edit().putString("gh_token", t.trim()).apply() }
    fun clearToken(c: Context) { sp(c).edit().remove("gh_token").apply() }
    fun hasToken(c: Context) = !sp(c).getString("gh_token", null).isNullOrEmpty()
    /** المستودع العام لا يحتاج رمزاً؛ الرمز اختياري للمستودع الخاص */
    private fun token(c: Context): String? = sp(c).getString("gh_token", null)?.takeIf { it.isNotEmpty() }

    @Volatile private var state = JSONObject().put("phase", "idle")
    @Volatile private var assetId = 0L
    fun state(): String = state.toString()

    private fun set(vararg kv: Pair<String, Any?>) {
        val o = JSONObject(state.toString())
        kv.forEach { o.put(it.first, it.second ?: JSONObject.NULL) }
        state = o
    }

    private fun open(url: String, auth: String?, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "rafiq-car-launcher")
        if (auth != null) c.setRequestProperty("Authorization", "Bearer $auth")
        return c
    }

    private fun httpError(code: Int): String = when (code) {
        401 -> "رمز GitHub غير صالح أو انتهت صلاحيته. أرسل رمزاً جديداً من الجوال."
        403 -> "GitHub رفض الطلب (صلاحية الرمز أو تجاوز عدد المحاولات). جرّب لاحقاً."
        404 -> "لم يُعثر على الإصدارات. إذا كان المستودع خاصاً أرسل رمز قراءة من الجوال، أو اجعل المستودع عاماً."
        else -> "خطأ من GitHub ($code)"
    }

    fun check(c: Context) {
        set("phase" to "checking", "error" to null)
        thread {
            try {
                val conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", token(c), "application/vnd.github+json")
                val code = conn.responseCode
                if (code != 200) { set("phase" to "error", "error" to httpError(code)); return@thread }
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val latest = j.optString("tag_name").removePrefix("v").toIntOrNull() ?: 0
                val assets = j.optJSONArray("assets") ?: JSONArray()
                var asset: JSONObject? = null
                for (i in 0 until assets.length()) if (assets.getJSONObject(i).optString("name").endsWith(".apk")) asset = assets.getJSONObject(i)
                if (asset == null) { set("phase" to "error", "error" to "الإصدار لا يحتوي على ملف APK."); return@thread }
                assetId = asset.getLong("id")
                set("latest" to latest, "current" to BuildConfig.VERSION_CODE, "notes" to j.optString("body"),
                    "size" to asset.optLong("size"), "date" to j.optString("published_at"),
                    "phase" to if (latest > BuildConfig.VERSION_CODE) "available" else "none")
            } catch (e: IOException) {
                set("phase" to "error", "error" to "لا يوجد اتصال بالإنترنت. اربط الشاشة بنقطة اتصال الجوال ثم حاول مرة أخرى.")
            } catch (e: Exception) {
                set("phase" to "error", "error" to (e.message ?: "خطأ غير متوقع"))
            }
        }
    }

    fun download(c: Context) {
        if (assetId == 0L) { check(c); return }
        set("phase" to "downloading", "progress" to 0, "error" to null)
        thread {
            val f = File(File(c.filesDir, "update").apply { mkdirs() }, "update.apk")
            try {
                var conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/assets/$assetId", token(c), "application/octet-stream")
                var code = conn.responseCode
                var hops = 0
                // GitHub يحوّل إلى رابط تحميل مؤقت؛ نتبعه بدون إرسال الرمز
                while (code in 301..308 && hops < 5) {
                    val loc = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    conn = open(loc, null, "application/octet-stream")
                    code = conn.responseCode; hops++
                }
                if (code != 200) { set("phase" to "error", "error" to httpError(code)); return@thread }
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { inp ->
                    f.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val r = inp.read(buf); if (r < 0) break
                            o.write(buf, 0, r); done += r
                            if (total > 0) set("progress" to (done * 100 / total).toInt())
                        }
                    }
                }
                val info = Apks.inspect(c, f, false)
                if (!info.optBoolean("self")) { f.delete(); set("phase" to "error", "error" to "الملف المحمَّل ليس رفيق الطريق."); return@thread }
                if (info.opt("sameSigner") == false) { f.delete(); set("phase" to "error", "error" to "توقيع التحديث مختلف عن المثبت. ثبّت النسخة الجديدة يدوياً مرة واحدة بعد حذف القديمة."); return@thread }
                set("phase" to "installing", "progress" to 100)
                Installer.install(c, f)
            } catch (e: IOException) {
                set("phase" to "error", "error" to "انقطع الاتصال أثناء التحميل. حاول مرة أخرى.")
            } catch (e: Exception) {
                set("phase" to "error", "error" to (e.message ?: "خطأ غير متوقع"))
            }
        }
    }
}

/** نسخة احتياطية كاملة: إعدادات الواجهة، الرحلات، الصيانة، الصور، صوت التنبيه */
object Backup {
    fun file(c: Context) = File(c.filesDir, "backup.json")
    private fun pending(c: Context) = File(c.filesDir, "pending_web.json")
    private val PREF_FILES = listOf("launcher", "trip")

    private fun dumpPrefs(c: Context, name: String): JSONObject {
        val o = JSONObject()
        for ((k, v) in c.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
            val t = when (v) { is String -> "s"; is Long -> "l"; is Int -> "i"; is Float -> "f"; is Boolean -> "b"; else -> null } ?: continue
            o.put(k, JSONObject().put("t", t).put("v", v))
        }
        return o
    }

    private fun filesB64(dir: File): JSONArray {
        val a = JSONArray()
        dir.listFiles()?.filter { it.isFile }?.forEach {
            a.put(JSONObject().put("name", it.name).put("b64", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)))
        }
        return a
    }

    fun prepare(c: Context, webJson: String): Boolean = try {
        val o = JSONObject()
        o.put("app", "rafiq").put("v", 1).put("created", System.currentTimeMillis()).put("version", BuildConfig.VERSION_CODE)
        o.put("web", JSONObject(webJson))
        val sp = JSONObject(); PREF_FILES.forEach { sp.put(it, dumpPrefs(c, it)) }
        o.put("prefs", sp)
        o.put("images", filesB64(Images.dir(c)))
        o.put("sounds", filesB64(Sounds.dir(c)))
        file(c).writeText(o.toString())
        true
    } catch (e: Exception) { false }

    fun restore(c: Context, f: File): Boolean = try {
        val o = JSONObject(f.readText())
        if (o.optString("app") != "rafiq") throw IllegalArgumentException()
        val sp = o.optJSONObject("prefs") ?: JSONObject()
        for (name in PREF_FILES) {
            val m = sp.optJSONObject(name) ?: continue
            val e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in m.keys()) {
                val x = m.getJSONObject(k)
                when (x.getString("t")) {
                    "s" -> e.putString(k, x.getString("v")); "l" -> e.putLong(k, x.getLong("v"))
                    "i" -> e.putInt(k, x.getInt("v")); "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                    "b" -> e.putBoolean(k, x.getBoolean("v"))
                }
            }
            e.apply()
        }
        fun writeFiles(arr: JSONArray?, dir: File) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i); val n = x.getString("name")
                if (n.contains('/') || n.contains("..")) continue
                File(dir, n).writeBytes(Base64.decode(x.getString("b64"), Base64.DEFAULT))
            }
        }
        writeFiles(o.optJSONArray("images"), Images.dir(c))
        Sounds.dir(c).listFiles()?.forEach { it.delete() }
        writeFiles(o.optJSONArray("sounds"), Sounds.dir(c))
        pending(c).writeText((o.optJSONObject("web") ?: JSONObject()).toString())
        true
    } catch (e: Exception) { false }

    /** إعدادات الواجهة المستعادة تنتظر أن تكتبها الصفحة في تخزينها */
    fun takePendingWeb(c: Context): String {
        val p = pending(c)
        if (!p.exists()) return ""
        val s = p.readText(); p.delete(); return s
    }
}
