package pasac.sleepino.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import pasac.sleepino.R
import pasac.sleepino.core.Preferences
import pasac.sleepino.core.ThemeMode
import org.junit.Rule
import org.junit.Test

class SettingsMotionTest {
 @get:Rule val compose = createComposeRule()

 @Test fun themeChoiceClosesMenuAndKeepsTheSelection() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val theme = context.getString(R.string.theme)
  val system = context.getString(R.string.system)
  val light = context.getString(R.string.light)
  compose.setContent {
   var selection by remember { mutableStateOf(ThemeMode.SYSTEM) }
   SleepTheme(Preferences()) {
    ChoiceRow(R.string.theme,selection,listOf(ThemeMode.SYSTEM to system,ThemeMode.LIGHT to light)) {
     selection = it
    }
   }
  }

  compose.onNodeWithText(theme).performClick()
  compose.onNode(isPopup()).assertExists()
  compose.onNode(hasText(system) and isSelected()).assertExists()
  compose.onNodeWithText(light).assertIsNotSelected().performClick()
  compose.onNode(isPopup()).assertDoesNotExist()
  compose.onNodeWithText(theme).assert(hasText(light)).performClick()
  compose.onNode(hasText(light) and isSelected()).assertExists()
 }
}
