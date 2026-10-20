package app.aiplanner.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aiplanner.domain.model.BlockType
import app.aiplanner.ui.common.minutesLabel
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(vm: PlannerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val openTasks by vm.openTasks.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("ИИ-планирование") }) }) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Опишите цель — оффлайн-ассистент разложит её на задачи, оценит время и встроит всё в ваш график с учётом биоритмов.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Декомпозиция цели ---
            OutlinedTextField(
                value = state.goalText,
                onValueChange = vm::setGoalText,
                label = { Text("Например: подготовить презентацию к пятнице и выучить 30 слов по испанскому") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::decompose, enabled = state.goalText.isNotBlank()) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.height(0.dp))
                    Text("  Разложить на задачи")
                }
                if (state.decomposedTasks.isNotEmpty()) {
                    OutlinedButton(onClick = vm::saveDecomposition, enabled = !state.savedDecomposition) {
                        Text(if (state.savedDecomposition) "Сохранено ✓" else "Добавить в список задач")
                    }
                }
            }
            state.decompositionSummary?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, Modifier.padding(10.dp))
                }
            }
            state.decomposedTasks.forEach { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${minutesLabel(t.estimatedMinutes)} · ${t.priority.name} · ${t.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // --- Выбор задач и горизонта ---
            Text("Горизонт планирования", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Horizon.entries.forEach { h ->
                    FilterChip(
                        selected = state.horizon == h,
                        onClick = { vm.setHorizon(h) },
                        label = { Text(if (h.days == 1) "1 день" else "${h.days} дней") },
                    )
                }
            }

            if (openTasks.isNotEmpty()) {
                Text(
                    if (state.selectedTaskIds.isEmpty()) "Задачи для плана (ничего не выбрано — возьмутся все активные)"
                    else "Выбранные задачи: ${state.selectedTaskIds.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
                openTasks.take(20).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = t.id in state.selectedTaskIds,
                            onCheckedChange = { vm.toggleTask(t.id) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${minutesLabel(t.estimatedMinutes)} · ${t.priority.name}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Button(onClick = vm::generatePlan, enabled = !state.generating, modifier = Modifier.fillMaxWidth()) {
                if (state.generating) CircularProgressIndicator(Modifier.height(18.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                Icon(Icons.Filled.Bolt, null)
                Text(" Построить план (${state.horizon.days} дн.)")
            }

            // --- Предпросмотр плана ---
            state.plan?.let { plan ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Предпросмотр плана", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Рабочих блоков: ${plan.blocks.count { it.type == BlockType.TASK }} · " +
                                "Запланировано: ${minutesLabel(plan.totalPlannedMinutes)} · " +
                                "Ресурс: ${minutesLabel(plan.availableMinutes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        plan.warnings.forEach { w ->
                            Text("⚠ $w", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        plan.insights.forEach { i ->
                            Text("💡 $i", style = MaterialTheme.typography.bodySmall)
                        }
                        plan.blocks.filter { it.type == BlockType.TASK }.take(15).forEach { b ->
                            Text(
                                "${b.start.format(timeFmt)} — ${b.title} (${minutesLabel(b.durationMinutes)})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = vm::applyPlan, modifier = Modifier.fillMaxWidth()) { Text("Применить план") }
                    }
                }
            }

            state.appliedMessage?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(it, Modifier.padding(10.dp))
                }
            }

            // --- Мини-ассистент ---
            Text("Спросить ассистента", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = { vm.setQuestion(it) },
                    label = { Text("Что вас беспокоит в расписании?") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(onClick = vm::askAssistant) { Text("Спросить") }
            }
            state.answer?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(it, Modifier.padding(10.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
