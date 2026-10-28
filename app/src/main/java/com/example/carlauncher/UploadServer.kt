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
            c.soTimeout = 30_000
            val inp = BufferedInputStream(c.getInputStream())
            val out = c.getOutputStream()
            val line = readLine(inp) ?: return
            val parts = line.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            var len = 0
            while (true) {
                val h = readLine(inp) ?: break
                if (h.isEmpty()) break
                val i = h.indexOf(':')
                if (i > 0 && h.substring(0, i).trim().equals("content-length", true)) len = h.substring(i + 1).trim().toIntOrNull() ?: 0
            }
            val path = target.substringBefore('?')
            val q = parseQuery(target.substringAfter('?', ""))
            if (q["t"] != token) { send(out, 403, "text/plain; charset=utf-8", "الرمز غير صحيح. امسح الباركود من الشاشة مرة أخرى.".toByteArray()); return }

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val html = ctx.assets.open("phone/upload.html").use { it.readBytes() }
                    send(out, 200, "text/html; charset=utf-8", html)
                }
                method == "GET" && path == "/info" -> {
                    val o = JSONObject().put("w", screenW).put("h", screenH)
                    send(out, 200, "application/json", o.toString().toByteArray())
                }
                method == "POST" && path == "/upload" -> {
                    if (len <= 0 || len > MAX_BYTES) { send(out, 413, "text/plain", "too large".toByteArray()); return }
                    val body = readN(inp, len)
                    val slot = q["slot"] ?: "home"
                    val name = Images.saveBytes(ctx, slot, body)
                    if (name == null) { send(out, 400, "text/plain", "bad image".toByteArray()); return }
                    uploads++
                    send(out, 200, "application/json", JSONObject().put("ok", true).put("name", name).toString().toByteArray())
                }
                else -> send(out, 404, "text/plain", "not found".toByteArray())
            }
        }
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
