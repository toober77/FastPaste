package com.github.toober77.fastpaste

import android.content.Context

/** Persists the chosen sort mode so the app, widget and quick-settings dialog agree. */
object SortPrefs {
    private const val FILE = "fastpaste_prefs"
    private const val KEY = "sort_order"

    fun load(context: Context): SortOrder = parse(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
    )

    fun save(context: Context, order: SortOrder) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, order.name).apply()
    }

    /** Unknown / missing values fall back to newest-first. */
    fun parse(value: String?): SortOrder =
        SortOrder.entries.firstOrNull { it.name == value } ?: SortOrder.TIME
}
