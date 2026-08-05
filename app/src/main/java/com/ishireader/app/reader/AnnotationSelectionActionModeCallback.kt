package com.ishireader.app.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.shared.publication.Locator

private const val ACTION_ID_HIGHLIGHT = 1
private const val ACTION_ID_NOTE = 2

/**
 * Adds "Highlight" and "Note" items to the text-selection floating toolbar -- the hook Readium
 * Kotlin exposes for this (EpubNavigatorFragment.Configuration.selectionActionModeCallback).
 * Mirrors the website's SelectionPopover color-swatch/note icons, simplified to two actions since
 * Android's ActionMode has little room for a 5-swatch color picker inline; a highlight's color can
 * be changed afterward by tapping the highlight it creates (see AnnotationsController).
 *
 * Providing a custom callback here replaces the WebView's default action mode entirely (confirmed
 * in R2BasicWebView.startActionMode -- it bypasses super.startActionMode when a custom callback is
 * set), so stock Copy/Share are intentionally not offered -- this is a dedicated annotation menu,
 * not a general text-selection menu, matching the website's own selection popover which doesn't
 * offer generic copy/share either.
 */
class AnnotationSelectionActionModeCallback(
    private val scope: CoroutineScope,
    /** A supplier, not a direct instance -- this callback is built and handed to
     *  createFragmentFactory *before* the EpubNavigatorFragment (which implements
     *  SelectableNavigator) is instantiated, so there's nothing to reference yet at
     *  construction time. */
    private val navigatorProvider: () -> SelectableNavigator?,
    private val onHighlight: (Locator) -> Unit,
    private val onNote: (Locator) -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, ACTION_ID_HIGHLIGHT, 1, "Highlight")
        menu.add(Menu.NONE, ACTION_ID_NOTE, 2, "Note")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val handler = when (item.itemId) {
            ACTION_ID_HIGHLIGHT -> onHighlight
            ACTION_ID_NOTE -> onNote
            else -> return false
        }
        scope.launch {
            val navigator = navigatorProvider() ?: return@launch
            navigator.currentSelection()?.locator?.let(handler)
            navigator.clearSelection()
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
