package com.ishireader.app.ui.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.CustomShelf
import com.ishireader.app.data.model.ShelfBookEntry
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.repository.LibraryPrefsRepository
import com.ishireader.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** A curated subset of the site's ~330-emoji searchable picker -- enough choice for a shelf icon
 *  without reproducing that whole searchable grid. */
val ShelfIcons = listOf(
    "📚", "❤️", "⭐", "🔥", "📖", "🎧",
    "🎭", "🐉", "👻", "🧛", "🖤", "👽",
    "🌌", "⚔️", "🧙", "👑", "💀", "👼",
    "🏰", "🌙", "☀️", "☁️", "⚡", "🌺",
    "🍁", "🍃", "🌊", "🗺️", "🔮", "🕓",
    "🏆", "🎓", "💼", "⚖️", "🔬", "🧪",
    "🚀", "🤖", "👾", "🎲", "♠️", "🎀",
    "📰", "📝", "📌", "🔖", "🏹", "🪄"
)

private val DefaultIcon = ShelfIcons.first()

data class ShelfModalState(
    val editingShelfId: String? = null,
    val name: String = "",
    val icon: String = DefaultIcon,
    /** Set when opened from the book context menu's "+ Create new shelf" -- that book is added
     *  to the shelf the instant it's created, same as the site's StatefulShelfFormModal. */
    val addBookUrl: String? = null
)

data class ShelvesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val shelves: List<CustomShelf> = emptyList(),
    val allBooks: List<Book> = emptyList(),
    val selectedShelfId: String? = null,
    val isManagingBooks: Boolean = false,
    val modal: ShelfModalState? = null,
    val pendingDeleteShelfId: String? = null
) {
    val selectedShelf: CustomShelf? get() = shelves.find { it.id == selectedShelfId }

    /** Silently drops any shelf entry whose book no longer exists in the library, same as the
     *  site's own join-by-url behavior. */
    val selectedShelfBooks: List<Book> get() {
        val shelf = selectedShelf ?: return emptyList()
        val byUrl = allBooks.associateBy { it.url }
        return shelf.books.mapNotNull { byUrl[it.url] }
    }
}

/** Reimplements useCustomShelves.ts: a flat, order-is-display-order array of shelves stored under
 *  library-prefs' customShelves key, each holding a list of member book URLs. Every mutation
 *  updates local state immediately, then PATCHes the whole array back (same pattern as the site,
 *  minus its extra localStorage mirror -- not needed here since this app has no offline mode). */
class ShelvesViewModel(
    private val libraryRepository: LibraryRepository,
    private val libraryPrefsRepository: LibraryPrefsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShelvesUiState())
    val uiState: StateFlow<ShelvesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val shelves = libraryPrefsRepository.getCustomShelves()
            when (val result = libraryRepository.fetchBooks()) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    shelves = shelves,
                    allBooks = result.data
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun selectShelf(shelfId: String) {
        _uiState.value = _uiState.value.copy(selectedShelfId = shelfId, isManagingBooks = false)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedShelfId = null, isManagingBooks = false)
    }

    fun toggleManagingBooks() {
        _uiState.value = _uiState.value.copy(isManagingBooks = !_uiState.value.isManagingBooks)
    }

    fun openCreateModal(addBookUrl: String? = null) {
        _uiState.value = _uiState.value.copy(modal = ShelfModalState(addBookUrl = addBookUrl))
    }

    fun openEditModal(shelfId: String) {
        val shelf = _uiState.value.shelves.find { it.id == shelfId } ?: return
        _uiState.value = _uiState.value.copy(modal = ShelfModalState(editingShelfId = shelfId, name = shelf.name, icon = shelf.icon))
    }

    fun closeModal() {
        _uiState.value = _uiState.value.copy(modal = null)
    }

    fun onModalNameChange(name: String) {
        val modal = _uiState.value.modal ?: return
        _uiState.value = _uiState.value.copy(modal = modal.copy(name = name))
    }

    fun onModalIconChange(icon: String) {
        val modal = _uiState.value.modal ?: return
        _uiState.value = _uiState.value.copy(modal = modal.copy(icon = icon))
    }

    fun submitModal() {
        val modal = _uiState.value.modal ?: return
        val name = modal.name.trim()
        if (name.isEmpty()) return

        val current = _uiState.value.shelves
        val updated = if (modal.editingShelfId != null) {
            current.map { shelf ->
                if (shelf.id == modal.editingShelfId) shelf.copy(name = name, icon = modal.icon) else shelf
            }
        } else {
            val initialBooks = modal.addBookUrl?.let { listOf(ShelfBookEntry(it, System.currentTimeMillis().toDouble())) } ?: emptyList()
            current + CustomShelf(id = UUID.randomUUID().toString(), name = name, icon = modal.icon, books = initialBooks)
        }
        _uiState.value = _uiState.value.copy(shelves = updated, modal = null)
        persist(updated)
    }

    fun requestDelete(shelfId: String) {
        _uiState.value = _uiState.value.copy(pendingDeleteShelfId = shelfId)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteShelfId = null)
    }

    fun confirmDelete() {
        val shelfId = _uiState.value.pendingDeleteShelfId ?: return
        val updated = _uiState.value.shelves.filterNot { it.id == shelfId }
        val stillSelected = _uiState.value.selectedShelfId == shelfId
        _uiState.value = _uiState.value.copy(
            shelves = updated,
            pendingDeleteShelfId = null,
            selectedShelfId = if (stillSelected) null else _uiState.value.selectedShelfId
        )
        persist(updated)
    }

    /** Used by the "manage books" picker grid to toggle membership for an arbitrary shelf. */
    fun toggleBookInShelf(shelfId: String, book: Book) {
        val shelf = _uiState.value.shelves.find { it.id == shelfId } ?: return
        val alreadyIn = shelf.books.any { it.url == book.url }
        setShelfMembership(shelfId, book.url, inShelf = !alreadyIn)
    }

    private fun setShelfMembership(shelfId: String, url: String, inShelf: Boolean) {
        val updated = _uiState.value.shelves.map { shelf ->
            if (shelf.id != shelfId) return@map shelf
            val without = shelf.books.filterNot { it.url == url }
            shelf.copy(books = if (inShelf) without + ShelfBookEntry(url, System.currentTimeMillis().toDouble()) else without)
        }
        _uiState.value = _uiState.value.copy(shelves = updated)
        persist(updated)
    }

    private fun persist(shelves: List<CustomShelf>) {
        viewModelScope.launch { libraryPrefsRepository.setCustomShelves(shelves) }
    }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val libraryPrefsRepository: LibraryPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShelvesViewModel(libraryRepository, libraryPrefsRepository) as T
    }
}
