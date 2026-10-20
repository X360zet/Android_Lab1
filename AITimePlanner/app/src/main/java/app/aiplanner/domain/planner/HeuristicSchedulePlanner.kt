package app.aiplanner.domain.planner

import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.EnergyCurve
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.domain.model.UserProfile
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Локальный ИИ-движок планирования. Работает полностью на устройстве, без сети:
 *
 * 1) [buildEnergyCurve] — строит кривую энергии дня из биоритмов пользователя;
 * 2) [scoreTask]        — многокритериальная оценка полезности задачи
 *                         (приоритет × дедлайн-аварийность × «залежалость»);
 * 3) [planDay]          — жадный алгоритм с поиском свободных слотов: размещает задачи
 *                         в окнах высокой энергии, режет длинные задачи на фокус-блоки,
 *                         вставляет перерывы и не даёт превысить дневной ресурс;
 * 4) [replanRemainingDay] — перестроение остатка дня после сдвига/пропуска блока;
 * 5) [learnedEstimate]  — самообучение: корректирует оценку времени по истории
 *                         фактических затрат (коэффициент недооценки).
 */
class HeuristicSchedulePlanner {

    companion object {
        const val MIN_BLOCK_MINUTES = 15
        const val SNACK_BREAK_MINUTES = 5
    }

    // ------------------------------------------------------------------
    // Энергия дня
    // ------------------------------------------------------------------

    fun buildEnergyCurve(profile: UserProfile): EnergyCurve {
        val values = HashMap<Int, Double>()
        for (h in 0..23) {
            val v = when {
                h < profile.wakeHour -> 0.05
                h >= profile.sleepHour && profile.sleepHour > profile.wakeHour -> 0.1
                h in profile.peakWindowStart until profile.peakWindowEnd -> 1.0
                h in profile.peakWindowEnd..14 -> 0.65   // послеобеденный спад
                h in 15..18 -> 0.8                        // второй пик
                else -> 0.4
            }
            values[h] = v
        }
        return EnergyCurve(values)
    }

    private fun energyAt(curve: EnergyCurve, dt: LocalDateTime): Double = curve.at(dt.hour)

    // ------------------------------------------------------------------
    // Оценка задач
    // ------------------------------------------------------------------

    /** Приоритетная функция для сортировки: чем больше значение, тем раньше задача попадает в график. */
    fun scoreTask(task: Task, now: LocalDateTime): Double {
        val priorityScore = task.priority.weight.toDouble()
        val deadlineScore = task.deadline?.let { dl ->
            val hoursLeft = Duration.between(now, dl).toMinutes() / 60.0
            when {
                hoursLeft <= 0.0 -> 12.0 // уже просрочено — максимальная авральность
                hoursLeft <= 24.0 -> 8.0
                hoursLeft <= 72.0 -> 5.0
                hoursLeft <= 168.0 -> 3.0
                else -> 1.0
            }
        } ?: 2.0
        val ageDays = Duration.between(task.createdAt, now).toHours() / 24.0
        val stalenessBonus = min(ageDays / 7.0, 2.0)
        return priorityScore + deadlineScore + stalenessBonus
    }

    /** Самообучающаяся оценка: если пользователь систематически недокладывает время, ИИ увеличивает оценку. */
    fun learnedEstimate(task: Task, categoryRatio: Double?, globalRatio: Double?): Int {
        val ratio = categoryRatio ?: globalRatio ?: task.difficultyFactor
        val safeRatio = ratio.coerceIn(0.6, 2.5)
        return max(MIN_BLOCK_MINUTES, (task.estimatedMinutes * safeRatio).roundToInt())
    }

    // ------------------------------------------------------------------
    // Построение плана
    // ------------------------------------------------------------------

    data class PlanOptions(
        val respectExistingBlocks: Boolean = true,
        val insertBreaks: Boolean = true,
        val allowSplitting: Boolean = true,
        val includeRoutine: Boolean = true,
    )

    data class DayPlan(
        val blocks: List<ScheduleBlock>,
        val unscheduled: List<Task>,
        val plannedProductiveMinutes: Int,
        val availableMinutes: Int,
    )

