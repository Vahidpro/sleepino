package pasac.sleepino.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pasac.sleepino.R
import pasac.sleepino.core.*
@Composable fun Onboarding(s: DashboardState,vm: AppViewModel,onEditor: (String)->Unit,onBegin: ()->Unit) {
 val p=s.config.prefs
 val context=LocalContext.current
 val locale=LocalConfiguration.current.locales[0]
 val beginInteractions=remember { MutableInteractionSource() }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
  Spacer(Modifier.height(18.dp))
  Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.Bedtime,null,Modifier.padding(22.dp).size(38.dp),tint=MaterialTheme.colorScheme.primary)}
  Text(stringResource(R.string.welcome_title),style=MaterialTheme.typography.headlineLarge)
  Text(stringResource(R.string.welcome_body),color=MaterialTheme.colorScheme.onSurfaceVariant)
  Text(stringResource(R.string.onboarding_optional),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Panel(Modifier.fillMaxWidth()) {
   SettingsLink(R.string.sleep_schedule,sleepScheduleSummary(s.config,p,locale,context)){onEditor("sleep")}
   SettingsLink(R.string.work_schedule,activityScheduleSummary(s.config.work,p,locale,context)){onEditor("work")}
   SettingsLink(R.string.exercise_schedule,activityScheduleSummary(s.config.exercise,p,locale,context)){onEditor("exercise")}
  }
  ChoiceRow(R.string.language,p.language,listOf("system" to stringResource(R.string.system),"fa" to stringResource(R.string.persian),"en" to stringResource(R.string.english))){v->vm.update{it.copy(prefs=it.prefs.copy(language=v,jalali=if(v=="fa") true else it.prefs.jalali))}}
  ChoiceRow(R.string.holiday_region,p.region,listOf(HolidayRegion.IRAN to stringResource(R.string.iran),HolidayRegion.US to stringResource(R.string.united_states),HolidayRegion.NONE to stringResource(R.string.no_holidays))){v->vm.update{it.copy(prefs=it.prefs.copy(region=v))}}
  Button(onClick={vm.update{it.copy(prefs=it.prefs.copy(onboarded=true))};onBegin()},modifier=Modifier.fillMaxWidth().heightIn(min=58.dp).pressScale(beginInteractions),shape=RoundedCornerShape(18.dp),interactionSource=beginInteractions){Text(stringResource(R.string.begin))}
  Spacer(Modifier.height(10.dp))
 }
}
