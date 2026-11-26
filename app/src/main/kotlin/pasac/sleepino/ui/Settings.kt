@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package pasac.sleepino.ui

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pasac.sleepino.R
import pasac.sleepino.core.*
import pasac.sleepino.platform.*
import java.time.*
import java.time.format.TextStyle as DayNameStyle
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PageHeader(title: Int, onBack: () -> Unit) {
 Row(
  Modifier.fillMaxWidth().padding(vertical = 8.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  IconButton(
   onClick = onBack,
   modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
  ) {
   AppIcon(Icons.AutoMirrored.Rounded.ArrowBack, R.string.back)
  }
  Spacer(Modifier.width(4.dp))
  Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
 }
}

private enum class SettingsModal { REMINDERS, DND, APPS, TIMEZONE, LICENSES }

@Composable
fun SettingsScreen(
 s: DashboardState,
 vm: AppViewModel,
 onBack: () -> Unit,
 onEditor: (String) -> Unit
) {
 val context = LocalContext.current
 val p = s.config.prefs
 val locale = LocalConfiguration.current.locales[0]
 var activeModal by rememberSaveable { mutableStateOf<SettingsModal?>(null) }
 var checkPermission by remember { mutableIntStateOf(0) }
 LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checkPermission++ }

 fun prefs(f: (Preferences) -> Preferences) { vm.update { it.copy(prefs = f(it.prefs)) } }
 fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { vm.message.tryEmit(R.string.error) } }

 val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
 val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }
 val holidayImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importHolidays) }
 val noticePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
  checkPermission++
  vm.resume()
 }
 val sound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if (uri != null) {
   runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   prefs { it.copy(soundUri = uri.toString()) }
  }
 }
 val requestNotificationAccess = {
  if (Build.VERSION.SDK_INT >= 33 &&
   ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
  ) {
   noticePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  } else {
   open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
  }
 }

 var reset by remember { mutableStateOf(false) }
 val backup by vm.pendingBackup.collectAsState()

 val notificationsAllowed by produceState<Boolean?>(null, context, checkPermission) {
  value = withContext(Dispatchers.IO) { Notifications(context).allowed() }
 }
 val usageAllowed by produceState<Boolean?>(null, context, checkPermission) {
  value = withContext(Dispatchers.IO) { DeviceActivity(context).hasAccess() }
 }
 val dndAllowed = remember(checkPermission, p.dnd) { vm.controller.dnd.allowed() }
 val exactAllowed = remember(checkPermission, p.preciseAlarms) { vm.controller.canBePrecise() }

 val today = remember(s.now, p.zoneId) { s.now.atZone(p.zone()).toLocalDate() }
 val upcomingOverrides = remember(s.config.overrides, today) {
  s.config.overrides.count { runCatching { LocalDate.parse(it.date) >= today }.getOrDefault(false) }
 }

 LazyColumn(
  Modifier.fillMaxSize().widthIn(max = 800.dp),
  contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
  verticalArrangement = Arrangement.spacedBy(18.dp)
 ) {
  item(key = "header", contentType = "header") {
   PageHeader(R.string.settings, onBack)
  }

  // Group 1: Schedule
  item(key = "schedule", contentType = "group") {
   SettingsSection(R.string.schedule) {
    SettingsLink(
     title = R.string.sleep_schedule,
     subtitle = sleepScheduleSummary(s.config, p, locale, context),
     icon = Icons.Rounded.Bedtime
    ) { onEditor("sleep") }
    RowDivider()
    SettingsLink(
     title = R.string.work_schedule,
     subtitle = activityScheduleSummary(s.config.work, p, locale, context),
     icon = Icons.Rounded.WorkOutline
    ) { onEditor("work") }
    RowDivider()
    SettingsLink(
     title = R.string.exercise_schedule,
     subtitle = activityScheduleSummary(s.config.exercise, p, locale, context),
     icon = Icons.Rounded.FitnessCenter
    ) { onEditor("exercise") }
    RowDivider()
    SettingsLink(
     title = R.string.overrides,
     subtitle = if (upcomingOverrides > 0) stringResource(R.string.upcoming_count, number(upcomingOverrides, p, locale))
     else stringResource(R.string.override_subtitle_empty),
     icon = Icons.Rounded.EventBusy
    ) { onEditor("overrides") }
   }
  }

  // Group 2: Reminders & Bedtime Focus
  item(key = "reminders", contentType = "group") {
   SettingsSection(R.string.reminders_and_focus) {
    val reminderSubtitle = when {
     p.notifications && notificationsAllowed == false -> stringResource(R.string.notifications_blocked)
     !p.notifications -> stringResource(R.string.off)
     else -> remindersSummary(s.config, p, locale, context)
    }
    val reminderColor = if (p.notifications && notificationsAllowed == false) WarningAmber
    else MaterialTheme.colorScheme.onSurfaceVariant

    SplitSwitchRow(
     title = R.string.bedtime_reminders,
     subtitle = reminderSubtitle,
     subtitleColor = reminderColor,
     checked = p.notifications,
     onClick = { activeModal = SettingsModal.REMINDERS },
     onCheckedChange = { prefs { p0 -> p0.copy(notifications = it) } }
    )
    RowDivider()

    val dndSubtitle = when {
     !p.dnd -> stringResource(R.string.off)
     !dndAllowed -> stringResource(R.string.needs_access)
     else -> stringResource(
      R.string.dnd_summary,
      offsetLabel(p.dndStartOffset, p, locale, context),
      minutesText(p.dndEndMinutes, p, locale)
     )
    }
    val dndColor = if (p.dnd && !dndAllowed) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant

    SplitSwitchRow(
     title = R.string.dnd,
     subtitle = dndSubtitle,
     subtitleColor = dndColor,
     checked = p.dnd,
     onClick = { activeModal = SettingsModal.DND },
     onCheckedChange = { enabled ->
      if (enabled) vm.controller.dnd.enableNewRule()
      prefs { it.copy(dnd = enabled) }
     }
    )
    RowDivider()

    val appsCount = p.distractionPackages.size
    SettingsLink(
     title = R.string.late_night_app_reminders,
     subtitle = if (appsCount == 0) stringResource(R.string.off)
     else pluralStringResource(R.plurals.apps_selected, appsCount, number(appsCount, p, locale))
    ) { activeModal = SettingsModal.APPS }
   }
  }

  // Group 3: Time & Calendar
  item(key = "time", contentType = "group") {
   SettingsSection(R.string.time_and_calendar) {
    SwitchRow(
     title = R.string.count_work_remaining,
     checked = p.countWorkRemaining,
     onChecked = { v -> prefs { it.copy(countWorkRemaining = v) } },
     description = R.string.count_work_remaining_explanation
    )
    RowDivider()
    ChoiceRow(
     title = R.string.week_start,
     value = p.weekStart,
     choices = (1..7).map { it to DayOfWeek.of(it).getDisplayName(DayNameStyle.FULL, locale) }
    ) { v -> prefs { it.copy(weekStart = v) } }
    RowDivider()
    ChoiceRow(
     title = R.string.holiday_region,
     value = p.region,
     choices = listOf(
      HolidayRegion.IRAN to stringResource(R.string.iran),
      HolidayRegion.US to stringResource(R.string.united_states),
      HolidayRegion.NONE to stringResource(R.string.no_holidays)
     )
    ) { v -> prefs { it.copy(region = v) } }
    if (p.region == HolidayRegion.IRAN) {
     val year = s.now.atZone(p.zone()).year
     val status by produceState<HolidayDataStatus?>(null, vm.holidays, p.region, year, s.holidayRevision) {
      value = withContext(Dispatchers.IO) { vm.holidays.getDataStatus(p.region, year) }
     }
     status?.let { coverage ->
      val from = coverage.validFrom
      val to = coverage.validTo
      val covText = if (from != null && to != null && coverage.reliable) {
       stringResource(R.string.holiday_coverage, dateText(from, p, locale, true) + " – " + dateText(to, p, locale, true))
      } else stringResource(R.string.no_coverage)
      Row(
       Modifier.fillMaxWidth().padding(vertical = 4.dp),
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
       Text(covText, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
       TextButton(onClick = { holidayImport.launch(arrayOf("application/json", "text/plain")) }) {
        Text(stringResource(R.string.update_holiday_file))
       }
      }
     }
    }
    RowDivider()
    SettingsLink(
     title = R.string.timezone,
     subtitle = timezoneSummary(p, s.now, locale)
    ) { activeModal = SettingsModal.TIMEZONE }
   }
  }

  // Group 4: Appearance & Language
  item(key = "appearance", contentType = "group") {
   SettingsSection(R.string.appearance) {
    Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
    ThemeSegmentedRow(p.theme) { selected -> prefs { it.copy(theme = selected) } }
    RowDivider()
    if (Build.VERSION.SDK_INT >= 31) {
     SwitchRow(R.string.dynamic_colors, p.dynamicColor, { v -> prefs { it.copy(dynamicColor = v) } })
    }
    ThemePresets(p.accent.takeUnless { p.dynamicColor && Build.VERSION.SDK_INT >= 31 }) { selected ->
     prefs { it.copy(accent = selected.id, dynamicColor = false) }
    }
    RowDivider()
    ChoiceRow(
     title = R.string.language,
     value = p.language,
     choices = listOf(
      "system" to stringResource(R.string.system),
      "en" to stringResource(R.string.english),
      "fa" to stringResource(R.string.persian)
     )
    ) { v -> prefs { it.copy(language = v, jalali = if (v == "fa") true else it.jalali) } }
    RowDivider()
    SwitchRow(R.string.date_format, p.jalali, { v -> prefs { it.copy(jalali = v) } })
    RowDivider()
    SwitchRow(R.string.clock_24, p.clock24, { v -> prefs { it.copy(clock24 = v) } })
    RowDivider()
    SwitchRow(R.string.reduce_motion, p.reduceMotion, { v -> prefs { it.copy(reduceMotion = v) } })
   }
  }

  // Group 5: Home Screen
  item(key = "home_screen", contentType = "group") {
   HomeScreenDisplaySection(p = p, onPrefs = ::prefs)
  }

  // Group 5: Permissions & Reliability
  item(key = "permissions", contentType = "group") {
   SettingsSection(R.string.permissions_and_reliability) {
    SettingsLink(
     title = R.string.notifications,
     subtitle = permissionLabel(notificationsAllowed == true),
     subtitleColor = if (notificationsAllowed == false) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
     onClick = requestNotificationAccess
    )
    RowDivider()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
     SwitchRow(
      title = R.string.exact_alarms,
      checked = p.preciseAlarms,
      onChecked = { v -> prefs { it.copy(preciseAlarms = v) } },
      description = R.string.exact_explanation
     )
     if (Build.VERSION.SDK_INT >= 31 && p.preciseAlarms && !exactAllowed) {
      TextButton(
       onClick = { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) },
       contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
      ) {
       Text(stringResource(R.string.needs_system_permission), color = WarningAmber)
      }
     }
    }
    RowDivider()
    SettingsLink(
     title = R.string.usage_access,
     subtitle = permissionLabel(usageAllowed == true)
    ) { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    RowDivider()
    SettingsLink(
     title = R.string.dnd_access,
     subtitle = permissionLabel(dndAllowed)
    ) { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
    RowDivider()
    SettingsLink(
     title = R.string.battery,
     subtitle = stringResource(R.string.battery_subtitle)
    ) { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
   }
  }

  // Group 6: Data & About
  item(key = "data", contentType = "group") {
   SettingsSection(R.string.data_and_about) {
    SettingsLink(R.string.export_backup) {
     export.launch("sleepino-backup-${LocalDate.now()}.json")
    }
    RowDivider()
    SettingsLink(R.string.import_backup) {
     restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    RowDivider()
    Row(
     Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button) { reset = true }
      .padding(vertical = 12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Text(stringResource(R.string.reset), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
    RowDivider()
    val version = number(pasac.sleepino.BuildConfig.VERSION_NAME, p, locale)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
     Text("${stringResource(R.string.app_name)} · $version", style = MaterialTheme.typography.titleSmall)
     Text(stringResource(R.string.privacy_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsLink(R.string.open_source_licenses) { activeModal = SettingsModal.LICENSES }
   }
   Spacer(Modifier.height(24.dp))
  }
 }

 when (activeModal) {
  SettingsModal.REMINDERS -> RemindersSheet(
   config = s.config,
   notificationsAllowed = notificationsAllowed == true,
   usageAllowed = usageAllowed == true,
   onRequestNotifications = requestNotificationAccess,
   onRequestUsageAccess = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
   onPickSound = { sound.launch(arrayOf("audio/*")) },
   onUpdate = vm::update,
   onDismiss = { activeModal = null }
  )
  SettingsModal.DND -> DndSheet(
   p = p,
   locale = locale,
   dndAllowed = dndAllowed,
   onRequestDndAccess = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
   onUpdate = { f -> prefs(f) },
   onDismiss = { activeModal = null }
  )
  SettingsModal.APPS -> AppPickerSheet(
   p = p,
   locale = locale,
   usageAllowed = usageAllowed == true,
   onRequestUsageAccess = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
   onOpenDigitalWellbeing = { open(Intent("android.settings.WELLBEING_SETTINGS")) },
   onChange = { pkgs -> prefs { it.copy(distractionPackages = pkgs) } },
   onDismiss = { activeModal = null }
  )
  SettingsModal.TIMEZONE -> TimezonePickerSheet(
   p = p,
   now = s.now,
   onSelect = { zoneId -> prefs { it.copy(zoneId = zoneId) }; activeModal = null },
   onDismiss = { activeModal = null }
  )
  SettingsModal.LICENSES -> LicensesSheet(onDismiss = { activeModal = null })
  null -> {}
 }

 if (backup != null) {
  ConfirmDialog(R.string.restore_title, R.string.restore_body, { vm.restore() }, { vm.pendingBackup.value = null })
 }
 if (reset) {
  ConfirmDialog(R.string.reset, R.string.reset_body, { vm.reset(); reset = false }, { reset = false })
 }
}

@Composable
private fun SettingsSection(title: Int, content: @Composable ColumnScope.() -> Unit) {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text(
   stringResource(title),
   Modifier.padding(horizontal = 4.dp),
   style = MaterialTheme.typography.titleSmall,
   color = MaterialTheme.colorScheme.primary
  )
  Panel(Modifier.fillMaxWidth(), content = content)
 }
}

@Composable
internal fun HomeScreenDisplaySection(p: Preferences, onPrefs: ((Preferences) -> Preferences) -> Unit) {
 SettingsSection(R.string.home_screen) {
  SwitchRow(R.string.show_year, p.showYear, { v -> onPrefs { it.copy(showYear = v) } })
  RowDivider()
  SwitchRow(R.string.show_bedtime, p.showBedtime, { v -> onPrefs { it.copy(showBedtime = v) } })
  RowDivider()
  SwitchRow(R.string.show_day_type, p.showDayType, { v -> onPrefs { it.copy(showDayType = v) } })
 }
}

@Composable
private fun RowDivider() {
 HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun permissionLabel(allowed: Boolean): String =
 stringResource(if (allowed) R.string.allowed else R.string.needs_access)

@Composable
private fun SplitSwitchRow(
 title: Int,
 subtitle: String,
 subtitleColor: Color,
 checked: Boolean,
 onClick: () -> Unit,
 onCheckedChange: (Boolean) -> Unit
) {
 val label = stringResource(title)
 Row(
  Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Column(
   Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
   verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
   Text(label, style = MaterialTheme.typography.bodyLarge)
   Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
  }
  Spacer(Modifier.width(12.dp))
  Switch(
   checked = checked,
   onCheckedChange = onCheckedChange,
   modifier = Modifier.semantics { contentDescription = label }
  )
 }
}

@Composable
internal fun ThemeSegmentedRow(theme: ThemeMode, onSelect: (ThemeMode) -> Unit) {
 val options = listOf(
  ThemeMode.SYSTEM to R.string.system,
  ThemeMode.LIGHT to R.string.light,
  ThemeMode.DARK to R.string.dark,
  ThemeMode.AMOLED to R.string.amoled
 )
 SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
  options.forEachIndexed { index, (mode, labelRes) ->
   SegmentedButton(
    selected = theme == mode,
    onClick = { onSelect(mode) },
    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
    icon = {}
   ) {
    Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
   }
  }
 }
}

internal fun sleepScheduleSummary(config: Configuration, p: Preferences, locale: Locale, context: Context): String {
 val effective = (1..7).mapNotNull { day ->
  val rule = config.sleep.firstOrNull { it.day == day }?.takeIf { it.enabled }
  when {
   rule != null -> if (rule.useWakeTime) Triple(rule.bedtime, rule.wake, true) else Triple(rule.bedtime, rule.durationMinutes, false)
   p.baseSleep -> Triple(p.baseBedtime, p.baseWake, true)
   else -> null
  }
 }
 if (effective.isEmpty()) return context.getString(R.string.not_set)
 fun formatWindow(w: Triple<Int, Int, Boolean>): String = if (w.third) {
  "${minutesText(w.first, p, locale)} – ${minutesText(w.second, p, locale)}"
 } else {
  "${minutesText(w.first, p, locale)} · ${duration(context, w.second * 60L, p)}"
 }
 if (config.sleep.none { it.enabled } && p.baseSleep) {
  return context.getString(R.string.default_bedtime_summary, "${minutesText(p.baseBedtime, p, locale)} – ${minutesText(p.baseWake, p, locale)}")
 }
 val distinct = effective.distinct()
 if (effective.size == 7 && distinct.size == 1) {
  return context.getString(R.string.every_day_summary, formatWindow(distinct.first()))
 }
 if (effective.size == 7) {
  val weekdays = (1..5).map { effective[it - 1] }.distinct()
  val weekends = (6..7).map { effective[it - 1] }.distinct()
  if (weekdays.size == 1 && weekends.size == 1) {
   return context.getString(
    R.string.weekdays_weekends_summary,
    minutesText(weekdays.first().first, p, locale),
    minutesText(weekends.first().first, p, locale)
   )
  }
 }
 return if (distinct.size == 1) {
  context.getString(R.string.nights_per_week_summary, number(effective.size, p, locale), formatWindow(distinct.first()))
 } else {
  context.getString(R.string.nights_per_week_custom, number(effective.size, p, locale))
 }
}

internal fun activityScheduleSummary(rules: List<DayRule>, p: Preferences, locale: Locale, context: Context): String {
 val active = rules.filter { it.slots.isNotEmpty() }
 if (active.isEmpty()) return context.getString(R.string.not_set)
 val singleSlots = active.mapNotNull { it.slots.singleOrNull() }
 if (singleSlots.size == active.size && singleSlots.distinct().size == 1) {
  val slot = singleSlots.first()
  val range = "${minutesText(slot.start, p, locale)} – ${minutesText(slot.end, p, locale)}"
  return if (active.size == 7) context.getString(R.string.every_day_summary, range)
  else context.getString(R.string.days_per_week_summary, number(active.size, p, locale), range)
 }
 return context.getString(R.string.days_per_week, number(active.size, p, locale))
}

private fun offsetLabel(offsetMinutes: Int, p: Preferences, locale: Locale, context: Context): String = when {
 offsetMinutes == 0 -> context.getString(R.string.at_bedtime)
 offsetMinutes == -60 -> context.getString(R.string.hour_before)
 offsetMinutes < 0 -> context.getString(R.string.min_before, number(offsetMinutes.absoluteValue, p, locale))
 else -> context.getString(R.string.min_after, number(offsetMinutes, p, locale))
}

private fun remindersSummary(config: Configuration, p: Preferences, locale: Locale, context: Context): String {
 val offsets = buildSet {
  config.reminders.filter { it.enabled }.forEach { add(it.offsetMinutes) }
  if (p.minimumScheduledReminder) add(0)
 }.sorted()
 val leadPart = if (offsets.isEmpty()) context.getString(R.string.on)
 else offsets.take(3).joinToString(" · ") { offsetLabel(it, p, locale, context) }
 return "$leadPart · ${context.getString(R.string.snooze_short, number(p.snoozeMinutes, p, locale))}"
}

@Composable
private fun timezoneSummary(p: Preferences, now: Instant, locale: Locale): String {
 if (p.zoneId == "system") return stringResource(R.string.timezone_auto)
 val zone = runCatching { ZoneId.of(p.zoneId) }.getOrNull() ?: return p.zoneId
 val offset = zone.rules.getOffset(now)
 val offsetStr = if (offset.totalSeconds == 0) "UTC+00:00" else "UTC${offset.id}"
 return "${p.zoneId} (${number(offsetStr, p, locale)})"
}

@Composable
private fun RemindersSheet(
 config: Configuration,
 notificationsAllowed: Boolean,
 usageAllowed: Boolean,
 onRequestNotifications: () -> Unit,
 onRequestUsageAccess: () -> Unit,
 onPickSound: () -> Unit,
 onUpdate: ((Configuration) -> Configuration) -> Unit,
 onDismiss: () -> Unit
) {
 val context = LocalContext.current
 val p = config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val presetOffsets = listOf(-60, -30, -15, -10, 0)
 val customStages = config.reminders.filter { it.offsetMinutes !in presetOffsets || it.text.isNotBlank() }

 var showCustomBuilder by remember { mutableStateOf(false) }
 var customBefore by remember { mutableStateOf(true) }
 var customMinutes by remember { mutableIntStateOf(20) }
 var customText by remember { mutableStateOf("") }

 ModalBottomSheet(
  onDismissRequest = onDismiss,
  sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
    .padding(horizontal = 22.dp).padding(bottom = 30.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Text(stringResource(R.string.bedtime_reminders), style = MaterialTheme.typography.headlineMedium)

   if (!notificationsAllowed) {
    InlineWarningBanner(
     text = stringResource(R.string.notifications_blocked),
     actionLabel = stringResource(R.string.notification_access),
     onAction = onRequestNotifications
    )
   }

   Panel(Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.when_to_remind), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
     for (offset in presetOffsets) {
      val selected = if (offset == 0) {
       config.reminders.any { it.enabled && it.offsetMinutes == 0 && it.text.isBlank() } || p.minimumScheduledReminder
      } else {
       config.reminders.any { it.enabled && it.offsetMinutes == offset && it.text.isBlank() }
      }
      FilterChip(
       selected = selected,
       onClick = {
        onUpdate { cfg ->
         val withoutPreset = cfg.reminders.filterNot { it.offsetMinutes == offset && it.text.isBlank() }
         val nextReminders = if (selected) {
          withoutPreset
         } else if (withoutPreset.size < 20) {
          val id = (1..1000).first { cand -> withoutPreset.none { it.id == cand } }
          withoutPreset + ReminderStage(id, offset, "", true)
         } else withoutPreset
         val nextPrefs = if (offset == 0) cfg.prefs.copy(minimumScheduledReminder = !selected) else cfg.prefs
         cfg.copy(prefs = nextPrefs, reminders = nextReminders)
        }
       },
       label = { Text(offsetLabel(offset, p, locale, context)) }
      )
     }
    }

    for (stage in customStages) key(stage.id) {
     Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text(offsetLabel(stage.offsetMinutes, p, locale, context), style = MaterialTheme.typography.bodyMedium)
       if (stage.text.isNotBlank()) Text(stage.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick = {
       onUpdate { cfg -> cfg.copy(reminders = cfg.reminders.filterNot { it.id == stage.id }) }
      }) {
       AppIcon(Icons.Rounded.DeleteOutline, R.string.delete)
      }
     }
    }

    if (showCustomBuilder) {
     Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
       .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
       .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
     ) {
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
       SegmentedButton(
        selected = customBefore,
        onClick = { customBefore = true },
        shape = SegmentedButtonDefaults.itemShape(0, 2)
       ) { Text(stringResource(R.string.before)) }
       SegmentedButton(
        selected = !customBefore,
        onClick = { customBefore = false },
        shape = SegmentedButtonDefaults.itemShape(1, 2)
       ) { Text(stringResource(R.string.after)) }
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
       IconButton(
        onClick = { customMinutes = (customMinutes - 5).coerceAtLeast(5) },
        enabled = customMinutes > 5
       ) { AppIcon(Icons.Rounded.Remove, null) }
       Text(
        stringResource(R.string.n_min, number(customMinutes, p, locale)),
        Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
       )
       IconButton(
        onClick = { customMinutes = (customMinutes + 5).coerceAtMost(720) },
        enabled = customMinutes < 720
       ) { AppIcon(Icons.Rounded.Add, null) }
      }
      OutlinedTextField(
       value = customText,
       onValueChange = { if (it.length <= 500) customText = it },
       label = { Text(stringResource(R.string.custom_text)) },
       singleLine = true,
       shape = RoundedCornerShape(14.dp),
       modifier = Modifier.fillMaxWidth()
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
       TextButton(onClick = { showCustomBuilder = false }) { Text(stringResource(R.string.cancel)) }
       FilledTonalButton(
        enabled = config.reminders.size < 20,
        onClick = {
         val signedOffset = if (customBefore) -customMinutes else customMinutes
         onUpdate { cfg ->
          val id = (1..1000).first { cand -> cfg.reminders.none { it.id == cand } }
          cfg.copy(reminders = cfg.reminders + ReminderStage(id, signedOffset, customText.trim(), true))
         }
         customText = ""
         showCustomBuilder = false
        }
       ) { Text(stringResource(R.string.add)) }
      }
     }
    } else if (config.reminders.size < 20) {
     TextButton(onClick = { showCustomBuilder = true }) {
      Text(stringResource(R.string.add_custom_reminder))
     }
    }
   }

   Panel(Modifier.fillMaxWidth()) {
    val repeatEnabled = p.maxRepeats > 0
    SwitchRow(
     title = R.string.repeat_if_awake,
     checked = repeatEnabled,
     onChecked = { on -> onUpdate { it.copy(prefs = it.prefs.copy(maxRepeats = if (on) 4 else 0)) } }
    )
    if (repeatEnabled) {
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (min in listOf(10, 15, 30)) {
       FilterChip(
        selected = p.repeatMinutes == min,
        onClick = { onUpdate { it.copy(prefs = it.prefs.copy(repeatMinutes = min)) } },
        label = { Text(stringResource(R.string.every_n_min, number(min, p, locale))) }
       )
      }
     }
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (limit in listOf(2, 4, 6)) {
       FilterChip(
        selected = p.maxRepeats == limit,
        onClick = { onUpdate { it.copy(prefs = it.prefs.copy(maxRepeats = limit)) } },
        label = { Text(stringResource(R.string.times_limit, number(limit, p, locale))) }
       )
      }
     }
    }
   }

   Panel(Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.snooze), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     for (min in listOf(5, 10, 15, 30)) {
      FilterChip(
       selected = p.snoozeMinutes == min,
       onClick = { onUpdate { it.copy(prefs = it.prefs.copy(snoozeMinutes = min)) } },
       label = { Text(stringResource(R.string.n_min, number(min, p, locale))) }
      )
     }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     for (limit in listOf(1, 3, 5)) {
      FilterChip(
       selected = p.maxSnoozes == limit,
       onClick = { onUpdate { it.copy(prefs = it.prefs.copy(maxSnoozes = limit)) } },
       label = { Text(stringResource(R.string.times_limit, number(limit, p, locale))) }
      )
     }
    }
   }

   Panel(Modifier.fillMaxWidth()) {
    SwitchRow(
     title = R.string.skip_if_idle,
     checked = p.smartReminders,
     onChecked = { on -> onUpdate { it.copy(prefs = it.prefs.copy(smartReminders = on)) } },
     description = R.string.smart_explanation
    )
    if (p.smartReminders && !usageAllowed) {
     InlineWarningBanner(
      text = stringResource(R.string.usage_access),
      actionLabel = stringResource(R.string.grant_usage_access),
      onAction = onRequestUsageAccess
     )
    }
    if (p.smartReminders) {
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (min in listOf(10, 20, 30, 45)) {
       FilterChip(
        selected = p.inactivityMinutes == min,
        onClick = { onUpdate { it.copy(prefs = it.prefs.copy(inactivityMinutes = min)) } },
        label = { Text(stringResource(R.string.n_min, number(min, p, locale))) }
       )
      }
     }
    }
   }

   Panel(Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.alert_sound_vibration), style = MaterialTheme.typography.titleMedium)
    val alertMode = when {
     !p.silent -> 0
     p.vibration -> 1
     else -> 2
    }
    val modeLabels = listOf(R.string.sound_and_vibrate, R.string.vibrate_only, R.string.silent_mode)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
     modeLabels.forEachIndexed { idx, resId ->
      SegmentedButton(
       selected = alertMode == idx,
       onClick = {
        onUpdate {
         it.copy(
          prefs = when (idx) {
           0 -> it.prefs.copy(silent = false, vibration = true)
           1 -> it.prefs.copy(silent = true, vibration = true)
           else -> it.prefs.copy(silent = true, vibration = false)
          }
         )
        }
       },
       shape = SegmentedButtonDefaults.itemShape(idx, modeLabels.size),
       icon = {}
      ) {
       Text(stringResource(resId), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
     }
    }
    val soundEnabled = !p.silent
    Row(
     Modifier.fillMaxWidth().alpha(if (soundEnabled) 1f else 0.45f),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Column(
      Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
       .clickable(enabled = soundEnabled, onClick = onPickSound)
       .padding(vertical = 8.dp)
     ) {
      Text(stringResource(R.string.sound), style = MaterialTheme.typography.bodyLarge)
      Text(
       stringResource(if (p.soundUri == "default") R.string.sound_default else R.string.sound_custom),
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
     if (p.soundUri != "default") {
      IconButton(
       enabled = soundEnabled,
       onClick = { onUpdate { it.copy(prefs = it.prefs.copy(soundUri = "default")) } },
       modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
      ) {
       AppIcon(Icons.Rounded.RestartAlt, R.string.reset_sound)
      }
     }
    }
   }
  }
 }
}

