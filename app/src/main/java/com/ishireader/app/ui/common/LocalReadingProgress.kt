package com.ishireader.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.ishireader.app.data.repository.PositionRepository

/**
 * Lets [BookCoverCard] look up each book's local reading percent (see
 * [PositionRepository.localPercent]) to draw its progress border, without threading that lookup
 * through every screen/ViewModel that shows a grid of covers -- same reasoning as
 * [LocalBookAvailability]'s per-book download check. Null (the default) draws no border, which
 * only actually happens before MainActivity provides the real repository.
 */
val LocalReadingProgress = compositionLocalOf<PositionRepository?> { null }
