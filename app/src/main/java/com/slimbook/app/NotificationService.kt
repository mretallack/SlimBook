package com.slimbook.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class NotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "fb_notifications"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "SlimBook"
        private const val URL_BOOKMARKS = "https://m.facebook.com/menu/bookmarks/"
        private const val MOBILE_UA = "curl/7.79.1"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Facebook Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Facebook notification alerts" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private var windowManager: WindowManager? = null
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationService: starting check")

        val wv = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.blockNetworkImage = true
            settings.userAgentString = MOBILE_UA
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(this@NotificationService, "customInterface")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "NotificationService: page loaded: $url")
                    view.loadUrl(
                        "javascript:" +
                        "var get=function(url){var elt=document.querySelector(\"[href*='/\"+url+\"']>strong\");" +
                        "return elt!=null ? elt.textContent.match(/[0-9]+/) : \"null\";};" +
                        "var json=window.location.pathname==\"/login.php\"||window.location.pathname==\"/login/\"" +
                        "?'{\"login\":true}'" +
                        ":'{\"login\":false,\"notifications\":'+get(\"notifications\")" +
                        "+',\"messages\":'+get(\"messages\")" +
                        "+',\"friends\":'+get(\"friends\")" +
                        "+',\"groups\":'+get(\"groups\")+'}';" +
                        "window.customInterface.processJSON(json);"
                    )
                }
            }
        }
        webView = wv

        try {
            val params = WindowManager.LayoutParams(
                1, 1,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager?.addView(wv, params)
            wv.loadUrl(URL_BOOKMARKS)
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService: overlay permission missing", e)
            stopSelf()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun processJSON(jsonStr: String) {
        Log.d(TAG, "NotificationService: result = $jsonStr")
        try {
            val json = org.json.JSONObject(jsonStr)
            if (json.optBoolean("login", false)) {
                Log.w(TAG, "NotificationService: not logged in")
                stopSelf()
                return
            }

            val notifications = json.optInt("notifications", 0)
            val messages = json.optInt("messages", 0)
            val friends = json.optInt("friends", 0)
            val groups = json.optInt("groups", 0)
            val total = notifications + messages + friends + groups

            Log.d(TAG, "NotificationService: N:$notifications M:$messages F:$friends G:$groups")

            val db = AuthorDatabase(this)
            db.setLastNotifCount(total)

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (total > 0) {
                val parts = mutableListOf<String>()
                if (notifications > 0) parts.add("$notifications notification${if (notifications > 1) "s" else ""}")
                if (messages > 0) parts.add("$messages message${if (messages > 1) "s" else ""}")
                if (friends > 0) parts.add("$friends friend request${if (friends > 1) "s" else ""}")
                if (groups > 0) parts.add("$groups group")

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("open_notifications", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Facebook")
                    .setContentText(parts.joinToString(", "))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm.notify(NOTIFICATION_ID, notification)
            } else {
                nm.cancel(NOTIFICATION_ID)
            }
        } catch (e: Exception) {
            Log.e(TAG, "NotificationService: JSON error", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.let { windowManager?.removeView(it) }
        webView?.destroy()
        webView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
