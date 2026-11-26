@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.sleeptime.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.sleeptime.R
import dev.sleeptime.core.*
import dev.sleeptime.platform.*
import java.time.*
import java.time.format.TextStyle as DayNameStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun PageHeader(title: Int,onBack: ()->Unit) {
 Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick=onBack){AppIcon(Icons.AutoMirrored.Rounded.ArrowBack,R.string.back)};Spacer(Modifier.width(8.dp));Text(stringResource(title),style=MaterialTheme.typography.headlineMedium) }
}
@Composable fun SettingsScreen(s: DashboardState,vm: AppViewModel,onBack: ()->Unit,onEditor: (String)->Unit) {
 val context=LocalContext.current;val p=s.config.prefs;val locale=LocalConfiguration.current.locales[0]
 val localizedDigits=remember(locale) {
  VisualTransformation { TransformedText(AnnotatedString(number(it.text,locale)),OffsetMapping.Identity) }
 }
 var expanded by rememberSaveable {mutableStateOf("")}
 var checkPermission by remember {mutableIntStateOf(0)}
 LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checkPermission++ }
 fun prefs(f: (Preferences)->Preferences){vm.update {it.copy(prefs=f(it.prefs))}}
 fun open(intent: Intent) {runCatching {context.startActivity(intent)}.onFailure {vm.message.tryEmit(R.string.error)}}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)}
 val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::readBackup)}
 val holidayImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importHolidays)}
 val noticePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
  checkPermission++
  vm.resume()
 }
 val sound=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};prefs {it.copy(soundUri=uri.toString())}}}
 var reset by remember {mutableStateOf(false)}
 val backup by vm.pendingBackup.collectAsState()
 LazyColumn(Modifier.fillMaxSize().widthIn(max=800.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item(key="header", contentType="header") { PageHeader(R.string.settings,onBack) }
  item(key="sleep", contentType="group") {
   SettingsGroup("sleep",R.string.sleep_settings,Icons.Rounded.Bedtime,expanded,{expanded=it},quick={
    QuickActions {
     AssistChip(onClick={onEditor("sleep")},label={Text(stringResource(R.string.sleep_schedule))})
     FilterChip(
      selected=p.notifications,
      onClick={prefs{it.copy(notifications=!it.notifications)}},
      label={Text(stringResource(R.string.notifications))},
      leadingIcon={AppIcon(Icons.Rounded.NotificationsNone,null,modifier=Modifier.size(18.dp))}
     )
    }
   }) {
    SettingsLink(R.string.reminder_stages){onEditor("stages")}
    SettingsLink(R.string.manage_routine){onEditor("routine")}
    SwitchRow(R.string.base_sleep,p.baseSleep,{v->prefs{it.copy(baseSleep=v)}},R.string.base_sleep_explanation)
    SettingVisibility(p.baseSleep) {
     FlowRow(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      TimeButton(R.string.base_bedtime,p.baseBedtime,p){v->prefs{it.copy(baseBedtime=v)}}
      TimeButton(R.string.base_wake,p.baseWake,p){v->prefs{it.copy(baseWake=v)}}
     }
    }
    SwitchRow(R.string.smart_reminders,p.smartReminders,{v->prefs{it.copy(smartReminders=v)}},R.string.smart_explanation)
    SwitchRow(R.string.minimum_reminder,p.minimumScheduledReminder,{v->prefs{it.copy(minimumScheduledReminder=v)}})
    SettingVisibility(p.smartReminders) {
     NumberInput(R.string.inactivity,p.inactivityMinutes,1..180){v->prefs{it.copy(inactivityMinutes=v)}}
    }
    NumberInput(R.string.repeat_interval,p.repeatMinutes,5..180){v->prefs{it.copy(repeatMinutes=v)}}
    NumberInput(R.string.repeat_count,p.maxRepeats,0..20){v->prefs{it.copy(maxRepeats=v)}}
    NumberInput(R.string.snooze_default,p.snoozeMinutes,1..180){v->prefs{it.copy(snoozeMinutes=v)}}
    NumberInput(R.string.snooze_max,p.maxSnoozes,0..20){v->prefs{it.copy(maxSnoozes=v)}}
    SwitchRow(R.string.dnd,p.dnd,{v->if(v)vm.controller.dnd.enableNewRule();prefs{it.copy(dnd=v)}},R.string.dnd_explanation)
    SettingVisibility(p.dnd) {
     Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
      NumberInput(R.string.dnd_start,p.dndStartOffset,-720..720){v->prefs{it.copy(dndStartOffset=v)}}
      TimeButton(R.string.dnd_end,p.dndEndMinutes,p){v->prefs{it.copy(dndEndMinutes=v)}}
      SwitchRow(R.string.dnd_snooze,p.dndFollowsSnooze,{v->prefs{it.copy(dndFollowsSnooze=v)}})
     }
    }
   }
  }
  item(key="time", contentType="group") {
   SettingsGroup("time",R.string.time_settings,Icons.Rounded.Schedule,expanded,{expanded=it},quick={
    QuickActions {
     AssistChip(onClick={onEditor("work")},label={Text(stringResource(R.string.work_schedule))})
     AssistChip(onClick={onEditor("exercise")},label={Text(stringResource(R.string.exercise_schedule))})
    }
   }) {
    SettingsLink(R.string.overrides){onEditor("overrides")}
    SwitchRow(R.string.count_work_remaining,p.countWorkRemaining,{v->prefs{it.copy(countWorkRemaining=v)}},R.string.count_work_remaining_explanation)
    ChoiceRow(R.string.week_start,p.weekStart,(1..7).map {it to DayOfWeek.of(it).getDisplayName(DayNameStyle.FULL,locale)}){v->prefs{it.copy(weekStart=v)}}
    ChoiceRow(R.string.holiday_region,p.region,listOf(HolidayRegion.IRAN to stringResource(R.string.iran),HolidayRegion.US to stringResource(R.string.united_states),HolidayRegion.NONE to stringResource(R.string.no_holidays))){v->prefs{it.copy(region=v)}}
    var zone by remember(p.zoneId){mutableStateOf(p.zoneId)}
    OutlinedTextField(
     zone,{zone=it.asciiDigits()},
     label={Text(stringResource(R.string.timezone))},
     supportingText={Text(stringResource(R.string.timezone_hint))},
     singleLine=true,visualTransformation=localizedDigits,
     shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()
    )
    TextButton(onClick={
     if(zone=="system" || runCatching {ZoneId.of(zone)}.isSuccess) prefs{it.copy(zoneId=zone)}
     else vm.message.tryEmit(R.string.invalid_input)
    }) {Text(stringResource(R.string.save))}
    val year=s.now.atZone(p.zone()).year
    key(p.region,year) {
     val status by produceState<HolidayDataStatus?>(null, vm.holidays, s.holidayRevision) {
      value=withContext(Dispatchers.IO) {vm.holidays.getDataStatus(p.region,year)}
     }
     Text(stringResource(R.string.holiday_data),style=MaterialTheme.typography.titleMedium)
     status?.let { coverage ->
      val from=coverage.validFrom
      val to=coverage.validTo
      SettingValue(if(from!=null && to!=null && coverage.reliable) stringResource(
       R.string.holiday_coverage,dateText(from,p,locale,true)+" – "+dateText(to,p,locale,true)
      ) else stringResource(R.string.no_coverage))
     }
    }
   }
  }
  item(key="appearance", contentType="group") {
   SettingsGroup("appearance",R.string.appearance,Icons.Rounded.Palette,expanded,{expanded=it},quick={
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     ChoiceRow(R.string.theme,p.theme,listOf(
      ThemeMode.SYSTEM to stringResource(R.string.system),
      ThemeMode.LIGHT to stringResource(R.string.light),
      ThemeMode.DARK to stringResource(R.string.dark),
      ThemeMode.AMOLED to stringResource(R.string.amoled)
     ),Modifier.weight(1f)){v->prefs{it.copy(theme=v)}}
     ChoiceRow(R.string.language,p.language,listOf(
      "system" to stringResource(R.string.system),
      "fa" to stringResource(R.string.persian),
      "en" to stringResource(R.string.english)
     ),Modifier.weight(1f)){v->prefs{it.copy(language=v,jalali=if(v=="fa") true else it.jalali,persianNumerals=if(v=="fa") true else it.persianNumerals)}}
    }
    ThemePresets(p.accent.takeUnless {p.dynamicColor && Build.VERSION.SDK_INT>=31}) { selected ->
     prefs{it.copy(accent=selected.id,dynamicColor=false)}
    }
   }) {
    if(Build.VERSION.SDK_INT>=31) SwitchRow(R.string.dynamic_colors,p.dynamicColor,{v->prefs{it.copy(dynamicColor=v)}})
    var customAccent by rememberSaveable(p.accent){mutableStateOf(if(p.accent.startsWith("#"))p.accent else "#B8A5FF")}
    OutlinedTextField(
     customAccent,{if(it.length<=7)customAccent=it.asciiDigits()},
     label={Text(stringResource(R.string.accent))},
     singleLine=true,visualTransformation=localizedDigits,
     shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()
    )
    TextButton(onClick={
     if(customAccent.matches(Regex("#[0-9a-fA-F]{6}"))) prefs{it.copy(accent=customAccent,dynamicColor=false)}
     else vm.message.tryEmit(R.string.invalid_input)
    }) {Text(stringResource(R.string.save))}
    SwitchRow(R.string.date_format,p.jalali,{v->prefs{it.copy(jalali=v)}})
    SwitchRow(R.string.clock_24,p.clock24,{v->prefs{it.copy(clock24=v)}})
    SwitchRow(R.string.reduce_motion,p.reduceMotion,{v->prefs{it.copy(reduceMotion=v)}})
   }
  }
  item(key="access", contentType="group") {
   SettingsGroup("access",R.string.permissions,Icons.Rounded.NotificationsNone,expanded,{expanded=it},quick={
    val allowed by produceState<Boolean?>(null,context,checkPermission) {
     value=withContext(Dispatchers.IO) { Notifications(context).allowed() }
    }
    val status=allowed?.let { stringResource(if(it) R.string.allowed else R.string.not_allowed) }
    SettingsLink(R.string.notification_access,status) {
     if(Build.VERSION.SDK_INT>=33 &&
      ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
     ) noticePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
     else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    }
   }) {
    Text(stringResource(R.string.permission_explanation),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    SwitchRow(R.string.exact_alarms,p.preciseAlarms,{v->prefs{it.copy(preciseAlarms=v)}},R.string.exact_explanation)
    if(Build.VERSION.SDK_INT>=31) SettingVisibility(p.preciseAlarms) {
     val status=stringResource(if(vm.controller.canBePrecise())R.string.allowed else R.string.not_allowed)
     SettingsLink(R.string.exact_alarms,status) {
      open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,"package:${context.packageName}".toUri()))
     }
    }
    SettingVisibility(p.smartReminders || p.distractionPackages.isNotEmpty()) {
     SettingsLink(R.string.usage_access,if(DeviceActivity(context).hasAccess())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)){open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}
    }
    SettingVisibility(p.dnd) {
     SettingsLink(R.string.dnd_access,if(vm.controller.dnd.allowed())stringResource(R.string.allowed)else stringResource(R.string.not_allowed)){open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}
    }
    SettingsLink(R.string.battery){open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}
    SwitchRow(R.string.silent,p.silent,{v->prefs{it.copy(silent=v)}})
    SwitchRow(R.string.vibration,p.vibration,{v->prefs{it.copy(vibration=v)}})
    SettingsLink(R.string.sound,if(p.soundUri=="default")stringResource(R.string.sound_default)else stringResource(R.string.sound_custom)){sound.launch(arrayOf("audio/*"))}
    TextButton(onClick={prefs{it.copy(soundUri="default")}}){Text(stringResource(R.string.sound_default))}
    SettingsLink(R.string.channel_settings){open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
    Text(stringResource(R.string.distractions),style=MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.distraction_hint),style=MaterialTheme.typography.bodySmall)
    DistractionApps(p){packages->prefs{it.copy(distractionPackages=packages)}}
    SettingsLink(R.string.digital_wellbeing){val i=Intent("android.settings.WELLBEING_SETTINGS");if(i.resolveActivity(context.packageManager)!=null)open(i)else open(Intent(Settings.ACTION_SETTINGS))}
   }
  }
  item(key="data", contentType="group") {
   SettingsGroup("data",R.string.data,Icons.Rounded.FolderOpen,expanded,{expanded=it},quick={
    QuickActions {
     AssistChip(onClick={export.launch("sleepino-backup-${LocalDate.now()}.json")},label={Text(stringResource(R.string.export_backup))})
     AssistChip(onClick={restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))},label={Text(stringResource(R.string.import_backup))})
    }
   }) {
    Text(stringResource(R.string.backup_hint),style=MaterialTheme.typography.bodySmall)
    SettingsLink(R.string.import_holidays){holidayImport.launch(arrayOf("application/json","text/plain"))}
    TextButton(onClick={reset=true}){Text(stringResource(R.string.reset),color=MaterialTheme.colorScheme.error)}
   }
  }
  item(key="about", contentType="group") {
   SettingsGroup("about",R.string.about,Icons.Rounded.Info,expanded,{expanded=it},quick={
    val version=number(dev.sleeptime.BuildConfig.VERSION_NAME,p,locale)
    Text(stringResource(R.string.app_name)+" · "+version,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }) {
    Text(stringResource(R.string.privacy_body),style=MaterialTheme.typography.bodySmall)
   }
  }
 }
 if(backup!=null) ConfirmDialog(R.string.restore_title,R.string.restore_body,{vm.restore()},{vm.pendingBackup.value=null})
 if(reset) ConfirmDialog(R.string.reset,R.string.reset_body,{vm.reset();reset=false},{reset=false})
}
private fun String.asciiDigits(): String = map {it.digitToIntOrNull()?.digitToChar() ?: it}.joinToString("")

