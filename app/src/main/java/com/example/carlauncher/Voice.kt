package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * الأوامر الصوتية: تحويل الكلام لنص عبر خدمة النظام (Google غالباً)، والرد بالصوت.
 * لا يعمل شيء إلا إذا فعّله المستخدم على هذه الشاشة، والتفعيل محفوظ هنا (لا يُنقل بالنسخة الاحتياطية).
 * الاستماع بزر: بضع ثوانٍ ثم تُحرَّر الخدمة من الذاكرة فوراً.
 * الاستماع الدائم (اختياري بشروط): يعيد الاستماع تلقائياً ما دامت الواجهة ظاهرة.
 */
object Voice {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var state = "idle"      // idle | listening | speaking | done | error
    @Volatile private var partial = ""
    @Volatile private var results = JSONArray()
    @Volatile private var err = 0
    @Volatile private var seq = 0
    @Volatile private var rms = 0f
    @Volatile private var wake = false
    @Volatile private var wakeMode = false     // الجلسة الحالية من الاستماع الدائم؟
    @Volatile private var foreground = true
    @Volatile private var offline = false
    private var appCtx: Context? = null

    fun sp(c: Context) = c.getSharedPreferences("voice", Context.MODE_PRIVATE)
    fun enabled(c: Context) = sp(c).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("on", on).apply()
        if (!on) { setWake(c, false); stop() }
    }
    fun keySeen(c: Context) { sp(c).edit().putBoolean("key", true).apply() }

    fun caps(c: Context): String {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val o = JSONObject()
            .put("recognizer", try { SpeechRecognizer.isRecognitionAvailable(c) } catch (e: Exception) { false })
            .put("mic", c.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            .put("perm", ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            .put("ramGb", Math.round(mi.totalMem / 1073741824.0 * 10) / 10.0)
            .put("tts", sp(c).getString("tts", "unknown"))
            .put("key", sp(c).getBoolean("key", false))
            .put("on", enabled(c)).put("wake", wake)
            .put("google", try { c.packageManager.getPackageInfo("com.google.android.googlequicksearchbox", 0); true } catch (e: Exception) { false })
        return o.toString()
    }

    fun requestPermission(a: Activity) {
        main.post { try { a.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002) } catch (e: Exception) {} }
    }

    fun stateJson(): String = JSONObject().put("state", state).put("partial", partial).put("results", results)
        .put("err", err).put("seq", seq).put("rms", rms.toDouble()).put("wake", wakeMode).toString()

    fun listen(c: Context, preferOffline: Boolean) {
        appCtx = c.applicationContext; offline = preferOffline
        main.post { startInternal(false) }
    }

    fun stop() {
        main.post {
            try { rec?.cancel() } catch (e: Exception) {}
            try { rec?.destroy() } catch (e: Exception) {}
            rec = null
            if (state == "listening") state = "idle"
        }
    }

    /** الاستماع الدائم: يعمل فقط والواجهة ظاهرة */
    fun setWake(c: Context, on: Boolean) {
        appCtx = c.applicationContext
        wake = on
        if (on && foreground) main.post { if (rec == null) startInternal(true) }
        if (!on) stop()
    }
    fun onForeground(fg: Boolean) {
        foreground = fg
        if (!fg) stop()
        else if (wake) main.postDelayed({ if (wake && rec == null) startInternal(true) }, 800)
    }

    private fun startInternal(isWake: Boolean) {
        val c = appCtx ?: return
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { state = "error"; err = 9; seq++; return }
        val r0: SpeechRecognizer? = try { SpeechRecognizer.createSpeechRecognizer(c) } catch (e: Exception) { null }
        if (r0 == null) { state = "error"; err = 5; seq++; return }
        val r: SpeechRecognizer = r0
        rec = r; wakeMode = isWake
        partial = ""; results = JSONArray(); err = 0; state = "listening"
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { rms = v }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onResults(b: Bundle?) {
                val a = JSONArray(); b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.take(3)?.forEach { a.put(it) }
                results = a; state = "done"; seq++
                release(); again()
            }
            override fun onError(code: Int) {
                err = code; state = "error"; seq++
                release(); again(if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == SpeechRecognizer.ERROR_CLIENT) 2500 else 500)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName)
        if (offline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try { r.startListening(i) } catch (e: Exception) { state = "error"; err = 5; seq++; release() }
    }

    private fun release() { main.post { try { rec?.destroy() } catch (e: Exception) {}; rec = null } }
    private fun again(delay: Long = 500) { if (wake && foreground) main.postDelayed({ if (wake && foreground && rec == null && state != "speaking") startInternal(true) }, delay) }

    // ===== الرد بالصوت =====
    fun checkTts(c: Context) {
        main.post {
            var t: TextToSpeech? = null
            t = TextToSpeech(c.applicationContext) { st ->
                val res = if (st != TextToSpeech.SUCCESS) "missing" else {
                    val a = t?.isLanguageAvailable(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (a >= TextToSpeech.LANG_AVAILABLE) "ok" else "nolang"
                }
                sp(c).edit().putString("tts", res).apply()
                try { t?.shutdown() } catch (e: Exception) {}
            }
        }
    }

    fun speak(c: Context, text: String) {
        main.post {
            try { tts?.stop(); tts?.shutdown() } catch (e: Exception) {}
            val wasWake = wake
            if (wasWake) { try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}; rec = null }
            state = "speaking"
            var t: TextToSpeech? = null
            t = TextToSpeech(c.applicationContext) { st ->
                if (st != TextToSpeech.SUCCESS) { state = "idle" } else {
                t?.language = Locale("ar")
                t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { finish() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { finish() }
                    fun finish() {
                        main.post { try { t?.shutdown() } catch (e: Exception) {}; if (tts === t) tts = null; state = "idle"; again(400) }
                    }
                })
                t?.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, null, "r")
                }
            }
            tts = t
        }
    }
}
