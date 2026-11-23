package dev.sleeptime.ui
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.sleeptime.R
import dev.sleeptime.core.ActiveSession
import dev.sleeptime.core.Preferences
import dev.sleeptime.core.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PresentationTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

 private fun beforeBedtime(minutes: Long): DashboardState {
  val s = previewState()
  return s.copy(now = checkNotNull(s.occurrence).start.minusSeconds(minutes * 60))
 }
 @Test fun sleepConfirmationActionIsReachable(){
  var confirmed=false;val s=beforeBedtime(20)
  compose.setContent{SleepTheme(s.config.prefs){BedtimeHero(s, { confirmed = true }, {}, {}, {}, {})}}
  compose.onNodeWithText(context.getString(R.string.go_to_sleep)).assertExists().performClick()
  compose.runOnIdle{check(confirmed)}
 }
 @Test fun emptySleepScheduleExplainsNextAction(){
  val s=previewState().copy(occurrence=null)
  compose.setContent{SleepTheme(s.config.prefs){BedtimeHero(s, {}, {}, {}, {}, {})}}
  compose.onNodeWithText(context.getString(R.string.setup_sleep)).assertExists()
 }
 @Test fun removingSleepScheduleReplacesThePrimaryAction(){
  val state = mutableStateOf(beforeBedtime(20))
  compose.setContent {
   SleepTheme(state.value.config.prefs) { BedtimeHero(state.value, {}, {}, {}, {}, {}) }
  }
  val confirm = compose.onNodeWithText(context.getString(R.string.go_to_sleep))
  confirm.assertExists()

  compose.mainClock.autoAdvance = false
  compose.runOnIdle { state.value = state.value.copy(occurrence = null) }
  compose.mainClock.advanceTimeBy(640)

  compose.onNodeWithText(context.getString(R.string.setup_sleep)).assertHasClickAction()
  confirm.assertDoesNotExist()
 }
 @Test fun persianAmoledAndLargeFontsKeepPrimaryAction(){
  val localized=context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag("fa"))})
  val s=beforeBedtime(20).let{it.copy(config=it.config.copy(prefs=it.config.prefs.copy(theme=ThemeMode.AMOLED,persianNumerals=true)))}
  compose.setContent{CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration, LocalDensity provides Density(context.resources.displayMetrics.density, 1.8f)){SleepTheme(s.config.prefs){BedtimeHero(s, {}, {}, {}, {}, {})}}}
  compose.onNodeWithText(localized.getString(R.string.go_to_sleep)).assertExists()
 }
 @Test fun confirmationShowsReportedStateAndSupportsUndo() {
  var undone = false
  val s = previewState().copy(confirmed = true)
  compose.setContent {
   SleepTheme(s.config.prefs) { BedtimeHero(s, {}, {}, {}, { undone = true }, {}) }
  }
  compose.onNodeWithText(context.getString(R.string.rest_well)).assertExists()
  compose.onNodeWithContentDescription(context.getString(R.string.undo)).performClick()
  compose.runOnIdle { check(undone) }
 }

 @Test fun activeSnoozeOffersCancellationBeforeShowingOverdueState() {
  var cancelled = false
  val sample = previewState()
  val sleep = checkNotNull(sample.occurrence)
  val state = mutableStateOf(sample.copy(
   now = sleep.start.plusSeconds(60),
   activeSession = ActiveSession(sleep.key, sleep.start.toEpochMilli(), sleep.start.plusSeconds(600).toEpochMilli())
  ))
  compose.setContent {
   SleepTheme(sample.config.prefs) {
    BedtimeHero(state.value, {}, {}, {}, {}, {
     cancelled = true
     state.value = state.value.copy(activeSession = null)
    })
   }
  }
  compose.onNodeWithText(context.getString(R.string.cancel_snooze)).performClick()
  compose.runOnIdle { check(cancelled) }
  compose.onNodeWithText(context.getString(R.string.bedtime_overdue)).assertExists()
 }

 @Test fun sleepActionsAppearAtThirtyMinutes() {
  val state = mutableStateOf(beforeBedtime(31))
  compose.setContent {
   SleepTheme(state.value.config.prefs) { BedtimeHero(state.value, {}, {}, {}, {}, {}) }
  }
  val sleep = compose.onNodeWithText(context.getString(R.string.go_to_sleep))
  val snooze = compose.onNodeWithText(context.getString(R.string.snooze))
  sleep.assertDoesNotExist()
  snooze.assertDoesNotExist()

  compose.runOnIdle { state.value = beforeBedtime(30) }
  sleep.assertHasClickAction()
  snooze.assertHasClickAction()
 }

 @Test fun dayBadgeReflectsTodaysResolvedWork() {
  val sample = beforeBedtime(71)
  val state = mutableStateOf(sample.copy(today = sample.today.copy(workdays = 1)))
  compose.setContent {
   SleepTheme(state.value.config.prefs) { BedtimeHero(state.value, {}, {}, {}, {}, {}) }
  }
  compose.onNodeWithText(context.getString(R.string.work_day)).assertExists()
  compose.runOnIdle { state.value = sample.copy(today = sample.today.copy(workdays = 0)) }
  compose.onNodeWithText(context.getString(R.string.rest_day)).assertExists()
 }

 @Test fun persianCountdownUsesPersianDigitsAutomatically() {
  val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("fa")) })
  val sample = previewState()
  val s = sample.copy(
   config = sample.config.copy(prefs = sample.config.prefs.copy(language = "fa", persianNumerals = false)),
   now = checkNotNull(sample.occurrence).start.minusSeconds(71 * 60L)
  )
  compose.setContent {
   CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
    SleepTheme(s.config.prefs) { BedtimeHero(s, {}, {}, {}, {}, {}) }
   }
  }
  compose.onNodeWithText("۱ ساعت و ۱۱ دقیقه").assertExists()
 }

 @Test fun durationOmitsZeroMinutesInPersian() {
  val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("fa")) })
  val prefs = Preferences(language = "fa")
  assertEquals("۱۱۲ ساعت", duration(localized, 112 * 3600L, prefs))
  assertEquals("۱۱۲ ساعت و ۱۵ دقیقه", duration(localized, 112 * 3600L + 15 * 60L, prefs))
  assertEquals("۴۵ دقیقه", duration(localized, 45 * 60L, prefs))
 }
}
