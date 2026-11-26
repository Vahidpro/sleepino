package pasac.sleepino.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import pasac.sleepino.R
import pasac.sleepino.core.Preferences
import pasac.sleepino.core.ThemeMode
import org.junit.Rule
import org.junit.Test

class SettingsPresetsTest {
 @get:Rule val compose = createComposeRule()

 @Test fun themeAndPresetSelectionsUpdateImmediately() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val rose = context.getString(R.string.preset_rose)
  val violet = context.getString(R.string.violet)
  val amoled = context.getString(R.string.amoled)

  compose.setContent {
   var accent by remember { mutableStateOf(AccentPreset.VIOLET.id) }
   var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
   SleepTheme(Preferences(accent = accent, theme = theme)) {
    Column {
     ThemeSegmentedRow(theme) { theme = it }
     ThemePresets(accent) { accent = it.id }
    }
   }
  }

  compose.onNodeWithContentDescription(violet).assertIsSelected()
  compose.onNodeWithContentDescription(rose).performClick().assertIsSelected()
  compose.onNodeWithText(amoled).performClick().assertIsSelected()
 }

 @Test fun homeScreenDisplayTogglesDefaultOnAndUpdatePreferences() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  compose.setContent {
   var prefs by remember { mutableStateOf(Preferences()) }
   SleepTheme(prefs) {
    HomeScreenDisplaySection(prefs) { f -> prefs = f(prefs) }
   }
  }

  compose.onNodeWithText(context.getString(R.string.home_screen)).assertExists()
  compose.onNodeWithText(context.getString(R.string.show_year)).assertIsOn().performClick().assertIsOff()
  compose.onNodeWithText(context.getString(R.string.show_bedtime)).assertIsOn().performClick().assertIsOff()
  compose.onNodeWithText(context.getString(R.string.show_day_type)).assertIsOn().performClick().assertIsOff()
 }
}
