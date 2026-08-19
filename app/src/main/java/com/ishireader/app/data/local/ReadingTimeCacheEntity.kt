package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-device durable state for one book's accumulated-seconds counter. [cachedSeconds] is the
 * best-known total (what the reader should display, offline or on); [lastSyncedBaselineSeconds]
 * is the total both this device and the server last agreed on. The gap between them --
 * `cachedSeconds - lastSyncedBaselineSeconds` -- is this device's own not-yet-confirmed delta,
 * which is what [com.ishireader.app.data.sync.ReadingTimerReconciler] adds on top of a *fresh*
 * server read rather than blindly overwriting it -- see that class's doc comment for why the old
 * "whole-file overwrite" flush destroyed data across an offline session. [forceOverwrite]
 * distinguishes an intentional absolute set (the user's own "reset timer" action) from a normal
 * additive flush, since those two need opposite merge behavior.
 */
@Entity(tableName = "reading_time_cache")
data class ReadingTimeCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val cachedSeconds: Double,
    val lastSyncedBaselineSeconds: Double,
    val pendingSync: Boolean,
    val forceOverwrite: Boolean,
    val updatedAtMillis: Long
)
