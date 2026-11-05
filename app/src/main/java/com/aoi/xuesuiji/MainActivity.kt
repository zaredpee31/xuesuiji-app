package com.aoi.xuesuiji

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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

    private val dlJs = """
        (function(){
        if(window.__xsjHook||!window.XsjSaver)return;
        window.__xsjHook=1;
        function post(d,n){XsjSaver.postMessage(JSON.stringify({d:d,n:n||''}));}
        window.__xsjSave=function(h,n){
          try{
            if(h.indexOf('data:')===0){post(h,n);return true;}
            if(h.indexOf('blob:')===0){
              var x=new XMLHttpRequest();
              x.open('GET',h,true);
              x.responseType='blob';
              x.onload=function(){
                var r=new FileReader();
                r.onloadend=function(){post(r.result,n);};
                r.readAsDataURL(x.response);
              };
              x.send();
              return true;
            }
          }catch(e){}
          return false;
        };
        var oc=HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click=function(){
          if(this.hasAttribute('download')&&this.href&&window.__xsjSave(this.href,this.getAttribute('download')))return;
          return oc.apply(this,arguments);
        };
        document.addEventListener('click',function(e){
          var a=e.target&&e.target.closest&&e.target.closest('a[download]');
          if(a&&a.href&&window.__xsjSave(a.href,a.getAttribute('download'))){e.preventDefault();e.stopImmediatePropagation();}
        },true);
        })()
    """.trimIndent()

    private val scrollJs = """
        (function(){
        if(window.__xsjScroll||!document.body)return;
        window.__xsjScroll=1;
        var lastTouch=0,pend=null,timer=0;
        ['touchstart','touchmove','wheel'].forEach(function(t){
          document.addEventListener(t,function(){lastTouch=Date.now();},{passive:true,capture:true});
        });
        function idle(){return Date.now()-lastTouch>=800;}
        function scroller(el){
          while(el&&el!==document.body&&el!==document.documentElement){
            if(el.nodeType===1){
              var o=getComputedStyle(el).overflowY;
              if((o==='auto'||o==='scroll'||o==='overlay')&&el.scrollHeight>el.clientHeight+2)return el;
            }
            el=el.parentElement;
          }
          return null;
        }
        function isChat(s){
          var sr=s.getBoundingClientRect();
          if(sr.height<200)return false;
          var p=s.parentElement;
          for(var i=0;i<5&&p;i++){
            var f=p.querySelectorAll('textarea,input[type=text],input:not([type]),[contenteditable=true]');
            for(var j=0;j<f.length;j++){
              var e=f[j];
              if(s.contains(e)||e.offsetParent===null)continue;
              var r=e.getBoundingClientRect();
              if(r.top>sr.top+sr.height*0.6&&r.bottom>innerHeight*0.6)return true;
            }
            p=p.parentElement;
          }
          return false;
        }
        function nearEnd(n,s){
          var r=n.getBoundingClientRect();
          if(!r.width&&!r.height)return false;
          var sr=s.getBoundingClientRect();
          return r.bottom-sr.top+s.scrollTop>=s.scrollHeight-300;
        }
        function toBottom(s){if(s.isConnected&&idle())s.scrollTop=s.scrollHeight;}
        function run(){
          var list=pend;pend=null;
          if(!list||!idle())return;
          list.forEach(function(s){
            toBottom(s);
            [80,250,600,1200].forEach(function(t){setTimeout(function(){toBottom(s);},t);});
          });
        }
        function check(n){
          var el=n.nodeType===1?n:n.parentElement;
          if(!el||!el.isConnected)return;
          var s=scroller(el);
          while(s){
            if(pend&&pend.indexOf(s)>=0)return;
            if(isChat(s)){
              if(nearEnd(el,s))(pend=pend||[]).push(s);
              return;
            }
            s=scroller(s.parentElement);
          }
        }
        new MutationObserver(function(ms){
          for(var i=0;i<ms.length;i++){
            var m=ms[i];
            if(m.type==='characterData'){check(m.target);continue;}
            var a=m.addedNodes;
            for(var k=a.length-1;k>=0&&k>=a.length-20;k--)check(a[k]);
          }
          if(pend){clearTimeout(timer);timer=setTimeout(run,30);}
        }).observe(document.body,{childList:true,subtree:true,characterData:true});
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
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView, "XsjSaver", setOf("https://$homeHost")
            ) { _, message, _, _, _ ->
                message.data?.let { saveFromJs(it) }
            }
        }
        webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
            when {
                url.startsWith("data:") ->
                    saveDataUrl(url, URLUtil.guessFileName(url, contentDisposition, mimetype))
                url.startsWith("blob:") ->
                    webView.evaluateJavascript(
                        "window.__xsjSave&&window.__xsjSave(${JSONObject.quote(url)},'')", null
                    )
                else -> try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun saveFromJs(raw: String) {
        try {
            val o = JSONObject(raw)
            saveDataUrl(o.optString("d"), o.optString("n"))
        } catch (_: Exception) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDataUrl(url: String, name: String) {
        Thread {
            val where = try {
                val comma = url.indexOf(',')
                require(url.startsWith("data:") && comma > 0)
                val meta = url.substring(5, comma)
                val payload = url.substring(comma + 1)
                val isB64 = meta.endsWith(";base64")
                val mime = meta.removeSuffix(";base64").substringBefore(';')
                    .ifBlank { "application/octet-stream" }
                val bytes = if (isB64) Base64.decode(payload, Base64.DEFAULT)
                else Uri.decode(payload).toByteArray()
                writeDownload(cleanName(name, mime), mime, bytes)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (where != null) "已保存到 $where" else "保存失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun cleanName(name: String, mime: String): String {
        val n = name.substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .trim()
            .take(120)
        if (n.isNotEmpty()) return n
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "雪穗机-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "." + ext
    }

    private fun writeDownload(name: String, mime: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/雪穗机")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("insert failed")
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("open failed")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
            return "下载/雪穗机/$name"
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val f = File(dir, name)
        f.writeBytes(bytes)
        return f.absolutePath
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

        override fun onPageFinished(view: WebView, url: String) {
            if (Uri.parse(url).host == homeHost) {
                view.evaluateJavascript(dlJs, null)
                view.evaluateJavascript(scrollJs, null)
            }
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
