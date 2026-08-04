package com.ishireader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ishireader.app.data.model.AppSettings
import com.ishireader.app.data.model.CoverSize
import com.ishireader.app.data.model.HomeShelfId
import com.ishireader.app.data.model.ThemeMode
import com.ishireader.app.data.repository.LibraryPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns the settings drawer's state (theme/accentColor/coverSize/Home shelf order+visibility),
 *  scoped to the Activity so it survives navigation between tabs and the book detail screen. */
class SettingsViewModel(private val repository: LibraryPrefsRepository) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch { _settings.value = repository.getSettings() }
    }

    fun setTheme(mode: ThemeMode) = update(_settings.value.copy(theme = mode))

    fun setAccentColor(hex: String?) = update(_settings.value.copy(accentColor = hex))

    fun setCoverSize(size: CoverSize) = update(_settings.value.copy(coverSize = size))

    fun setShelfVisible(id: HomeShelfId, visible: Boolean) {
        update(_settings.value.copy(shelfVisibility = _settings.value.shelfVisibility + (id to visible)))
    }

    /** Swaps [id] with its neighbor in the given direction -- a plain reorder-by-one-step rather
     *  than drag-and-drop, since the drawer's list is short (4 Home shelves) and this avoids
     *  pulling in a reorderable-list dependency for something this small. */
    fun moveShelf(id: HomeShelfId, delta: Int) {
        val order = _settings.value.shelfOrder.toMutableList()
        val from = order.indexOf(id)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from == -1 || from == to) return
        order.removeAt(from)
        order.add(to, id)
        update(_settings.value.copy(shelfOrder = order))
    }

    /** Optimistic: applies locally first (so the drawer feels instant), then fires the PATCH --
     *  matches the same pattern already used by the Shelves tab and Continue Reading dismissal. */
    private fun update(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch { repository.patchSettings(newSettings) }
    }

    class Factory(private val repository: LibraryPrefsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository) as T
    }
}
