package app.aiplanner.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    indices = [Index("deadline"), Index("title")]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    /** ISO-8601 дата (yyyy-MM-dd) или null. */
    val deadlineIso: String?,
    val createdAtIso: String,
)

@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("priority"), Index("deadline"), Index("goalId")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val priority: String,
    val category: String,
    val status: String,
    val goalId: Long?,
    val deadlineIso: String?,
    val createdAtIso: String,
    val completedAtIso: String?,
    val difficultyFactor: Double,
)

@Entity(
    tableName = "schedule_blocks",
    indices = [Index("start"), Index("taskId")]
)
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long?,
    val title: String,
    val startIso: String,
    val endIso: String,
    val type: String,
    val locked: Boolean,
    val category: String,
)

/** Сырое событие выполнения — используется ИИ для обучения оценок времени. */
@Entity(tableName = "completion_events", indices = [Index("completedAt")])
data class CompletionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskTitle: String,
    val category: String,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val completedAtIso: String,
)
