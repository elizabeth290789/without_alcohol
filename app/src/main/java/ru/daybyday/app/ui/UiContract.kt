package ru.daybyday.app.ui

import java.time.LocalDate

/** Stable presentation model: all counting and persistence stay in the view model. */
data class AppUiState(
    val isLoading: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val startDate: LocalDate? = null,
    val earliestStartDate: LocalDate? = null,
    val fullDays: Long = 0,
    val currentDay: Long = 1,
    val bestDays: Long = 0,
    val totalDays: Long = 0,
    val completedPeriods: Int = 0,
    val level: UiLevel = UiLevel(),
    val achievements: List<UiAchievement> = emptyList(),
    val nextAchievement: UiAchievement? = null,
    val periods: List<UiPeriod> = emptyList(),
    val consumptionEvents: List<UiConsumptionEvent> = emptyList(),
    val currentStartConsumptionEventId: Long? = null,
    val consumptionMinDate: LocalDate? = null,
    val isSaving: Boolean = false,
    val notifications: UiNotificationSettings = UiNotificationSettings(),
    val notificationsAllowed: Boolean = true,
    val darkTheme: Boolean = true,
    val motivation: String = "Прогресс складывается из маленьких шагов",
    val error: String? = null,
    val celebration: UiCelebration? = null,
)

data class UiLevel(
    val number: Int = 1,
    val title: String = "Начало пути",
    val progress: Float = 0f,
    val nextAt: Long? = 3,
)

data class UiAchievement(
    val id: String,
    val days: Long,
    val title: String,
    val description: String,
    val unlockedDate: LocalDate? = null,
)

data class UiPeriod(
    val id: Long,
    val start: LocalDate,
    /** Last confirmed day; null for the current period. Today is still in progress. */
    val endInclusive: LocalDate? = null,
    val fullDays: Long = 0,
)

data class UiConsumptionEvent(
    val id: Long,
    val date: LocalDate,
    val previousDays: Long,
    val minDate: LocalDate,
    val maxDate: LocalDate,
)

data class UiConsumptionPreview(
    val previousDays: Long = 0,
    val newStart: LocalDate? = null,
    val fullDays: Long = 0,
    val error: String? = null,
    val existingEventId: Long? = null,
    val newPeriod: Boolean = true,
)

data class UiNotificationSettings(
    val dailyEnabled: Boolean = false,
    val achievementEnabled: Boolean = false,
    val recordEnabled: Boolean = false,
    val dailyHour: Int = 9,
    val dailyMinute: Int = 0,
    val privateNotifications: Boolean = true,
)

data class UiCelebration(
    val title: String,
    val message: String,
    val achievement: UiAchievement? = null,
    val isNeutral: Boolean = false,
)

data class AppActions(
    val onStart: (LocalDate) -> Unit = {},
    val onEditStart: (LocalDate) -> Unit = {},
    val onNewPeriod: (endInclusive: LocalDate, newStart: LocalDate) -> Unit = { _, _ -> },
    val onNotificationsChange: (UiNotificationSettings) -> Unit = {},
    val onThemeChange: (Boolean) -> Unit = {},
    val onClearAll: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onDismissCelebration: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onRecordConsumption: (LocalDate) -> Unit = {},
    val onEditConsumption: (Long, LocalDate) -> Unit = { _, _ -> },
    val previewConsumption: (Long?, LocalDate) -> UiConsumptionPreview = { _, _ ->
        UiConsumptionPreview(error = "Не удалось подготовить расчёт")
    },
)
