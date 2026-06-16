package com.slimbook.app

import android.content.Context
import android.content.SharedPreferences

class AuthorDatabase(context: Context) {

    private val authorPrefs: SharedPreferences =
        context.getSharedPreferences("authors", Context.MODE_PRIVATE)
    private val groupPrefs: SharedPreferences =
        context.getSharedPreferences("groups", Context.MODE_PRIVATE)

    fun reportAuthor(name: String) {
        if (!authorPrefs.contains(name)) {
            authorPrefs.edit().putBoolean(name, true).apply()
        }
    }

    fun reportGroup(name: String) {
        if (!groupPrefs.contains(name)) {
            groupPrefs.edit().putBoolean(name, true).apply()
        }
    }

    fun isBlocked(name: String): Boolean {
        if (authorPrefs.contains(name)) return !authorPrefs.getBoolean(name, true)
        if (groupPrefs.contains(name)) return !groupPrefs.getBoolean(name, true)
        return false
    }

    fun getAllAuthors(): List<Pair<String, Boolean>> {
        return authorPrefs.all
            .mapNotNull { (k, v) -> if (v is Boolean) k to v else null }
            .sortedBy { it.first.lowercase() }
    }

    fun getAllGroups(): List<Pair<String, Boolean>> {
        return groupPrefs.all
            .mapNotNull { (k, v) -> if (v is Boolean) k to v else null }
            .sortedBy { it.first.lowercase() }
    }

    fun setAuthorEnabled(name: String, enabled: Boolean) {
        authorPrefs.edit().putBoolean(name, enabled).apply()
    }

    fun setGroupEnabled(name: String, enabled: Boolean) {
        groupPrefs.edit().putBoolean(name, enabled).apply()
    }
}
