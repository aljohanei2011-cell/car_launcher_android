package com.example.carlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * خادم صغير على الشبكة المحلية فقط (نقطة اتصال الجوال أو واي فاي السيارة).
 * يعمل فقط وشاشة «رفع من الجوال» مفتوحة، ويُغلق تلقائياً بعد 15 دقيقة.
 * كل طلب يجب أن يحمل الرمز السري الموجود في الباركود.
 */
class UploadServer(private val ctx: Context, private val screenW: Int, private val screenH: Int) {

    companion object {
        const val PORT = 8765
        private const val MAX_BYTES = 12 * 1024 * 1024
        private const val LIFETIME = 15 * 60_000L

        fun localIps(): List<String> {
            val out = ArrayList<String>()
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in ni.inetAddresses) {
                        if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) {
                            // واجهة الواي فاي أولاً
                            if (ni.name.startsWith("wlan") || ni.name.startsWith("ap")) out.add(0, a.hostAddress!!)
                            else out.add(a.hostAddress!!)
                        }
                    }
                }
            } catch (e: Exception) {}
            return out.distinct()
        }
    }

    val token: String = run {
        val b = ByteArray(6); SecureRandom().nextBytes(b)
        b.joinToString("") { "%02x".format(it) }
    }
    @Volatile var uploads = 0; private set
    @Volatile var lastType = ""; private set
    @Volatile var lastName = ""; private set
    private fun event(type: String, name: String) { lastType = type; lastName = name; uploads++ }
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val startedAt = System.currentTimeMillis()

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(PORT)
            server = s; running = true
            thread(isDaemon = true, name = "upload-server") {
                while (running) {
                    try {
                        val c = s.accept()
                        thread(isDaemon = true) { handle(c) }
                    } catch (e: Exception) { if (!running) break }
                    if (System.currentTimeMillis() - startedAt > LIFETIME) stop()
                }
            }
            true
        } catch (e: Exception) { running = false; false }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: Exception) {}
        server = null
    }

    val isRunning get() = running

    private fun handle(sock: Socket) {
        sock.use { c ->
            c.soTimeout = 60_000
            val inp = BufferedInputStream(c.getInputStream())
            val out = c.getOutputStream()
            val line = readLine(inp) ?: return
            val parts = line.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            var len = 0L
            while (true) {
                val h = readLine(inp) ?: break
                if (h.isEmpty()) break
                val i = h.indexOf(':')
                if (i > 0 && h.substring(0, i).trim().equals("content-length", true)) len = h.substring(i + 1).trim().toLongOrNull() ?: 0L
            }
            val path = target.substringBefore('?')
            val q = parseQuery(target.substringAfter('?', ""))
            if (q["t"] != token) { send(out, 403, "text/plain; charset=utf-8", "الرمز غير صحيح. امسح الباركود من الشاشة مرة أخرى.".toByteArray()); return }

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = ctx.assets.open("phone/upload.html").use { it.readBytes() }
                    send(out, 200, "text/html; charset=utf-8", html)
                }
                method == "GET" && path == "/log" -> {
                    val t = CrashLog.read(ctx).ifEmpty { "لا توجد أعطال مسجلة." }
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Disposition: attachment; filename=\"rafiq-log.txt\"\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray()); out.write(t.toByteArray(Charsets.UTF_8)); out.flush()
                    event("log", "")
                }
                method == "GET" && path == "/info" -> {
                    val o = JSONObject().put("w", screenW).put("h", screenH)
                    send(out, 200, "application/json", o.toString().toByteArray())
                }
                method == "POST" && path == "/upload" -> {
                    if (len <= 0 || len > MAX_BYTES) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val body = readN(inp, len.toInt())
                    val slot = q["slot"] ?: "home"
                    val name = Images.saveBytes(ctx, slot, body)
                    if (name == null) { send(out, 400, "text/plain", "bad image".toByteArray()); return }
                    event("image", name)
                    ok(out, name)
                }
                // ملف تطبيق APK يُحفظ في مجلد خاص، ويُثبَّت من الشاشة بعد مراجعته
                method == "POST" && path == "/apk" -> {
                    if (len <= 0 || len > 400L * 1024 * 1024) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val raw = (q["name"] ?: "app").substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._\\-]"), "_").take(60).ifEmpty { "app" }
                    val f = File(Apks.dir(ctx), raw + "_" + System.currentTimeMillis() + ".apk")
                    if (!streamToFile(inp, len, f) || !Apks.looksLikeApk(f)) { f.delete(); send(out, 400, "text/plain", "not an apk".toByteArray()); return }
                    event("apk", f.name)
                    ok(out, f.name)
                }
                // صوت تنبيه الصلاة (mp3/ogg/m4a)
                method == "POST" && path == "/sound" -> {
                    if (len <= 0 || len > 25L * 1024 * 1024) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val ext = (q["ext"] ?: "mp3").lowercase().takeIf { it in setOf("mp3", "ogg", "m4a", "aac", "wav", "opus") } ?: "mp3"
                    Sounds.dir(ctx).listFiles()?.forEach { it.delete() }
                    val f = File(Sounds.dir(ctx), "alert.$ext")
                    if (!streamToFile(inp, len, f)) { f.delete(); send(out, 400, "text/plain", "bad".toByteArray()); return }
                    event("sound", f.name)
                    ok(out, f.name)
                }
                // خط من الجوال (ttf / otf / woff / woff2) يبقى على الجهاز فقط
                method == "POST" && path == "/font" -> {
                    if (len <= 0 || len > 20L * 1024 * 1024) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val body = readN(inp, len.toInt())
                    val name = Fonts.save(ctx, q["name"] ?: "font", body)
                    if (name == null) { send(out, 400, "text/plain", "not a font".toByteArray()); return }
                    event("font", name)
                    ok(out, name)
                }
                // رمز GitHub للتحديث: يُلصق في الجوال بدلاً من كتابته على الشاشة
                method == "POST" && path == "/token" -> {
                    if (len <= 0 || len > 4096) { send(out, 400, "text/plain", "bad".toByteArray()); return }
                    val t = String(readN(inp, len.toInt()), Charsets.UTF_8).trim()
                    if (!t.matches(Regex("^[A-Za-z0-9_]{20,255}$"))) { send(out, 400, "text/plain", "bad token".toByteArray()); return }
                    Updater.setToken(ctx, t)
                    event("token", "")
                    ok(out, "")
                }
                // ملاحظة / تذكير / محطة راديو من الجوال (نص JSON صغير)
                method == "POST" && (path == "/note" || path == "/reminder" || path == "/station" || path == "/expense") -> {
                    if (len <= 0 || len > 64 * 1024) { send(out, 400, "text/plain", "bad".toByteArray()); return }
                    val body = String(readN(inp, len.toInt()), Charsets.UTF_8)
                    val j = try { JSONObject(body) } catch (e: Exception) { null }
                    if (j == null) { send(out, 400, "text/plain", "bad json".toByteArray()); return }
                    val type = path.removePrefix("/")
                    Inbox.add(ctx, type, j)
                    event(type, "")
                    ok(out, "")
                }
                method == "GET" && path == "/backup" -> {
                    val f = Backup.file(ctx)
                    if (!f.exists()) { send(out, 404, "text/plain; charset=utf-8", "جهّز النسخة الاحتياطية من الشاشة أولاً".toByteArray()); return }
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${f.length()}\r\n" +
                        "Content-Disposition: attachment; filename=\"rafiq-backup-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.json\"\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray()); f.inputStream().use { it.copyTo(out) }; out.flush()
                    event("backup", f.name)
                }
                method == "POST" && path == "/restore" -> {
                    if (len <= 0 || len > 120L * 1024 * 1024) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val tmp = File(ctx.cacheDir, "restore.json")
                    if (!streamToFile(inp, len, tmp) || !Backup.restore(ctx, tmp, q["scope"] == "ui")) { tmp.delete(); send(out, 400, "text/plain", "bad backup".toByteArray()); return }
                    tmp.delete()
                    event("restore", "")
                    ok(out, "")
                }
                else -> send(out, 404, "text/plain", "not found".toByteArray())
            }
        }
    }

    private fun ok(out: OutputStream, name: String) =
        send(out, 200, "application/json", JSONObject().put("ok", true).put("name", name).toString().toByteArray())

    private fun streamToFile(inp: InputStream, len: Long, f: File): Boolean {
        var left = len
        val buf = ByteArray(64 * 1024)
        f.outputStream().use { o ->
            while (left > 0) {
                val r = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (r < 0) break
                o.write(buf, 0, r); left -= r
            }
        }
        return left == 0L
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').mapNotNull {
            val k = it.substringBefore('=', ""); if (k.isEmpty()) null
            else k to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.toMap()

    private fun readLine(i: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val x = i.read()
            if (x == -1) return if (b.size() == 0) null else b.toString("UTF-8")
            if (x == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            b.write(x)
            if (b.size() > 8192) return null
        }
    }

    private fun readN(i: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n); var off = 0
        while (off < n) { val r = i.read(buf, off, n - off); if (r < 0) break; off += r }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun send(o: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; 413 -> "Too Large"; else -> "Bad Request" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        o.write(head.toByteArray()); o.write(body); o.flush()
    }
}

