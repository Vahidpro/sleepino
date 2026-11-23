@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.sleeptime.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sleeptime.R
import dev.sleeptime.core.*
import java.time.*
import java.time.format.TextStyle
import java.util.UUID
import kotlinx.coroutines.launch

@Composable fun EditorSheet(route: String, s: DashboardState, vm: AppViewModel, dismiss: ()->Unit) = key(route) {
 val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
 val scope=rememberCoroutineScope()
 val reduced=LocalReducedMotion.current
 var submitting by remember { mutableStateOf(false) }
 fun submit(action: ()->Unit) {
  if(submitting) return
  submitting=true
  action()
  if(reduced) dismiss() else scope.launch {
   sheetState.hide()
   dismiss()
  }
 }
 ModalBottomSheet(
  onDismissRequest=dismiss,
  sheetState=sheetState,
  sheetGesturesEnabled=!submitting,
  properties=ModalBottomSheetProperties(shouldDismissOnBackPress=!submitting,shouldDismissOnClickOutside=!submitting),
  containerColor=MaterialTheme.colorScheme.background
 ) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=22.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   when(route) {
    "sleep" -> SleepEditor(s.config) { rules -> submit { vm.update { it.copy(sleep=rules) } } }
    "work" -> ActivityEditor(s.config,Kind.WORK,vm,::submit)
    "exercise" -> ActivityEditor(s.config,Kind.EXERCISE,vm,::submit)
    "stages" -> StagesEditor(s.config,vm,::submit)
    "routine" -> RoutineEditor(s.config,vm,::submit)
    "overrides" -> OverrideEditor(s.config,vm,::submit)
    "snooze" -> SnoozeEditor(s.config.prefs) { minutes -> submit { vm.snooze(minutes) } }
   }
  }
 }
}
@Composable fun TimeButton(label: Int, minutes: Int, p: Preferences, onChange: (Int) -> Unit) {
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

@Composable fun NumberInput(label: Int,value: Int,range: IntRange,onChange: (Int)->Unit) {
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
@Composable fun SwitchRow(title: Int,checked: Boolean,onChecked: (Boolean)->Unit,description: Int?=null) {
 Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).toggleable(value=checked,role=Role.Switch,onValueChange=onChecked).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f).padding(end=14.dp)) {Text(stringResource(title),style=MaterialTheme.typography.bodyLarge);description?.let {Text(stringResource(it),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  Switch(checked=checked,onCheckedChange=null)
 }
}
@Composable fun SaveButton(onClick: ()->Unit) {
 val interactions=remember { MutableInteractionSource() }
 Button(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).pressScale(interactions),shape=RoundedCornerShape(18.dp),interactionSource=interactions){Text(stringResource(R.string.save))}
}
@Composable internal fun SleepEditor(config: Configuration, onSave: (List<SleepRule>) -> Unit) {
 val p = config.prefs
 val locale = LocalConfiguration.current.locales[0]
 var rules by remember { mutableStateOf((1..7).map { day -> config.sleep.firstOrNull { it.day == day } ?: SleepRule(day, enabled = false) }) }
 val initialRule = rules.firstOrNull { it.enabled } ?: rules.first()
 var sharedBedtime by rememberSaveable { mutableIntStateOf(initialRule.bedtime) }
 var sharedWake by rememberSaveable { mutableIntStateOf(initialRule.wake) }
 var sharedDays by rememberSaveable { mutableIntStateOf(0) }
 var individualDays by rememberSaveable { mutableStateOf(false) }
 var selected by rememberSaveable { mutableIntStateOf(0) }
 val weekdays = remember(p.weekStart) { List(7) { DayOfWeek.of((p.weekStart - 1 + it) % 7 + 1) } }

 Text(stringResource(R.string.sleep_schedule), style = MaterialTheme.typography.headlineMedium)
 Text(stringResource(R.string.sleep_ownership), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
 Panel(Modifier.fillMaxWidth()) {
  Text(stringResource(R.string.same_sleep_schedule), style = MaterialTheme.typography.titleLarge)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   TimeButton(R.string.bedtime, sharedBedtime, p) { sharedBedtime = it }
   TimeButton(R.string.wake_time, sharedWake, p) { sharedWake = it }
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   for (day in weekdays) {
    val mask = 1 shl (day.value - 1)
    FilterChip(
     selected = sharedDays and mask != 0,
     onClick = { sharedDays = sharedDays xor mask },
     label = { Text(day.getDisplayName(TextStyle.FULL, locale)) }
    )
   }
  }
  Button(
   onClick = {
    onSave(rules.map { rule ->
     if (sharedDays and (1 shl (rule.day - 1)) != 0) {
      rule.copy(enabled = true, bedtime = sharedBedtime, wake = sharedWake, useWakeTime = true)
     } else rule
    })
   },
   enabled = sharedDays != 0,
   modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
  ) {
   Text(stringResource(R.string.apply_selected_days))
  }
 }

 OutlinedButton(
  onClick = { individualDays = !individualDays },
  modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
 ) {
  Text(stringResource(R.string.edit_each_day), Modifier.weight(1f))
  Icon(if (individualDays) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
 }
 if (individualDays) {
  for (weekday in weekdays) {
   val day = weekday.value
   val rule = rules[day - 1]
   fun update(newRule: SleepRule) { rules = rules.map { if (it.day == day) newRule else it } }
   Panel(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Column(
      Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
       .clickable { selected = if (selected == day) 0 else day },
      verticalArrangement = Arrangement.Center
     ) {
      Text(weekday.getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.titleMedium)
      if (rule.enabled && selected != day) {
       Text(minutesText(rule.bedtime, p, locale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else if (!rule.enabled && p.baseSleep && selected != day) {
       Text(stringResource(R.string.base_bedtime_hint, minutesText(p.baseBedtime, p, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
     Switch(
      checked = rule.enabled,
      onCheckedChange = { update(rule.copy(enabled = it)); selected = day },
      modifier = Modifier.semantics { contentDescription = weekday.getDisplayName(TextStyle.FULL, locale) }
     )
    }
    if (selected == day && !rule.enabled && p.baseSleep) {
     Text(stringResource(R.string.base_sleep_active_hint, minutesText(p.baseBedtime, p, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (selected == day && rule.enabled) {
     FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      TimeButton(R.string.bedtime, rule.bedtime, p) { update(rule.copy(bedtime = it)) }
      if (rule.useWakeTime) TimeButton(R.string.wake_time, rule.wake, p) { update(rule.copy(wake = it)) }
     }
     SwitchRow(R.string.target_duration, !rule.useWakeTime, { update(rule.copy(useWakeTime = !it)) })
     if (!rule.useWakeTime) NumberInput(R.string.sleep_duration, rule.durationMinutes, 30..1200) { update(rule.copy(durationMinutes = it)) }
     TextButton(onClick = { rules = rules.map { rule.copy(day = it.day) } }) { Text(stringResource(R.string.copy_to_all)) }
    }
   }
  }
  SaveButton { onSave(rules) }
 }
}
@Composable private fun ActivityEditor(config: Configuration,kind: Kind,vm: AppViewModel,submit: (()->Unit)->Unit) {
 val p=config.prefs;val locale=LocalConfiguration.current.locales[0]
 val reduced=LocalReducedMotion.current
 var rules by remember {mutableStateOf((1..7).map {d->(if(kind==Kind.WORK)config.work else config.exercise).firstOrNull {it.day==d}?:DayRule(d)})}
 var selected by remember {mutableIntStateOf(p.weekStart)}
 Text(stringResource(if(kind==Kind.WORK)R.string.work_schedule else R.string.exercise_schedule),style=MaterialTheme.typography.headlineMedium)
 for(offset in 0..6) {
  val day=(p.weekStart-1+offset)%7+1;val r=rules.first {it.day==day}
  fun update(new: DayRule) {rules=rules.map {if(it.day==day)new else it}}
  Panel(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced),alignment=Alignment.TopStart)) {
   Row(Modifier.fillMaxWidth().clickable {selected=if(selected==day)0 else day},verticalAlignment=Alignment.CenterVertically) {
    Text(DayOfWeek.of(day).getDisplayName(TextStyle.FULL,locale),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
    Switch(checked=r.slots.isNotEmpty(),onCheckedChange={v->update(r.copy(slots=if(v)listOf(if(kind==Kind.WORK)TimeSlot(540,1080)else TimeSlot(1080,1140))else emptyList(),breaks=emptyList()));selected=day})
   }
   if(selected==day && r.slots.isNotEmpty()) {
    for((index,slot)in r.slots.withIndex()) SlotEditor(slot,p,{v->update(r.copy(slots=r.slots.toMutableList().also {it[index]=v}))},{update(r.copy(slots=r.slots.filterIndexed {i,_->i!=index}))})
    TextButton(enabled=r.slots.size<12,onClick={update(r.copy(slots=r.slots+TimeSlot(1080,1200)))}){Text(stringResource(R.string.add_interval))}
    if(kind==Kind.WORK) {
     if(r.breaks.isNotEmpty()) Text(stringResource(R.string.breaks),style=MaterialTheme.typography.labelLarge)
     for((index,slot)in r.breaks.withIndex()) SlotEditor(slot,p,{v->update(r.copy(breaks=r.breaks.toMutableList().also {it[index]=v}))},{update(r.copy(breaks=r.breaks.filterIndexed {i,_->i!=index}))})
     TextButton(enabled=r.breaks.size<12,onClick={update(r.copy(breaks=r.breaks+TimeSlot(720,780)))}){Text(stringResource(R.string.add_break))}
    }
    TextButton(onClick={rules=rules.map {r.copy(day=it.day)}}){Text(stringResource(R.string.copy_to_all))}
   } else if(r.slots.isNotEmpty()) Text(r.slots.joinToString(" · "){minutesText(it.start,p,locale)+" – "+minutesText(it.end,p,locale)},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
 SaveButton {
  val savedRules=rules
  submit {vm.update {if(kind==Kind.WORK)it.copy(work=savedRules)else it.copy(exercise=savedRules)}}
 }
}
@Composable private fun SlotEditor(slot: TimeSlot,p: Preferences,onChange: (TimeSlot)->Unit,onDelete: ()->Unit) {
 var error by remember {mutableStateOf(false)}
 FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
  TimeButton(R.string.start_time,slot.start,p){v->if(v!=slot.end){error=false;onChange(slot.copy(start=v))}else error=true}
  TimeButton(R.string.end_time,slot.end,p){v->if(v!=slot.start){error=false;onChange(slot.copy(end=v))}else error=true}
  IconButton(onClick=onDelete){AppIcon(Icons.Rounded.DeleteOutline,R.string.delete)}
 }
 if(error) Text(stringResource(R.string.same_time_invalid),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
}
@Composable private fun StagesEditor(config: Configuration,vm: AppViewModel,submit: (()->Unit)->Unit) {
 val reduced=LocalReducedMotion.current
 var stages by remember {mutableStateOf(config.reminders)}
 Text(stringResource(R.string.reminder_stages),style=MaterialTheme.typography.headlineMedium)
 Column(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced)),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  for(stage in stages) key(stage.id) {
   Panel(Modifier.fillMaxWidth()) {
    Row(verticalAlignment=Alignment.CenterVertically) {Text(stringResource(R.string.stage_label,number(stage.offsetMinutes,config.prefs,LocalConfiguration.current.locales[0])),Modifier.weight(1f));Switch(stage.enabled,{v->stages=stages.map {if(it.id==stage.id)it.copy(enabled=v)else it}});IconButton(onClick={stages=stages.filter {it.id!=stage.id}}){AppIcon(Icons.Rounded.DeleteOutline,R.string.delete)}}
    NumberInput(R.string.offset_minutes,stage.offsetMinutes,-720..720){v->stages=stages.map {if(it.id==stage.id)it.copy(offsetMinutes=v)else it}}
    OutlinedTextField(stage.text,{v->if(v.length<=500)stages=stages.map {if(it.id==stage.id)it.copy(text=v)else it}},label={Text(stringResource(R.string.custom_text))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
   }
  }
  OutlinedButton(enabled=stages.size<20,onClick={val id=(1..1000).first {d->stages.none {it.id==d}};stages=stages+ReminderStage(id,0)}){Text(stringResource(R.string.add_stage))}
 }
 SaveButton {
  val savedStages=stages
  submit {vm.update {it.copy(reminders=savedStages)}}
 }
}
@Composable private fun RoutineEditor(config: Configuration,vm: AppViewModel,submit: (()->Unit)->Unit) {
 val context=LocalContext.current
 val reduced=LocalReducedMotion.current
 var items by remember {mutableStateOf(config.routine)}
 var lead by remember {mutableIntStateOf(config.prefs.routineLeadMinutes)}
 Text(stringResource(R.string.manage_routine),style=MaterialTheme.typography.headlineMedium)
 NumberInput(R.string.routine_lead,lead,0..720){lead=it}
 LookaheadScope {
  Column(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced)),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   for((index,item)in items.withIndex()) key(item.id) {
    Panel(Modifier.fillMaxWidth().animateBounds(this@LookaheadScope,boundsTransform={_,_->motionSpec(reduced)})) {
     OutlinedTextField(routineText(context,item),{v->if(v.length<=160)items=items.map {if(it.id==item.id)it.copy(text=v,defaultKey=null)else it}},label={Text(stringResource(R.string.routine_item))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
     Row {
      IconButton(enabled=index>0,onClick={val copy=items.toMutableList();val old=copy[index-1];copy[index-1]=item;copy[index]=old;items=copy}){AppIcon(Icons.Rounded.ArrowUpward,R.string.move_up)}
      IconButton(enabled=index<items.lastIndex,onClick={val copy=items.toMutableList();val old=copy[index+1];copy[index+1]=item;copy[index]=old;items=copy}){AppIcon(Icons.Rounded.ArrowDownward,R.string.move_down)}
      Spacer(Modifier.weight(1f));IconButton(onClick={items=items.filter {it.id!=item.id}}){AppIcon(Icons.Rounded.DeleteOutline,R.string.delete)}
     }
    }
   }
   val newItemLabel=stringResource(R.string.routine_item)
   OutlinedButton(enabled=items.size<100,onClick={items=items+RoutineItem(UUID.randomUUID().toString(),newItemLabel)}){Text(stringResource(R.string.add))}
  }
 }
 SaveButton {
  val savedItems=items.filter {it.text.isNotBlank() || it.defaultKey!=null}
  val savedLead=lead
  submit {vm.update {it.copy(routine=savedItems,prefs=it.prefs.copy(routineLeadMinutes=savedLead))}}
 }
}
@Composable private fun OverrideEditor(config: Configuration,vm: AppViewModel,submit: (()->Unit)->Unit) {
 val p=config.prefs
 val reduced=LocalReducedMotion.current
 val locale = LocalConfiguration.current.locales[0]
 var date by remember {mutableStateOf(LocalDate.now(p.zone()).toString())}
 val existing=config.overrides.firstOrNull {it.date==date}
 var workMode by remember(date) {mutableIntStateOf(if(existing?.work==null)0 else if(existing.work.orEmpty().isEmpty())1 else 2)} // 0 recurring, 1 day off, 2 explicit work
 var shift by remember(date) {mutableStateOf(existing?.work?.firstOrNull()?:TimeSlot(540,1080))}
 var changeSleep by remember(date) {mutableStateOf(existing?.sleepEnabled==true)}
 var bed by remember(date) {mutableIntStateOf(existing?.bedtime?:1380)}
 var wake by remember(date) {mutableIntStateOf(existing?.wake?:420)}
 var exerciseMode by remember(date) {mutableIntStateOf(if(existing?.exercise==null)0 else if(existing.exercise.orEmpty().isEmpty())1 else 2)}
 var exerciseSlot by remember(date) {mutableStateOf(existing?.exercise?.firstOrNull()?:TimeSlot(1080,1140))}
 var error by remember {mutableStateOf(false)}
 val parsedPreview = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
 Text(stringResource(R.string.overrides),style=MaterialTheme.typography.headlineMedium)
 Text(stringResource(R.string.override_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 OutlinedTextField(
  number(date, p, locale), { date = it.asciiDigits(); error = false },
  label = { Text(stringResource(R.string.date_iso)) },
  supportingText = { parsedPreview?.let { Text(dateText(it, p, locale)) } },
  textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
  singleLine = true, isError = error, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
 )
 for((mode,title)in listOf(0 to R.string.remove_override,1 to R.string.personal_day_off,2 to R.string.work_this_day)) Row(Modifier.fillMaxWidth().clickable {workMode=mode},verticalAlignment=Alignment.CenterVertically){RadioButton(workMode==mode,{workMode=mode});Text(stringResource(title))}
 Column(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced)),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  if(workMode==2) SlotEditor(shift,p,{shift=it},{workMode=0})
  SwitchRow(R.string.change_bedtime,changeSleep,{changeSleep=it})
  if(changeSleep) FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){TimeButton(R.string.bedtime,bed,p){bed=it};TimeButton(R.string.wake_time,wake,p){wake=it}}
  ChoiceRow(R.string.exercise,exerciseMode,listOf(0 to stringResource(R.string.remove_override),1 to stringResource(R.string.remove_exercise),2 to stringResource(R.string.add_interval))){exerciseMode=it}
  if(exerciseMode==2)SlotEditor(exerciseSlot,p,{exerciseSlot=it},{exerciseMode=0})
 }
 Column(Modifier.fillMaxWidth().animateContentSize(motionSpec(reduced)),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  SaveButton {
   val parsed=runCatching {LocalDate.parse(date)}.getOrNull()
   if(parsed==null) error=true else {
    val override=DateOverride(parsed.toString(),work=when(workMode){1->emptyList();2->listOf(shift);else->null},sleepEnabled=if(changeSleep)true else null,bedtime=if(changeSleep)bed else null,wake=if(changeSleep)wake else null,exercise=when(exerciseMode){1->emptyList();2->listOf(exerciseSlot);else->null})
    submit {vm.update {c->c.copy(overrides=c.overrides.filter {it.date!=override.date}+override)}}
   }
  }
  if(config.overrides.isNotEmpty()) {
   HorizontalDivider()
   for(o in config.overrides.sortedBy {it.date}) key(o.date) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(dateText(LocalDate.parse(o.date),p,LocalConfiguration.current.locales[0]),Modifier.weight(1f));IconButton(onClick={vm.update {it.copy(overrides=it.overrides.filter {v->v.date!=o.date})}}){AppIcon(Icons.Rounded.DeleteOutline,R.string.delete)}}
   }
  }
 }
}
@Composable private fun SnoozeEditor(p: Preferences,onSnooze: (Int)->Unit) {
 var custom by remember {mutableIntStateOf(p.snoozeMinutes)}
 Text(stringResource(R.string.snooze),style=MaterialTheme.typography.headlineMedium)
 for(minutes in listOf(5,10,15)) FilledTonalButton(onClick={onSnooze(minutes)},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(stringResource(R.string.snooze_duration,number(minutes,p,LocalConfiguration.current.locales[0])))}
 NumberInput(R.string.custom_minutes,custom,1..180){custom=it}
 SaveButton {onSnooze(custom)}
}
