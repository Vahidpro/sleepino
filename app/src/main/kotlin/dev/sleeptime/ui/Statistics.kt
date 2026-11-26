@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package dev.sleeptime.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.sleeptime.R
import dev.sleeptime.core.*
import dev.sleeptime.data.BedtimeEntity
import java.time.*
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class WeekReport(
 val records: List<BedtimeEntity>,
 val scheduled: List<SleepOccurrence>,
 val onTime: Int,
 val averageDelayMinutes: Long?,
 val projectedBudget: Pair<TimeBudget, List<Pair<LocalDate, TimeBudget>>>?,
)

private enum class DayAdherenceStatus { ON_TIME, EARLY, LATE, MISSED, UPCOMING }

@Composable
fun StatisticsScreen(
 s: DashboardState,
 vm: AppViewModel,
 onBack: () -> Unit = {},
 onSettings: () -> Unit = {}
) {
 val p = s.config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val reduced = LocalReducedMotion.current
 var selectedWeekDay by rememberSaveable { mutableStateOf<Long?>(null) }
 var choosingWeek by rememberSaveable { mutableStateOf(false) }
 val currentWeek = TimeEngine.weekStart(s.now.atZone(p.zone()).toLocalDate(), p.weekStart)
 val first = selectedWeekDay?.let { TimeEngine.weekStart(LocalDate.ofEpochDay(it), p.weekStart) } ?: currentWeek
 val isCurrent = first == currentWeek
 fun selectWeek(week: LocalDate) { selectedWeekDay = week.takeUnless { it == currentWeek }?.toEpochDay() }

 val report = key(first, currentWeek, s.config, s.history, s.holidayRevision) {
  val loaded by produceState<WeekReport?>(null) {
   value = withContext(Dispatchers.Default) {
    val end = first.plusWeeks(1)
    val records = s.history.filter { record ->
     ensureActive()
     val date = LocalDate.parse(record.ownerDate)
     date in first..<end
    }
    val schedule = (0L..6L).mapNotNull { TimeEngine.sleep(s.config, first.plusDays(it)) }
    val onTime = records.count { (it.confirmed - it.scheduled).absoluteValue <= 900_000L }
    val average = if (records.isEmpty()) null else records.asSequence().map { it.confirmed - it.scheduled }.average().toLong() / 60_000L
    val budget = if (isCurrent) null else {
     val at = (if (first < currentWeek) end else first).atStartOfDay(p.zone()).toInstant()
     val days = (0L..6L).map {
      val day = first.plusDays(it)
      day to TimeEngine.budget(s.config, day, 1, at, vm.holidays)
     }
     TimeEngine.budget(s.config, first, 7, at, vm.holidays) to days
    }
    WeekReport(records, schedule, onTime, average, budget)
   }
  }
  loaded
 }
 val records = report?.records.orEmpty()
 val budget = if (isCurrent) s.week to s.days else report?.projectedBudget
 val expected = report?.scheduled.orEmpty().filter { it.start <= s.now }.map { it.key }.toSet() + records.map { it.key }
 val rate = if (expected.isEmpty()) null else report?.let { it.onTime * 100 / expected.size }

 LazyColumn(
  Modifier.fillMaxSize(),
  contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 40.dp),
  verticalArrangement = Arrangement.spacedBy(18.dp),
  horizontalAlignment = Alignment.CenterHorizontally
 ) {
  item(key = "header", contentType = "header") {
   Row(
    Modifier.widthIn(max = 780.dp).fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
   ) {
    IconButton(
     onClick = onBack,
     modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
     AppIcon(Icons.AutoMirrored.Rounded.ArrowBack, R.string.back)
    }
    Text(
     stringResource(R.string.reports),
     Modifier.weight(1f).padding(horizontal = 4.dp),
     style = MaterialTheme.typography.headlineSmall
    )
    IconButton(
     onClick = onSettings,
     modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
     AppIcon(Icons.Rounded.Settings, R.string.settings)
    }
   }
  }
  item(key = "weeks", contentType = "week controls") {
   ReportWeekControls(
    first = first,
    isCurrent = isCurrent,
    isUpcoming = first > currentWeek,
    p = p,
    locale = locale,
    onSelect = ::selectWeek,
    onCurrent = { selectedWeekDay = null },
    onOpen = { choosingWeek = true }
   )
  }
  if (report == null || budget == null) {
   item(key = "loading", contentType = "loading") { CircularProgressIndicator(Modifier.padding(12.dp)) }
  } else {
   val (week, days) = budget
   item(key = "adherence", contentType = "adherence") {
    AdherenceCard(
     days = days.map { it.first },
     records = records,
     scheduled = report.scheduled,
     onTime = report.onTime,
     expectedCount = expected.size,
     rate = rate,
     averageDelayMinutes = report.averageDelayMinutes,
     now = s.now,
     p = p,
     locale = locale,
     modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth()
    )
   }
   item(key = "weekly", contentType = "weekly") {
    WeeklyCard(
     budget = week,
     days = days,
     config = s.config,
     isCurrent = isCurrent,
     modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth()
    )
   }
   item(key = "history", contentType = "history") {
    BedtimeLogCard(
     records = records,
     p = p,
     locale = locale,
     modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth().then(if (reduced) Modifier else Modifier.animateItem())
    )
   }
  }
 }
 if (choosingWeek) {
  ReportWeekDialog(
   first = first,
   currentWeek = currentWeek,
   p = p,
   locale = locale,
   onSelect = ::selectWeek,
   onDismiss = { choosingWeek = false }
  )
 }
}

