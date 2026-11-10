package ru.daybyday.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.daybyday.app.BuildConfig.VERSION_NAME
import java.time.LocalDate
import java.util.Locale

@Composable
fun OnboardingScreen(
    state: AppUiState,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    var selectedEpochDay by rememberSaveable { mutableStateOf(state.today.toEpochDay()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay).coerceAtMost(state.today)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ДЕНЬ ЗА ДНЁМ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(28.dp))
        WelcomeIllustration()
        Spacer(Modifier.height(28.dp))
        Text(
            "Большие перемены.\nМаленькие шаги.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Твой личный путь без алкоголя. Замечай прогресс, береги себя и двигайся в своём темпе.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("Когда начался твой путь?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Выбери первый день без алкоголя. Его можно изменить позже.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            DateValueRow(
                title = if (selectedDate == state.today) "Сегодня" else "Первый день",
                date = selectedDate,
                onClick = { showDatePicker = true },
                enabled = !state.isSaving,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { actions.onStart(selectedDate) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = !state.isLoading && !state.isSaving,
        ) {
            Text("Начать свой путь", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Без аккаунта. Данные только на устройстве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
    if (showDatePicker && !state.isSaving) {
        DateSelectionDialog(
            initialDate = selectedDate,
            maxDate = state.today,
            onDate = { selectedEpochDay = it.toEpochDay(); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun WelcomeIllustration() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier.size(184.dp).background(
            Brush.radialGradient(listOf(primary.copy(alpha = .18f), tertiary.copy(alpha = .06f))),
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(160.dp)) {
            val center = Offset(size.width / 2, size.height * .51f)
            val radius = size.width * .21f
            drawCircle(primary.copy(alpha = .12f), radius * 1.65f, center)
            drawCircle(primary.copy(alpha = .22f), radius * 1.3f, center)
            drawCircle(primary, radius, center)
            drawLine(tertiary, Offset(size.width * .15f, size.height * .73f), Offset(size.width * .85f, size.height * .73f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = tertiary.copy(alpha = .55f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(size.width * .1f, size.height * .75f),
                size = Size(size.width * .8f, size.height * .5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            listOf(-.65f, 0f, .65f).forEach { x ->
                drawCircle(tertiary.copy(alpha = .8f), 2.5.dp.toPx(), Offset(center.x + x * size.width * .45f, size.height * .2f + kotlin.math.abs(x) * size.height * .1f))
            }
        }
        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun SettingsScreen(
    state: AppUiState,
    actions: AppActions,
    onBack: () -> Unit,
    onNewPeriod: () -> Unit,
    onConsumptionHistory: () -> Unit = {},
    onEditConsumption: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val notifications = state.notifications
    val controlsEnabled = !state.isSaving

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
        ScreenTitle("Настройки", onBack, enabled = controlsEnabled)
        Spacer(Modifier.height(24.dp))
        SectionLabel("ТВОЙ ПУТЬ")
        Spacer(Modifier.height(10.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            state.startDate?.let { start ->
                val linkedConsumptionId = state.currentStartConsumptionEventId
                DateValueRow(
                    title = if (linkedConsumptionId != null) "Начало текущего периода" else "Дата начала",
                    date = start,
                    onClick = {
                        if (linkedConsumptionId != null) onEditConsumption(linkedConsumptionId)
                        else showDatePicker = true
                    },
                    enabled = controlsEnabled,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (linkedConsumptionId != null) {
                        "Начало периода определяется отметкой об употреблении. Чтобы изменить эту дату, отредактируй отметку."
                    } else {
                        "Счётчик обновится с учётом новой даты. Завершённые периоды сохранятся."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            SettingsNavigationRow(
                title = "Отметить употребление",
                subtitle = "Сохрани отметку и продолжай свой путь",
                onClick = onNewPeriod,
                enabled = controlsEnabled,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            SettingsNavigationRow(
                title = "История отметок",
                subtitle = "Сохранено: ${state.consumptionEvents.size}",
                onClick = onConsumptionHistory,
                enabled = controlsEnabled,
            )
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("УВЕДОМЛЕНИЯ")
        Spacer(Modifier.height(10.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            SettingsSwitchRow(
                title = "Ежедневное напоминание",
                subtitle = "Немного поддержки каждый день",
                checked = notifications.dailyEnabled,
                onCheckedChange = { actions.onNotificationsChange(notifications.copy(dailyEnabled = it)) },
                icon = Icons.Rounded.NotificationsNone,
                enabled = controlsEnabled,
            )
            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (controlsEnabled) 1f else .55f).clickable(enabled = controlsEnabled) { showTimePicker = true }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Время напоминания", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format(Locale.ROOT, "%02d:%02d", notifications.dailyHour, notifications.dailyMinute), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            SettingsSwitchRow(
                title = "Новые достижения",
                subtitle = "Отмечать важные этапы пути",
                checked = notifications.achievementEnabled,
                onCheckedChange = { actions.onNotificationsChange(notifications.copy(achievementEnabled = it)) },
                enabled = controlsEnabled,
            )
            SettingsSwitchRow(
                title = "Личный рекорд",
                subtitle = "Сообщать о новом лучшем результате",
                checked = notifications.recordEnabled,
                onCheckedChange = { actions.onNotificationsChange(notifications.copy(recordEnabled = it)) },
                enabled = controlsEnabled,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            SettingsSwitchRow(
                title = "Приватные уведомления",
                subtitle = "Нейтральный текст без упоминания алкоголя",
                checked = notifications.privateNotifications,
                onCheckedChange = { actions.onNotificationsChange(notifications.copy(privateNotifications = it)) },
                icon = Icons.Rounded.Shield,
                enabled = controlsEnabled,
            )
            if (!state.notificationsAllowed) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text("Уведомления не разрешены", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Разреши их в настройках устройства, чтобы получать напоминания.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    TextButton(onClick = actions.onOpenNotificationSettings, enabled = controlsEnabled) { Text("Открыть настройки") }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("ОФОРМЛЕНИЕ")
        Spacer(Modifier.height(10.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            SettingsSwitchRow("Тёмная тема", "Мягкие цвета для спокойного вечера", state.darkTheme, actions.onThemeChange, Icons.Rounded.DarkMode, enabled = controlsEnabled)
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("ДАННЫЕ И ПРИВАТНОСТЬ")
        Spacer(Modifier.height(10.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Твои данные хранятся только на этом устройстве. Здесь нет аккаунтов, рекламы и отслеживания.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Удалить все данные") }
        }
        Spacer(Modifier.height(26.dp))
        Text("День за днём · Версия $VERSION_NAME", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
    if (showDatePicker && state.startDate != null && state.currentStartConsumptionEventId == null && controlsEnabled) {
        DateSelectionDialog(
            initialDate = state.startDate,
            maxDate = state.today,
            minDate = state.earliestStartDate,
            onDate = { actions.onEditStart(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker && controlsEnabled) {
        ReminderTimeDialog(
            hour = notifications.dailyHour,
            minute = notifications.dailyMinute,
            onSave = { hour, minute ->
                actions.onNotificationsChange(notifications.copy(dailyHour = hour, dailyMinute = minute))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Удалить все данные?") },
            text = { Text("История периодов, отметки об употреблении, достижения и настройки будут удалены с устройства. Восстановить их не получится.") },
            confirmButton = {
                TextButton(onClick = { showClearConfirmation = false; actions.onClearAll() }, enabled = controlsEnabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun NewPeriodScreen(
    state: AppUiState,
    actions: AppActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStart = state.startDate ?: state.today
    val canFinish = currentStart < state.today
    var endEpochDay by rememberSaveable(currentStart.toEpochDay()) { mutableStateOf(state.today.minusDays(1).toEpochDay()) }
    var newStartEpochDay by rememberSaveable(currentStart.toEpochDay()) { mutableStateOf(state.today.toEpochDay()) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    val endDate = LocalDate.ofEpochDay(endEpochDay)
    val newStartDate = LocalDate.ofEpochDay(newStartEpochDay)
    val validDates = canFinish && endDate >= currentStart && endDate < newStartDate && newStartDate <= state.today

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
        ScreenTitle("Новый период", onBack)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Новый день —\nновая возможность", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("Твой опыт остаётся с тобой. Сохрани предыдущий период и начни следующий в своём темпе.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        if (!canFinish) {
            SoftCard(Modifier.fillMaxWidth()) {
                Text(if (currentStart > state.today) "Проверь дату устройства" else "Сегодня — первый день", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(if (currentStart > state.today) "Начало сохранённого периода позже текущей даты. Проверь дату и часовой пояс устройства перед созданием нового периода." else "Завершить период можно, когда пройдёт хотя бы один полный день. Возвращайся завтра — твой путь уже начался.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            SectionLabel("ЗАВЕРШАЕМ ТЕКУЩИЙ ПЕРИОД")
            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                Text("Начало: ${formatDate(currentStart)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                DateValueRow("Последний полный день", endDate, onClick = { showEndPicker = true })
                Spacer(Modifier.height(10.dp))
                Text("Эта дата включается в результат. Сегодняшний день ещё идёт.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("НАЧИНАЕМ СЛЕДУЮЩИЙ")
            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                DateValueRow("Первый день нового периода", newStartDate, onClick = { showStartPicker = true })
                Spacer(Modifier.height(10.dp))
                Text("Периоды не пересекаются. Между ними может быть перерыв.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (validDates) { actions.onNewPeriod(endDate, newStartDate); onBack() } },
            enabled = validDates && !state.isLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Начать новый период", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к своему пути") }
        Spacer(Modifier.height(20.dp))
    }
    if (showEndPicker && canFinish) {
        DateSelectionDialog(
            initialDate = endDate,
            maxDate = state.today.minusDays(1),
            minDate = currentStart,
            onDate = { date ->
                endEpochDay = date.toEpochDay()
                if (newStartDate <= date) newStartEpochDay = date.plusDays(1).toEpochDay()
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
    if (showStartPicker && canFinish) {
        DateSelectionDialog(
            initialDate = newStartDate,
            maxDate = state.today,
            minDate = endDate.plusDays(1),
            onDate = { newStartEpochDay = it.toEpochDay(); showStartPicker = false },
            onDismiss = { showStartPicker = false },
        )
    }
}

@Composable
private fun ScreenTitle(title: String, onBack: () -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .55f)
            .clickable(enabled = enabled, onClick = onClick).padding(top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DateValueRow(title: String, date: LocalDate, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .55f).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(formatDate(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .55f).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}


@Composable
private fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hoursText by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%02d", hour)) }
    var minutesText by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%02d", minute)) }
    val selectedHour = hoursText.toIntOrNull()
    val selectedMinute = minutesText.toIntOrNull()
    val validTime = selectedHour != null && selectedHour in 0..23 && selectedMinute != null && selectedMinute in 0..59
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время для себя") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Введи время в 24-часовом формате.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { value -> if (value.length <= 2 && value.all { it in '0'..'9' }) hoursText = value },
                        label = { Text("Часы") },
                        placeholder = { Text("09") },
                        isError = hoursText.isNotEmpty() && (selectedHour == null || selectedHour !in 0..23),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { value -> if (value.length <= 2 && value.all { it in '0'..'9' }) minutesText = value },
                        label = { Text("Минуты") },
                        placeholder = { Text("00") },
                        isError = minutesText.isNotEmpty() && (selectedMinute == null || selectedMinute !in 0..59),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Часы: 0–23 · Минуты: 0–59", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (validTime) onSave(requireNotNull(selectedHour), requireNotNull(selectedMinute)) }, enabled = validTime) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
