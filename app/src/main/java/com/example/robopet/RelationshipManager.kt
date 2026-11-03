package com.example.robopet

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RelationshipManager(
    context: Context,
    private val personalityEngine: PersonalityEngine? = null
) {
    private val dao: MemoryDao = RobotDatabase.getInstance(context).memoryDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _stats = MutableStateFlow(RelationshipStatsEntity())
    val stats: StateFlow<RelationshipStatsEntity> = _stats.asStateFlow()

    init {
        scope.launch {
            val existing = dao.getRelationshipStats()
            if (existing != null) {
                _stats.value = existing
                syncToPersonality(existing)
            } else {
                val initial = RelationshipStatsEntity()
                dao.upsertRelationshipStats(initial)
                _stats.value = initial
            }
        }
    }

    private fun syncToPersonality(stats: RelationshipStatsEntity) {
        personalityEngine?.updateTraits(
            PersonalityTraits(
                curiosity = stats.curiosity,
                affection = stats.affection,
                playfulness = stats.playfulness,
                independence = stats.independence,
                sociability = stats.sociability
            )
        )
    }

    fun recordPet() {
        scope.launch {
            val current = _stats.value
            val newScore = (current.score + 4).coerceAtMost(1000)
            val newAffection = (current.affection + 0.004f).coerceAtMost(0.98f)
            val updated = current.copy(
                score = newScore,
                totalInteractions = current.totalInteractions + 1,
                petCount = current.petCount + 1,
                affection = newAffection,
                lastInteractionDate = System.currentTimeMillis()
            )
            _stats.value = updated
            dao.upsertRelationshipStats(updated)
            syncToPersonality(updated)
        }
    }

    fun recordChat() {
        scope.launch {
            val current = _stats.value
            val newScore = (current.score + 6).coerceAtMost(1000)
            val newSociability = (current.sociability + 0.005f).coerceAtMost(0.95f)
            val updated = current.copy(
                score = newScore,
                totalInteractions = current.totalInteractions + 1,
                chatCount = current.chatCount + 1,
                sociability = newSociability,
                lastInteractionDate = System.currentTimeMillis()
            )
            _stats.value = updated
            dao.upsertRelationshipStats(updated)
            syncToPersonality(updated)
        }
    }

    fun recordGesture() {
        scope.launch {
            val current = _stats.value
            val newScore = (current.score + 2).coerceAtMost(1000)
            val newPlayfulness = (current.playfulness + 0.003f).coerceAtMost(0.95f)
            val updated = current.copy(
                score = newScore,
                totalInteractions = current.totalInteractions + 1,
                gestureCount = current.gestureCount + 1,
                playfulness = newPlayfulness,
                lastInteractionDate = System.currentTimeMillis()
            )
            _stats.value = updated
            dao.upsertRelationshipStats(updated)
            syncToPersonality(updated)
        }
    }

    fun recordPoints(amount: Int) {
        scope.launch {
            val current = _stats.value
            val newScore = (current.score + amount).coerceIn(0, 1000)
            val updated = current.copy(
                score = newScore,
                totalInteractions = current.totalInteractions + 1,
                lastInteractionDate = System.currentTimeMillis()
            )
            _stats.value = updated
            dao.upsertRelationshipStats(updated)
            syncToPersonality(updated)
        }
    }

    fun resetRelationship() {
        scope.launch {
            val initial = RelationshipStatsEntity(id = 1)
            _stats.value = initial
            dao.upsertRelationshipStats(initial)
            syncToPersonality(initial)
        }
    }
}