    /**
     * @param tasks   пул незапланированных задач
     * @param existing уже занятые интервалы дня (закреплённые встречи, рутина)
     */
    fun planDay(
        date: LocalDate,
        tasks: List<Task>,
        existing: List<ScheduleBlock>,
        profile: UserProfile,
        options: PlanOptions = PlanOptions(),
        now: LocalDateTime = LocalDateTime.of(date, profile.wakeTime()),
        estimates: Map<Long, Int> = emptyMap(),
    ): DayPlan {
        val curve = buildEnergyCurve(profile)
        val dayStart = maxOf(LocalDateTime.of(date, profile.wakeTime()), now)
        val dayEnd = profile.dayEnd(date)
        if (!dayEnd.isAfter(dayStart)) {
            return DayPlan(emptyList(), tasks, 0, profile.capacityMinutes())
        }

        var remainingCapacity = max(
            0,
            profile.capacityMinutes() - existing.filter { it.type == BlockType.TASK }.sumOf { it.durationMinutes },
        )
        val busy = BusyIntervals()
        if (options.respectExistingBlocks) existing.forEach { busy.add(it.start, it.end) }

        val scored = tasks
            .map { it to scoreTask(it, dayStart) }
            .sortedByDescending { it.second }
            .map { it.first }

        val blocks = mutableListOf<ScheduleBlock>()
        val unscheduled = mutableListOf<Task>()
        var cursor = roundToNextSlot(dayStart)

        for (task in scored) {
            val totalNeeded = estimates[task.id] ?: task.estimatedMinutes
            if (remainingCapacity < MIN_BLOCK_MINUTES) {
                unscheduled += task
                continue
            }
            val placed = placeTask(
                task = task,
                totalNeeded = totalNeeded,
                cursor = cursor,
                dayEnd = dayEnd,
                busy = busy,
                curve = curve,
                profile = profile,
                options = options,
                remainingCapacity = remainingCapacity,
                out = blocks,
            )
            if (placed == null || placed == 0) {
                unscheduled += task
            } else {
                remainingCapacity -= placed
                cursor = blocks.maxOf { it.end }
            }
        }

        val routine = if (options.includeRoutine) {
            routineBlocks(date, profile, busy, dayStart, dayEnd)
        } else emptyList()

        return DayPlan(
            blocks = (blocks + routine).sortedBy { it.start },
            unscheduled = unscheduled,
            plannedProductiveMinutes = blocks.filter { it.type == BlockType.TASK }.sumOf { it.durationMinutes },
            availableMinutes = profile.capacityMinutes(),
        )
    }

    /** Размещает одну задачу, при необходимости деля её на фокус-блоки. Возвращает число размещённых минут или null. */
    private fun placeTask(
        task: Task,
        totalNeeded: Int,
        cursor: LocalDateTime,
        dayEnd: LocalDateTime,
        busy: BusyIntervals,
        curve: EnergyCurve,
        profile: UserProfile,
        options: PlanOptions,
        remainingCapacity: Int,
        out: MutableList<ScheduleBlock>,
    ): Int? {
        val focusLen = profile.focusBlockMinutes.coerceAtLeast(MIN_BLOCK_MINUTES)
        val chunks = if (options.allowSplitting && totalNeeded > focusLen) split(totalNeeded, focusLen) else listOf(totalNeeded)
        var placed = 0
        var localCursor = cursor
        val startCount = out.size

        for (chunk in chunks) {
            if (placed + chunk > remainingCapacity) break
            val slot = findSlot(localCursor, dayEnd, chunk, busy, curve, profile) ?: break
            val end = slot.plusMinutes(chunk.toLong())
            out += ScheduleBlock(
                taskId = task.id,
                title = task.title,
                start = slot,
                end = end,
                type = BlockType.TASK,
                category = task.category,
            )
            busy.add(slot, end)
            placed += chunk
            localCursor = end
            if (options.insertBreaks) {
                val brkMin = if (chunks.size > 1) profile.breakMinutes else SNACK_BREAK_MINUTES
                val afterBusy = busy.firstFree(localCursor, brkMin)
                if (afterBusy.plusMinutes(brkMin.toLong()).isBefore(dayEnd)) {
                    out += ScheduleBlock(
                        title = "Перерыв",
                        start = afterBusy,
                        end = afterBusy.plusMinutes(brkMin.toLong()),
                        type = BlockType.BREAK,
                        category = TaskCategory.REST,
                    )
                    busy.add(afterBusy, afterBusy.plusMinutes(brkMin.toLong()))
                    localCursor = afterBusy.plusMinutes(brkMin.toLong())
                }
            }
        }
        return if (out.size > startCount) placed else null
    }

