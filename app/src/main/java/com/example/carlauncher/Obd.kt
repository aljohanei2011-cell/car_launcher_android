package com.example.carlauncher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * قارئ OBD-II عبر قطعة ELM327 بلوتوث (الكلاسيكية SPP).
 * يقرأ: دورات المحرك، السرعة، حرارة المحرك، جهد البطارية، مستوى الوقود، حمل المحرك، وأكواد الأعطال.
 * يعمل في خيط مستقل، ويتوقف عند الإيقاف أو انقطاع الاتصال. لا يرسل أي أمر يغيّر شيئاً في السيارة
 * إلا «مسح الأعطال» عند طلبه صراحة.
 */
@SuppressLint("MissingPermission")
object Obd {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    @Volatile private var sock: BluetoothSocket? = null
    @Volatile private var running = false
    @Volatile private var state = "off"          // off | connecting | on | error
    @Volatile private var err = ""
    @Volatile private var devName = ""
    @Volatile private var pendingClear = false
    @Volatile private var pendingDtc = false
    private val vals = JSONObject()
    @Volatile private var dtcs = JSONArray()
    @Volatile private var dtcAt = 0L

    private fun sp(c: Context) = c.getSharedPreferences("obd", Context.MODE_PRIVATE)

    fun devices(): String {
        val a = JSONArray()
        try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.forEach { d ->
                a.put(JSONObject().put("name", d.name ?: d.address).put("addr", d.address)
                    .put("likely", (d.name ?: "").let { n -> n.contains("OBD", true) || n.contains("ELM", true) || n.contains("V-LINK", true) || n.contains("VLINK", true) || n.contains("KONNWEI", true) || n.contains("Vgate", true) }))
            }
        } catch (e: Exception) {}
        return a.toString()
    }

    fun json(c: Context): String = synchronized(vals) {
        JSONObject(vals.toString()).put("state", state).put("err", err).put("dev", devName)
            .put("saved", sp(c).getString("addr", "") ?: "").put("dtcs", dtcs).put("dtcAt", dtcAt).toString()
    }

    fun start(c: Context, addr: String) {
        if (running) stop()
        sp(c).edit().putString("addr", addr).apply()
        running = true; state = "connecting"; err = ""
        thread(name = "obd") { loop(addr) }
    }

    fun stop() {
        running = false
        try { sock?.close() } catch (e: Exception) {}
        sock = null; state = "off"
    }

    fun readDtc() { pendingDtc = true }
    fun clearDtc() { pendingClear = true }

    private fun loop(addr: String) {
        try {
            val ad = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("لا يوجد بلوتوث")
            if (!ad.isEnabled) throw Exception("البلوتوث مُطفأ")
            val dev = ad.getRemoteDevice(addr)
            devName = dev.name ?: addr
            ad.cancelDiscovery()
            val s = try { dev.createRfcommSocketToServiceRecord(SPP).also { it.connect() } }
            catch (e: Exception) {
                // بعض القطع الرخيصة تحتاج القناة 1 مباشرة
                val m = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                (m.invoke(dev, 1) as BluetoothSocket).also { it.connect() }
            }
            sock = s
            val inp = s.inputStream; val out = s.outputStream
            for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) { cmdRaw(inp, out, cmd, if (cmd == "ATZ") 2500 else 1500) }
            // أول طلب يحدد بروتوكول السيارة (قد يأخذ ثواني)
            cmdRaw(inp, out, "0100", 8000)
            state = "on"
            var tick = 0
            while (running) {
                if (pendingClear) { pendingClear = false; cmdRaw(inp, out, "04", 4000); dtcs = JSONArray(); dtcAt = System.currentTimeMillis() }
                if (pendingDtc || tick % 60 == 0) { pendingDtc = false; dtcs = parseDtc(cmdRaw(inp, out, "03", 4000)); dtcAt = System.currentTimeMillis() }
                val rpm = pid(inp, out, "010C") { b -> if (b.size >= 2) (b[0] * 256 + b[1]) / 4.0 else null }
                val spd = pid(inp, out, "010D") { b -> b.getOrNull(0)?.toDouble() }
                val cool = if (tick % 3 == 0) pid(inp, out, "0105") { b -> b.getOrNull(0)?.let { it - 40.0 } } else null
                val load = if (tick % 3 == 1) pid(inp, out, "0104") { b -> b.getOrNull(0)?.let { it * 100.0 / 255 } } else null
                val fuel = if (tick % 10 == 2) pid(inp, out, "012F") { b -> b.getOrNull(0)?.let { it * 100.0 / 255 } } else null
                val volt = if (tick % 5 == 3) cmdRaw(inp, out, "ATRV", 1500).replace("V", "").trim().toDoubleOrNull() else null
                synchronized(vals) {
                    rpm?.let { vals.put("rpm", Math.round(it)) }; spd?.let { vals.put("speed", Math.round(it)) }
                    cool?.let { vals.put("coolant", Math.round(it)) }; load?.let { vals.put("load", Math.round(it)) }
                    fuel?.let { vals.put("fuel", Math.round(it)) }; volt?.let { vals.put("volt", it) }
                    vals.put("at", System.currentTimeMillis())
                }
                tick++
                Thread.sleep(700)
            }
        } catch (e: Exception) {
            if (running) { state = "error"; err = e.message ?: "تعذّر الاتصال" }
        } finally {
            try { sock?.close() } catch (x: Exception) {}
            sock = null
            if (state != "error") state = "off"
            running = false
        }
    }

    private fun pid(inp: InputStream, out: OutputStream, cmd: String, f: (IntArray) -> Double?): Double? {
        val r = cmdRaw(inp, out, cmd, 1500).replace(" ", "")
        val head = "4" + cmd.substring(1)
        val i = r.indexOf(head); if (i < 0) return null
        val hex = r.substring(i + head.length)
        val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }.take(4).toIntArray()
        return try { f(bytes) } catch (e: Exception) { null }
    }

    private fun parseDtc(raw: String): JSONArray {
        val a = JSONArray()
        val r = raw.replace(" ", "").replace("\r", "").replace("\n", "")
        val i = r.indexOf("43"); if (i < 0) return a
        var hex = r.substring(i + 2)
        // بعض البروتوكولات تضع عدد الأكواد أولاً
        if (hex.length % 4 == 2) hex = hex.substring(2)
        hex.chunked(4).forEach { c ->
            if (c.length == 4 && c != "0000" && c.all { ch -> ch.isLetterOrDigit() }) {
                val v = c.toIntOrNull(16) ?: return@forEach
                val sys = "PCBU"[(v shr 14) and 3]
                a.put(sys.toString() + ((v shr 12) and 3).toString() + String.format("%03X", v and 0xFFF))
            }
        }
        return a
    }

    private fun cmdRaw(inp: InputStream, out: OutputStream, cmd: String, timeout: Long): String {
        while (inp.available() > 0) inp.read()
        out.write((cmd + "\r").toByteArray()); out.flush()
        val sb = StringBuilder(); val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < timeout) {
            if (inp.available() > 0) {
                val ch = inp.read().toChar()
                if (ch == '>') break
                sb.append(ch)
            } else Thread.sleep(15)
        }
        val s = sb.toString().replace("SEARCHING...", "").trim()
        if (s.contains("UNABLE TO CONNECT") || s.contains("CAN ERROR")) throw Exception("القطعة لم تتصل بالسيارة (شغّل المحرك)")
        return s
    }
}
