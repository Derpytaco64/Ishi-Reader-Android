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
private const val ACTION_ID_COPY = 4
private const val ACTION_ID_DICTIONARY = 5

/**
 * Adds "Highlight", "Note", "Bookmark", "Copy", and (when configured) "Dictionary" items to the
 * text-selection floating toolbar -- the hook Readium Kotlin exposes for this
 * (EpubNavigatorFragment.Configuration.selectionActionModeCallback). Mirrors the three
 * non-dictionary actions SelectionPopover.tsx offers for a fresh (non-existing) selection, plus a
 * "Copy" action that has no website equivalent (the site never disabled the browser's own native
 * selection Copy the way this custom callback has to).
 * "Highlight" hands the whole [Selection] (locator + on-screen rect) back rather than just a
 * Locator, since the caller uses the rect to anchor the website's color-swatch popover
 * (HighlightColorPopover) right at the selection, mirroring SelectionPopover.tsx, instead of
 * silently applying a default color the way the old single "Highlight" action used to.
 *
 * "Dictionary" is an Android-only addition with no website equivalent (Moon+ Reader-style external
 * lookup): it's only added to the menu when [isDictionaryConfigured] is true, i.e. the user picked
 * a lookup app (one supporting Android's ACTION_PROCESS_TEXT) in Reader Settings. Otherwise the
 * item is simply omitted rather than shown disabled, since ActionMode's floating toolbar has no
 * disabled-item affordance worth explaining.
 *
 * Providing a custom callback here replaces the WebView's default action mode entirely (confirmed
 * in R2BasicWebView.startActionMode -- it bypasses super.startActionMode when a custom callback is
 * set), so stock Copy/Share are intentionally not offered by default -- this is a dedicated
 * annotation menu, not a general text-selection menu -- Copy is added back explicitly above since
 * losing the ability to copy selected text entirely wasn't the goal.
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
    private val onBookmark: (Locator) -> Unit,
    private val onCopy: (Locator) -> Unit,
    /** Whether the user has picked a dictionary/lookup app in Reader Settings -- checked fresh
     *  each time a selection starts (rather than baked in at construction) since this callback
     *  outlives any one settings change. */
    private val isDictionaryConfigured: () -> Boolean,
    private val onDictionary: (Locator) -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, ACTION_ID_HIGHLIGHT, 1, "Highlight")
        menu.add(Menu.NONE, ACTION_ID_NOTE, 2, "Note")
        menu.add(Menu.NONE, ACTION_ID_BOOKMARK, 3, "Bookmark")
        menu.add(Menu.NONE, ACTION_ID_COPY, 4, "Copy")
        if (isDictionaryConfigured()) {
            menu.add(Menu.NONE, ACTION_ID_DICTIONARY, 5, "Dictionary")
        }
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
            ACTION_ID_COPY -> scope.launch {
                val navigator = navigatorProvider() ?: return@launch
                navigator.currentSelection()?.locator?.let(onCopy)
                navigator.clearSelection()
            }
            ACTION_ID_DICTIONARY -> scope.launch {
                val navigator = navigatorProvider() ?: return@launch
                navigator.currentSelection()?.locator?.let(onDictionary)
                navigator.clearSelection()
            }
            else -> return false
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
