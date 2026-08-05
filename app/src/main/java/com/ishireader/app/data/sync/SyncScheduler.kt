package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues background sync of locally-pending changes. Position and library-prefs are the
 * offline-first data today; future phases (highlights, bookmarks, reading time) will add their
 * own schedule* methods here rather than their own scheduler class.
 *
 * Requests are coalesced with [ExistingWorkPolicy.KEEP] so repeated local writes while offline
 * don't pile up duplicate work -- WorkManager holds the single pending request until
 * [NetworkType.CONNECTED] is satisfied, and each run re-reads whatever's pending in Room at that
 * moment, so nothing saved while a request was already queued gets missed.
 */
class SyncScheduler(private val context: Context) {

    fun schedulePositionSync() {
        val request = OneTimeWorkRequestBuilder<PositionSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(POSITION_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleLibraryPrefsSync() {
        val request = OneTimeWorkRequestBuilder<LibraryPrefsSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(LIBRARY_PREFS_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        private const val POSITION_SYNC_WORK_NAME = "position-sync"
        private const val LIBRARY_PREFS_SYNC_WORK_NAME = "library-prefs-sync"
    }
}
