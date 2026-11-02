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
    @Volatile private var rmsMax = -100f
    /** تخصيص: اللهجة، ومدة الانتظار، وصوت الرد (السرعة والطبقة ومستوى الصوت) */
    @Volatile var opts = JSONObject()
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
            .put("services", JSONArray().apply { services(c).forEach { put(it.packageName) } })
            .put("google", try { c.packageManager.getPackageInfo("com.google.android.googlequicksearchbox", 0); true } catch (e: Exception) { false })
        return o.toString()
    }

    fun requestPermission(a: Activity) {
        main.post { try { a.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002) } catch (e: Exception) {} }
    }

    fun stateJson(): String = JSONObject().put("state", state).put("partial", partial).put("results", results)
        .put("err", err).put("seq", seq).put("rms", rms.toDouble()).put("rmsMax", rmsMax.toDouble()).put("wake", wakeMode).toString()

    /** «اضغط وتكلّم»: عند رفع الإصبع ننهي الاستماع ونأخذ ما قيل */
    fun stopListening() { main.post { try { rec?.stopListening() } catch (e: Exception) {} } }

    /** بديل: نافذة Google نفسها للتعرف على الكلام (تستخدم مسار الصوت الخاص بها) */
    fun startUi(a: Activity, launch: (Intent) -> Unit) {
        appCtx = a.applicationContext
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}
        rec = null
        partial = ""; results = JSONArray(); err = 0; state = "listening"; rmsMax = -100f
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, opts.optString("lang", "ar-SA"))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم الآن")
        try { launch(i) } catch (e: Exception) { state = "error"; err = 5; seq++ }
    }
    fun uiResult(list: ArrayList<String>?) {
        if (list.isNullOrEmpty()) { state = "error"; err = 6; seq++; return }
        val a = JSONArray(); list.take(3).forEach { a.put(it) }
        results = a; state = "done"; seq++
    }

    fun listen(c: Context, preferOffline: Boolean) {
        appCtx = c.applicationContext; offline = preferOffline
        main.post { startInternal(false, sp(c).getInt("alt", 0)) }
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

    private fun startInternal(isWake: Boolean, alt: Int = 0) {
        val t0 = System.currentTimeMillis()
        val c = appCtx ?: return
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { state = "error"; err = 9; seq++; return }
        // المحاولة الأولى: خدمة الكلام الافتراضية للشاشة (كالإصدار الأول).
        // إذا رفضت فوراً بدون أن تسمع: نجرب خدمة Google بالاسم، ثم بإعدادات لغة أبسط
        // قائمة المحاولات: (الخدمة، اللغة). اللغة الفارغة = لغة الشاشة نفسها
        val lang = opts.optString("lang", "ar-SA")
        val tries = ArrayList<Pair<android.content.ComponentName?, String>>()
        tries.add(null to lang); tries.add(null to "")
        services(c).sortedBy { if (it.packageName == "com.google.android.googlequicksearchbox") 0 else if (it.packageName.startsWith("com.google.")) 1 else 2 }
            .forEach { tries.add(it to lang); tries.add(it to "") }
        val last = tries.size - 1
        val pick = tries[alt.coerceAtMost(last)]
        val comp: android.content.ComponentName? = pick.first
        val r0: SpeechRecognizer? = try { if (comp != null) SpeechRecognizer.createSpeechRecognizer(c, comp) else SpeechRecognizer.createSpeechRecognizer(c) } catch (e: Exception) { null }
        if (r0 == null) { if (!isWake && alt < last) { startInternal(false, alt + 1); return }; state = "error"; err = 5; seq++; return }
        val r: SpeechRecognizer = r0
        rec = r; wakeMode = isWake
        partial = ""; results = JSONArray(); err = 0; state = "listening"; rmsMax = -100f
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { rms = v; if (v > rmsMax) rmsMax = v }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            }
            override fun onResults(b: Bundle?) {
                if (!isWake) sp(c).edit().putInt("alt", alt).apply()
                val a = JSONArray(); b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.take(3)?.forEach { a.put(it) }
                results = a; state = "done"; seq++
                release(); again()
            }
            override fun onError(code: Int) {
                // رفض فوري بدون أي صوت: نجرب الطريقة البديلة قبل إظهار الخطأ للمستخدم
                val quick = System.currentTimeMillis() - t0 < 3000 && rmsMax <= -99f
                if (!isWake && quick && alt < last && code != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    Events.add(c, "أمر صوتي: رفض فوري رمز " + code + " · " + (comp?.packageName ?: "افتراضية") + " · لغة " + pick.second.ifEmpty { "الشاشة" } + "، أجرب طريقة أخرى")
                    try { r.destroy() } catch (e: Exception) {}
                    if (rec === r) rec = null
                    main.postDelayed({ startInternal(false, alt + 1) }, 250)
                    return
                }
                // لم يصل للميكروفون أي صوت إطلاقاً (غالباً تطبيق آخر يمسكه أو الصوت يذهب للبلوتوث)
                val noAudio = (code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_AUDIO) && rmsMax < 1f
                err = if (noAudio && !isWake) 100 else code; state = "error"; seq++
                if (!isWake) sp(c).edit().remove("alt").apply()
                if (!isWake) Events.add(c, "أمر صوتي: خطأ " + code + " بعد " + (System.currentTimeMillis() - t0) + "ms محاولة " + (alt + 1) + (if (noAudio) " (لم يصل صوت للميكروفون)" else "") + " · الخدمة: " + (comp?.packageName ?: "افتراضية"))
                release(); again(if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == SpeechRecognizer.ERROR_CLIENT) 2500 else 500)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (pick.second.isNotEmpty()) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, pick.second).putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, pick.second)
        if (offline) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        // مدة الصبر قبل انتهاء الاستماع (قصيرة/متوسطة/طويلة)
        val wait = if (isWake) 0L else opts.optLong("waitMs", 0L)
        if (wait > 0) {
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, wait)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, wait)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (opts.optBoolean("hold")) 30_000L else 2_000L)
        }
        try { r.startListening(i) } catch (e: Exception) { state = "error"; err = 5; seq++; release() }
    }

    /** خدمات التعرف على الكلام في الجهاز، مع تفضيل Google */
    fun services(c: Context): List<android.content.ComponentName> = try {
        c.packageManager.queryIntentServices(Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0)
            .map { android.content.ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    } catch (e: Exception) { emptyList() }
    private fun googleService(c: Context): android.content.ComponentName? {
        val all = services(c)
        return all.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
            ?: all.firstOrNull { it.packageName.startsWith("com.google.") }
    }

    /** نغمة قصيرة من أندرويد نفسه، تُغلق فور انتهائها (لا يبقى مسار صوت مفتوح) */
    fun tone(up: Boolean) {
        main.post {
            try {
                val g = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 45)
                g.startTone(if (up) android.media.ToneGenerator.TONE_PROP_BEEP else android.media.ToneGenerator.TONE_PROP_ACK, 150)
                main.postDelayed({ try { g.release() } catch (e: Exception) {} }, 400)
            } catch (e: Exception) {}
        }
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
                try { t?.setSpeechRate(opts.optDouble("rate", 1.0).toFloat()); t?.setPitch(opts.optDouble("pitch", 1.0).toFloat()) } catch (e: Exception) {}
                t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { finish() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { finish() }
                    fun finish() {
                        main.post { try { t?.shutdown() } catch (e: Exception) {}; if (tts === t) tts = null; state = "idle"; again(400) }
                    }
                })
                val pb = Bundle(); pb.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, opts.optDouble("vol", 1.0).toFloat())
                t?.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, pb, "r")
                }
            }
            tts = t
        }
    }
}
