package com.github.toober77.fastpaste

import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests for PasteItem model defaults and the SortOrder enum.
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
    fun sortOrder_enumHasTimeAndAlpha() {
        assertEquals(2, SortOrder.entries.size)
        assertEquals(SortOrder.TIME, SortOrder.valueOf("TIME"))
        assertEquals(SortOrder.ALPHA, SortOrder.valueOf("ALPHA"))
    }
}
