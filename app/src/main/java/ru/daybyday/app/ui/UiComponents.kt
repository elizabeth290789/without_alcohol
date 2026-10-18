package ru.daybyday.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val AccentPurple = Color(0xFFAA95FF)
val AccentTeal = Color(0xFF71DECB)
val AccentBlue = Color(0xFF7FAEFF)

fun dayWord(number: Long): String {
    val n = number % 100
    return when {
        n in 11..14 -> "дней"
        n % 10 == 1L -> "день"
        n % 10 in 2..4 -> "дня"
        else -> "дней"
    }
}
fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionDialog(
    initialDate: LocalDate,
    maxDate: LocalDate,
    minDate: LocalDate? = null,
    onDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    if (minDate != null && minDate > maxDate) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Проверь дату устройства") },
            text = { Text("Сохранённый период заканчивается позже текущей даты. Проверь дату и часовой пояс устройства, затем попробуй снова.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        )
        return
    }
    val safeInitial = initialDate.coerceAtMost(maxDate).let { if (minDate != null) it.coerceAtLeast(minDate) else it }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = safeInitial.toEpochDay() * 86_400_000L,
        yearRange = (minDate?.year ?: minOf(1900, safeInitial.year, maxDate.year))..maxDate.year,
        selectableDates = remember(minDate, maxDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L))
                    return date <= maxDate && (minDate == null || date >= minDate)
                }
                override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year && (minDate == null || year >= minDate.year)
            }
        },
    )
    val selectedDate = pickerState.selectedDateMillis?.let { LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)) }
    val selectedValid = selectedDate != null && selectedDate <= maxDate && (minDate == null || selectedDate >= minDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (selectedValid && selectedDate != null) onDate(selectedDate)
            }, enabled = selectedValid) { Text("Выбрать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = pickerState, title = { Text("Выберите дату", Modifier.padding(start = 24.dp, top = 20.dp), fontSize = 14.sp) })
    }
}