@Composable
private fun DndSheet(
 p: Preferences,
 locale: Locale,
 dndAllowed: Boolean,
 onRequestDndAccess: () -> Unit,
 onUpdate: ((Preferences) -> Preferences) -> Unit,
 onDismiss: () -> Unit
) {
 val context = LocalContext.current
 ModalBottomSheet(
  onDismissRequest = onDismiss,
  sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    .padding(horizontal = 22.dp).padding(bottom = 30.dp),
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Text(stringResource(R.string.dnd), style = MaterialTheme.typography.headlineMedium)
   Text(stringResource(R.string.dnd_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

   if (!dndAllowed) {
    InlineWarningBanner(
     text = stringResource(R.string.dnd_access),
     actionLabel = stringResource(R.string.grant_dnd_access),
     onAction = onRequestDndAccess
    )
   }

   Panel(Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.dnd_starts), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     for (offset in listOf(-30, -15, 0)) {
      FilterChip(
       selected = p.dndStartOffset == offset,
       onClick = { onUpdate { it.copy(dndStartOffset = offset) } },
       label = { Text(offsetLabel(offset, p, locale, context)) }
      )
     }
    }
    TimeButton(R.string.dnd_ends, p.dndEndMinutes, p) { v -> onUpdate { it.copy(dndEndMinutes = v) } }
    SwitchRow(R.string.dnd_snooze, p.dndFollowsSnooze, { v -> onUpdate { it.copy(dndFollowsSnooze = v) } })
   }
  }
 }
}

