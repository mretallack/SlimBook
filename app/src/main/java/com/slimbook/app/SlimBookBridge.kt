package com.slimbook.app

import android.webkit.JavascriptInterface

class SlimBookBridge(private val db: AuthorDatabase) {

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
}
