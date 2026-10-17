package com.github.toober77.fastpaste

/** Pure helpers shared by the ViewModel and UI; kept free of Android types for unit tests. */
object PasteLogic {
    const val MAX_CONTENT_LENGTH = 5000
    const val MAX_LABEL_LENGTH = 40

    fun filter(items: List<PasteItem>, query: String, favoritesOnly: Boolean = false): List<PasteItem> {
        val q = query.trim()
        if (q.isEmpty() && !favoritesOnly) return items
        return items.filter {
            (!favoritesOnly || it.favorite) &&
                (q.isEmpty() || it.content.contains(q, ignoreCase = true) ||
                    it.label.contains(q, ignoreCase = true))
        }
    }

    /** Same ordering as the DAO queries: pinned first, then [order] within each group. */
    fun sort(items: List<PasteItem>, order: SortOrder): List<PasteItem> {
        val pinnedFirst = compareByDescending<PasteItem> { it.pinned }
        return items.sortedWith(
            when (order) {
                SortOrder.TIME -> pinnedFirst.thenByDescending { it.createdAt }
                SortOrder.ALPHA -> pinnedFirst.thenBy(String.CASE_INSENSITIVE_ORDER) { it.content }
                SortOrder.CUSTOM -> pinnedFirst.thenBy { it.position }.thenByDescending { it.createdAt }
            }
        )
    }

    /**
     * Moves the item with id [fromId] to the slot of [toId]. Returns null when the move would
     * cross the pinned / unpinned boundary (pinned items always stay on top) or ids are unknown.
     */
    fun move(items: List<PasteItem>, fromId: Int, toId: Int): List<PasteItem>? {
        val from = items.indexOfFirst { it.id == fromId }
        val to = items.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || items[from].pinned != items[to].pinned) return null
        if (from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Dense positions (0, 1, 2…) following the displayed order. */
    fun positionsFor(orderedIds: List<Int>): List<PositionUpdate> =
        orderedIds.mapIndexed { index, id -> PositionUpdate(id, index) }

    fun normalizeContent(raw: String): String = raw.trim().take(MAX_CONTENT_LENGTH)

    fun normalizeLabel(raw: String): String = raw.trim().take(MAX_LABEL_LENGTH)

    /** True if a snippet with the same (trimmed) content already exists. */
    fun isDuplicate(items: List<PasteItem>, content: String): Boolean {
        val c = content.trim()
        if (c.isEmpty()) return false
        return items.any { it.content.trim() == c }
    }
}
