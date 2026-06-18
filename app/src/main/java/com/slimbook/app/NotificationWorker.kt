package com.slimbook.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        private const val TAG = "SlimBook"
        private const val URL_BOOKMARKS = "https://m.facebook.com/menu/bookmarks/"
        private const val UA = "curl/7.79.1"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NotificationWorker: starting check")

        if (!android.provider.Settings.canDrawOverlays(applicationContext)) {
            Log.w(TAG, "NotificationWorker: no overlay permission")
            return Result.failure()
        }

        val total = withTimeoutOrNull(30_000L) { checkNotifications() } ?: -1
        Log.d(TAG, "NotificationWorker: total = $total")

        if (total < 0) return Result.retry()

        val db = AuthorDatabase(applicationContext)
        db.setLastNotifCount(total)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (total > 0) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra("open_notifications", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, NotificationService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Facebook")
                .setContentText("You have $total new notification${if (total > 1) "s" else ""}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NotificationService.NOTIFICATION_ID, notification)
        } else {
            nm.cancel(NotificationService.NOTIFICATION_ID)
        }
        return Result.success()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun checkNotifications(): Int = suspendCancellableCoroutine { cont ->
        Handler(Looper.getMainLooper()).post {
            var wm: WindowManager? = null
            val wv = WebView(applicationContext).apply {
                visibility = View.GONE
                settings.javaScriptEnabled = true
                settings.blockNetworkImage = true
                settings.userAgentString = UA
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }

            wv.addJavascriptInterface(object {
                @JavascriptInterface
                fun processJSON(jsonStr: String) {
                    Log.d(TAG, "NotificationWorker: result = $jsonStr")
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val total = if (json.optBoolean("login", false)) 0
                        else json.optInt("notifications", 0) +
                             json.optInt("messages", 0) +
                             json.optInt("friends", 0)
                        Handler(Looper.getMainLooper()).post {
                            wm?.removeView(wv)
                            wv.destroy()
                        }
                        if (cont.isActive) cont.resume(total)
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            wm?.removeView(wv)
                            wv.destroy()
                        }
                        if (cont.isActive) cont.resume(-1)
                    }
                }
            }, "customInterface")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "NotificationWorker: page loaded: $url")
                    view.loadUrl(
                        "javascript:" +
                        "var get=function(url){var elt=document.querySelector(\"[href*='/\"+url+\"']>strong\");" +
                        "return elt!=null ? elt.textContent.match(/[0-9]+/) : \"null\";};" +
                        "var json=window.location.pathname==\"/login.php\"||window.location.pathname==\"/login/\"" +
                        "?'{\"login\":true}'" +
                        ":'{\"login\":false,\"notifications\":'+get(\"notifications\")" +
                        "+',\"messages\":'+get(\"messages\")" +
                        "+',\"friends\":'+get(\"friends\")+'}';" +
                        "window.customInterface.processJSON(json);"
                    )
                }
            }

            try {
                val params = WindowManager.LayoutParams(
                    1, 1,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.START }

                wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(wv, params)
                wv.loadUrl(URL_BOOKMARKS)
            } catch (e: Exception) {
                Log.e(TAG, "NotificationWorker: overlay error", e)
                if (cont.isActive) cont.resume(-1)
            }

            cont.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    wm?.removeView(wv)
                    wv.destroy()
                }
            }
        }
    }
}
