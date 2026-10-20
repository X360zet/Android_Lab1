package app.aiplanner.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Доменные модели приложения. Чистый Kotlin без зависимостей от Android —
 * это ядро, которое переиспользуется планировщиком, use-case'ами и UI.
 */

enum class Priority(val weight: Int) {
    LOW(1), MEDIUM(2), HIGH(4), CRITICAL(8);

    companion object {
        fun fromOrdinalSafe(name: String?): Priority =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class TaskCategory {
    WORK, STUDY, HEALTH, FAMILY, HOME, REST, PERSONAL;

    val isProductive: Boolean get() = this != REST

    companion object {
        fun fromNameSafe(name: String?): TaskCategory =
            entries.firstOrNull { it.name == name } ?: PERSONAL
    }
}

enum class TaskStatus {
    PLANNED, IN_PROGRESS, DONE, SKIPPED, OVERDUE;

    val isFinished: Boolean get() = this == DONE || this == SKIPPED
}

data class Goal(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val deadline: LocalDate? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

data class Task(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int = 30,
    val actualMinutes: Int = 0,
    val priority: Priority = Priority.MEDIUM,
    val category: TaskCategory = TaskCategory.PERSONAL,
    val status: TaskStatus = TaskStatus.PLANNED,
    val goalId: Long? = null,
    val deadline: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
    /** Коэффициент сложности, который ИИ дообучает по факту выполнения. */
    val difficultyFactor: Double = 1.0,
) {
    val isPlannedOrActive: Boolean
        get() = status == TaskStatus.PLANNED || status == TaskStatus.IN_PROGRESS

    fun duration(): Duration = Duration.ofMinutes(estimatedMinutes.toLong())
}

/** Тип блока времени: рабочая задача, перерыв, рутина (завтрак/спорт) или «буфер». */
enum class BlockType { TASK, BREAK, ROUTINE, BUFFER }

data class ScheduleBlock(
    val id: Long = 0L,
    val taskId: Long? = null,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val type: BlockType = BlockType.TASK,
    val locked: Boolean = false,
    val category: TaskCategory = TaskCategory.PERSONAL,
) {
    val durationMinutes: Int
        get() = Duration.between(start, end).toMinutes().toInt()

    operator fun contains(instant: LocalDateTime): Boolean =
        !instant.isBefore(start) && instant.isBefore(end)
}

/** Профиль биоритмов — то, на чём планировщик строит «окна энергии». */
data class UserProfile(
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val sleepHour: Int = 23,
    val sleepMinute: Int = 0,
    val peakWindowStart: Int = 9,
    val peakWindowEnd: Int = 12,
    val focusBlockMinutes: Int = 50,
    val breakMinutes: Int = 10,
    val dailyCapacityHours: Double = 6.0,
    val notificationsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
) {
    fun wakeTime(): LocalTime = LocalTime.of(wakeHour.coerceIn(0, 23), wakeMinute.coerceIn(0, 59))
    fun sleepTime(): LocalTime = LocalTime.of(sleepHour.coerceIn(0, 23), sleepMinute.coerceIn(0, 59))

    fun dayStart(date: LocalDate): LocalDateTime = LocalDateTime.of(date, wakeTime())
    fun dayEnd(date: LocalDate): LocalDateTime {
        val sleep = sleepTime()
        return if (sleep.isBefore(wakeTime())) {
            // Отбой после полуночи — считаем конец дня полупночью следующего дня
            LocalDateTime.of(date.plusDays(1), sleep)
        } else {
            LocalDateTime.of(date, sleep)
        }
    }

    /** Ёмкость дня в минутах с учётом биоритма. */
    fun capacityMinutes(): Int = (dailyCapacityHours * 60).toInt()
}

/** Оценка «энергетической цены» часа суток для данного профиля: 0.0..1.0 */
data class EnergyCurve(val values: Map<Int, Double>) {
    fun at(hour: Int): Double = values[hour.mod(24)] ?: 0.5
}
