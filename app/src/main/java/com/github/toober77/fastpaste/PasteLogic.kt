package com.github.toober77.fastpaste

/** Pure helpers shared by the ViewModel and UI; kept free of Android types for unit tests. */
object PasteLogic {
    const val MAX_CONTENT_LENGTH = 5000
    const val MAX_LABEL_LENGTH = 40

    fun filter(items: List<PasteItem>, query: String): List<PasteItem> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter {
            it.content.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true)
        }
    }

    fun normalizeContent(raw: String): String = raw.trim().take(MAX_CONTENT_LENGTH)

    fun normalizeLabel(raw: String): String = raw.trim().take(MAX_LABEL_LENGTH)

    /** True if a snippet with the same (trimmed) content already exists. */
    fun isDuplicate(items: List<PasteItem>, content: String): Boolean {
        val c = content.trim()
        if (c.isEmpty()) return false
        return items.any { it.content.trim() == c }
    }
}
