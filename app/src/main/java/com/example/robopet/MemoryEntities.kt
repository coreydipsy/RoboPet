package com.example.robopet

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user" or "robot"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // e.g., "preference", "user_info", "pet_fact"
    val fact: String, // e.g., "User's favorite color is blue"
    val sourceSnippet: String, // Actual user utterance: "my favorite color is blue"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
