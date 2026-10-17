package com.github.toober77.fastpaste

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasteViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakePasteDao
    private var changes = 0

    private companion object {
        const val NOW = 1_000L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakePasteDao(
            listOf(
                PasteItem(id = 1, content = "banana", label = "fruit", createdAt = 100),
                PasteItem(id = 2, content = "Apple", createdAt = 300),
                PasteItem(id = 3, content = "cherry", createdAt = 200)
            )
        )
        changes = 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): PasteViewModel {
        val vm = PasteViewModel(dao, ioDispatcher = dispatcher, clock = { NOW }, onDataChanged = { changes++ })
        backgroundScope.launch(dispatcher) { vm.items.collect {} }
        return vm
    }

    @Test
    fun items_nullUntilSubscribed_thenSortedByTime() = runTest(dispatcher) {
        val vm = PasteViewModel(dao)
        assertNull(vm.items.value)
        backgroundScope.launch(dispatcher) { vm.items.collect {} }
        assertEquals(listOf(2, 3, 1), vm.items.value!!.map { it.id })
    }

    @Test
    fun sortAlpha_isCaseInsensitive_andSwitchesQuery() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSortOrder(SortOrder.ALPHA)
        assertEquals(listOf("Apple", "banana", "cherry"), vm.items.value!!.map { it.content })
        assertEquals(1, dao.timeQueries)
        assertEquals(1, dao.alphaQueries)
    }

    @Test
    fun search_filtersWithoutNewDbQuery() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSearchQuery("FRUIT")
        assertEquals(listOf(1), vm.items.value!!.map { it.id })
        vm.setSearchQuery("")
        assertEquals(3, vm.items.value!!.size)
        assertEquals(1, dao.timeQueries)
    }

    @Test
    fun addItem_trimsInput_ignoresBlank_andNotifiesWidget() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.addItem("   ", "x")
        assertEquals(3, dao.state.value.size)
        assertEquals(0, changes)

        vm.addItem("  hello  ", "  tag ")
        val added = dao.state.value.single { it.content == "hello" }
        assertEquals("tag", added.label)
        assertEquals(1, changes)
    }

    @Test
    fun isDuplicate_usesUnfilteredList() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.setSearchQuery("zzz")
        assertTrue(vm.isDuplicate(" cherry "))
        assertFalse(vm.isDuplicate("durian"))
    }

    @Test
    fun selection_toggleSelectAndClear() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.select(1)
        vm.toggleSelection(2)
        assertEquals(setOf(1, 2), vm.selectedIds.value)
        vm.toggleSelection(1)
        assertEquals(setOf(2), vm.selectedIds.value)
        vm.clearSelection()
        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun deleteSelected_emitsEvent_andRestoreBringsItemsBack() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.select(1)
        vm.select(3)
        vm.deleteSelected()

        assertTrue(vm.selectedIds.value.isEmpty())
        assertEquals(listOf(2), vm.items.value!!.map { it.id })
        val event = vm.events.first() as PasteEvent.Deleted
        assertEquals(setOf(1, 3), event.items.map { it.id }.toSet())

        vm.restore(event.items)
        assertEquals(listOf(2, 3, 1), vm.items.value!!.map { it.id })
        // Original label and timestamp survive the round trip.
        assertEquals(PasteItem(id = 1, content = "banana", label = "fruit", createdAt = 100),
            dao.state.value.single { it.id == 1 })
        assertEquals(2, changes)
    }

    @Test
    fun deleteSelected_withEmptySelection_doesNothing() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.deleteSelected()
        assertEquals(3, dao.state.value.size)
        assertEquals(0, changes)
    }

    @Test
    fun pinnedItems_sortFirst_inBothOrders() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.togglePinned(dao.state.value.single { it.id == 3 }) // cherry
        assertEquals(listOf(3, 2, 1), vm.items.value!!.map { it.id })
        vm.setSortOrder(SortOrder.ALPHA)
        assertEquals(listOf("cherry", "Apple", "banana"), vm.items.value!!.map { it.content })
    }

    @Test
    fun favoritesOnly_filtersAndCombinesWithSearch() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.toggleFavorite(dao.state.value.single { it.id == 1 })
        vm.toggleFavorite(dao.state.value.single { it.id == 2 })
        vm.setFavoritesOnly(true)
        assertEquals(listOf(2, 1), vm.items.value!!.map { it.id })
        vm.setSearchQuery("ban")
        assertEquals(listOf(1), vm.items.value!!.map { it.id })
        vm.setFavoritesOnly(false)
        vm.setSearchQuery("")
        assertEquals(3, vm.items.value!!.size)
    }

    @Test
    fun updateItem_keepsCreatedAtAndFlags_setsUpdatedAt() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.togglePinned(dao.state.value.single { it.id == 1 })
        vm.toggleFavorite(dao.state.value.single { it.id == 1 })
        vm.updateItem(1, "  blueberry ", " berry ")
        val edited = dao.state.value.single { it.id == 1 }
        assertEquals(PasteItem(1, "blueberry", "berry", createdAt = 100, pinned = true, favorite = true, updatedAt = NOW), edited)
        assertTrue(vm.isDuplicate("blueberry"))
        assertFalse(vm.isDuplicate("blueberry", excludeId = 1))
    }

    @Test
    fun updateItem_blankContent_isIgnored() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.updateItem(1, "   ", "x")
        assertEquals("banana", dao.state.value.single { it.id == 1 }.content)
    }

    @Test
    fun bulkPin_pinsMixedSelection_thenUnpinsWhenAllPinned() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch(dispatcher) { vm.selectionFlags.collect {} }
        vm.togglePinned(dao.state.value.single { it.id == 1 })
        vm.select(1)
        vm.select(2)
        assertFalse(vm.selectionFlags.value.allPinned)
        vm.togglePinnedForSelection()
        assertTrue(dao.state.value.filter { it.id in setOf(1, 2) }.all { it.pinned })
        assertTrue(vm.selectionFlags.value.allPinned)
        vm.togglePinnedForSelection()
        assertTrue(dao.state.value.none { it.pinned })

        vm.toggleFavoriteForSelection()
        assertTrue(dao.state.value.filter { it.id in setOf(1, 2) }.all { it.favorite })
    }

    @Test
    fun deleteItem_undoRestoresPinFavoriteAndTimes() = runTest(dispatcher) {
        val original = PasteItem(9, "otp", "sms", createdAt = 50, pinned = true, favorite = true, updatedAt = 70)
        dao.state.value = dao.state.value + original
        val vm = newViewModel()
        vm.deleteItem(9)
        val event = vm.events.first() as PasteEvent.Deleted
        assertTrue(dao.state.value.none { it.id == 9 })
        vm.restore(event.items)
        assertEquals(original, dao.state.value.single { it.id == 9 })
        assertEquals(9, vm.items.value!!.first().id) // pinned -> first again
    }

    @Test
    fun export_writesAllItemsAsJson() = runTest(dispatcher) {
        val vm = newViewModel()
        val out = java.io.ByteArrayOutputStream()
        vm.exportBackup { out }
        assertEquals(PasteEvent.Exported(3), vm.events.first())
        val file = Backup.decode(out.toString(Charsets.UTF_8.name()))
        assertEquals(Backup.FORMAT_VERSION, file.version)
        assertEquals(setOf("banana", "Apple", "cherry"), file.items.map { it.content }.toSet())
    }

    @Test
    fun export_failingStream_reportsWriteError() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.exportBackup { null }
        assertEquals(PasteEvent.BackupFailed(BackupError.WRITE), vm.events.first())
    }

    @Test
    fun import_mergesAndSkipsDuplicates() = runTest(dispatcher) {
        val vm = newViewModel()
        val json = Backup.encode(
            listOf(
                PasteItem(content = "banana", label = "fruit", createdAt = 1), // duplicate
                PasteItem(content = "banana", label = "", createdAt = 2),      // different label -> new
                PasteItem(content = "kiwi", createdAt = 3, pinned = true, favorite = true)
            )
        )
        vm.importBackup { json.byteInputStream() }
        assertEquals(PasteEvent.Imported(added = 2, skipped = 1), vm.events.first())
        assertEquals(5, dao.state.value.size)
        val kiwi = dao.state.value.single { it.content == "kiwi" }
        assertTrue(kiwi.pinned && kiwi.favorite)
        assertEquals(3L, kiwi.createdAt)
        assertEquals(1, changes)
    }

    @Test
    fun import_corruptOrForeignFiles_reportErrorsWithoutChanges() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.importBackup { "not json {".byteInputStream() }
        assertEquals(PasteEvent.BackupFailed(BackupError.INVALID), vm.events.first())
        vm.importBackup { """{"version": 99, "items": []}""".byteInputStream() }
        assertEquals(PasteEvent.BackupFailed(BackupError.UNSUPPORTED_VERSION), vm.events.first())
        vm.importBackup { throw java.io.FileNotFoundException("gone") }
        assertEquals(PasteEvent.BackupFailed(BackupError.READ), vm.events.first())
        vm.importBackup { java.io.ByteArrayInputStream(ByteArray(Backup.MAX_BYTES + 1)) }
        assertEquals(PasteEvent.BackupFailed(BackupError.TOO_LARGE), vm.events.first())
        assertEquals(3, dao.state.value.size)
        assertEquals(0, changes)
    }

    @Test
    fun applyCustomOrder_savesPositions_andSwitchesToCustom() = runTest(dispatcher) {
        val saved = mutableListOf<SortOrder>()
        val vm = PasteViewModel(dao, ioDispatcher = dispatcher, clock = { NOW }, onSortOrderChanged = { saved += it })
        backgroundScope.launch(dispatcher) { vm.items.collect {} }
        vm.applyCustomOrder(listOf(3, 1, 2))
        assertEquals(SortOrder.CUSTOM, vm.sortOrder.value)
        assertEquals(listOf(SortOrder.CUSTOM), saved)
        assertEquals(listOf(3, 1, 2), vm.items.value!!.map { it.id })
        // Back to time and then custom again: the custom order is remembered.
        vm.setSortOrder(SortOrder.TIME)
        assertEquals(listOf(2, 3, 1), vm.items.value!!.map { it.id })
        vm.setSortOrder(SortOrder.CUSTOM)
        assertEquals(listOf(3, 1, 2), vm.items.value!!.map { it.id })
        assertEquals(listOf(SortOrder.CUSTOM, SortOrder.TIME, SortOrder.CUSTOM), saved)
    }

    @Test
    fun initialSortOrder_isUsed_andNewItemsGoToTopOfCustomOrder() = runTest(dispatcher) {
        val vm = PasteViewModel(dao, ioDispatcher = dispatcher, clock = { NOW }, initialSortOrder = SortOrder.CUSTOM)
        backgroundScope.launch(dispatcher) { vm.items.collect {} }
        vm.applyCustomOrder(listOf(1, 2, 3))
        vm.addItem("fresh")
        assertEquals("fresh", vm.items.value!!.first().content)
        assertEquals(1, dao.positionQueries)
    }
}
