package app.aiplanner.domain.planner

import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.EnergyCurve
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.UserProfile
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Многодневная сборка плана: берёт однодневный алгоритм [HeuristicSchedulePlanner]
 * и прогоняет его по дням горизонта, перенося невыполненное на следующий день.
 * Сложные/продуктивные задачи попадают в окна высокой энергии первых дней.
 */
class PlanMerger(private val planner: HeuristicSchedulePlanner) {

    data class MultiDayPlan(
        val blocks: List<ScheduleBlock>,
        val unscheduled: List<Task>,
        val plannedProductiveMinutes: Int,
        val availableMinutes: Int,
    )

    fun planHorizon(
        startDate: LocalDate,
        days: Int,
        tasks: List<Task>,
        existingByDay: Map<LocalDate, List<ScheduleBlock>>,
        profile: UserProfile,
        now: LocalDateTime = LocalDateTime.now(),
        estimates: Map<Long, Int> = emptyMap(),
    ): MultiDayPlan {
        val curve: EnergyCurve = planner.buildEnergyCurve(profile)
        var pool = tasks.toList()
        val allBlocks = mutableListOf<ScheduleBlock>()
        val busy = HeuristicSchedulePlanner.BusyIntervals()
        // защищаем уже существующие блоки всего горизонта
        existingByDay.values.flatten().forEach { busy.add(it.start, it.end) }

        for (d in 0 until days) {
            val date = startDate.plusDays(d.toLong())
            val dayStart = LocalDateTime.of(date, profile.wakeTime())
            val startFrom = if (date == startDate && now.isAfter(dayStart)) now else dayStart
            val res = planner.planDay(
                date = date,
                tasks = pool,
                existing = existingByDay[date].orEmpty(),
                profile = profile,
                options = HeuristicSchedulePlanner.PlanOptions(
                    respectExistingBlocks = true,
                    insertBreaks = true,
                    allowSplitting = true,
                    includeRoutine = d == 0, // рутина только на первый день остальное занято встречами
                ),
                now = startFrom,
                estimates = estimates,
            )
            allBlocks += res.blocks
            pool = res.unscheduled
        }

        return MultiDayPlan(
            blocks = allBlocks.sortedBy { it.start },
            unscheduled = pool,
            plannedProductiveMinutes = allBlocks.filter { it.type == BlockType.TASK }.sumOf { it.durationMinutes },
            availableMinutes = profile.capacityMinutes() * days.coerceAtLeast(1),
        )
    }
}
