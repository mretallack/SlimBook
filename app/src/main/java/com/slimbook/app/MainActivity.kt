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

        webView.addJavascriptInterface(SlimBookBridge(authorDb), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme ?: ""
                Log.d("SlimBook", "NAV: $url")
                // Redirect fb-messenger:// to desktop messages (mobile web blocks messaging)
                if (scheme == "fb-messenger" || scheme == "fb") {
                    // Temporarily switch to desktop UA for messages
                    view.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    view.loadUrl("https://www.facebook.com/messages/")
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
            24 -> "1 day"
            48 -> "2 days"
            120 -> "5 days"
            240 -> "10 days"
            else -> "${authorDb.getMaxAgeHours()}h"
        }
        val items = arrayOf(
            if (highlightMode) "Disable highlight mode" else "Enable highlight mode",
            "Manage authors (${authorDb.getAllAuthors().size})",
            "Manage groups (${authorDb.getAllGroups().size})",
            "Post age filter ($ageLabel)",
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
                    4 -> showLog()
                    5 -> webView.evaluateJavascript("window.__slimbook_dump()", null)
                    6 -> injectFilter()
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
        val names = authors.map { it.first }.toTypedArray()
        val checked = authors.map { it.second }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Authors (uncheck to hide)")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                authorDb.setAuthorEnabled(names[which], isChecked)
            }
            .setPositiveButton("OK") { _, _ -> injectFilter() }
            .show()
    }

    private fun showGroupList() {
        val groups = authorDb.getAllGroups()
        if (groups.isEmpty()) {
            Toast.makeText(this, "No groups seen yet. Scroll the feed first.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = groups.map { it.first }.toTypedArray()
        val checked = groups.map { it.second }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Groups (uncheck to hide)")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                authorDb.setGroupEnabled(names[which], isChecked)
            }
            .setPositiveButton("OK") { _, _ -> injectFilter() }
            .show()
    }

    private fun showAgeFilter() {
        val options = arrayOf("Off", "1 day", "2 days", "5 days", "10 days")
        val values = intArrayOf(0, 24, 48, 120, 240)
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
