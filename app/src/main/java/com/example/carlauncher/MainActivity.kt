package com.example.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * شاشة واحدة تعرض واجهة اللانشر من أصول التطبيق (بدون إنترنت).
 * التحجيم إلى حجم الشاشة يتم داخل الصفحات بنسبة min(العرض/1280، الارتفاع/720).
 */
class MainActivity : AppCompatActivity() {

    companion object { @Volatile var alive = false; @Volatile var visible = false }
    private var resumed = false
    private var needRebuild = false

    private lateinit var webView: WebView
    private val base = "file:///android_asset/"

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val locationRequestCode = 1001

    // اختيار صورة من ذاكرة الجهاز (زر «صورة من الجهاز» في الإعدادات)
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        alive = true
        Events.add(this, "تشغيل الواجهة" + (if (savedInstanceState != null) " (بعد إغلاقها من النظام)" else ""))
        hideSystemBars()

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // الأصول المحلية + صور المستخدم المحفوظة داخل التطبيق
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = false
                setGeolocationEnabled(true)
            }
            addJavascriptInterface(Bridge(this@MainActivity), "Native")
            webViewClient = object : WebViewClient() {
                // منع أي تنقل إلى الإنترنت من داخل التطبيق
                // إذا توقف محرك الصفحات (نقص ذاكرة مثلاً) نعيد بناء الواجهة بدل أن يُغلق التطبيق
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    CrashLog.add(this@MainActivity, "webview", "توقف محرك الصفحات" + (if (detail?.didCrash() == true) " (عطل)" else " (أغلقه النظام لنقص الذاكرة)"))
                    try { (view?.parent as? android.view.ViewGroup)?.removeView(view); view?.destroy() } catch (e: Exception) {}
                    // إن كانت الواجهة ظاهرة نعيد بناءها الآن، وإلا ننتظر حتى يعود المستخدم إليها
                    if (resumed) runOnUiThread { recreate() } else needRebuild = true
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url == null || !url.startsWith(base)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, hasLocationPermission(), false)
                }

                override fun onShowFileChooser(
                    view: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    return try {
                        pickImage.launch("image/*")
                        true
                    } catch (e: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }
        }
        // أولوية عالية لمحرك الصفحات حتى لا يُغلقه النظام عند ضغط الذاكرة
        // مهم وهو ظاهر، ويخفّ عن النظام عندما يكون تطبيق آخر في المقدمة
        try { webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true) } catch (e: Exception) {}
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // نوقف الضغط المطوّل الافتراضي (تحديد النص) حتى تصل الضغطة المطوّلة للواجهة (تحرير البطاقات)
        webView.setOnLongClickListener { true }
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.keepScreenOn = false
        // تشغيل أسرع: آخر شكل للرئيسية يظهر فوراً كصورة، ثم تحل محله الواجهة الحية حين تجهز
        val root = android.widget.FrameLayout(this)
        root.addView(webView, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        showSnapshot(root)
        webView.loadUrl(base + pageFrom(intent, "index.html"))
        if (intent?.action == Intent.ACTION_VOICE_COMMAND && Voice.enabled(this))
            webView.postDelayed({ webView.evaluateJavascript("window.onVoiceKey && window.onVoiceKey()", null) }, 3000)

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, locationPermissions, locationRequestCode)
        } else {
            TripService.start(this)
        }
    }

    /** فتح صفحة معيّنة عند الضغط على إشعار (مثل الصلاة أو الاستراحة) */
    private fun pageFrom(i: Intent?, def: String): String {
        val p = i?.getStringExtra("page") ?: return def
        return if (p.matches(Regex("^[a-z]+\\.html(\\?[a-z=]+)?$"))) p else def
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // زر الأوامر الصوتية في الشاشة (إن اختير رفيق الطريق لها)
        if (intent?.action == Intent.ACTION_VOICE_COMMAND) {
            if (Voice.enabled(this)) { Voice.keySeen(this); webView.evaluateJavascript("window.onVoiceKey && window.onVoiceKey()", null) }
            return
        }
        val p = intent?.getStringExtra("page")
        if (p != null) webView.loadUrl(base + pageFrom(intent, "index.html"))
        else if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) webView.loadUrl(base + "index.html")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode && hasLocationPermission()) TripService.start(this)
        if (requestCode == 1002) webView.evaluateJavascript("window.onResumeNative && window.onResumeNative()", null)
    }

    /** إبقاء الشاشة تعمل (شاشة الاستراحة) حتى لا يقاطعها توفير الشاشة في الجهاز */
    fun keepScreenOn(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun askLocation() { runOnUiThread { ActivityCompat.requestPermissions(this, locationPermissions, locationRequestCode) } }

    fun clearWebCache() { runOnUiThread { try { webView.clearCache(false) } catch (e: Exception) {} } }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ===== صورة آخر شكل للرئيسية =====
    private var snapView: android.widget.ImageView? = null
    @Volatile var overlayOpen = false
    private fun snapFile() = java.io.File(filesDir, "snap.jpg")
    private fun showSnapshot(root: android.widget.FrameLayout) {
        try {
            val f = snapFile()
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86_400_000L) return
            val bm = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return
            val iv = android.widget.ImageView(this)
            iv.scaleType = android.widget.ImageView.ScaleType.FIT_XY
            iv.setImageBitmap(bm)
            root.addView(iv, android.widget.FrameLayout.LayoutParams(-1, -1))
            snapView = iv
            iv.postDelayed({ pageReady() }, 5000) // احتياط إن تأخرت الواجهة
        } catch (e: Exception) {}
    }
    /** تستدعيها الواجهة عندما تجهز: نخفي الصورة بتلاشٍ سريع */
    fun pageReady() {
        runOnUiThread {
            val iv = snapView ?: return@runOnUiThread
            snapView = null
            iv.animate().alpha(0f).setDuration(220).withEndAction { (iv.parent as? android.view.ViewGroup)?.removeView(iv) }.start()
        }
    }
    /** نلتقط شكل الرئيسية (بنصف الدقة) عند الخروج منها */
    private fun saveSnapshot() {
        try {
            val w = webView.width, h = webView.height
            if (w < 100 || h < 100) return
            val bm = android.graphics.Bitmap.createBitmap(w / 2, h / 2, android.graphics.Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bm); c.scale(0.5f, 0.5f); webView.draw(c)
            // صورة سوداء تقريباً (بعض الشاشات لا تسمح بالالتقاط) لا نحفظها
            var sum = 0L; var n = 0
            var y = 0; while (y < bm.height) { var x = 0; while (x < bm.width) { val p = bm.getPixel(x, y); sum += (p shr 16 and 255) + (p shr 8 and 255) + (p and 255); n++; x += 37 }; y += 29 }
            if (n == 0 || sum / (n * 3) < 6) { bm.recycle(); return }
            val tmp = java.io.File(filesDir, "snap.tmp")
            java.io.FileOutputStream(tmp).use { bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            tmp.renameTo(snapFile())
            bm.recycle()
        } catch (e: Exception) {}
    }

    // عند فتح تطبيق آخر (الخرائط مثلاً) نوقف الواجهة مؤقتاً لتوفير المعالج؛ التنبيهات تعمل من الخدمة
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Events.add(this, "النظام يطلب تخفيف الذاكرة (المستوى $level)")
    }

    override fun onDestroy() {
        Events.add(this, "إغلاق الواجهة" + (if (isFinishing) "" else " من النظام"))
        alive = false
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        visible = false
        if (snapView == null && !overlayOpen) saveSnapshot()
        RestGuard.pausedAt = System.currentTimeMillis()
        Events.add(this, "اختفت الواجهة" + (if (RestGuard.restOpen) " والاستراحة مفتوحة" else "") +
            (if (System.currentTimeMillis() - RestGuard.lastTouch < 4000) " بعد لمس" else " بدون لمس"))
        Watchdog.setForeground(this, false)
        Voice.onForeground(false)
        try { webView.onPause(); webView.pauseTimers() } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        visible = true
        Events.add(this, "ظهرت الواجهة")
        Alerts.hideOverlay()
        if (needRebuild) { needRebuild = false; recreate(); return }
        Watchdog.setForeground(this, true)
        Voice.onForeground(true)
        try { webView.resumeTimers(); webView.onResume() } catch (e: Exception) {}
        // تحديث الصفحة الحالية (مثلاً بعد منح الوصول للإشعارات)
        webView.evaluateJavascript("window.onResumeNative && window.onResumeNative()", null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    /** زر الأوامر الصوتية في المقود أو الشاشة (إن كانت الشاشة ترسله للتطبيقات) */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) RestGuard.lastTouch = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        RestGuard.lastTouch = System.currentTimeMillis()
        val voiceKeys = setOf(android.view.KeyEvent.KEYCODE_VOICE_ASSIST, android.view.KeyEvent.KEYCODE_SEARCH, android.view.KeyEvent.KEYCODE_ASSIST)
        if (event.keyCode in voiceKeys && Voice.enabled(this)) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Voice.keySeen(this)
                webView.evaluateJavascript("window.onVoiceKey && window.onVoiceKey()", null)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // اللانشر هو الشاشة الرئيسية: الرجوع يعود للصفحة السابقة داخله فقط
        // إن كانت صفحة مفتوحة فوق الرئيسية تعالج الرجوع بنفسها
        webView.evaluateJavascript("(window.onBack&&window.onBack())?1:0") { r ->
            if (r != "1" && webView.canGoBack()) webView.goBack()
        }
    }
}
