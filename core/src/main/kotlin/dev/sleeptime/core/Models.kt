package dev.sleeptime.core

import kotlinx.serialization.Serializable
import java.time.*

@Serializable enum class Kind { SLEEP, WORK, EXERCISE }
@Serializable enum class HolidayRegion { IRAN, US, NONE }
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
@Serializable data class TimeSlot(val start: Int, val end: Int) {
 init { require(start in 0..1439 && end in 0..1439 && start != end) }
}
@Serializable data class SleepRule(val day: Int, val enabled: Boolean = true, val bedtime: Int = 1380, val wake: Int = 420, val durationMinutes: Int = 480, val useWakeTime: Boolean = true) {
 init { require(day in 1..7 && bedtime in 0..1439 && wake in 0..1439 && durationMinutes in 30..1200) }
}
@Serializable data class DayRule(val day: Int, val slots: List<TimeSlot> = emptyList(), val breaks: List<TimeSlot> = emptyList()) {
 init { require(day in 1..7 && slots.size <= 12 && breaks.size <= 12) }
}
@Serializable data class DateOverride(val date: String, val work: List<TimeSlot>? = null, val sleepEnabled: Boolean? = null, val bedtime: Int? = null, val wake: Int? = null, val exercise: List<TimeSlot>? = null) {
 init { LocalDate.parse(date); require(bedtime == null || bedtime in 0..1439); require(wake == null || wake in 0..1439) }
}
@Serializable data class ReminderStage(val id: Int, val offsetMinutes: Int, val text: String = "", val enabled: Boolean = true) {
 init { require(id in 1..1000 && offsetMinutes in -720..720 && text.length <= 500) }
}
@Serializable data class RoutineItem(val id: String, val text: String, val defaultKey: Int? = null) { init { require(id.length in 1..200 && text.length <= 160 && (defaultKey == null || defaultKey in 1..4)) } }
@Serializable data class Preferences(
 val onboarded: Boolean = false,
 val theme: ThemeMode = ThemeMode.SYSTEM,
 val dynamicColor: Boolean = false,
 val accent: String = "violet",
 val language: String = "system",
 val persianNumerals: Boolean = false,
 val jalali: Boolean = false,
 val clock24: Boolean = true,
 val reduceMotion: Boolean = false,
 val weekStart: Int = 6,
 val region: HolidayRegion = HolidayRegion.IRAN,
 val zoneId: String = "system",
 val countWorkRemaining: Boolean = false,
 val notifications: Boolean = false,
 val preciseAlarms: Boolean = false,
 val smartReminders: Boolean = false,
 val minimumScheduledReminder: Boolean = true,
 val inactivityMinutes: Int = 20,
 val repeatMinutes: Int = 15,
 val maxRepeats: Int = 4,
 val snoozeMinutes: Int = 10,
 val maxSnoozes: Int = 3,
 val soundUri: String = "default",
 val vibration: Boolean = true,
 val silent: Boolean = false,
 val dnd: Boolean = false,
 val dndStartOffset: Int = 0,
 val dndEndMinutes: Int = 420,
 val dndFollowsSnooze: Boolean = false,
 val routineLeadMinutes: Int = 60,
 val distractionPackages: Set<String> = emptySet(),
 val baseSleep: Boolean = true,
 val baseBedtime: Int = 1380,
 val baseWake: Int = 420
) {
 init {
  require(language in setOf("system", "en", "fa")); require(weekStart in 1..7)
  require(inactivityMinutes in 1..180 && repeatMinutes in 5..180 && maxRepeats in 0..20)
  require(snoozeMinutes in 1..180 && maxSnoozes in 0..20)
  require(dndStartOffset in -720..720 && dndEndMinutes in 0..1439 && routineLeadMinutes in 0..720)
  require(baseBedtime in 0..1439 && baseWake in 0..1439)
  if (zoneId != "system") ZoneId.of(zoneId)
 }
 fun zone(): ZoneId = if (zoneId == "system") ZoneId.systemDefault() else ZoneId.of(zoneId)
}
@Serializable data class Configuration(
 val prefs: Preferences = Preferences(),
 val sleep: List<SleepRule> = emptyList(),
 val work: List<DayRule> = emptyList(),
 val exercise: List<DayRule> = emptyList(),
 val overrides: List<DateOverride> = emptyList(),
 val reminders: List<ReminderStage> = listOf(ReminderStage(1, -30), ReminderStage(2, -10), ReminderStage(3, 0)),
 val routine: List<RoutineItem> = listOf(RoutineItem("phone", "", 1), RoutineItem("teeth", "", 2), RoutineItem("clothes", "", 3), RoutineItem("rest", "", 4))
) {
 init {
  require(sleep.map { it.day }.distinct().size == sleep.size && sleep.size <= 7)
  require(work.map { it.day }.distinct().size == work.size && work.size <= 7)
  require(exercise.map { it.day }.distinct().size == exercise.size && exercise.size <= 7)
  require(reminders.map { it.id }.distinct().size == reminders.size && reminders.size <= 20)
  require(routine.size <= 100 && routine.map { it.id }.distinct().size == routine.size)
  require(overrides.map { it.date }.distinct().size == overrides.size && overrides.size <= 5000)
 }
}
@Serializable data class SleepRecord(val key: String, val ownerDate: String, val scheduled: Long, val confirmed: Long, val snoozes: Int = 0)
@Serializable data class RoutineRecord(val key: String, val itemId: String, val ownerDate: String, val completed: Long)
@Serializable data class ActiveSession(val key: String, val scheduled: Long, val snoozedUntil: Long? = null, val snoozes: Int = 0, val emitted: Set<String> = emptySet(), val completed: Boolean = false)
@Serializable data class Backup(val schemaVersion: Int = 1, val createdAt: Long, val configuration: Configuration, val history: List<SleepRecord> = emptyList(), val routines: List<RoutineRecord> = emptyList())

data class Span(val start: Instant, val end: Instant, val kind: Kind) { init { require(!end.isBefore(start)) } }
data class SleepOccurrence(val owner: LocalDate, val start: Instant, val end: Instant) { val key: String get() = owner.toString() }
enum class HolidayState { HOLIDAY, WORKDAY, UNKNOWN }
data class HolidayDataStatus(val validFrom: LocalDate?, val validTo: LocalDate?, val revision: String, val reliable: Boolean)
interface HolidayProvider {
 fun status(date: LocalDate, region: HolidayRegion): HolidayState
 fun getDataStatus(region: HolidayRegion, year: Int): HolidayDataStatus
 fun getHolidays(start: LocalDate, endExclusive: LocalDate, region: HolidayRegion): Set<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.filter { status(it, region) == HolidayState.HOLIDAY }.toSet()
}
data class TimeBudget(val start: Instant, val end: Instant, val free: Duration, val remainingFree: Duration, val breakdown: Map<Kind, Duration>, val freeSpans: List<Span>, val workdays: Int, val holidaysAffectingWork: Int, val uncertainDates: Set<LocalDate>)
