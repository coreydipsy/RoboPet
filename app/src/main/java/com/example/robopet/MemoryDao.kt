package com.example.robopet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    // Conversations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ConversationEntity>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getMessageCount(): Int

    // Facts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: FactEntity): Long

    @Update
    suspend fun updateFact(fact: FactEntity)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteFact(id: Long)

    @Query("SELECT * FROM facts ORDER BY timestamp DESC")
    fun getAllFactsFlow(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts ORDER BY timestamp DESC")
    suspend fun getAllFacts(): List<FactEntity>

    @Query("SELECT * FROM facts WHERE LOWER(fact) = LOWER(:factText) LIMIT 1")
    suspend fun findExistingFact(factText: String): FactEntity?

    @Query("SELECT * FROM facts WHERE fact LIKE '%' || :keyword || '%' OR sourceSnippet LIKE '%' || :keyword || '%'")
    suspend fun searchFacts(keyword: String): List<FactEntity>

    // Summaries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity): Long

    @Update
    suspend fun updateSummary(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteSummary(id: Long)

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    fun getAllSummariesFlow(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    suspend fun getAllSummaries(): List<SummaryEntity>

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSummaries(limit: Int): List<SummaryEntity>
}
