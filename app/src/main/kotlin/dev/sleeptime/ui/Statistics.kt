package dev.sleeptime.ui
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sleeptime.R
import dev.sleeptime.core.*
import dev.sleeptime.data.BedtimeEntity
import java.time.*
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class WeekReport(
 val records: List<BedtimeEntity>,
 val scheduled: List<SleepOccurrence>,
 val onTime: Int,
 val averageDelayMinutes: Long?,
 val projectedBudget: Pair<TimeBudget,List<Pair<LocalDate,TimeBudget>>>?,
)

@Composable fun StatisticsScreen(s: DashboardState,vm: AppViewModel,onEditor: (String)->Unit) {
 val p=s.config.prefs
 val locale=LocalConfiguration.current.locales[0]
 val reduced=LocalReducedMotion.current
 var selectedWeekDay by rememberSaveable {mutableStateOf<Long?>(null)}
 var choosingWeek by rememberSaveable {mutableStateOf(false)}
 val currentWeek=TimeEngine.weekStart(s.now.atZone(p.zone()).toLocalDate(),p.weekStart)
 val first=selectedWeekDay?.let {TimeEngine.weekStart(LocalDate.ofEpochDay(it),p.weekStart)} ?: currentWeek
 val isCurrent=first==currentWeek
 fun selectWeek(week: LocalDate) {selectedWeekDay=week.takeUnless {it==currentWeek}?.toEpochDay()}

 val report=key(first,currentWeek,s.config,s.history,s.holidayRevision) {
  val loaded by produceState<WeekReport?>(null) {
   value=withContext(Dispatchers.Default) {
    val end=first.plusWeeks(1)
    val records=s.history.filter {record ->
     ensureActive()
     val date=LocalDate.parse(record.ownerDate)
     date in first..<end
    }
    val schedule=(0L..6L).mapNotNull {TimeEngine.sleep(s.config,first.plusDays(it))}
    val onTime=records.count {(it.confirmed-it.scheduled).absoluteValue<=900_000L}
    val average=if(records.isEmpty()) null else records.asSequence().map {it.confirmed-it.scheduled}.average().toLong()/60_000L
    val budget=if(isCurrent) null else {
     val at=(if(first<currentWeek) end else first).atStartOfDay(p.zone()).toInstant()
     val days=(0L..6L).map {
      val day=first.plusDays(it)
      day to TimeEngine.budget(s.config,day,1,at,vm.holidays)
     }
     TimeEngine.budget(s.config,first,7,at,vm.holidays) to days
    }
    WeekReport(records,schedule,onTime,average,budget)
   }
  }
  loaded
 }
 val records=report?.records.orEmpty()
 val budget=if(isCurrent) s.week to s.days else report?.projectedBudget
 val expected=report?.scheduled.orEmpty().filter {it.start<=s.now}.map {it.key}.toSet()+records.map {it.key}
 val rate=if(expected.isEmpty()) null else report?.let {it.onTime*100/expected.size}
 var selected by remember {mutableStateOf<BedtimeEntity?>(null)}

 LazyColumn(
  Modifier.fillMaxSize(),
  contentPadding=PaddingValues(start=20.dp,top=20.dp,end=20.dp,bottom=48.dp),
  verticalArrangement=Arrangement.spacedBy(18.dp),
  horizontalAlignment=Alignment.CenterHorizontally
 ) {
  item(key="title",contentType="title") {
   Text(stringResource(R.string.statistics),Modifier.widthIn(max=780.dp).fillMaxWidth(),style=MaterialTheme.typography.headlineSmall)
  }
  item(key="weeks",contentType="week controls") {
   ReportWeekControls(first,isCurrent,p,locale,onSelect=::selectWeek,onCurrent={selectedWeekDay=null},onOpen={choosingWeek=true})
  }
  if (report == null || budget == null) {
   item(key="loading",contentType="loading") { CircularProgressIndicator(Modifier.padding(12.dp)) }
  } else {
   val (week, days) = budget
   item(key="weekly",contentType="weekly") {WeeklyCard(week,days,p,Modifier.widthIn(max=780.dp).fillMaxWidth())}
   item(key="adherence",contentType="adherence") {Panel(Modifier.widthIn(max=780.dp).fillMaxWidth()) {
   Text(stringResource(R.string.adherence),style=MaterialTheme.typography.titleLarge)
   Crossfade(rate,animationSpec=motionSpec(reduced),label="adherence-rate") { percent ->
    Text(if(percent==null) "—" else number(percent,p,locale)+(if(locale.language=="fa") "٪" else "%"),Modifier.hideOutgoingContent(percent!=rate),style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary)
   }
   Text(stringResource(R.string.adherence_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    for((date,_)in days) key(date) {
     val record=records.firstOrNull {it.ownerDate==date.toString()}
     val color=if(record==null)MaterialTheme.colorScheme.surfaceVariant else if((record.confirmed-record.scheduled).absoluteValue<=900000)FreeColor else if(record.confirmed<record.scheduled)WorkColor else ExerciseColor
     val background by animateColorAsState(color.copy(alpha=if(record==null)1f else .25f),animationSpec=motionSpec(reduced),label="adherence-day")
     val shape=RoundedCornerShape(16.dp)
     Surface(Modifier.weight(1f).heightIn(min=60.dp).clip(shape).clickable(enabled=record!=null){selected=record},shape=shape,color=background) {
      Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
       Text(number(if(isJalali(p, locale)) PersianDates.fromGregorian(date).day else date.dayOfMonth,p,locale),style=MaterialTheme.typography.labelLarge)
       AnimatedVisibility(record!=null,enter=sectionEnter(reduced),exit=sectionExit(reduced)) {
        Box(Modifier.padding(top=6.dp).size(5.dp).background(color,CircleShape))
       }
      }
     }
    }
   }
   val average=report.averageDelayMinutes
   AnimatedVisibility(average!=null,enter=sectionEnter(reduced),exit=sectionExit(reduced)) {
    Crossfade(average,animationSpec=motionSpec(reduced),label="average-bedtime-delay") { delay ->
     if(delay!=null) Text(stringResource(R.string.average_delay)+" · "+(if (delay < 0) "−" else "")+stringResource(R.string.minutes_short,number(delay.absoluteValue,p,locale)),Modifier.hideOutgoingContent(delay!=average),style=MaterialTheme.typography.bodyMedium)
    }
   }
   Text(stringResource(R.string.workdays,number(week.workdays,p,locale)),style=MaterialTheme.typography.bodySmall)
   Text(stringResource(R.string.holidays_work,number(week.holidaysAffectingWork,p,locale)),style=MaterialTheme.typography.bodySmall)
   }}
  }
  if(isCurrent) item(key="routine",contentType="routine") { RoutineCard(s,vm,onEditor) }
  if(report!=null) {
   item(key="history title",contentType="title") {
    Text(stringResource(R.string.reported_history),Modifier.widthIn(max=780.dp).fillMaxWidth(),style=MaterialTheme.typography.titleLarge)
   }
   if(records.isEmpty()) item(key="empty_history",contentType="empty history") {
    Panel(Modifier.widthIn(max=780.dp).fillMaxWidth().then(if(reduced) Modifier else Modifier.animateItem())) {
     Text(stringResource(R.string.no_history),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   items(records,key={it.key},contentType={"bedtime record"}) { record ->
    Panel(Modifier.widthIn(max=780.dp).fillMaxWidth().then(if(reduced) Modifier else Modifier.animateItem()).clip(CardShape).clickable {selected=record}) {
     val delay=record.confirmed-record.scheduled
     Text(dateText(LocalDate.parse(record.ownerDate),p,locale),style=MaterialTheme.typography.titleMedium)
     Text(stringResource(R.string.planned,clockText(Instant.ofEpochMilli(record.scheduled),p,locale)),style=MaterialTheme.typography.bodyMedium)
     Text(stringResource(R.string.reported,clockText(Instant.ofEpochMilli(record.confirmed),p,locale)),style=MaterialTheme.typography.bodyMedium)
     Text(stringResource(if(delay.absoluteValue<=900000)R.string.on_time else if(delay<0)R.string.early else R.string.late),color=if(delay.absoluteValue<=900000)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
   }
  }
 }
 if(choosingWeek) ReportWeekDialog(first,p,locale,onSelect=::selectWeek,onDismiss={choosingWeek=false})
 selected?.let {r->AlertDialog(onDismissRequest={selected=null},title={Text(stringResource(R.string.reported_bedtime))},text={Column{Text(dateText(LocalDate.parse(r.ownerDate),p,locale));Text(stringResource(R.string.planned,clockText(Instant.ofEpochMilli(r.scheduled),p,locale)));Text(stringResource(R.string.reported,clockText(Instant.ofEpochMilli(r.confirmed),p,locale)))}},confirmButton={TextButton(onClick={selected=null}){Text(stringResource(R.string.done))}})}
}

@Composable private fun ReportWeekControls(
 first: LocalDate,isCurrent: Boolean,p: Preferences,locale: Locale,
 onSelect: (LocalDate)->Unit,onCurrent: ()->Unit,onOpen: ()->Unit
) {
 Column(Modifier.widthIn(max=780.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick={onSelect(first.minusWeeks(1))}) {
    AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft,R.string.previous_week)
   }
   OutlinedButton(onClick=onOpen,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)) {
    AppIcon(Icons.Rounded.DateRange,null,modifier=Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
     Text(weekRangeText(first,p,locale),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
     Text(stringResource(R.string.choose_week),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelSmall)
    }
   }
   IconButton(onClick={onSelect(first.plusWeeks(1))}) {
    AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,R.string.next_week)
   }
  }
  if(!isCurrent) TextButton(onClick=onCurrent) {Text(stringResource(R.string.this_week))}
 }
}

@Composable private fun weekRangeText(first: LocalDate,p: Preferences,locale: Locale): String {
 val (start,end)=remember(first,p,locale) {
  val last=first.plusDays(6)
  val startYear=if(isJalali(p, locale)) PersianDates.fromGregorian(first).year else first.year
  val endYear=if(isJalali(p, locale)) PersianDates.fromGregorian(last).year else last.year
  val startLabel=dateText(first,p,locale,true)
  val endLabel=dateText(last,p,locale,true)+" "+number(endYear,p,locale)
  (if(startYear==endYear) startLabel else startLabel+" "+number(startYear,p,locale)) to endLabel
 }
 return stringResource(R.string.week_date_range,start,end)
}

@Composable private fun ReportWeekDialog(
 first: LocalDate,p: Preferences,locale: Locale,onSelect: (LocalDate)->Unit,onDismiss: ()->Unit
) {
 val size=LocalWindowInfo.current.containerSize
 val compact=with(LocalDensity.current) {size.height.toDp()<560.dp || size.width.toDp()<360.dp}
 val years=DatePickerDefaults.YearRange
 val state=rememberDatePickerState(
  initialSelectedDateMillis=first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
  yearRange=minOf(years.first,first.year)..maxOf(years.last,first.year),
  initialDisplayMode=if(compact) DisplayMode.Input else DisplayMode.Picker
 )
 val week=state.selectedDateMillis?.let {weekStartFromCalendarSelection(it,p.weekStart)}
 DatePickerDialog(
  onDismissRequest=onDismiss,
  confirmButton={
   TextButton(onClick={week?.let(onSelect);onDismiss()},enabled=week!=null) {Text(stringResource(R.string.done))}
  },
  dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}}
 ) {
  DatePicker(
   state=state,
   modifier=Modifier.verticalScroll(rememberScrollState()),
   title={Text(stringResource(R.string.choose_week),Modifier.padding(start=24.dp,top=16.dp,end=12.dp))},
   headline={
    if(week!=null) Text(
     weekRangeText(week,p,locale),
     Modifier.padding(start=24.dp,end=4.dp,bottom=8.dp),
     style=MaterialTheme.typography.titleLarge
    )
   },
   focusRequester=null
  )
 }
}

internal fun weekStartFromCalendarSelection(utcMillis: Long,firstDay: Int): LocalDate =
 TimeEngine.weekStart(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate(),firstDay)
