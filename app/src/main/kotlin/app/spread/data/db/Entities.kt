package app.spread.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stored book metadata. The actual parsed book content is kept in memory
 * and re-parsed from the file when opened.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String?,
    val fileUri: String,        // Content URI for the file
    val addedAt: Long,          // Timestamp when book was added
    val lastOpenedAt: Long?     // Timestamp when book was last opened
)

/**
 * Reading progress for a book.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey
    val bookId: String,
    val chapterIndex: Int,
    val wordIndex: Int,
    val updatedAt: Long
)
