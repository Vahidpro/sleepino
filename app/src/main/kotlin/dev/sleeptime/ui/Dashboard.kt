package dev.sleeptime.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.sleeptime.R
import dev.sleeptime.core.*
import java.time.*
import java.time.format.TextStyle
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.PI

@Composable fun Dashboard(
 s: DashboardState,
 vm: AppViewModel,
 onSettings: () -> Unit = {},
 onEditor: (String) -> Unit,
 onConfirmed: (String) -> Unit
) {
 BedtimeHero(
  s,
  onConfirm = { vm.confirm(onConfirmed) },
  onSetup = { onEditor("sleep") },
  onSnooze = { onEditor("snooze") },
  onUndo = { s.occurrence?.let { vm.undo(it.key) } },
  onCancelSnooze = { s.occurrence?.let { vm.cancelSnooze(it.key) } },
  onSettings = onSettings
 )
}

@Composable fun BedtimeHero(
 s: DashboardState,
 onConfirm: () -> Unit,
 onSetup: () -> Unit,
 onSnooze: () -> Unit,
 onUndo: () -> Unit,
 onCancelSnooze: () -> Unit,
 onSettings: () -> Unit = {},
 initialShowSettings: Boolean = false
) {
 val p = s.config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val reduced = LocalReducedMotion.current
 var showSettings by rememberSaveable { mutableStateOf(initialShowSettings) }
 val localTime = s.now.atZone(p.zone())
 val localDate = localTime.toLocalDate()
 val date = remember(localDate, p, locale) { dateText(localDate, p, locale) }
 val sleep = s.occurrence
 val snoozed = s.activeSession
  ?.takeIf { it.key == sleep?.key && !it.completed && !s.confirmed }
  ?.snoozedUntil?.let(Instant::ofEpochMilli)?.takeIf { it > s.now }
 val pastBedtime = sleep != null && s.now >= sleep.start && !s.confirmed
 val night = localTime.hour !in 6..19
 val twilight = localTime.hour in 18..19
 val light = !night && !twilight && !pastBedtime
 val ink = if (light) Color(0xFF102B41) else Color.White
 val muted = if (light) Color(0xFF244961) else Color(0xFFE4ECFA)
 val sky = remember(night, twilight, pastBedtime) {
  Brush.verticalGradient(
   when {
    pastBedtime -> listOf(Color(0xFF963A4D), Color(0xFF541022))
    night -> listOf(Color(0xFF172B4D), Color(0xFF101B36), Color(0xFF0A1328))
    twilight -> listOf(Color(0xFF77475B), Color(0xFF402942), Color(0xFF302038))
    else -> listOf(Color(0xFF65B4E4), Color(0xFFBCE4F8), Color(0xFFDBF1FA))
   }
  )
 }
 val actionColor = when {
  pastBedtime -> Color(0xFFFFD9DD)
  light -> Color(0xFF153C58)
  else -> Color(0xFFD6E9FF)
 }
 val actionInk = if (light) Color.White else if (pastBedtime) Color(0xFF541022) else Color(0xFF152D48)
 val buttonColors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = actionInk)
 val workDay = s.today.workdays > 0

 BoxWithConstraints(
  Modifier
   .fillMaxSize()
   .background(sky)
   .pointerInput(Unit) {
    detectTapGestures {
     showSettings = !showSettings
    }
   },
  contentAlignment = Alignment.TopCenter
 ) {
  val compact = maxHeight < 450.dp
  val countdownSize = remember(compact) {
   TextAutoSize.StepBased(
    minFontSize = if (compact) 22.sp else 34.sp,
    maxFontSize = if (compact) 44.sp else 76.sp,
    stepSize = 2.sp
   )
  }
  Column(
   Modifier.widthIn(max = 760.dp).fillMaxSize()
    .padding(start = 18.dp, top = if (compact) 8.dp else 18.dp, end = 18.dp, bottom = 44.dp),
   horizontalAlignment = Alignment.CenterHorizontally
  ) {
   Box(
    Modifier
     .fillMaxWidth()
     .height(if (compact) 36.dp else 44.dp),
    contentAlignment = Alignment.Center
   ) {
    Text(
     date,
     modifier = Modifier.padding(horizontal = 48.dp),
     style = MaterialTheme.typography.labelLarge,
     color = muted,
     maxLines = 1,
     overflow = TextOverflow.Ellipsis
    )
    androidx.compose.animation.AnimatedVisibility(
     visible = showSettings,
     enter = if (reduced) EnterTransition.None else fadeIn(motionSpec(false)),
     exit = if (reduced) ExitTransition.None else fadeOut(motionSpec(false, 150)),
     modifier = Modifier.align(Alignment.CenterEnd)
    ) {
     IconButton(
      onClick = {
       showSettings = false
       onSettings()
      },
      modifier = Modifier.size(if (compact) 36.dp else 44.dp)
     ) {
      AppIcon(Icons.Rounded.Tune, R.string.settings, tint = ink)
     }
    }
   }
   SkyIllustration(
    night = night,
    twilight = twilight,
    overdue = pastBedtime,
    modifier = Modifier.fillMaxWidth().weight(1f)
   )
   Column(
    Modifier.widthIn(max = 560.dp).fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
   ) {
    when {
     sleep == null -> {
      Text(stringResource(R.string.unconfigured_sleep), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, color = ink)
      Button(onClick = onSetup, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) {
       Text(stringResource(R.string.setup_sleep))
      }
     }
     s.confirmed -> {
      Text(stringResource(R.string.rest_well), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, color = ink)
      FilledIconButton(
       onClick = onUndo,
       modifier = Modifier.size(48.dp),
       colors = IconButtonDefaults.filledIconButtonColors(containerColor = ink.copy(alpha = .12f), contentColor = ink)
      ) {
       Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo))
      }
     }
     else -> {
      val remaining = TimeEngine.remainingToBedtime(s.config, s.now, sleep, snoozed, s.holidays)
      val overdue = snoozed == null && pastBedtime
      val wholeMinutes = if (overdue) remaining.abs().toMinutes() else ceil(remaining.toMillis() / 60_000.0).toLong()
      Text(
       bedtimeDuration(wholeMinutes, p),
       modifier = Modifier.fillMaxWidth(),
       style = MaterialTheme.typography.displayLarge.copy(
        lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp, fontWeight = FontWeight.Black,
        lineBreak = LineBreak.Heading
       ),
       autoSize = countdownSize,
       maxLines = 2,
       textAlign = TextAlign.Center,
       color = ink
      )
      val caption = if (snoozed != null) {
       val until = remember(snoozed, p, locale) { clockText(snoozed, p, locale) }
       stringResource(R.string.snoozed_until, until)
      } else stringResource(if (overdue) R.string.bedtime_overdue else R.string.bedtime_countdown)
      Text(caption, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = muted)
      if (snoozed != null || s.now >= sleep.start.minusSeconds(30 * 60L)) {
       val haptic = LocalHapticFeedback.current
       val interaction = remember { MutableInteractionSource() }
       val contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
       Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
         onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onConfirm() },
         colors = buttonColors,
         shape = RoundedCornerShape(18.dp),
         interactionSource = interaction,
         contentPadding = contentPadding,
         modifier = Modifier.weight(1f).heightIn(min = 48.dp).pressScale(interaction)
        ) {
         Text(stringResource(R.string.go_to_sleep), textAlign = TextAlign.Center)
        }
        OutlinedButton(
         onClick = if (snoozed != null) onCancelSnooze else onSnooze,
         colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
         border = BorderStroke(1.dp, ink.copy(alpha = .40f)),
         shape = RoundedCornerShape(18.dp),
         contentPadding = contentPadding,
         modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
         Text(stringResource(if (snoozed != null) R.string.cancel_snooze else R.string.snooze), textAlign = TextAlign.Center)
        }
       }
      }
     }
    }
   }
   Spacer(Modifier.weight(if (compact) .12f else .22f))
   Row(
    Modifier.clip(CircleShape).background(ink.copy(alpha = .10f)).padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
   ) {
    Icon(if (workDay) Icons.Rounded.WorkOutline else Icons.Rounded.Spa, null, Modifier.size(14.dp), tint = muted)
    Text(stringResource(if (workDay) R.string.work_day else R.string.rest_day), style = MaterialTheme.typography.labelMedium, color = muted)
   }
  }
 }
}

