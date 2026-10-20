package app.aiplanner.domain.usecase

import app.aiplanner.data.LocalRepository
import app.aiplanner.data.SettingsDataStore
import app.aiplanner.domain.model.AiPlan
import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.PlanSource
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskStatus
import app.aiplanner.domain.planner.GoalDecomposer
import app.aiplanner.domain.planner.HeuristicSchedulePlanner
import app.aiplanner.domain.planner.PlanMerger
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Оркестрация ИИ-планирования: генерация плана, применение, перестроение дня,
 * завершение задач с обучением оценок.
 */
class PlanningUseCase @Inject constructor(
    private val repo: LocalRepository,
    private val settings: SettingsDataStore,
    private val planner: HeuristicSchedulePlanner,
    private val decomposer: GoalDecomposer,
    private val merger: PlanMerger,
) {

    /** Оценки времени по истории (самообучение движка). */
    suspend fun estimatesFor(tasks: List<Task>): Map<Long, Int> {
        val globalRatio = runCatching {
            val events = repo.recentEvents(200)
            if (events.isEmpty()) null
            else events.map { it.actualMinutes.toDouble() / it.estimatedMinutes.coerceAtLeast(1) }.average()
        }.getOrNull()
        return tasks.associate { t ->
            val catRatio = repo.avgRatioForCategory(t.category)
            t.id to planner.learnedEstimate(t, catRatio, globalRatio)
        }
    }

    suspend fun generatePlan(
        startDate: LocalDate,
        days: Int,
        taskIds: List<Long>,
    ): AiPlan {
        val profile = settings.profile.first()
        val allOpen = repo.observeOpenTasks().first()
        val chosen = if (taskIds.isEmpty()) allOpen else allOpen.filter { it.id in taskIds }
        val existing = (0 until days).associate { off ->
            val d = startDate.plusDays(off.toLong())
            d to repo.blocksInRange(d.atStartOfDay(), d.plusDays(1).atStartOfDay())
                .filter { it.type == BlockType.TASK || it.locked || it.type == BlockType.ROUTINE }
        }
        val estimates = estimatesFor(chosen)
        val multi = merger.planHorizon(
            startDate = startDate,
            days = days,
            tasks = chosen,
            existingByDay = existing,
            profile = profile,
            now = LocalDateTime.now(),
            estimates = estimates,
        )
        val bestHours = repo.bestHours().map { it.hourOfDay to it.avgActual }
        val completionRate = computeCompletionRate()
        val insights = planner.insights(
            plan = HeuristicSchedulePlanner.DayPlan(
                blocks = multi.blocks,
                unscheduled = multi.unscheduled,
                plannedProductiveMinutes = multi.plannedProductiveMinutes,
                availableMinutes = multi.availableMinutes,
            ),
            profile = profile,
            bestHours = bestHours,
            completionRate = completionRate,
        )
        val warnings = mutableListOf<String>()
        if (multi.plannedProductiveMinutes > multi.availableMinutes) {
            warnings += "План превышает ресурс ${multi.availableMinutes} мин — длинные задачи разбиты, часть уйдёт в резерв."
        }
        if (multi.unscheduled.isNotEmpty()) {
            warnings += "${multi.unscheduled.size} задач не влезли в горизонт $days дн. Они останутся в списке «Активные»."
        }
        return AiPlan(
            blocks = multi.blocks,
            unscheduledTaskIds = multi.unscheduled.map { it.id },
            insights = insights,
            warnings = warnings,
            totalPlannedMinutes = multi.plannedProductiveMinutes,
            availableMinutes = multi.availableMinutes,
            source = PlanSource.LOCAL,
        )
    }

    /** Применяет план: заменяет незаблокированные блоки горизонта и помечает задачи PLANNED. */
    suspend fun applyPlan(startDate: LocalDate, days: Int, plan: AiPlan) {
        val from = startDate.atStartOfDay()
        val to = startDate.plusDays(days.toLong()).atStartOfDay()
        val cleaned = plan.blocks.map { it.copy(id = 0L) }
        repo.replaceUnlockedRange(from, to, cleaned)
        val plannedIds = plan.blocks.mapNotNull { it.taskId }.distinct()
        plannedIds.forEach { repo.setTaskStatus(it, TaskStatus.PLANNED) }
    }

    /** Динамическое изменение графика: пересборка незаблокированной части дня. */
    suspend fun replanRemainingDay(from: LocalDateTime): Int {
        val profile = settings.profile.first()
        val date = from.toLocalDate()
        val dayEnd = profile.dayEnd(date)
        val blocks = repo.blocksInRange(date.atStartOfDay(), dayEnd)
        val keptLocked = blocks.filter { it.locked }
        val openTasks = repo.observeOpenTasks().first()
        val estimates = estimatesFor(openTasks)
        val res = planner.replanRemainingDay(
            from = from,
            remainingTasks = openTasks,
            keptBlocks = keptLocked,
            profile = profile,
            estimates = estimates,
        )
        repo.replaceUnlockedRange(from, dayEnd, res.blocks)
        return res.blocks.count { it.type == BlockType.TASK }
    }

    /** Завершение блока: фиксирует факт, обучает оценки, отмечает задачу DONE. */
    suspend fun finishBlock(block: ScheduleBlock, actualMinutes: Int) {
        val taskId = block.taskId
        if (taskId != null) {
            val task = repo.taskById(taskId)
            if (task != null) {
                repo.recordCompletion(task, actualMinutes, LocalDateTime.now())
                val newFactor = (actualMinutes.toDouble() / task.estimatedMinutes.coerceAtLeast(1))
                    .coerceIn(0.5, 3.0)
                    .let { 0.7 * task.difficultyFactor + 0.3 * it } // EMA-обновление
                repo.saveTask(task.copy(difficultyFactor = newFactor))
                repo.setTaskStatus(taskId, TaskStatus.DONE)
            }
        }
        repo.deleteBlock(block)
    }

    suspend fun skipBlock(block: ScheduleBlock) {
        block.taskId?.let { repo.setTaskStatus(it, TaskStatus.SKIPPED) }
        repo.deleteBlock(block)
    }

    suspend fun startBlock(block: ScheduleBlock) {
        block.taskId?.let { repo.setTaskStatus(it, TaskStatus.IN_PROGRESS) }
    }

    suspend fun decomposeGoal(text: String): Pair<List<Task>, String> {
        val r = decomposer.decompose(text)
        return r.tasks to r.summary
    }

    suspend fun saveGoalWithTasks(goalTitle: String, tasks: List<Task>) {
        val goalId = repo.saveGoal(
            app.aiplanner.domain.model.Goal(title = goalTitle)
        )
        repo.saveTasks(tasks.map { it.copy(goalId = goalId) })
    }

    private suspend fun computeCompletionRate(): Double {
        val all = repo.observeTasks().first()
        if (all.isEmpty()) return 0.0
        return all.count { it.status.isFinished }.toDouble() / all.size
    }
}
