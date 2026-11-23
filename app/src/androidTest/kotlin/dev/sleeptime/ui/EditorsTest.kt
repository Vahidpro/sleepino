package dev.sleeptime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.sleeptime.R
import dev.sleeptime.core.Configuration
import dev.sleeptime.core.Preferences
import dev.sleeptime.core.SleepRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.junit.Rule
import org.junit.Test

class EditorsTest {
 @get:Rule val compose = createComposeRule()
 private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

 @Test fun sharedTimesApplyToSelectedDaysOnly() {
  val tuesday = SleepRule(2, bedtime = 1320, wake = 360, durationMinutes = 450, useWakeTime = false)
  val configuration = Configuration(prefs = Preferences(weekStart = 1), sleep = listOf(tuesday))
  var saved: List<SleepRule>? = null
  compose.setContent {
   SleepTheme(configuration.prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) { SleepEditor(configuration) { saved = it } }
   }
  }

  val locale = context.resources.configuration.locales[0]
  val apply = compose.onNodeWithText(context.getString(R.string.apply_selected_days))
  apply.assertIsNotEnabled()
  for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)) {
   compose.onNodeWithText(day.getDisplayName(TextStyle.FULL, locale)).performScrollTo().performClick().assertIsSelected()
  }
  apply.performScrollTo().performClick()

  compose.runOnIdle {
   val rules = checkNotNull(saved)
   check(rules.filter { it.enabled }.map { it.day } == listOf(1, 2, 3))
   check(rules[1] == tuesday)
   for (rule in listOf(rules[0], rules[2])) {
    check(rule.useWakeTime && rule.bedtime == 1320 && rule.wake == 360)
   }
  }
 }

 @Test fun timePickerSwitchesBetweenDialAndKeyboard() {
  var selected: Int? = null
  compose.setContent {
   SleepTheme(Preferences()) { TimeButton(R.string.bedtime, 22 * 60 + 15, Preferences()) { selected = it } }
  }

  compose.onNodeWithText(context.getString(R.string.bedtime)).performClick()
  compose.onNodeWithContentDescription(context.getString(R.string.time_keyboard)).performClick()
  compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("۲۱")
  compose.onNodeWithContentDescription(context.getString(R.string.time_dial)).assertExists().performClick()
  compose.onNodeWithContentDescription(context.getString(R.string.time_keyboard)).assertExists()
  compose.onNodeWithText(context.getString(R.string.done)).performClick()
  compose.runOnIdle { check(selected == 21 * 60 + 15) }
 }
}
