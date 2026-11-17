package dev.sleeptime.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sleeptime.R
import dev.sleeptime.core.*
import java.time.*
import kotlin.math.absoluteValue

@Composable fun StatisticsScreen(s: DashboardState,vm: AppViewModel,onBack: ()->Unit) {
 val context=LocalContext.current;val p=s.config.prefs;val locale=LocalConfiguration.current.locales[0]
 var offset by rememberSaveable {mutableIntStateOf(0)}
 val first=TimeEngine.weekStart(s.now.atZone(p.zone()).toLocalDate(),p.weekStart).plusWeeks(offset.toLong())
 val week=remember(s.config,offset,s.now.epochSecond/60){TimeEngine.budget(s.config,first,7,s.now,vm.holidays)}
 val days=remember(s.config,offset,s.now.epochSecond/60){(0L..6L).map {first.plusDays(it).let {d->d to TimeEngine.budget(s.config,d,1,s.now,vm.holidays)}}}
 val records=s.history.filter {LocalDate.parse(it.ownerDate)>=first && LocalDate.parse(it.ownerDate)<first.plusDays(7)}
 val expected=(0L..6L).mapNotNull {TimeEngine.sleep(s.config,first.plusDays(it))}.filter {it.start<=s.now}.map {it.key}.toSet()+records.map {it.key}
 val onTime=records.count {(it.confirmed-it.scheduled).absoluteValue<=900_000L}
 val rate=if(expected.isEmpty())null else onTime*100/expected.size
 var selected by remember {mutableStateOf<dev.sleeptime.data.BedtimeEntity?>(null)}
 LazyColumn(Modifier.fillMaxSize().widthIn(max=800.dp),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  item {PageHeader(R.string.statistics,onBack)}
  item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={offset--}){AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft,R.string.previous_week)};TextButton(onClick={offset=0},modifier=Modifier.weight(1f)){Text(stringResource(R.string.this_week))};IconButton(onClick={offset++}){AppIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,R.string.next_week)}}}
  item {WeeklyCard(week,days,p)}
  item {Panel(Modifier.fillMaxWidth()) {
   Text(stringResource(R.string.adherence),style=MaterialTheme.typography.titleLarge)
   Text(if(rate==null) "—" else number(rate,p)+"%",style=MaterialTheme.typography.displayMedium,color=MaterialTheme.colorScheme.primary)
   Text(stringResource(R.string.adherence_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    for((date,_)in days) {
     val record=records.firstOrNull {it.ownerDate==date.toString()}
     val color=if(record==null)MaterialTheme.colorScheme.surfaceVariant else if((record.confirmed-record.scheduled).absoluteValue<=900000)FreeColor else if(record.confirmed<record.scheduled)WorkColor else ExerciseColor
     Surface(Modifier.weight(1f).heightIn(min=60.dp).clickable(enabled=record!=null){selected=record},shape=RoundedCornerShape(16.dp),color=color.copy(alpha=if(record==null)1f else .25f)){Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(number(if(p.jalali)PersianDates.fromGregorian(date).day else date.dayOfMonth,p),style=MaterialTheme.typography.labelLarge);if(record!=null)Box(Modifier.padding(top=6.dp).size(5.dp).background(color,CircleShape))}}
    }
   }
   if(records.isNotEmpty()) {val average=records.map {it.confirmed-it.scheduled}.average().toLong()/60000;Text(stringResource(R.string.average_delay)+" · "+(if(average<0)"−"else "")+stringResource(R.string.minutes_short,number(average.absoluteValue,p)),style=MaterialTheme.typography.bodyMedium)}
   Text(stringResource(R.string.workdays,number(week.workdays,p)),style=MaterialTheme.typography.bodySmall)
   Text(stringResource(R.string.holidays_work,number(week.holidaysAffectingWork,p)),style=MaterialTheme.typography.bodySmall)
  }}
  item {Text(stringResource(R.string.reported_history),style=MaterialTheme.typography.titleLarge)}
  if(records.isEmpty())item{Panel(Modifier.fillMaxWidth()){Text(stringResource(R.string.no_history),color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  for(record in records.sortedByDescending {it.confirmed})item(key=record.key){Panel(Modifier.fillMaxWidth().clickable {selected=record}){
   val delay=record.confirmed-record.scheduled
   Text(dateText(LocalDate.parse(record.ownerDate),p,locale),style=MaterialTheme.typography.titleMedium)
   Text(stringResource(R.string.planned,clockText(Instant.ofEpochMilli(record.scheduled),p,locale)),style=MaterialTheme.typography.bodyMedium)
   Text(stringResource(R.string.reported,clockText(Instant.ofEpochMilli(record.confirmed),p,locale)),style=MaterialTheme.typography.bodyMedium)
   Text(stringResource(if(delay.absoluteValue<=900000)R.string.on_time else if(delay<0)R.string.early else R.string.late),color=if(delay.absoluteValue<=900000)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
  }}
  item{Spacer(Modifier.height(24.dp))}
 }
 selected?.let {r->AlertDialog(onDismissRequest={selected=null},title={Text(stringResource(R.string.reported_bedtime))},text={Column{Text(dateText(LocalDate.parse(r.ownerDate),p,locale));Text(stringResource(R.string.planned,clockText(Instant.ofEpochMilli(r.scheduled),p,locale)));Text(stringResource(R.string.reported,clockText(Instant.ofEpochMilli(r.confirmed),p,locale)))}},confirmButton={TextButton(onClick={selected=null}){Text(stringResource(R.string.done))}})}
}
