package app.aiplanner.domain.model

/**
 * Результаты работы ИИ-модуля. UI оперирует только этими типами,
 * поэтому переход с локального движка на облачную модель прозрачен для экранов.
 */

/** Задача, которую ИИ создал из описания цели пользователя. */
data class TaskSuggestion(
    val title: String,
    val estimatedMinutes: Int,
    val priority: Priority,
    val category: TaskCategory,
    val rationale: String = "",
)

/** Готовый план: блоки расписания + пояснения модели. */
data class AiPlan(
    val blocks: List<ScheduleBlock>,
    /** Ключ taskId -> предложенная длительность в минутах (для задач вне расписания). */
    val unscheduledTaskIds: List<Long> = emptyList(),
    val insights: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val totalPlannedMinutes: Int = 0,
    val availableMinutes: Int = 0,
    val source: PlanSource = PlanSource.LOCAL,
) {
    val isOverloaded: Boolean get() = totalPlannedMinutes > availableMinutes && availableMinutes > 0
}

enum class PlanSource { LOCAL, REMOTE }

/** Ответ ассистента на свободный вопрос пользователя о его времени. */
data class AssistantAnswer(
    val text: String,
    val suggestions: List<String> = emptyList(),
)