@Composable private fun bedtimeDuration(totalMinutes: Long, p: Preferences): String {
 val locale = LocalConfiguration.current.locales[0]
 val hours = (totalMinutes / 60).toInt()
 val minutes = (totalMinutes % 60).toInt()
 if (hours == 0) return pluralStringResource(R.plurals.bedtime_minutes, minutes, number(minutes, p, locale))
 val hoursText = pluralStringResource(R.plurals.bedtime_hours, hours, number(hours, p, locale))
 if (minutes == 0) return hoursText
 val minutesText = pluralStringResource(R.plurals.bedtime_minutes, minutes, number(minutes, p, locale))
 return stringResource(R.string.bedtime_time_parts, hoursText, minutesText)
}

@Composable
fun SkyIllustration(
 night: Boolean,
 twilight: Boolean,
 overdue: Boolean,
 modifier: Modifier = Modifier
) {
 val reduced = LocalReducedMotion.current
 val transition = rememberInfiniteTransition(label = "sky-animation")

 val floatY by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = -5f,
  targetValue = 5f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "celestial-float"
 )

 val sunScale by if (reduced) remember { mutableFloatStateOf(1f) }
 else transition.animateFloat(
  initialValue = 0.95f,
  targetValue = 1.08f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "sun-scale"
 )

 val sunHaloAlpha by if (reduced) remember { mutableFloatStateOf(0.20f) }
 else transition.animateFloat(
  initialValue = 0.14f,
  targetValue = 0.28f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "sun-halo-alpha"
 )

 val moonScale by if (reduced) remember { mutableFloatStateOf(1f) }
 else transition.animateFloat(
  initialValue = 0.94f,
  targetValue = 1.06f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "moon-scale"
 )

 val starPhase by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4000, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "star-phase"
 )

 val cloudDrift1 by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = -18f,
  targetValue = 18f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 7500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "cloud-drift-1"
 )

 val cloudDrift2 by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = 14f,
  targetValue = -14f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 9500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "cloud-drift-2"
 )

 Box(modifier = modifier) {
  Canvas(modifier = Modifier.fillMaxSize()) {
   if (size.minDimension <= 0f) return@Canvas

   val orbit = Offset(
    size.width * .56f,
    size.height * (if (twilight) .66f else .51f) + floatY.dp.toPx()
   )
   val radius = minOf(88.dp.toPx(), size.width * .25f, size.height * .42f)

   if (night) {
    val haloRadius = radius * 1.85f * moonScale
    val halo = Brush.radialGradient(
     listOf(Color(if (overdue) 0x32FFC0BD else 0x3C3DC5FF), Color.Transparent),
     center = orbit,
     radius = haloRadius
    )
    drawCircle(halo, haloRadius, orbit)

    val dust = Path().apply {
     for (index in 1..64) {
      val point = Offset(
       size.width * ((index * 73) % 251) / 251f,
       size.height * ((index * index * 13 + index * 29) % 199) / 199f
      )
      if ((point - orbit).getDistance() > radius) {
       addOval(Rect(point, (.6f + index % 3 * .35f).dp.toPx()))
      }
     }
    }
    drawPath(dust, Color(0xFFB9D7FB).copy(alpha = .42f))

    val starPoints = listOf(
     .07f to .21f, .12f to .57f, .18f to .84f, .23f to .18f, .26f to .43f,
     .33f to .68f, .38f to .31f, .42f to .12f, .48f to .85f, .57f to .14f,
     .69f to .10f, .74f to .72f, .79f to .29f, .83f to .58f, .89f to .84f, .94f to .36f
    )
    for ((index, point) in starPoints.withIndex()) {
     val x = size.width * point.first
     val y = size.height * point.second
     val r = minOf((2 + index % 4).dp.toPx(), radius * .15f)
     val starPath = Path().apply {
      moveTo(x, y - r)
      lineTo(x + r * .28f, y - r * .28f)
      lineTo(x + r, y)
      lineTo(x + r * .28f, y + r * .28f)
      lineTo(x, y + r)
      lineTo(x - r * .28f, y + r * .28f)
      lineTo(x - r, y)
      lineTo(x - r * .28f, y - r * .28f)
      close()
     }
     val twinkle = if (reduced) 0.85f
     else (0.45f + 0.55f * ((sin((starPhase * 2 * PI + index * 1.4f)).toFloat() + 1f) / 2f))
     drawPath(starPath, Color(0xFFE5F2FF).copy(alpha = twinkle))
    }

    val nightCloud = Path().apply {
     val cx = size.width * .30f + cloudDrift1.dp.toPx()
     val cy = size.height * .70f
     addOval(Rect(Offset(cx - 0.45f * radius, cy), radius * .26f))
     addOval(Rect(Offset(cx, cy - radius * .12f), radius * .40f))
     addRoundRect(RoundRect(Rect(cx - radius * 0.9f, cy - radius * .04f, cx + radius * 0.9f, cy + radius * .35f), CornerRadius(radius * .20f)))
    }
    drawPath(nightCloud, Color(0xFF8BA7D6).copy(alpha = .22f))

    val moon = Path.combine(
     PathOperation.Difference,
     Path().apply { addOval(Rect(orbit, radius)) },
     Path().apply { addOval(Rect(orbit + Offset(radius * .52f, -radius * .43f), radius * .88f)) }
    )
    val moonlight = Brush.linearGradient(
     listOf(Color(0xFFE0F8FF), Color(0xFF4FCBFF)),
     start = orbit - Offset(radius, radius),
     end = orbit + Offset(radius, radius)
    )
    drawPath(moon, moonlight)
   } else {
    val coronaRadius = radius * 1.45f * sunScale
    drawCircle(Color(0xFFFFE5B6).copy(alpha = sunHaloAlpha), coronaRadius, orbit)

    val sun = Brush.radialGradient(
     listOf(Color(0xFFFFEDD0), Color(if (twilight) 0xFFF4A077 else 0xFFFFCC71)),
     center = orbit - Offset(radius / 4, radius / 4),
     radius = radius * 1.3f
    )
    drawCircle(sun, radius, orbit)

    val backCloud = Path().apply {
     val bx = size.width * .65f + cloudDrift2.dp.toPx()
     val by = size.height * (if (twilight) .52f else .40f)
     val br = radius * 0.65f
     addOval(Rect(Offset(bx - 0.4f * br, by), br * .28f))
     addOval(Rect(Offset(bx, by - br * .14f), br * .42f))
     addRoundRect(RoundRect(Rect(bx - br * 0.85f, by - br * .05f, bx + br * 0.85f, by + br * .38f), CornerRadius(br * .20f)))
    }
    drawPath(backCloud, (if (twilight) Color(0xFFF9C3A5) else Color.White).copy(alpha = .30f))

    val cloud = Path().apply {
     val x = size.width * .28f + cloudDrift1.dp.toPx()
     val y = size.height * .69f
     addOval(Rect(Offset(x - 0.5f * radius, y), radius * .30f))
     addOval(Rect(Offset(x, y - radius * .15f), radius * .46f))
     addRoundRect(RoundRect(Rect(x - radius, y - radius * .05f, x + radius, y + radius * .41f), CornerRadius(radius * .23f)))
    }
    val cloudColor = if (twilight) Color(0xFFFFD4BE) else Color.White
    drawPath(cloud, cloudColor.copy(alpha = .60f))
   }
  }
 }
}

