package app.spread.data

import android.content.Context
import android.net.Uri
import app.spread.domain.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Repository for loading and managing books.
 */
class BookRepository(private val context: Context) {

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
