package dev.sleeptime.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.sleeptime.R
import dev.sleeptime.core.Preferences
import org.junit.Rule
import org.junit.Test

class SettingsPresetsTest {
 @get:Rule val compose=createComposeRule()

 @Test fun presetCanBeSelectedWithAppearanceCollapsed() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val title=context.getString(R.string.appearance)
  val rose=context.getString(R.string.preset_rose)
  val violet=context.getString(R.string.violet)
  val advanced=context.getString(R.string.dynamic_colors)

  compose.setContent {
   var accent by remember {mutableStateOf(AccentPreset.VIOLET.id)}
   var expanded by remember {mutableStateOf("")}
   SleepTheme(Preferences(accent=accent)) {
    SettingsGroup("appearance",R.string.appearance,Icons.Rounded.Palette,expanded,{expanded=it},quick={
     ThemePresets(accent) {accent=it.id}
    }) {
     Text(advanced)
    }
   }
  }

  compose.onNodeWithContentDescription(violet).assertIsSelected()
  compose.onNodeWithText(advanced).assertDoesNotExist()
  compose.onNodeWithContentDescription(rose).performClick().assertIsSelected()
  compose.onNodeWithText(title).performClick()
  compose.onNodeWithText(advanced).assertExists()
  compose.onNodeWithContentDescription(rose).assertIsSelected()
 }
}
