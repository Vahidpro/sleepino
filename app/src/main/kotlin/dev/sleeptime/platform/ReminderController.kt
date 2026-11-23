package dev.sleeptime.platform

import android.app.*
import android.content.*
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sleeptime.core.*
import dev.sleeptime.data.*
import dev.sleeptime.widgets.refreshWidgets
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton class ReminderController @Inject constructor(
 @param:ApplicationContext private val context: Context,
 private val store: ConfigurationStore, private val history: HistoryDao, private val clock: Clock,
 private val notices: Notifications, private val activity: DeviceActivity, val dnd: DndController
) {
 private val mutex = Mutex()
 private val alarm get() = context.getSystemService(AlarmManager::class.java)
 private fun pending(action: String, owner: String = "", key: String = "", fingerprint: Int = 0) = PendingIntent.getBroadcast(context,0,Intent(context,AlarmReceiver::class.java).setAction(action).putExtra("owner",owner).putExtra("event",key).putExtra("fingerprint",fingerprint),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private data class Candidate(val at: Instant, val owner: String, val key: String)
 fun canBePrecise(): Boolean = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
 suspend fun reconcile() = mutex.withLock { scheduleLocked() }
 private suspend fun scheduleLocked() {
  alarm.cancel(pending(ACTION_ALARM))
  val config = store.get(); val now = clock.instant()
  if (!config.prefs.notifications && !config.prefs.dnd) { dnd.setActive(false); return }
  val reported = history.all().associateBy { it.key }
  val stored = store.session()
  val candidates = mutableListOf<Candidate>()
  var dndActiveNow = false
  if(!config.prefs.dnd) dnd.setActive(false)
  for (occurrence in TimeEngine.occurrences(config,now)) {
   if (occurrence.key !in reported && config.prefs.notifications) {
    val session = stored?.takeIf { it.key == occurrence.key && it.scheduled == occurrence.start.toEpochMilli() }
    ReminderEngine.events(config,occurrence,session).filter { it.at > now }.forEach { candidates += Candidate(it.at,occurrence.key,it.key) }
   }
   if (config.prefs.dnd) {
    val reportedStart=reported[occurrence.key]?.let {Instant.ofEpochMilli(it.confirmed)}
    val nominalStart=occurrence.start.plusSeconds(config.prefs.dndStartOffset * 60L)
    val start = minOf(nominalStart,reportedStart?:nominalStart).let { if(config.prefs.dndFollowsSnooze && stored?.key==occurrence.key && stored.snoozedUntil != null) maxOf(it,Instant.ofEpochMilli(stored.snoozedUntil!!)) else it }
    var end = TimeEngine.localInstant(start.atZone(config.prefs.zone()).toLocalDate(),config.prefs.dndEndMinutes,config.prefs.zone())
    if (end <= start) end = TimeEngine.localInstant(start.atZone(config.prefs.zone()).toLocalDate().plusDays(1),config.prefs.dndEndMinutes,config.prefs.zone())
    if (start > now) candidates += Candidate(start,occurrence.key,"dnd:start")
    if (end > now) candidates += Candidate(end,occurrence.key,"dnd:end")
    if (start <= now && now < end) dndActiveNow = true
   }
  }
  if(config.prefs.dnd) dnd.setActive(dndActiveNow)
  val next = candidates.minByOrNull { it.at } ?: return
  val pi = pending(ACTION_ALARM,next.owner,next.key,config.hashCode())
  try {
   if (config.prefs.preciseAlarms && canBePrecise()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at.toEpochMilli(),pi)
   else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at.toEpochMilli(),pi)
  } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at.toEpochMilli(),pi) }
 }
 suspend fun fire(owner: String, key: String, fingerprint: Int) = mutex.withLock {
  val config = store.get(); val now = clock.instant()
  if (config.hashCode() == fingerprint) {
   if (key.startsWith("dnd:")) { if(config.prefs.dnd) dnd.setActive(key=="dnd:start") }
   else if (config.prefs.notifications && history.all().none { it.key == owner }) {
    val occurrence = runCatching { TimeEngine.sleep(config,LocalDate.parse(owner)) }.getOrNull()
    if (occurrence != null && now < occurrence.end) {
     val session = store.session()?.takeIf { it.key == owner && it.scheduled == occurrence.start.toEpochMilli() } ?: ActiveSession(owner,occurrence.start.toEpochMilli())
     val event = ReminderEngine.events(config,occurrence,session).firstOrNull { it.key == key }
     if (event != null && event.at <= now.plusSeconds(5)) {
      val coincident=ReminderEngine.events(config,occurrence,session).filter {it.at==event.at}
      val snapshot = if(config.prefs.smartReminders || config.prefs.distractionPackages.isNotEmpty()) activity.snapshot(now) else DeviceActivity.Snapshot(null,null)
      if(ReminderEngine.shouldEmit(event,config.prefs.smartReminders,snapshot.lastInteraction,now,config.prefs.inactivityMinutes)) notices.show(config,event,snapshot.foregroundPackage in config.prefs.distractionPackages)
      store.setSession(session.copy(emitted=session.emitted + coincident.map {it.key}))
     }
    }
   }
  }
  scheduleLocked()
 }
 suspend fun confirm(owner: String? = null): String? = mutex.withLock {
  val config = store.get(); val now = clock.instant()
  val occurrence = if(owner != null) TimeEngine.sleep(config,LocalDate.parse(owner)) else TimeEngine.activeSleep(config,now)
  if (occurrence == null) return@withLock null
  // A stale notification cannot confirm a different night.
  if (owner != null && TimeEngine.activeSleep(config,now)?.key != owner) return@withLock null
  val session = store.session()?.takeIf { it.key == occurrence.key } ?: ActiveSession(occurrence.key,occurrence.start.toEpochMilli())
  val inserted = history.confirm(BedtimeEntity(occurrence.key,occurrence.owner.toString(),occurrence.start.toEpochMilli(),now.toEpochMilli(),session.snoozes))
  if(inserted == -1L) return@withLock null
  store.setSession(session.copy(completed=true)); notices.dismiss()
  if(config.prefs.dnd) dnd.setActive(true)
  scheduleLocked(); refreshWidgets(context); occurrence.key
 }
 suspend fun undo(key: String) = mutex.withLock { history.undo(key); store.session()?.takeIf { it.key == key }?.let { store.setSession(it.copy(completed=false)) }; scheduleLocked(); refreshWidgets(context) }
 suspend fun snooze(minutes: Int? = null, owner: String? = null): Boolean = mutex.withLock {
  val config = store.get(); val now = clock.instant()
  val occurrence = TimeEngine.activeSleep(config,now) ?: return@withLock false
  if (owner != null && owner != occurrence.key) return@withLock false
  if (history.all().any { it.key == occurrence.key }) return@withLock false
  val current = store.session()?.takeIf { it.key == occurrence.key && it.scheduled == occurrence.start.toEpochMilli() } ?: ActiveSession(occurrence.key,occurrence.start.toEpochMilli())
  val next = ReminderEngine.snooze(config,current,maxOf(now,occurrence.start),minutes ?: config.prefs.snoozeMinutes) ?: return@withLock false
  if(next.snoozedUntil!! >= occurrence.end.toEpochMilli()) return@withLock false
  store.setSession(next); notices.dismiss()
  if(config.prefs.dndFollowsSnooze) dnd.setActive(false)
  scheduleLocked(); refreshWidgets(context); true
 }
 suspend fun cancelSnooze(key: String) = mutex.withLock {
  val config = store.get()
  val now = clock.instant()
  val occurrence = TimeEngine.activeSleep(config,now) ?: return@withLock
  if (occurrence.key != key) return@withLock
  val current = store.session()?.takeIf { it.key == key && it.scheduled == occurrence.start.toEpochMilli() }
   ?: return@withLock
  val restored = ReminderEngine.cancelSnooze(current,now) ?: return@withLock
  store.setSession(restored)
  scheduleLocked()
  refreshWidgets(context)
 }
 companion object { const val ACTION_ALARM = "dev.sleeptime.ALARM" }
}
