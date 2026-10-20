package app.aiplanner.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aiplanner.data.LocalRepository
import app.aiplanner.data.SettingsDataStore
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val totalTasks: Int = 0,
    val doneTasks: Int = 0,
    val eventsCount: Int = 0,
    val bestHours: List<Int> = emptyList(),
    val categoryRatios: Map<String, Double> = emptyMap(),
    val advice: List<String> = emptyList(),
) {
    val completionRate: Float = if (totalTasks == 0) 0f else doneTasks.toFloat() / totalTasks
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val repo: LocalRepository,
) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val statsFlow: kotlinx.coroutines.flow.Flow<StatsSnapshot> =
        refresh.flatMapLatest { _ -> flow { emit(statsSnapshot()) } }

    val state: StateFlow<ProfileUiState> = combine(
        settings.profile,
        repo.observeTasks(),
        statsFlow,
    ) { profile: UserProfile, tasks: List<app.aiplanner.domain.model.Task>, stats: StatsSnapshot ->
        val eventsCount = stats.events
        val best: List<Int> = stats.best
        val ratios: Map<String, Double> = stats.ratios
        val done = tasks.count { it.status.isFinished }
        val rate = if (tasks.isEmpty()) 0.0 else done.toDouble() / tasks.size
        val advice = buildList {
            if (rate in 0.01..0.6) add("Вы закрываете лишь ${(rate * 100).toInt()}% плана — снизьте дневной ресурс на 1 час или поднимите долю коротких задач.")
            if (rate > 0.85 && tasks.isNotEmpty()) add("Выполняется более 85% плана — можно ставить более амбициозные цели.")
            if (profile.breakMinutes < 10) add("Увеличите перерывы до 10–15 минут: короткие паузы не дают восстановиться attention-ресурсу.")
            if (best.isNotEmpty()) {
                val hoursText = best.joinToString(separator = ", ") { hour: Int -> "%02d:00".format(hour) }
                add("Пик фактической продуктивности: $hoursText — ставьте туда сложные задачи.")
            }
            val underestimated: Map<String, Double> = ratios.filterValues { value: Double -> value > 1.2 }
            if (underestimated.isNotEmpty()) {
                val avg = underestimated.values.average()
                val keys = underestimated.keys.joinToString(separator = ", ")
                add("Вы систематически недооцениваете время в категориях: $keys (в среднем ×%.1f).".format(avg))
            }
            if (profile.dailyCapacityHours > 8) add("Ресурс ${profile.dailyCapacityHours.toInt()} ч/день выше реалистичного среднего — ИИ часто не сможет уместить план.")
            if (isEmpty()) add("Продолжайте отмечать выполнение задач — ИИ обучит оценки времени точнее.")
        }
        ProfileUiState(
            profile = profile,
            totalTasks = tasks.size,
            doneTasks = done,
            eventsCount = eventsCount,
            bestHours = best,
            categoryRatios = ratios,
            advice = advice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    data class StatsSnapshot(val events: Int, val best: List<Int>, val ratios: Map<String, Double>)

    private suspend fun statsSnapshot(): StatsSnapshot {
        val events = repo.recentEvents(200).size
        val best = repo.bestHours().take(3).map { it.hourOfDay }
        val ratios = TaskCategory.entries.mapNotNull { c ->
            repo.avgRatioForCategory(c)?.let { c.labelRu() to it }
        }.toMap()
        return StatsSnapshot(events, best, ratios)
    }

    fun save(profile: UserProfile) = viewModelScope.launch {
        settings.saveProfile(profile.copy(onboardingCompleted = true))
        refresh.value += 1
    }

    private fun TaskCategory.labelRu(): String = when (this) {
        TaskCategory.WORK -> "Работа"
        TaskCategory.STUDY -> "Учёба"
        TaskCategory.HEALTH -> "Здоровье"
        TaskCategory.FAMILY -> "Семья"
        TaskCategory.HOME -> "Быт"
        TaskCategory.REST -> "Отдых"
        TaskCategory.PERSONAL -> "Личное"
    }
}
