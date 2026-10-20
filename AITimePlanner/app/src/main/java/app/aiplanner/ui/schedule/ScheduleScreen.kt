package app.aiplanner.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Unlock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aiplanner.domain.model.BlockType
import app.aiplanner.domain.model.ScheduleBlock
import app.aiplanner.ui.common.CategoryDot
import app.aiplanner.ui.common.minutesLabel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: ScheduleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val replanMsg by vm.replanMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(replanMsg) {
        replanMsg?.let {
            snackbar.showSnackbar(it)
            vm.consumeReplanMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.date == java.time.LocalDate.now()) "Сегодня" else state.date.toString()) },
                actions = {
                    IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.Undo, "Предыдущий день") }
                    TextButton(onClick = { if (state.date != java.time.LocalDate.now()) vm.selectToday() }) { Text("Сегодня") }
                    IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Filled.ChevronRight, "Следующий день") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { vm.replanToday() },
                    label = { Text("Перестроить день ИИ") },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text("Занято: ${minutesLabel(state.productiveMinutes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Ресурс: ${minutesLabel(state.capacityMinutes)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "План пуст. Откройте «ИИ-планирование», чтобы собрать расписание.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.blocks, key = { it.id }) { block ->
                        BlockRow(
                            block = block,
                            isNow = state.now in block,
                            onStart = { vm.start(block) },
                            onFinish = { vm.finish(block) },
                            onSkip = { vm.skip(block) },
                            onLock = { vm.toggleLock(block) },
                        )
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: ScheduleBlock,
    isNow: Boolean,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onLock: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp)) { CategoryDot(block.category, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${block.start.format(timeFmt)}–${block.end.format(timeFmt)}  ${minutesLabel(block.durationMinutes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    block.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (block.type == BlockType.TASK) FontWeight.SemiBold else FontWeight.Normal,
                )
                when (block.type) {
                    BlockType.BREAK -> Text("Перерыв", style = MaterialTheme.typography.bodySmall)
                    BlockType.ROUTINE -> Text("Рутина дня", style = MaterialTheme.typography.bodySmall)
                    BlockType.BUFFER -> Text("Буфер", style = MaterialTheme.typography.bodySmall)
                    BlockType.TASK -> Unit
                }
            }
            if (block.locked) {
                Icon(Icons.Filled.Lock, "Закреплено", tint = MaterialTheme.colorScheme.tertiary)
            }
            if (block.type == BlockType.TASK) {
                IconButton(onClick = onLock) {
                    Icon(if (block.locked) Icons.Filled.Unlock else Icons.Filled.Lock, "Зафиксировать интервал")
                }
                IconButton(onClick = onStart) { Icon(Icons.Filled.PlayArrow, "Начать") }
                IconButton(onClick = onFinish) { Icon(Icons.Filled.Check, "Завершить") }
                IconButton(onClick = onSkip) { Icon(Icons.Filled.Close, "Пропустить") }
            }
        }
    }
}
