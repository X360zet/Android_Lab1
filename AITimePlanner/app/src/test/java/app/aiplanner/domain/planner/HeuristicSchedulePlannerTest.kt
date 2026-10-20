package app.aiplanner.domain.planner

import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Тесты локального ИИ-движка планирования (оффлайн-ядро). */
class HeuristicSchedulePlannerTest {

    private val planner = HeuristicSchedulePlanner()
    private val profile = UserProfile(
        wakeHour = 7, sleepHour = 23,
        peakWindowStart = 9, peakWindowEnd = 12,
        focusBlockMinutes = 50, breakMinutes = 10,
        dailyCapacityHours = 6.0,
    )
    private val date: LocalDate = LocalDate.of(2026, 10, 20)
    private val morning = LocalDateTime.of(date.atTime(8, 0))

    private fun task(id: Long, minutes: Int, priority: Priority = Priority.MEDIUM) = Task(
        id = id,
        title = "Задача $id",
        estimatedMinutes = minutes,
        priority = priority,
        category = TaskCategory.WORK,
        createdAt = morning.minusDays(1),
    )

    @Test
    fun `plan places blocks inside day window without overlaps`() {
        val tasks = listOf(task(1, 45), task(2, 90, Priority.HIGH), task(3, 30))
        val plan = planner.planDay(date, tasks, emptyList(), profile, now = morning)

        assertTrue("должны появиться блоки", plan.blocks.isNotEmpty())
        val taskBlocks = plan.blocks.filter { it.type == BlockType.TASK }
        assertTrue(taskBlocks.isNotEmpty())

        val dayStart = LocalDateTime.of(date, profile.wakeTime())
        val dayEnd = profile.dayEnd(date)
        taskBlocks.forEach { b ->
            assertFalse("блок раньше подъёма", b.start.isBefore(dayStart))
            assertTrue("блок позже отбоя", b.end.isBefore(dayEnd.plusSeconds(1)))
            assertTrue("пустой блок", b.durationMinutes > 0)
        }

        // отсутствие пересечений между задачами
        val sorted = taskBlocks.sortedBy { it.start }
        for (i in 1 until sorted.size) {
            assertFalse(
                "пересечение ${sorted[i - 1].title} / ${sorted[i].title}",
                sorted[i].start.isBefore(sorted[i - 1].end),
            )
        }
    }

    @Test
    fun `long task is split into focus blocks`() {
        val big = task(1, 120)
        val plan = planner.planDay(date, listOf(big), emptyList(), profile, now = morning)
        val parts = plan.blocks.filter { it.taskId == 1L }
        assertTrue("длинная задача должна быть разбита", parts.size >= 2)
        assertEquals(120, parts.sumOf { it.durationMinutes })
    }

    @Test
    fun `capacity limits productive minutes`() {
        val many = (1..20).map { task(it.toLong(), 60, Priority.HIGH) }
        val plan = planner.planDay(date, many, emptyList(), profile, now = morning)
        assertTrue(
            "перегруз запрещён: ${plan.plannedProductiveMinutes} > ${profile.capacityMinutes()}",
            plan.plannedProductiveMinutes <= profile.capacityMinutes(),
        )
        assertTrue("часть задач уйдёт в нераспределённые", plan.unscheduled.isNotEmpty())
    }

    @Test
    fun `existing locked block is respected`() {
        val meeting = app.aiplanner.domain.model.ScheduleBlock(
            id = 0, taskId = null, title = "Встреча",
            start = LocalDateTime.of(date, java.time.LocalTime.of(9, 30)),
            end = LocalDateTime.of(date, java.time.LocalTime.of(10, 30)),
            type = BlockType.ROUTINE, locked = true, category = TaskCategory.WORK,
        )
        val plan = planner.planDay(date, listOf(task(1, 40)), listOf(meeting), profile, now = morning)
        plan.blocks.filter { it.type == BlockType.TASK }.forEach { b ->
            val overlap = b.start.isBefore(meeting.end) && meeting.start.isBefore(b.end)
            assertFalse("задача наехала на закреплённую встречу", overlap)
        }
    }

    @Test
    fun `replan keeps locked blocks and schedules the rest`() {
        val from = LocalDateTime.of(date, java.time.LocalTime.of(12, 0))
        val locked = app.aiplanner.domain.model.ScheduleBlock(
            id = 0, taskId = null, title = "Тренировка",
            start = LocalDateTime.of(date, java.time.LocalTime.of(18, 0)),
            end = LocalDateTime.of(date, java.time.LocalTime.of(19, 0)),
            type = BlockType.ROUTINE, locked = true, category = TaskCategory.HEALTH,
        )
        val res = planner.replanRemainingDay(
            from = from,
            remainingTasks = listOf(task(1, 45)),
            keptBlocks = listOf(locked),
            profile = profile,
        )
        val kept = res.blocks.firstOrNull { it.title == "Тренировка" }
        assertTrue("закреплённый блок должен сохраниться", kept != null)
        assertTrue("после полудня должны появиться задачи", res.blocks.any { it.type == BlockType.TASK })
        res.blocks.filter { it.type == BlockType.TASK }.forEach { b ->
            assertFalse("нельзя ставить в прошлое", b.start.isBefore(from))
        }
    }

    @Test
    fun `learnedEstimate inflates systematically underestimated time`() {
        val t = task(1, 30)
        val boosted = planner.learnedEstimate(t, categoryRatio = 1.5, globalRatio = null)
        assertEquals(45, boosted)
        val clamped = planner.learnedEstimate(t, categoryRatio = 5.0, globalRatio = null)
        assertTrue("коэффициент ограничен сверху", clamped <= 30 * 2.5 + 1)
    }
}
