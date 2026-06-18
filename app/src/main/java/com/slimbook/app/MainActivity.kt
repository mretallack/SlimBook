package com.slimbook.app

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
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var statsBadge: TextView
    private lateinit var filterManager: FilterManager
    private lateinit var authorDb: AuthorDatabase

    private var filterJs: String = ""
    private var highlightMode = false
    private val logMessages = mutableListOf<String>()

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
        loadFilter()
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
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = MOBILE_UA
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }

        webView.addJavascriptInterface(SlimBookBridge(authorDb) { count ->
            // Only cache, don't post notification from foreground
            Log.d("SlimBook", "Notification count from page: $count")
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
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
                // Redirect fb-messenger:// and messages URLs to messenger.com (like SlimSocial)
                if (scheme == "fb-messenger" || scheme == "fb" || url.contains("facebook.com/messages")) {
                    view.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    view.loadUrl("https://www.messenger.com/")
                    return true
                }
                // Coming back from messenger to facebook - restore mobile UA
                if (url.contains("www.facebook.com") && !url.contains("/messages") && view.url?.contains("messenger.com") == true) {
                    view.settings.userAgentString = MOBILE_UA
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

            override fun onPageFinished(view: WebView, url: String) {
                Log.d("SlimBook", "PAGE: $url")
                // If we ended up on a messages page, redirect to messenger.com
                if (url.contains("facebook.com/messages")) {
                    view.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    view.loadUrl("https://www.messenger.com/")
                    return
                }
                // Restore mobile UA when back on feed
                if (url.contains("web.facebook.com")) {
                    view.settings.userAgentString = MOBILE_UA
                }
                swipeRefresh.isRefreshing = false
                injectFilter()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
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

    private fun injectFilter() {
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
                    6 -> showLog()
                    7 -> webView.evaluateJavascript("window.__slimbook_dump()", null)
                    8 -> injectFilter()
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
        val enabled = !authorDb.isRemoteFilterEnabled()
        authorDb.setRemoteFilterEnabled(enabled)
        Toast.makeText(this, "Remote filter: ${if (enabled) "on" else "off"} (restart to apply)", Toast.LENGTH_SHORT).show()
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
        if (url.contains("messenger.com")) {
            // Leave messenger - go back to feed with mobile UA
            webView.settings.userAgentString = MOBILE_UA
            webView.loadUrl(FB_URL)
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }
}
