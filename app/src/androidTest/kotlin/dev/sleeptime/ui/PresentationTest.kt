package dev.sleeptime.ui
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.sleeptime.R
import dev.sleeptime.core.ThemeMode
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PresentationTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun sleepConfirmationActionIsReachable(){
  var confirmed=false;val s=previewState()
  compose.setContent{SleepTheme(s.config.prefs){BedtimeHero(s,{confirmed=true},{},{})}}
  compose.onNodeWithText(context.getString(R.string.go_to_sleep)).assertExists().performClick()
  compose.runOnIdle{check(confirmed)}
 }
 @Test fun emptySleepScheduleExplainsNextAction(){
  val s=previewState().copy(occurrence=null)
  compose.setContent{SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{})}}
  compose.onNodeWithText(context.getString(R.string.setup_sleep)).assertExists()
 }
 @Test fun persianAmoledAndLargeFontsKeepPrimaryAction(){
  val localized=context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag("fa"))})
  val s=previewState().let{it.copy(config=it.config.copy(prefs=it.config.prefs.copy(theme=ThemeMode.AMOLED,persianNumerals=true)))}
  compose.setContent{CompositionLocalProvider(LocalContext provides localized,LocalDensity provides Density(context.resources.displayMetrics.density,1.8f)){SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{})}}}
  compose.onNodeWithText(localized.getString(R.string.go_to_sleep)).assertExists()
 }
 @Test fun confirmationShowsReportedState(){val s=previewState().copy(confirmed=true);compose.setContent{SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{})}};compose.onNodeWithText(context.getString(R.string.rest_well)).assertExists()}
}