@Composable
private fun AppPickerSheet(
 p: Preferences,
 locale: Locale,
 usageAllowed: Boolean,
 onRequestUsageAccess: () -> Unit,
 onOpenDigitalWellbeing: () -> Unit,
 onChange: (Set<String>) -> Unit,
 onDismiss: () -> Unit
) {
 val context = LocalContext.current
 var query by remember { mutableStateOf("") }
 val apps by produceState<List<Pair<String, String>>?>(null, context) {
  value = withContext(Dispatchers.IO) {
   val manager = context.packageManager
   manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    .asSequence()
    .filter { it.activityInfo.packageName != context.packageName }
    .map { it.activityInfo.packageName to it.loadLabel(manager).toString() }
    .distinctBy { it.first }
    .sortedBy { it.second.lowercase(locale) }
    .toList()
  }
 }
 val hasDigitalWellbeing = remember(context) {
  Intent("android.settings.WELLBEING_SETTINGS").resolveActivity(context.packageManager) != null
 }
 val filtered = remember(apps, query, p.distractionPackages) {
  val list = apps.orEmpty()
  val q = query.trim().lowercase(locale)
  val matching = if (q.isEmpty()) list else list.filter { it.second.lowercase(locale).contains(q) || it.first.lowercase(locale).contains(q) }
  matching.sortedByDescending { it.first in p.distractionPackages }
 }

 ModalBottomSheet(
  onDismissRequest = onDismiss,
  sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()
    .padding(horizontal = 22.dp).padding(bottom = 20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Text(stringResource(R.string.late_night_app_reminders), style = MaterialTheme.typography.headlineMedium)
   Text(stringResource(R.string.distraction_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

   if (!usageAllowed) {
    InlineWarningBanner(
     text = stringResource(R.string.usage_access),
     actionLabel = stringResource(R.string.grant_usage_access),
     onAction = onRequestUsageAccess
    )
   }

   OutlinedTextField(
    value = query,
    onValueChange = { query = it },
    label = { Text(stringResource(R.string.search_apps)) },
    leadingIcon = { AppIcon(Icons.Rounded.Search, null) },
    trailingIcon = {
     if (query.isNotEmpty()) {
      IconButton(onClick = { query = "" }) { AppIcon(Icons.Rounded.Close, R.string.close) }
     }
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
   )

   Box(Modifier.weight(1f).fillMaxWidth()) {
    when {
     apps == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
     filtered.isEmpty() -> Text(stringResource(R.string.no_apps), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
     else -> LazyColumn(Modifier.fillMaxSize()) {
      items(filtered, key = { it.first }, contentType = { "app" }) { (pkg, label) ->
       val checked = pkg in p.distractionPackages
       Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
         .toggleable(value = checked, role = Role.Checkbox) { selected ->
          onChange(if (selected) p.distractionPackages + pkg else p.distractionPackages - pkg)
         }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
       ) {
        Checkbox(checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
       }
      }
     }
    }
   }

   if (hasDigitalWellbeing) {
    TextButton(onClick = onOpenDigitalWellbeing) {
     Text(stringResource(R.string.digital_wellbeing))
    }
   }
  }
 }
}

@Composable
private fun TimezonePickerSheet(
 p: Preferences,
 now: Instant,
 onSelect: (String) -> Unit,
 onDismiss: () -> Unit
) {
 val locale = LocalConfiguration.current.locales[0]
 var query by remember { mutableStateOf("") }
 val zones = remember(now) {
  ZoneId.getAvailableZoneIds()
   .sorted()
   .map { id ->
    val offset = ZoneId.of(id).rules.getOffset(now)
    val offsetLabel = if (offset.totalSeconds == 0) "UTC+00:00" else "UTC${offset.id}"
    id to offsetLabel
   }
 }
 val filtered = remember(zones, query) {
  val q = query.trim().lowercase(locale)
  if (q.isEmpty()) zones else zones.filter { it.first.lowercase(locale).contains(q) || it.second.lowercase(locale).contains(q) }
 }

 ModalBottomSheet(
  onDismissRequest = onDismiss,
  sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()
    .padding(horizontal = 22.dp).padding(bottom = 20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Text(stringResource(R.string.timezone), style = MaterialTheme.typography.headlineMedium)
   SwitchRow(
    title = R.string.follow_device_timezone,
    checked = p.zoneId == "system",
    onChecked = { follow -> if (follow) onSelect("system") }
   )
   OutlinedTextField(
    value = query,
    onValueChange = { query = it },
    label = { Text(stringResource(R.string.search_timezone)) },
    leadingIcon = { AppIcon(Icons.Rounded.Search, null) },
    trailingIcon = {
     if (query.isNotEmpty()) {
      IconButton(onClick = { query = "" }) { AppIcon(Icons.Rounded.Close, R.string.close) }
     }
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
   )
   LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
    items(filtered, key = { it.first }, contentType = { "zone" }) { (id, offset) ->
     val chosen = p.zoneId == id
     Row(
      Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
       .clickable { onSelect(id) }.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
     ) {
      Column(Modifier.weight(1f)) {
       Text(id, style = MaterialTheme.typography.bodyLarge)
       Text(number(offset, p, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (chosen) AppIcon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
     }
    }
   }
  }
 }
}

@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
 val context = LocalContext.current
 val licenseTexts by produceState<List<Pair<String, String>>?>(null, context) {
  value = withContext(Dispatchers.IO) {
   listOf("Estedad-OFL.txt", "events-license.txt", "qamari-license.txt", "jalaali-license.txt").mapNotNull { file ->
    runCatching {
     file to context.assets.open("licenses/$file").bufferedReader().use { it.readText() }
    }.getOrNull()
   }
  }
 }
 ModalBottomSheet(
  onDismissRequest = onDismiss,
  sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    .padding(horizontal = 22.dp).padding(bottom = 30.dp),
   verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
   Text(stringResource(R.string.open_source_licenses), style = MaterialTheme.typography.headlineMedium)
   Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall)
   licenseTexts?.forEach { (name, body) ->
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    Text(name, style = MaterialTheme.typography.titleSmall)
    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}

@Composable
private fun InlineWarningBanner(text: String, actionLabel: String, onAction: () -> Unit) {
 Surface(
  shape = RoundedCornerShape(16.dp),
  color = WarningAmber.copy(alpha = 0.16f),
  modifier = Modifier.fillMaxWidth()
 ) {
  Row(
   Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
   AppIcon(Icons.Rounded.WarningAmber, null, tint = WarningAmber)
   Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
   TextButton(onClick = onAction) { Text(actionLabel) }
  }
 }
}

internal enum class AccentPreset(val id: String, val swatch: Long, val label: Int) {
 VIOLET("violet", 0xFFAF9CF7, R.string.violet),
 TEAL("teal", 0xFF75CEB9, R.string.teal),
 BLUE("blue", 0xFF7BB6EF, R.string.blue),
 ROSE("rose", 0xFFF0A7C3, R.string.preset_rose),
 AMBER("amber", 0xFFF1BD75, R.string.preset_amber),
 JADE("jade", 0xFF9FCD91, R.string.preset_jade),
 CORAL("coral", 0xFFF3A790, R.string.preset_coral),
 SLATE("slate", 0xFFA8B9D3, R.string.preset_slate)
}

@Composable
internal fun ThemePresets(accent: String?, onSelect: (AccentPreset) -> Unit) {
 val selected = AccentPreset.entries.firstOrNull { it.id == accent }
 Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
  Text(stringResource(R.string.theme_presets), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
  if (selected != null) {
   Text(
    stringResource(selected.label),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
 FlowRow(Modifier.fillMaxWidth().selectableGroup(), maxItemsInEachRow = 4) {
  for (preset in AccentPreset.entries) {
   val name = stringResource(preset.label)
   val chosen = selected == preset
   Box(
    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
     .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(preset) })
     .semantics { contentDescription = name },
    contentAlignment = Alignment.Center
   ) {
    Box(Modifier.size(32.dp).background(Color(preset.swatch), CircleShape), contentAlignment = Alignment.Center) {
     if (chosen) AppIcon(Icons.Rounded.Check, null, Color(0xFF1B2130), Modifier.size(18.dp))
    }
   }
  }
 }
}

private fun Modifier.settingExpandAction(expanded: Boolean?, onClick: () -> Unit): Modifier =
 if (expanded == null) this else semantics {
  val toggle = { onClick(); true }
  if (expanded) collapse(action = toggle) else expand(action = toggle)
 }

@Composable
private fun SettingChevron(expanded: Boolean) {
 val reduced = LocalReducedMotion.current
 val rotation by animateFloatAsState(if (expanded) 180f else 0f, motionSpec(reduced), label = "settings chevron")
 val tint by animateColorAsState(
  if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
  motionSpec(reduced),
  label = "settings chevron color"
 )
 AppIcon(Icons.Rounded.ExpandMore, null, tint, Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
}

@Composable
private fun SettingValue(
 value: String?,
 style: TextStyle = MaterialTheme.typography.bodySmall,
 color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
 Crossfade(value, animationSpec = motionSpec(LocalReducedMotion.current), label = "settings value") { text ->
  if (text != null) {
   Text(
    text,
    modifier = if (text == value) Modifier else Modifier.clearAndSetSemantics {},
    style = style,
    color = color
   )
  }
 }
}

@Composable
fun SettingsLink(
 title: Int,
 subtitle: String? = null,
 expanded: Boolean? = null,
 icon: ImageVector? = null,
 subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
 onClick: () -> Unit
) {
 Row(
  Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
   .settingExpandAction(expanded, onClick)
   .clickable(role = Role.Button, onClick = onClick)
   .padding(vertical = 10.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  if (icon != null) {
   AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
   Spacer(Modifier.width(12.dp))
  }
  Column(Modifier.weight(1f)) {
   Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
   SettingValue(subtitle, color = subtitleColor)
  }
  if (expanded == null) AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
  else SettingChevron(expanded)
 }
}

@Composable
fun <T> ChoiceRow(title: Int, value: T, choices: List<Pair<T, String>>, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
 var open by remember { mutableStateOf(false) }
 val reduced = LocalReducedMotion.current
 Box(modifier) {
  SettingsLink(title, choices.firstOrNull { it.first == value }?.second, expanded = open) { open = !open }
  DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
   for ((option, label) in choices) {
    val chosen = option == value
    val highlight by animateColorAsState(
     if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent,
     motionSpec(reduced), label = "selected option"
    )
    val checkAlpha by animateFloatAsState(if (chosen) 1f else 0f, motionSpec(reduced), label = "option checkmark")
    DropdownMenuItem(
     text = { Text(label) },
     onClick = { if (open) { open = false; onChange(option) } },
     modifier = Modifier.drawBehind { drawRect(highlight) }.semantics {
      selected = chosen
      if (!open) hideFromAccessibility()
     },
     trailingIcon = { AppIcon(Icons.Rounded.Check, null, modifier = Modifier.size(22.dp).graphicsLayer { alpha = checkAlpha }) }
    )
   }
  }
 }
}

@Composable
fun ConfirmDialog(title: Int, body: Int, confirm: () -> Unit, dismiss: () -> Unit) {
 AlertDialog(
  onDismissRequest = dismiss,
  title = { Text(stringResource(title)) },
  text = { Text(stringResource(body)) },
  confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.done)) } },
  dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
 )
}
