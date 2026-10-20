package app.aiplanner.domain.planner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Тесты оффлайн-декомпозиции целей на задачи. */
class GoalDecomposerTest {

    private val decomposer = GoalDecomposer()

    @Test
    fun `study goal produces several tasks`() {
        val r = decomposer.decompose("подготовить презентацию по истории к пятнице", today = LocalDate.of(2026, 10, 20))
        assertTrue("должно появиться минимум 3 задачи", r.tasks.size >= 3)
        assertTrue("задачи должны иметь оценку времени", r.tasks.all { it.estimatedMinutes in 5..600 })
        assertTrue("summary непустой", r.summary.isNotBlank())
    }

    @Test
    fun `unknown goal still yields fallback tasks`() {
        val r = decomposer.decompose("разобрать завал в гараже sometime", today = LocalDate.of(2026, 10, 20))
        assertTrue(r.tasks.isNotEmpty())
    }
}
