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

data class DashboardState(val config: Configuration, val now: Instant, val occurrence: SleepOccurrence?, val confirmed: Boolean, val nextWork: Span?, val today: TimeBudget, val week: TimeBudget, val days: List<Pair<LocalDate,TimeBudget>>, val history: List<BedtimeEntity>, val routine: List<RoutineEntity>, val routineOwner: LocalDate, val activeSession: ActiveSession?)
@HiltViewModel class AppViewModel @Inject constructor(
 val store: ConfigurationStore, private val historyDao: HistoryDao, val holidays: HolidayRepository,
 val controller: ReminderController, private val backup: BackupManager, val clock: Clock,
 @param:ApplicationContext private val context: Context
): ViewModel() {
 private val dataMutex=kotlinx.coroutines.sync.Mutex()
 private val refresh = MutableStateFlow(0)
 private val ticks = flow { while(currentCoroutineContext().isActive) { emit(clock.instant()); delay(1000L-clock.millis().mod(1000L)) } }
 val message = MutableSharedFlow<Int>(extraBufferCapacity=8)
 val pendingBackup = MutableStateFlow<Backup?>(null)
 private var cachedKey: Pair<Configuration,Long>? = null
 private var cachedBudgets: Triple<TimeBudget,TimeBudget,List<Pair<LocalDate,TimeBudget>>>? = null
 val state: StateFlow<DashboardState?> = combine(store.configuration,historyDao.history(),historyDao.routines(),ticks,refresh) { config, history, routines, now, _ ->
  val date = now.atZone(config.prefs.zone()).toLocalDate()
  val key = config to (now.epochSecond / 60)
  if (cachedKey != key || cachedBudgets == null) {
   val first = TimeEngine.weekStart(date,config.prefs.weekStart)
   cachedBudgets = Triple(TimeEngine.budget(config,date,1,now,holidays),TimeEngine.budget(config,first,7,now,holidays),(0L..6L).map { first.plusDays(it).let { d -> d to TimeEngine.budget(config,d,1,now,holidays) } }); cachedKey = key
  }
  val budgets = cachedBudgets!!
  val occurrence = TimeEngine.activeSleep(config,now)
  DashboardState(config,now,occurrence,history.any { it.key == occurrence?.key },TimeEngine.activeOrNextWork(config,now,holidays),budgets.first,budgets.second,budgets.third,history,routines,TimeEngine.routineOwner(config,now),store.session())
 }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
 init { viewModelScope.launch(Dispatchers.IO) { store.configuration.collect { controller.reconcile() } } }
 fun update(transform: (Configuration) -> Configuration) = action { store.update(transform); controller.reconcile(); refreshWidgets(context); refresh.value++ }
 fun confirm(onRecorded: (String) -> Unit = {}) = action { controller.confirm()?.let { withContext(Dispatchers.Main) { onRecorded(it) } }; refresh.value++ }
 fun undo(key: String) = action { controller.undo(key); refresh.value++ }
 fun snooze(minutes: Int) = action { if(!controller.snooze(minutes)) message.emit(dev.sleeptime.R.string.snooze_limit); refresh.value++ }
 fun routineToggle(item: RoutineItem, date: LocalDate, checked: Boolean) = action {
  if (checked) historyDao.complete(RoutineEntity(date.toString(),item.id,clock.millis())) else historyDao.uncomplete(date.toString(),item.id)
 }
 fun resume() = action { controller.reconcile(); refresh.value++ }
 fun export(uri: Uri) = action { context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { it.write(backup.export()) }; message.emit(dev.sleeptime.R.string.success) }
 fun readBackup(uri: Uri) = action { pendingBackup.value = backup.validate(readBounded(uri,10_000_000)) }
 fun restore() = action { pendingBackup.value?.let { backup.restore(it); pendingBackup.value=null; cachedKey=null; controller.reconcile(); refresh.value++; message.emit(dev.sleeptime.R.string.success) } }
 fun importHolidays(uri: Uri) = action { holidays.install(readBounded(uri,2_000_000)); cachedKey=null; refresh.value++; controller.reconcile(); message.emit(dev.sleeptime.R.string.success) }
 fun reset() = action { backup.reset(); cachedKey=null; controller.reconcile(); refresh.value++ }
 private fun readBounded(uri: Uri, limit: Int): String = context.contentResolver.openInputStream(uri)!!.use { stream -> val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); var total=0; while(true) { val n=stream.read(chunk); if(n<0) break; total+=n; require(total<=limit); out.write(chunk,0,n) }; out.toString("UTF-8") }
 private fun action(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { dataMutex.withLock { block() } } catch(c: CancellationException) { throw c } catch(_: Exception) { message.emit(dev.sleeptime.R.string.error) } } }
}