@Composable
internal fun AdherenceCard(
 days: List<LocalDate>,
 records: List<BedtimeEntity>,
 scheduled: List<SleepOccurrence>,
 onTime: Int,
 expectedCount: Int,
 rate: Int?,
 averageDelayMinutes: Long?,
 now: Instant,
 p: Preferences,
 locale: Locale,
 modifier: Modifier = Modifier
) {
 val reduced = LocalReducedMotion.current
 var inspectedDate by remember(days.firstOrNull()) { mutableStateOf<LocalDate?>(null) }

 Panel(modifier) {
  Text(stringResource(R.string.adherence), style = MaterialTheme.typography.titleLarge)
  Row(
   Modifier.fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Crossfade(rate, animationSpec = motionSpec(reduced), label = "adherence-rate") { percent ->
    Text(
     if (percent == null) "—" else number(percent, p, locale) + (if (locale.language == "fa") "٪" else "%"),
     Modifier.hideOutgoingContent(percent != rate),
     style = MaterialTheme.typography.displayMedium,
     color = MaterialTheme.colorScheme.primary
    )
   }
   Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
     if (expectedCount == 0) stringResource(R.string.no_scheduled_nights)
     else stringResource(R.string.nights_on_time, number(onTime, p, locale), number(expectedCount, p, locale)),
     style = MaterialTheme.typography.titleSmall
    )
    AnimatedVisibility(averageDelayMinutes != null, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
     val avg = averageDelayMinutes ?: 0L
     val avgText = when {
      avg.absoluteValue <= 2L -> stringResource(R.string.avg_on_schedule)
      avg < -2L -> stringResource(R.string.avg_early, number(avg.absoluteValue, p, locale))
      else -> stringResource(R.string.avg_late, number(avg, p, locale))
     }
     Text(avgText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
  }

  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
   for (date in days) key(date) {
    val record = records.firstOrNull { it.ownerDate == date.toString() }
    val occurrence = scheduled.firstOrNull { it.owner == date }
    val status = when {
     record != null -> {
      val diff = record.confirmed - record.scheduled
      when {
       diff.absoluteValue <= 900_000L -> DayAdherenceStatus.ON_TIME
       diff < 0 -> DayAdherenceStatus.EARLY
       else -> DayAdherenceStatus.LATE
      }
     }
     occurrence != null && occurrence.start < now -> DayAdherenceStatus.MISSED
     else -> DayAdherenceStatus.UPCOMING
    }
    val tint = when (status) {
     DayAdherenceStatus.ON_TIME -> FreeColor
     DayAdherenceStatus.EARLY -> EarlyColor
     DayAdherenceStatus.LATE -> LateColor
     DayAdherenceStatus.MISSED -> MaterialTheme.colorScheme.onSurfaceVariant
     DayAdherenceStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cellBg by animateColorAsState(
     when (status) {
      DayAdherenceStatus.ON_TIME, DayAdherenceStatus.EARLY, DayAdherenceStatus.LATE -> tint.copy(alpha = 0.18f)
      DayAdherenceStatus.MISSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      DayAdherenceStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
     },
     animationSpec = motionSpec(reduced),
     label = "adherence-day-bg"
    )
    val inspectable = status != DayAdherenceStatus.UPCOMING
    val isSelected = inspectedDate == date
    val shape = RoundedCornerShape(14.dp)
    val dayNumber = number(if (isJalali(p, locale)) PersianDates.fromGregorian(date).day else date.dayOfMonth, p, locale)
    val weekdayLetter = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)

    Surface(
     modifier = Modifier
      .weight(1f)
      .heightIn(min = 58.dp)
      .clip(shape)
      .clickable(enabled = inspectable) { inspectedDate = if (isSelected) null else date }
      .then(if (status == DayAdherenceStatus.UPCOMING) Modifier.alpha(0.45f) else Modifier),
     shape = shape,
     color = cellBg,
     border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
     Column(
      Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
     ) {
      Text(weekdayLetter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(dayNumber, style = MaterialTheme.typography.labelLarge)
      when (status) {
       DayAdherenceStatus.ON_TIME, DayAdherenceStatus.EARLY, DayAdherenceStatus.LATE -> {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
       }
       DayAdherenceStatus.MISSED -> {
        Box(Modifier.size(6.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), CircleShape))
       }
       DayAdherenceStatus.UPCOMING -> {
        Spacer(Modifier.size(6.dp))
       }
      }
     }
    }
   }
  }

  FlowRow(
   Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(12.dp),
   verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
   LegendDot(FreeColor, stringResource(R.string.on_time), filled = true)
   LegendDot(EarlyColor, stringResource(R.string.early), filled = true)
   LegendDot(LateColor, stringResource(R.string.late), filled = true)
   LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), stringResource(R.string.missed), filled = false)
  }

  AnimatedVisibility(inspectedDate != null, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   val date = inspectedDate
   if (date != null) {
    val record = records.firstOrNull { it.ownerDate == date.toString() }
    val occurrence = scheduled.firstOrNull { it.owner == date }
    val dayShort = dateText(date, p, locale, short = true)
    val weekdayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val header = "$weekdayShort, $dayShort"
    val line = if (record != null) {
     val planned = clockText(Instant.ofEpochMilli(record.scheduled), p, locale)
     val reported = clockText(Instant.ofEpochMilli(record.confirmed), p, locale)
     val diffMin = (record.confirmed - record.scheduled) / 60_000L
     val delta = when {
      diffMin.absoluteValue <= 15L -> stringResource(R.string.delta_on_time)
      diffMin < 0 -> stringResource(R.string.delta_early, number(diffMin.absoluteValue, p, locale))
      else -> stringResource(R.string.delta_late, number(diffMin, p, locale))
     }
     val arrow = stringResource(R.string.time_arrow, planned, reported)
     val base = "$header · $arrow ($delta)"
     if (record.snoozes > 0) "$base · ${stringResource(R.string.snoozed_count, number(record.snoozes, p, locale))}" else base
    } else if (occurrence != null) {
     stringResource(R.string.day_inspect_missed, header, clockText(occurrence.start, p, locale))
    } else ""
    if (line.isNotEmpty()) {
     Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.fillMaxWidth()
     ) {
      Text(
       line,
       Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurface
      )
     }
    }
   }
  }

  Text(
   stringResource(R.string.adherence_hint),
   style = MaterialTheme.typography.bodySmall,
   color = MaterialTheme.colorScheme.onSurfaceVariant
  )
 }
}

