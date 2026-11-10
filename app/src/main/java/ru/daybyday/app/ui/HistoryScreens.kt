package ru.daybyday.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

private val HistoryPurple = Color(0xFF9E8BFF)
private val HistoryTeal = Color(0xFF65DDCA)
private val RussianLocale = Locale.forLanguageTag("ru-RU")

@Composable
fun CalendarScreen(state: AppUiState, modifier: Modifier = Modifier) {
    val calendarCellHeight = (40 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    var month by remember(state.today) { mutableStateOf(YearMonth.from(state.today)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val achievementsByDate = remember(state.achievements) {
        state.achievements.filter { it.unlockedDate != null }.groupBy { it.unlockedDate }
    }
    val consumptionByDate = remember(state.consumptionEvents) {
        state.consumptionEvents.associateBy { it.date }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        HistoryHeading("Календарь", "Каждый день имеет значение")
        HistoryCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Предыдущий месяц", Modifier.size(20.dp))
                }
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", RussianLocale))
                        .replaceFirstChar { it.titlecase(RussianLocale) },
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Следующий месяц", Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                    Text(day, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val rows = (offset + month.lengthOfMonth() + 6) / 7
            repeat(rows) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { weekday ->
                        val dayNumber = week * 7 + weekday - offset + 1
                        if (dayNumber !in 1..month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).height(calendarCellHeight))
                        } else {
                            val date = month.atDay(dayNumber)
                            CalendarDay(
                                date = date,
                                confirmed = state.isConfirmed(date),
                                consumed = consumptionByDate.containsKey(date),
                                today = date == state.today,
                                earned = achievementsByDate[date].orEmpty().isNotEmpty(),
                                future = date > state.today,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDate = date },
                            )
                        }
                    }
                }
                if (week < rows - 1) Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendItem(HistoryTeal, "День без алкоголя")
                LegendItem(HistoryPurple, "Отмечено употребление")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(13.dp), tint = HistoryPurple)
                    Spacer(Modifier.width(5.dp))
                    Text("Достижение", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HistoryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarToday, null, tint = HistoryPurple, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("В вашем ритме", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.startDate == null) "Начните путь, и здесь появится ваша история."
                        else "Нажмите на день, чтобы увидеть подробности. Сегодня ещё продолжается — он будет засчитан завтра.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    selectedDate?.let { date ->
        CalendarDayDialog(date, state, achievementsByDate[date].orEmpty(), onDismiss = { selectedDate = null })
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    confirmed: Boolean,
    consumed: Boolean,
    today: Boolean,
    earned: Boolean,
    future: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val dayShape = RoundedCornerShape(13.dp)
    val background = when {
        consumed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        confirmed -> HistoryTeal.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val description = buildString {
        append(historyDate(date))
        if (today) append(", сегодня")
        append(when {
            consumed -> ", отмечено употребление, день не засчитывается"
            confirmed -> ", день без алкоголя подтверждён"
            else -> ", нет завершённого дня"
        })
        if (earned) append(", получено достижение")
    }
    Box(
        modifier.heightIn(min = (40 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
            .background(background, dayShape)
            .then(if (today) Modifier.border(1.5.dp, HistoryPurple, dayShape) else Modifier)
            .clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(), fontSize = 13.sp, maxLines = 1,
            fontWeight = if (consumed || confirmed || today) FontWeight.Bold else FontWeight.Medium,
            color = when {
                consumed -> MaterialTheme.colorScheme.primary
                confirmed -> if (MaterialTheme.colorScheme.background.red < 0.5f) HistoryTeal else Color(0xFF126B5D)
                today -> HistoryPurple
                future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (consumed) Box(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)
            .size(5.dp).background(HistoryPurple, CircleShape))
        if (earned) Icon(Icons.Rounded.Star, null,
            Modifier.align(if (consumed) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(start = if (consumed) 3.dp else 0.dp, bottom = 2.dp).size(9.dp),
            tint = HistoryPurple)
    }
}

@Composable
private fun CalendarDayDialog(date: LocalDate, state: AppUiState, earned: List<UiAchievement>, onDismiss: () -> Unit) {
    val consumptionEvent = state.consumptionEvents.firstOrNull { it.date == date }
    val confirmed = state.isConfirmed(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(when {
            consumptionEvent != null -> Icons.Rounded.EventNote
            confirmed -> Icons.Rounded.Check
            else -> Icons.Rounded.CalendarToday
        }, null, tint = when {
            consumptionEvent != null -> MaterialTheme.colorScheme.primary
            confirmed -> HistoryTeal
            else -> HistoryPurple
        }) },
        title = { Text(historyDate(date), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(when {
                    consumptionEvent != null -> "В этот день отмечено употребление. Он не входит в дни без алкоголя. История и полученные награды сохраняются."
                    confirmed -> "Завершённый день без алкоголя. Ещё один шаг в вашу пользу."
                    date == state.today && state.startDate != null && date >= state.startDate -> "Сегодня путь продолжается. Этот день будет засчитан после полуночи."
                    date > state.today -> "Этот день ещё впереди. Двигайтесь в своём темпе."
                    else -> "За этот день нет записи о завершённом периоде."
                })
                if (earned.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Достижения этого дня", style = MaterialTheme.typography.labelLarge)
                    earned.forEach { achievement ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = HistoryPurple, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(achievement.title, fontWeight = FontWeight.SemiBold)
                                Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}

@Composable
fun StatisticsScreen(state: AppUiState, modifier: Modifier = Modifier) {
    val unlocked = state.achievements.count { it.unlockedDate != null }
    var visiblePeriodCount by remember(state.periods.size) { mutableStateOf(20) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        HistoryHeading("Статистика", "Ваш прогресс в деталях")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Текущий период", state.fullDays.toString(), historyDayWord(state.fullDays),
                Icons.Rounded.TrendingUp, HistoryTeal, Modifier.weight(1f))
            MetricCard("Лучший результат", state.bestDays.toString(), historyDayWord(state.bestDays),
                Icons.Rounded.EmojiEvents, HistoryPurple, Modifier.weight(1f))
        }
        HistoryCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallMetric("Всего дней", state.totalDays.toString(), Modifier.weight(1f))
                SmallMetric("Периодов завершено", state.completedPeriods.toString(), Modifier.weight(1f))
                SmallMetric("Наград", "$unlocked/${state.achievements.size}", Modifier.weight(0.8f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HistorySectionLabel("ИСТОРИЯ ПЕРИОДОВ")
            HistoryCard {
                Text("Шаг за шагом", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Завершённые дни в каждом периоде", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                if (state.periods.isEmpty()) {
                    EmptyHistory("Первый период станет началом вашей истории.")
                } else {
                    PeriodChart(state.periods)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HistorySectionLabel("ВАША ПОСЛЕДОВАТЕЛЬНОСТЬ")
            HistoryCard {
                Text("Последние 12 недель", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Каждая клетка — один день", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                ActivityHeatmap(state)
                Spacer(Modifier.height(16.dp))
                LegendItem(HistoryTeal, "Завершённый день без алкоголя")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HistorySectionLabel("ЛИЧНАЯ ИСТОРИЯ")
            HistoryCard {
                RecordRow(Icons.Rounded.CalendarToday, "Начало текущего периода",
                    state.startDate?.let(::historyDate) ?: "Пока не начат")
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(16.dp))
                RecordRow(Icons.Rounded.EmojiEvents, "Личный рекорд", "${state.bestDays} ${historyDayWord(state.bestDays)}")
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(16.dp))
                RecordRow(Icons.Rounded.AutoAwesome, "Получено достижений", "$unlocked из ${state.achievements.size}")
            }
        }
        if (state.periods.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HistorySectionLabel("ПЕРИОДЫ")
                HistoryCard {
                    state.periods.sortedByDescending { it.start }.take(visiblePeriodCount).forEachIndexed { index, period ->
                        if (index > 0) {
                            Spacer(Modifier.height(15.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            Spacer(Modifier.height(15.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (period.endInclusive == null) "Текущий период" else "Завершённый период",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (period.endInclusive == null) HistoryPurple else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text("${historyDate(period.start)} — ${period.endInclusive?.let(::historyDate) ?: "сейчас"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${period.fullDays} ${historyDayWord(period.fullDays)}",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (visiblePeriodCount < state.periods.size) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { visiblePeriodCount += 20 }) {
                            Text("Показать ещё периоды")
                        }
                    }
                }
            }
        }
        Text("Прогресс — это забота о себе, день за днём.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PeriodChart(periods: List<UiPeriod>) {
    val sorted = periods.sortedBy { it.start }
    val visible = sorted.takeLast(8)
    val firstIndex = sorted.size - visible.size + 1
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val accessibleSummary = visible.mapIndexed { index, period ->
        "Период ${firstIndex + index}: ${period.fullDays} ${historyDayWord(period.fullDays)}"
    }.joinToString(". ")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartWidth = maxOf(maxWidth, (visible.size * 44 * fontScale).dp)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height((170 * fontScale).dp)
                .semantics { contentDescription = accessibleSummary }) {
                val maxDays = max(1L, visible.maxOf { it.fullDays }).toFloat()
                val cellWidth = size.width / visible.size
                val baseline = size.height - (25 * fontScale).dp.toPx()
                val chartHeight = baseline - (26 * fontScale).dp.toPx()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = foreground.toArgb()
                    textAlign = Paint.Align.CENTER
                    textSize = 11.sp.toPx()
                }
                val points = visible.mapIndexed { index, period ->
                    Offset(cellWidth * (index + 0.5f),
                        baseline - (period.fullDays.coerceAtLeast(0).toFloat() / maxDays) * chartHeight)
                }
                drawLine(baselineColor, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(HistoryTeal, start, end, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
                visible.forEachIndexed { index, period ->
                    val point = points[index]
                    val accent = if (period.endInclusive == null) HistoryPurple else HistoryTeal
                    drawCircle(accent.copy(alpha = 0.16f), radius = 9.dp.toPx(), center = point)
                    drawCircle(accent, radius = 4.5.dp.toPx(), center = point)
                    drawContext.canvas.nativeCanvas.drawText(period.fullDays.toString(), point.x,
                        point.y - 12.dp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("${firstIndex + index}", point.x,
                        size.height - 4.dp.toPx(), paint)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(if (sorted.size > 8) "Последние 8 периодов · номер периода по горизонтали"
        else "Номер периода по горизонтали", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActivityHeatmap(state: AppUiState) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val monday = state.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
    val confirmedCount = (0L until 84L).count { state.isConfirmed(monday.plusDays(it)) }
    Column(Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Активность за последние 12 недель: $confirmedCount завершённых дней без алкоголя"
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { day ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (day == 0) "Пн" else if (day == 3) "Чт" else if (day == 6) "Вс" else "",
                    Modifier.width((19 * fontScale).dp), style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(12) { week ->
                    val date = monday.plusDays((week * 7 + day).toLong())
                    val confirmed = state.isConfirmed(date)
                    val future = date > state.today
                    Box(Modifier.weight(1f).aspectRatio(1f).background(
                        when {
                            confirmed -> HistoryTeal.copy(alpha = 0.8f)
                            future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        }, RoundedCornerShape(3.dp),
                    ).then(if (date == state.today) Modifier.border(1.dp, HistoryPurple, RoundedCornerShape(3.dp)) else Modifier))
                }
            }
        }
    }
}

private fun AppUiState.isConfirmed(date: LocalDate): Boolean =
    !date.isAfter(today) && consumptionEvents.none { it.date == date } && periods.any { period ->
        val end = period.endInclusive ?: today.minusDays(1)
        !date.isBefore(period.start) && !date.isAfter(end)
    }

@Composable
private fun HistoryHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun HistorySectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    HistoryCard(modifier) {
        Icon(icon, null, Modifier.size(23.dp), tint = accent)
        Spacer(Modifier.height(18.dp))
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(13.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallMetric(title: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordRow(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(HistoryPurple.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(19.dp), tint = HistoryPurple) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHistory(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun historyDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", RussianLocale))

private fun historyDayWord(value: Long): String = when {
    value % 100 in 11..14 -> "дней"
    value % 10 == 1L -> "день"
    value % 10 in 2..4 -> "дня"
    else -> "дней"
}
