@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package pasac.sleepino.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pasac.sleepino.R
import pasac.sleepino.core.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun EditorSheet(route: String, s: DashboardState, vm: AppViewModel, dismiss: () -> Unit) = key(route) {
 val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 val scope = rememberCoroutineScope()
 val reduced = LocalReducedMotion.current
 var submitting by remember { mutableStateOf(false) }
 fun submit(action: () -> Unit) {
  if (submitting) return
  submitting = true
  action()
  if (reduced) dismiss() else scope.launch {
   sheetState.hide()
   dismiss()
  }
 }
 ModalBottomSheet(
  onDismissRequest = dismiss,
  sheetState = sheetState,
  sheetGesturesEnabled = !submitting,
  properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !submitting, shouldDismissOnClickOutside = !submitting),
  containerColor = MaterialTheme.colorScheme.background
 ) {
  Column(
   Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
    .padding(horizontal = 22.dp).padding(bottom = 30.dp),
   verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
   when (route) {
    "sleep" -> SleepEditor(
     config = s.config,
     onApply = { rules, baseSleep, baseBedtime, baseWake ->
      vm.update {
       it.copy(
        sleep = rules,
        prefs = it.prefs.copy(baseSleep = baseSleep, baseBedtime = baseBedtime, baseWake = baseWake)
       )
      }
     },
     onSave = { rules, baseSleep, baseBedtime, baseWake ->
      submit {
       vm.update {
        it.copy(
         sleep = rules,
         prefs = it.prefs.copy(baseSleep = baseSleep, baseBedtime = baseBedtime, baseWake = baseWake)
        )
       }
      }
     }
    )
    "work" -> ActivityEditor(s.config, Kind.WORK, vm, ::submit)
    "exercise" -> ActivityEditor(s.config, Kind.EXERCISE, vm, ::submit)
    "overrides" -> OverrideEditor(s.config, vm, ::submit)
    "snooze" -> SnoozeEditor(s.config.prefs) { minutes -> submit { vm.snooze(minutes) } }
   }
  }
 }
}

@Composable
fun TimeButton(label: Int, minutes: Int, p: Preferences, onChange: (Int) -> Unit) {
 val configuration = LocalConfiguration.current
 val title = stringResource(label)
 var show by rememberSaveable { mutableStateOf(false) }
 OutlinedButton(onClick = { show = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 52.dp)) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
   Text(title, style = MaterialTheme.typography.labelSmall)
   Text(minutesText(minutes, p, configuration.locales[0]), style = MaterialTheme.typography.titleMedium)
  }
 }
 if (show) {
  val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = p.clock24)
  var keyboard by rememberSaveable { mutableStateOf(false) }
  val window = LocalWindowInfo.current.containerDpSize
  val horizontal = (window.width >= 560.dp) && (window.width > window.height)
  val layout = if (horizontal) TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical
  Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
   Surface(
    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
     .widthIn(max = if (horizontal) 572.dp else 360.dp).fillMaxWidth()
     .semantics { paneTitle = title },
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface
   ) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
     Column(
      Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
     ) {
      Text(
       title,
       Modifier.align(Alignment.Start).padding(start = 8.dp, top = 8.dp, bottom = 20.dp).semantics { heading() },
       style = MaterialTheme.typography.titleLarge
      )
      val colors = TimePickerDefaults.colors(clockDialColor = MaterialTheme.colorScheme.surfaceVariant)
      if (keyboard) TimeInput(state, colors = colors)
      else TimePicker(state, colors = colors, layoutType = layout)
     }
     Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { keyboard = !keyboard }) {
       AppIcon(
        if (keyboard) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
        if (keyboard) R.string.time_dial else R.string.time_keyboard
       )
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { show = false }) { Text(stringResource(R.string.cancel)) }
      TextButton(onClick = { onChange(state.hour * 60 + state.minute); show = false }) {
       Text(stringResource(R.string.done))
      }
     }
    }
   }
  }
 }
}

private fun String.asciiDigits(): String = map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")