@Composable
private fun LegendDot(color: Color, label: String, filled: Boolean) {
 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
  Box(
   Modifier.size(7.dp).then(
    if (filled) Modifier.background(color, CircleShape)
    else Modifier.border(1.5.dp, color, CircleShape)
   )
  )
  Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
 }
}

@Composable
internal fun WeeklyCard(
 budget: TimeBudget,
 days: List<Pair<LocalDate, TimeBudget>>,
 config: Configuration,
 isCurrent: Boolean,
 modifier: Modifier = Modifier
) {
 val context = LocalContext.current
 val p = config.prefs
 val locale = LocalConfiguration.current.locales[0]
 val reduced = LocalReducedMotion.current
 var selectedDate by remember(days.firstOrNull()?.first) { mutableStateOf<LocalDate?>(null) }

 val workDuration = budget.breakdown[Kind.WORK] ?: Duration.ZERO
 val exerciseDuration = budget.breakdown[Kind.EXERCISE] ?: Duration.ZERO
 val showWork = workDuration > Duration.ZERO || config.work.any { it.slots.isNotEmpty() }
 val showExercise = exerciseDuration > Duration.ZERO || config.exercise.any { it.slots.isNotEmpty() }

 val visibleKinds = remember(showWork, showExercise) {
  buildList<Kind?> {
   add(Kind.SLEEP)
   if (showWork) add(Kind.WORK)
   if (showExercise) add(Kind.EXERCISE)
   add(null)
  }
 }

 Panel(modifier) {
  Text(stringResource(R.string.report_week_budget), style = MaterialTheme.typography.titleLarge)

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
   Crossfade(budget.free.seconds, animationSpec = motionSpec(reduced), label = "weekly-free-time") { seconds ->
    Text(
     stringResource(R.string.week_free_hero, duration(context, seconds, p)),
     Modifier.hideOutgoingContent(seconds != budget.free.seconds),
     style = MaterialTheme.typography.headlineLarge,
     color = MaterialTheme.colorScheme.secondary
    )
   }
   if (isCurrent) {
    Text(
     stringResource(R.string.report_week_remaining, duration(context, budget.remainingFree.seconds, p)),
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
   }
  }

  Row(
   Modifier.fillMaxWidth().height(18.dp).clip(CircleShape),
   horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
   for (kind in visibleKinds) {
    val seconds = if (kind == null) budget.free.seconds else budget.breakdown[kind]?.seconds ?: 0L
    val segmentTitle = stringResource(kindTitle(kind))
    val weight by animateFloatAsState(seconds.toFloat(), animationSpec = motionSpec(reduced, 300), label = "budget-segment")
    if (weight > 0f) {
     Box(
      Modifier
       .weight(weight)
       .fillMaxHeight()
       .clip(RectangleShape)
       .background(kindColor(kind))
       .semantics {
        if (seconds > 0) contentDescription = "$segmentTitle: ${duration(context, seconds, p)}"
        else hideFromAccessibility()
       }
     )
    }
   }
  }

  FlowRow(
   Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(16.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   for (kind in visibleKinds) {
    val seconds = if (kind == null) budget.free.seconds else budget.breakdown[kind]?.seconds ?: 0L
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
     Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(kind)))
     Text(
      stringResource(R.string.category_summary, stringResource(kindTitle(kind)), duration(context, seconds, p)),
      style = MaterialTheme.typography.labelMedium
     )
    }
   }
  }

  Row(
   Modifier.fillMaxWidth().height(92.dp),
   horizontalArrangement = Arrangement.spacedBy(8.dp),
   verticalAlignment = Alignment.Bottom
  ) {
   val today = LocalDate.now(p.zone())
   for ((date, day) in days) key(date) {
    val isSelected = selectedDate == date
    val isToday = date == today
    Column(
     Modifier
      .weight(1f)
      .fillMaxHeight()
      .clip(RoundedCornerShape(10.dp))
      .clickable { selectedDate = if (isSelected) null else date },
     horizontalAlignment = Alignment.CenterHorizontally,
     verticalArrangement = Arrangement.Bottom
    ) {
     val totalSec = Duration.between(day.start, day.end).seconds.coerceAtLeast(1L)
     val fraction = (day.free.seconds.toFloat() / totalSec).coerceIn(.02f, 1f)
     val height by animateDpAsState((fraction * 56 + 4).dp, animationSpec = motionSpec(reduced, 300), label = "daily-budget")
     Box(
      Modifier
       .fillMaxWidth(.65f)
       .height(height)
       .clip(RoundedCornerShape(7.dp))
       .background(
        when {
         isSelected -> FreeColor
         isToday -> FreeColor.copy(alpha = 0.85f)
         else -> FreeColor.copy(alpha = 0.42f)
        }
       )
       .then(if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp)) else Modifier)
     )
     Spacer(Modifier.height(10.dp))
     Text(
      date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (isSelected) FontWeight.Bold else null,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
     )
    }
   }
  }

  AnimatedVisibility(selectedDate != null, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   val picked = days.firstOrNull { it.first == selectedDate }
   if (picked != null) {
    val (date, day) = picked
    val header = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}, ${dateText(date, p, locale, short = true)}"
    val parts = buildList {
     add(stringResource(R.string.category_summary, stringResource(R.string.free), duration(context, day.free.seconds, p)))
     val sleepSec = day.breakdown[Kind.SLEEP]?.seconds ?: 0L
     if (sleepSec > 0L) add(stringResource(R.string.category_summary, stringResource(R.string.sleep), duration(context, sleepSec, p)))
     val dayWorkSec = day.breakdown[Kind.WORK]?.seconds ?: 0L
     if (dayWorkSec > 0L) add(stringResource(R.string.category_summary, stringResource(R.string.work), duration(context, dayWorkSec, p)))
     val dayExSec = day.breakdown[Kind.EXERCISE]?.seconds ?: 0L
     if (dayExSec > 0L) add(stringResource(R.string.category_summary, stringResource(R.string.exercise), duration(context, dayExSec, p)))
    }
    Surface(
     shape = RoundedCornerShape(12.dp),
     color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
     modifier = Modifier.fillMaxWidth()
    ) {
     Text(
      "$header · ${parts.joinToString(" · ")}",
      Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      style = MaterialTheme.typography.bodySmall
     )
    }
   }
  }

  if (budget.workdays > 0 || budget.holidaysAffectingWork > 0) {
   val footnote = buildList {
    if (budget.workdays > 0) add(pluralStringResource(R.plurals.workdays_count, budget.workdays, number(budget.workdays, p, locale)))
    if (budget.holidaysAffectingWork > 0) add(pluralStringResource(R.plurals.holidays_off_count, budget.holidaysAffectingWork, number(budget.holidaysAffectingWork, p, locale)))
   }.joinToString(" · ")
   Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }

  AnimatedVisibility(budget.uncertainDates.isNotEmpty(), enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   Text(
    stringResource(R.string.holiday_unknown),
    Modifier.hideOutgoingContent(budget.uncertainDates.isEmpty()),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
}

@Composable
internal fun BedtimeLogCard(
 records: List<BedtimeEntity>,
 p: Preferences,
 locale: Locale,
 modifier: Modifier = Modifier
) {
 Panel(modifier) {
  Text(stringResource(R.string.reported_history), style = MaterialTheme.typography.titleLarge)
  if (records.isEmpty()) {
   Text(stringResource(R.string.no_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
  } else {
   Column {
    records.forEachIndexed { index, record ->
     if (index > 0) {
      HorizontalDivider(
       Modifier.padding(vertical = 10.dp),
       color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
      )
     }
     val delayMs = record.confirmed - record.scheduled
     val delayMin = delayMs / 60_000L
     val dotColor = when {
      delayMs.absoluteValue <= 900_000L -> FreeColor
      delayMs < 0 -> EarlyColor
      else -> LateColor
     }
     val statusText = when {
      delayMs.absoluteValue <= 900_000L -> stringResource(R.string.on_time)
      delayMs < 0 -> stringResource(R.string.log_early_short, number(delayMin.absoluteValue, p, locale))
      else -> stringResource(R.string.log_late_short, number(delayMin, p, locale))
     }
     val date = LocalDate.parse(record.ownerDate)
     val dateLabel = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}, ${dateText(date, p, locale, short = true)}"
     val planned = clockText(Instant.ofEpochMilli(record.scheduled), p, locale)
     val reported = clockText(Instant.ofEpochMilli(record.confirmed), p, locale)
     val arrow = stringResource(R.string.time_arrow, planned, reported)
     val subLine = if (record.snoozes > 0) {
      "$arrow · ${stringResource(R.string.snoozed_count, number(record.snoozes, p, locale))}"
     } else arrow

     Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
     ) {
      Box(Modifier.size(8.dp).background(dotColor, CircleShape))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
       Text(dateLabel, style = MaterialTheme.typography.titleSmall)
       Text(subLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Surface(
       shape = RoundedCornerShape(10.dp),
       color = dotColor.copy(alpha = 0.16f)
      ) {
       Text(
        statusText,
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface
       )
      }
     }
    }
   }
  }
 }
}

@Composable
private fun ReportWeekControls(
 first: LocalDate,
 isCurrent: Boolean,
 isUpcoming: Boolean,
 p: Preferences,
 locale: Locale,
 onSelect: (LocalDate) -> Unit,
 onCurrent: () -> Unit,
 onOpen: () -> Unit
) {
 val reduced = LocalReducedMotion.current
 Column(Modifier.widthIn(max = 780.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   IconButton(
    onClick = { onSelect(first.minusWeeks(1)) },
    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
   ) {
    AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, R.string.previous_week)
   }
   Surface(
    onClick = onOpen,
    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
   ) {
    Row(
     Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
     verticalAlignment = Alignment.CenterVertically,
     horizontalArrangement = Arrangement.Center
    ) {
     AppIcon(Icons.Rounded.DateRange, null, modifier = Modifier.size(18.dp))
     Spacer(Modifier.width(8.dp))
     Text(
      weekRangeText(first, p, locale),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
     )
    }
   }
   IconButton(
    onClick = { onSelect(first.plusWeeks(1)) },
    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
   ) {
    AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, R.string.next_week)
   }
  }
  AnimatedVisibility(!isCurrent, enter = sectionEnter(reduced), exit = sectionExit(reduced)) {
   Row(
    Modifier.padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    if (isUpcoming) {
     Text(
      stringResource(R.string.upcoming_week),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
    TextButton(onClick = onCurrent) { Text(stringResource(R.string.this_week)) }
   }
  }
 }
}

@Composable
internal fun weekRangeText(first: LocalDate, p: Preferences, locale: Locale): String {
 val (start, end) = remember(first, p, locale) {
  val last = first.plusDays(6)
  val startYear = if (isJalali(p, locale)) PersianDates.fromGregorian(first).year else first.year
  val endYear = if (isJalali(p, locale)) PersianDates.fromGregorian(last).year else last.year
  val startLabel = dateText(first, p, locale, true)
  val endLabel = dateText(last, p, locale, true) + " " + number(endYear, p, locale)
  (if (startYear == endYear) startLabel else startLabel + " " + number(startYear, p, locale)) to endLabel
 }
 return stringResource(R.string.week_date_range, start, end)
}

@Composable
internal fun ReportWeekDialog(
 first: LocalDate,
 currentWeek: LocalDate,
 p: Preferences,
 locale: Locale,
 onSelect: (LocalDate) -> Unit,
 onDismiss: () -> Unit
) {
 if (isJalali(p, locale)) {
  JalaliDatePickerDialog(
   initialDate = first,
   p = p,
   locale = locale,
   highlightWeek = true,
   todayActionLabel = R.string.this_week,
   onSelect = { picked -> onSelect(TimeEngine.weekStart(picked, p.weekStart)) },
   onDismiss = onDismiss
  )
  return
 }
 val size = LocalWindowInfo.current.containerSize
 val compact = with(LocalDensity.current) { size.height.toDp() < 560.dp || size.width.toDp() < 360.dp }
 val years = DatePickerDefaults.YearRange
 val state = rememberDatePickerState(
  initialSelectedDateMillis = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
  yearRange = minOf(years.first, first.year, currentWeek.year)..maxOf(years.last, first.year, currentWeek.year),
  initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker
 )
 val week = state.selectedDateMillis?.let { weekStartFromCalendarSelection(it, p.weekStart) }
 DatePickerDialog(
  onDismissRequest = onDismiss,
  confirmButton = {
   TextButton(onClick = { week?.let(onSelect); onDismiss() }, enabled = week != null) {
    Text(stringResource(R.string.done))
   }
  },
  dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
 ) {
  DatePicker(
   state = state,
   modifier = Modifier.verticalScroll(rememberScrollState()),
   title = { Text(stringResource(R.string.choose_week), Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)) },
   headline = {
    if (week != null) {
     Text(
      weekRangeText(week, p, locale),
      Modifier.padding(start = 24.dp, end = 4.dp, bottom = 8.dp),
      style = MaterialTheme.typography.titleLarge
     )
    }
   },
   focusRequester = null
  )
 }
}

@Composable
internal fun JalaliDatePickerDialog(
 initialDate: LocalDate,
 p: Preferences,
 locale: Locale,
 highlightWeek: Boolean,
 todayActionLabel: Int,
 onSelect: (LocalDate) -> Unit,
 onDismiss: () -> Unit
) {
 val initialJalali = remember(initialDate) { PersianDates.fromGregorian(initialDate) }
 var jYear by remember { mutableIntStateOf(initialJalali.year) }
 var jMonth by remember { mutableIntStateOf(initialJalali.month) }
 var selectedDate by remember { mutableStateOf(initialDate) }
 val today = remember(p.zoneId) { LocalDate.now(p.zone()) }
 val selectedWeek = remember(selectedDate, p.weekStart) { TimeEngine.weekStart(selectedDate, p.weekStart) }
 val weekdays = remember(p.weekStart) { List(7) { DayOfWeek.of((p.weekStart - 1 + it) % 7 + 1) } }
 val firstOfMonth = remember(jYear, jMonth) { PersianDates.toGregorian(jYear, jMonth, 1) }
 val daysInMonth = remember(jYear, jMonth) {
  when {
   jMonth <= 6 -> 31
   jMonth <= 11 -> 30
   else -> ChronoUnit.DAYS.between(PersianDates.newYear(jYear), PersianDates.newYear(jYear + 1)).toInt() - 336
  }
 }
 val leadingBlanks = (firstOfMonth.dayOfWeek.value - p.weekStart + 7) % 7
 val totalRows = (leadingBlanks + daysInMonth + 6) / 7

 Dialog(onDismissRequest = onDismiss) {
  Surface(
   shape = RoundedCornerShape(28.dp),
   color = MaterialTheme.colorScheme.surface,
   modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth()
  ) {
   Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
     stringResource(if (highlightWeek) R.string.choose_week else R.string.choose_date),
     style = MaterialTheme.typography.labelLarge,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
     if (highlightWeek) weekRangeText(selectedWeek, p, locale) else dateText(selectedDate, p, locale),
     style = MaterialTheme.typography.titleLarge
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     IconButton(
      onClick = {
       if (jMonth == 1) { jYear -= 1; jMonth = 12 } else { jMonth -= 1 }
      },
      modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
     ) {
      AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, R.string.previous_week)
     }
     Text(
      "${jalaliMonthName(jMonth, locale)} ${number(jYear, p, locale)}",
      Modifier.weight(1f),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleMedium
     )
     IconButton(
      onClick = {
       if (jMonth == 12) { jYear += 1; jMonth = 1 } else { jMonth += 1 }
      },
      modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
     ) {
      AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, R.string.next_week)
     }
    }
    Row(Modifier.fillMaxWidth()) {
     for (day in weekdays) {
      Text(
       day.getDisplayName(TextStyle.NARROW, locale),
       Modifier.weight(1f),
       textAlign = TextAlign.Center,
       style = MaterialTheme.typography.labelSmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
     for (row in 0 until totalRows) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
       for (col in 0 until 7) {
        val dayNum = row * 7 + col - leadingBlanks + 1
        if (dayNum !in 1..daysInMonth) {
         Spacer(Modifier.weight(1f).height(40.dp))
        } else {
         val cellDate = firstOfMonth.plusDays((dayNum - 1).toLong())
         val isSelected = if (highlightWeek) {
          cellDate >= selectedWeek && cellDate < selectedWeek.plusDays(7)
         } else {
          cellDate == selectedDate
         }
         val isToday = cellDate == today
         Surface(
          onClick = { selectedDate = cellDate },
          modifier = Modifier.weight(1f).height(40.dp),
          shape = RoundedCornerShape(10.dp),
          color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
          border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
         ) {
          Box(contentAlignment = Alignment.Center) {
           Text(
            number(dayNum, p, locale),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
           )
          }
         }
        }
       }
      }
     }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     TextButton(onClick = {
      val t = LocalDate.now(p.zone())
      val jd = PersianDates.fromGregorian(t)
      jYear = jd.year
      jMonth = jd.month
      selectedDate = t
     }) {
      Text(stringResource(todayActionLabel))
     }
     Spacer(Modifier.weight(1f))
     TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
     TextButton(onClick = { onSelect(selectedDate); onDismiss() }) { Text(stringResource(R.string.done)) }
    }
   }
  }
 }
}

internal fun weekStartFromCalendarSelection(utcMillis: Long, firstDay: Int): LocalDate =
 TimeEngine.weekStart(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate(), firstDay)
