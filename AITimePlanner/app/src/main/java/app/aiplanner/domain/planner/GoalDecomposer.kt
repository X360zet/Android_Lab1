package app.aiplanner.domain.planner

import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Декомпозиция цели на задачи. Локальный «LLM-подобный» движок:
 *  - токенизирует формулировку цели,
 *  - ищет типовые паттерны (глаголы действия, числа, даты, категории),
 *  - строит цепочку подзадач с оценками времени по эмпирическим моделям
 *    (аналог function-of-size оценки: O(n), O(n log n) для «выучить N слов» и т.п.).
 *
 * В облачном режиме (см. RemoteAssistantSource) эта логика служит фолбэком.
 */
class GoalDecomposer {

    data class DecompositionResult(val tasks: List<Task>, val summary: String)

    fun decompose(goalText: String, today: LocalDate = LocalDate.now()): DecompositionResult {
        val text = goalText.trim().ifBlank { "Новая цель" }
        val lower = text.lowercase()
        val numbers = Regex("""\b(\d{1,4})\b""").findAll(lower).map { it.value.toInt() }.toList()
        val deadline = detectDeadline(lower, today)
        val tasks = mutableListOf<Task>()

        // --- Паттерн «выучить / выучивать N слов / тем» -------------------
        if (containsAny(lower, "выуч", "учит", "учеб", "слов", "язык", "курс", "подготов")) {
            val items = numbers.firstOrNull { it in 5..2000 } ?: 30
            val sessions = max(2, ceilDiv(items, 10))
            val perSession = min(60, max(15, ceilDiv(items * 2, sessions)))
            repeat(sessions.coerceAtMost(7)) { i ->
                tasks += task(
                    title = "Занятие ${i + 1}: $topicOf(text) — блок из $items элементов",
                    minutes = perSession,
                    priority = if (i == 0) Priority.HIGH else Priority.MEDIUM,
                    category = TaskCategory.STUDY,
                    deadline = spreadDeadline(deadline, today, i, sessions),
                    rationale = "Интервальные повторения эффективнее одного марафона.",
                )
            }
            tasks += task(
                title = "Самопроверка по теме «${truncate(topicOf(text), 40)}»",
                minutes = 20,
                priority = Priority.MEDIUM,
                category = TaskCategory.STUDY,
                deadline = deadline?.atTime(18, 0),
                rationale = "Активное припоминание закрепляет материал.",
            )
        }

        // --- Паттерн «презентация / отчёт / документ / письмо» ------------
        if (containsAny(lower, "презентац", "отчёт", "отчет", "доклад", "документ", "письм", "стать", "реферат", "диплом", "слайд")) {
            tasks += task("Собрать материалы и источники", 30, Priority.HIGH, TaskCategory.WORK, shift(deadline, 0), "Чёткий список источников экономит до 40% времени.")
            tasks += task("Написать структуру и тезисы", 45, Priority.HIGH, TaskCategory.WORK, shift(deadline, 1), "Структура до текста снижает переделки.")
            tasks += task("Основная часть / текст", 90, Priority.CRITICAL, TaskCategory.WORK, shift(deadline, 2), "Самый ёмкий этап — ставим в пик энергии.")
            tasks += task("Оформление и визуализация", 40, Priority.MEDIUM, TaskCategory.WORK, shift(deadline, 3), "Дизайн — отдельный контекст, не смешивать с текстом.")
            tasks += task("Репетиция и правки", 25, Priority.MEDIUM, TaskCategory.WORK, shift(deadline, 4), "Прогоном находятся 80% слабых мест.")
        }

        // --- Паттерн «спорт / здоровье / пробежка» -------------------------
        if (containsAny(lower, "спорт", "бег", "тренеров", "зал", "похуд", "здоров", "зарядк", "плаван")) {
            val weeks = numbers.firstOrNull { it in 1..52 } ?: 4
            repeat(min(weeks, 4)) { w ->
                tasks += task(
                    title = "Тренировки: неделя ${w + 1} (3 × 40 мин)",
                    minutes = 40,
                    priority = Priority.MEDIUM,
                    category = TaskCategory.HEALTH,
                    deadline = null,
                    rationale = "Регулярность важнее интенсивности на старте.",
                )
            }
        }

        // --- Паттерн «уборка / ремонт / быт» --------------------------------
        if (containsAny(lower, "уборк", "ремонт", "генеральн", "быт", "главк", "стирк")) {
            tasks += task("Разбор завалов и сортировка", 45, Priority.MEDIUM, TaskCategory.HOME, shift(deadline, 0), "Сначала «большие горы», потом детальная уборка.")
            tasks += task("Основная уборка по зонам", 60, Priority.MEDIUM, TaskCategory.HOME, shift(deadline, 1), "Метод зон уменьшает прокрастинацию.")
        }

        // --- Паттерн «запустить / проект / продукт / код» -------------------
        if (containsAny(lower, "запуст", "проект", "приложен", "продукт", "код", "разработ", "сайт", "бот")) {
            tasks += task("Уточнить требования и скоуп", 40, Priority.CRITICAL, TaskCategory.WORK, shift(deadline, 0), "Фиксация скоупа защищает от расползания задач.")
            tasks += task("Прототип / MVP-каркас", 120, Priority.HIGH, TaskCategory.WORK, shift(deadline, 1), "Ранний каркас позволяет тестировать идею раньше.")
            tasks += task("Основная реализация", 180, Priority.CRITICAL, TaskCategory.WORK, shift(deadline, 2), "Крупный блок режется планировщиком на фокус-сессии.")
            tasks += task("Тесты и полировка", 60, Priority.MEDIUM, TaskCategory.WORK, shift(deadline, 3), "Отдельный проход качества.")
            tasks += task("Релиз и анонс", 30, Priority.HIGH, TaskCategory.WORK, shift(deadline, 4), "Финальная точка цели.")
        }

        // --- Фолбэк: общая декомпозиция -------------------------------------
        if (tasks.isEmpty()) {
            tasks += task("Исследовать задачу: «${truncate(text, 50)}»", 30, Priority.HIGH, guessCategory(lower), shift(deadline, 0), "Понимание условия — 20% успеха.")
            tasks += task("Планирование шагов и ресурсов", 25, Priority.HIGH, guessCategory(lower), shift(deadline, 1), "Явный план снижает оценку сроков.")
            tasks += task("Основной рабочий блок", 90, Priority.CRITICAL, guessCategory(lower), shift(deadline, 2), "Ставим в окно пиковой энергии.")
            tasks += task("Проверка результата и коррекция", 20, Priority.MEDIUM, guessCategory(lower), shift(deadline, 3), "Review закрепляет прогресс.")
        }

        val totalMinutes = tasks.sumOf { it.estimatedMinutes }
        val summary = "Цель разложена на ${tasks.size} задач суммарно на ${(totalMinutes / 60.0).roundToInt()} ч."
        return DecompositionResult(tasks = tasks, summary = summary)
    }

