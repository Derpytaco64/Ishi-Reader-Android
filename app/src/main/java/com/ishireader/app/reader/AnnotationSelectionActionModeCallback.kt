package com.ishireader.app.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.shared.publication.Locator

private const val ACTION_ID_HIGHLIGHT = 1
private const val ACTION_ID_NOTE = 2
private const val ACTION_ID_BOOKMARK = 3

/**
 * Adds "Highlight", "Note", and "Bookmark" items to the text-selection floating toolbar -- the
 * hook Readium Kotlin exposes for this
 * (EpubNavigatorFragment.Configuration.selectionActionModeCallback). Mirrors the three
 * non-dictionary actions SelectionPopover.tsx offers for a fresh (non-existing) selection.
 * "Highlight" hands the whole [Selection] (locator + on-screen rect) back rather than just a
 * Locator, since the caller uses the rect to anchor the website's color-swatch popover
 * (HighlightColorPopover) right at the selection, mirroring SelectionPopover.tsx, instead of
 * silently applying a default color the way the old single "Highlight" action used to.
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
    private val onHighlight: (Selection) -> Unit,
    private val onNote: (Locator) -> Unit,
    private val onBookmark: (Locator) -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, ACTION_ID_HIGHLIGHT, 1, "Highlight")
        menu.add(Menu.NONE, ACTION_ID_NOTE, 2, "Note")
        menu.add(Menu.NONE, ACTION_ID_BOOKMARK, 3, "Bookmark")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            ACTION_ID_HIGHLIGHT -> scope.launch {
                val navigator = navigatorProvider() ?: return@launch
                navigator.currentSelection()?.let(onHighlight)
                navigator.clearSelection()
            }
            ACTION_ID_NOTE -> scope.launch {
                val navigator = navigatorProvider() ?: return@launch
                navigator.currentSelection()?.locator?.let(onNote)
                navigator.clearSelection()
            }
            ACTION_ID_BOOKMARK -> scope.launch {
                val navigator = navigatorProvider() ?: return@launch
                navigator.currentSelection()?.locator?.let(onBookmark)
                navigator.clearSelection()
            }
            else -> return false
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
