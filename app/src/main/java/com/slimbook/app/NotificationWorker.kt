package com.slimbook.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "fb_notifications"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "SlimBook"
        private const val NOTIF_URL = "https://web.facebook.com/notifications"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // JS to count unread notifications on the notifications page.
        // Notifications page has items with "unread" styling or recent timestamps.
        // We look for notification-like containers that are visible.
        private const val EXTRACT_JS = """
            (function() {
                // On the notifications page, each notification is typically a link/div
                // with role="link" or inside a list. Count items that appear unread
                // (they usually have a blue dot or different background).
                // Strategy: count elements with a blue/highlighted background in the list.
                var items = document.querySelectorAll('[role="link"], [data-sigil*="notification"]');
                if (items.length > 0) return '' + items.length;
                // Fallback: count spans with timestamps like "1m", "5m", "1h" etc
                var recent = 0;
                var spans = document.querySelectorAll('span');
                for (var i = 0; i < spans.length; i++) {
                    var t = spans[i].textContent.trim();
                    if (t.match(/^\d+[mh]$/) || t === 'Just now') recent++;
                }
                return '' + recent;
            })()
        """

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Facebook Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Facebook notification alerts" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NotificationWorker: starting poll")

        val count = withTimeoutOrNull(45_000L) { fetchNotificationCount() } ?: -1
        Log.d(TAG, "NotificationWorker: notification count = $count")

        if (count < 0) return Result.retry() // failed to load

        val db = AuthorDatabase(applicationContext)
        db.setLastNotifCount(count)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (count > 0) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra("open_notifications", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Facebook")
                .setContentText("You have $count notification${if (count > 1) "s" else ""}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        } else {
            nm.cancel(NOTIFICATION_ID)
        }
        return Result.success()
    }

    private suspend fun fetchNotificationCount(): Int = suspendCancellableCoroutine { cont ->
        Handler(Looper.getMainLooper()).post {
            val wv = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = MOBILE_UA
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Give it a real layout size so content renders
                layout(0, 0, 1080, 2400)
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "NotificationWorker: page loaded: $url")
                    // Wait for WebSocket content to stream in
                    view.postDelayed({
                        view.evaluateJavascript(EXTRACT_JS) { result ->
                            Log.d(TAG, "NotificationWorker: JS result = $result")
                            val clean = result?.trim('"') ?: "0"
                            val count = clean.toIntOrNull() ?: 0
                            view.destroy()
                            if (cont.isActive) cont.resume(count)
                        }
                    }, 12000) // 12s for WebSocket content to render
                }
            }
            wv.loadUrl(NOTIF_URL)

            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { wv.destroy() }
            }
        }
    }
}
