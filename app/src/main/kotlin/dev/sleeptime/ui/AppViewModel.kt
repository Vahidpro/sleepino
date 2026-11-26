package dev.sleeptime.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sleeptime.core.*
import dev.sleeptime.data.*
import dev.sleeptime.platform.ReminderController
import dev.sleeptime.widgets.refreshWidgets
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

data class DashboardState(val config: Configuration, val now: Instant, val occurrence: SleepOccurrence?, val confirmed: Boolean, val nextWork: Span?, val today: TimeBudget, val week: TimeBudget, val days: List<Pair<LocalDate,TimeBudget>>, val history: List<BedtimeEntity>, val activeSession: ActiveSession?, val holidayRevision: Int = 0, val holidays: HolidayProvider? = null)
@HiltViewModel class AppViewModel @Inject constructor(
 val store: ConfigurationStore, private val historyDao: HistoryDao, val holidays: HolidayRepository,
 val controller: ReminderController, private val backup: BackupManager, val clock: Clock,
 @param:ApplicationContext private val context: Context
): ViewModel() {
 private val dataMutex=kotlinx.coroutines.sync.Mutex()
 private val refresh = MutableStateFlow(0)
 val message = MutableSharedFlow<Int>(extraBufferCapacity=8)
 val pendingBackup = MutableStateFlow<Backup?>(null)

 @OptIn(ExperimentalCoroutinesApi::class)
 val state: StateFlow<DashboardState?> = combine(
  store.configuration,historyDao.history(),store.activeSession,refresh
 ) { config, history, session, revision ->
  flow {
   val zone = config.prefs.zone()
   var budgetDate: LocalDate? = null
   var budgets: Triple<TimeBudget,TimeBudget,List<Pair<LocalDate,TimeBudget>>>? = null
   while (currentCoroutineContext().isActive) {
    val now = clock.instant()
    val date = now.atZone(zone).toLocalDate()
    if (budgetDate != date) {
     val first = TimeEngine.weekStart(date,config.prefs.weekStart)
     val days = (0L..6L).map {
      val day = first.plusDays(it)
      day to TimeEngine.budget(config,day,1,now,holidays)
     }
     budgets = Triple(days.first { it.first == date }.second,TimeEngine.budget(config,first,7,now,holidays),days)
     budgetDate = date
    }
    val (today,week,days) = checkNotNull(budgets)
    val occurrence = TimeEngine.activeSleep(config,now)
    val active = session?.takeIf { it.key == occurrence?.key && it.scheduled == occurrence.start.toEpochMilli() }
    emit(DashboardState(config,now,occurrence,history.any { it.key == occurrence?.key },TimeEngine.activeOrNextWork(config,now,holidays),
     today.at(now),week.at(now),days,history,active,revision,holidays))
    val clockNow = clock.millis()
    val displayNow = now.toEpochMilli()
    val nextMinute = displayNow - displayNow.mod(60_000L) + 60_000L
    val minuteDelay = (nextMinute - clockNow).coerceAtLeast(1L)
    val snoozeDelay = active?.snoozedUntil?.takeIf { it > displayNow }
     ?.let { (it - clockNow).coerceAtLeast(1L) }
    delay(minOf(minuteDelay,snoozeDelay ?: minuteDelay))
   }
  }
 }.flatMapLatest { it }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)

 init {
  viewModelScope.launch(Dispatchers.IO) {
   var first = true
   store.configuration.collect {
    controller.reconcile()
    if (!first) refreshWidgets(context)
    first = false
   }
  }
 }
 fun update(transform: (Configuration) -> Configuration) = action { store.update(transform) }
 fun confirm(onRecorded: (String) -> Unit = {}) = action { controller.confirm()?.let { withContext(Dispatchers.Main) { onRecorded(it) } } }
 fun undo(key: String) = action { controller.undo(key) }
 fun snooze(minutes: Int) = action { if(!controller.snooze(minutes)) message.emit(dev.sleeptime.R.string.snooze_limit) }
 fun cancelSnooze(key: String) = action { controller.cancelSnooze(key) }
 fun resume() = action { refresh.update { it + 1 }; controller.reconcile() }
 fun export(uri: Uri) = action { context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(backup.export()) }; message.emit(dev.sleeptime.R.string.success) }
 fun readBackup(uri: Uri) = action { pendingBackup.value = backup.validate(readBounded(uri,10_000_000)) }
 fun restore() = action { pendingBackup.value?.let { backup.restore(it); pendingBackup.value=null; refresh.update { revision -> revision + 1 }; message.emit(dev.sleeptime.R.string.success) } }
 fun importHolidays(uri: Uri) = action { holidays.install(readBounded(uri,2_000_000)); refresh.update { it + 1 }; controller.reconcile(); refreshWidgets(context); message.emit(dev.sleeptime.R.string.success) }
 fun reset() = action { backup.reset(); refresh.update { it + 1 } }
 private fun readBounded(uri: Uri, limit: Int): String = context.contentResolver.openInputStream(uri)!!.use { stream -> val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); var total=0; while(true) { val n=stream.read(chunk); if(n<0) break; total+=n; require(total<=limit); out.write(chunk,0,n) }; out.toString("UTF-8") }
 private fun action(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { dataMutex.withLock { block() } } catch(c: CancellationException) { throw c } catch(_: Exception) { message.emit(dev.sleeptime.R.string.error) } } }
}

private fun TimeBudget.at(now: Instant): TimeBudget =
 copy(remainingFree=TimeEngine.remainingFree(freeSpans,now))
