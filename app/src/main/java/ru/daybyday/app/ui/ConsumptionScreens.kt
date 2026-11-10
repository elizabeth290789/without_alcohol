package ru.daybyday.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Selection only previews the domain result; confirmation commits a ledger entry. */
@Composable
fun ConsumptionScreen(
    state: AppUiState,
    actions: AppActions,
    onBack: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    eventId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val event = state.consumptionEvents.firstOrNull { it.id == eventId }
    val initialDate = event?.date ?: state.today
    var selectedEpoch by rememberSaveable(eventId) { mutableStateOf(initialDate.toEpochDay()) }
    var showDatePicker by rememberSaveable(eventId) { mutableStateOf(false) }
    var customChoice by rememberSaveable(eventId) {
        mutableStateOf(initialDate != state.today && initialDate != state.today.minusDays(1))
    }
    var pendingEpoch by rememberSaveable(eventId) { mutableStateOf<Long?>(null) }
    // Preserve the pre-submit IDs across recreation, including a commit before navigation.
    val initialIds = rememberSaveable(eventId) { state.consumptionEvents.map { it.id }.toLongArray() }
    val selectedDate = LocalDate.ofEpochDay(selectedEpoch)
    val minDate = event?.minDate ?: state.consumptionMinDate ?: state.startDate ?: state.today
    val maxDate = event?.maxDate ?: state.today
    val boundsAvailable = minDate <= maxDate
    val insideBounds = boundsAvailable && selectedDate >= minDate && selectedDate <= maxDate
    val preview = actions.previewConsumption(eventId, selectedDate)
    val existingId = preview.existingEventId?.takeIf { it != eventId }
    val unchanged = event != null && event.date == selectedDate
    val editing = eventId != null
    val currentTodayEvent = state.consumptionEvents.firstOrNull { it.date == state.today }
    val canConfirm = insideBounds && preview.error == null && preview.newStart != null &&
        existingId == null && !unchanged && !state.isSaving

    BackHandler(enabled = state.isSaving) { /* A confirmed write is finishing. */ }
    LaunchedEffect(state.consumptionEvents, state.isSaving, pendingEpoch) {
        val pending = pendingEpoch?.let(LocalDate::ofEpochDay)
        if (pending != null && !state.isSaving) {
            val saved = if (eventId == null) {
                state.consumptionEvents.any { it.id !in initialIds && it.date == pending }
            } else {
                state.consumptionEvents.any { it.id == eventId && it.date == pending }
            }
            if (saved) {
                pendingEpoch = null
                onBack()
            }
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) pendingEpoch = null
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ConsumptionHeading(if (editing) "Изменить отметку" else "Отметить употребление", onBack, !state.isSaving)
        Text(
            if (editing) "Исправь дату отметки. Соседние периоды пересчитаются, остальные отметки и полученные награды сохранятся."
            else "Выбери дату и проверь расчёт. День употребления не входит в дни без алкоголя. История и полученные награды сохранятся.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            editing && event == null -> SoftCard(Modifier.fillMaxWidth()) {
                Text("Отметка не найдена", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Вернись к истории и выбери сохранённую дату.", style = MaterialTheme.typography.bodyMedium)
            }
            !boundsAvailable -> SoftCard(Modifier.fillMaxWidth()) {
                Text(
                    if (currentTodayEvent != null) "На сегодня отметка уже есть" else "Проверь дату устройства",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (currentTodayEvent != null) "Её можно исправить. Следующий период начнётся завтра."
                    else "Сохранённая история находится позже текущей даты. Проверь дату и часовой пояс устройства.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (currentTodayEvent != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { onOpenExisting(currentTodayEvent.id) }, enabled = !state.isSaving) {
                        Text("Изменить сохранённую отметку")
                    }
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val yesterday = state.today.minusDays(1)
                    ConsumptionDateChoice(
                        "Сегодня", formatDate(state.today), selectedDate == state.today && !customChoice,
                        state.today in minDate..maxDate && !state.isSaving,
                    ) { customChoice = false; selectedEpoch = state.today.toEpochDay() }
                    ConsumptionDateChoice(
                        "Вчера", formatDate(yesterday), selectedDate == yesterday && !customChoice,
                        yesterday in minDate..maxDate && !state.isSaving,
                    ) { customChoice = false; selectedEpoch = yesterday.toEpochDay() }
                    ConsumptionDateChoice(
                        "Другая дата", if (customChoice) formatDate(selectedDate) else "Выбрать в календаре",
                        customChoice, !state.isSaving,
                    ) { showDatePicker = true }
                }
                Text(
                    "Допустимые даты: " + formatDate(minDate) + " — " + formatDate(maxDate),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    existingId != null -> SoftCard(Modifier.fillMaxWidth()) {
                        Text("На эту дату уже есть отметка", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Открой её, чтобы исправить дату. Повторная отметка не нужна.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { onOpenExisting(existingId) }, enabled = !state.isSaving) {
                            Text("Открыть сохранённую отметку")
                        }
                    }
                    preview.error != null || !insideBounds -> SoftCard(Modifier.fillMaxWidth()) {
                        Text(
                            preview.error ?: "Выбери дату в допустимом диапазоне.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> ConsumptionPreviewCard(preview, selectedDate, state.today)
                }
                if (unchanged) Text(
                    "Выбери другую дату, чтобы сохранить изменение.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        pendingEpoch = selectedDate.toEpochDay()
                        if (eventId == null) actions.onRecordConsumption(selectedDate)
                        else actions.onEditConsumption(eventId, selectedDate)
                    },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (state.isSaving) "Сохраняем…" else if (editing) "Подтвердить изменение" else "Подтвердить отметку",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        TextButton(onClick = onBack, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        Spacer(Modifier.height(8.dp))
    }
    if (showDatePicker && boundsAvailable) DateSelectionDialog(
        initialDate = selectedDate, minDate = minDate, maxDate = maxDate,
        onDate = { selectedEpoch = it.toEpochDay(); customChoice = true; showDatePicker = false },
        onDismiss = { showDatePicker = false },
    )
}

@Composable
private fun ConsumptionPreviewCard(preview: UiConsumptionPreview, date: LocalDate, today: LocalDate) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text("Проверь перед подтверждением", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(18.dp))
        Text("День употребления", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDate(date), style = MaterialTheme.typography.titleLarge)
        Text("Не засчитывается в дни без алкоголя", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Text("Период до этой отметки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(consumptionFullDays(preview.previousDays), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(18.dp))
        Text("Период после этой отметки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        preview.newStart?.let { start ->
            Text(
                when (start) {
                    today -> "Начало сегодня"
                    today.plusDays(1) -> "Начало завтра"
                    else -> "Начало · " + formatDate(start)
                },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary,
            )
            if (start == today || start == today.plusDays(1)) Text(
                formatDate(start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Полных дней: " + preview.fullDays, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConsumptionHistoryScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val events = remember(state.consumptionEvents) { state.consumptionEvents.sortedByDescending { it.date } }
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ConsumptionHeading("История отметок", onBack, !state.isSaving)
            Spacer(Modifier.height(16.dp))
            Text(
                "Здесь можно исправить дату любой сохранённой отметки. Соседние периоды пересчитаются, остальные отметки и полученные награды сохранятся.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (events.isEmpty()) item {
            SoftCard(Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.EventNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(14.dp))
                Text("Отметок пока нет", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("После сохранения здесь появятся даты. Каждую из них можно будет исправить.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onRecord, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Отметить употребление")
                }
            }
        }
        items(events, key = { it.id }) { event ->
            Surface(
                onClick = { onEdit(event.id) }, enabled = !state.isSaving, shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(formatDate(event.date), style = MaterialTheme.typography.titleMedium)
                        Text("До отметки: " + consumptionFullDays(event.previousDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (event.id == state.currentStartConsumptionEventId) Text(
                            "Определяет начало текущего периода", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                        )
                        Text("Изменить дату", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ConsumptionHeading(title: String, onBack: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад") }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConsumptionDateChoice(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Row(
            Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .45f))
            }
        }
    }
}

private fun consumptionFullDays(days: Long): String =
    days.toString() + " " + (if (days % 10 == 1L && days % 100 != 11L) "полный" else "полных") + " " + dayWord(days)
