package app.aiplanner.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.Task
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.ui.common.CategoryDot
import app.aiplanner.ui.common.label
import app.aiplanner.ui.common.minutesLabel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TasksViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Мои задачи") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Filled.Add, "Добавить задачу")
            }
        },
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Поиск по задачам") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TasksFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = {
                            Text(
                                when (f) {
                                    TasksFilter.ALL -> "Все"
                                    TasksFilter.ACTIVE -> "Активные"
                                    TasksFilter.DONE -> "Выполненные"
                                }
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.visible.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Задач пока нет.", style = MaterialTheme.typography.bodyLarge)
                    Text("Добавьте вручную или разложите цель во вкладке «ИИ-планирование».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.visible, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            goalTitle = state.goals.firstOrNull { it.id == task.goalId }?.title,
                            onToggle = { vm.toggleDone(task) },
                            onEdit = { editing = task; showEditor = true },
                            onDelete = { vm.delete(task) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editing,
            goals = state.goals.map { it.id to it.title },
            onDismiss = { showEditor = false },
            onSave = { id, title, desc, minutes, pr, cat, dl, goalId ->
                vm.saveTask(id, title, desc, minutes, pr, cat, dl, goalId)
                showEditor = false
            },
        )
    }
}

@Composable
private fun TaskRow(
    task: Task,
    goalTitle: String?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val done = task.status.isFinished
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val sub = buildString {
                    append(task.priority.label()); append(" · ")
                    append(task.category.label()); append(" · ")
                    append(minutesLabel(task.estimatedMinutes))
                    task.deadline?.let { append(" · до ${it.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))}") }
                    goalTitle?.let { append(" · цель: $it") }
                    if (task.difficultyFactor != 1.0) append(" · ИИ-коэфф. ×%.1f".format(task.difficultyFactor))
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box8dp(task)
            IconButton(onClick = onEdit) { Text("✎") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Удалить") }
        }
    }
}

@Composable
private fun Box8dp(task: Task) {
    androidx.compose.foundation.layout.Box(Modifier.padding(6.dp)) {
        CategoryDot(task.category, Modifier.size(10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorDialog(
    initial: Task?,
    goals: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Int, Priority, TaskCategory, LocalDateTime?, Long?) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var minutes by remember { mutableStateOf((initial?.estimatedMinutes ?: 30).toString()) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.MEDIUM) }
    var category by remember { mutableStateOf(initial?.category ?: TaskCategory.PERSONAL) }
    val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    var deadlineText by remember { mutableStateOf(initial?.deadline?.format(isoFmt) ?: "") }
    var goalId by remember { mutableStateOf(initial?.goalId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val dl = runCatching {
                    // ожидаем формат "yyyy-MM-ddTHH:mm" из простого ввода или игнорируем
                    if (deadlineText.isBlank()) null else LocalDateTime.parse(deadlineText)
                }.getOrNull()
                onSave(
                    initial?.id ?: 0L,
                    title, desc,
                    minutes.toIntOrNull() ?: 30,
                    priority, category, dl, goalId,
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") })
                OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit) }, label = { Text("Оценка, мин") }, singleLine = true)
                LabeledDropdown(
                    label = "Приоритет",
                    options = Priority.entries.map { it.name to it.label() },
                    selected = priority.name,
                ) { priority = Priority.valueOf(it) }
                LabeledDropdown(
                    label = "Категория",
                    options = TaskCategory.entries.map { it.name to it.label() },
                    selected = category.name,
                ) { category = TaskCategory.valueOf(it) }
                if (goals.isNotEmpty()) {
                    LabeledDropdown(
                        label = "Цель",
                        options = listOf(null to "Без цели") + goals.map { it.first.toString() to it.second },
                        selected = goalId?.toString() ?: "null",
                    ) { goalId = it.toLongOrNull() }
                }
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it },
                    label = { Text("Дедлайн (ISO, напр. 2026-10-25T18:00)") },
                    singleLine = true,
                )
            }
        },
        title = { Text(if (initial == null) "Новая задача" else "Редактировать задачу") },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    options: List<Pair<String?, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(key ?: "null")
                    },
                )
            }
        }
    }
}
