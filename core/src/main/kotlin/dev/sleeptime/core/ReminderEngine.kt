package dev.sleeptime.core

import java.time.Instant

data class ReminderEvent(val key: String, val occurrenceKey: String, val at: Instant, val text: String, val overdue: Boolean, val ordinal: Int)
object ReminderEngine {
 fun events(config: Configuration, occurrence: SleepOccurrence, session: ActiveSession?): List<ReminderEvent> {
  if (session?.completed == true) return emptyList()
  val guaranteed = if(config.prefs.minimumScheduledReminder && config.reminders.none {it.enabled && it.offsetMinutes==0}) listOf(ReminderEvent("guaranteed",occurrence.key,occurrence.start,"",false,0)) else emptyList()
  val events = guaranteed + config.reminders.filter { it.enabled }.map { s -> ReminderEvent("stage:${s.id}", occurrence.key, occurrence.start.plusSeconds(s.offsetMinutes * 60L), s.text, s.offsetMinutes > 0, s.id) } +
   (1..config.prefs.maxRepeats).map { n -> ReminderEvent("repeat:$n", occurrence.key, occurrence.start.plusSeconds(n * config.prefs.repeatMinutes * 60L), "", true, 1000 + n) }
  val snooze = session?.snoozedUntil?.let { Instant.ofEpochMilli(it) }
  val candidates = if (snooze == null) events else events.filter { it.at > snooze } + ReminderEvent("snooze:${session.snoozes}", occurrence.key, snooze, "", snooze > occurrence.start, 2000 + session.snoozes)
  return candidates.filter { it.key !in session?.emitted.orEmpty() && it.at < occurrence.end }.sortedBy { it.at }
 }
 fun snooze(config: Configuration, current: ActiveSession, now: Instant, minutes: Int): ActiveSession? {
  if (current.completed || current.snoozes >= config.prefs.maxSnoozes || minutes !in 1..180) return null
  return current.copy(snoozes = current.snoozes + 1, snoozedUntil = now.plusSeconds(minutes * 60L).toEpochMilli())
 }
 fun shouldEmit(event: ReminderEvent, smart: Boolean, lastActivity: Instant?, now: Instant, inactivityMinutes: Int): Boolean {
  if (!event.overdue || !smart || lastActivity == null) return true
  return java.time.Duration.between(lastActivity, now).seconds <= inactivityMinutes * 60L
 }
}
