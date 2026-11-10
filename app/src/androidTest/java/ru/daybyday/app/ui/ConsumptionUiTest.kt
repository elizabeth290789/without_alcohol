package ru.daybyday.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixed authoritative previews isolate UI routing; the domain suite tests counting. */
@RunWith(AndroidJUnit4::class)
class ConsumptionUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 9)
    private val beginning = today.minusDays(8)
    private fun initialState() = AppUiState(
        today = today, startDate = beginning, fullDays = 8, currentDay = 9,
        bestDays = 8, totalDays = 8, consumptionMinDate = beginning.minusDays(1),
    )
    private fun preview(eventId: Long?, date: LocalDate): UiConsumptionPreview = when (date) {
        today -> UiConsumptionPreview(previousDays = 8, newStart = today.plusDays(1), fullDays = 0)
        today.minusDays(1) -> UiConsumptionPreview(previousDays = 7, newStart = today, fullDays = 0)
        else -> UiConsumptionPreview(error = "Выбери другую дату")
    }

    @Test fun homeActionIsVisibleWithLargeFontWithoutScrolling() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                DayByDayApp(initialState(), AppActions(previewConsumption = ::preview))
            }
        }
        compose.onNodeWithText("Отметить употребление").assertIsDisplayed().performClick()
        compose.onNodeWithText("Сегодня").assertExists()
        compose.onNodeWithText("Подтвердить отметку").performScrollTo().assertIsDisplayed()
    }

    @Test fun todayRequiresExplicitConfirmationAndShowsTomorrowAtZero() {
        val state = mutableStateOf(initialState())
        var recorded: LocalDate? = null
        compose.setContent {
            DayByDayApp(state.value, AppActions(
                previewConsumption = ::preview,
                onRecordConsumption = { date ->
                    recorded = date
                    state.value = state.value.copy(
                        startDate = today.plusDays(1), fullDays = 0, currentDay = 0,
                        consumptionEvents = listOf(UiConsumptionEvent(1, date, 8, beginning.minusDays(1), today)),
                        currentStartConsumptionEventId = 1, consumptionMinDate = today.plusDays(1),
                    )
                },
            ))
        }
        compose.onNodeWithText("Отметить употребление").performClick()
        assertNull(recorded)
        compose.onNodeWithText("Подтвердить отметку").performScrollTo().performClick()
        compose.onNodeWithText("Начало завтра").assertExists()
        assertEquals(today, recorded)
        assertEquals(0L, state.value.fullDays)
        assertEquals(0L, state.value.currentDay)
    }

    @Test fun yesterdayStartsTodayAtZeroFullDaysAndDayOne() {
        val state = mutableStateOf(initialState())
        var recorded: LocalDate? = null
        compose.setContent {
            DayByDayApp(state.value, AppActions(
                previewConsumption = ::preview,
                onRecordConsumption = { date ->
                    recorded = date
                    state.value = state.value.copy(
                        startDate = today, fullDays = 0, currentDay = 1,
                        consumptionEvents = listOf(UiConsumptionEvent(1, date, 7, beginning.minusDays(1), today)),
                        currentStartConsumptionEventId = 1, consumptionMinDate = today,
                    )
                },
            ))
        }
        compose.onNodeWithText("Отметить употребление").performClick()
        compose.onNodeWithText("Вчера").performClick()
        compose.onNodeWithText("Подтвердить отметку").performScrollTo().performClick()
        compose.onNodeWithText("Сейчас день 1").assertExists()
        assertEquals(today.minusDays(1), recorded)
        assertEquals(0L, state.value.fullDays)
    }

    @Test fun cancellingSelectionDoesNotRecordConsumption() {
        var recorded: LocalDate? = null
        compose.setContent {
            DayByDayApp(initialState(), AppActions(
                previewConsumption = ::preview, onRecordConsumption = { recorded = it },
            ))
        }
        compose.onNodeWithText("Отметить употребление").performClick()
        compose.onNodeWithText("Вчера").performClick()
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithText("Сейчас день 9").assertExists()
        assertNull(recorded)
    }

    @Test fun ledgerCanOpenAnyEarlierSavedEvent() {
        var editedId: Long? = null
        val first = UiConsumptionEvent(11, today.minusDays(4), 4, beginning.minusDays(1), today.minusDays(2))
        val second = UiConsumptionEvent(12, today.minusDays(1), 2, today.minusDays(3), today)
        val state = initialState().copy(consumptionEvents = listOf(first, second), currentStartConsumptionEventId = second.id)
        compose.setContent {
            DayByDayTheme {
                ConsumptionHistoryScreen(state, onBack = {}, onEdit = { editedId = it }, onRecord = {})
            }
        }
        compose.onNodeWithText(formatDate(first.date)).performScrollTo().performClick()
        assertEquals(first.id, editedId)
    }

    @Test fun editingExistingEventUsesEditCallbackAndDoesNotRecordAgain() {
        val event = UiConsumptionEvent(12, today.minusDays(1), 7, beginning.minusDays(1), today)
        var edited: Pair<Long, LocalDate>? = null
        var recorded: LocalDate? = null
        val state = initialState().copy(consumptionEvents = listOf(event), currentStartConsumptionEventId = event.id)
        compose.setContent {
            DayByDayTheme {
                ConsumptionScreen(
                    state, AppActions(
                        previewConsumption = ::preview,
                        onRecordConsumption = { recorded = it },
                        onEditConsumption = { id, date -> edited = id to date },
                    ), onBack = {}, onOpenExisting = {}, eventId = event.id,
                )
            }
        }
        compose.onNodeWithText("Подтвердить изменение").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Сегодня").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить изменение").performScrollTo().assertIsEnabled().performClick()
        assertEquals(event.id to today, edited)
        assertNull(recorded)
    }

    @Test fun todayAlreadyRecordedOpensItsEditorInsteadOfAnotherRecord() {
        val event = UiConsumptionEvent(33, today, 8, beginning.minusDays(1), today)
        val state = initialState().copy(
            startDate = today.plusDays(1), fullDays = 0, currentDay = 0,
            consumptionEvents = listOf(event), currentStartConsumptionEventId = event.id,
            consumptionMinDate = today.plusDays(1),
        )
        var duplicateRecord: LocalDate? = null
        compose.setContent {
            DayByDayApp(state, AppActions(
                previewConsumption = ::preview, onRecordConsumption = { duplicateRecord = it },
            ))
        }
        compose.onNodeWithText("Отметить употребление").performClick()
        compose.onNodeWithText("Изменить отметку").assertExists()
        compose.onNodeWithText("Подтвердить изменение").performScrollTo().assertIsNotEnabled()
        assertNull(duplicateRecord)
    }

    @Test fun linkedStartDateOpensItsConsumptionEditor() {
        var editedId: Long? = null
        val event = UiConsumptionEvent(22, today.minusDays(1), 7, beginning.minusDays(1), today)
        val state = initialState().copy(
            startDate = today, fullDays = 0, currentDay = 1,
            consumptionEvents = listOf(event), currentStartConsumptionEventId = event.id,
        )
        compose.setContent {
            DayByDayTheme {
                SettingsScreen(
                    state, AppActions(), onBack = {}, onNewPeriod = {},
                    onConsumptionHistory = {}, onEditConsumption = { editedId = it },
                )
            }
        }
        compose.onNodeWithText(formatDate(today)).performClick()
        assertEquals(event.id, editedId)
    }
}
