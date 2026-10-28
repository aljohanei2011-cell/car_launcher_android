package com.example.carlauncher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** حالة البلوتوث والواي فاي والإنترنت (لأيقونات الشريط العلوي) */
object StatusInfo {
    @Suppress("DEPRECATION", "MissingPermission")
    fun read(c: Context): JSONObject {
        val o = JSONObject()
        // البلوتوث
        try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (bt == null) o.put("bt", "none")
            else if (!bt.isEnabled) o.put("bt", "off")
            else {
                // 11 = A2DP_SINK و 16 = HEADSET_CLIENT (شاشات السيارات تستقبل الصوت من الجوال)
                val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 11, 16)
                val connected = profiles.any {
                    try { bt.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED } catch (e: Exception) { false }
                }
                o.put("bt", if (connected) "connected" else "on")
            }
        } catch (e: Exception) { o.put("bt", "unknown") }
        // الواي فاي والإنترنت
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            o.put("wifi", wifi)
            o.put("internet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            if (wifi) {
                val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                o.put("wifiLevel", WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 4))
            }
        } catch (e: Exception) { o.put("wifi", false) }
        return o
    }
}

/** الطقس من Open-Meteo (مجاني بدون مفتاح). يحتاج إنترنت، ونحفظ آخر قراءة */
object Weather {
    private fun sp(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)
    @Volatile private var busy = false

    fun get(c: Context): String = sp(c).getString("data", "{}") ?: "{}"

    fun refresh(c: Context, lat: Double, lng: Double) {
        if (busy) return
        busy = true
        thread {
            try {
                val u = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(java.util.Locale.US, lat, lng) +
                    "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "rafiq-car-launcher")
                if (conn.responseCode != 200) throw Exception("HTTP " + conn.responseCode)
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cur = j.getJSONObject("current")
                val d = j.optJSONObject("daily")
                val o = JSONObject()
                    .put("ok", true).put("at", System.currentTimeMillis()).put("lat", lat).put("lng", lng)
                    .put("temp", cur.optDouble("temperature_2m")).put("code", cur.optInt("weather_code"))
                    .put("wind", cur.optDouble("wind_speed_10m")).put("hum", cur.optInt("relative_humidity_2m"))
                if (d != null) {
                    o.put("max", d.optJSONArray("temperature_2m_max")?.optDouble(0))
                    o.put("min", d.optJSONArray("temperature_2m_min")?.optDouble(0))
                }
                sp(c).edit().putString("data", o.toString()).apply()
            } catch (e: Exception) {
                val old = try { JSONObject(get(c)) } catch (x: Exception) { JSONObject() }
                old.put("lastError", System.currentTimeMillis())
                sp(c).edit().putString("data", old.toString()).apply()
            } finally { busy = false }
        }
    }
}

/** راديو الإنترنت: تشغيل رابط بث مباشر (mp3/aac) */
object Radio {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    @Volatile var state = "idle"; private set   // idle | buffering | playing | error
    @Volatile var name = ""; private set
    @Volatile var url = ""; private set

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    @Synchronized
    fun play(c: Context, u: String, n: String) {
        stop(c)
        if (!(u.startsWith("http://") || u.startsWith("https://"))) { state = "error"; return }
        url = u; name = n; state = "buffering"
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { ch ->
                when (ch) {
                    AudioManager.AUDIOFOCUS_LOSS -> stop(c)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.25f, 0.25f)
                    AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
                }
            }.build()
        focus = req
        am.requestAudioFocus(req)
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(u)
            mp.setOnPreparedListener { it.start(); state = "playing" }
            mp.setOnErrorListener { _, _, _ -> state = "error"; true }
            mp.setOnCompletionListener { state = "idle" }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) { state = "error" }
    }

    @Synchronized
    fun stop(c: Context) {
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        focus?.let { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focus = null
        if (state != "error") state = "idle"
    }

    fun stateJson(): String = JSONObject().put("state", state).put("name", name).put("url", url).toString()
}

/** صندوق وارد: ملاحظات وتذكيرات ومحطات تصل من الجوال، تأخذها الواجهة وتحفظها */
object Inbox {
    private fun sp(c: Context) = c.getSharedPreferences("inbox", Context.MODE_PRIVATE)
    @Synchronized
    fun add(c: Context, type: String, payload: JSONObject) {
        val a = try { JSONArray(sp(c).getString("items", "[]")) } catch (e: Exception) { JSONArray() }
        a.put(JSONObject().put("type", type).put("data", payload).put("at", System.currentTimeMillis()))
        sp(c).edit().putString("items", a.toString()).apply()
    }
    @Synchronized
    fun take(c: Context): String {
        val s = sp(c).getString("items", "[]") ?: "[]"
        sp(c).edit().putString("items", "[]").apply()
        return s
    }
}
