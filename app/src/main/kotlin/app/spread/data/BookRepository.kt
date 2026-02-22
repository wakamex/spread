package app.spread.data

import android.content.Context
import android.net.Uri
import app.spread.data.db.AppDatabase
import app.spread.data.db.BookDao
import app.spread.data.db.BookEntity
import app.spread.data.db.ReadingProgressEntity
import app.spread.domain.Book
import app.spread.domain.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import java.security.MessageDigest

/**
 * Stored book info for the library (doesn't include parsed content).
 */
data class StoredBook(
    val id: String,
    val title: String,
    val author: String?,
    val fileUri: String,
    val addedAt: Long,
    val lastOpenedAt: Long?
)

/**
 * Repository for loading and managing books.
 */
class BookRepository(private val context: Context) {

    private val dao: BookDao by lazy {
        AppDatabase.getInstance(context).bookDao()
    }

    // --- Book Library ---

    /**
     * Get all books in the library as a Flow.
     */
    fun getAllBooks(): Flow<List<StoredBook>> {
        return dao.getAllBooks().map { entities ->
            entities.map { it.toStoredBook() }
        }
    }

    /**
     * Save a book to the library.
     */
    suspend fun saveBookToLibrary(book: Book, fileUri: Uri) {
        val entity = BookEntity(
            id = book.id.value,
            title = book.metadata.title,
            author = book.metadata.author,
            fileUri = fileUri.toString(),
            addedAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis()
        )
        dao.insertBook(entity)
    }

    /**
     * Update last opened timestamp for a book.
     */
    suspend fun updateLastOpened(bookId: String) {
        dao.updateLastOpened(bookId, System.currentTimeMillis())
    }

    /**
     * Delete a book from the library.
     */
    suspend fun deleteBook(bookId: String) {
        dao.deleteBookById(bookId)
        dao.deleteProgress(bookId)
    }

    // --- Reading Progress ---

    /**
     * Get reading progress for a book.
     */
    suspend fun getProgress(bookId: String): Position? {
        return dao.getProgress(bookId)?.toPosition()
    }

    /**
     * Save reading progress for a book.
     */
    suspend fun saveProgress(bookId: String, position: Position) {
        val entity = ReadingProgressEntity(
            bookId = bookId,
            chapterIndex = position.chapterIndex,
            wordIndex = position.wordIndex,
            updatedAt = System.currentTimeMillis()
        )
        dao.saveProgress(entity)
    }

    // --- EPUB Parsing ---

    /**
     * Parse an EPUB file from a content URI.
     */
    suspend fun loadBook(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI: $uri")

            val data = inputStream.use { it.readBytes() }
            val id = generateBookId(data)

            val nativeBook = NativeParser.parseEpub(data)
                ?: throw IllegalStateException("Failed to parse EPUB")

            nativeBook.toDomain(id)
        }.onFailure {
            Firebase.crashlytics.log("loadBook: uri=$uri")
            Firebase.crashlytics.recordException(it)
        }
    }

    /**
     * Parse an EPUB from raw bytes.
     */
    suspend fun loadBookFromBytes(data: ByteArray, fileName: String): Result<Book> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = generateBookId(data)

                val nativeBook = NativeParser.parseEpub(data)
                    ?: throw IllegalStateException("Failed to parse EPUB")

                nativeBook.toDomain(id)
            }.onFailure {
                Firebase.crashlytics.log("loadBookFromBytes: fileName=$fileName, ${data.size} bytes")
                Firebase.crashlytics.recordException(it)
            }
        }

    /**
     * Generate a stable ID for a book based on content hash.
     */
    private fun generateBookId(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile
        private var instance: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return instance ?: synchronized(this) {
                instance ?: BookRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

// --- Extension functions for entity conversion ---

private fun BookEntity.toStoredBook() = StoredBook(
    id = id,
    title = title,
    author = author,
    fileUri = fileUri,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt
)

private fun ReadingProgressEntity.toPosition() = Position(
    chapterIndex = chapterIndex,
    wordIndex = wordIndex
)
