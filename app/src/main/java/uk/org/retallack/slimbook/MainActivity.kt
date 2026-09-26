package uk.org.retallack.slimbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val FB_URL = "https://web.facebook.com/"
        // Desktop UA is the long-standing bypass for Meta's "Get Messenger"
        // install wall (mobile UAs are walled on every messages host);
        // the feed stays on the lightweight WebLite mobile UA.
        private const val MESSENGER_URL = "https://www.messenger.com/"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        // Single source of truth for "Get Messenger" install-wall signals,
        // shared by the JS detector and the Kotlin re-probe below. NOTE:
        // English-only; non-English sessions will log OK instead of WALL.
        private val CHAT_WALL_MARKERS = listOf(
            "get messenger", "conversations are moving", "install the app",
            "download messenger", "mobile browsers", "use the messenger app",
            "open in the messenger", "blocked", "unavailable",
        )
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statsBadge: TextView
    private lateinit var filterManager: FilterManager
    private lateinit var authorDb: AuthorDatabase

    private var filterJs: String = ""
    private var highlightMode = false
    private var isMessengerMode = false
    private var messengerRedirectPending = false
    private val logMessages = mutableListOf<String>()
    private var fileUploadCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
    private val filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data.data != null) {
                arrayOf(data.data!!)
            } else null
        } else null
        if (uris != null && uris.isNotEmpty()) {
            Toast.makeText(this, "Photo uploaded", Toast.LENGTH_SHORT).show()
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        statsBadge = findViewById(R.id.statsBadge)
        filterManager = FilterManager(this)
        authorDb = AuthorDatabase(this)

        setupCookies()
        setupWebView()
        setupSwipeRefresh()
        setupStatsBadge()
        requestNotificationPermission()
        NotificationService.createChannel(this)
        authorDb.setLastNotifCount(0) // Clear stale count on open
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(NotificationService.NOTIFICATION_ID)
        scheduleNotificationWorker()
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            CoroutineScope(Dispatchers.IO).launch { filterJs = filterManager.getFilterJs() }
        } else {
            loadFilter()
        }
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    private fun handleNotificationIntent(intent: Intent) {
        if (intent.getBooleanExtra("open_notifications", false)) {
            webView.postDelayed({
                webView.loadUrl("https://web.facebook.com/notifications")
            }, 500)
        }
    }

    private fun isMessagesUrl(url: String): Boolean {
        return url.contains("/messages")
    }

    private fun isChatUrl(url: String?): Boolean {
        return url != null && (url.contains("/messages") || url.contains("messenger.com"))
    }

    /**
     * Centralized redirect to the desktop chat stack. The desktop UA must be
     * set synchronously before loadUrl: the Message tab fires an SPA
     * navigation that never hits onPageFinished on first click (issue #2),
     * and the UA has to be desktop before FB's chat bootstrap runs, otherwise
     * it serves the "Get Messenger" install wall.
     */
    private fun redirectToMessenger(view: WebView, target: String = MESSENGER_URL) {
        // Avoid redirect loops when already on the target page
        if (view.url == target) {
            isMessengerMode = true
            return
        }
        isMessengerMode = true
        messengerRedirectPending = true
        view.settings.userAgentString = DESKTOP_UA
        applyMessengerMode()
        view.loadUrl(target)
    }

    private fun applyFeedMode() {
        if (::swipeRefresh.isInitialized) swipeRefresh.isEnabled = true
        if (::statsBadge.isInitialized) statsBadge.visibility = View.VISIBLE
        if (::webView.isInitialized) {
            webView.settings.useWideViewPort = false
            webView.settings.loadWithOverviewMode = false
        }
    }

    /**
     * Desktop chat layout: disable pull-to-refresh (it steals vertical
     * scroll and clips the fixed header), enable the overview viewport so
     * the page scales to phone width, and hide the stats badge so it never
     * covers the chat composer.
     */
    private fun applyMessengerMode() {
        if (::swipeRefresh.isInitialized) swipeRefresh.isEnabled = false
        if (::statsBadge.isInitialized) statsBadge.visibility = View.GONE
        if (::webView.isInitialized) {
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun scheduleNotificationWorker() {
        val minutes = authorDb.getPollIntervalMinutes()
        if (minutes <= 0) {
            WorkManager.getInstance(this).cancelUniqueWork("fb_notif_poll")
            return
        }
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(
            minutes.toLong(), TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "fb_notif_poll", ExistingPeriodicWorkPolicy.UPDATE, request
        )
        Log.d("SlimBook", "Notification polling scheduled: every ${minutes}m")
    }

    private fun updateNotification(count: Int) {
        // Cache the count for the background worker to use
        authorDb.setLastNotifCount(count)
        Log.d("SlimBook", "Notification count from page: $count")

        if (authorDb.getPollIntervalMinutes() <= 0) return

        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (count > 0) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("open_notifications", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(this, NotificationService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Facebook")
                .setContentText("You have $count notification${if (count > 1) "s" else ""}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NotificationService.NOTIFICATION_ID, notification)
        } else {
            nm.cancel(NotificationService.NOTIFICATION_ID)
        }
    }

    private fun setupCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun setupWebView() {
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = MOBILE_UA
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)

        webView.addJavascriptInterface(SlimBookBridge(authorDb) { count ->
            // Only cache, don't post notification from foreground
            Log.d("SlimBook", "Notification count from page: $count")
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Don't touch chat pages: they are WebSocket-heavy and replacing
                // WebSocket.prototype breaks their transport (issue #2 cut-off).
                if (isChatUrl(url)) return
                view.evaluateJavascript("""(function(){
                    if(window.__sb_ws_hooked)return;window.__sb_ws_hooked=true;
                    var orig=WebSocket.prototype.send;
                    WebSocket.prototype.send=function(d){
                        if(d&&d.byteLength!==undefined){
                            var a=new Uint8Array(d instanceof ArrayBuffer?d:d.buffer||d);
                            // For frames with JSON (>100 bytes), extract the text content
                            if(a.length>100){
                                var str='';
                                for(var i=0;i<Math.min(a.length,500);i++){
                                    var c=a[i];
                                    if(c>=32&&c<127)str+=String.fromCharCode(c);
                                }
                                if(str.indexOf('{')!==-1){
                                    var jsonStart=str.indexOf('{');
                                    console.log('SLIMBOOK_WS_JSON:len='+a.length+':'+str.substring(jsonStart,jsonStart+300));
                                }
                            } else {
                                var h='';var n=Math.min(a.length,48);
                                for(var i=0;i<n;i++)h+=('0'+a[i].toString(16)).slice(-2);
                                console.log('SLIMBOOK_WS:len='+a.length+':'+h);
                            }
                        }
                        return orig.call(this,d);
                    };
                })()""", null)
            }

            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                val url = request.url.toString()
                // Log telemetry/tracking requests
                if (url.contains("/ajax/bz") || url.contains("logging") ||
                    url.contains("/tr") || url.contains("time_spent") ||
                    url.contains("beacon") || url.contains("impression") ||
                    url.contains("viewability") || url.contains("exposure")) {
                    val params = request.url.query?.take(200) ?: ""
                    Log.d("SlimBook", "TRACK: ${request.method} ${request.url.path} q=$params")
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme ?: ""
                Log.d("SlimBook", "NAV: $url")
                // Swallow app-store pushes: FB bounces mobile chat users to the
                // Play Store ("Get Messenger"). Stay in the WebView instead —
                // going back usually lands on the working chat (FaceSlim trick).
                if (scheme == "market" || url.contains("play.google.com/store")) {
                    Log.d("SlimBook", "CHAT_STORE_BLOCK:$url")
                    if (view.canGoBack()) view.goBack()
                    return true
                }
                // Handle intent:// URLs for messenger share
                if (scheme == "intent" && url.contains("fb-messenger")) {
                    val linkMatch = Regex("link=([^&]+)").find(url)
                    val link = linkMatch?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                    if (link.isNotEmpty()) {
                        redirectToMessenger(view, "$MESSENGER_URL/new?link=${java.net.URLEncoder.encode(link, "UTF-8")}")
                    } else {
                        redirectToMessenger(view)
                    }
                    return true
                }
                // Other intent:// URLs (e.g. Play Store intents): stay in app.
                if (scheme == "intent") {
                    Log.d("SlimBook", "CHAT_STORE_BLOCK:$url")
                    return true
                }
                // Redirect fb-messenger:// and messages URLs to the desktop
                // chat stack (like SlimSocial).
                if (scheme == "fb-messenger" || scheme == "fb" || isMessagesUrl(url)) {
                    redirectToMessenger(view)
                    return true
                }
                // Coming back from chat to facebook - restore mobile UA
                if (url.contains("facebook.com") && !url.contains("/messages") && isChatUrl(view.url)) {
                    isMessengerMode = false
                    messengerRedirectPending = false
                    view.settings.userAgentString = MOBILE_UA
                    applyFeedMode()
                    view.loadUrl(FB_URL)
                    return true
                }
                return if (isFacebookUrl(url)) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {
                        // No app to handle, ignore
                    }
                    true
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Issue #2: the Message tab navigates via the SPA history API,
                // which never triggers shouldOverrideUrlLoading/onPageFinished
                // on first click. Catch the history entry instead.
                Log.d("SlimBook", "HIST: $url reload=$isReload")
                if (url != null && isMessagesUrl(url) && !isMessengerMode) {
                    redirectToMessenger(view)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d("SlimBook", "PAGE: $url")
                // Stay in messenger mode: keep desktop UA + chat layout
                if (isChatUrl(url)) {
                    isMessengerMode = true
                    messengerRedirectPending = false
                    if (view.settings.userAgentString != DESKTOP_UA) {
                        view.settings.userAgentString = DESKTOP_UA
                    }
                    applyMessengerMode()
                    swipeRefresh.isRefreshing = false
                    applyMessengerTweaks(view, url)
                    return
                }
                // A settling Facebook page finishing after a messenger redirect
                // started (e.g. tapped Message mid-load): swallow it so it can't
                // reset the desktop UA before the messenger load commits.
                if (messengerRedirectPending && url.contains("facebook.com")) {
                    messengerRedirectPending = false
                    swipeRefresh.isRefreshing = false
                    return
                }
                messengerRedirectPending = false
                // Restore mobile UA when genuinely back on Facebook
                if (url.contains("facebook.com")) {
                    isMessengerMode = false
                    view.settings.userAgentString = MOBILE_UA
                    applyFeedMode()
                }
                swipeRefresh.isRefreshing = false
                injectFilter()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
                return true
            }

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val text = msg.message()
                Log.d("SlimBook", text)
                if (text.startsWith("SLIMBOOK_STATS:")) {
                    updateStats(text.removePrefix("SLIMBOOK_STATS:"))
                } else if (text.startsWith("SLIMBOOK")) {
                    logMessages.add(text)
                    if (logMessages.size > 100) logMessages.removeAt(0)
                }
                return true
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    private fun setupStatsBadge() {
        statsBadge.visibility = View.VISIBLE
        statsBadge.setOnLongClickListener {
            showDebugMenu()
            true
        }
    }

    private fun loadFilter() {
        CoroutineScope(Dispatchers.IO).launch {
            filterJs = filterManager.getFilterJs()
            withContext(Dispatchers.Main) {
                webView.loadUrl(FB_URL)
            }
        }
    }

    /**
     * Chat entry point: light chrome-hiding CSS, "Get Messenger" install-wall
     * detection with an on-page bypass attempt ("continue on web"-style
     * dismissal), plus the virtualized-grid repair (the grid sometimes mounts
     * ~100px tall while its navigation pane is full-height, painting ~1.5
     * rows with no scroll). Detect-and-log only: no cross-host fallback —
     * the wall markers + probe exist so a future Meta change is diagnosable
     * from logcat. Everything is session-local and reverts itself if
     * geometry looks insane.
     */
    private fun applyMessengerTweaks(view: WebView, url: String) {
        val markersJs = CHAT_WALL_MARKERS.joinToString(",") { "'$it'" }
        view.evaluateJavascript("""
            (function() {
                if (window.__sb_chat_tweaked) return;
                window.__sb_chat_tweaked = true;
                // NOTE: never hide a[href="/"] or [aria-label="Facebook"] —
                // that is the header logo linking back to the feed, the only
                // on-screen path out of chat.
                var css = 'a[href="/watch"],a[href="/marketplace"],'
                    + '[aria-label="Search Facebook"]{display:none!important;}'
                    + 'form[role="search"]{display:none!important;}';
                var st = document.createElement('style');
                st.id = 'slimbook-chat';
                st.textContent = css;
                document.documentElement.appendChild(st);
                function low(s, n) { return ((s || '').slice(0, n || 600)).toLowerCase(); }
                var markers = [$markersJs];
                function wallPresent() {
                    var body = low(document.body && document.body.innerText, 600);
                    var title = low(document.title, 120);
                    for (var i = 0; i < markers.length; i++) {
                        if (body.indexOf(markers[i]) !== -1 || title.indexOf(markers[i]) !== -1) return markers[i];
                    }
                    return null;
                }
                function chatPresent() {
                    return !!document.querySelector('[role="grid"],[role="main"] input,'
                        + '[aria-label*="essage"] input,[placeholder*="essage"]');
                }
                var hit = wallPresent();
                if (hit && !chatPresent()) {
                    // Try the on-page bypass: many FB interstitials hide a
                    // web-continue path behind the CTA. English-only, like the
                    // markers above.
                    var clicked = null;
                    var els = document.querySelectorAll('a,button,[role="button"]');
                    for (var k = 0; k < els.length; k++) {
                        var t = low(els[k].innerText || els[k].getAttribute('aria-label') || '', 80);
                        if (/continue on web|not now|use facebook|dismiss|^close$|no thanks/.test(t)) {
                            try { els[k].click(); clicked = t; } catch (e) {}
                            break;
                        }
                    }
                    console.log('SLIMBOOK_CHAT:WALL:' + hit + ':bypass=' + (clicked || 'none')
                        + ':' + document.title.slice(0, 80));
                } else {
                    console.log('SLIMBOOK_CHAT:OK:' + document.title.slice(0, 80));
                }
            })();
        """.trimIndent(), null)
        // Re-probe after async render (wall text often arrives after
        // onPageFinished). Log-only: no navigation, just a signal for logs.
        view.postDelayed({
            if (!isChatUrl(view.url)) return@postDelayed
            view.evaluateJavascript(
                "JSON.stringify({t: document.title, b: (document.body && document.body.innerText || '').slice(0, 500), chat: !!document.querySelector('[role=\"grid\"],[placeholder*=\"essage\"]')})",
            ) { json ->
                if (json == null) return@evaluateJavascript
                val lower = json.lowercase()
                val wall = CHAT_WALL_MARKERS.any { lower.contains(it) } &&
                    !lower.contains("\"chat\":true")
                Log.d("SlimBook", "CHAT_PROBE:$url wall=$wall -> $json".take(300))
            }
        }, 2500)
        repairMessengerLayout(view)
    }

    /**
     * The desktop chat grid sometimes mounts collapsed (~100px tall, overflow
     * hidden) while its navigation pane is full-height — likely measured
     * mid-transition during auto-nav to the last thread — so only ~1.5 rows
     * paint and the column can't scroll. Size the collapsed wrappers to fill
     * the pane, make the grid scrollable, then kick it with scroll/resize
     * events so it renders rows. Chat-only, session-local; reverts itself if
     * geometry looks insane.
     */
    private fun repairMessengerLayout(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                if (window.__sb_msgr_repairing) return;
                function info(el) {
                    try {
                        var r = el.getBoundingClientRect();
                        return el.tagName + ':' + Math.round(r.width) + 'x' + Math.round(r.height) + '@' + Math.round(r.top)
                            + ':ch=' + el.clientHeight + ':kids=' + el.children.length;
                    } catch (e) { return 'err'; }
                }
                function attempt(tag) {
                    var grid = document.querySelector('[role="grid"]');
                    if (!grid) { console.log('SLIMBOOK_MSGR:' + tag + ':no-grid'); return false; }
                    var gr = grid.getBoundingClientRect();
                    var innerRows = grid.firstElementChild ? grid.firstElementChild.children.length : -1;
                    console.log('SLIMBOOK_MSGR:' + tag + ':grid=' + info(grid) + ':innerRows=' + innerRows);
                    if (gr.height > 200 && gr.width <= window.innerWidth * 1.2 && innerRows > 2) {
                        console.log('SLIMBOOK_MSGR:' + tag + ':healthy');
                        return true;
                    }
                    if (gr.height > 200) {
                        // Tall but empty/narrow: kick rendering without touching layout
                        try { grid.scrollTop = 1; } catch (e) {}
                        window.dispatchEvent(new Event('resize'));
                        console.log('SLIMBOOK_MSGR:' + tag + ':kicked');
                        return true;
                    }
                    // Find the full-height navigation pane to compute fill height
                    var nav = grid.closest('[role="navigation"]') || document.body;
                    var nr = nav.getBoundingClientRect();
                    var target = Math.round(nr.bottom - gr.top - 4);
                    if (target < 200) { console.log('SLIMBOOK_MSGR:' + tag + ':no-room'); return false; }
                    window.__sb_msgr_repairing = true;
                    var touched = [];
                    var p = grid;
                    for (var d = 0; d < 6 && p && p !== nav; d++) {
                        if (p.clientHeight < target - 50) {
                            p.style.setProperty('height', target + 'px', 'important');
                            p.style.setProperty('min-height', target + 'px', 'important');
                            p.style.setProperty('max-width', '100%', 'important');
                            touched.push(p);
                        }
                        p = p.parentElement;
                    }
                    grid.style.setProperty('overflow-y', 'auto', 'important');
                    // Sanity: never let our overrides blow the layout out horizontally
                    var gw = grid.getBoundingClientRect().width;
                    if (gw > window.innerWidth * 1.2) {
                        for (var k = 0; k < touched.length; k++) {
                            touched[k].style.removeProperty('height');
                            touched[k].style.removeProperty('min-height');
                            touched[k].style.removeProperty('max-width');
                        }
                        grid.style.removeProperty('overflow-y');
                        window.__sb_msgr_repairing = false;
                        console.log('SLIMBOOK_MSGR:' + tag + ':blowout-reverted');
                        return true;
                    }
                    try { grid.scrollTop = 1; } catch (e) {}
                    window.dispatchEvent(new Event('resize'));
                    setTimeout(function() {
                        try { grid.scrollTop = 0; } catch (e) {}
                        window.dispatchEvent(new Event('resize'));
                        var gr2 = grid.getBoundingClientRect();
                        var ir2 = grid.firstElementChild ? grid.firstElementChild.children.length : -1;
                        console.log('SLIMBOOK_MSGR:repaired:grid=' + Math.round(gr2.width) + 'x' + Math.round(gr2.height)
                            + ':innerRows=' + ir2 + ':sh=' + grid.scrollHeight);
                        window.__sb_msgr_repairing = false;
                    }, 1200);
                    return true;
                }
                if (attempt('load')) return;
                var tries = 0;
                var timer = setInterval(function() {
                    tries++;
                    if (attempt('poll' + tries)) { clearInterval(timer); }
                    else if (tries >= 12) { clearInterval(timer); console.log('SLIMBOOK_MSGR:giveup'); }
                }, 1000);
            })();
        """.trimIndent(), null)
    }

    private fun injectFilter() {
        // Never inject feed filtering/tracking hooks into chat pages
        // (breaks their messaging transport and layout).
        if (isChatUrl(webView.url)) return
        if (filterJs.isNotEmpty()) {
            webView.evaluateJavascript(filterJs, null)
        }
        // Inject tracking logger to intercept WebSocket, sendBeacon, and XHR
        webView.evaluateJavascript("""
            (function() {
                if (window.__slimbook_track_hooked) return;
                window.__slimbook_track_hooked = true;

                function bufToHex(buf, maxBytes) {
                    var arr = new Uint8Array(buf instanceof ArrayBuffer ? buf : buf.buffer || buf);
                    var hex = '';
                    var len = Math.min(arr.length, maxBytes || 48);
                    for (var i = 0; i < len; i++) hex += ('0' + arr[i].toString(16)).slice(-2);
                    return hex;
                }

                // Hook the existing WebSocket instance (Facebook stores it as window.__lws)
                function hookWS(ws) {
                    if (!ws || ws.__slimbook_hooked) return;
                    ws.__slimbook_hooked = true;
                    var origSend = ws.send.bind(ws);
                    ws.send = function(data) {
                        if (data instanceof ArrayBuffer || (data && data.byteLength !== undefined)) {
                            var len = data.byteLength || data.length || 0;
                            var hex = bufToHex(data, 48);
                            console.log('SLIMBOOK_WS:len=' + len + ':' + hex);
                        } else if (typeof data === 'string') {
                            console.log('SLIMBOOK_WS:str:' + data.substring(0, 200));
                        }
                        return origSend(data);
                    };
                    console.log('SLIMBOOK_WS_HOOKED:readyState=' + ws.readyState);
                }

                // Hook existing instance
                if (window.__lws) hookWS(window.__lws);

                // Also hook future instances via prototype
                var origWSCtor = window.WebSocket;
                window.WebSocket = function(url, protocols) {
                    var ws = protocols ? new origWSCtor(url, protocols) : new origWSCtor(url);
                    console.log('SLIMBOOK_WS_NEW:' + url.substring(0, 100));
                    setTimeout(function() { hookWS(ws); }, 100);
                    return ws;
                };
                window.WebSocket.prototype = origWSCtor.prototype;
                window.WebSocket.CONNECTING = origWSCtor.CONNECTING;
                window.WebSocket.OPEN = origWSCtor.OPEN;
                window.WebSocket.CLOSING = origWSCtor.CLOSING;
                window.WebSocket.CLOSED = origWSCtor.CLOSED;

                // Intercept sendBeacon
                var origBeacon = navigator.sendBeacon.bind(navigator);
                navigator.sendBeacon = function(url, data) {
                    console.log('SLIMBOOK_BEACON:' + url.substring(0, 150));
                    return origBeacon(url, data);
                };

                console.log('SLIMBOOK_TRACK_HOOKS_INSTALLED:__lws=' + !!window.__lws);
            })();
        """.trimIndent(), null)
    }

    private fun updateStats(json: String) {
        try {
            val nums = Regex("\\d+").findAll(json).map { it.value.toInt() }.toList()
            if (nums.size >= 6) {
                val total = nums.sum()
                val text = "\uD83D\uDEE1 A:${nums[0]} S:${nums[1]} P:${nums[2]} G:${nums[3]} F:${nums[4]} B:${nums[5]}"
                statsBadge.text = text
            }
        } catch (_: Exception) {}
    }

    private fun showDebugMenu() {
        val ageLabel = when (authorDb.getMaxAgeHours()) {
            0 -> "off"
            12 -> "12 hours"
            24 -> "1 day"
            48 -> "2 days"
            120 -> "5 days"
            240 -> "10 days"
            else -> "${authorDb.getMaxAgeHours()}h"
        }
        val pollLabel = when (authorDb.getPollIntervalMinutes()) {
            0 -> "off"
            15 -> "15 min"
            30 -> "30 min"
            60 -> "1 hour"
            120 -> "2 hours"
            360 -> "6 hours"
            720 -> "12 hours"
            else -> "${authorDb.getPollIntervalMinutes()}m"
        }
        val items = arrayOf(
            if (highlightMode) "Disable highlight mode" else "Enable highlight mode",
            "Manage authors (${authorDb.getAllAuthors().size})",
            "Manage groups (${authorDb.getAllGroups().size})",
            "Post age filter ($ageLabel)",
            "Notification poll ($pollLabel)",
            "Remote filter (${if (authorDb.isRemoteFilterEnabled()) "on" else "off"})",
            "Hide news feed (${if (authorDb.isHideFeedEnabled()) "on" else "off"})",
            "View log (${logMessages.size} entries)",
            "Dump DOM",
            "Re-run filter"
        )
        AlertDialog.Builder(this)
            .setTitle("SlimBook Debug")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleHighlight()
                    1 -> showAuthorList()
                    2 -> showGroupList()
                    3 -> showAgeFilter()
                    4 -> showPollInterval()
                    5 -> toggleRemoteFilter()
                    6 -> toggleHideFeed()
                    7 -> showLog()
                    8 -> webView.evaluateJavascript("window.__slimbook_dump()", null)
                    9 -> injectFilter()
                }
            }
            .show()
    }

    private fun toggleHighlight() {
        highlightMode = !highlightMode
        webView.evaluateJavascript(
            "window.__slimbook_setHighlight($highlightMode);", null
        )
        Toast.makeText(this, "Highlight: $highlightMode", Toast.LENGTH_SHORT).show()
    }

    private fun showLog() {
        val msg = if (logMessages.isEmpty()) "No log messages"
                  else logMessages.takeLast(20).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("Filter Log")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAuthorList() {
        val authors = authorDb.getAllAuthors()
        if (authors.isEmpty()) {
            Toast.makeText(this, "No authors seen yet. Scroll the feed first.", Toast.LENGTH_SHORT).show()
            return
        }
        showFilterableList("Authors (uncheck to hide)", authors) { name, enabled ->
            authorDb.setAuthorEnabled(name, enabled)
        }
    }

    private fun showGroupList() {
        val groups = authorDb.getAllGroups()
        if (groups.isEmpty()) {
            Toast.makeText(this, "No groups seen yet. Scroll the feed first.", Toast.LENGTH_SHORT).show()
            return
        }
        showFilterableList("Groups (uncheck to hide)", groups) { name, enabled ->
            authorDb.setGroupEnabled(name, enabled)
        }
    }

    private fun showFilterableList(
        title: String,
        items: List<Pair<String, Boolean>>,
        onToggle: (String, Boolean) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_search_list, null)
        val searchEdit = view.findViewById<android.widget.EditText>(R.id.searchEdit)
        val listView = view.findViewById<android.widget.ListView>(R.id.listView)

        var filtered = items.toMutableList()
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(pos: Int) = filtered[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, cv: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val cb = (cv as? android.widget.CheckBox) ?: android.widget.CheckBox(this@MainActivity)
                val (name, enabled) = filtered[pos]
                cb.text = name
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = enabled
                cb.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(name, isChecked)
                    filtered[pos] = name to isChecked
                }
                return cb
            }
        }
        listView.adapter = adapter

        searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                filtered = if (query.isEmpty()) items.toMutableList()
                else items.filter { it.first.lowercase().contains(query) }.toMutableList()
                adapter.notifyDataSetChanged()
            }
        })

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("OK") { _, _ -> injectFilter() }
            .show()
    }

    private fun showAgeFilter() {
        val options = arrayOf("Off", "12 hours", "1 day", "2 days", "5 days", "10 days")
        val values = intArrayOf(0, 12, 24, 48, 120, 240)
        val current = values.indexOf(authorDb.getMaxAgeHours()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Hide posts older than:")
            .setSingleChoiceItems(options, current) { dialog, which ->
                authorDb.setMaxAgeHours(values[which])
                dialog.dismiss()
                injectFilter()
                Toast.makeText(this, "Age filter: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPollInterval() {
        val options = arrayOf("Off", "15 min", "30 min", "1 hour", "2 hours", "6 hours", "12 hours")
        val values = intArrayOf(0, 15, 30, 60, 120, 360, 720)
        val current = values.indexOf(authorDb.getPollIntervalMinutes()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Check notifications every:")
            .setSingleChoiceItems(options, current) { dialog, which ->
                val minutes = values[which]
                if (minutes > 0 && !android.provider.Settings.canDrawOverlays(this)) {
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Background notification checking needs the \"Display over other apps\" permission. This is used to run an invisible WebView that checks Facebook for new notifications.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            try {
                                // Open app info page where user can find the permission
                                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:$packageName")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (e: Exception) {
                                Toast.makeText(this, "Go to Settings > Apps > SlimBook > Display over other apps", Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@setSingleChoiceItems
                }
                authorDb.setPollIntervalMinutes(minutes)
                scheduleNotificationWorker()
                dialog.dismiss()
                Toast.makeText(this, "Notification poll: ${options[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun toggleRemoteFilter() {
        val currentlyEnabled = authorDb.isRemoteFilterEnabled()
        if (currentlyEnabled) {
            // Turning off - no confirmation needed
            authorDb.setRemoteFilterEnabled(false)
            Toast.makeText(this, "Remote filter: off (restart to apply)", Toast.LENGTH_SHORT).show()
        } else {
            // Turning on - show confirmation dialog
            android.app.AlertDialog.Builder(this)
                .setTitle("Enable remote filter?")
                .setMessage(
                    "This will download and execute filter.js from GitHub " +
                    "(https://github.com/mretallack/SlimBook) each time the app starts.\n\n" +
                    "The script runs inside the WebView to filter Facebook content. " +
                    "When disabled, a bundled copy of the filter is used instead."
                )
                .setPositiveButton("Enable") { _, _ ->
                    authorDb.setRemoteFilterEnabled(true)
                    Toast.makeText(this, "Remote filter: on (restart to apply)", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun isFacebookUrl(url: String): Boolean {
        val host = Uri.parse(url).host ?: ""
        val scheme = Uri.parse(url).scheme ?: ""
        // fb-messenger:// and fb:// are internal Facebook schemes
        if (scheme == "fb-messenger" || scheme == "fb") return true
        return host.endsWith("facebook.com") || host.endsWith("fbcdn.net") || host.endsWith("fb.com") || host.endsWith("messenger.com")
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        val url = webView.url ?: ""
        if (isChatUrl(url)) {
            // Leave chat - go back to feed with mobile UA
            isMessengerMode = false
            messengerRedirectPending = false
            webView.settings.userAgentString = MOBILE_UA
            applyFeedMode()
            webView.loadUrl(FB_URL)
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    private fun toggleHideFeed() {
        val newState = !authorDb.isHideFeedEnabled()
        authorDb.setHideFeedEnabled(newState)
        Toast.makeText(this, "Hide news feed: ${if (newState) "on" else "off"}", Toast.LENGTH_SHORT).show()
        injectFilter()
    }
}