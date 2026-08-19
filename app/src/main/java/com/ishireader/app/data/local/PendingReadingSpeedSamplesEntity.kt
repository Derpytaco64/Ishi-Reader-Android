package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Outbox of new WPM samples recorded locally since the last successful sync, not yet folded into
 *  the server's global buffer -- [com.ishireader.app.data.sync.ReadingTimerReconciler] appends
 *  these onto a fresh server read (trimmed to the buffer's max size) before POSTing, instead of
 *  the old behavior of POSTing this device's whole in-memory buffer (which, seeded from a failed
 *  offline GET, was an empty list that wiped every other book's WPM history on the next successful
 *  flush). Single row, same convention as [PendingLibraryPrefsPatchEntity]. */
@Entity(tableName = "pending_reading_speed_samples")
data class PendingReadingSpeedSamplesEntity(
    @PrimaryKey val id: Int = 0,
    val samplesJson: String
)
