package uk.org.retallack.slimbook

import android.util.Log
import android.webkit.JavascriptInterface

class SlimBookBridge(
    private val db: AuthorDatabase,
    private val onNotifCount: (Int) -> Unit,
    private val onComposerFocused: (Boolean) -> Unit = {},
    private val onMessageCount: (Int) -> Unit = {},
    private val onSaveImageUrl: (String) -> Unit = {},
    private val onSaveFailed: (String) -> Unit = {}
) {

    @JavascriptInterface
    fun reportAuthor(name: String) {
        db.reportAuthor(name.trim())
    }

    @JavascriptInterface
    fun reportGroup(name: String) {
        db.reportGroup(name.trim())
    }

    @JavascriptInterface
    fun isBlocked(name: String): Boolean {
        return db.isBlocked(name.trim())
    }

    @JavascriptInterface
    fun getMaxAgeHours(): Int {
        return db.getMaxAgeHours()
    }

    @JavascriptInterface
    fun reportNotifCount(count: String) {
        val n = count.replace("+", "").toIntOrNull() ?: 0
        onNotifCount(n)
    }

    /** Unread Messenger count read from the page (see unread_watch.js). */
    @JavascriptInterface
    fun reportMessageCount(count: String) {
        onMessageCount(count.replace("+", "").toIntOrNull() ?: return)
    }

    @JavascriptInterface
    fun isLiveMessageCheckEnabled(): Boolean {
        return db.isLiveMessageCheckEnabled()
    }

    @JavascriptInterface
    fun setComposerFocused(focused: Boolean) {
        onComposerFocused(focused)
    }

    /**
     * Save a direct https image URL via the native downloader (no page
     * network, so FB's Content-Security-Policy cannot block it). Issue #10.
     */
    @JavascriptInterface
    fun saveImageUrl(url: String) {
        Log.d("SlimBook", "saveImageUrl: $url")
        onSaveImageUrl(url)
    }

    @JavascriptInterface
    fun saveImageFailed(reason: String) {
        Log.d("SlimBook", "SLIMBOOK_SAVE:failed:$reason")
        onSaveFailed(reason)
    }
}
