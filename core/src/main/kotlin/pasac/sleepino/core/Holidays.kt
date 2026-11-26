package pasac.sleepino.core

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.Serializable

@Serializable data class HolidayRule(val type: String, val holiday: Boolean, val calendar: String, val rule: String, val month: Int, val day: Int? = null, val beginningPersianYear: Int? = null, val endPersianYear: Int? = null)
@Serializable data class LunarMonth(val year: Int, val month: Int, val start: String, val end: String, val complete: Boolean = true)
@Serializable data class HolidayBundle(val schema: Int = 1, val revision: String, val generatedAt: String, val sourcePersianYear: Int, val rules: List<HolidayRule>, val lunar: List<LunarMonth>) {
 fun validate(): HolidayBundle {
  require(schema == 1 && revision.matches(Regex("[0-9a-f]{40}")) && sourcePersianYear in 1300..1600)
  require(rules.size in 20..100)
  rules.forEach { r ->
   require(r.type == "Iran" && r.holiday)
   require(r.calendar in setOf("Persian", "Gregorian", "Hijri"))
   require(r.rule in setOf("simple", "end of month"))
   require(r.month in 1..12 && (r.rule != "simple" || r.day in 1..31))
  }
  val ordered = lunar.sortedBy { it.start }
  ordered.forEach { m ->
   require(m.month in 1..12)
   val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(m.start), LocalDate.parse(m.end))
   require(if(m.complete) days in 29..30 else days in 1..30)
  }
  require(ordered.map { it.start }.distinct().size == ordered.size)
  ordered.zipWithNext().forEach { (a,b) -> require(a.end <= b.start) }
  return this
 }
}
class OfflineHolidayProvider(bundle: HolidayBundle): HolidayProvider {
 private val data = bundle.validate()
 private val lunar = data.lunar.sortedBy { it.start }
 override fun status(date: LocalDate, region: HolidayRegion): HolidayState {
  if (region == HolidayRegion.NONE) return HolidayState.WORKDAY
  if (region == HolidayRegion.US) return if (usHolidays(date.year).contains(date)) HolidayState.HOLIDAY else HolidayState.WORKDAY
  val persian = runCatching { PersianDates.fromGregorian(date) }.getOrNull() ?: return HolidayState.UNKNOWN
  // Recurrence is only authoritative for the source calendar's published year.
  val coveredYear = persian.year == data.sourcePersianYear
  val month = lunar.firstOrNull { date >= LocalDate.parse(it.start) && date < LocalDate.parse(it.end) }
  for (r in data.rules) {
   if (r.beginningPersianYear != null && persian.year < r.beginningPersianYear) continue
   if (r.endPersianYear != null && persian.year > r.endPersianYear) continue
   val coordinates = when (r.calendar) {
    "Persian" -> persian.month to persian.day
    "Gregorian" -> date.monthValue to date.dayOfMonth
    "Hijri" -> month?.let { it.month to (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it.start), date).toInt() + 1) }
    else -> null
   } ?: continue
   val isEnd = when (r.calendar) {
    "Persian" -> PersianDates.fromGregorian(date.plusDays(1)).month != persian.month
    "Gregorian" -> date.plusDays(1).month != date.month
    "Hijri" -> month != null && month.complete && date.plusDays(1).toString() == month.end
    else -> false
   }
   if (coordinates.first == r.month && ((r.rule == "simple" && coordinates.second == r.day) || (r.rule == "end of month" && isEnd))) {
    return if (coveredYear) HolidayState.HOLIDAY else HolidayState.UNKNOWN
   }
  }
  return if (coveredYear && month != null) HolidayState.WORKDAY else HolidayState.UNKNOWN
 }
 override fun getDataStatus(region: HolidayRegion, year: Int): HolidayDataStatus {
  if (region != HolidayRegion.IRAN) return HolidayDataStatus(LocalDate.of(year,1,1), LocalDate.of(year,12,31), if (region == HolidayRegion.US) "OPM-5USC6103" else "none", true)
  val from = maxOf(PersianDates.newYear(data.sourcePersianYear), lunar.minOfOrNull { LocalDate.parse(it.start) } ?: LocalDate.MAX)
  val to = minOf(PersianDates.newYear(data.sourcePersianYear + 1).minusDays(1), lunar.maxOfOrNull { LocalDate.parse(it.end).minusDays(1) } ?: LocalDate.MIN)
  return HolidayDataStatus(from, to, data.revision, to >= from)
 }
 private fun usHolidays(year: Int): Set<LocalDate> = buildSet {
  fun observed(d: LocalDate): LocalDate = when(d.dayOfWeek) { DayOfWeek.SATURDAY -> d.minusDays(1); DayOfWeek.SUNDAY -> d.plusDays(1); else -> d }
  for (y in year-1..year+1) {
   listOf(1 to 1, 6 to 19, 7 to 4, 11 to 11, 12 to 25).filter { (m,_) -> m != 6 || y >= 2021 }.forEach { (m,d) -> add(observed(LocalDate.of(y,m,d))) }
  }
  fun nth(month: Int, day: DayOfWeek, n: Int) = LocalDate.of(year,month,1).with(TemporalAdjusters.dayOfWeekInMonth(n,day))
  add(nth(1,DayOfWeek.MONDAY,3)); add(nth(2,DayOfWeek.MONDAY,3))
  add(LocalDate.of(year,5,31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
  add(nth(9,DayOfWeek.MONDAY,1)); add(nth(10,DayOfWeek.MONDAY,2)); add(nth(11,DayOfWeek.THURSDAY,4))
 }.filter { it.year == year }.toSet()
}
