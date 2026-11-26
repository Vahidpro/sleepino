package dev.sleeptime.ui

import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import dev.sleeptime.AppLanguageResources
import dev.sleeptime.HomePages
import dev.sleeptime.R
import dev.sleeptime.core.Preferences
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class HomePagesTest {
 @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

 @Before fun keepTestActivityVisible() {
  val activity = compose.activity
  compose.runOnUiThread {
   if (Build.VERSION.SDK_INT >= 27) {
    activity.setShowWhenLocked(true)
    activity.setTurnScreenOn(true)
   }
   activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
 }

 @Test fun persianReportsTitleUsesTheSelectedLanguageBeforeActivityRecreation() {
  compose.setContent {
   val english = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
   CompositionLocalProvider(LocalConfiguration provides english) {
    AppLanguageResources("fa") {
     Text(stringResource(R.string.statistics))
    }
   }
  }
  compose.onNodeWithText("گزارش و آمار").assertIsDisplayed()
 }

 @Test fun swipingAndCornerButtonsNavigateBetweenHomeAndReports() {
  compose.setContent {
   CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    SleepTheme(Preferences()) {
     HomePages(
      dashboard = { onReports ->
       Button(onClick = onReports) { Text("Go to reports") }
      },
      reports = { onBack ->
       Button(onClick = onBack) { Text("Reports sample") }
      }
     )
    }
   }
  }

  compose.onNodeWithText("Go to reports").performClick()
  compose.onNodeWithText("Reports sample").assertIsDisplayed()

  compose.onNodeWithText("Reports sample").performClick()
  compose.onNodeWithText("Go to reports").assertIsDisplayed()

  compose.onRoot().performTouchInput { swipeLeft(startX = width * .84f, endX = width * .16f) }
  compose.onNodeWithText("Reports sample").assertIsDisplayed()

  compose.onRoot().performTouchInput { swipeRight(startX = width * .16f, endX = width * .84f) }
  compose.onNodeWithText("Go to reports").assertIsDisplayed()
 }

 @Test fun reportsAndSettingsCornerButtonsAreImmediatelyVisibleAndClickable() {
  var settingsOpens = 0
  var reportsOpens = 0
  val sample = previewState()
  compose.setContent {
   SleepTheme(sample.config.prefs) {
    BedtimeHero(
     s = sample,
     onConfirm = {},
     onSetup = {},
     onSnooze = {},
     onUndo = {},
     onCancelSnooze = {},
     onSettings = { settingsOpens++ },
     onReports = { reportsOpens++ }
    )
   }
  }

  val reportsIcon = compose.onNodeWithContentDescription(compose.activity.getString(R.string.statistics))
  val settingsIcon = compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings))

  reportsIcon.assertIsDisplayed().performClick()
  settingsIcon.assertIsDisplayed().performClick()

  compose.runOnIdle {
   check(reportsOpens == 1)
   check(settingsOpens == 1)
  }
 }
}