@Composable
fun NumberInput(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
 val locale = LocalConfiguration.current.locales[0]
 var text by remember(value, locale) { mutableStateOf(number(value, locale)) }
 val invalid = text.asciiDigits().toIntOrNull()?.let { it !in range } ?: true
 OutlinedTextField(
  value = text,
  onValueChange = { input ->
   if (input.length <= 5) {
    val digits = input.asciiDigits()
    text = number(digits, locale)
    digits.toIntOrNull()?.takeIf { it in range }?.let(onChange)
   }
  },
  label = { Text(stringResource(label)) },
  singleLine = true,
  isError = invalid,
  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  shape = RoundedCornerShape(16.dp),
  modifier = Modifier.fillMaxWidth(),
  supportingText = if (invalid) {{ Text(stringResource(R.string.invalid_input)) }} else null
 )
}

@Composable
fun SwitchRow(title: Int, checked: Boolean, onChecked: (Boolean) -> Unit, description: Int? = null) {
 Row(
  Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
   .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
   .padding(vertical = 8.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Column(Modifier.weight(1f).padding(end = 14.dp)) {
   Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
   description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
  Switch(checked = checked, onCheckedChange = null)
 }
}

@Composable
fun SaveButton(onClick: () -> Unit) {
 val interactions = remember { MutableInteractionSource() }
 Button(
  onClick = onClick,
  modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interactions),
  shape = RoundedCornerShape(18.dp),
  interactionSource = interactions
 ) { Text(stringResource(R.string.save)) }
}

@Composable
private fun DurationStepper(
 minutes: Int,
 p: Preferences,
 onChange: (Int) -> Unit
) {
 val context = LocalContext.current
 Row(
  Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
   .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
   .padding(horizontal = 12.dp, vertical = 6.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Text(stringResource(R.string.sleep_duration_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
  IconButton(
   onClick = { onChange((minutes - 15).coerceAtLeast(30)) },
   enabled = minutes > 30,
   modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
  ) {
   AppIcon(Icons.Rounded.Remove, null)
  }
  Text(
   duration(context, minutes * 60L, p),
   style = MaterialTheme.typography.titleMedium,
   modifier = Modifier.padding(horizontal = 6.dp)
  )
  IconButton(
   onClick = { onChange((minutes + 15).coerceAtMost(1200)) },
   enabled = minutes < 1200,
   modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
  ) {
   AppIcon(Icons.Rounded.Add, null)
  }
 }
}

private fun weekdayMaskForRegion(region: HolidayRegion): Int =
 if (region == HolidayRegion.IRAN) (1 shl 5) or (1 shl 6) or (1 shl 0) or (1 shl 1) or (1 shl 2)
 else 0b0011111

@Composable
private fun DaySelectorChips(
 weekdays: List<DayOfWeek>,
 selectedMask: Int,
 region: HolidayRegion,
 locale: Locale,
 onMaskChange: (Int) -> Unit
) {
 val everyDayMask = 0b1111111
 val weekdaysMask = weekdayMaskForRegion(region)
 val weekendsMask = everyDayMask xor weekdaysMask
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   FilterChip(
    selected = selectedMask == everyDayMask,
    onClick = { onMaskChange(if (selectedMask == everyDayMask) 0 else everyDayMask) },
    label = { Text(stringResource(R.string.every_day)) }
   )
   FilterChip(
    selected = selectedMask == weekdaysMask,
    onClick = { onMaskChange(if (selectedMask == weekdaysMask) 0 else weekdaysMask) },
    label = { Text(stringResource(R.string.weekdays)) }
   )
   FilterChip(
    selected = selectedMask == weekendsMask,
    onClick = { onMaskChange(if (selectedMask == weekendsMask) 0 else weekendsMask) },
    label = { Text(stringResource(R.string.weekends)) }
   )
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   for (day in weekdays) {
    val mask = 1 shl (day.value - 1)
    FilterChip(
     selected = selectedMask and mask != 0,
     onClick = { onMaskChange(selectedMask xor mask) },
     label = { Text(day.getDisplayName(TextStyle.FULL, locale)) }
    )
   }
  }
 }
}

@Composable
internal fun SleepEditor(
 config: Configuration,
 onApply: (List<SleepRule>, Boolean, Int, Int) -> Unit = { _, _, _, _ -> },
 onSave: (List<SleepRule>, Boolean, Int, Int) -> Unit
) {
 val context = LocalContext.current
 val p = config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val reduced = LocalReducedMotion.current
 var rules by remember { mutableStateOf((1..7).map { day -> config.sleep.firstOrNull { it.day == day } ?: SleepRule(day, enabled = false) }) }
 var baseSleep by rememberSaveable { mutableStateOf(p.baseSleep) }
 var baseBedtime by rememberSaveable { mutableIntStateOf(p.baseBedtime) }
 var baseWake by rememberSaveable { mutableIntStateOf(p.baseWake) }

 val initialRule = rules.firstOrNull { it.enabled } ?: rules.first()
 var sharedBedtime by rememberSaveable { mutableIntStateOf(initialRule.bedtime) }
 var sharedWake by rememberSaveable { mutableIntStateOf(initialRule.wake) }
 var sharedUseDuration by rememberSaveable { mutableStateOf(!initialRule.useWakeTime) }
 var sharedDuration by rememberSaveable { mutableIntStateOf(initialRule.durationMinutes) }
 var sharedDays by rememberSaveable { mutableIntStateOf(0) }
 var appliedNotice by remember { mutableStateOf(false) }
 var selectedDay by rememberSaveable { mutableIntStateOf(0) }
 val weekdays = remember(p.weekStart) { List(7) { DayOfWeek.of((p.weekStart - 1 + it) % 7 + 1) } }

 Text(stringResource(R.string.sleep_schedule), style = MaterialTheme.typography.headlineMedium)
 Text(stringResource(R.string.sleep_ownership), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

 Panel(Modifier.fillMaxWidth()) {
  Text(stringResource(R.string.same_sleep_schedule), style = MaterialTheme.typography.titleLarge)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   TimeButton(R.string.bedtime, sharedBedtime, p) { sharedBedtime = it; appliedNotice = false }
   if (!sharedUseDuration) {
    TimeButton(R.string.wake_time, sharedWake, p) { sharedWake = it; appliedNotice = false }
   }
  }
  SwitchRow(R.string.target_duration, sharedUseDuration, { sharedUseDuration = it; appliedNotice = false })
  if (sharedUseDuration) {
   DurationStepper(sharedDuration, p) { sharedDuration = it; appliedNotice = false }
  }
  DaySelectorChips(weekdays, sharedDays, p.region, locale) { sharedDays = it; appliedNotice = false }
  FilledTonalButton(
   onClick = {
    val nextRules = rules.map { rule ->
     if (sharedDays and (1 shl (rule.day - 1)) != 0) {
      rule.copy(
       enabled = true,
       bedtime = sharedBedtime,
       wake = sharedWake,
       durationMinutes = sharedDuration,
       useWakeTime = !sharedUseDuration
      )
     } else rule
    }
    rules = nextRules
    sharedDays = 0
    appliedNotice = true
    onApply(nextRules, baseSleep, baseBedtime, baseWake)
   },
   enabled = sharedDays != 0,
   modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
  ) {
   Text(stringResource(R.string.apply_selected_days))
  }
  AnimatedVisibility(appliedNotice, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   Text(
    stringResource(R.string.applied_to_draft),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.secondary
   )
  }
 }

 Panel(Modifier.fillMaxWidth()) {
  for ((idx, weekday) in weekdays.withIndex()) {
   if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
   val day = weekday.value
   val rule = rules[day - 1]
   fun update(newRule: SleepRule) { rules = rules.map { if (it.day == day) newRule else it } }
   Column(Modifier.animateContentSize(motionSpec(reduced))) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Column(
      Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
       .clickable { selectedDay = if (selectedDay == day) 0 else day },
      verticalArrangement = Arrangement.Center
     ) {
      Text(weekday.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.titleMedium)
      val subtitle = when {
       rule.enabled && rule.useWakeTime -> "${minutesText(rule.bedtime, p, locale)} – ${minutesText(rule.wake, p, locale)}"
       rule.enabled -> "${minutesText(rule.bedtime, p, locale)} · ${duration(context, rule.durationMinutes * 60L, p)}"
       baseSleep -> stringResource(R.string.default_bedtime_summary, "${minutesText(baseBedtime, p, locale)} – ${minutesText(baseWake, p, locale)}")
       else -> stringResource(R.string.disabled)
      }
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     Switch(
      checked = rule.enabled,
      onCheckedChange = { update(rule.copy(enabled = it)); if (it) selectedDay = day },
      modifier = Modifier.semantics { contentDescription = weekday.getDisplayName(TextStyle.FULL, locale) }
     )
    }
    if (selectedDay == day && rule.enabled) {
     Column(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
       TimeButton(R.string.bedtime, rule.bedtime, p) { update(rule.copy(bedtime = it)) }
       if (rule.useWakeTime) TimeButton(R.string.wake_time, rule.wake, p) { update(rule.copy(wake = it)) }
      }
      SwitchRow(R.string.target_duration, !rule.useWakeTime, { update(rule.copy(useWakeTime = !it)) })
      if (!rule.useWakeTime) DurationStepper(rule.durationMinutes, p) { update(rule.copy(durationMinutes = it)) }
     }
    }
   }
  }
 }

 Panel(Modifier.fillMaxWidth()) {
  SwitchRow(R.string.use_default_remaining_days, baseSleep, { baseSleep = it })
  AnimatedVisibility(baseSleep, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TimeButton(R.string.base_bedtime, baseBedtime, p) { baseBedtime = it }
    TimeButton(R.string.base_wake, baseWake, p) { baseWake = it }
   }
  }
 }

 SaveButton { onSave(rules, baseSleep, baseBedtime, baseWake) }
}

