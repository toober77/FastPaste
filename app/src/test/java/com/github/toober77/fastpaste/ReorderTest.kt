package com.github.toober77.fastpaste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderTest {
    private val items = listOf(
        PasteItem(id = 1, content = "a", pinned = true, position = 5, createdAt = 1),
        PasteItem(id = 2, content = "b", pinned = true, position = 6, createdAt = 2),
        PasteItem(id = 3, content = "c", position = 0, createdAt = 3),
        PasteItem(id = 4, content = "d", position = 1, createdAt = 4),
        PasteItem(id = 5, content = "e", position = 2, createdAt = 5)
    )

    @Test
    fun move_withinGroup() {
        assertEquals(listOf(1, 2, 5, 3, 4), PasteLogic.move(items, 5, 3)!!.map { it.id })
        assertEquals(listOf(2, 1, 3, 4, 5), PasteLogic.move(items, 1, 2)!!.map { it.id })
    }

    @Test
    fun move_acrossPinnedBoundary_orUnknown_isRejected() {
        assertNull(PasteLogic.move(items, 3, 2))
        assertNull(PasteLogic.move(items, 1, 5))
        assertNull(PasteLogic.move(items, 99, 1))
    }

    @Test
    fun positionsFor_isDenseInDisplayOrder() {
        assertEquals(listOf(PositionUpdate(4, 0), PositionUpdate(3, 1)), PasteLogic.positionsFor(listOf(4, 3)))
    }

    @Test
    fun sort_customKeepsPinnedFirst() {
        val shuffled = items.reversed()
        assertEquals(listOf(1, 2, 3, 4, 5), PasteLogic.sort(shuffled, SortOrder.CUSTOM).map { it.id })
        assertEquals(listOf(2, 1, 5, 4, 3), PasteLogic.sort(shuffled, SortOrder.TIME).map { it.id })
    }

    @Test
    fun sortPrefs_parse_fallsBackToTime() {
        assertEquals(SortOrder.CUSTOM, SortPrefs.parse("CUSTOM"))
        assertEquals(SortOrder.ALPHA, SortPrefs.parse("ALPHA"))
        assertEquals(SortOrder.TIME, SortPrefs.parse(null))
        assertEquals(SortOrder.TIME, SortPrefs.parse("bogus"))
    }
}