@Composable fun WeeklyCard(budget: TimeBudget,days: List<Pair<LocalDate,TimeBudget>>,p: Preferences,modifier: Modifier=Modifier,onMore: (() -> Unit)?=null) {
 val context=LocalContext.current;val locale=LocalConfiguration.current.locales[0]
 val reduced=LocalReducedMotion.current
 var selected by remember { mutableStateOf<Kind?>(null) }; var showDetail by remember { mutableStateOf(false) }
 var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
 Panel(modifier) {
  SectionLabel(stringResource(R.string.report_week_budget),if(onMore!=null) { { IconButton(onClick=onMore) { AppIcon(Icons.AutoMirrored.Rounded.ArrowForward,R.string.statistics) } } } else null)
  val start=budget.start.atZone(p.zone()).toLocalDate()
  val end=budget.end.atZone(p.zone()).toLocalDate().minusDays(1)
  Crossfade(start to end,animationSpec=motionSpec(reduced),label="week-range") { (from, to) ->
   Text(dateText(from,p,locale,true)+" — "+dateText(to,p,locale,true),Modifier.hideOutgoingContent(from!=start || to!=end),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Crossfade(budget.free.seconds,animationSpec=motionSpec(reduced),label="weekly-free-time") { seconds ->
   Text(duration(context,seconds,p),Modifier.hideOutgoingContent(seconds!=budget.free.seconds),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.secondary)
  }
  Text(stringResource(R.string.report_week_remaining,duration(context,budget.remainingFree.seconds,p)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth().height(20.dp).clip(CircleShape),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
   for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) {
    val seconds=if(kind==null) budget.free.seconds else budget.breakdown[kind]?.seconds?:0
    val segmentTitle=stringResource(kindTitle(kind))
    val weight by animateFloatAsState(seconds.toFloat(),animationSpec=motionSpec(reduced, 300),label="budget-segment")
    if(weight>0f) Box(Modifier.weight(weight).fillMaxHeight().clip(RectangleShape).background(kindColor(kind))
     .clickable(enabled=seconds>0) {selected=kind;showDetail=true}
     .semantics { if(seconds>0) contentDescription=segmentTitle+": "+duration(context,seconds,p) else hideFromAccessibility() })
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) Column(Modifier.weight(1f).clickable { selected=kind;showDetail=true }.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(kindColor(kind))); Spacer(Modifier.height(6.dp)); Text(stringResource(kindTitle(kind)),style=MaterialTheme.typography.labelSmall)
   }
  }
  Row(Modifier.fillMaxWidth().height(92.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
   for((date,day) in days) key(date) {
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable {selectedDate=date},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
     val fraction=(day.free.seconds.toFloat()/Duration.between(day.start,day.end).seconds).coerceIn(.02f,1f)
     val height by animateDpAsState((fraction*56+4).dp,animationSpec=motionSpec(reduced, 300),label="daily-budget")
     Box(Modifier.fillMaxWidth(.65f).height(height).clip(RoundedCornerShape(7.dp)).background(if(date==LocalDate.now(p.zone())) FreeColor else FreeColor.copy(alpha=.42f)))
     Spacer(Modifier.height(10.dp)); Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW,locale),style=MaterialTheme.typography.labelMedium)
    }
   }
  }
  AnimatedVisibility(budget.uncertainDates.isNotEmpty(),enter=sectionEnter(reduced),exit=sectionExit(reduced)) {
   Text(stringResource(R.string.holiday_unknown),Modifier.hideOutgoingContent(budget.uncertainDates.isEmpty()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
 if(showDetail) AlertDialog(onDismissRequest={showDetail=false},title={Text(stringResource(kindTitle(selected)))},text={Column { Text(duration(context,if(selected==null) budget.free.seconds else budget.breakdown[selected]?.seconds?:0,p),style=MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.chart_note)) }},confirmButton={TextButton(onClick={showDetail=false}) {Text(stringResource(R.string.done))}})
 days.firstOrNull { it.first==selectedDate }?.let { (date, day) ->
  AlertDialog(onDismissRequest={selectedDate=null},title={Text(dateText(date,p,locale))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){ for(kind in listOf(Kind.SLEEP,Kind.WORK,Kind.EXERCISE,null)) Text(stringResource(kindTitle(kind))+" · "+duration(context,if(kind==null)day.free.seconds else day.breakdown[kind]?.seconds?:0,p)) }},confirmButton={TextButton(onClick={selectedDate=null}){Text(stringResource(R.string.done))}})
 }
}
@Composable fun RoutineCard(s: DashboardState,vm: AppViewModel,onEditor: (String)->Unit) {
 val context=LocalContext.current;val p=s.config.prefs;val locale=LocalConfiguration.current.locales[0]
 var expanded by rememberSaveable { mutableStateOf(true) }
 val reduced=LocalReducedMotion.current
 val chevronRotation by animateFloatAsState(if(expanded) 180f else 0f,animationSpec=motionSpec(reduced),label="routine-chevron")
 val completed = remember(s.routine, s.routineOwner) {
  val owner = s.routineOwner.toString()
  s.routine.filter { it.ownerDate == owner }.map { it.itemId }.toSet()
 }
 val active=s.occurrence?.let {s.now >= it.start.minusSeconds(p.routineLeadMinutes*60L)} ?: true
 Panel(Modifier.widthIn(max=780.dp).fillMaxWidth()) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f).clickable {expanded=!expanded}) {
    Text(stringResource(R.string.routine),style=MaterialTheme.typography.titleLarge)
    Crossfade(completed.size,animationSpec=motionSpec(reduced),label="routine-progress") { count ->
     Text(stringResource(R.string.routine_progress,number(count,p,locale),number(s.config.routine.size,p,locale)),Modifier.hideOutgoingContent(count!=completed.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   IconButton(onClick={onEditor("routine")}) {AppIcon(Icons.Rounded.Edit,R.string.manage_routine)}
   IconButton(onClick={expanded=!expanded}) {AppIcon(Icons.Rounded.ExpandMore,R.string.routine,modifier=Modifier.size(22.dp).graphicsLayer { rotationZ = chevronRotation })}
  }
  AnimatedVisibility(!active,enter=sectionEnter(reduced),exit=sectionExit(reduced)) {
   Text(stringResource(R.string.routine_active_at,number(p.routineLeadMinutes,p,locale)),Modifier.hideOutgoingContent(active),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  AnimatedVisibility(expanded,enter=sectionEnter(reduced),exit=sectionExit(reduced)) {
   Column(Modifier.hideOutgoingContent(!expanded)) {
    for(item in s.config.routine) key(item.id) {
     val checked=item.id in completed
     val color by animateColorAsState(if(checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,animationSpec=motionSpec(reduced),label="routine-completion")
     Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
      .toggleable(value=checked,enabled=active && expanded,role=Role.Checkbox,onValueChange={vm.routineToggle(item,s.routineOwner,it)})
      .padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
      Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) { Checkbox(checked=checked,onCheckedChange=null,enabled=active) }
      Text(number(routineText(context,item),p,locale),Modifier.weight(1f),color=color)
     }
    }
    if(s.config.routine.isEmpty()) Text(stringResource(R.string.routine_empty),style=MaterialTheme.typography.bodyMedium)
   }
  }
 }
}

internal fun Modifier.hideOutgoingContent(outgoing: Boolean): Modifier =
 if(outgoing) clearAndSetSemantics { hideFromAccessibility() } else this
