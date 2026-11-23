package dev.sleeptime.core
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimeEngineTest {
 private val date=LocalDate.of(2026,11,16)
 private val noHolidays=object: HolidayProvider {
  override fun status(date: LocalDate,region: HolidayRegion)=HolidayState.WORKDAY
  override fun getDataStatus(region: HolidayRegion,year: Int)=HolidayDataStatus(null,null,"test",true)
 }
 private fun config(zone: String="UTC")=Configuration(prefs=Preferences(zoneId=zone,region=HolidayRegion.NONE),sleep=(1..7).map {SleepRule(it)},work=(1..5).map {DayRule(it,listOf(TimeSlot(540,1080)))},exercise=listOf(DayRule(1,listOf(TimeSlot(1080,1140)))))
 private fun budget(c: Configuration=config(),d: LocalDate=date,now: Instant=d.atStartOfDay(c.prefs.zone()).toInstant(),holidays: HolidayProvider=noHolidays)=TimeEngine.budget(c,d,1,now,holidays)
 private fun holidays(vararg dates: LocalDate)=object: HolidayProvider {
  override fun status(date: LocalDate,region: HolidayRegion)=if(date in dates)HolidayState.HOLIDAY else HolidayState.WORKDAY
  override fun getDataStatus(region: HolidayRegion,year: Int)=HolidayDataStatus(null,null,"test",true)
 }
 @Test fun standardDay() {assertEquals(6,budget().free.toHours())}
 @Test fun nonWorkingDay() {assertEquals(16,budget(d=date.plusDays(5)).free.toHours())}
 @Test fun holidayCancelsOnlyWork() {val b=budget(holidays=holidays(date));assertEquals(15,b.free.toHours());assertEquals(1,b.holidaysAffectingWork);assertEquals(8L,b.breakdown[Kind.SLEEP]?.toHours());assertEquals(1L,b.breakdown[Kind.EXERCISE]?.toHours())}
 @Test fun holidayOnDayOffAddsNothing() {val d=date.plusDays(5);val b=budget(d=d,holidays=holidays(d));assertEquals(16,b.free.toHours());assertEquals(0,b.holidaysAffectingWork)}
 @Test fun explicitWorkRestoresHoliday() {val c=config().copy(overrides=listOf(DateOverride(date.toString(),work=listOf(TimeSlot(540,1080)))));assertEquals(6,budget(c,holidays=holidays(date)).free.toHours())}
 @Test fun explicitLeaveWins() {val c=config().copy(overrides=listOf(DateOverride(date.toString(),work=emptyList())));assertEquals(15,budget(c).free.toHours())}
 @Test fun exerciseOverlapCountedOnce() {val c=config().copy(exercise=listOf(DayRule(1,listOf(TimeSlot(600,660)))));assertEquals(7,budget(c).free.toHours());assertEquals(0L,budget(c).breakdown[Kind.EXERCISE]?.toHours())}
 @Test fun nestedAndTouchingIntervalsMerge() {val c=Configuration(prefs=Preferences(zoneId="UTC",baseSleep=false),work=listOf(DayRule(1,listOf(TimeSlot(540,1080),TimeSlot(600,660),TimeSlot(1080,1140)))));assertEquals(14,budget(c).free.toHours())}
 @Test fun breakIsFreeUntilOtherActivityReservesIt() {val c=config().copy(work=listOf(DayRule(1,listOf(TimeSlot(540,1080)),listOf(TimeSlot(720,780)))),exercise=emptyList());assertEquals(8,budget(c).free.toHours())}
 @Test fun overnightWorkClippedAcrossDays() {val c=Configuration(prefs=Preferences(zoneId="UTC",baseSleep=false),work=listOf(DayRule(1,listOf(TimeSlot(1320,360)))));assertEquals(22,budget(c).free.toHours());assertEquals(18,budget(c,date.plusDays(1)).free.toHours())}
 @Test fun holidayCancelsCarryInPortion() {val c=Configuration(prefs=Preferences(zoneId="UTC",baseSleep=false),work=listOf(DayRule(1,listOf(TimeSlot(1320,360)))));assertEquals(24,budget(c,date.plusDays(1),holidays=holidays(date.plusDays(1))).free.toHours())}
 @Test fun afterMidnightBelongsToPreviousEvening() {val c=config().copy(sleep=listOf(SleepRule(4,bedtime=30,wake=450)));val o=TimeEngine.sleep(c,LocalDate.of(2026,11,19))!!;assertEquals(Instant.parse("2026-11-20T00:30:00Z"),o.start);assertEquals(7,Duration.between(o.start,o.end).toHours())}
 @Test fun sleepIsClippedOnBothCalendarDays() {val c=Configuration(prefs=Preferences(zoneId="UTC",baseSleep=false),sleep=listOf(SleepRule(1)));assertEquals(23,budget(c).free.toHours());assertEquals(17,budget(c,date.plusDays(1)).free.toHours())}
 @Test fun remainingFromCurrentInstant() {val b=budget(now=Instant.parse("2026-11-16T19:00:00Z"));assertEquals(4,b.remainingFree.toHours())}
 @Test fun remainingDuringWorkCountsOnlyFreeIntervals() {val b=budget(now=Instant.parse("2026-11-16T12:00:00Z"));assertEquals(4,b.remainingFree.toHours())}
 @Test fun weeklyAndDailyBudgetsAgree() {val c=config();val week=TimeEngine.budget(c,date,7,date.atStartOfDay().toInstant(ZoneOffset.UTC),noHolidays);val sum=(0L..6L).sumOf {budget(c,date.plusDays(it)).free.seconds};assertEquals(sum,week.free.seconds)}
 @Test fun multipleHolidaysNeverDoubleSubtract() {val c=config();val h=holidays(date,date.plusDays(1));val plain=TimeEngine.budget(c,date,7,Instant.EPOCH,noHolidays);val adjusted=TimeEngine.budget(c,date,7,Instant.EPOCH,h);assertEquals(18,adjusted.free.minus(plain.free).toHours())}
 @Test fun allWeekStarts() {for(d in 1..7){val start=TimeEngine.weekStart(date,d);assertEquals(d,start.dayOfWeek.value);assertFalse(start>date);assertTrue(Duration.between(start.atStartOfDay(),date.atStartOfDay()).toDays()<7)}}
 @Test fun futureWeekRemainingEqualsTotal() {val b=TimeEngine.budget(config(),date,7,Instant.EPOCH,noHolidays);assertEquals(b.free,b.remainingFree)}
 @Test fun pastWeekRemainingIsZero() {val b=TimeEngine.budget(config(),date,7,Instant.parse("2027-01-01T00:00:00Z"),noHolidays);assertEquals(Duration.ZERO,b.remainingFree)}
 @Test fun springDayContains23ActualHours() {val c=Configuration(prefs=Preferences(zoneId="Europe/Berlin",baseSleep=false));assertEquals(23,budget(c,LocalDate.of(2026,3,29)).free.toHours())}
 @Test fun autumnDayContains25ActualHours() {val c=Configuration(prefs=Preferences(zoneId="Europe/Berlin",baseSleep=false));assertEquals(25,budget(c,LocalDate.of(2026,10,25)).free.toHours())}
 @Test fun nonexistentLocalTimeShiftsForward() {assertEquals(Instant.parse("2026-03-29T01:30:00Z"),TimeEngine.localInstant(LocalDate.of(2026,3,29),150,ZoneId.of("Europe/Berlin")))}
 @Test fun ambiguousLocalTimeUsesEarlierOffset() {assertEquals(Instant.parse("2026-10-25T00:30:00Z"),TimeEngine.localInstant(LocalDate.of(2026,10,25),150,ZoneId.of("Europe/Berlin")))}
 @Test fun timezoneChangeResolvesNewAbsoluteTimestamp() {val c=config();assertNotEquals(TimeEngine.sleep(c,date)?.start,TimeEngine.sleep(c.copy(prefs=c.prefs.copy(zoneId="Asia/Tehran")),date)?.start)}
 @Test fun disabledDaysAreSkipped() {val c=config().copy(prefs=config().prefs.copy(baseSleep=false),sleep=listOf(SleepRule(5)));assertEquals(DayOfWeek.FRIDAY,TimeEngine.activeSleep(c,Instant.parse("2026-11-16T10:00:00Z"))?.owner?.dayOfWeek)}
 @Test fun overdueSessionStaysUntilWake() {val c=config();assertEquals(date,TimeEngine.activeSleep(c,Instant.parse("2026-11-17T01:00:00Z"))?.owner)}
 @Test fun unknownHolidaysAreExplicitlyProvisional() {val h=object: HolidayProvider {override fun status(date: LocalDate,region: HolidayRegion)=HolidayState.UNKNOWN;override fun getDataStatus(region: HolidayRegion,year: Int)=HolidayDataStatus(null,null,"missing",false)};assertEquals(setOf(date),budget(holidays=h).uncertainDates)}
 @Test fun chartSegmentsExactlyFillTheWindow() {val b=budget();val occupied=b.breakdown.values.fold(Duration.ZERO){a,v->a+v};assertEquals(Duration.between(b.start,b.end),occupied+b.free)}

 @Test fun distantOneNightExceptionIsFound() {val target=date.plusDays(30);val c=Configuration(prefs=Preferences(zoneId="UTC",baseSleep=false),overrides=listOf(DateOverride(target.toString(),sleepEnabled=true,bedtime=1380,wake=420)));assertEquals(target,TimeEngine.activeSleep(c,date.atStartOfDay().toInstant(ZoneOffset.UTC))?.owner)}
 @Test fun disabledWeeksDoNotHideNextEnabledNight() {val c=config().copy(prefs=config().prefs.copy(baseSleep=false),sleep=listOf(SleepRule(1)),overrides=listOf(DateOverride(date.toString(),sleepEnabled=false),DateOverride(date.plusWeeks(1).toString(),sleepEnabled=false)));assertEquals(date.plusWeeks(2),TimeEngine.activeSleep(c,date.atStartOfDay().toInstant(ZoneOffset.UTC))?.owner)}
 @Test fun unconfiguredDaysUseBaseSleepWhenEnabled() {
  val c=config().copy(prefs=config().prefs.copy(baseSleep=true,baseBedtime=1320,baseWake=360),sleep=listOf(SleepRule(5)))
  val occurrence=TimeEngine.activeSleep(c,Instant.parse("2026-11-16T10:00:00Z"))
  assertEquals(DayOfWeek.MONDAY,occurrence?.owner?.dayOfWeek)
  val mondayOccurrence=TimeEngine.sleep(c,date)!!
  assertEquals(Instant.parse("2026-11-16T22:00:00Z"),mondayOccurrence.start)
  assertEquals(Instant.parse("2026-11-17T06:00:00Z"),mondayOccurrence.end)
 }
 @Test fun disabledOverrideWinsOverBaseSleep() {
  val c=config().copy(prefs=config().prefs.copy(baseSleep=true),sleep=listOf(SleepRule(5)),overrides=listOf(DateOverride(date.toString(),sleepEnabled=false)))
  assertNull(TimeEngine.sleep(c,date))
 }
 @Test fun collapsedDstSleepNeverBecomesNegative() {val d=LocalDate.of(2026,3,28);val c=Configuration(prefs=Preferences(zoneId="Europe/Berlin"),sleep=listOf(SleepRule(6,bedtime=150,wake=180)));val o=TimeEngine.sleep(c,d)!!;assertFalse(o.end<o.start)}
}
