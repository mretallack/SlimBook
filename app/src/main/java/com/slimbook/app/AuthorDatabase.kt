package com.slimbook.app

import android.content.Context
import android.content.SharedPreferences

class AuthorDatabase(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("authors", Context.MODE_PRIVATE)

    /** Report a seen author. Adds to DB if new (enabled by default). */
    fun reportAuthor(name: String) {
        if (!prefs.contains(name)) {
            prefs.edit().putBoolean(name, true).apply() // true = enabled (shown)
        }
    }

    /** Returns true if author is blocked (checkbox disabled). */
    fun isBlocked(name: String): Boolean {
        // Unknown authors are allowed by default
        return prefs.contains(name) && !prefs.getBoolean(name, true)
    }

    /** Get all authors sorted alphabetically with their enabled state. */
    fun getAll(): List<Pair<String, Boolean>> {
        return prefs.all
            .mapNotNull { (k, v) -> if (v is Boolean) k to v else null }
            .sortedBy { it.first.lowercase() }
    }

    /** Set enabled state for an author. */
    fun setEnabled(name: String, enabled: Boolean) {
        prefs.edit().putBoolean(name, enabled).apply()
    }
}
