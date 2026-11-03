package com.example.robopet

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        FactEntity::class,
        SummaryEntity::class,
        RelationshipStatsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RobotDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: RobotDatabase? = null

        fun getInstance(context: Context): RobotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RobotDatabase::class.java,
                    "robot_pet_memory.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