    // ------------------------------------------------------------------
    // Вспомогательные функции
    // ------------------------------------------------------------------

    private fun task(
        title: String,
        minutes: Int,
        priority: Priority,
        category: TaskCategory,
        deadline: LocalDateTime?,
        rationale: String,
    ) = Task(
        title = title,
        description = rationale,
        estimatedMinutes = minutes,
        priority = priority,
        category = category,
        deadline = deadline,
        difficultyFactor = 1.0,
    )

    private fun containsAny(haystack: String, vararg needles: String) = needles.any { haystack.contains(it) }

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

    private fun truncate(s: String, n: Int) = if (s.length <= n) s else s.take(n - 1) + "…"

    private fun topicOf(text: String): String {
        val cleaned = text.replace(Regex("(?i)(подготовить|выучить|сделать|запустить|написать)"), "").trim()
        return truncate(cleaned.ifBlank { text }, 30)
    }

    private fun guessCategory(lower: String): TaskCategory = when {
        containsAny(lower, "дом", "семь", "дет") -> TaskCategory.FAMILY
        containsAny(lower, "курс", "экзамен", "язык") -> TaskCategory.STUDY
        containsAny(lower, "бег", "зал", "врач") -> TaskCategory.HEALTH
        containsAny(lower, "клиент", "работа", "отчёт", "презентац") -> TaskCategory.WORK
        else -> TaskCategory.PERSONAL
    }

    /** Простейший NLU дат: «к пятнице», «завтра», «через 3 дня», «до 25 октября». */
    private fun detectDeadline(lower: String, today: LocalDate): LocalDate? {
        Regex("завтра").find(lower)?.let { return today.plusDays(1) }
        Regex("через\\s+(\\d+)\\s+(?:дн|день|дня|нед)").find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            return if (lower.contains("нед")) today.plusWeeks(n.toLong().coerceAtMost(52)) else today.plusDays(n.toLong().coerceAtMost(365))
        }
        val dayNames = mapOf(
            "понедельник" to 1, "вторник" to 2, "среду" to 3, "среда" to 3, "четверг" to 4,
            "пятниц" to 5, "суббот" to 6, "воскресен" to 7,
        )
        for ((k, dow) in dayNames) {
            if (lower.contains(k)) {
                var d = today
                while (d.dayOfWeek.value != dow) d = d.plusDays(1)
                return if (d == today) d.plusDays(7) else d
            }
        }
        Regex("(\\d{1,2})[./](\\d{1,2})").find(lower)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val mon = m.groupValues[2].toIntOrNull() ?: return@let
            runCatching { return LocalDate.of(today.year, mon, day).let { if (it.isBefore(today)) it.plusYears(1) else it } }
        }
        return null
    }

    private fun shift(deadline: LocalDate?, index: Int): LocalDateTime? =
        deadline?.atTime(18, 0)?.plusDays(index / 2L) // распределяем этапы равномерно к дедлайну

    private fun spreadDeadline(deadline: LocalDate?, today: LocalDate, i: Int, total: Int): LocalDateTime? {
        val base = deadline ?: today.plusDays((i + 1).toLong())
        val step = Duration.between(today.atStartOfDay(), base.atStartOfDay()).toDays().toInt()
        val per = max(1, step / total)
        return base.minusDays(((total - 1 - i) * per).toLong()).atTime(18, 0)
    }
}