/** صور المستخدم: الخلفيات وشعار مخصص، محفوظة كملفات داخل التطبيق */
object Images {
    private val SLOTS = setOf("home", "rest", "logo")
    fun dir(c: Context) = File(c.filesDir, "images").apply { mkdirs() }

    fun saveBytes(c: Context, slot: String, data: ByteArray): String? {
        if (slot !in SLOTS || data.size < 12) return null
        val ext = when {
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "jpg"
            data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() -> "png"
            data[0] == 'R'.code.toByte() && data[8] == 'W'.code.toByte() -> "webp"
            else -> return null
        }
        val name = "${slot}_${System.currentTimeMillis()}.$ext"
        File(dir(c), name).writeBytes(data)
        return name
    }

    fun saveDataUrl(c: Context, slot: String, dataUrl: String): String? = try {
        val b64 = dataUrl.substringAfter("base64,", "")
        saveBytes(c, slot, android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
    } catch (e: Exception) { null }

    fun list(c: Context): String {
        val a = JSONArray()
        dir(c).listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
            a.put(JSONObject().put("name", it.name).put("slot", it.name.substringBefore('_')).put("url", "file://" + it.absolutePath))
        }
        return a.toString()
    }

    fun delete(c: Context, name: String) {
        if (name.contains('/') || name.contains("..")) return
        File(dir(c), name).delete()
    }
}

