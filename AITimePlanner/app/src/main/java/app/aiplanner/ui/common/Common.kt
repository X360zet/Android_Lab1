package app.aiplanner.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aiplanner.domain.model.Priority
import app.aiplanner.domain.model.TaskCategory
import app.aiplanner.ui.theme.CategoryPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CategoryDot(category: TaskCategory, modifier: Modifier = Modifier) {
    val color = when (category) {
        TaskCategory.WORK -> CategoryPalette.work
        TaskCategory.STUDY -> CategoryPalette.study
        TaskCategory.HEALTH -> CategoryPalette.health
        TaskCategory.FAMILY -> CategoryPalette.family
        TaskCategory.HOME -> CategoryPalette.home
        TaskCategory.REST -> CategoryPalette.rest
        TaskCategory.PERSONAL -> CategoryPalette.personal
    }
    androidx.compose.foundation.Canvas(modifier) {
        drawCircle(color)
    }
}

fun Priority.label(): String = when (this) {
    Priority.CRITICAL -> "Критичный"
    Priority.HIGH -> "Высокий"
    Priority.MEDIUM -> "Средний"
    Priority.LOW -> "Низкий"
}

fun TaskCategory.label(): String = when (this) {
    TaskCategory.WORK -> "Работа"
    TaskCategory.STUDY -> "Учёба"
    TaskCategory.HEALTH -> "Здоровье"
    TaskCategory.FAMILY -> "Семья"
    TaskCategory.HOME -> "Быт"
    TaskCategory.REST -> "Отдых"
    TaskCategory.PERSONAL -> "Личное"
}

fun minutesLabel(min: Int): String =
    if (min >= 60) "%.1f ч".format(min / 60.0) else "$min мин"

private val dayFmt = DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault())

fun LocalDate.humanDay(): String {
    val dow = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "${format(dayFmt)} ($dow)"
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}
