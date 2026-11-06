package com.shopmanager.chat.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,            // "user" | "assistant"
    val content: String,
    val isError: Boolean = false,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun all(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isError = 0 ORDER BY id DESC LIMIT :n")
    suspend fun last(n: Int): List<MessageEntity>

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MessageDao
}
