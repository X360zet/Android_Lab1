package app.aiplanner.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aiplanner.data.LocalRepository
import app.aiplanner.domain.model.Goal
import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class TasksFilter { ALL, ACTIVE, DONE }

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val filter: TasksFilter = TasksFilter.ALL,
    val query: String = "",
) {
    val visible: List<Task> = tasks.filter { t ->
        val byFilter = when (filter) {
            TasksFilter.ALL -> true
            TasksFilter.ACTIVE -> !t.status.isFinished
            TasksFilter.DONE -> t.status.isFinished
        }
        val byQuery = query.isBlank() || t.title.contains(query, ignoreCase = true)
        byFilter && byQuery
    }
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: LocalRepository,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TasksFilter.ALL)
    private val queryFlow = MutableStateFlow("")

    val state: StateFlow<TasksUiState> = combine(
        repo.observeTasks(),
        repo.observeGoals(),
        filterFlow,
        queryFlow,
    ) { tasks, goals, filter, query ->
        TasksUiState(tasks = tasks, goals = goals, filter = filter, query = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(f: TasksFilter) { filterFlow.value = f }
    fun setQuery(q: String) { queryFlow.value = q }

    fun saveTask(
        id: Long,
        title: String,
        description: String,
        minutes: Int,
        priority: Priority,
        category: TaskCategory,
        deadline: LocalDateTime?,
        goalId: Long?,
    ) = viewModelScope.launch {
        val existing = if (id != 0L) repo.taskById(id) else null
        val task = (existing ?: Task(title = title)).copy(
            id = id,
            title = title.ifBlank { "Без названия" },
            description = description,
            estimatedMinutes = minutes.coerceIn(5, 600),
            priority = priority,
            category = category,
            deadline = deadline,
            goalId = goalId,
        )
        repo.saveTask(task)
    }

    fun delete(task: Task) = viewModelScope.launch { repo.deleteTask(task) }

    fun toggleDone(task: Task) = viewModelScope.launch {
        if (task.status.isFinished) {
            repo.setTaskStatus(task.id, TaskStatus.PLANNED)
        } else {
            repo.recordCompletion(task, task.estimatedMinutes, LocalDateTime.now())
            repo.setTaskStatus(task.id, TaskStatus.DONE)
        }
    }
}