    /** Ищет ближайший свободный слот заданной длины, предпочитая окна с достаточной энергией. */
    private fun findSlot(
        from: LocalDateTime,
        dayEnd: LocalDateTime,
        minutes: Int,
        busy: BusyIntervals,
        curve: EnergyCurve,
        profile: UserProfile,
    ): LocalDateTime? {
        var candidate = from
        val limit = dayEnd.minusMinutes(minutes.toLong())
        var guard = 0
        while (!candidate.isAfter(limit) && guard++ < 1000) {
            val free = busy.firstFree(candidate, minutes)
            if (free != candidate) {
                candidate = free
                continue
            }
            val e = energyAt(curve, candidate)
            if (e >= 0.3) return candidate
            candidate = candidate.plusMinutes(30)
        }
        return null
    }

    private fun split(total: Int, chunkSize: Int): List<Int> {
        val res = mutableListOf<Int>()
        var left = total
        while (left > chunkSize) {
            res += chunkSize
            left -= chunkSize
        }
        if (left >= MIN_BLOCK_MINUTES || res.isEmpty()) res += left
        else res[res.lastIndex] += left
        return res
    }

    private fun roundToNextSlot(dt: LocalDateTime): LocalDateTime {
        val rem = dt.minute % 5
        return if (rem == 0 && dt.second == 0) dt
        else dt.plusMinutes((5 - rem).toLong()).withSecond(0).withNano(0)
    }

    /** Рутина дня: завтрак и обед защищают время еды от захвата рабочими блоками. */
    private fun routineBlocks(
        date: LocalDate,
        profile: UserProfile,
        busy: BusyIntervals,
        dayStart: LocalDateTime,
        dayEnd: LocalDateTime,
    ): List<ScheduleBlock> {
        val res = mutableListOf<ScheduleBlock>()
        fun addRoutine(title: String, at: LocalDateTime, minutes: Int) {
            if (at.isBefore(dayStart) || at.plusMinutes(minutes.toLong()).isAfter(dayEnd)) return
            val free = busy.firstFree(at, minutes)
            if (free.plusMinutes(minutes.toLong()).isAfter(dayEnd)) return
            res += ScheduleBlock(
                title = title,
                start = free,
                end = free.plusMinutes(minutes.toLong()),
                type = BlockType.ROUTINE,
                category = TaskCategory.HEALTH,
            )
            busy.add(free, free.plusMinutes(minutes.toLong()))
        }
        addRoutine("Завтрак", LocalDateTime.of(date, profile.wakeTime()).plusMinutes(15), 25)
        addRoutine("Обед", LocalDateTime.of(date.year, date.month, date.dayOfMonth, 13, 0), 40)
        return res
    }

    // ------------------------------------------------------------------
    // Перестроение дня (динамическое изменение графика)
    // ------------------------------------------------------------------

    /** После пропуска/сдвига блока пересобираем всё незаблокированное время после [from]. */
    fun replanRemainingDay(
        from: LocalDateTime,
        remainingTasks: List<Task>,
        keptBlocks: List<ScheduleBlock>,
        profile: UserProfile,
        estimates: Map<Long, Int> = emptyMap(),
    ): DayPlan {
        val futureKept = keptBlocks.filter { it.locked || it.end.isAfter(from) }
        val res = planDay(
            date = from.toLocalDate(),
            tasks = remainingTasks,
            existing = futureKept,
            profile = profile,
            options = PlanOptions(includeRoutine = false),
            now = from,
            estimates = estimates,
        )
        // Закреплённые будущие блоки (встречи/тренировки) возвращаем в плане как есть —
        // они не пересоздаются алгоритмом, а лишь защищают интервалы.
        val merged = (res.blocks + futureKept.filter { it.locked && !it.start.isBefore(from) })
            .distinctBy { it.title + it.start }
            .sortedBy { it.start }
        return res.copy(blocks = merged)
    }

    // ------------------------------------------------------------------
    // Инсайты и рекомендации (локальная генерация текста)
    // ------------------------------------------------------------------

