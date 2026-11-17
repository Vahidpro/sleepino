package dev.sleeptime.core

import java.time.*
import java.time.temporal.TemporalAdjusters

object TimeEngine {
 // Gaps advance by the gap length; overlaps use the earlier offset, matching java.time.
 fun localInstant(date: LocalDate, minutes: Int, zone: ZoneId): Instant = date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant()
 fun sleep(config: Configuration, owner: LocalDate): SleepOccurrence? {
  val rule = config.sleep.firstOrNull { it.day == owner.dayOfWeek.value }
  val override = config.overrides.firstOrNull { it.date == owner.toString() }
  if (override?.sleepEnabled == false || (override?.sleepEnabled != true && rule?.enabled != true)) return null
  val bed = override?.bedtime ?: rule?.bedtime ?: return null
  val zone = config.prefs.zone()
  val date = owner.plusDays(if (bed < 720) 1 else 0)
  val start = localInstant(date, bed, zone)
  val wake = override?.wake ?: rule?.wake ?: 420
  val end = if (rule?.useWakeTime != false) {
   var d = date
   if (wake <= bed) d = d.plusDays(1)
   localInstant(d, wake, zone)
  } else start.plusSeconds((rule.durationMinutes * 60).toLong())
  return SleepOccurrence(owner, start, maxOf(start,end))
 }
 fun occurrences(config: Configuration, now: Instant, days: Int = 10): List<SleepOccurrence> {
  val today = now.atZone(config.prefs.zone()).toLocalDate()
  val dates=(-2L..days.toLong()).map {today.plusDays(it)}.toMutableSet()
  val overrides=config.overrides.associateBy {it.date}
  for(rule in config.sleep.filter {it.enabled}) {
   var d=today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(rule.day)))
   var found=0
   while(found<3) {
    if(overrides[d.toString()]?.sleepEnabled!=false) {dates+=d;found++}
    d=d.plusWeeks(1)
   }
  }
  config.overrides.filter {it.sleepEnabled==true && LocalDate.parse(it.date)>=today.minusDays(2)}.forEach {dates+=LocalDate.parse(it.date)}
  return dates.mapNotNull {sleep(config,it)}.sortedBy {it.start}
 }
 fun activeSleep(config: Configuration, now: Instant, completed: Set<String> = emptySet()): SleepOccurrence? {
  val all = occurrences(config, now)
  return all.lastOrNull { it.start <= now && now < it.end && it.key !in completed }
   ?: all.firstOrNull { it.start > now && it.key !in completed }
 }
 fun routineOwner(config: Configuration, now: Instant): LocalDate {
  val all = occurrences(config, now)
  val last = all.lastOrNull { it.start <= now && now < it.end }
  return (last ?: all.firstOrNull { it.start > now })?.owner ?: now.atZone(config.prefs.zone()).toLocalDate()
 }
 fun weekStart(date: LocalDate, firstDay: Int): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(firstDay)))
 fun span(owner: LocalDate, slot: TimeSlot, zone: ZoneId, kind: Kind): Span {
  val endDate = if (slot.end <= slot.start) owner.plusDays(1) else owner
  return Span(localInstant(owner, slot.start, zone), localInstant(endDate, slot.end, zone), kind)
 }
 fun clip(span: Span, start: Instant, end: Instant): Span? {
  val a = maxOf(span.start, start); val b = minOf(span.end, end)
  return if (b > a) Span(a, b, span.kind) else null
 }
 fun merge(spans: List<Span>): List<Span> {
  val result = mutableListOf<Span>()
  for (span in spans.filter { it.end > it.start }.sortedBy { it.start }) {
   val last = result.lastOrNull()
   if (last != null && span.start <= last.end) result[result.lastIndex] = last.copy(end = maxOf(last.end, span.end)) else result += span
  }
  return result
 }
 fun subtract(spans: List<Span>, exclusions: List<Span>): List<Span> {
  var result = spans
  for (cut in merge(exclusions)) result = result.flatMap { s ->
   if (cut.end <= s.start || cut.start >= s.end) listOf(s) else buildList {
    if (cut.start > s.start) add(s.copy(end = cut.start))
    if (cut.end < s.end) add(s.copy(start = cut.end))
   }
  }
  return result
 }
 fun rawActivity(config: Configuration, date: LocalDate, kind: Kind): List<Span> {
  val override = config.overrides.firstOrNull { it.date == date.toString() }
  val rule = (if (kind == Kind.WORK) config.work else config.exercise).firstOrNull { it.day == date.dayOfWeek.value }
  val explicit = if (kind == Kind.WORK) override?.work else override?.exercise
  val slots = explicit ?: rule?.slots.orEmpty()
  val occupied = slots.map { span(date, it, config.prefs.zone(), kind) }
  return if (explicit != null) occupied else subtract(occupied, rule?.breaks.orEmpty().map { span(date, it, config.prefs.zone(), kind) })
 }
 fun resolvedDay(config: Configuration, date: LocalDate, holidays: HolidayProvider): List<Span> {
  val zone = config.prefs.zone(); val a = date.atStartOfDay(zone).toInstant(); val b = date.plusDays(1).atStartOfDay(zone).toInstant()
  val override = config.overrides.firstOrNull { it.date == date.toString() }
  return buildList {
   (-2L..0L).mapNotNull { sleep(config, date.plusDays(it)) }.forEach { clip(Span(it.start, it.end, Kind.SLEEP), a, b)?.let(::add) }
   for (kind in listOf(Kind.WORK, Kind.EXERCISE)) {
    val explicit = if (kind == Kind.WORK) override?.work else override?.exercise
    val cancelled = kind == Kind.WORK && explicit == null && holidays.status(date, config.prefs.region) == HolidayState.HOLIDAY
    if (!cancelled) {
     // An explicit override replaces the calendar day's work, including a recurring carry-in.
     val candidates = if (explicit != null) explicit.map { span(date, it, zone, kind) } else rawActivity(config, date.minusDays(1), kind) + rawActivity(config, date, kind)
     candidates.mapNotNull { clip(it, a, b) }.forEach(::add)
    }
   }
  }
 }
 fun budget(config: Configuration, firstDate: LocalDate, days: Int, now: Instant, holidays: HolidayProvider): TimeBudget {
  require(days in 1..366)
  val zone = config.prefs.zone(); val start = firstDate.atStartOfDay(zone).toInstant(); val end = firstDate.plusDays(days.toLong()).atStartOfDay(zone).toInstant()
  val dates = (0 until days).map { firstDate.plusDays(it.toLong()) }
  val spans = dates.flatMap { resolvedDay(config, it, holidays) }
  // Mutually exclusive chart: sleep first, then work, then exercise. Free time uses the union.
  val assigned = mutableListOf<Span>(); val breakdown = mutableMapOf<Kind, Duration>()
  for (kind in listOf(Kind.SLEEP, Kind.WORK, Kind.EXERCISE)) {
   val own = subtract(merge(spans.filter { it.kind == kind }), assigned)
   breakdown[kind] = own.fold(Duration.ZERO) { d, s -> d + Duration.between(s.start, s.end) }; assigned += own
  }
  val free = subtract(listOf(Span(start, end, Kind.WORK)), assigned)
  val total = free.fold(Duration.ZERO) { d, s -> d + Duration.between(s.start, s.end) }
  val remaining = free.mapNotNull { clip(it, maxOf(start, minOf(end, now)), end) }.fold(Duration.ZERO) { d, s -> d + Duration.between(s.start, s.end) }
  return TimeBudget(start, end, total, remaining, breakdown, free,
   dates.count { d -> resolvedDay(config, d, holidays).any { it.kind == Kind.WORK } },
   dates.count { d -> holidays.status(d, config.prefs.region) == HolidayState.HOLIDAY && config.overrides.firstOrNull { it.date == d.toString() }?.work == null && (rawActivity(config, d, Kind.WORK) + rawActivity(config, d.minusDays(1), Kind.WORK)).any { clip(it, d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant()) != null } },
   dates.filter { holidays.status(it, config.prefs.region) == HolidayState.UNKNOWN }.toSet())
 }
 fun activeOrNextWork(config: Configuration, now: Instant, holidays: HolidayProvider): Span? {
  val today = now.atZone(config.prefs.zone()).toLocalDate()
  return merge((0L..14L).flatMap { resolvedDay(config, today.plusDays(it), holidays) }.filter { it.kind == Kind.WORK }).firstOrNull { it.end > now }
 }
}