@Composable private fun QuickActions(content: @Composable FlowRowScope.() -> Unit) {
 FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),content=content)
}

internal enum class AccentPreset(val id: String,val swatch: Long,val label: Int) {
 VIOLET("violet",0xFFAF9CF7,R.string.violet),
 TEAL("teal",0xFF75CEB9,R.string.teal),
 BLUE("blue",0xFF7BB6EF,R.string.blue),
 ROSE("rose",0xFFF0A7C3,R.string.preset_rose),
 AMBER("amber",0xFFF1BD75,R.string.preset_amber),
 JADE("jade",0xFF9FCD91,R.string.preset_jade),
 CORAL("coral",0xFFF3A790,R.string.preset_coral),
 SLATE("slate",0xFFA8B9D3,R.string.preset_slate)
}

@Composable internal fun ThemePresets(accent: String?,onSelect: (AccentPreset)->Unit) {
 val selected=AccentPreset.entries.firstOrNull { it.id==accent }
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  Text(stringResource(R.string.theme_presets),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
  if(selected!=null) Text(
   stringResource(selected.label),
   style=MaterialTheme.typography.bodySmall,
   color=MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
 FlowRow(Modifier.fillMaxWidth().selectableGroup(),maxItemsInEachRow=4) {
  for(preset in AccentPreset.entries) {
   val name=stringResource(preset.label)
   val chosen=selected==preset
   Box(
    Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
     .selectable(selected=chosen,role=Role.RadioButton,onClick={onSelect(preset)})
     .semantics {contentDescription=name},
    contentAlignment=Alignment.Center
   ) {
    Box(Modifier.size(32.dp).background(Color(preset.swatch),CircleShape),contentAlignment=Alignment.Center) {
     if(chosen) AppIcon(Icons.Rounded.Check,null,Color(0xFF1B2130),Modifier.size(18.dp))
    }
   }
  }
 }
}

@Composable internal fun SettingsGroup(
 key: String,title: Int,icon: androidx.compose.ui.graphics.vector.ImageVector,
 expanded: String,onExpand: (String)->Unit,quick: @Composable ColumnScope.()->Unit,
 content: @Composable ColumnScope.()->Unit
) {
 val isExpanded=expanded==key
 val toggle={onExpand(if(isExpanded) "" else key)}
 Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
  Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
   Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(16.dp))
    .settingExpandAction(isExpanded,toggle).clickable(role=Role.Button,onClick=toggle),
    verticalAlignment=Alignment.CenterVertically
   ) {
    AppIcon(icon,null,MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(12.dp))
    Text(stringResource(title),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
    SettingChevron(isExpanded)
   }
   quick()
   SettingVisibility(isExpanded) {
    Column(Modifier.padding(top=8.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     HorizontalDivider()
     content()
    }
   }
  }
 }
}
@Composable private fun SettingVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
 val reduced = LocalReducedMotion.current
 val modifier = if(visible) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() }
 AnimatedVisibility(visible, modifier, enter=sectionEnter(reduced), exit=sectionExit(reduced), content=content)
}
private fun Modifier.settingExpandAction(expanded: Boolean?, onClick: () -> Unit): Modifier =
 if(expanded==null) this else semantics {
  val toggle = { onClick(); true }
  if(expanded) collapse(action=toggle) else expand(action=toggle)
 }
