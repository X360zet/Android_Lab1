package app.aiplanner.data

import app.aiplanner.data.local.dao.GoalDao
import app.aiplanner.data.local.dao.ScheduleDao
import app.aiplanner.data.local.dao.StatsDao
import app.aiplanner.data.local.dao.TaskDao
import app.aiplanner.data.local.entity.CompletionEventEntity
import app.aiplanner.data.local.entity.GoalEntity
import app.aiplanner.data.local.entity.ScheduleBlockEntity
import app.aiplanner.data.local.entity.TaskEntity
import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.Goal
import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Маппинг слой/сущность <-> домен вынесен сюда, чтобы ViewModel не знала про Room.
 */
fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
    priority = Priority.fromOrdinalSafe(priority),
    category = TaskCategory.fromNameSafe(category),
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PLANNED),
    goalId = goalId,
    deadline = deadlineIso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
    createdAt = runCatching { LocalDateTime.parse(createdAtIso) }.getOrDefault(LocalDateTime.now()),
    completedAt = completedAtIso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
    difficultyFactor = difficultyFactor,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
    priority = priority.name,
    category = category.name,
    status = status.name,
    goalId = goalId,
    deadlineIso = deadline?.toString(),
    createdAtIso = createdAt.toString(),
    completedAtIso = completedAt?.toString(),
    difficultyFactor = difficultyFactor,
)

fun ScheduleBlockEntity.toDomain(): ScheduleBlock = ScheduleBlock(
    id = id,
    taskId = taskId,
    title = title,
    start = LocalDateTime.parse(startIso),
    end = LocalDateTime.parse(endIso),
    type = runCatching { BlockType.valueOf(type) }.getOrDefault(BlockType.TASK),
    locked = locked,
    category = TaskCategory.fromNameSafe(category),
)

fun ScheduleBlock.toEntity(): ScheduleBlockEntity = ScheduleBlockEntity(
    id = id,
    taskId = taskId,
    title = title,
    startIso = start.toString(),
    endIso = end.toString(),
    type = type.name,
    locked = locked,
    category = category.name,
)

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    title = title,
    description = description,
    deadline = deadlineIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    createdAt = runCatching { LocalDateTime.parse(createdAtIso) }.getOrDefault(LocalDateTime.now()),
)

fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    title = title,
    description = description,
    deadlineIso = deadline?.toString(),
    createdAtIso = createdAt.toString(),
)

@Singleton
class LocalRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val scheduleDao: ScheduleDao,
    private val goalDao: GoalDao,
    private val statsDao: StatsDao,
) {
    // --- Задачи -----------------------------------------------------------
    fun observeTasks(): Flow<List<Task>> = taskDao.observeAll().map { it.map(TaskEntity::toDomain) }

    fun observeOpenTasks(): Flow<List<Task>> = taskDao.observeOpen().map { it.map(TaskEntity::toDomain) }

    suspend fun taskById(id: Long): Task? = taskDao.byId(id)?.toDomain()

    suspend fun saveTask(task: Task): Long {
        val entity = task.toEntity()
        return if (task.id == 0L) taskDao.insert(entity) else {
            taskDao.update(entity); task.id
        }
    }

    suspend fun saveTasks(tasks: List<Task>): List<Long> = taskDao.insertAll(tasks.map(Task::toEntity))

    suspend fun deleteTask(task: Task) = taskDao.delete(task.toEntity())

    suspend fun setTaskStatus(id: Long, status: TaskStatus) = taskDao.setStatus(id, status.name)

    // --- График -----------------------------------------------------------
    fun observeDay(date: LocalDate): Flow<List<ScheduleBlock>> {
        val from = date.atStartOfDay().toString()
        val to = date.plusDays(1).atStartOfDay().toString()
        return scheduleDao.observeRange(from, to).map { it.map(ScheduleBlockEntity::toDomain) }
    }

    fun observeRange(from: LocalDateTime, to: LocalDateTime): Flow<List<ScheduleBlock>> =
        scheduleDao.observeRange(from.toString(), to.toString()).map { it.map(ScheduleBlockEntity::toDomain) }

    suspend fun blocksInRange(from: LocalDateTime, to: LocalDateTime): List<ScheduleBlock> =
        scheduleDao.range(from.toString(), to.toString()).map(ScheduleBlockEntity::toDomain)

    suspend fun saveBlocks(blocks: List<ScheduleBlock>): List<Long> =
        scheduleDao.insertAll(blocks.map(ScheduleBlock::toEntity))

    suspend fun updateBlock(block: ScheduleBlock) = scheduleDao.update(block.toEntity())

    suspend fun deleteBlock(block: ScheduleBlock) = scheduleDao.delete(block.toEntity())

    suspend fun replaceUnlockedRange(from: LocalDateTime, to: LocalDateTime, blocks: List<ScheduleBlock>) =
        scheduleDao.replaceUnlockedRange(from.toString(), to.toString(), blocks.map(ScheduleBlock::toEntity))

    suspend fun upcomingBlocks(now: LocalDateTime) =
        scheduleDao.upcomingTaskBlocks(now.toString()).map(ScheduleBlockEntity::toDomain)

    // --- Цели -------------------------------------------------------------
    fun observeGoals(): Flow<List<Goal>> = goalDao.observeAll().map { it.map(GoalEntity::toDomain) }

    suspend fun saveGoal(goal: Goal): Long {
        val entity = goal.toEntity()
        return if (goal.id == 0L) goalDao.upsert(entity) else {
            goalDao.upsert(entity); goal.id
        }
    }

    // --- События для обучения ИИ -----------------------------------------
    suspend fun recordCompletion(task: Task, actualMinutes: Int, endedAt: LocalDateTime) {
        statsDao.insert(
            CompletionEventEntity(
                taskTitle = task.title,
                category = task.category.name,
                hourOfDay = endedAt.hour,
                dayOfWeek = endedAt.dayOfWeek.value,
                estimatedMinutes = task.estimatedMinutes,
                actualMinutes = actualMinutes,
                completedAtIso = endedAt.toString(),
            )
        )
    }

    suspend fun recentEvents(limit: Int = 200) = statsDao.recent(limit)

    suspend fun avgRatioForCategory(category: TaskCategory): Double? =
        statsDao.avgRatioForCategory(category.name)

    suspend fun bestHours() = statsDao.bestHours()

    suspend fun totalEvents(): Int = statsDao.totalEvents()

    suspend fun archiveDoneBefore(cutoff: LocalDateTime) = taskDao.archiveDoneBefore(cutoff.toString())
}
