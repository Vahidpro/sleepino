package pasac.sleepino.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pasac.sleepino.core.*
import java.time.*

/** Fixtures are preview-only; no user repository is seeded with these values. */
fun previewState(
 prefs: Preferences=Preferences(zoneId="Asia/Tehran",theme=ThemeMode.DARK),
 now: Instant=Instant.parse("2026-11-17T16:30:00Z")
): DashboardState {
 val c=Configuration(prefs=prefs,sleep=(1..7).map{SleepRule(it)},work=(1..5).map{DayRule(it,listOf(TimeSlot(540,1080)))})
 val date=now.atZone(prefs.zone()).toLocalDate()
 val h=object:HolidayProvider{override fun status(date: LocalDate,region: HolidayRegion)=HolidayState.WORKDAY;override fun getDataStatus(region: HolidayRegion,year: Int)=HolidayDataStatus(null,null,"preview",true)}
 val first=TimeEngine.weekStart(date,prefs.weekStart)
 return DashboardState(c,now,TimeEngine.activeSleep(c,now),false,TimeEngine.activeOrNextWork(c,now,h),TimeEngine.budget(c,date,1,now,h),TimeEngine.budget(c,first,7,now,h),(0L..6L).map{first.plusDays(it).let{d->d to TimeEngine.budget(c,d,1,now,h)}},emptyList(),null)
}
@Preview(name = "Midnight", showBackground = true, widthDp = 393, heightDp = 760)
@Preview(name = "Persian", locale = "fa", showBackground = true, widthDp = 393, heightDp = 760)
@Preview(name = "Large font", fontScale = 1.7f, showBackground = true, widthDp = 360, heightDp = 620)
@Composable fun BedtimeHeroPreview() {
 val sample = previewState()
 val s = sample.copy(now = checkNotNull(sample.occurrence).start.minusSeconds(71 * 60L))
 SleepTheme(s.config.prefs) {
  BedtimeHero(s, {}, {}, {}, {}, {})
 }
}
@Preview(name="Daylight",showBackground=true,widthDp=393)
@Composable fun DaylightPreview(){val s=previewState(Preferences(zoneId="Asia/Tehran",theme=ThemeMode.LIGHT));SleepTheme(s.config.prefs){Surface{WeeklyCard(s.week,s.days,s.config,isCurrent=true,Modifier.padding(20.dp))}}}
@Preview(name="Dawn Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun DawnHeroPreview(){val s=previewState().let{it.copy(now=LocalDateTime.of(2026,11,17,5,45).atZone(it.config.prefs.zone()).toInstant())};SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Morning Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun MorningHeroPreview(){val s=previewState().let{it.copy(now=LocalDateTime.of(2026,11,17,8,30).atZone(it.config.prefs.zone()).toInstant())};SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Day Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun DayHeroPreview(){val s=previewState().let{it.copy(now=LocalDateTime.of(2026,11,17,13,0).atZone(it.config.prefs.zone()).toInstant())};SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Golden Hour Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun GoldenHourHeroPreview(){val s=previewState().let{it.copy(now=LocalDateTime.of(2026,11,17,16,45).atZone(it.config.prefs.zone()).toInstant())};SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Twilight Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun TwilightHeroPreview(){val s=previewState().let{it.copy(now=LocalDateTime.of(2026,11,17,18,45).atZone(it.config.prefs.zone()).toInstant())};SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Wind-Down Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun WindDownHeroPreview(){val sample=previewState();val s=sample.copy(now=checkNotNull(sample.occurrence).start.minusSeconds(18*60L));SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Snoozed Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun SnoozedHeroPreview(){val sample=previewState();val sleep=checkNotNull(sample.occurrence);val now=sleep.start.plusSeconds(4*60L);val s=sample.copy(now=now,activeSession=ActiveSession(sleep.key,sleep.start.toEpochMilli(),now.plusSeconds(10*60L).toEpochMilli(),snoozes=1));SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Overdue Dark Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun OverdueHeroPreview(){val sample=previewState();val s=sample.copy(now=checkNotNull(sample.occurrence).start.plusSeconds(18*60L));SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Resting Sky",showBackground=true,widthDp=393,heightDp=760)
@Composable fun RestingHeroPreview(){val sample=previewState();val s=sample.copy(now=checkNotNull(sample.occurrence).start.plusSeconds(5*60L),confirmed=true);SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Unconfigured Sleep",showBackground=true,widthDp=393,heightDp=760)
@Composable fun UnconfiguredHeroPreview(){val s=previewState().copy(occurrence=null);SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Rest Day (Day)",showBackground=true,widthDp=393,heightDp=760)
@Composable fun RestDayDayHeroPreview(){val s=previewState(now=LocalDateTime.of(2026,11,21,13,0).atZone(ZoneId.of("Asia/Tehran")).toInstant());SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Rest Day (Overdue)",showBackground=true,widthDp=393,heightDp=760)
@Composable fun RestDayOverdueHeroPreview(){val sample=previewState(now=LocalDateTime.of(2026,11,21,21,0).atZone(ZoneId.of("Asia/Tehran")).toInstant());val s=sample.copy(now=checkNotNull(sample.occurrence).start.plusSeconds(18*60L));SleepTheme(s.config.prefs){BedtimeHero(s,{},{},{},{},{})}}
@Preview(name="Settings (Schedule & Focus)",showBackground=true,widthDp=393,heightDp=760)
@Composable fun SettingsSchedulePreview(){val s=previewState(Preferences(zoneId="Asia/Tehran",theme=ThemeMode.DARK,notifications=true,dnd=true));SleepTheme(s.config.prefs){Surface(Modifier.fillMaxSize()){SettingsContent(s=s,onBack={},onEditor={},holidayStatus=HolidayDataStatus(LocalDate.of(2026,3,21),LocalDate.of(2027,3,20),"preview",true))}}}
@Preview(name="Settings (Appearance & Home)",showBackground=true,widthDp=393,heightDp=760)
@Composable fun SettingsAppearancePreview(){val s=previewState(Preferences(zoneId="Asia/Tehran",theme=ThemeMode.DARK));SleepTheme(s.config.prefs){Surface(Modifier.fillMaxSize()){SettingsContent(s=s,onBack={},onEditor={},initialScrollIndex=4)}}}
