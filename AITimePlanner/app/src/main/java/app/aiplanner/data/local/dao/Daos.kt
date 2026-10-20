package app.aiplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.aiplanner.data.local.entity.CompletionEventEntity
import app.aiplanner.data.local.entity.GoalEntity
import app.aiplanner.data.local.entity.ScheduleBlockEntity
import app.aiplanner.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAtIso DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun byId(id: Long): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity): Long

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY status ASC, priority DESC, deadlineIso ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status IN ('PLANNED','IN_PROGRESS') ORDER BY priority DESC, deadlineIso ASC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE goalId = :goalId")
    suspend fun byGoal(goalId: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE title = :title")
    suspend fun countByTitle(title: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM tasks WHERE status = 'DONE' AND completedAtIso IS NOT NULL AND completedAtIso < :cutoffIso")
    suspend fun archiveDoneBefore(cutoffIso: String): Int
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_blocks WHERE startIso >= :fromIso AND startIso < :toIso ORDER BY startIso ASC")
    fun observeRange(fromIso: String, toIso: String): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE startIso >= :fromIso AND startIso < :toIso ORDER BY startIso ASC")
    suspend fun range(fromIso: String, toIso: String): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE taskId = :taskId ORDER BY startIso DESC LIMIT 1")
    suspend fun lastBlockForTask(taskId: Long): ScheduleBlockEntity?

    @Query("SELECT * FROM schedule_blocks WHERE endIso <= :nowIso AND type = 'TASK' ORDER BY startIso ASC")
    suspend fun finishedTaskBlocks(nowIso: String): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE startIso > :nowIso AND type = 'TASK' ORDER BY startIso ASC LIMIT 5")
    suspend fun upcomingTaskBlocks(nowIso: String): List<ScheduleBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduleBlockEntity>): List<Long>

    @Update
    suspend fun update(block: ScheduleBlockEntity)

    @Delete
    suspend fun delete(block: ScheduleBlockEntity)

    /** Удаляет незаблокированные блоки дня — «перестроить день» без потери закреплённых встреч. */
    @Query("DELETE FROM schedule_blocks WHERE locked = 0 AND startIso >= :fromIso AND startIso < :toIso")
    suspend fun clearUnlockedRange(fromIso: String, toIso: String): Int

    @Transaction
    suspend fun replaceUnlockedRange(
        fromIso: String,
        toIso: String,
        blocks: List<ScheduleBlockEntity>,
    ): List<Long> {
        clearUnlockedRange(fromIso, toIso)
        return insertAll(blocks)
    }
}

@Dao
interface StatsDao {
    @Insert
    suspend fun insert(event: CompletionEventEntity)

    @Query("SELECT * FROM completion_events ORDER BY completedAtIso DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<CompletionEventEntity>

    @Query("SELECT AVG(actualMinutes * 1.0 / NULLIF(estimatedMinutes, 0)) FROM completion_events WHERE category = :category")
    suspend fun avgRatioForCategory(category: String): Double?

    @Query("SELECT hourOfDay, AVG(actualMinutes) AS avgActual FROM completion_events GROUP BY hourOfDay ORDER BY avgActual DESC")
    suspend fun bestHours(): List<HourAvg>

    @Query("SELECT COUNT(*) FROM completion_events")
    suspend fun totalEvents(): Int
}

data class HourAvg(val hourOfDay: Int, val avgActual: Double)