    fun insights(
        plan: DayPlan,
        profile: UserProfile,
        bestHours: List<Pair<Int, Double>>,
        completionRate: Double,
    ): List<String> {
        val out = mutableListOf<String>()
        if (plan.plannedProductiveMinutes > plan.availableMinutes) {
            out += "План перегружен на ${plan.plannedProductiveMinutes - plan.availableMinutes} мин — часть задач уйдёт в резерв."
        }
        if (plan.unscheduled.isNotEmpty()) {
            out += "${plan.unscheduled.size} задач не поместились сегодня. Разбейте их или перенесите приоритет."
        }
        if (bestHours.isNotEmpty()) {
            val top = bestHours.take(3).joinToString(", ") { "%02d:00".format(it.first) }
            out += "Исторически вы эффективнее всего в $top — сложные задачи лучше ставить туда."
        }
        if (completionRate in 0.01..0.6) {
            out += "Вы закрываете только ${(completionRate * 100).roundToInt()}% запланированного. Попробуйте снизить дневной ресурс на 1 час."
        } else if (completionRate > 0.85) {
            out += "Вы закрываете ${(completionRate * 100).roundToInt()}% плана — можно брать более амбициозные цели."
        }
        if (profile.breakMinutes < 10) {
            out += "Короткие перерывы (${profile.breakMinutes} мин) снижают восстановление. Оптимально 10–15 мин между фокус-блоками."
        }
        return out
    }

    /** Ответ ассистента на простой вопрос о расписании (rule-based NLU). */
    fun answerQuestion(
        question: String,
        todayPlan: DayPlan,
        openTasks: List<Task>,
        profile: UserProfile,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val q = question.lowercase()
        return when {
            q.contains("перегруз") || q.contains("устал") || q.contains("много") ->
                "Сегодня запланировано ${todayPlan.plannedProductiveMinutes} мин при ресурсе ${todayPlan.availableMinutes} мин. " +
                    if (todayPlan.plannedProductiveMinutes > todayPlan.availableMinutes)
                        "Это перегруз — снимаем ${todayPlan.plannedProductiveMinutes - todayPlan.availableMinutes} мин самых низкорейтинговых задач."
                    else "Перегруза нет, график сбалансирован."

            q.contains("когда") || q.contains("свобод") -> {
                val nextFree = nextFreeMoment(todayPlan.blocks, now)
                if (nextFree == null) "Остаток дня полностью занят. Ближайшее окно — завтра в ${profile.wakeTime()}."
                else "Ближайшее свободное окно начинается в ${"%tR".format(nextFree)}."
            }

            q.contains("приорит") || q.contains("важн") -> {
                val top = openTasks.maxWithOrNull(
                    compareBy({ it.priority.weight }, { it.deadline?.toEpochSecond(java.time.ZoneOffset.UTC) ?: Long.MAX_VALUE })
                )
                if (top == null) "Открытых задач нет."
                else "Самое важное сейчас: «${top.title}» (приоритет ${top.priority.name.lowercase()}, ${top.estimatedMinutes} мин)."
            }

            q.contains("перенес") || q.contains("просроч") || q.contains("забыв") -> {
                val overdue = openTasks.count { it.deadline != null && it.deadline.isBefore(now) }
                "Просроченных задач: $overdue. Нажмите «Перестроить день», чтобы ИИ встроил их заново."
            }

            else -> "Я вижу ${openTasks.size} открытых задач и ${todayPlan.blocks.count { it.type == BlockType.TASK }} блоков на сегодня. " +
                    "Спросите про перегрузку, приоритеты или свободные окна."
        }
    }

    private fun nextFreeMoment(blocks: List<ScheduleBlock>, now: LocalDateTime): LocalDateTime? {
        val sorted = blocks.filter { it.end.isAfter(now) }.sortedBy { it.start }
        var t = now
        for (b in sorted) {
            if (b.start.isAfter(t)) return t
            if (b.end.isAfter(t)) t = b.end
        }
        return t
    }

    // ------------------------------------------------------------------
    // Утилита занятости
    // ------------------------------------------------------------------

    class BusyIntervals {
        private val list = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

        fun add(from: LocalDateTime, to: LocalDateTime) {
            list += from to to
            list.sortBy { it.first }
        }

        /** Первая точка >= [from], откуда можно занять [minutes] без пересечений. */
        fun firstFree(from: LocalDateTime, minutes: Int): LocalDateTime {
            var t = from
            var guard = 0
            while (guard++ < 500) {
                val conflict = list.firstOrNull { (s, e) -> t.isBefore(e) && t.plusMinutes(minutes.toLong()).isAfter(s) }
                if (conflict == null) return t
                t = conflict.second
            }
            return t
        }
    }
}
