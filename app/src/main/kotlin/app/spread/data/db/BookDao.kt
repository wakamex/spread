package app.spread.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    // Books
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long)

    // Reading Progress
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)
}
