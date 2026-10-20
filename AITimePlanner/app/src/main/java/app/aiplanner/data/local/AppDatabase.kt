package app.aiplanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.aiplanner.data.local.dao.GoalDao
import app.aiplanner.data.local.dao.ScheduleDao
import app.aiplanner.data.local.dao.StatsDao
import app.aiplanner.data.local.dao.TaskDao
import app.aiplanner.data.local.entity.CompletionEventEntity
import app.aiplanner.data.local.entity.GoalEntity
import app.aiplanner.data.local.entity.ScheduleBlockEntity
import app.aiplanner.data.local.entity.TaskEntity

@Database(
    entities = [
        GoalEntity::class,
        TaskEntity::class,
        ScheduleBlockEntity::class,
        CompletionEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "ai_time_planner.db"
    }
}