/* خطوط يرفعها المستخدم من جواله: تُحفظ في ملفات التطبيق ولا تدخل في المستودع */
object Fonts {
    fun dir(c: Context) = File(c.filesDir, "fonts").apply { mkdirs() }

    fun save(c: Context, rawName: String, data: ByteArray): String? {
        if (data.size < 64) return null
        fun at(i: Int, ch: Char) = data[i] == ch.code.toByte()
        val ext = when {
            data[0] == 0.toByte() && data[1] == 1.toByte() && data[2] == 0.toByte() && data[3] == 0.toByte() -> "ttf"
            at(0, 't') && at(1, 'r') && at(2, 'u') && at(3, 'e') -> "ttf"
            at(0, 'O') && at(1, 'T') && at(2, 'T') && at(3, 'O') -> "otf"
            at(0, 'w') && at(1, 'O') && at(2, 'F') && at(3, 'F') -> "woff"
            at(0, 'w') && at(1, 'O') && at(2, 'F') && at(3, '2') -> "woff2"
            else -> return null
        }
        val base = rawName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9\\-]"), "-").replace(Regex("-+"), "-").trim('-').take(40).ifEmpty { "font" }
        val name = base + "_" + System.currentTimeMillis() + "." + ext
        File(dir(c), name).writeBytes(data)
        return name
    }

    fun list(c: Context): String {
        val a = JSONArray()
        dir(c).listFiles()?.sortedBy { it.lastModified() }?.forEach {
            a.put(JSONObject().put("file", it.name).put("name", it.name.substringBeforeLast('_').replace('-', ' ')).put("url", "file://" + it.absolutePath))
        }
        return a.toString()
    }

    fun delete(c: Context, name: String) {
        if (name.contains('/') || name.contains("..")) return
        File(dir(c), name).delete()
    }
}
