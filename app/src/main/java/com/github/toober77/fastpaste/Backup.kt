package com.github.toober77.fastpaste

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** On-disk backup format. Bump [Backup.FORMAT_VERSION] on incompatible changes. */
@Serializable
data class BackupFile(
    @SerialName("version") val version: Int,
    @SerialName("app") val app: String = Backup.APP_ID,
    @SerialName("exportedAt") val exportedAt: Long = 0L,
    @SerialName("items") val items: List<BackupItem> = emptyList()
)

@Serializable
data class BackupItem(
    @SerialName("content") val content: String,
    @SerialName("label") val label: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("favorite") val favorite: Boolean = false
)

class BackupFormatException(val reason: Reason, cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason { INVALID, UNSUPPORTED_VERSION, TOO_LARGE }
}

data class MergeResult(val toInsert: List<PasteItem>, val skipped: Int)

object Backup {
    const val FORMAT_VERSION = 1
    const val APP_ID = "FastPaste"
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(items: List<PasteItem>, exportedAt: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                version = FORMAT_VERSION,
                exportedAt = exportedAt,
                items = items.map {
                    BackupItem(
                        content = it.content,
                        label = it.label,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        pinned = it.pinned,
                        favorite = it.favorite
                    )
                }
            )
        )

    /** @throws BackupFormatException for malformed JSON, wrong shape or a newer format version. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException(BackupFormatException.Reason.INVALID, e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(BackupFormatException.Reason.INVALID, e)
        }
        if (file.version < 1) throw BackupFormatException(BackupFormatException.Reason.INVALID)
        if (file.version > FORMAT_VERSION) {
            throw BackupFormatException(BackupFormatException.Reason.UNSUPPORTED_VERSION)
        }
        return file
    }

    /**
     * Merges [incoming] into [existing]: entries are normalised like user input, blank ones are
     * dropped, and anything whose (content, label) already exists — in the DB or earlier in the
     * same file — is skipped. New rows get fresh ids.
     */
    fun merge(
        existing: List<PasteItem>,
        incoming: List<BackupItem>,
        now: Long = System.currentTimeMillis()
    ): MergeResult {
        val seen = existing.mapTo(HashSet()) {
            PasteLogic.normalizeContent(it.content) to PasteLogic.normalizeLabel(it.label)
        }
        val toInsert = ArrayList<PasteItem>()
        var skipped = 0
        for (b in incoming) {
            val content = PasteLogic.normalizeContent(b.content)
            val label = PasteLogic.normalizeLabel(b.label)
            if (content.isEmpty() || !seen.add(content to label)) {
                skipped++
                continue
            }
            val created = if (b.createdAt > 0) b.createdAt else now
            toInsert += PasteItem(
                content = content,
                label = label,
                createdAt = created,
                updatedAt = if (b.updatedAt >= created) b.updatedAt else created,
                pinned = b.pinned,
                favorite = b.favorite
            )
        }
        return MergeResult(toInsert, skipped)
    }

    fun defaultFileName(date: java.time.LocalDate = java.time.LocalDate.now()): String =
        String.format(java.util.Locale.ROOT, "fastpaste-backup-%04d%02d%02d.json", date.year, date.monthValue, date.dayOfMonth)
}
