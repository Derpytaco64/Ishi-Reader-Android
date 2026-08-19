package com.ishireader.app.data.repository

import com.ishireader.app.data.model.Book
import com.ishireader.app.data.model.manifestUrl
import com.ishireader.app.data.network.dataOrNull

/**
 * Proactively seeds every Room cache a downloaded book's UI needs (time/pace, daily history,
 * completed reads/listens, highlights/bookmarks/notes, last-known position) so a book that's been
 * downloaded for offline reading shows its real data immediately in offline mode instead of only
 * after the user opens it once while online -- every repository this touches is already
 * local-first (see ReadingTimerReconciler/ListeningTimerReconciler/LocalFirstAnnotationStore's own
 * doc comments), so calling their plain getters here is enough: a successful GET caches the result
 * exactly the same way opening the book or viewing its detail screen would.
 */
class LibraryMetadataPrefetcher(
    private val libraryRepository: LibraryRepository,
    private val bookDownloadRepository: BookDownloadRepository,
    private val positionRepository: PositionRepository,
    private val readingTimerRepository: ReadingTimerRepository,
    private val completedReadsRepository: CompletedReadsRepository,
    private val annotationsRepository: AnnotationsRepository,
    private val notesRepository: NotesRepository,
    private val listeningTimeRepository: ListeningTimeRepository
) {

    /** Every currently-downloaded book, refetched from the server's book list -- run only under a
     *  [androidx.work.NetworkType.CONNECTED] constraint (see LibraryMetadataSyncWorker), so this
     *  network round trip is never attempted while actually offline. */
    suspend fun prefetchAllDownloaded() {
        val books = libraryRepository.fetchBooks().dataOrNull() ?: return
        books.filter { bookDownloadRepository.isDownloaded(it.manifestUrl()) }
            .forEach { prefetchOne(it) }
    }

    /** Everything one book's detail screen / in-reader timer would otherwise fetch on first
     *  online view -- see BookDetailViewModel.loadBookDetail and ReadingTimerTracker/
     *  ListeningTimeTracker.start for the equivalent per-book call sets this mirrors. */
    suspend fun prefetchOne(book: Book) {
        val manifestUrl = book.manifestUrl()
        positionRepository.getPosition(manifestUrl)
        if (book.isAudiobook) {
            listeningTimeRepository.getListeningTime(manifestUrl)
            listeningTimeRepository.getDailyListeningHistory(manifestUrl)
            listeningTimeRepository.getCompletedListens(manifestUrl)
        } else {
            readingTimerRepository.getReadingTimeSeconds(manifestUrl)
            readingTimerRepository.getWordCount(manifestUrl)
            readingTimerRepository.getDailyReadingHistory(manifestUrl)
            readingTimerRepository.getPageCount(manifestUrl)
            readingTimerRepository.getReadingSpeedSamples()
            completedReadsRepository.getCompletedReadTimes(manifestUrl)
        }
        annotationsRepository.getHighlights(manifestUrl)
        annotationsRepository.getBookmarks(manifestUrl)
        notesRepository.getNotes(manifestUrl)
    }
}
