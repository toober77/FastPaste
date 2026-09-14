package com.github.toober77.fastpaste

import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests for PasteItem model defaults and search-style filtering logic
 * used by PasteViewModel (mirrored here without Android runtime).
 */
class PasteItemTest {

    @Test
    fun pasteItem_defaults_labelEmptyAndIdZero() {
        val item = PasteItem(content = "hello")
        assertEquals(0, item.id)
        assertEquals("hello", item.content)
        assertEquals("", item.label)
        assertTrue(item.createdAt > 0L)
    }

    @Test
    fun filterByQuery_matchesContentOrLabel_caseInsensitive() {
        val items = listOf(
            PasteItem(id = 1, content = "Email address", label = "work"),
            PasteItem(id = 2, content = "Phone", label = "Home"),
            PasteItem(id = 3, content = "Address", label = "")
        )
        fun filter(query: String) =
            if (query.isBlank()) items
            else items.filter {
                it.content.contains(query, ignoreCase = true) ||
                    it.label.contains(query, ignoreCase = true)
            }

        assertEquals(listOf(1, 3), filter("address").map { it.id })
        assertEquals(listOf(2), filter("home").map { it.id })
        assertEquals(3, filter("").size)
        assertTrue(filter("xyz").isEmpty())
    }

    @Test
    fun sortOrder_enumHasTimeAndAlpha() {
        assertEquals(2, SortOrder.entries.size)
        assertEquals(SortOrder.TIME, SortOrder.valueOf("TIME"))
        assertEquals(SortOrder.ALPHA, SortOrder.valueOf("ALPHA"))
    }
}
