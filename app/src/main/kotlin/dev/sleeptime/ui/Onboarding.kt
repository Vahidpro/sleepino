package dev.sleeptime.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sleeptime.R
import dev.sleeptime.core.*
@Composable fun Onboarding(s: DashboardState,vm: AppViewModel,onEditor: (String)->Unit,onBegin: ()->Unit) {
 val p=s.config.prefs
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
  Spacer(Modifier.height(18.dp))
  Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.Bedtime,null,Modifier.padding(22.dp).size(38.dp),tint=MaterialTheme.colorScheme.primary)}
  Text(stringResource(R.string.welcome_title),style=MaterialTheme.typography.headlineLarge)
  Text(stringResource(R.string.welcome_body),color=MaterialTheme.colorScheme.onSurfaceVariant)
  Text(stringResource(R.string.onboarding_optional),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Panel(Modifier.fillMaxWidth()) {
   SettingsLink(R.string.sleep_schedule,stringResource(if(s.config.sleep.any{it.enabled})R.string.configured else R.string.not_configured)){onEditor("sleep")}
   SettingsLink(R.string.work_schedule,stringResource(if(s.config.work.any{it.slots.isNotEmpty()})R.string.configured else R.string.not_configured)){onEditor("work")}
   SettingsLink(R.string.exercise_schedule,stringResource(if(s.config.exercise.any{it.slots.isNotEmpty()})R.string.configured else R.string.not_configured)){onEditor("exercise")}
  }
  ChoiceRow(R.string.language,p.language,listOf("system" to stringResource(R.string.system),"fa" to stringResource(R.string.persian),"en" to stringResource(R.string.english))){v->vm.update{it.copy(prefs=it.prefs.copy(language=v))}}
  ChoiceRow(R.string.holiday_region,p.region,listOf(HolidayRegion.IRAN to stringResource(R.string.iran),HolidayRegion.US to stringResource(R.string.united_states),HolidayRegion.NONE to stringResource(R.string.no_holidays))){v->vm.update{it.copy(prefs=it.prefs.copy(region=v))}}
  Button(onClick={vm.update{it.copy(prefs=it.prefs.copy(onboarded=true))};onBegin()},modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),shape=RoundedCornerShape(18.dp)){Text(stringResource(R.string.begin))}
  Spacer(Modifier.height(10.dp))
 }
}
