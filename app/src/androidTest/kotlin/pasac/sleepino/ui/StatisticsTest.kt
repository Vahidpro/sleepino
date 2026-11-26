package pasac.sleepino.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import pasac.sleepino.R
import pasac.sleepino.core.*
import pasac.sleepino.data.BedtimeEntity
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class StatisticsTest {
 @get:Rule val compose = createComposeRule()
 private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

 @Test fun adherenceCardInspectsLoggedAndMissedDaysInlineWithoutDialog() {
  val prefs = Preferences(zoneId = "UTC", weekStart = 1)
  val monday = LocalDate.of(2026, 11, 16)
  val days = (0L..6L).map { monday.plusDays(it) }
  val config = Configuration(prefs = prefs, sleep = (1..7).map { SleepRule(it) })
  val scheduled = days.mapNotNull { TimeEngine.sleep(config, it) }
  val monStart = scheduled[0].start
  val tueStart = scheduled[1].start
  val records = listOf(
   BedtimeEntity(
    key = monday.toString(),
    ownerDate = monday.toString(),
    scheduled = monStart.toEpochMilli(),
    confirmed = monStart.plusSeconds(10 * 60L).toEpochMilli(),
    snoozes = 1
   ),
   BedtimeEntity(
    key = monday.plusDays(1).toString(),
    ownerDate = monday.plusDays(1).toString(),
    scheduled = tueStart.toEpochMilli(),
    confirmed = tueStart.minusSeconds(25 * 60L).toEpochMilli(),
    snoozes = 0
   )
  )
  val now = scheduled[3].start.plusSeconds(60) // Wednesday past bedtime -> Wed is missed, Thu..Sun upcoming
  val locale = context.resources.configuration.locales[0]

  compose.setContent {
   SleepTheme(prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
     AdherenceCard(
      days = days,
      records = records,
      scheduled = scheduled,
      onTime = 1,
      expectedCount = 4,
      rate = 25,
      averageDelayMinutes = -7L,
      now = now,
      p = prefs,
      locale = locale
     )
    }
   }
  }

  compose.onNodeWithText("25%").assertExists()
  compose.onNodeWithText("7 min early on avg").assertExists()
  compose.onNodeWithText("16").performClick()
  compose.onNodeWithText("Snoozed 1×", substring = true).assertExists()
  compose.onNodeWithText("18").performClick()
  compose.onNodeWithText("Missed", substring = true).assertExists()
 }

 @Test fun weeklyCardHidesZeroUnconfiguredCategoriesAndPastRemainingTime() {
  val prefs = Preferences(zoneId = "UTC", weekStart = 1)
  val monday = LocalDate.of(2026, 11, 16)
  val config = Configuration(prefs = prefs, sleep = (1..7).map { SleepRule(it) })
  val holidays = object : HolidayProvider {
   override fun status(date: LocalDate, region: HolidayRegion) = HolidayState.WORKDAY
   override fun getDataStatus(region: HolidayRegion, year: Int) = HolidayDataStatus(null, null, "test", true)
  }
  val now = Instant.parse("2026-11-25T12:00:00Z")
  val weekBudget = TimeEngine.budget(config, monday, 7, now, holidays)
  val days = (0L..6L).map { d -> monday.plusDays(d).let { it to TimeEngine.budget(config, it, 1, now, holidays) } }

  compose.setContent {
   SleepTheme(prefs) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
     WeeklyCard(budget = weekBudget, days = days, config = config, isCurrent = false)
    }
   }
  }

  compose.onNodeWithText(context.getString(R.string.sleep), substring = true).assertExists()
  compose.onNodeWithText(context.getString(R.string.work), substring = true).assertDoesNotExist()
  compose.onNodeWithText(context.getString(R.string.exercise), substring = true).assertDoesNotExist()
  compose.onNodeWithText("remaining", substring = true).assertDoesNotExist()
 }

 @Test fun jalaliWeekPickerDisplaysPersianMonthAndSelectsWeek() {
  val prefs = Preferences(language = "fa", jalali = true, weekStart = 6, zoneId = "Asia/Tehran")
  val date = LocalDate.of(2026, 11, 21) // 30 Aban 1405
  var picked: LocalDate? = null
  compose.setContent {
   SleepTheme(prefs) {
    ReportWeekDialog(
     first = date,
     currentWeek = date,
     p = prefs,
     locale = java.util.Locale.forLanguageTag("fa"),
     onSelect = { picked = it },
     onDismiss = {}
    )
   }
  }

  compose.onNodeWithText("آبان ۱۴۰۵", substring = true).assertExists()
  compose.onNodeWithText("۱").performClick()
  compose.onNodeWithText(context.getString(R.string.done)).performClick()
  compose.runOnIdle {
   val result = checkNotNull(picked)
   check(result.dayOfWeek.value == 6)
  }
 }
}
