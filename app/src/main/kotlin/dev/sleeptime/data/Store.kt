package dev.sleeptime.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sleeptime.core.*
import dev.sleeptime.core.Preferences
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

val Context.settingsStore by preferencesDataStore("settings")
val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
@Singleton class ConfigurationStore @Inject constructor(@param:ApplicationContext private val context: Context) {
 private val key = stringPreferencesKey("configuration_v1")
 private val sessionKey = stringPreferencesKey("session_v1")
 val configuration = context.settingsStore.data.map { it[key] }.distinctUntilChanged()
  .map { it?.let { json -> AppJson.decodeFromString<Configuration>(json) } ?: getDefault() }
 val activeSession = context.settingsStore.data.map { it[sessionKey] }.distinctUntilChanged()
  .map { it?.let { json -> AppJson.decodeFromString<ActiveSession>(json) } }
 suspend fun get(): Configuration = configuration.first()
 suspend fun update(transform: (Configuration) -> Configuration) {
  context.settingsStore.edit { p -> val old = p[key]?.let { AppJson.decodeFromString<Configuration>(it) } ?: getDefault(); p[key] = AppJson.encodeToString(transform(old)) }
 }
 private fun getDefault(): Configuration {
  val fa = context.resources.configuration.locales[0].language == "fa"
  return Configuration(prefs = Preferences(weekStart = if (fa) 6 else 7, region = if (fa) HolidayRegion.IRAN else HolidayRegion.NONE, jalali = fa))
 }
 suspend fun replace(value: Configuration) { context.settingsStore.edit { it[key] = AppJson.encodeToString(value); it.remove(sessionKey) } }
 suspend fun session(): ActiveSession? = activeSession.first()
 suspend fun setSession(value: ActiveSession?) { context.settingsStore.edit { if (value == null) it.remove(sessionKey) else it[sessionKey] = AppJson.encodeToString(value) } }
}
@Entity(tableName = "bedtimes") data class BedtimeEntity(@PrimaryKey val key: String, val ownerDate: String, val scheduled: Long, val confirmed: Long, val snoozes: Int) {
 fun record() = SleepRecord(key,ownerDate,scheduled,confirmed,snoozes)
 companion object { fun from(r: SleepRecord) = BedtimeEntity(r.key,r.ownerDate,r.scheduled,r.confirmed,r.snoozes) }
}
@Entity(tableName = "routine_completions", primaryKeys = ["ownerDate", "itemId"]) data class RoutineEntity(val ownerDate: String, val itemId: String, val completed: Long)
@Dao interface HistoryDao {
 @Query("SELECT * FROM bedtimes ORDER BY confirmed DESC") fun history(): Flow<List<BedtimeEntity>>
 @Query("SELECT * FROM bedtimes") suspend fun all(): List<BedtimeEntity>
 @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun confirm(entity: BedtimeEntity): Long
 @Query("DELETE FROM bedtimes WHERE `key` = :key") suspend fun undo(key: String)
 @Query("SELECT * FROM routine_completions ORDER BY completed DESC") fun routines(): Flow<List<RoutineEntity>>
 @Query("SELECT * FROM routine_completions") suspend fun allRoutines(): List<RoutineEntity>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun complete(entity: RoutineEntity)
 @Query("DELETE FROM routine_completions WHERE ownerDate = :date AND itemId = :id") suspend fun uncomplete(date: String, id: String)
 @Query("DELETE FROM bedtimes") suspend fun clearBedtimes()
 @Query("DELETE FROM routine_completions") suspend fun clearRoutines()
}
@Database(entities=[BedtimeEntity::class,RoutineEntity::class], version=1, exportSchema=true)
abstract class SleepDatabase: RoomDatabase() { abstract fun history(): HistoryDao }
@Singleton class HolidayRepository @Inject constructor(@param:ApplicationContext private val context: Context): HolidayProvider {
 @Volatile private var provider: OfflineHolidayProvider? = null
 @Synchronized fun reload() { provider = null }
 private fun get(): OfflineHolidayProvider = provider ?: synchronized(this) {
  provider ?: run {
   val cached = java.io.File(context.filesDir, "iran_holidays.json")
   val data = runCatching { if (cached.exists()) AppJson.decodeFromString<HolidayBundle>(cached.readText()).validate() else null }.getOrNull()
     ?: AppJson.decodeFromString<HolidayBundle>(context.assets.open("iran_holidays.json").bufferedReader().use { it.readText() }).validate()
   OfflineHolidayProvider(data).also { provider = it }
  }
 }
 override fun status(date: java.time.LocalDate, region: HolidayRegion) = get().status(date,region)
 override fun getDataStatus(region: HolidayRegion, year: Int) = get().getDataStatus(region,year)
 /** A release-validated bundle is imported explicitly, atomically, and never auto-trusted from a network response. */
 suspend fun install(json: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
  require(json.toByteArray().size <= 2_000_000)
  val data = AppJson.decodeFromString<HolidayBundle>(json).validate()
  val old = get().getDataStatus(HolidayRegion.IRAN, java.time.LocalDate.now().year)
  require(data.sourcePersianYear >= (old.validTo?.let { PersianDates.fromGregorian(it).year } ?: 0))
  val tmp = java.io.File(context.filesDir, "iran_holidays.tmp"); tmp.writeText(AppJson.encodeToString(data))
  check(tmp.renameTo(java.io.File(context.filesDir, "iran_holidays.json"))); reload()
 }
}
@Singleton class BackupManager @Inject constructor(private val store: ConfigurationStore, private val db: SleepDatabase, private val clock: Clock, @param:ApplicationContext private val context: Context) {
 private val journal get() = java.io.File(context.filesDir,"restore-journal.json")
 suspend fun recoverPending() { if(journal.exists()) applyRestore(validate(journal.readText())) }
 suspend fun export(includeHistory: Boolean = true): String = AppJson.encodeToString(Backup(createdAt=clock.millis(),configuration=store.get(),history=if(includeHistory) db.history().all().map { it.record() } else emptyList(),routines=if(includeHistory) db.history().allRoutines().map { RoutineRecord("${it.ownerDate}:${it.itemId}",it.itemId,it.ownerDate,it.completed) } else emptyList()))
 fun validate(text: String): Backup {
  require(text.toByteArray().size <= 10_000_000)
  val b = AppJson.decodeFromString<Backup>(text)
  require(b.schemaVersion == 1 && b.history.size <= 100_000 && b.routines.size <= 100_000)
  require(b.history.map {it.key}.distinct().size==b.history.size)
  require(b.routines.map {it.ownerDate to it.itemId}.distinct().size==b.routines.size)
  b.history.forEach { java.time.LocalDate.parse(it.ownerDate); require(it.key == it.ownerDate && it.confirmed > 0 && it.scheduled > 0) }
  b.routines.forEach { java.time.LocalDate.parse(it.ownerDate); require(it.itemId.length <= 200 && it.completed > 0) }
  return b
 }
 suspend fun restore(backup: Backup) {
  val tmp=java.io.File(context.filesDir,"restore-journal.tmp");tmp.writeText(AppJson.encodeToString(backup));check(tmp.renameTo(journal))
  applyRestore(backup)
 }
 private suspend fun applyRestore(backup: Backup) {
  // A process interruption replays the validated journal idempotently on next launch.
  db.withTransaction {
   db.history().clearBedtimes(); db.history().clearRoutines()
   backup.history.forEach { db.history().confirm(BedtimeEntity.from(it)) }
   backup.routines.forEach { db.history().complete(RoutineEntity(it.ownerDate,it.itemId,it.completed)) }
  }
  store.replace(backup.configuration)
  check(journal.delete() || !journal.exists())
 }
 suspend fun reset() { journal.delete(); db.withTransaction { db.history().clearBedtimes(); db.history().clearRoutines() }; store.replace(Configuration()) }
}
