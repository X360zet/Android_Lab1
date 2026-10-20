package app.aiplanner.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aiplanner.data.LocalRepository
import app.aiplanner.data.SettingsDataStore
import app.aiplanner.domain.model.AiPlan
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.planner.HeuristicSchedulePlanner
import app.aiplanner.domain.usecase.PlanningUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class Horizon(val days: Int) { ONE(1), THREE(3), WEEK(7) }

data class PlannerUiState(
    val goalText: String = "",
    val decomposedTasks: List<Task> = emptyList(),
    val decompositionSummary: String? = null,
    val savedDecomposition: Boolean = false,
    val horizon: Horizon = Horizon.ONE,
    val selectedTaskIds: Set<Long> = emptySet(),
    val plan: AiPlan? = null,
    val generating: Boolean = false,
    val appliedMessage: String? = null,
    val question: String = "",
    val answer: String? = null,
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repo: LocalRepository,
    private val settings: SettingsDataStore,
    private val planning: PlanningUseCase,
    private val planner: HeuristicSchedulePlanner,
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state

    val openTasks: StateFlow<List<Task>> = repo.observeOpenTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setGoalText(t: String) { _state.value = _state.value.copy(goalText = t) }

    fun setQuestion(q: String) { _state.value = _state.value.copy(question = q) }
    fun setHorizon(h: Horizon) { _state.value = _state.value.copy(horizon = h) }
    fun toggleTask(id: Long) {
        val cur = _state.value.selectedTaskIds.toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        _state.value = _state.value.copy(selectedTaskIds = cur)
    }

    fun decompose() = viewModelScope.launch {
        val (tasks, summary) = planning.decomposeGoal(_state.value.goalText)
        _state.value = _state.value.copy(decomposedTasks = tasks, decompositionSummary = summary, savedDecomposition = false)
    }

    fun saveDecomposition() = viewModelScope.launch {
        val s = _state.value
        if (s.decomposedTasks.isEmpty()) return@launch
        planning.saveGoalWithTasks(s.goalText.ifBlank { "Цель" }, s.decomposedTasks)
        _state.value = s.copy(savedDecomposition = true, decomposedTasks = emptyList(), decompositionSummary = null, goalText = "")
    }

    fun generatePlan() = viewModelScope.launch {
        _state.value = _state.value.copy(generating = true, appliedMessage = null)
        val start = LocalDate.now()
        val ids = _state.value.selectedTaskIds.toList()
        val plan = planning.generatePlan(start, _state.value.horizon.days, ids)
        _state.value = _state.value.copy(plan = plan, generating = false)
    }

    fun applyPlan() = viewModelScope.launch {
        val plan = _state.value.plan ?: return@launch
        planning.applyPlan(LocalDate.now(), _state.value.horizon.days, plan)
        _state.value = _state.value.copy(
            appliedMessage = "План применён: ${plan.blocks.count { it.taskId != null }} рабочих блоков",
            plan = null,
            selectedTaskIds = emptySet(),
        )
    }

    fun askAssistant() = viewModelScope.launch {
        val q = _state.value.question
        if (q.isBlank()) return@launch
        val profile = settings.profile.first()
        _state.value = _state.value.copy(answer = null)
        val tasks = openTasks.value
        val today = LocalDate.now()
        val blocks = repo.blocksInRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
        val dayPlan = planner.replanRemainingDay(
            from = java.time.LocalDateTime.now(),
            remainingTasks = emptyList(),
            keptBlocks = blocks,
            profile = profile,
        )
        val text = planner.answerQuestion(q, dayPlan, tasks.filter { !it.status.isFinished }, profile)
        _state.value = _state.value.copy(answer = text)
    }

    fun dismissMessage() { _state.value = _state.value.copy(appliedMessage = null) }
}
