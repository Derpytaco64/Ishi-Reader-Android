package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    fun scheduleReadingTimerSync() {
        val request = OneTimeWorkRequestBuilder<ReadingTimerSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(READING_TIMER_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleAnnotationsSync() {
        val request = OneTimeWorkRequestBuilder<AnnotationsSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ANNOTATIONS_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleListeningTimerSync() {
        val request = OneTimeWorkRequestBuilder<ListeningTimerSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(LISTENING_TIMER_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** True while either sync worker is actively running -- drives the library screen's sync
     *  progress ring (see MainTabsScreen's SyncProgressRing). Only [WorkInfo.State.RUNNING] counts,
     *  not ENQUEUED: a request can sit enqueued indefinitely offline, which would otherwise spin
     *  the ring forever instead of just while a sync is genuinely happening. */
    fun isSyncingFlow(): Flow<Boolean> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(POSITION_SYNC_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(LIBRARY_PREFS_SYNC_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(READING_TIMER_SYNC_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(ANNOTATIONS_SYNC_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(LISTENING_TIMER_SYNC_WORK_NAME)
        ) { position, prefs, readingTimer, annotations, listeningTimer ->
            (position + prefs + readingTimer + annotations + listeningTimer).any { it.state == WorkInfo.State.RUNNING }
        }
    }

    companion object {
        private const val POSITION_SYNC_WORK_NAME = "position-sync"
        private const val LIBRARY_PREFS_SYNC_WORK_NAME = "library-prefs-sync"
        private const val READING_TIMER_SYNC_WORK_NAME = "reading-timer-sync"
        private const val ANNOTATIONS_SYNC_WORK_NAME = "annotations-sync"
        private const val LISTENING_TIMER_SYNC_WORK_NAME = "listening-timer-sync"
    }
}
