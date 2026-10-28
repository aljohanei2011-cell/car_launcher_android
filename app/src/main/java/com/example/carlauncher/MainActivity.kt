package com.example.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * شاشة واحدة تعرض واجهة اللانشر من أصول التطبيق (بدون إنترنت).
 * التحجيم إلى حجم الشاشة يتم داخل الصفحات بنسبة min(العرض/1280، الارتفاع/720).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val locationRequestCode = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // الأصول محلية فقط
                allowFileAccess = true
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = false
                // الموقع للصفحة المحلية فقط (عداد الرحلة)
                setGeolocationEnabled(true)
            }
            webViewClient = object : WebViewClient() {
                // منع أي تنقل إلى الإنترنت من داخل التطبيق
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url == null || !url.startsWith("file:///android_asset/")
                }
            }
            webChromeClient = object : WebChromeClient() {
                // تمرير إذن الموقع إلى WebView فقط بعد أن يمنحه المستخدم للتطبيق
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    val granted = hasLocationPermission()
                    callback?.invoke(origin, granted, false)
                }
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, locationPermissions, locationRequestCode)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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

    override fun onBackPressed() {
        // اللانشر هو الشاشة الرئيسية: الرجوع يعود للصفحة السابقة داخله فقط
        if (webView.canGoBack()) webView.goBack()
    }
}
