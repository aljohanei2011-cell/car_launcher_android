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

    companion object { @Volatile var alive = false }

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
                    runOnUiThread { recreate() }
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
        try { webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false) } catch (e: Exception) {}
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.keepScreenOn = false
        setContentView(webView)
        webView.loadUrl(base + pageFrom(intent, "index.html"))

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
        val p = intent?.getStringExtra("page")
        if (p != null) webView.loadUrl(base + pageFrom(intent, "index.html"))
        else if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) webView.loadUrl(base + "index.html")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode && hasLocationPermission()) TripService.start(this)
    }

    /** إبقاء الشاشة تعمل (شاشة الاستراحة) حتى لا يقاطعها توفير الشاشة في الجهاز */
    fun keepScreenOn(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun clearWebCache() { runOnUiThread { try { webView.clearCache(false) } catch (e: Exception) {} } }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // عند فتح تطبيق آخر (الخرائط مثلاً) نوقف الواجهة مؤقتاً لتوفير المعالج؛ التنبيهات تعمل من الخدمة
    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        Watchdog.setForeground(this, false)
        try { webView.onPause(); webView.pauseTimers() } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        Watchdog.setForeground(this, true)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // اللانشر هو الشاشة الرئيسية: الرجوع يعود للصفحة السابقة داخله فقط
        if (webView.canGoBack()) webView.goBack()
    }
}
