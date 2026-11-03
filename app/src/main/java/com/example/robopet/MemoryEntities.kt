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

@Entity(tableName = "relationship_stats")
data class RelationshipStatsEntity(
    @PrimaryKey
    val id: Int = 1,
    val score: Int = 120, // 0 to 1000
    val totalInteractions: Int = 0,
    val petCount: Int = 0,
    val chatCount: Int = 0,
    val gestureCount: Int = 0,
    val affection: Float = 0.65f,
    val sociability: Float = 0.45f,
    val playfulness: Float = 0.75f,
    val curiosity: Float = 0.70f,
    val independence: Float = 0.55f,
    val lastInteractionDate: Long = System.currentTimeMillis()
) {
    val stage: String
        get() = when {
            score >= 800 -> "Best Friend / Soulmate"
            score >= 500 -> "Close Companion"
            score >= 250 -> "Familiar Friend"
            else -> "New Acquaintance"
        }

    val stageLevel: Int
        get() = when {
            score >= 800 -> 4
            score >= 500 -> 3
            score >= 250 -> 2
            else -> 1
        }
}
