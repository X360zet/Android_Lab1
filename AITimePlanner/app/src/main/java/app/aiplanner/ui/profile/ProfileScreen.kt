package app.aiplanner.ui.profile

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aiplanner.R
import app.aiplanner.domain.model.UserProfile
import app.aiplanner.ui.common.SectionTitle
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var draft by remember(state.profile) { mutableStateOf(state.profile) }
    var savedFlash by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)

        // ---- Биоритмы ----
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeRow(stringResource(R.string.profile_wake), draft.wakeHour, draft.wakeMinute) { h, m ->
                    draft = draft.copy(wakeHour = h, wakeMinute = m)
                }
                TimeRow(stringResource(R.string.profile_sleep), draft.sleepHour, draft.sleepMinute) { h, m ->
                    draft = draft.copy(sleepHour = h, sleepMinute = m)
                }

                Text(stringResource(R.string.profile_peak), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeakOption(draft, 7, 10, R.string.profile_peak_morning) { p, s, e ->
                        draft = p.copy(peakWindowStart = s, peakWindowEnd = e)
                    }
                    PeakOption(draft, 9, 12, R.string.profile_peak_afternoon) { p, s, e ->
                        draft = p.copy(peakWindowStart = s, peakWindowEnd = e)
                    }
                    PeakOption(draft, 16, 20, R.string.profile_peak_evening) { p, s, e ->
                        draft = p.copy(peakWindowStart = s, peakWindowEnd = e)
                    }
                }

                LabeledSlider(
                    stringResource(R.string.profile_focus_block),
                    value = draft.focusBlockMinutes.toFloat(),
                    range = 25f..90f,
                    steps = 12,
                ) { draft = draft.copy(focusBlockMinutes = it.roundToInt()) }

                LabeledSlider(
                    stringResource(R.string.profile_break_duration),
                    value = draft.breakMinutes.toFloat(),
                    range = 5f..30f,
                    steps = 4,
                ) { draft = draft.copy(breakMinutes = it.roundToInt()) }

                LabeledSlider(
                    stringResource(R.string.profile_daily_capacity),
                    value = draft.dailyCapacityHours.toFloat(),
                    range = 2f..10f,
                    steps = 7,
                ) { draft = draft.copy(dailyCapacityHours = (it * 2).roundToInt() / 2.0) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.notificationsEnabled,
                        onCheckedChange = { draft = draft.copy(notificationsEnabled = it) },
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(stringResource(R.string.profile_notifications))
                }

                Button(onClick = {
                    viewModel.save(draft)
                    savedFlash = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (savedFlash) stringResource(R.string.profile_saved) else stringResource(R.string.profile_save))
                }
            }
        }

        // ---- Статистика и рекомендации ИИ ----
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(stringResource(R.string.profile_stats_title))
                StatLine(
                    stringResource(R.string.profile_completion_rate),
                    "${(state.completionRate * 100).roundToInt()}% (${state.doneTasks}/${state.totalTasks})",
                )
                StatLine(
                    stringResource(R.string.profile_best_hours),
                    if (state.bestHours.isEmpty()) "—"
                    else state.bestHours.joinToString { "%02d:00".format(it) },
                )
                if (state.categoryRatios.isNotEmpty()) {
                    StatLine(
                        "Точность оценок по категориям",
                        state.categoryRatios.entries.joinToString { "${it.key} ×%.1f".format(it.value) },
                    )
                }
                StatLine("Событий для обучения ИИ", "${state.eventsCount}")

                Spacer(Modifier.height(4.dp))
                SectionTitle(stringResource(R.string.profile_ai_advice))
                state.advice.forEach { a ->
                    Text("• $a", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TimeRow(label: String, hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    Column {
        Text("$label: %02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = hour.toFloat() + minute / 60f,
            onValueChange = { v ->
                val total = (v * 60).roundToInt()
                onChange((total / 60).coerceIn(0, 23), (total % 60))
            },
            valueRange = 0f..23.99f,
        )
    }
}

@Composable
private fun PeakOption(
    profile: UserProfile,
    start: Int,
    end: Int,
    labelRes: Int,
    selected: Boolean = profile.peakWindowStart == start && profile.peakWindowEnd == end,
    onSelect: (UserProfile, Int, Int) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(profile, start, end) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValue: (Float) -> Unit,
) {
    Column {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps)
    }
}