@Composable private fun SettingChevron(expanded: Boolean) {
 val reduced = LocalReducedMotion.current
 val rotation by animateFloatAsState(if(expanded) 180f else 0f, motionSpec(reduced), label="settings chevron")
 val tint by animateColorAsState(
  if(expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
  motionSpec(reduced),
  label="settings chevron color"
 )
 AppIcon(Icons.Rounded.ExpandMore,null,tint,Modifier.size(22.dp).graphicsLayer { rotationZ=rotation })
}
@Composable private fun SettingValue(
 value: String?,
 style: TextStyle = MaterialTheme.typography.bodySmall,
 color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
 Crossfade(value, animationSpec=motionSpec(LocalReducedMotion.current), label="settings value") { text ->
  if(text!=null) Text(
   text,
   modifier=if(text==value) Modifier else Modifier.clearAndSetSemantics {},
   style=style,
   color=color
  )
 }
}
@Composable fun SettingsLink(title: Int,subtitle: String?=null,expanded: Boolean?=null,onClick: ()->Unit) {
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
  .settingExpandAction(expanded,onClick).clickable(role=Role.Button,onClick=onClick)
  .padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)) {
   Text(stringResource(title),style=MaterialTheme.typography.bodyLarge)
   SettingValue(subtitle)
  }
  if(expanded==null) AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null) else SettingChevron(expanded)
 }
}
@Composable fun <T> ChoiceRow(title: Int,value: T,choices: List<Pair<T,String>>,modifier: Modifier=Modifier,onChange: (T)->Unit) {
 var open by remember{mutableStateOf(false)}
 val reduced = LocalReducedMotion.current
 Box(modifier) {
  SettingsLink(title,choices.firstOrNull {it.first==value}?.second,expanded=open){open=!open}
  DropdownMenu(expanded=open,onDismissRequest={open=false}) {
   for((option,label) in choices) {
    val chosen = option == value
    val highlight by animateColorAsState(
     if(chosen) MaterialTheme.colorScheme.primary.copy(alpha=.12f) else Color.Transparent,
     motionSpec(reduced), label="selected option"
    )
    val checkAlpha by animateFloatAsState(if(chosen) 1f else 0f, motionSpec(reduced), label="option checkmark")
    DropdownMenuItem(
     text={Text(label)},
     onClick={if(open){open=false;onChange(option)}},
     modifier=Modifier.drawBehind { drawRect(highlight) }.semantics {
      selected = chosen
      if(!open) hideFromAccessibility()
     },
     trailingIcon={AppIcon(Icons.Rounded.Check,null,modifier=Modifier.size(22.dp).graphicsLayer { alpha=checkAlpha })}
    )
   }
  }
 }
}
@Composable private fun DistractionApps(p: Preferences,onChange: (Set<String>)->Unit) {
 val context=LocalContext.current
 val locale=LocalConfiguration.current.locales[0]
 var expanded by remember {mutableStateOf(false)}
 var requested by remember {mutableStateOf(false)}
 val apps=if(requested) {
  produceState<List<Pair<String,String>>?>(null,context) {
   value=withContext(Dispatchers.IO) {
    val manager=context.packageManager
    manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
     .asSequence()
     .filter {it.activityInfo.packageName!=context.packageName}
     .map {it.activityInfo.packageName to it.loadLabel(manager).toString()}
     .distinctBy {it.first}
     .sortedBy {it.second.lowercase(locale)}
     .toList()
   }
  }.value
 } else null
 val toggle={requested=true;expanded=!expanded}
 TextButton(onClick=toggle,modifier=Modifier.settingExpandAction(expanded,toggle)) {
  SettingValue(stringResource(R.string.distractions)+" · "+number(p.distractionPackages.size,p,locale),LocalTextStyle.current,Color.Unspecified)
  Spacer(Modifier.width(4.dp))
  SettingChevron(expanded)
 }
 SettingVisibility(expanded) {
  when {
   apps==null -> CircularProgressIndicator(Modifier.size(24.dp))
   apps.isEmpty() -> Text(stringResource(R.string.no_apps))
   else -> LazyColumn(Modifier.heightIn(max=280.dp)) {
    items(apps,key={it.first},contentType={"app"}) { (pkg,label) ->
     val checked=pkg in p.distractionPackages
     Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,role=Role.Checkbox) { selected ->
      onChange(if(selected) p.distractionPackages+pkg else p.distractionPackages-pkg)
     },verticalAlignment=Alignment.CenterVertically) {
      Checkbox(checked,onCheckedChange=null)
      Spacer(Modifier.width(12.dp))
      Text(label)
     }
    }
   }
  }
 }
}
@Composable fun ConfirmDialog(title: Int,body: Int,confirm: ()->Unit,dismiss: ()->Unit) {
 AlertDialog(onDismissRequest=dismiss,title={Text(stringResource(title))},text={Text(stringResource(body))},confirmButton={TextButton(onClick=confirm){Text(stringResource(R.string.done))}},dismissButton={TextButton(onClick=dismiss){Text(stringResource(R.string.cancel))}})
}
