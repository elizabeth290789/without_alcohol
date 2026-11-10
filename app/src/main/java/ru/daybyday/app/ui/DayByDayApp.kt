package ru.daybyday.app.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DayByDayApp(state: AppUiState, actions: AppActions) {
    DayByDayTheme(state.darkTheme) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            state.error?.let {
                snackbar.showSnackbar(it, actionLabel = "Понятно")
                actions.onDismissError()
            }
        }
        Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.startDate == null -> OnboardingScreen(state, actions, Modifier.widthIn(max = 680.dp).fillMaxSize())
                    else -> MainExperience(state, actions)
                }
            }
        }
        state.celebration?.let { CelebrationDialog(it, actions.onDismissCelebration) }
    }
}

private data class Destination(val route: String, val title: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Главная", Icons.Outlined.Home),
    Destination("awards", "Достижения", Icons.Outlined.EmojiEvents),
    Destination("calendar", "Календарь", Icons.Outlined.CalendarMonth),
    Destination("stats", "Статистика", Icons.Outlined.BarChart),
)

@Composable
private fun MainExperience(state: AppUiState, actions: AppActions) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val fontScale = LocalDensity.current.fontScale
    val openEditor: (Long) -> Unit = { id ->
        nav.navigate("consumption/edit/" + id) { launchSingleTop = true }
    }
    val openConsumption: () -> Unit = {
        val existing = state.consumptionEvents.firstOrNull { it.date == state.today }
        if (existing != null) openEditor(existing.id)
        else nav.navigate("consumption") { launchSingleTop = true }
    }
    val openExistingFromForm: (Long) -> Unit = { id ->
        val currentDestinationId = nav.currentDestination?.id
        nav.navigate("consumption/edit/" + id) {
            if (currentDestinationId != null) popUpTo(currentDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (destinations.any { it.route == route }) {
                Column {
                    if (route == "home") HomeConsumptionAction(openConsumption, !state.isSaving)
                    NavigationBar(
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
                        modifier = Modifier.heightIn(min = (80 * fontScale.coerceIn(1f, 1.8f)).dp),
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route,
                                onClick = { nav.navigate(destination.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            NavHost(nav, startDestination = "home", modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                composable("home") { HomeScreen(state, onSettings = { nav.navigate("settings") }, onAward = { nav.navigate("award/" + Uri.encode(it.id)) }, onAllAwards = { nav.navigate("awards") }) }
                composable("awards") { AwardsScreen(state, onAward = { nav.navigate("award/" + Uri.encode(it.id)) }) }
                composable("calendar") { CalendarScreen(state) }
                composable("stats") { StatisticsScreen(state) }
                composable("settings") {
                    SettingsScreen(
                        state, actions, onBack = { nav.popBackStack() }, onNewPeriod = openConsumption,
                        onConsumptionHistory = { nav.navigate("consumption/history") }, onEditConsumption = openEditor,
                    )
                }
                composable("consumption") {
                    ConsumptionScreen(state, actions, onBack = { nav.popBackStack() }, onOpenExisting = openExistingFromForm)
                }
                composable("consumption/history") {
                    ConsumptionHistoryScreen(state, onBack = { nav.popBackStack() }, onEdit = openEditor, onRecord = openConsumption)
                }
                composable("consumption/edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { recordEntry ->
                    ConsumptionScreen(state, actions, onBack = { nav.popBackStack() }, onOpenExisting = openExistingFromForm,
                        eventId = recordEntry.arguments?.getLong("id"))
                }
                // Keep restored version1.0 back stacks, using the new workflow.
                composable("new-period") {
                    ConsumptionScreen(state, actions, onBack = { nav.popBackStack() }, onOpenExisting = openExistingFromForm)
                }
                composable("award/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                    val award = state.achievements.firstOrNull { it.id == backStack.arguments?.getString("id") }
                    if (award != null) AwardDetailScreen(award, state.bestDays, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun HomeConsumptionAction(onClick: () -> Unit, enabled: Boolean) {
    Surface(color = MaterialTheme.colorScheme.background) {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp).heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f)),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Outlined.EventNote, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Отметить употребление", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HomeScreen(state: AppUiState, onSettings: () -> Unit, onAward: (UiAchievement) -> Unit, onAllAwards: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("День за днём", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))).replaceFirstChar { it.titlecase(Locale("ru")) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ТВОЙ ТЕКУЩИЙ ПЕРИОД", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            DayCounter(state)
            Spacer(Modifier.height(10.dp))
            Text(when {
                state.startDate == state.today.plusDays(1) -> "Начало завтра"
                state.currentDay > 0 -> "Сейчас день " + state.currentDay
                state.startDate != null -> "Начало периода: " + formatDate(state.startDate)
                else -> "Путь начинается"
            }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            state.startDate?.let { Text("Начало · ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LevelCard(state)
        state.nextAchievement?.let { next ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Следующая награда", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAllAwards) { Text("Все", color = MaterialTheme.colorScheme.primary) }
                }
                Surface(onClick = { onAward(next) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AchievementArt(next, Modifier.size(90.dp), unlocked = true)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(next.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(5.dp))
                            val left = (next.days - state.fullDays).coerceAtLeast(0)
                            Text("Ещё $left ${dayWord(left)} до новой вершины", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { (state.fullDays.toFloat() / next.days.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = .12f))
                        }
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } ?: SoftCard(Modifier.fillMaxWidth()) {
            Text("Коллекция собрана", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text("Твой путь продолжается. Каждый новый день имеет значение.", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactStat("Лучший\nпериод", state.bestDays.toString(), Modifier.weight(1f))
            CompactStat("Всего\nдней", state.totalDays.toString(), Modifier.weight(1f))
            CompactStat("Получено\nнаград", state.achievements.count { it.unlockedDate != null }.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f)) {
                Text(state.motivation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("В своём темпе. С заботой о себе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DayCounter(state: AppUiState) {
    val next = state.nextAchievement?.days
    val progressTarget = if (next == null) 1f else (state.fullDays.toFloat() / next.coerceAtLeast(1)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(progressTarget, animationSpec = tween(800), label = "Прогресс до награды")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, 300.dp)
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val stroke = 8.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = .09f), Color.Transparent)), radius = size.minDimension / 2)
                drawCircle(track, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                drawArc(brush = Brush.sweepGradient(listOf(primary, AccentBlue, secondary, primary)), startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResponsiveCounter(state.fullDays.toString())
                Text("${dayWord(state.fullDays)} без алкоголя", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("ПОЛНЫХ ДНЕЙ", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
            }
        }
    }
}

@Composable
private fun ResponsiveCounter(value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.displayLarge
        val width = measurer.measure(AnnotatedString(value), style).size.width
        val allowedWidth = with(density) { maxWidth.toPx() }
        val ratio = (allowedWidth / width.coerceAtLeast(1)).coerceAtMost(1f)
        Text(value, style = style.copy(fontSize = style.fontSize * ratio, lineHeight = style.lineHeight * ratio), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun LevelCard(state: AppUiState) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)))) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                    Text(state.level.number.toString().padStart(2, '0'), Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text("УРОВЕНЬ ${state.level.number}", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                    Text(state.level.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { state.level.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .13f))
            Spacer(Modifier.height(8.dp))
            Text(state.level.nextAt?.let { "Следующий уровень — $it ${dayWord(it)}" } ?: "Высший уровень. Продолжай свой путь", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 18.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AwardsScreen(state: AppUiState, onAward: (UiAchievement) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Твоя коллекция", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Маленькие шаги. Большие открытия.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text("${state.achievements.count { it.unlockedDate != null }} из ${state.achievements.size} наград получено", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        items(state.achievements, key = { it.id }) { achievement ->
            val unlocked = achievement.unlockedDate != null
            Surface(onClick = { onAward(achievement) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AchievementArt(achievement, Modifier.size(112.dp))
                    Text(achievement.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(min = 42.dp))
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (unlocked) Icons.Outlined.CheckCircle else Icons.Outlined.Lock, contentDescription = if (unlocked) "Получено" else "Закрыто", modifier = Modifier.size(13.dp), tint = if (unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${achievement.days} ${dayWord(achievement.days)}", style = MaterialTheme.typography.bodySmall, color = if (unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardDetailScreen(achievement: UiAchievement, bestDays: Long, onBack: () -> Unit) {
    val unlocked = achievement.unlockedDate != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад") }
            Text("Достижение", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(34.dp))
        AchievementArt(achievement, Modifier.size(236.dp))
        Spacer(Modifier.height(20.dp))
        Surface(shape = CircleShape, color = if (unlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Text(if (unlocked) "Получено" else "Впереди открытие", Modifier.padding(horizontal = 16.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = if (unlocked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Text(achievement.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(achievement.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Text("${achievement.days} ${if (achievement.days % 10 == 1L && achievement.days % 100 != 11L) "полный" else "полных"} ${dayWord(achievement.days)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(7.dp))
            Text("Один период без алкоголя. Каждый завершённый день приближает к награде.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            if (unlocked) {
                Text("Дата получения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(achievement.unlockedDate!!), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            } else {
                LinearProgressIndicator(progress = { (bestDays.toFloat() / achievement.days.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(9.dp))
                Text("Полных дней в лучшем периоде: $bestDays / ${achievement.days}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Открытые награды остаются с тобой при начале нового периода.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CelebrationDialog(celebration: UiCelebration, onDismiss: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // Compose's duration scale follows the system animator setting, including zero.
    val reveal by animateFloatAsState(if (appeared) 1f else .75f, tween(550), label = "Появление награды")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (celebration.isNeutral) {
                Icon(Icons.Outlined.EventNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                celebration.achievement?.let { AchievementArt(it, Modifier.size(150.dp).scale(reveal), unlocked = true) }
                    ?: Icon(Icons.Outlined.EmojiEvents, contentDescription = null, modifier = Modifier.size(64.dp).scale(reveal), tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = { Text(celebration.title, textAlign = TextAlign.Center) },
        text = { Text(celebration.message, textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (celebration.isNeutral) "Понятно" else "Продолжить путь") } },
    )
}
