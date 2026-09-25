package com.github.toober77.fastpaste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteLogicTest {
    private val items = listOf(
        PasteItem(id = 1, content = "Email address", label = "work"),
        PasteItem(id = 2, content = "Phone", label = "Home"),
        PasteItem(id = 3, content = "Address", label = "")
    )

    @Test
    fun filter_matchesContentOrLabel_caseInsensitive() {
        assertEquals(listOf(1, 3), PasteLogic.filter(items, "address").map { it.id })
        assertEquals(listOf(2), PasteLogic.filter(items, "home").map { it.id })
        assertEquals(3, PasteLogic.filter(items, "").size)
        assertTrue(PasteLogic.filter(items, "xyz").isEmpty())
    }

    @Test
    fun filter_ignoresSurroundingWhitespaceInQuery() {
        assertEquals(listOf(2), PasteLogic.filter(items, "  phone ").map { it.id })
        assertEquals(3, PasteLogic.filter(items, "   ").size)
    }

    @Test
    fun normalize_trimsAndCapsLength() {
        assertEquals("hi", PasteLogic.normalizeContent("  hi \n"))
        assertEquals(PasteLogic.MAX_CONTENT_LENGTH, PasteLogic.normalizeContent("a".repeat(9000)).length)
        assertEquals(PasteLogic.MAX_LABEL_LENGTH, PasteLogic.normalizeLabel("b".repeat(100)).length)
    }

    @Test
    fun isDuplicate_comparesTrimmedContentExactly() {
        assertTrue(PasteLogic.isDuplicate(items, " Phone "))
        assertFalse(PasteLogic.isDuplicate(items, "phone")) // case matters for snippets
        assertFalse(PasteLogic.isDuplicate(items, "   "))
    }
}