@Composable
private fun ActivityEditor(config: Configuration, kind: Kind, vm: AppViewModel, submit: (() -> Unit) -> Unit) {
 val p = config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val initialRules = remember(config, kind) {
  (1..7).map { d -> (if (kind == Kind.WORK) config.work else config.exercise).firstOrNull { it.day == d } ?: DayRule(d) }
 }
 val defaultSlot = if (kind == Kind.WORK) TimeSlot(540, 1020) else TimeSlot(1080, 1140)
 ActivityEditorContent(
  initialRules = initialRules,
  kind = kind,
  p = p,
  locale = locale,
  defaultSlot = defaultSlot,
  onApply = { savedRules ->
   vm.update { if (kind == Kind.WORK) it.copy(work = savedRules) else it.copy(exercise = savedRules) }
  },
  onSave = { savedRules ->
   submit { vm.update { if (kind == Kind.WORK) it.copy(work = savedRules) else it.copy(exercise = savedRules) } }
  }
 )
}

@Composable
internal fun ActivityEditorContent(
 initialRules: List<DayRule>,
 kind: Kind,
 p: Preferences,
 locale: Locale,
 defaultSlot: TimeSlot = if (kind == Kind.WORK) TimeSlot(540, 1020) else TimeSlot(1080, 1140),
 onApply: (List<DayRule>) -> Unit = {},
 onSave: (List<DayRule>) -> Unit
) {
 val reduced = LocalReducedMotion.current
 var rules by remember { mutableStateOf(initialRules) }
 var selected by rememberSaveable { mutableIntStateOf(0) }
 val firstSlot = rules.firstOrNull { it.slots.isNotEmpty() }?.slots?.firstOrNull() ?: defaultSlot
 var quickStart by rememberSaveable { mutableIntStateOf(firstSlot.start) }
 var quickEnd by rememberSaveable { mutableIntStateOf(firstSlot.end) }
 var quickDays by rememberSaveable { mutableIntStateOf(0) }
 var quickError by remember { mutableStateOf(false) }
 var appliedNotice by remember { mutableStateOf(false) }
 val weekdays = remember(p.weekStart) { List(7) { DayOfWeek.of((p.weekStart - 1 + it) % 7 + 1) } }

 Text(stringResource(if (kind == Kind.WORK) R.string.work_schedule else R.string.exercise_schedule), style = MaterialTheme.typography.headlineMedium)

 Panel(Modifier.fillMaxWidth()) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   TimeButton(R.string.start_time, quickStart, p) { quickStart = it; quickError = (quickStart == quickEnd); appliedNotice = false }
   TimeButton(R.string.end_time, quickEnd, p) { quickEnd = it; quickError = (quickStart == quickEnd); appliedNotice = false }
  }
  if (quickError) {
   Text(stringResource(R.string.same_time_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
  DaySelectorChips(weekdays, quickDays, p.region, locale) { quickDays = it; appliedNotice = false }
  FilledTonalButton(
   onClick = {
    if (quickStart == quickEnd) {
     quickError = true
    } else {
     quickError = false
     val slot = TimeSlot(quickStart, quickEnd)
     val nextRules = rules.map { rule ->
      if (quickDays and (1 shl (rule.day - 1)) != 0) rule.copy(slots = listOf(slot))
      else rule
     }
     rules = nextRules
     quickDays = 0
     appliedNotice = true
     onApply(nextRules)
    }
   },
   enabled = quickDays != 0,
   modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
  ) {
   Text(stringResource(R.string.apply_selected_days))
  }
  AnimatedVisibility(appliedNotice, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   Text(
    stringResource(R.string.applied_to_draft),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.secondary
   )
  }
 }

 Panel(Modifier.fillMaxWidth()) {
  for ((idx, weekday) in weekdays.withIndex()) {
   if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
   val day = weekday.value
   val r = rules.first { it.day == day }
   fun update(new: DayRule) { rules = rules.map { if (it.day == day) new else it } }
   Column(Modifier.animateContentSize(motionSpec(reduced), alignment = Alignment.TopStart)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Column(
      Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
       .clickable { selected = if (selected == day) 0 else day },
      verticalArrangement = Arrangement.Center
     ) {
      Text(weekday.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.titleMedium)
      val sub = if (r.slots.isEmpty()) stringResource(R.string.disabled)
      else r.slots.joinToString(" · ") { "${minutesText(it.start, p, locale)} – ${minutesText(it.end, p, locale)}" }
      Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     Switch(
      checked = r.slots.isNotEmpty(),
      onCheckedChange = { v ->
       update(r.copy(slots = if (v) listOf(defaultSlot) else emptyList(), breaks = emptyList()))
       if (v) selected = day
      },
      modifier = Modifier.semantics { contentDescription = weekday.getDisplayName(TextStyle.FULL, locale) }
     )
    }
    if (selected == day && r.slots.isNotEmpty()) {
     Column(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      for ((index, slot) in r.slots.withIndex()) {
       SlotEditor(
        slot,
        p,
        { v -> update(r.copy(slots = r.slots.toMutableList().also { it[index] = v })) },
        { update(r.copy(slots = r.slots.filterIndexed { i, _ -> i != index })) }
       )
      }
      TextButton(enabled = r.slots.size < 12, onClick = { update(r.copy(slots = r.slots + TimeSlot(1080, 1200))) }) {
       Text(stringResource(R.string.add_interval))
      }
      if (kind == Kind.WORK) {
       if (r.breaks.isNotEmpty()) Text(stringResource(R.string.breaks), style = MaterialTheme.typography.labelLarge)
       for ((index, slot) in r.breaks.withIndex()) {
        SlotEditor(
         slot,
         p,
         { v -> update(r.copy(breaks = r.breaks.toMutableList().also { it[index] = v })) },
         { update(r.copy(breaks = r.breaks.filterIndexed { i, _ -> i != index })) }
        )
       }
       TextButton(enabled = r.breaks.size < 12, onClick = { update(r.copy(breaks = r.breaks + TimeSlot(720, 780))) }) {
        Text(stringResource(R.string.add_break))
       }
      }
     }
    }
   }
  }
 }

 SaveButton { onSave(rules) }
}

@Composable
private fun SlotEditor(slot: TimeSlot, p: Preferences, onChange: (TimeSlot) -> Unit, onDelete: () -> Unit) {
 var error by remember { mutableStateOf(false) }
 FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
  TimeButton(R.string.start_time, slot.start, p) { v -> if (v != slot.end) { error = false; onChange(slot.copy(start = v)) } else error = true }
  TimeButton(R.string.end_time, slot.end, p) { v -> if (v != slot.start) { error = false; onChange(slot.copy(end = v)) } else error = true }
  IconButton(onClick = onDelete, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
   AppIcon(Icons.Rounded.DeleteOutline, R.string.delete)
  }
 }
 if (error) Text(stringResource(R.string.same_time_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun OverrideEditor(config: Configuration, vm: AppViewModel, submit: (() -> Unit) -> Unit) {
 OverrideEditorContent(
  config = config,
  onSaveOverride = { override ->
   submit {
    vm.update { c ->
     val filtered = c.overrides.filter { it.date != override.date }
     val isNoOp = override.work == null && override.sleepEnabled == null && override.exercise == null
     c.copy(overrides = if (isNoOp) filtered else filtered + override)
    }
   }
  },
  onDeleteOverride = { dateStr ->
   vm.update { it.copy(overrides = it.overrides.filter { v -> v.date != dateStr }) }
  }
 )
}

@Composable
internal fun OverrideEditorContent(
 config: Configuration,
 onSaveOverride: (DateOverride) -> Unit,
 onDeleteOverride: (String) -> Unit
) {
 val p = config.prefs
 val reduced = LocalReducedMotion.current
 val locale = LocalConfiguration.current.locales[0]
 val today = remember(p.zoneId) { LocalDate.now(p.zone()) }
 val tomorrow = remember(today) { today.plusDays(1) }
 var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
 val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
 val dateStr = selectedDate.toString()
 val existing = config.overrides.firstOrNull { it.date == dateStr }

 var workMode by remember(dateStr, existing) {
  mutableIntStateOf(if (existing?.work == null) 0 else if (existing.work.orEmpty().isEmpty()) 1 else 2)
 }
 var shift by remember(dateStr, existing) { mutableStateOf(existing?.work?.firstOrNull() ?: TimeSlot(540, 1020)) }
 var sleepMode by remember(dateStr, existing) {
  mutableIntStateOf(when (existing?.sleepEnabled) { null -> 0; false -> 1; true -> 2 })
 }
 var bed by remember(dateStr, existing) { mutableIntStateOf(existing?.bedtime ?: p.baseBedtime) }
 var wake by remember(dateStr, existing) { mutableIntStateOf(existing?.wake ?: p.baseWake) }
 var exerciseMode by remember(dateStr, existing) {
  mutableIntStateOf(if (existing?.exercise == null) 0 else if (existing.exercise.orEmpty().isEmpty()) 1 else 2)
 }
 var exerciseSlot by remember(dateStr, existing) { mutableStateOf(existing?.exercise?.firstOrNull() ?: TimeSlot(1080, 1140)) }
 var showDatePicker by rememberSaveable { mutableStateOf(false) }

 Text(stringResource(R.string.overrides), style = MaterialTheme.typography.headlineMedium)
 Text(stringResource(R.string.override_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

 FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
  FilterChip(
   selected = selectedDate == today,
   onClick = { selectedEpochDay = today.toEpochDay() },
   label = { Text(stringResource(R.string.today)) }
  )
  FilterChip(
   selected = selectedDate == tomorrow,
   onClick = { selectedEpochDay = tomorrow.toEpochDay() },
   label = { Text(stringResource(R.string.tomorrow)) }
  )
  OutlinedButton(
   onClick = { showDatePicker = true },
   shape = RoundedCornerShape(16.dp),
   modifier = Modifier.heightIn(min = 48.dp)
  ) {
   AppIcon(Icons.Rounded.DateRange, R.string.choose_date, modifier = Modifier.size(18.dp))
   Spacer(Modifier.width(8.dp))
   Text(dateText(selectedDate, p, locale), maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
 }

 Panel(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced))) {
  OverrideModeRow(R.string.work, workMode) { workMode = it }
  if (workMode == 2) SlotEditor(shift, p, { shift = it }, { workMode = 0 })

  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

  OverrideModeRow(R.string.bedtime, sleepMode) { sleepMode = it }
  if (sleepMode == 2) {
   FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    TimeButton(R.string.bedtime, bed, p) { bed = it }
    TimeButton(R.string.wake_time, wake, p) { wake = it }
   }
  }

  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

  OverrideModeRow(R.string.exercise, exerciseMode) { exerciseMode = it }
  if (exerciseMode == 2) SlotEditor(exerciseSlot, p, { exerciseSlot = it }, { exerciseMode = 0 })
 }

 SaveButton {
  onSaveOverride(
   DateOverride(
    date = dateStr,
    work = when (workMode) { 1 -> emptyList(); 2 -> listOf(shift); else -> null },
    sleepEnabled = when (sleepMode) { 1 -> false; 2 -> true; else -> null },
    bedtime = if (sleepMode == 2) bed else null,
    wake = if (sleepMode == 2) wake else null,
    exercise = when (exerciseMode) { 1 -> emptyList(); 2 -> listOf(exerciseSlot); else -> null }
   )
  )
 }

 if (config.overrides.isNotEmpty()) {
  Panel(Modifier.fillMaxWidth()) {
   val sorted = config.overrides.sortedBy { it.date }
   for ((idx, o) in sorted.withIndex()) key(o.date) {
    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val oDate = LocalDate.parse(o.date)
    val summary = overrideSummary(o, p, locale)
    Row(
     Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedEpochDay = oDate.toEpochDay() }.padding(vertical = 4.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Column(Modifier.weight(1f)) {
      Text(dateText(oDate, p, locale), style = MaterialTheme.typography.titleSmall)
      if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     IconButton(onClick = { onDeleteOverride(o.date) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
      AppIcon(Icons.Rounded.DeleteOutline, R.string.delete)
     }
    }
   }
  }
 }

 if (showDatePicker) {
  if (isJalali(p, locale)) {
   JalaliDatePickerDialog(
    initialDate = selectedDate,
    p = p,
    locale = locale,
    highlightWeek = false,
    todayActionLabel = R.string.today,
    onSelect = { selectedEpochDay = it.toEpochDay() },
    onDismiss = { showDatePicker = false }
   )
  } else {
   val size = LocalWindowInfo.current.containerSize
   val compact = with(LocalDensity.current) { size.height.toDp() < 560.dp || size.width.toDp() < 360.dp }
   val years = DatePickerDefaults.YearRange
   val pickerState = rememberDatePickerState(
    initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    yearRange = minOf(years.first, selectedDate.year)..maxOf(years.last, selectedDate.year),
    initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker
   )
   DatePickerDialog(
    onDismissRequest = { showDatePicker = false },
    confirmButton = {
     TextButton(
      onClick = {
       pickerState.selectedDateMillis?.let { millis ->
        selectedEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
       }
       showDatePicker = false
      },
      enabled = pickerState.selectedDateMillis != null
     ) { Text(stringResource(R.string.done)) }
    },
    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
   ) {
    DatePicker(state = pickerState, modifier = Modifier.verticalScroll(rememberScrollState()))
   }
  }
 }
}

@Composable
private fun OverrideModeRow(label: Int, mode: Int, onChange: (Int) -> Unit) {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
  val options = listOf(R.string.override_normal, R.string.override_off, R.string.override_custom)
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
   options.forEachIndexed { index, titleRes ->
    SegmentedButton(
     selected = mode == index,
     onClick = { onChange(index) },
     shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
    ) {
     Text(stringResource(titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
   }
  }
 }
}

@Composable
private fun overrideSummary(o: DateOverride, p: Preferences, locale: Locale): String = buildList {
 when {
  o.work != null && o.work.orEmpty().isEmpty() -> add(stringResource(R.string.day_off))
  !o.work.isNullOrEmpty() -> add("${stringResource(R.string.work)} ${minutesText(o.work!!.first().start, p, locale)}")
 }
 when (o.sleepEnabled) {
  false -> add(stringResource(R.string.sleep_disabled))
  true -> o.bedtime?.let { add("${stringResource(R.string.bedtime)} ${minutesText(it, p, locale)}") }
  null -> {}
 }
 when {
  o.exercise != null && o.exercise.orEmpty().isEmpty() -> add(stringResource(R.string.remove_exercise))
  !o.exercise.isNullOrEmpty() -> add("${stringResource(R.string.exercise)} ${minutesText(o.exercise!!.first().start, p, locale)}")
 }
}.joinToString(" · ")

@Composable
private fun SnoozeEditor(p: Preferences, onSnooze: (Int) -> Unit) {
 var custom by remember { mutableIntStateOf(p.snoozeMinutes) }
 Text(stringResource(R.string.snooze), style = MaterialTheme.typography.headlineMedium)
 for (minutes in listOf(5, 10, 15)) {
  FilledTonalButton(onClick = { onSnooze(minutes) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
   Text(stringResource(R.string.snooze_duration, number(minutes, p, LocalConfiguration.current.locales[0])))
  }
 }
 NumberInput(R.string.custom_minutes, custom, 1..180) { custom = it }
 SaveButton { onSnooze(custom) }
}
