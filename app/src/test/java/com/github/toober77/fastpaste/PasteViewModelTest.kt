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
        val vm = PasteViewModel(dao) { changes++ }
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
}
