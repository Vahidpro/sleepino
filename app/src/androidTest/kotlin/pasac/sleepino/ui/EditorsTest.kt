package pasac.sleepino.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import pasac.sleepino.R
import pasac.sleepino.core.Configuration
import pasac.sleepino.core.DateOverride
import pasac.sleepino.core.DayRule
import pasac.sleepino.core.Preferences
import pasac.sleepino.core.SleepRule
import pasac.sleepino.core.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import org.junit.Rule
import org.junit.Test

class EditorsTest {
 @get:Rule val compose = createComposeRule()
 private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

 @Test fun sleepEditorApplyPersistsImmediatelyWithoutNeedingSave() {
  val tuesday = SleepRule(2, bedtime = 1320, wake = 360, durationMinutes = 450, useWakeTime = false)
  val configuration = Configuration(prefs = Preferences(weekStart = 1), sleep = listOf(tuesday))
  var applied: List<SleepRule>? = null
  var saved: List<SleepRule>? = null
  compose.setContent {
   SleepTheme(configuration.prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
     SleepEditor(
      config = configuration,
      onApply = { rules, _, _, _ -> applied = rules },
      onSave = { rules, _, _, _ -> saved = rules }
     )
    }
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
   val rules = checkNotNull(applied)
   check(saved == null)
   check(rules.filter { it.enabled }.map { it.day } == listOf(1, 2, 3))
   check(rules[1] == tuesday)
   for (rule in listOf(rules[0], rules[2])) {
    check(rule.useWakeTime && rule.bedtime == 1320 && rule.wake == 360)
   }
  }
  compose.onNodeWithText(context.getString(R.string.applied_to_draft)).assertExists()
 }

 @Test fun activityEditorAppliesSlotImmediatelyOnApply() {
  val configuration = Configuration(prefs = Preferences(weekStart = 1))
  var applied: List<DayRule>? = null
  compose.setContent {
   SleepTheme(configuration.prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
     ActivityEditorContent(
      initialRules = (1..7).map { DayRule(it) },
      kind = pasac.sleepino.core.Kind.WORK,
      p = configuration.prefs,
      locale = context.resources.configuration.locales[0],
      onApply = { applied = it },
      onSave = {}
     )
    }
   }
  }

  compose.onNodeWithText(context.getString(R.string.weekdays)).performScrollTo().performClick().assertIsSelected()
  compose.onNodeWithText(context.getString(R.string.apply_selected_days)).performScrollTo().performClick()

  compose.runOnIdle {
   val rules = checkNotNull(applied)
   check(rules.filter { it.slots.isNotEmpty() }.map { it.day } == listOf(1, 2, 3, 4, 5))
   check(rules.first().slots == listOf(TimeSlot(540, 1020)))
  }
 }

 @Test fun overrideEditorSupportsQuickDateChipsAndSegmentedModes() {
  val prefs = Preferences(zoneId = "UTC")
  val tomorrow = LocalDate.now(prefs.zone()).plusDays(1)
  var saved: DateOverride? = null
  compose.setContent {
   SleepTheme(prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
     OverrideEditorContent(
      config = Configuration(prefs = prefs),
      onSaveOverride = { saved = it },
      onDeleteOverride = {}
     )
    }
   }
  }

  compose.onNodeWithText(context.getString(R.string.tomorrow)).performScrollTo().performClick().assertIsSelected()
  compose.onAllNodesWithText(context.getString(R.string.override_off))[0].performScrollTo().performClick()
  compose.onNodeWithText(context.getString(R.string.save)).performScrollTo().performClick()

  compose.runOnIdle {
   val result = checkNotNull(saved)
   check(result.date == tomorrow.toString())
   check(result.work == emptyList<TimeSlot>())
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
