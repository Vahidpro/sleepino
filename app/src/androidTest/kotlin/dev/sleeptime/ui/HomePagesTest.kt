package dev.sleeptime.ui

import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
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

 @Test fun swipingChangesPageAndIndicator() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  compose.setContent {
   CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    SleepTheme(Preferences()) {
     HomePages({}, { Text("Home sample") }, { Text("Reports sample") })
    }
   }
  }

  compose.onNodeWithContentDescription(context.getString(R.string.dashboard)).assertExists()
  compose.onRoot().performTouchInput { swipeLeft(startX = width * .84f, endX = width * .16f) }
  compose.onNodeWithText("Reports sample").assertIsDisplayed()
  compose.onNodeWithContentDescription(context.getString(R.string.statistics)).assertExists()
  compose.onRoot().performTouchInput { swipeRight(startX = width * .16f, endX = width * .84f) }
  compose.onNodeWithText("Home sample").assertIsDisplayed()
 }

 @Test fun downwardSwipeOpensSettingsOnlyWhenItStartsAtTop() {
  var settingsOpens = 0
  compose.setContent {
   SleepTheme(Preferences()) {
    HomePages({ settingsOpens++ }, { Text("Home sample") }, { Text("Reports sample") })
   }
  }

  compose.onRoot().performTouchInput {
   swipe(Offset(centerX, height * .48f), Offset(centerX, height * .80f))
  }
  compose.runOnIdle { check(settingsOpens == 0) }
  compose.onRoot().performTouchInput {
   swipe(Offset(centerX, height * .025f), Offset(centerX, height * .40f))
  }
  compose.runOnIdle { check(settingsOpens == 1) }
 }

 @Test fun tappingMainPageTogglesSettingsIcon() {
  var settingsOpens = 0
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
     onSettings = { settingsOpens++ }
    )
   }
  }

  val settingsIcon = compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings))
  settingsIcon.assertDoesNotExist()

  compose.onRoot().performClick()
  settingsIcon.assertIsDisplayed()

  compose.onRoot().performClick()
  settingsIcon.assertDoesNotExist()

  compose.onRoot().performClick()
  settingsIcon.assertIsDisplayed()
  settingsIcon.performClick()
  compose.runOnIdle { check(settingsOpens == 1) }
 }
}
