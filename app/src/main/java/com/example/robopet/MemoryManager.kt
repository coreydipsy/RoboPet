package com.example.robopet

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class MemoryContext(
    val relevantFacts: List<FactEntity>,
    val recentMessages: List<ConversationEntity>,
    val recentSummaries: List<SummaryEntity>
) {
    fun toPromptString(): String {
        val parts = mutableListOf<String>()

        if (relevantFacts.isNotEmpty()) {
            val factsStr = relevantFacts.joinToString("\n") { "- ${it.fact}" }
            parts.add("KNOWN USER FACTS (Truths explicitly shared by user):\n$factsStr")
        }

        if (recentSummaries.isNotEmpty()) {
            val summariesStr = recentSummaries.joinToString("\n") { "- ${it.summary}" }
            parts.add("PAST CONVERSATION SUMMARIES:\n$summariesStr")
        }

        return parts.joinToString("\n\n")
    }
}

class MemoryManager(
    context: Context,
    private val database: RobotDatabase = RobotDatabase.getInstance(context)
) {
    private val dao = database.memoryDao()

    companion object {
        private const val TAG = "MemoryManager"
        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "but", "is", "are", "am", "was", "were",
            "in", "on", "at", "to", "for", "with", "by", "about", "of", "it", "its",
            "i", "you", "he", "she", "we", "they", "me", "my", "your", "his", "her",
            "what", "whats", "who", "where", "when", "why", "how", "do", "did", "does",
            "can", "could", "would", "should", "will", "tell", "please", "robot", "robopet"
        )
    }

    // ----------------------------------------------------
    // Full conversation history
    // ----------------------------------------------------

    suspend fun recordTurn(userText: String, robotText: String) {
        val now = System.currentTimeMillis()
        dao.insertMessage(ConversationEntity(role = "user", text = userText, timestamp = now))
        dao.insertMessage(ConversationEntity(role = "robot", text = robotText, timestamp = now + 1))

        // Check if user text contains explicit statements to remember
        extractAndSaveFacts(userText)

        // Periodically generate a short conversation summary
        checkAndSummarize()
    }

    fun getAllMessagesFlow(): Flow<List<ConversationEntity>> = dao.getAllMessagesFlow()

    suspend fun getAllMessages(): List<ConversationEntity> = dao.getAllMessages()

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun clearHistory() = dao.clearAllMessages()

    // ----------------------------------------------------
    // Recent Conversational Context
    // ----------------------------------------------------

    suspend fun getRecentMessages(limit: Int = 6): List<ConversationEntity> {
        return dao.getRecentMessages(limit).reversed()
    }

    // ----------------------------------------------------
    // Relevant Memory Retrieval
    // ----------------------------------------------------

    suspend fun retrieveRelevantContext(query: String, recentLimit: Int = 6): MemoryContext {
        val keywords = extractKeywords(query)
        val matchedFacts = mutableListOf<FactEntity>()

        val lowerQuery = query.lowercase(Locale.ROOT)
        val isPersonalQuery = lowerQuery.contains("remember") ||
                lowerQuery.contains("who am i") ||
                lowerQuery.contains("my name") ||
                lowerQuery.contains("about me") ||
                lowerQuery.contains("know me")

        if (isPersonalQuery) {
            // Retrieve all key facts for broad personal queries
            matchedFacts.addAll(dao.getAllFacts())
        } else {
            // Retrieve facts matching extracted keywords
            for (kw in keywords) {
                val results = dao.searchFacts(kw)
                for (fact in results) {
                    if (matchedFacts.none { it.id == fact.id }) {
                        matchedFacts.add(fact)
                    }
                }
            }
        }

        val recentMessages = getRecentMessages(recentLimit)
        val recentSummaries = dao.getRecentSummaries(2)

        return MemoryContext(
            relevantFacts = matchedFacts.take(8),
            recentMessages = recentMessages,
            recentSummaries = recentSummaries
        )
    }

    // ----------------------------------------------------
    // Facts and Preferences (Long-term Memory)
    // ----------------------------------------------------

    fun getAllFactsFlow(): Flow<List<FactEntity>> = dao.getAllFactsFlow()

    suspend fun getAllFacts(): List<FactEntity> = dao.getAllFacts()

    suspend fun saveFact(category: String, fact: String, sourceSnippet: String): Boolean {
        val cleanFact = fact.trim()
        if (cleanFact.isEmpty()) return false

        // Check for duplicates
        val existing = dao.findExistingFact(cleanFact)
        if (existing != null) {
            Log.d(TAG, "Fact already exists: '$cleanFact' (id=${existing.id}), skipping.")
            return false
        }

        val id = dao.insertFact(
            FactEntity(
                category = category,
                fact = cleanFact,
                sourceSnippet = sourceSnippet.trim()
            )
        )
        Log.i(TAG, "Saved new fact (id=$id): '$cleanFact'")
        return true
    }

    suspend fun updateFact(fact: FactEntity) {
        dao.updateFact(fact)
    }

    suspend fun deleteFact(id: Long) {
        dao.deleteFact(id)
    }

    /**
     * Extracts facts strictly supported by actual user statements.
     * Prevents invented memories and duplicates.
     */
    suspend fun extractAndSaveFacts(userText: String) {
        val lower = userText.trim().lowercase(Locale.ROOT)

        // Pattern 1: My name is X
        val nameMatch = Regex("^(?:hello|hi|hey)?(?:\\s+)?my name is ([a-z\\s]+)", RegexOption.IGNORE_CASE).find(lower)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            if (name.isNotEmpty() && name.split(" ").size <= 3) {
                saveFact("user_info", "User's name is $name", userText)
            }
        }

        // Pattern 2: My favorite X is Y
        val favMatch = Regex("my favorite ([a-z\\s]+) is ([a-z0-9\\s]+)", RegexOption.IGNORE_CASE).find(lower)
        if (favMatch != null) {
            val item = favMatch.groupValues[1].trim()
            val value = favMatch.groupValues[2].trim()
            if (item.isNotEmpty() && value.isNotEmpty()) {
                saveFact("preference", "User's favorite $item is $value", userText)
            }
        }

        // Pattern 3: I like / love / hate X
        val likeMatch = Regex("i (?:really )?(like|love|hate) ([a-z0-9\\s]+)", RegexOption.IGNORE_CASE).find(lower)
        if (likeMatch != null) {
            val sentiment = likeMatch.groupValues[1]
            val target = likeMatch.groupValues[2].trim()
            if (target.isNotEmpty() && target.split(" ").size <= 5 && !target.contains("you")) {
                saveFact("preference", "User $sentiment $target", userText)
            }
        }

        // Pattern 4: I have a X named Y / I have a X
        val petMatch = Regex("i have a ([a-z]+)(?: named ([a-z]+))?", RegexOption.IGNORE_CASE).find(lower)
        if (petMatch != null) {
            val petType = petMatch.groupValues[1].trim()
            val petName = petMatch.groupValues.getOrNull(2)?.trim()?.replaceFirstChar { it.uppercase() }
            if (!petName.isNullOrEmpty()) {
                saveFact("pet_fact", "User has a $petType named $petName", userText)
            } else if (petType.isNotEmpty()) {
                saveFact("user_info", "User has a $petType", userText)
            }
        }

        // Pattern 5: I live in X
        val liveMatch = Regex("i live in ([a-z\\s]+)", RegexOption.IGNORE_CASE).find(lower)
        if (liveMatch != null) {
            val location = liveMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            if (location.isNotEmpty() && location.split(" ").size <= 3) {
                saveFact("user_info", "User lives in $location", userText)
            }
        }
    }

    /**
     * Verifies that a fact suggested by Gemini is substantiated by user text before saving.
     */
    suspend fun saveGeminiSuggestedFact(suggestedFact: String?, sourceUserText: String) {
        if (suggestedFact.isNullOrBlank()) return
        val lowerFact = suggestedFact.lowercase(Locale.ROOT)
        val lowerSource = sourceUserText.lowercase(Locale.ROOT)

        // Safety check: ensure at least one significant content word in the suggested fact
        // was actually uttered by the user, preventing hallucinations
        val factWords = extractKeywords(lowerFact)
        val matchesSource = factWords.any { lowerSource.contains(it) }

        if (matchesSource) {
            saveFact("gemini_learned", suggestedFact.trim(), sourceUserText)
        } else {
            Log.w(TAG, "Rejected suggested fact '$suggestedFact' because it is not supported by user text '$sourceUserText'")
        }
    }

    // ----------------------------------------------------
    // Conversation Summaries
    // ----------------------------------------------------

    fun getAllSummariesFlow(): Flow<List<SummaryEntity>> = dao.getAllSummariesFlow()

    suspend fun getAllSummaries(): List<SummaryEntity> = dao.getAllSummaries()

    suspend fun saveSummary(summary: String) {
        dao.insertSummary(SummaryEntity(summary = summary.trim()))
    }

    suspend fun updateSummary(summary: SummaryEntity) {
        dao.updateSummary(summary)
    }

    suspend fun deleteSummary(id: Long) {
        dao.deleteSummary(id)
    }

    private suspend fun checkAndSummarize() {
        val totalCount = dao.getMessageCount()
        // If we have accumulated unsummarized messages (every 10 turns = 20 messages)
        if (totalCount >= 10 && totalCount % 10 == 0) {
            val messages = dao.getRecentMessages(10).reversed()
            val userTopics = messages.filter { it.role == "user" }
                .take(3)
                .joinToString(", ") { it.text.take(30) }
            val summaryText = "Chatted about: $userTopics"
            saveSummary(summaryText)
            Log.d(TAG, "Generated automatic summary: $summaryText")
        }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }
            .distinct()
    }
}
