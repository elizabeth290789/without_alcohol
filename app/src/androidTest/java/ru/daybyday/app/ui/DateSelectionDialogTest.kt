package ru.daybyday.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateSelectionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backwardClockBeyondHistoryShowsExplanationWithoutInvalidPicker() {
        val open = mutableStateOf(true)
        var dateCommitted = false
        compose.setContent {
            DayByDayTheme {
                if (open.value) {
                    DateSelectionDialog(
                        initialDate = LocalDate.of(2026, 10, 18),
                        minDate = LocalDate.of(2026, 10, 18),
                        maxDate = LocalDate.of(2025, 10, 18),
                        onDate = { dateCommitted = true },
                        onDismiss = { open.value = false },
                    )
                } else Text("Выбор даты закрыт")
            }
        }
        compose.onNodeWithText("Проверь дату устройства").assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText("Выбор даты закрыт").assertIsDisplayed()
        assertFalse(dateCommitted)
    }
}
