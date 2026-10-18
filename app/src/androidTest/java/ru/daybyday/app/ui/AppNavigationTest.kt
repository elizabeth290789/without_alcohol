package ru.daybyday.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.daybyday.app.DayByDayApplication
import ru.daybyday.app.MainActivity
import ru.daybyday.app.data.TrackerRepository

/** Real activity, Room and DataStore: verifies the complete offline navigation path. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: TrackerRepository

    @Before fun resetData() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DayByDayApplication
        repository = app.repository
        runBlocking { repository.clear() }
    }

    @After fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
        runBlocking { repository.clear() }
    }

    @Test fun firstLaunchStartsAtZeroAndPersistsAfterRecreation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Начать свой путь")
        compose.onNodeWithText("Начать свой путь").performScrollTo().performClick()
        waitForText("Сейчас день 1")
        compose.onNodeWithText("дней без алкоголя").assertExists()
        val saved = runBlocking { repository.readHistory() }
        assertEquals(LocalDate.now(), saved.periods.single().start)
        scenario.recreate()
        waitForText("Сейчас день 1")
        assertEquals(1, runBlocking { repository.readHistory() }.periods.size)
    }

    @Test fun allTabsAndAwardDetailsUseSavedHistory() {
        val today = LocalDate.now()
        runBlocking { repository.initialize(today.minusDays(8), today) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Сейчас день 9")
        compose.onNodeWithText("Достижения").performClick()
        waitForText("Твоя коллекция")
        compose.onNodeWithText("Первый шаг").performScrollTo().performClick()
        waitForText("Дата получения")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Календарь").performClick()
        waitForText("Каждый день имеет значение")
        compose.onNodeWithText("Статистика").performClick()
        waitForText("Ваш прогресс в деталях")
        compose.onNodeWithText("Главная").performClick()
        waitForText("Сейчас день 9")
        assertEquals(3, runBlocking { repository.readHistory() }.awards.size)
    }

    @Test fun clearDataRequiresConfirmationAndReturnsToWelcome() {
        val today = LocalDate.now()
        runBlocking { repository.initialize(today.minusDays(3), today) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Сейчас день 4")
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Удалить все данные").performScrollTo().performClick()
        compose.onNodeWithText("Удалить все данные?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        assertFalse(runBlocking { repository.readHistory() }.periods.isEmpty())
        compose.onNodeWithText("Удалить все данные").performScrollTo().performClick()
        compose.onNodeWithText("Удалить").performClick()
        waitForText("Начать свой путь")
        assertEquals(0, runBlocking { repository.readHistory() }.periods.size)
        assertEquals(0, runBlocking { repository.readHistory() }.awards.size)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
