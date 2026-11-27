@file:OptIn(ExperimentalLayoutApi::class)
package pasac.sleepino.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import pasac.sleepino.R
import pasac.sleepino.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayNameStyle
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

@Composable fun Dashboard(
 s: DashboardState,
 vm: AppViewModel,
 onSettings: () -> Unit = {},
 onReports: () -> Unit = {},
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
  onSettings = onSettings,
  onReports = onReports
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
 onReports: () -> Unit = {}
) {
 val p = s.config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val localTime = s.now.atZone(p.zone())
 val localDate = localTime.toLocalDate()
 val sleep = s.occurrence
 val snoozed = s.activeSession
  ?.takeIf { it.key == sleep?.key && !it.completed && !s.confirmed }
  ?.snoozedUntil?.let(Instant::ofEpochMilli)?.takeIf { it > s.now }
 val pastBedtime = sleep != null && s.now >= sleep.start && !s.confirmed
 val windDown = sleep != null && !s.confirmed && !pastBedtime && s.now >= sleep.start.minusSeconds(30 * 60L)
 val skyPhase = resolveSkyPhase(
  hour = localTime.hour,
  confirmed = s.confirmed,
  pastBedtime = pastBedtime,
  snoozed = snoozed != null,
  windDown = windDown
 )
 val palette = skyPalette(skyPhase)
 val reduced = LocalReducedMotion.current
 val colorSpec = motionSpec<Color>(reduced, 600)
 val skyTop by animateColorAsState(palette.top, colorSpec, label = "sky-top")
 val skyMid by animateColorAsState(palette.mid, colorSpec, label = "sky-mid")
 val skyBottom by animateColorAsState(palette.bottom, colorSpec, label = "sky-bottom")
 val ink by animateColorAsState(palette.ink, colorSpec, label = "sky-ink")
 val muted by animateColorAsState(palette.muted, colorSpec, label = "sky-muted")
 val sky = remember(skyTop, skyMid, skyBottom) {
  Brush.verticalGradient(listOf(skyTop, skyMid, skyBottom))
 }
 val buttonColors = ButtonDefaults.buttonColors(containerColor = palette.actionColor, contentColor = palette.actionInk)
 val workDay = s.today.workdays > 0

 BoxWithConstraints(
  Modifier
   .fillMaxSize()
   .background(sky),
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
   Modifier.fillMaxSize()
    .padding(top = if (compact) 8.dp else 14.dp, bottom = if (compact) 12.dp else 24.dp),
   horizontalAlignment = Alignment.CenterHorizontally
  ) {
   Box(
    Modifier
     .widthIn(max = 760.dp)
     .fillMaxWidth()
     .padding(horizontal = 18.dp)
     .heightIn(min = if (compact) 40.dp else 48.dp),
    contentAlignment = Alignment.Center
   ) {
    IconButton(
     onClick = onReports,
     modifier = Modifier.align(Alignment.CenterStart).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
     AppIcon(Icons.Rounded.BarChart, R.string.statistics, tint = muted)
    }
    val dateLabel = if (isJalali(p, locale)) {
     val d = remember(localDate) { PersianDates.fromGregorian(localDate) }
     val mainDate = remember(localDate, d, p, locale) {
      "${localDate.dayOfWeek.getDisplayName(DayNameStyle.FULL, locale)}، ${number(d.day, p, locale)} ${jalaliMonthName(d.month, locale)}"
     }
     val yearPart = remember(d, p, locale) { " ${number(d.year, p, locale)}" }
     val mainStyle = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink)
     val yearStyle = SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize, fontWeight = FontWeight.Medium, color = muted)
     buildAnnotatedString {
      withStyle(mainStyle) { append(mainDate) }
      if (p.showYear) {
       withStyle(yearStyle) { append(yearPart) }
      }
     }
    } else {
     val mainDate = remember(localDate, p, locale) {
      number(localDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)), p, locale)
     }
     val yearPart = remember(localDate, p, locale) { " ${number(localDate.year, p, locale)}" }
     val mainStyle = SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink)
     val yearStyle = SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize, fontWeight = FontWeight.Medium, color = muted)
     buildAnnotatedString {
      withStyle(mainStyle) { append(mainDate) }
      if (p.showYear) {
       withStyle(yearStyle) { append(yearPart) }
      }
     }
    }
    Text(
     dateLabel,
     modifier = Modifier.padding(horizontal = 52.dp),
     style = MaterialTheme.typography.titleMedium,
     maxLines = 1,
     overflow = TextOverflow.Ellipsis
    )
    IconButton(
     onClick = onSettings,
     modifier = Modifier.align(Alignment.CenterEnd).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
     AppIcon(Icons.Rounded.Settings, R.string.settings, tint = muted)
    }
   }
   SkyIllustration(
    phase = skyPhase,
    modifier = Modifier.fillMaxWidth().weight(1f)
   )
   Column(
    Modifier.padding(horizontal = 18.dp).widthIn(max = 560.dp).fillMaxWidth(),
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
   val showDayBadge = p.showDayType
   val showSleepBadge = p.showBedtime && sleep != null
   if (showDayBadge || showSleepBadge) {
    Spacer(Modifier.weight(if (compact) .12f else .22f))
    FlowRow(
     modifier = Modifier.padding(horizontal = 18.dp),
     horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
     verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     if (showDayBadge) {
      Row(
       Modifier.clip(CircleShape).background(ink.copy(alpha = .10f)).padding(horizontal = 12.dp, vertical = 6.dp),
       horizontalArrangement = Arrangement.spacedBy(6.dp),
       verticalAlignment = Alignment.CenterVertically
      ) {
       Icon(if (workDay) Icons.Rounded.WorkOutline else Icons.Rounded.Spa, null, Modifier.size(14.dp), tint = muted)
       Text(stringResource(if (workDay) R.string.work_day else R.string.rest_day), style = MaterialTheme.typography.labelMedium, color = muted)
      }
     }
     if (showSleepBadge) {
      val bedtimeClock = remember(sleep.start, p, locale) { clockText(sleep.start, p, locale) }
      val bedtimeDesc = "${stringResource(R.string.bedtime)} $bedtimeClock"
      Row(
       Modifier.clip(CircleShape).background(ink.copy(alpha = .10f)).padding(horizontal = 12.dp, vertical = 6.dp)
        .semantics(mergeDescendants = true) { contentDescription = bedtimeDesc },
       horizontalArrangement = Arrangement.spacedBy(6.dp),
       verticalAlignment = Alignment.CenterVertically
      ) {
       Icon(Icons.Rounded.Bedtime, null, Modifier.size(14.dp), tint = muted)
       Text(bedtimeClock, style = MaterialTheme.typography.labelMedium, color = muted)
      }
     }
    }
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

internal enum class SkyPhase {
 DAWN,
 MORNING,
 DAY,
 GOLDEN_HOUR,
 TWILIGHT,
 NIGHT,
 WIND_DOWN,
 OVERDUE,
 RESTING
}

internal fun resolveSkyPhase(
 hour: Int,
 confirmed: Boolean,
 pastBedtime: Boolean,
 snoozed: Boolean,
 windDown: Boolean
): SkyPhase = when {
 confirmed -> SkyPhase.RESTING
 pastBedtime && !snoozed -> SkyPhase.OVERDUE
 windDown || snoozed -> SkyPhase.WIND_DOWN
 hour in 5..6 -> SkyPhase.DAWN
 hour in 7..10 -> SkyPhase.MORNING
 hour in 11..15 -> SkyPhase.DAY
 hour in 16..17 -> SkyPhase.GOLDEN_HOUR
 hour in 18..19 -> SkyPhase.TWILIGHT
 else -> SkyPhase.NIGHT
}

private data class SkyPalette(
 val top: Color,
 val mid: Color,
 val bottom: Color,
 val ink: Color,
 val muted: Color,
 val actionColor: Color,
 val actionInk: Color
)

private fun skyPalette(phase: SkyPhase): SkyPalette = when (phase) {
 SkyPhase.DAWN -> SkyPalette(
  top = Color(0xFF1F2752),
  mid = Color(0xFF5D416E),
  bottom = Color(0xFF9E5A6E),
  ink = Color.White,
  muted = Color(0xFFF3E5EC),
  actionColor = Color(0xFFFFE1D6),
  actionInk = Color(0xFF3F1E37)
 )
 SkyPhase.MORNING -> SkyPalette(
  top = Color(0xFF57ACE3),
  mid = Color(0xFFA7DCF8),
  bottom = Color(0xFFDDF3FC),
  ink = Color(0xFF0E2A42),
  muted = Color(0xFF1F4766),
  actionColor = Color(0xFF133A58),
  actionInk = Color.White
 )
 SkyPhase.DAY -> SkyPalette(
  top = Color(0xFF3E98DE),
  mid = Color(0xFF8FD0F7),
  bottom = Color(0xFFCBEBF9),
  ink = Color(0xFF0D2840),
  muted = Color(0xFF1D4361),
  actionColor = Color(0xFF133A58),
  actionInk = Color.White
 )
 SkyPhase.GOLDEN_HOUR -> SkyPalette(
  top = Color(0xFF7CB0DE),
  mid = Color(0xFFC8D7DC),
  bottom = Color(0xFFF9DFB6),
  ink = Color(0xFF12273B),
  muted = Color(0xFF284158),
  actionColor = Color(0xFF1F3852),
  actionInk = Color.White
 )
 SkyPhase.TWILIGHT -> SkyPalette(
  top = Color(0xFF1D224D),
  mid = Color(0xFF5B3766),
  bottom = Color(0xFFA04B62),
  ink = Color.White,
  muted = Color(0xFFF0E4F0),
  actionColor = Color(0xFFFFD9D2),
  actionInk = Color(0xFF3C1C32)
 )
 SkyPhase.NIGHT -> SkyPalette(
  top = Color(0xFF0C1734),
  mid = Color(0xFF15254A),
  bottom = Color(0xFF091124),
  ink = Color.White,
  muted = Color(0xFFE0EAFA),
  actionColor = Color(0xFFD6E9FF),
  actionInk = Color(0xFF152D48)
 )
 SkyPhase.WIND_DOWN -> SkyPalette(
  top = Color(0xFF191E46),
  mid = Color(0xFF2A265A),
  bottom = Color(0xFF161838),
  ink = Color.White,
  muted = Color(0xFFE7E4FA),
  actionColor = Color(0xFFE2DCFF),
  actionInk = Color(0xFF1E1A42)
 )
 SkyPhase.OVERDUE -> SkyPalette(
  top = Color(0xFF050711),
  mid = Color(0xFF0F1426),
  bottom = Color(0xFF04050B),
  ink = Color(0xFFF6F1E7),
  muted = Color(0xFFD4C5AF),
  actionColor = Color(0xFFF0C27B),
  actionInk = Color(0xFF1D1408)
 )
 SkyPhase.RESTING -> SkyPalette(
  top = Color(0xFF060C1C),
  mid = Color(0xFF0F1933),
  bottom = Color(0xFF050814),
  ink = Color.White,
  muted = Color(0xFFD6E2F5),
  actionColor = Color(0xFFD6E9FF),
  actionInk = Color(0xFF152D48)
 )
}

@Composable
internal fun SkyIllustration(
 phase: SkyPhase,
 modifier: Modifier = Modifier
) {
 val reduced = LocalReducedMotion.current
 val transition = rememberInfiniteTransition(label = "sky-animation")

 val floatY by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = -5f,
  targetValue = 5f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "celestial-float"
 )

 val pulse by if (reduced) remember { mutableFloatStateOf(0.5f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "celestial-pulse"
 )

 val starPhase by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4500, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "star-phase"
 )

 val rayRotation by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 360f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 36000, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "ray-rotation"
 )

 val meteorPhase by if (reduced) remember { mutableFloatStateOf(0.5f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 9000, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "meteor-phase"
 )

 val cloudDrift1 by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = -20f,
  targetValue = 20f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "cloud-drift-1"
 )

 val cloudDrift2 by if (reduced) remember { mutableFloatStateOf(0f) }
 else transition.animateFloat(
  initialValue = 16f,
  targetValue = -16f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 10500, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "cloud-drift-2"
 )

 val morningFlight by if (reduced) remember { mutableFloatStateOf(0.48f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 14000, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "morning-flight"
 )

 val sleepCycle by if (reduced) remember { mutableFloatStateOf(0.35f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 7200, easing = LinearEasing),
   repeatMode = RepeatMode.Restart
  ),
  label = "sleep-cycle"
 )

 val breath by if (reduced) remember { mutableFloatStateOf(0.45f) }
 else transition.animateFloat(
  initialValue = 0f,
  targetValue = 1f,
  animationSpec = infiniteRepeatable(
   animation = tween(durationMillis = 4600, easing = FastOutSlowInEasing),
   repeatMode = RepeatMode.Reverse
  ),
  label = "sleep-breath"
 )

 Box(modifier = modifier) {
  Canvas(modifier = Modifier.fillMaxSize()) {
   if (size.minDimension <= 0f) return@Canvas

   val radius = minOf(88.dp.toPx(), size.width * .25f, size.height * .42f)
   val bob = floatY.dp.toPx()

   when (phase) {
    SkyPhase.DAWN, SkyPhase.MORNING, SkyPhase.DAY, SkyPhase.GOLDEN_HOUR, SkyPhase.TWILIGHT -> {
     val (orbX, orbY) = when (phase) {
      SkyPhase.DAWN -> .34f to .72f
      SkyPhase.MORNING -> .44f to .53f
      SkyPhase.DAY -> .56f to .43f
      SkyPhase.GOLDEN_HOUR -> .65f to .55f
      SkyPhase.TWILIGHT -> .72f to .71f
      else -> .56f to .50f
     }
     val orbit = Offset(size.width * orbX, size.height * orbY + bob)

     // Soft horizon atmospheric glow for low-sun phases
     if (phase == SkyPhase.DAWN || phase == SkyPhase.GOLDEN_HOUR || phase == SkyPhase.TWILIGHT) {
      val horizonColor = when (phase) {
       SkyPhase.DAWN -> Color(0xFFFFB899)
       SkyPhase.GOLDEN_HOUR -> Color(0xFFFFD085)
       else -> Color(0xFFFF8C7A)
      }
      val horizonCenter = Offset(orbit.x, size.height * .95f)
      val horizonRadius = size.width * .78f
      drawCircle(
       Brush.radialGradient(listOf(horizonColor.copy(alpha = .34f), Color.Transparent), center = horizonCenter, radius = horizonRadius),
       horizonRadius,
       horizonCenter
      )
     }

     // Lingering or emerging stars at dawn & twilight
     if (phase == SkyPhase.DAWN || phase == SkyPhase.TWILIGHT) {
      val horizonStars = listOf(.11f to .18f, .24f to .29f, .47f to .13f, .62f to .22f, .81f to .16f, .90f to .31f)
      for ((idx, pt) in horizonStars.withIndex()) {
       val twinkle = if (reduced) 0.55f else (0.30f + 0.50f * ((sin((starPhase * 2 * PI + idx * 1.7f)).toFloat() + 1f) / 2f))
       drawSparkleStar(
        center = Offset(size.width * pt.first, size.height * pt.second),
        r = minOf((2.2f + idx % 2).dp.toPx(), radius * .11f),
        color = Color(0xFFFFEEF5).copy(alpha = twinkle * .72f)
       )
      }
     }

     // Sun atmospheric bloom & rotating rays
     val bloomColor = when (phase) {
      SkyPhase.DAWN -> Color(0xFFFFC5A1)
      SkyPhase.GOLDEN_HOUR -> Color(0xFFFFD88F)
      SkyPhase.TWILIGHT -> Color(0xFFFF9C84)
      else -> Color(0xFFFFEAB8)
     }
     val bloomRadius = radius * (2.05f + 0.16f * pulse)
     drawCircle(
      Brush.radialGradient(listOf(bloomColor.copy(alpha = .28f + .10f * pulse), Color.Transparent), center = orbit, radius = bloomRadius),
      bloomRadius,
      orbit
     )

     val rayAlpha = if (phase == SkyPhase.DAWN || phase == SkyPhase.TWILIGHT) .09f else .14f
     val rayCount = 10
     rotate(degrees = rayRotation, pivot = orbit) {
      for (i in 0 until rayCount) {
       val angle = (i * (360f / rayCount)) * (PI.toFloat() / 180f)
       val inner = radius * 1.08f
       val outer = radius * (if (i % 2 == 0) 1.62f else 1.44f)
       drawLine(
        color = bloomColor.copy(alpha = rayAlpha),
        start = Offset(orbit.x + cos(angle) * inner, orbit.y + sin(angle) * inner),
        end = Offset(orbit.x + cos(angle) * outer, orbit.y + sin(angle) * outer),
        strokeWidth = (if (i % 2 == 0) 3.5f else 2.2f).dp.toPx(),
        cap = StrokeCap.Round
       )
      }
     }

     val coronaRadius = radius * (1.36f + 0.08f * pulse)
     drawCircle(bloomColor.copy(alpha = .18f + .08f * pulse), coronaRadius, orbit)

     val sunColors = when (phase) {
      SkyPhase.DAWN -> listOf(Color(0xFFFFF0DB), Color(0xFFFFBE94), Color(0xFFF0877D))
      SkyPhase.MORNING -> listOf(Color(0xFFFFF8E2), Color(0xFFFFDC88), Color(0xFFFFC35E))
      SkyPhase.DAY -> listOf(Color(0xFFFFF9E6), Color(0xFFFFD875), Color(0xFFFFB94D))
      SkyPhase.GOLDEN_HOUR -> listOf(Color(0xFFFFF0CE), Color(0xFFFFC46B), Color(0xFFF3935B))
      else -> listOf(Color(0xFFFFDFC2), Color(0xFFF69973), Color(0xFFD96468))
     }
     drawCircle(
      Brush.radialGradient(sunColors, center = orbit - Offset(radius * .26f, radius * .26f), radius = radius * 1.28f),
      radius,
      orbit
     )
     drawCircle(
      color = Color.White.copy(alpha = .24f),
      radius = radius - 1.dp.toPx(),
      center = orbit,
      style = Stroke(width = 1.5.dp.toPx())
     )

     // Soft floating light motes for daytime clarity
     if (phase == SkyPhase.MORNING || phase == SkyPhase.DAY || phase == SkyPhase.GOLDEN_HOUR) {
      val motes = listOf(.14f to .32f, .24f to .58f, .82f to .26f, .88f to .62f)
      for ((idx, pt) in motes.withIndex()) {
       val drift = sin(starPhase * 2 * PI + idx * 1.3f).toFloat() * 3.dp.toPx()
       drawCircle(
        color = Color.White.copy(alpha = .22f),
        radius = (2.2f + idx % 2).dp.toPx(),
        center = Offset(size.width * pt.first, size.height * pt.second + drift)
       )
      }
     }

     // Gliding morning birds with gentle wing beats
     if (phase == SkyPhase.MORNING) {
      val flightAlpha = (if (reduced) 0.34f else sin(morningFlight * PI).toFloat().coerceIn(0f, 1f) * 0.42f)
      val flockOffsets = listOf(0f to 0f, -.075f to -.055f, -.13f to .045f)
      val leadX = size.width * (.10f + .78f * morningFlight)
      val leadY = size.height * (.28f - .06f * sin(morningFlight * PI).toFloat())
      for ((idx, rel) in flockOffsets.withIndex()) {
       val flap = if (reduced) 0.45f else sin(morningFlight * 2 * PI * 7 + idx * 1.15f).toFloat()
       drawMorningBird(
        center = Offset(leadX + size.width * rel.first, leadY + size.height * rel.second),
        wingSpan = minOf((9.5f - idx * 1.2f).dp.toPx(), radius * .13f),
        flap = flap,
        color = Color(0xFF153D5E).copy(alpha = flightAlpha * (1f - idx * .15f))
       )
      }
     }

     // Three depth-layered clouds
     val mistTint = when (phase) {
      SkyPhase.DAWN -> Color(0xFFFCC7B8)
      SkyPhase.GOLDEN_HOUR -> Color(0xFFFFE5BD)
      SkyPhase.TWILIGHT -> Color(0xFFF3B3AE)
      else -> Color.White
     }
     val cloudHighlight = when (phase) {
      SkyPhase.DAWN -> Color(0xFFFFE4D6)
      SkyPhase.GOLDEN_HOUR -> Color(0xFFFFF2D6)
      SkyPhase.TWILIGHT -> Color(0xFFFFD5CC)
      else -> Color.White
     }
     drawSoftCloud(
      center = Offset(size.width * .18f + cloudDrift2.dp.toPx() * .5f, size.height * .44f),
      scale = radius * .48f,
      baseColor = mistTint.copy(alpha = .24f),
      highlightColor = cloudHighlight.copy(alpha = .28f)
     )
     drawSoftCloud(
      center = Offset(size.width * .68f + cloudDrift2.dp.toPx(), size.height * (if (phase == SkyPhase.TWILIGHT || phase == SkyPhase.DAWN) .52f else .38f)),
      scale = radius * .68f,
      baseColor = mistTint.copy(alpha = .36f),
      highlightColor = cloudHighlight.copy(alpha = .45f)
     )
     drawSoftCloud(
      center = Offset(size.width * .28f + cloudDrift1.dp.toPx(), size.height * .71f),
      scale = radius * .98f,
      baseColor = mistTint.copy(alpha = .62f),
      highlightColor = cloudHighlight.copy(alpha = .78f)
     )
    }

    SkyPhase.OVERDUE -> {
     val orbit = Offset(size.width * .50f, size.height * .52f + bob * 1.15f)

     // Deep nocturnal nebula & subtle warm lantern ember behind the eclipsed moon
     drawCircle(
      Brush.radialGradient(
       listOf(Color(0xFF1D2B59).copy(alpha = .24f), Color.Transparent),
       center = Offset(size.width * .22f, size.height * .26f),
       radius = size.width * .65f
      ),
      size.width * .65f,
      Offset(size.width * .22f, size.height * .26f)
     )
     drawCircle(
      Brush.radialGradient(
       listOf(Color(0xFF8A5424).copy(alpha = .16f), Color.Transparent),
       center = orbit,
       radius = radius * 2.5f
      ),
      radius * 2.5f,
      orbit
     )

     // Soft horizon depth glow (radial only, no rectangular clip edges)
     drawCircle(
      Brush.radialGradient(
       listOf(Color(0x5503040A), Color.Transparent),
       center = Offset(size.width * .50f, size.height * .92f),
       radius = size.width * .85f
      ),
      size.width * .85f,
      Offset(size.width * .50f, size.height * .92f)
     )

     // Quiet late-night stardust
     val dust = Path().apply {
      for (index in 1..64) {
       val point = Offset(
        size.width * ((index * 73) % 251) / 251f,
        size.height * ((index * index * 13 + index * 29) % 199) / 199f
       )
       if ((point - orbit).getDistance() > radius * 1.25f) {
        addOval(Rect(point, (.50f + index % 3 * .30f).dp.toPx()))
       }
      }
     }
     drawPath(dust, Color(0xFF8EA5D0).copy(alpha = .20f))

     // Dim twinkling stars
     val starPoints = listOf(
      .09f to .19f, .16f to .60f, .24f to .15f, .31f to .38f,
      .43f to .11f, .64f to .13f, .76f to .28f, .86f to .18f,
      .88f to .58f, .14f to .82f, .82f to .80f
     )
     for ((index, point) in starPoints.withIndex()) {
      val center = Offset(size.width * point.first, size.height * point.second)
      if ((center - orbit).getDistance() <= radius * 1.35f) continue
      val twinkle = if (reduced) 0.55f
      else (0.28f + 0.52f * ((sin((starPhase * 2 * PI + index * 1.5f)).toFloat() + 1f) / 2f))
      drawSparkleStar(
       center = center,
       r = minOf((1.8f + index % 3).dp.toPx(), radius * .11f),
       color = Color(0xFFD6E2FA).copy(alpha = twinkle * .45f)
      )
     }

     // Falling "Sandman / time-slipping" golden motes drifting slowly downward
     val moteCount = 12
     for (i in 0 until moteCount) {
      val laneX = 0.08f + 0.84f * (((i * 67 + 19) % 97) / 97f)
      val t = if (reduced) ((i * 0.13f + 0.25f) % 1f) else ((sleepCycle + i / moteCount.toFloat()) % 1f)
      val sway = if (reduced) 0f else sin((t * 2 * PI + i * 1.3f)).toFloat() * 6.dp.toPx()
      val moteCenter = Offset(size.width * laneX + sway, size.height * (.10f + .76f * t))
      if ((moteCenter - orbit).getDistance() > radius * 1.15f) {
       val moteAlpha = (if (reduced) 0.35f else sin(t * PI).toFloat().coerceIn(0f, 1f) * 0.48f)
       val moteRadius = (1.1f + (i % 3) * 0.55f).dp.toPx()
       drawCircle(Color(0xFFF3C67A).copy(alpha = moteAlpha), moteRadius, moteCenter)
      }
     }

     // Calm breathing sleep ripples radiating outward
     val ring1 = radius * (1.30f + 0.18f * breath)
     val ring2 = radius * (1.58f + 0.28f * breath)
     drawCircle(
      color = Color(0xFFEAB86E).copy(alpha = .20f * (1f - breath * .50f)),
      radius = ring1,
      center = orbit,
      style = Stroke(width = 1.6.dp.toPx())
     )
     drawCircle(
      color = Color(0xFF7589C7).copy(alpha = .14f * (1f - breath * .55f)),
      radius = ring2,
      center = orbit,
      style = Stroke(width = 1.2.dp.toPx())
     )

     // Celestial overdue clock dial: subtle circular track + orbiting amber lantern & fading trail
     val dialRadius = radius * 1.18f
     drawCircle(
      color = Color(0xFF566596).copy(alpha = .24f),
      radius = dialRadius,
      center = orbit,
      style = Stroke(width = 1.2.dp.toPx())
     )
     for (tick in 0 until 12) {
      val tickRad = (-90f + tick * 30f) * (PI.toFloat() / 180f)
      val tickPos = Offset(orbit.x + cos(tickRad) * dialRadius, orbit.y + sin(tickRad) * dialRadius)
      val isCardinal = tick % 3 == 0
      drawCircle(
       color = Color(0xFF8EA1D6).copy(alpha = if (isCardinal) .48f else .22f),
       radius = (if (isCardinal) 2.1f else 1.2f).dp.toPx(),
       center = tickPos
      )
     }
     val handAngleDeg = if (reduced) 55f else (sleepCycle * 360f - 90f)
     val trailSweep = 88f
     val trailSteps = 10
     for (step in 0 until trailSteps) {
      val frac = (step + 1) / trailSteps.toFloat()
      val segSweep = trailSweep / trailSteps
      val segStart = handAngleDeg - trailSweep + step * segSweep
      drawArc(
       color = Color(0xFFF3C378).copy(alpha = .05f + .42f * frac * frac),
       startAngle = segStart,
       sweepAngle = segSweep + 0.5f,
       useCenter = false,
       topLeft = Offset(orbit.x - dialRadius, orbit.y - dialRadius),
       size = Size(dialRadius * 2f, dialRadius * 2f),
       style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
      )
     }
     val handRad = handAngleDeg * (PI.toFloat() / 180f)
     val lanternPos = Offset(orbit.x + cos(handRad) * dialRadius, orbit.y + sin(handRad) * dialRadius)
     drawCircle(Color(0xFFF6C97E).copy(alpha = .28f), 8.dp.toPx(), lanternPos)
     drawCircle(Color(0xFFFFE5B4), 3.3.dp.toPx(), lanternPos)

     // Soft warm bedside-lantern halo around the moon
     val haloRadius = radius * (1.72f + 0.16f * breath)
     drawCircle(
      Brush.radialGradient(
       listOf(Color(0x3AF0B765), Color.Transparent),
       center = orbit,
       radius = haloRadius
      ),
      haloRadius,
      orbit
     )

     // Distant dark cloud drifting behind the moon
     drawSoftCloud(
      center = Offset(size.width * .74f + cloudDrift2.dp.toPx(), size.height * .37f),
      scale = radius * .64f,
      baseColor = Color(0xFF141B30).copy(alpha = .42f),
      highlightColor = Color(0xFF202A49).copy(alpha = .48f)
     )

     // Eclipsed golden-amber crescent moon: dark umbra sphere with soft earthshine + breathing shadow
     val shadowShift = Offset(radius * (.44f - .05f * breath), -radius * (.36f - .04f * breath))
     drawCircle(
      brush = Brush.radialGradient(
       colors = listOf(Color(0xFF141B33), Color(0xFF070A16)),
       center = orbit - Offset(radius * .2f, radius * .2f),
       radius = radius * 1.1f
      ),
      radius = radius * .98f,
      center = orbit
     )
     drawCircle(
      color = Color(0xFF445382).copy(alpha = .24f),
      radius = radius * .98f,
      center = orbit,
      style = Stroke(width = 1.2.dp.toPx())
     )
     val eclipsedMoon = Path.combine(
      PathOperation.Difference,
      Path().apply { addOval(Rect(orbit, radius)) },
      Path().apply { addOval(Rect(orbit + shadowShift, radius * .89f)) }
     )
     val moonBrush = Brush.linearGradient(
      listOf(Color(0xFFFFF2D6), Color(0xFFF1BF74), Color(0xFFB5773A)),
      start = orbit - Offset(radius, radius),
      end = orbit + Offset(radius, radius)
     )
     drawPath(eclipsedMoon, moonBrush)
     clipPath(eclipsedMoon) {
      drawCircle(Color.White.copy(alpha = .24f), radius * .22f, orbit + Offset(-radius * .46f, radius * .12f))
      drawCircle(Color.Black.copy(alpha = .10f), radius * .15f, orbit + Offset(-radius * .22f, radius * .46f))
      drawCircle(Color.Black.copy(alpha = .08f), radius * .10f, orbit + Offset(-radius * .52f, -radius * .24f))
     }

     // Rising, swaying "Z" sleep glyphs floating up from the moon
     val zOrigin = orbit + Offset(radius * .46f, -radius * .42f)
     for (idx in 0 until 3) {
      val t = if (reduced) (0.20f + idx * 0.28f) else ((sleepCycle + idx / 3f) % 1f)
      val fade = if (reduced) (0.76f - idx * 0.16f) else sin(t * PI).toFloat().coerceIn(0f, 1f)
      val swayX = if (reduced) 0f else sin((t * 2 * PI + idx * 1.4f)).toFloat() * radius * .08f
      val zPos = Offset(
       x = zOrigin.x + radius * (.20f + .09f * idx) * t + swayX,
       y = zOrigin.y - radius * .92f * t
      )
      val zSize = radius * (.11f + .11f * t)
      val zTilt = -10f + 14f * sin(t * PI).toFloat()
      val zColor = Color(0xFFF6D496).copy(alpha = fade * .90f)
      drawSleepZ(
       center = zPos,
       size = zSize,
       angleDegrees = zTilt,
       strokeWidth = (2.0f + 1.1f * t).dp.toPx(),
       color = zColor
      )
     }

     // Cozy dark foreground cloud tucking into the night horizon
     drawSoftCloud(
      center = Offset(size.width * .28f + cloudDrift1.dp.toPx(), size.height * .75f),
      scale = radius * .94f,
      baseColor = Color(0xFF0A0F1E).copy(alpha = .72f),
      highlightColor = Color(0xFF17203B).copy(alpha = .76f)
     )
    }

    SkyPhase.NIGHT, SkyPhase.WIND_DOWN, SkyPhase.RESTING -> {
     val (orbX, orbY) = when (phase) {
      SkyPhase.RESTING -> .50f to .56f
      SkyPhase.WIND_DOWN -> .54f to .49f
      else -> .56f to .47f
     }
     val orbit = Offset(size.width * orbX, size.height * orbY + bob)

     // Soft diagonal nebula / Milky Way wash
     val nebulaColor = when (phase) {
      SkyPhase.WIND_DOWN -> Color(0xFF8C7AE6)
      SkyPhase.RESTING -> Color(0xFF2E5C9E)
      else -> Color(0xFF3E78FF)
     }
     drawCircle(
      Brush.radialGradient(listOf(nebulaColor.copy(alpha = .14f), Color.Transparent), center = Offset(size.width * .25f, size.height * .30f), radius = size.width * .62f),
      size.width * .62f,
      Offset(size.width * .25f, size.height * .30f)
     )
     drawCircle(
      Brush.radialGradient(listOf(nebulaColor.copy(alpha = .11f), Color.Transparent), center = Offset(size.width * .78f, size.height * .68f), radius = size.width * .56f),
      size.width * .56f,
      Offset(size.width * .78f, size.height * .68f)
     )

     // Stardust background
     val dustAlpha = if (phase == SkyPhase.RESTING) .48f else .42f
     val dustColor = Color(0xFFB9D7FB)
     val dust = Path().apply {
      for (index in 1..72) {
       val point = Offset(
        size.width * ((index * 73) % 251) / 251f,
        size.height * ((index * index * 13 + index * 29) % 199) / 199f
       )
       if ((point - orbit).getDistance() > radius * 1.05f) {
        addOval(Rect(point, (.55f + index % 3 * .35f).dp.toPx()))
       }
      }
     }
     drawPath(dust, dustColor.copy(alpha = dustAlpha))

     // Twinkling 4-point stars
     val starPoints = listOf(
      .07f to .21f, .12f to .57f, .18f to .84f, .23f to .18f, .26f to .43f,
      .33f to .68f, .38f to .31f, .42f to .12f, .48f to .85f, .57f to .14f,
      .69f to .10f, .74f to .72f, .79f to .29f, .83f to .58f, .89f to .84f, .94f to .36f
     )
     val starBaseTint = if (phase == SkyPhase.WIND_DOWN) Color(0xFFEBE6FF) else Color(0xFFE5F2FF)
     for ((index, point) in starPoints.withIndex()) {
      val center = Offset(size.width * point.first, size.height * point.second)
      if ((center - orbit).getDistance() <= radius * 1.1f) continue
      val twinkle = if (reduced) 0.85f
      else (0.42f + 0.58f * ((sin((starPhase * 2 * PI + index * 1.4f)).toFloat() + 1f) / 2f))
      drawSparkleStar(
       center = center,
       r = minOf((2f + index % 4).dp.toPx(), radius * .15f),
       color = starBaseTint.copy(alpha = twinkle)
      )
     }

     // Periodic shooting star in Night & Resting states
     if (!reduced && (phase == SkyPhase.NIGHT || phase == SkyPhase.RESTING) && meteorPhase < 0.18f) {
      val t = meteorPhase / 0.18f
      val meteorAlpha = sin(t * PI).toFloat().coerceIn(0f, 1f) * 0.85f
      val start = Offset(size.width * (0.14f + 0.34f * t), size.height * (0.12f + 0.20f * t))
      val tail = start - Offset(radius * 0.72f, radius * 0.40f)
      drawLine(
       brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFE5F4FF).copy(alpha = meteorAlpha)), start = tail, end = start),
       start = tail,
       end = start,
       strokeWidth = 2.dp.toPx(),
       cap = StrokeCap.Round
      )
      drawCircle(Color.White.copy(alpha = meteorAlpha), 2.2.dp.toPx(), start)
     }

     // Moon halo + breathing rings for Wind-Down
     val haloColor = when (phase) {
      SkyPhase.WIND_DOWN -> Color(0x42A794FF)
      SkyPhase.RESTING -> Color(0x2A5BA8FF)
      else -> Color(0x403DC5FF)
     }
     val haloRadius = radius * (1.75f + 0.22f * pulse)
     drawCircle(Brush.radialGradient(listOf(haloColor, Color.Transparent), center = orbit, radius = haloRadius), haloRadius, orbit)

     if (phase == SkyPhase.WIND_DOWN) {
      val ringTint = Color(0xFFC5B8FF)
      val ring1 = radius * (1.24f + 0.20f * pulse)
      val ring2 = radius * (1.46f + 0.28f * pulse)
      drawCircle(ringTint.copy(alpha = .24f * (1f - pulse * .4f)), ring1, orbit, style = Stroke(width = 1.6.dp.toPx()))
      drawCircle(ringTint.copy(alpha = .14f * (1f - pulse * .5f)), ring2, orbit, style = Stroke(width = 1.2.dp.toPx()))
     }

     // Moonlit drifting night clouds (behind moon for depth, one softly below)
     val nightCloudTint = if (phase == SkyPhase.WIND_DOWN) Color(0xFFA7A3DB) else Color(0xFF8BA7D6)
     drawSoftCloud(
      center = Offset(size.width * .72f + cloudDrift2.dp.toPx(), size.height * .36f),
      scale = radius * .62f,
      baseColor = nightCloudTint.copy(alpha = .16f),
      highlightColor = nightCloudTint.copy(alpha = .24f)
     )

     // Sculpted crescent moon with craters & rim highlight
     val moon = Path.combine(
      PathOperation.Difference,
      Path().apply { addOval(Rect(orbit, radius)) },
      Path().apply { addOval(Rect(orbit + Offset(radius * .52f, -radius * .43f), radius * .88f)) }
     )
     val moonColors = when (phase) {
      SkyPhase.WIND_DOWN -> listOf(Color(0xFFF2EEFF), Color(0xFFC5BEFF), Color(0xFF8C86E8))
      SkyPhase.RESTING -> listOf(Color(0xFFDCEBFF), Color(0xFF8BBCEB), Color(0xFF4F87C4))
      else -> listOf(Color(0xFFECFAFF), Color(0xFF9BE4FF), Color(0xFF49C6FC))
     }
     val moonlight = Brush.linearGradient(
      moonColors,
      start = orbit - Offset(radius, radius),
      end = orbit + Offset(radius, radius)
     )
     drawPath(moon, moonlight)
     clipPath(moon) {
      drawCircle(Color.White.copy(alpha = .22f), radius * .24f, orbit + Offset(-radius * .46f, radius * .12f))
      drawCircle(Color.Black.copy(alpha = .08f), radius * .16f, orbit + Offset(-radius * .22f, radius * .48f))
      drawCircle(Color.Black.copy(alpha = .06f), radius * .11f, orbit + Offset(-radius * .52f, -radius * .26f))
     }

     // Companion evening star near crescent horn
     drawSparkleStar(
      center = orbit + Offset(radius * .74f, -radius * .58f),
      r = minOf(4.2.dp.toPx(), radius * .16f) * (0.85f + 0.25f * pulse),
      color = starBaseTint.copy(alpha = .85f)
     )

     drawSoftCloud(
      center = Offset(size.width * .28f + cloudDrift1.dp.toPx(), size.height * .72f),
      scale = radius * .92f,
      baseColor = nightCloudTint.copy(alpha = .22f),
      highlightColor = nightCloudTint.copy(alpha = .32f)
     )
    }
   }
  }
 }
}

