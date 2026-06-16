package com.slimbook.app

import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*

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

        setupCookies()
        setupWebView()
        setupSwipeRefresh()
        setupStatsBadge()
        loadFilter()
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
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme ?: ""
                // Redirect fb-messenger:// to mbasic messages (web.facebook.com blocks messaging)
                if (scheme == "fb-messenger" || scheme == "fb") {
                    view.loadUrl("https://mbasic.facebook.com/messages/")
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
        val items = arrayOf(
            if (highlightMode) "Disable highlight mode" else "Enable highlight mode",
            "View log (${logMessages.size} entries)",
            "Dump DOM (Join/Follow elements)",
            "Re-run filter"
        )
        AlertDialog.Builder(this)
            .setTitle("SlimBook Debug")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleHighlight()
                    1 -> showLog()
                    2 -> webView.evaluateJavascript("window.__slimbook_dump()", null)
                    3 -> injectFilter()
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

    private fun isFacebookUrl(url: String): Boolean {
        val host = Uri.parse(url).host ?: ""
        val scheme = Uri.parse(url).scheme ?: ""
        // fb-messenger:// and fb:// are internal Facebook schemes
        if (scheme == "fb-messenger" || scheme == "fb") return true
        return host.endsWith("facebook.com") || host.endsWith("fbcdn.net") || host.endsWith("fb.com")
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
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
