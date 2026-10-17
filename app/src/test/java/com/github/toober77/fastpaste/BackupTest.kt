package com.github.toober77.fastpaste

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupTest {

    private fun assertDecodeFails(text: String, reason: BackupFormatException.Reason) {
        try {
            Backup.decode(text)
            fail("Expected $reason for: $text")
        } catch (e: BackupFormatException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun encode_writesVersionAndAllFields() {
        val text = Backup.encode(
            listOf(PasteItem(id = 7, content = "a", label = "L", createdAt = 10, pinned = true, favorite = true, updatedAt = 20)),
            exportedAt = 99
        )
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(1, root["version"]!!.jsonPrimitive.int)
        val item = root["items"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("content", "label", "createdAt", "updatedAt", "pinned", "favorite", "position"), item.keys)
        assertEquals("true", item["pinned"]!!.jsonPrimitive.content)
    }

    @Test
    fun roundTrip_preservesEverythingButId() {
        val items = listOf(
            PasteItem(id = 1, content = "多行\n內容 \"quoted\"", label = "工作", createdAt = 5, pinned = true, updatedAt = 6),
            PasteItem(id = 2, content = "plain", createdAt = 7, favorite = true)
        )
        val decoded = Backup.decode(Backup.encode(items))
        assertEquals(
            items.map { BackupItem(it.content, it.label, it.createdAt, it.updatedAt, it.pinned, it.favorite, it.position) },
            decoded.items
        )
    }

    @Test
    fun decode_toleratesMissingOptionalAndUnknownFields() {
        val file = Backup.decode("""{"version":1,"extra":true,"items":[{"content":"x","future":1}]}""")
        assertEquals(listOf(BackupItem(content = "x")), file.items)
    }

    @Test
    fun decode_rejectsBadInput() {
        assertDecodeFails("", BackupFormatException.Reason.INVALID)
        assertDecodeFails("garbage", BackupFormatException.Reason.INVALID)
        assertDecodeFails("[1,2,3]", BackupFormatException.Reason.INVALID)
        assertDecodeFails("""{"items":[]}""", BackupFormatException.Reason.INVALID) // no version
        assertDecodeFails("""{"version":1,"items":[{"label":"no content"}]}""", BackupFormatException.Reason.INVALID)
        assertDecodeFails("""{"version":"one","items":[]}""", BackupFormatException.Reason.INVALID)
        assertDecodeFails("""{"version":0,"items":[]}""", BackupFormatException.Reason.INVALID)
        assertDecodeFails("""{"version":2,"items":[]}""", BackupFormatException.Reason.UNSUPPORTED_VERSION)
    }

    @Test
    fun merge_skipsExistingAndInFileDuplicates_andBlanks() {
        val existing = listOf(PasteItem(id = 1, content = "hello", label = "a"))
        val incoming = listOf(
            BackupItem("hello", "a"),        // exists
            BackupItem("  hello ", " a "),   // exists after trimming
            BackupItem("hello", "b"),        // different label -> new
            BackupItem("world"),             // new
            BackupItem("world"),             // duplicate within file
            BackupItem("   ")                // blank
        )
        val result = Backup.merge(existing, incoming, now = 500)
        assertEquals(listOf("hello" to "b", "world" to ""), result.toInsert.map { it.content to it.label })
        assertEquals(4, result.skipped)
        assertTrue(result.toInsert.all { it.id == 0 })
    }

    @Test
    fun merge_keepsFlagsAndTimes_withSaneFallbacks() {
        val result = Backup.merge(
            emptyList(),
            listOf(
                BackupItem("a", createdAt = 10, updatedAt = 30, pinned = true, favorite = true),
                BackupItem("b", createdAt = 0, updatedAt = 0),
                BackupItem("c", createdAt = 40, updatedAt = 5)
            ),
            now = 500
        )
        val (a, b, c) = result.toInsert
        assertEquals(listOf(10L, 30L, true, true), listOf(a.createdAt, a.updatedAt, a.pinned, a.favorite))
        assertEquals(500L to 500L, b.createdAt to b.updatedAt)
        assertEquals(40L to 40L, c.createdAt to c.updatedAt)
    }

    @Test
    fun merge_capsOverlongContent() {
        val result = Backup.merge(emptyList(), listOf(BackupItem("x".repeat(PasteLogic.MAX_CONTENT_LENGTH + 10))))
        assertEquals(PasteLogic.MAX_CONTENT_LENGTH, result.toInsert.single().content.length)
    }

    @Test
    fun defaultFileName_usesDate() {
        assertEquals("fastpaste-backup-20260925.json", Backup.defaultFileName(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun position_roundTrips_andIsOptional() {
        val text = Backup.encode(listOf(PasteItem(content = "x", position = 7)), exportedAt = 1)
        assertEquals(7, Backup.decode(text).items.single().position)
        val old = Backup.decode("""{"version":1,"items":[{"content":"y"}]}""")
        assertEquals(null, old.items.single().position)
    }

    @Test
    fun merge_placesImportsAboveExisting_inBackupPositionOrder() {
        val incoming = listOf(
            BackupItem("legacy"),
            BackupItem("second", position = 1),
            BackupItem("first", position = 0)
        )
        val result = Backup.merge(emptyList(), incoming, now = 500, existingMinPosition = -2)
        assertEquals(listOf("first", "second", "legacy"), result.toInsert.map { it.content })
        assertEquals(listOf(-5, -4, -3), result.toInsert.map { it.position })
    }
}
