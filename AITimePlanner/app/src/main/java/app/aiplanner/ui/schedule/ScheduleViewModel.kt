package app.aiplanner.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aiplanner.data.LocalRepository
import app.aiplanner.data.SettingsDataStore
import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.domain.model.UserProfile
import app.aiplanner.domain.usecase.PlanningUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class ScheduleUiState(
    val date: LocalDate = LocalDate.now(),
    val blocks: List<ScheduleBlock> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val now: LocalDateTime = LocalDateTime.now(),
) {
    val productiveMinutes: Int = blocks.filter { it.type == BlockType.TASK }.sumOf { it.durationMinutes }
    val capacityMinutes: Int = profile.capacityMinutes()
    val freeMinutes: Int = (capacityMinutes - productiveMinutes).coerceAtLeast(0)
    val isEmpty: Boolean = blocks.none { it.type == BlockType.TASK }
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repo: LocalRepository,
    settings: SettingsDataStore,
    private val planning: PlanningUseCase,
) : ViewModel() {

    private val dateFlow = MutableStateFlow(LocalDate.now())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScheduleUiState> = combine(
        dateFlow,
        dateFlow.flatMapLatest { d -> repo.observeDay(d) },
        settings.profile,
    ) { date, blocks, profile ->
        ScheduleUiState(date = date, blocks = blocks, profile = profile, now = LocalDateTime.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun shiftDay(deltaDays: Long) { dateFlow.value = dateFlow.value.plusDays(deltaDays) }

    fun selectToday() { dateFlow.value = LocalDate.now() }

    fun start(block: ScheduleBlock) = viewModelScope.launch { planning.startBlock(block) }

    fun finish(block: ScheduleBlock) = viewModelScope.launch {
        val actual = Duration.between(block.start, LocalDateTime.now()).toMinutes().toInt()
            .coerceAtLeast(5)
        planning.finishBlock(block, actual)
    }

    fun skip(block: ScheduleBlock) = viewModelScope.launch { planning.skipBlock(block) }

    private val _replanMessage = MutableStateFlow<String?>(null)
    val replanMessage: StateFlow<String?> = _replanMessage

    /** Динамическое изменение графика — ИИ пересобирает остаток дня. */
    fun replanToday() = viewModelScope.launch {
        val count = planning.replanRemainingDay(LocalDateTime.now())
        _replanMessage.value = "ИИ перестроил день: блоков в графике — $count"
    }

    fun consumeReplanMessage() { _replanMessage.value = null }

    fun toggleLock(block: ScheduleBlock) = viewModelScope.launch {
        repo.updateBlock(block.copy(locked = !block.locked))
    }
}
