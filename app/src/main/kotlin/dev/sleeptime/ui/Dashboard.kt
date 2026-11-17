package dev.sleeptime.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.sleeptime.R
import dev.sleeptime.core.*
import java.time.*
import java.time.format.TextStyle
import kotlin.math.absoluteValue

@Composable fun Dashboard(s: DashboardState, vm: AppViewModel, onSettings: ()->Unit, onStats: ()->Unit, onEditor: (String)->Unit, onConfirmed: (String)->Unit) {
 val context=LocalContext.current; val p=s.config.prefs; val locale=LocalConfiguration.current.locales[0]
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  item {
   Row(Modifier.widthIn(max=780.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) { Text(dateText(s.now.atZone(p.zone()).toLocalDate(),p,locale),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.dashboard),style=MaterialTheme.typography.titleLarge) }
    IconButton(onClick=onStats) { AppIcon(Icons.Rounded.Insights,R.string.statistics) }
    IconButton(onClick=onSettings) { AppIcon(Icons.Rounded.Tune,R.string.settings) }
   }
  }
  item { BedtimeHero(s, { vm.confirm(onConfirmed) }, { onEditor("sleep") }, { onEditor("snooze") }) }
  item {
   BoxWithConstraints(Modifier.widthIn(max=780.dp).fillMaxWidth()) {
    val wide=maxWidth>=500.dp
    val cards: @Composable (Modifier)->Unit = { mod -> WorkCard(s,mod) }
    if(wide) Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) { cards(Modifier.weight(1f)); FreeCard(s,Modifier.weight(1f)) }
    else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) { cards(Modifier.fillMaxWidth()); FreeCard(s,Modifier.fillMaxWidth()) }
   }
  }
  item { WeeklyCard(s.week,s.days,p,Modifier.widthIn(max=780.dp).fillMaxWidth(),onStats) }
  item { RoutineCard(s,vm,onEditor) }
  if(!p.notifications && s.config.sleep.any { it.enabled }) item { TextButton(onClick=onSettings) { Text(stringResource(R.string.notifications_hint),style=MaterialTheme.typography.bodySmall) } }
  item { Spacer(Modifier.height(20.dp)) }
 }
}
@Composable fun BedtimeHero(s: DashboardState,onConfirm: ()->Unit,onSetup: ()->Unit,onSnooze: ()->Unit) {
 val p=s.config.prefs; val context=LocalContext.current; val locale=LocalConfiguration.current.locales[0]
 val occurrence=s.occurrence
 val snoozed=s.activeSession?.takeIf {it.key==occurrence?.key && !it.completed}?.snoozedUntil?.let {Instant.ofEpochMilli(it)}?.takeIf {it>s.now}
 val seconds=(snoozed?:occurrence?.start)?.let {Duration.between(s.now,it).seconds}?:0L
 val overdue=seconds<0
 val reduced=LocalReducedMotion.current
 val haptic=LocalHapticFeedback.current
 val progress by animateFloatAsState(if(overdue || s.confirmed) 1f else (1f-seconds.coerceAtLeast(0)/86400f).coerceIn(0f,1f),animationSpec=if(reduced) snap() else tween(900),label="bedtime-progress")
 Surface(Modifier.widthIn(max=780.dp).fillMaxWidth(),shape=RoundedCornerShape(36.dp),color=Color(0xFF252441)) {
  Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF302B50),Color(0xFF1C263F))))) {
   Canvas(Modifier.matchParentSize()) {
    val center=Offset(size.width*0.87f,size.height*0.05f)
    for(i in 1..4) drawCircle(Color(0xFFB9A8F8).copy(alpha=0.035f),size.width*(0.17f+i*0.13f),center,style=Stroke(1.dp.toPx()))
    listOf(.12f to .15f,.65f to .22f,.84f to .40f,.44f to .10f,.93f to .67f).forEach { (x,y)->drawCircle(Color(0xFFC3B6EE).copy(alpha=.3f),1.4.dp.toPx(),Offset(size.width*x,size.height*y)) }
   }
   Column(Modifier.padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
     Surface(shape=CircleShape,color=Color.White.copy(alpha=.07f)) { Icon(if(s.confirmed) Icons.Rounded.Check else Icons.Rounded.DarkMode,null,Modifier.padding(12.dp).size(24.dp),tint=SleepColor) }
     Spacer(Modifier.weight(1f))
     Surface(shape=CircleShape,color=Color.White.copy(alpha=.07f)) { Text(if(snoozed!=null && !s.confirmed)stringResource(R.string.snoozed_until,clockText(snoozed,p,locale))else stringResource(if(s.confirmed) R.string.sleep_confirmed else if(overdue) R.string.bedtime_overdue else R.string.bedtime_countdown),Modifier.padding(horizontal=13.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium,color=Color(0xFFE1D8FF)) }
    }
    if(occurrence == null) {
     Text(stringResource(R.string.unconfigured_sleep),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,color=Color.White)
     Text(stringResource(R.string.setup_sleep_hint),color=Color(0xFFC3BBD6),textAlign=TextAlign.Center)
     Button(onClick=onSetup,colors=ButtonDefaults.buttonColors(containerColor=SleepColor,contentColor=Color(0xFF211B38)),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text(stringResource(R.string.setup_sleep)) }
    } else if(s.confirmed) {
     Icon(Icons.Rounded.Nightlight,null,Modifier.size(60.dp),tint=SleepColor)
     Text(stringResource(R.string.rest_well),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,color=Color.White)
     val record=s.history.firstOrNull { it.key==occurrence.key }
     if(record != null) Text(stringResource(R.string.reported,clockText(Instant.ofEpochMilli(record.confirmed),p,locale)),color=Color(0xFFD1C6EC))
    } else {
     val value=seconds.absoluteValue
     val text=number("%02d:%02d:%02d".format(java.util.Locale.ROOT,value/3600,value/60%60,value%60),p)
     BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
      val fontScale=LocalDensity.current.fontScale
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
       Text(text,style=MaterialTheme.typography.displayLarge.copy(fontSize=(maxWidth.value/(text.length*.65f)/fontScale).coerceIn(22f,76f).sp,letterSpacing=(-1).sp),color=Color.White,maxLines=1)
      }
     }
     Text(stringResource(R.string.tonight,clockText(occurrence.start,p,locale)),style=MaterialTheme.typography.bodyMedium,color=Color(0xFFC9C2DF))
     LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),color=SleepColor,trackColor=Color.White.copy(alpha=.08f),drawStopIndicator={})
     Button(onClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);onConfirm()},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD4C5FF),contentColor=Color(0xFF251B40)),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=58.dp)) {
      Icon(Icons.Rounded.Bedtime,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.go_to_sleep))
     }
     TextButton(onClick=onSnooze,colors=ButtonDefaults.textButtonColors(contentColor=Color(0xFFD6CDEE))) { Icon(Icons.Rounded.Snooze,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.snooze)) }
    }
   }
  }
 }
}
@Composable private fun WorkCard(s: DashboardState,modifier: Modifier) {
 val p=s.config.prefs; val context=LocalContext.current; val locale=LocalConfiguration.current.locales[0]; val work=s.nextWork
 val active=work!=null && work.start<=s.now
 Panel(modifier) {
  Row(verticalAlignment=Alignment.CenterVertically) { AppIcon(Icons.Rounded.WorkOutline,null,WorkColor); Spacer(Modifier.width(9.dp)); Eyebrow(stringResource(if(active) R.string.work_remaining else R.string.work)) }
  if(work==null) Text(stringResource(R.string.day_off),style=MaterialTheme.typography.headlineMedium) else {
   Text(if(active) duration(context,Duration.between(s.now,work.end).seconds,p) else stringResource(R.string.day_off),style=MaterialTheme.typography.headlineMedium)
   Text(stringResource(if(active) R.string.shift_ends else R.string.next_shift,if(active) clockText(work.end,p,locale) else dateText(work.start.atZone(p.zone()).toLocalDate(),p,locale,true)+" · "+clockText(work.start,p,locale)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
@Composable private fun FreeCard(s: DashboardState,modifier: Modifier) {
 val context=LocalContext.current;val p=s.config.prefs
 Panel(modifier) {
  Row(verticalAlignment=Alignment.CenterVertically) { AppIcon(Icons.Rounded.Spa,null,FreeColor); Spacer(Modifier.width(9.dp)); Eyebrow(stringResource(R.string.free_today)) }
  Text(duration(context,s.today.remainingFree.seconds,p),style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.secondary)
  Text(stringResource(R.string.total_today,duration(context,s.today.free.seconds,p)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
@Composable fun WeeklyCard(budget: TimeBudget,days: List<Pair<LocalDate,TimeBudget>>,p: Preferences,modifier: Modifier=Modifier,onMore: (() -> Unit)?=null) {
 val context=LocalContext.current;val locale=LocalConfiguration.current.locales[0]
 var selected by remember { mutableStateOf<Kind?>(null) }; var showDetail by remember { mutableStateOf(false) }
 var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
 Panel(modifier) {
  SectionLabel(stringResource(R.string.week_title),if(onMore!=null) { { IconButton(onClick=onMore) { AppIcon(Icons.AutoMirrored.Rounded.ArrowForward,R.string.statistics) } } } else null)
  Text(dateText(budget.start.atZone(p.zone()).toLocalDate(),p,locale,true)+" — "+dateText(budget.end.atZone(p.zone()).toLocalDate().minusDays(1),p,locale,true),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Text(duration(context,budget.free.seconds,p),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.secondary)
  Text(stringResource(R.string.remaining_week,duration(context,budget.remainingFree.seconds,p)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth().height(20.dp).clip(CircleShape),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
   for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) {
    val seconds=if(kind==null) budget.free.seconds else budget.breakdown[kind]?.seconds?:0
    val segmentTitle=stringResource(kindTitle(kind))
    if(seconds>0) Box(Modifier.weight(seconds.toFloat()).fillMaxHeight().background(kindColor(kind)).clickable {selected=kind;showDetail=true}.semantics { contentDescription=segmentTitle+": "+duration(context,seconds,p) })
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) Column(Modifier.weight(1f).clickable { selected=kind;showDetail=true }.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(kindColor(kind))); Spacer(Modifier.height(6.dp)); Text(stringResource(kindTitle(kind)),style=MaterialTheme.typography.labelSmall)
   }
  }
  Row(Modifier.fillMaxWidth().height(92.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
   for((date,day) in days) Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable {selectedDay=date},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
    val fraction=(day.free.seconds.toFloat()/Duration.between(day.start,day.end).seconds).coerceIn(.02f,1f)
    Box(Modifier.fillMaxWidth(.65f).height((fraction*56+4).dp).clip(RoundedCornerShape(7.dp)).background(if(date==LocalDate.now(p.zone())) FreeColor else FreeColor.copy(alpha=.42f)))
    Spacer(Modifier.height(10.dp)); Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW,locale),style=MaterialTheme.typography.labelMedium)
   }
  }
  if(budget.uncertainDates.isNotEmpty()) Text(stringResource(R.string.holiday_unknown),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 if(showDetail) AlertDialog(onDismissRequest={showDetail=false},title={Text(stringResource(kindTitle(selected)))},text={Column { Text(duration(context,if(selected==null) budget.free.seconds else budget.breakdown[selected]?.seconds?:0,p),style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.chart_note)) }},confirmButton={TextButton(onClick={showDetail=false}) {Text(stringResource(R.string.done))}})
 selectedDay?.let { date -> val day=days.first {it.first==date}.second; AlertDialog(onDismissRequest={selectedDay=null},title={Text(dateText(date,p,locale))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){ for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) Text(stringResource(kindTitle(kind))+" · "+duration(context,if(kind==null)day.free.seconds else day.breakdown[kind]?.seconds?:0,p)) }},confirmButton={TextButton(onClick={selectedDay=null}){Text(stringResource(R.string.done))}}) }
}
@Composable private fun RoutineCard(s: DashboardState,vm: AppViewModel,onEditor: (String)->Unit) {
 val context=LocalContext.current;val p=s.config.prefs
 var expanded by rememberSaveable { mutableStateOf(true) }
 val completed=s.routine.filter {it.ownerDate==s.routineOwner.toString()}.map {it.itemId}.toSet()
 val active=s.occurrence?.let {s.now >= it.start.minusSeconds(p.routineLeadMinutes*60L)} ?: true
 Panel(Modifier.widthIn(max=780.dp).fillMaxWidth()) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f).clickable {expanded=!expanded}) {Text(stringResource(R.string.routine),style=MaterialTheme.typography.titleLarge); Text(stringResource(R.string.routine_progress,number(completed.size,p),number(s.config.routine.size,p)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   IconButton(onClick={onEditor("routine")}) {AppIcon(Icons.Rounded.Edit,R.string.manage_routine)}
   IconButton(onClick={expanded=!expanded}) {AppIcon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,R.string.routine)}
  }
  if(!active) Text(stringResource(R.string.routine_active_at,number(p.routineLeadMinutes,p)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  AnimatedVisibility(expanded,enter=if(LocalReducedMotion.current) EnterTransition.None else expandVertically()+fadeIn(),exit=if(LocalReducedMotion.current) ExitTransition.None else shrinkVertically()+fadeOut()) {
   Column {
    for(item in s.config.routine) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled=active){vm.routineToggle(item,s.routineOwner,item.id !in completed)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
     Checkbox(checked=item.id in completed,onCheckedChange={vm.routineToggle(item,s.routineOwner,it)},enabled=active)
     Text(routineText(context,item),Modifier.weight(1f),color=if(item.id in completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
    if(s.config.routine.isEmpty()) Text(stringResource(R.string.routine_empty),style=MaterialTheme.typography.bodyMedium)
   }
  }
 }
}
