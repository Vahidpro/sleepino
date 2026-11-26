@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.sleeptime.ui

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sleeptime.R
import dev.sleeptime.core.*
import dev.sleeptime.platform.*
import java.time.*
import java.time.format.TextStyle

@Composable fun PageHeader(title: Int,onBack: ()->Unit) {
 Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick=onBack){AppIcon(Icons.AutoMirrored.Rounded.ArrowBack,R.string.back)};Spacer(Modifier.width(8.dp));Text(stringResource(title),style=MaterialTheme.typography.headlineMedium) }
}
@Composable fun SettingsScreen(s: DashboardState,vm: AppViewModel,onBack: ()->Unit,onEditor: (String)->Unit) {
 val context=LocalContext.current;val p=s.config.prefs;val locale=LocalConfiguration.current.locales[0]
 var expanded by rememberSaveable {mutableStateOf("appearance")}
 fun prefs(f: (Preferences)->Preferences){vm.update {it.copy(prefs=f(it.prefs))}}
 fun open(intent: Intent) {runCatching {context.startActivity(intent)}.onFailure {vm.message.tryEmit(R.string.error)}}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)}
 val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::readBackup)}
 val holidayImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importHolidays)}
 val noticePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.resume()}
 val sound=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};prefs {it.copy(soundUri=uri.toString())}}}
 var reset by remember {mutableStateOf(false)}
 val backup by vm.pendingBackup.collectAsState()
 LazyColumn(Modifier.fillMaxSize().widthIn(max=800.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {PageHeader(R.string.settings,onBack)}
  item {SettingsGroup("sleep",R.string.sleep_settings,Icons.Rounded.Bedtime,expanded,{expanded=it}) {
   SettingsLink(R.string.sleep_schedule){onEditor("sleep")}
   SettingsLink(R.string.reminder_stages){onEditor("stages")}
   SettingsLink(R.string.manage_routine){onEditor("routine")}
   SwitchRow(R.string.notifications,p.notifications,{v->prefs{it.copy(notifications=v)}})
   SwitchRow(R.string.smart_reminders,p.smartReminders,{v->prefs{it.copy(smartReminders=v)}},R.string.smart_explanation)
   SwitchRow(R.string.minimum_reminder,p.minimumScheduledReminder,{v->prefs{it.copy(minimumScheduledReminder=v)}})
   if(p.smartReminders) NumberInput(R.string.inactivity,p.inactivityMinutes,1..180){v->prefs{it.copy(inactivityMinutes=v)}}
   NumberInput(R.string.repeat_interval,p.repeatMinutes,5..180){v->prefs{it.copy(repeatMinutes=v)}}
   NumberInput(R.string.repeat_count,p.maxRepeats,0..20){v->prefs{it.copy(maxRepeats=v)}}
   NumberInput(R.string.snooze_default,p.snoozeMinutes,1..180){v->prefs{it.copy(snoozeMinutes=v)}}
   NumberInput(R.string.snooze_max,p.maxSnoozes,0..20){v->prefs{it.copy(maxSnoozes=v)}}
   SwitchRow(R.string.dnd,p.dnd,{v->if(v)vm.controller.dnd.enableNewRule();prefs{it.copy(dnd=v)}},R.string.dnd_explanation)
   if(p.dnd) {
    NumberInput(R.string.dnd_start,p.dndStartOffset,-720..720){v->prefs{it.copy(dndStartOffset=v)}}
    TimeButton(R.string.dnd_end,p.dndEndMinutes,p){v->prefs{it.copy(dndEndMinutes=v)}}
    SwitchRow(R.string.dnd_snooze,p.dndFollowsSnooze,{v->prefs{it.copy(dndFollowsSnooze=v)}})
   }
  }}
  item {SettingsGroup("time",R.string.time_settings,Icons.Rounded.Schedule,expanded,{expanded=it}) {
   SettingsLink(R.string.work_schedule){onEditor("work")};SettingsLink(R.string.exercise_schedule){onEditor("exercise")};SettingsLink(R.string.overrides){onEditor("overrides")}
   ChoiceRow(R.string.week_start,p.weekStart,(1..7).map {it to DayOfWeek.of(it).getDisplayName(TextStyle.FULL,locale)}){v->prefs{it.copy(weekStart=v)}}
   ChoiceRow(R.string.holiday_region,p.region,listOf(HolidayRegion.IRAN to stringResource(R.string.iran),HolidayRegion.US to stringResource(R.string.united_states),HolidayRegion.NONE to stringResource(R.string.no_holidays))){v->prefs{it.copy(region=v)}}
   var zone by remember(p.zoneId){mutableStateOf(p.zoneId)}
   OutlinedTextField(zone,{v->zone=v},label={Text(stringResource(R.string.timezone))},supportingText={Text(stringResource(R.string.timezone_hint))},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
   TextButton(onClick={if(zone=="system"||runCatching {ZoneId.of(zone)}.isSuccess)prefs{it.copy(zoneId=zone)}else vm.message.tryEmit(R.string.invalid_input)}){Text(stringResource(R.string.save))}
   val status=vm.holidays.getDataStatus(p.region,s.now.atZone(p.zone()).year)
   Text(stringResource(R.string.holiday_data),style=MaterialTheme.typography.titleMedium)
   Text(if(status.validFrom!=null && status.validTo!=null && status.reliable)stringResource(R.string.holiday_coverage,dateText(status.validFrom!!,p,locale,true)+" — "+dateText(status.validTo!!,p,locale,true))else stringResource(R.string.no_coverage),style=MaterialTheme.typography.bodySmall)
  }}
  item {SettingsGroup("appearance",R.string.appearance,Icons.Rounded.Palette,expanded,{expanded=it}) {
   ChoiceRow(R.string.theme,p.theme,listOf(ThemeMode.SYSTEM to stringResource(R.string.system),ThemeMode.LIGHT to stringResource(R.string.light),ThemeMode.DARK to stringResource(R.string.dark),ThemeMode.AMOLED to stringResource(R.string.amoled))){v->prefs{it.copy(theme=v)}}
   var customAccent by remember(p.accent){mutableStateOf(if(p.accent.startsWith("#"))p.accent else "#B8A5FF")}
   OutlinedTextField(customAccent,{v->if(v.length<=7)customAccent=v},label={Text(stringResource(R.string.accent))},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
   TextButton(onClick={if(customAccent.matches(Regex("#[0-9a-fA-F]{6}")))prefs{it.copy(accent=customAccent)}else vm.message.tryEmit(R.string.invalid_input)}){Text(stringResource(R.string.save))}
   SwitchRow(R.string.dynamic_colors,p.dynamicColor,{v->prefs{it.copy(dynamicColor=v)}})
   ChoiceRow(R.string.accent,p.accent,listOf("violet" to stringResource(R.string.violet),"teal" to stringResource(R.string.teal),"blue" to stringResource(R.string.blue))){v->prefs{it.copy(accent=v)}}
   ChoiceRow(R.string.language,p.language,listOf("system" to stringResource(R.string.system),"fa" to stringResource(R.string.persian),"en" to stringResource(R.string.english))){v->prefs{it.copy(language=v)}}
   SwitchRow(R.string.persian_numerals,p.persianNumerals,{v->prefs{it.copy(persianNumerals=v)}})
   SwitchRow(R.string.date_format,p.jalali,{v->prefs{it.copy(jalali=v)}})
   SwitchRow(R.string.clock_24,p.clock24,{v->prefs{it.copy(clock24=v)}})
   SwitchRow(R.string.reduce_motion,p.reduceMotion,{v->prefs{it.copy(reduceMotion=v)}})
  }}
  item {SettingsGroup("access",R.string.permissions,Icons.Rounded.NotificationsNone,expanded,{expanded=it}) {
   Text(stringResource(R.string.permission_explanation),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   SettingsLink(R.string.notification_access,if(Notifications(context).allowed())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)) {if(Build.VERSION.SDK_INT>=33)noticePermission.launch(Manifest.permission.POST_NOTIFICATIONS)else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
   SwitchRow(R.string.exact_alarms,p.preciseAlarms,{v->prefs{it.copy(preciseAlarms=v)}},R.string.exact_explanation)
   if(p.preciseAlarms && Build.VERSION.SDK_INT>=31) SettingsLink(R.string.exact_alarms,if(vm.controller.canBePrecise())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)){open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}
   if(p.smartReminders || p.distractionPackages.isNotEmpty()) SettingsLink(R.string.usage_access,if(DeviceActivity(context).hasAccess())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)){open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}
   if(p.dnd) SettingsLink(R.string.dnd_access,if(vm.controller.dnd.allowed())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)){open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}
   SettingsLink(R.string.battery){open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
   SwitchRow(R.string.silent,p.silent,{v->prefs{it.copy(silent=v)}})
   SwitchRow(R.string.vibration,p.vibration,{v->prefs{it.copy(vibration=v)}})
   SettingsLink(R.string.sound,if(p.soundUri=="default")stringResource(R.string.sound_default)else stringResource(R.string.sound_custom)){sound.launch(arrayOf("audio/*"))}
   TextButton(onClick={prefs{it.copy(soundUri="default")}}){Text(stringResource(R.string.sound_default))}
   SettingsLink(R.string.channel_settings){open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
   Text(stringResource(R.string.distractions),style=MaterialTheme.typography.titleMedium)
   Text(stringResource(R.string.distraction_hint),style=MaterialTheme.typography.bodySmall)
   DistractionApps(p){packages->prefs{it.copy(distractionPackages=packages)}}
   SettingsLink(R.string.digital_wellbeing){val i=Intent("android.settings.WELLBEING_SETTINGS");if(i.resolveActivity(context.packageManager)!=null)open(i)else open(Intent(Settings.ACTION_SETTINGS))}
  }}
  item {SettingsGroup("data",R.string.data,Icons.Rounded.FolderOpen,expanded,{expanded=it}) {
   Text(stringResource(R.string.backup_hint),style=MaterialTheme.typography.bodySmall)
   SettingsLink(R.string.export_backup){export.launch("sleepino-backup-${LocalDate.now()}.json")}
   SettingsLink(R.string.import_backup){restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
   SettingsLink(R.string.import_holidays){holidayImport.launch(arrayOf("application/json","text/plain"))}
   TextButton(onClick={reset=true}){Text(stringResource(R.string.reset),color=MaterialTheme.colorScheme.error)}
  }}
  item {SettingsGroup("about",R.string.about,Icons.Rounded.Info,expanded,{expanded=it}) {Text(stringResource(R.string.app_name)+" · "+dev.sleeptime.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.privacy_body),style=MaterialTheme.typography.bodySmall)}}
  item {Spacer(Modifier.height(30.dp))}
 }
 if(backup!=null) ConfirmDialog(R.string.restore_title,R.string.restore_body,{vm.restore()},{vm.pendingBackup.value=null})
 if(reset) ConfirmDialog(R.string.reset,R.string.reset_body,{vm.reset();reset=false},{reset=false})
}
@Composable private fun SettingsGroup(key: String,title: Int,icon: androidx.compose.ui.graphics.vector.ImageVector,expanded: String,onExpand: (String)->Unit,content: @Composable ColumnScope.()->Unit) {
 Panel(Modifier.fillMaxWidth()) {
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {onExpand(if(expanded==key)""else key)}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) { AppIcon(icon,null,MaterialTheme.colorScheme.primary);Spacer(Modifier.width(14.dp));Text(stringResource(title),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);AppIcon(if(expanded==key)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null) }
  if(expanded==key) Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
 }
}
@Composable fun SettingsLink(title: Int,subtitle: String?=null,onClick: ()->Unit) {
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)){Text(stringResource(title),style=MaterialTheme.typography.bodyLarge);subtitle?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null)
 }
}
@Composable fun <T> ChoiceRow(title: Int,value: T,choices: List<Pair<T,String>>,onChange: (T)->Unit) {
 var open by remember{mutableStateOf(false)}
 Box {
  SettingsLink(title,choices.firstOrNull {it.first==value}?.second){open=true}
  DropdownMenu(expanded=open,onDismissRequest={open=false}) {for((v,label)in choices) DropdownMenuItem(text={Text(label)},onClick={onChange(v);open=false},trailingIcon=if(v==value){{AppIcon(Icons.Rounded.Check,null)}}else null)}
 }
}
@Composable private fun DistractionApps(p: Preferences,onChange: (Set<String>)->Unit) {
 val context=LocalContext.current
 var expanded by remember {mutableStateOf(false)}
 val apps by produceState<List<Pair<String,String>>>(emptyList()) {
  value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
   context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).filter {it.activityInfo.packageName!=context.packageName}.map {it.activityInfo.packageName to it.loadLabel(context.packageManager).toString()}.distinctBy {it.first}.sortedBy {it.second.lowercase()}
  }
 }
 TextButton(onClick={expanded=!expanded}){Text(stringResource(R.string.distractions)+" · "+number(p.distractionPackages.size,p))}
 if(expanded) Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState())) {
  if(apps.isEmpty())Text(stringResource(R.string.no_apps))
  for((pkg,label)in apps) Row(Modifier.fillMaxWidth().clickable {onChange(if(pkg in p.distractionPackages)p.distractionPackages-pkg else p.distractionPackages+pkg)},verticalAlignment=Alignment.CenterVertically){Checkbox(pkg in p.distractionPackages,{v->onChange(if(v)p.distractionPackages+pkg else p.distractionPackages-pkg)});Text(label)}
 }
}
@Composable fun ConfirmDialog(title: Int,body: Int,confirm: ()->Unit,dismiss: ()->Unit) {
 AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(title))},text={Text(stringResource(body))},confirmButton={TextButton(onClick=confirm){Text(stringResource(R.string.done))}},dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.cancel))}})
}
