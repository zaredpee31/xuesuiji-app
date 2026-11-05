package com.aoi.xuesuiji

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val homeUrl = "https://weiguzhi1112.github.io/xuesuiji/"
    private val homeHost = "weiguzhi1112.github.io"

    private val backJs = """
        (function(){
        try{
          var q=function(s){return document.querySelector(s);};
          var vis=function(e){return e&&e.offsetParent!==null;};
          if(q('#sh.on')){shClose();return 'ok';}
          if(q('#mpop')){mpClose();return 'ok';}
          if(q('#msbar')){msEnd();return 'ok';}
          if(q('#cc.open')){cc(0);return 'ok';}
          var vc=q('#vcall.on');
          if(vc){var mn=vc.querySelector('[data-cv=min]');if(mn){mn.click();}return 'ok';}
          if(typeof locked!=='undefined'&&locked){return 'home';}
          if(typeof cur==='undefined'||!cur){
            if(typeof editing!=='undefined'&&editing){setEdit(0);return 'ok';}
            return 'home';
          }
          var mf=q('#mu_f.on');
          if(mf){var fb=mf.querySelector('[data-mu=fclose]');if(fb){fb.click();return 'ok';}}
          if(q('#mu_md.on')){muClose();return 'ok';}
          var root=q('#wx.open .v.on')||q('#st.open')||q('#sm.open')||q('#cr.open')||q('#app.open');
          if(root){
            var bs=root.querySelectorAll('[aria-label="返回"],[aria-label="关闭"],[data-a=back],[data-a=sback],[data-a=ofback],[data-mm=back],[data-wb=back],[data-pn=back],[data-mu=pback]');
            for(var i=0;i<bs.length;i++){
              if(vis(bs[i])&&!bs[i].closest('.mufull')){bs[i].click();return 'ok';}
            }
          }
          homeBtn();
          return 'ok';
        }catch(e){return 'home';}
        })()
    """.trimIndent()

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingMedia: PermissionRequest? = null
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var lastHomeBack = 0L

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode != RESULT_OK || data == null) {
            null
        } else {
            data.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
                ?: data.data?.let { arrayOf(it) }
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val mediaLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMedia() }

    private val geoLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        geoCallback?.invoke(geoOrigin, result.values.any { it }, false)
        geoCallback = null
        geoOrigin = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()

        root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, cutout.top, 0, ime.bottom)
            insets
        }

        createWebView()
        webView.clearCache(true)
        webView.loadUrl(homeUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun handleBack() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        webView.evaluateJavascript(backJs) { r ->
            val res = r?.trim('"') ?: ""
            if (res == "ok") return@evaluateJavascript
            val now = System.currentTimeMillis()
            if (now - lastHomeBack < 2000) {
                lastHomeBack = 0
                showMenu()
            } else {
                lastHomeBack = now
                Toast.makeText(this, "再返回一次打开菜单", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                textZoom = 100
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                setGeolocationEnabled(true)
            }
            webViewClient = ShellClient()
            webChromeClient = ShellChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private inner class ShellClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
            }
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            root.removeView(view)
            view.destroy()
            createWebView()
            webView.loadUrl(homeUrl)
            Toast.makeText(this@MainActivity, "页面崩了，已重新加载", Toast.LENGTH_SHORT).show()
            return true
        }
    }

    private inner class ShellChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            return try {
                fileLauncher.launch(params.createIntent())
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (request.origin.host != homeHost) {
                request.deny()
                return
            }
            pendingMedia?.deny()
            pendingMedia = request
            val need = mediaPermsFor(request).filterNot { granted(it) }
            if (need.isEmpty()) grantMedia() else mediaLauncher.launch(need.toTypedArray())
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingMedia == request) pendingMedia = null
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback
        ) {
            if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origin, true, false)
                return
            }
            geoOrigin = origin
            geoCallback = callback
            geoLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun mediaPermsFor(request: PermissionRequest): List<String> =
        request.resources.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }.distinct()

    private fun grantMedia() {
        val request = pendingMedia ?: return
        pendingMedia = null
        val allowed = request.resources.filter {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> granted(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> granted(Manifest.permission.RECORD_AUDIO)
                else -> false
            }
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showMenu() {
        Thread {
            val cache = dirSize(cacheDir)
            val data = dirSize(File(applicationInfo.dataDir, "app_webview"))
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                AlertDialog.Builder(this)
                    .setTitle("缓存 ${mb(cache)}　数据 ${mb(data)}")
                    .setItems(arrayOf("清理缓存", "刷新", "清空全部数据", "退出")) { _, which ->
                        when (which) {
                            0 -> clearCacheOnly()
                            1 -> webView.reload()
                            2 -> confirmClearAll()
                            3 -> finish()
                        }
                    }
                    .show()
            }
        }.start()
    }

    private fun clearCacheOnly() {
        webView.clearCache(true)
        Toast.makeText(this, "缓存清好了", Toast.LENGTH_SHORT).show()
        webView.reload()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("清空全部数据")
            .setMessage("网页里存的所有东西都会删掉，删了就找不回来了。")
            .setPositiveButton("清空") { _, _ -> clearAll() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearAll() {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        webView.clearCache(true)
        webView.clearHistory()
        webView.loadUrl(homeUrl)
        Toast.makeText(this, "已全部清空", Toast.LENGTH_SHORT).show()
    }

    private fun dirSize(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf { dirSize(it) } ?: 0L

    private fun mb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupWindow()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