private fun DrawScope.drawSleepZ(
 center: Offset,
 size: Float,
 angleDegrees: Float,
 strokeWidth: Float,
 color: Color
) {
 val half = size * 0.5f
 val zPath = Path().apply {
  moveTo(center.x - half * 0.85f, center.y - half)
  lineTo(center.x + half * 0.85f, center.y - half)
  lineTo(center.x - half * 0.85f, center.y + half)
  lineTo(center.x + half * 0.85f, center.y + half)
 }
 rotate(degrees = angleDegrees, pivot = center) {
  drawPath(
   path = zPath,
   color = color.copy(alpha = color.alpha * 0.24f),
   style = Stroke(width = strokeWidth * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  )
  drawPath(
   path = zPath,
   color = color,
   style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
  )
 }
}

private fun DrawScope.drawSparkleStar(center: Offset, r: Float, color: Color) {
 val starPath = Path().apply {
  moveTo(center.x, center.y - r)
  lineTo(center.x + r * .28f, center.y - r * .28f)
  lineTo(center.x + r, center.y)
  lineTo(center.x + r * .28f, center.y + r * .28f)
  lineTo(center.x, center.y + r)
  lineTo(center.x - r * .28f, center.y + r * .28f)
  lineTo(center.x - r, center.y)
  lineTo(center.x - r * .28f, center.y - r * .28f)
  close()
 }
 drawPath(starPath, color)
}

private fun DrawScope.drawMorningBird(
 center: Offset,
 wingSpan: Float,
 flap: Float,
 color: Color
) {
 val tipY = center.y - wingSpan * (.22f + .24f * flap)
 val ctrlY = center.y - wingSpan * (.48f + .16f * flap)
 val path = Path().apply {
  moveTo(center.x - wingSpan, tipY)
  quadraticTo(center.x - wingSpan * .45f, ctrlY, center.x, center.y)
  quadraticTo(center.x + wingSpan * .45f, ctrlY, center.x + wingSpan, tipY)
 }
 drawPath(
  path = path,
  color = color,
  style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
 )
}

private fun DrawScope.drawSoftCloud(
 center: Offset,
 scale: Float,
 baseColor: Color,
 highlightColor: Color
) {
 val bottom = center.y + scale * .36f
 val path = Path().apply {
  moveTo(center.x - scale * .76f, bottom)
  cubicTo(
   center.x - scale * 1.04f, bottom,
   center.x - scale * .94f, center.y - scale * .22f,
   center.x - scale * .52f, center.y - scale * .18f
  )
  cubicTo(
   center.x - scale * .40f, center.y - scale * .64f,
   center.x + scale * .28f, center.y - scale * .62f,
   center.x + scale * .40f, center.y - scale * .16f
  )
  cubicTo(
   center.x + scale * .78f, center.y - scale * .24f,
   center.x + scale * 1.04f, bottom,
   center.x + scale * .76f, bottom
  )
  close()
 }
 drawPath(
  path = path,
  brush = Brush.verticalGradient(
   colors = listOf(highlightColor, baseColor),
   startY = center.y - scale * .54f,
   endY = bottom
  )
 )
}

internal fun Modifier.hideOutgoingContent(outgoing: Boolean): Modifier =
 if(outgoing) clearAndSetSemantics { hideFromAccessibility() } else this
