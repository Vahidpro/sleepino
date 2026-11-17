package dev.sleeptime.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sleeptime.core.*
import java.time.*

/** Fixtures are preview-only; no user repository is seeded with these values. */
fun previewState(prefs: Preferences=Preferences(zoneId="Asia/Tehran",theme=ThemeMode.DARK)): DashboardState {
 val c=Configuration(prefs=prefs,sleep=(1..7).map{SleepRule(it)},work=(1..5).map{DayRule(it,listOf(TimeSlot(540,1080)))})
 val now=Instant.parse("2026-11-17T16:30:00Z");val date=now.atZone(prefs.zone()).toLocalDate()
 val h=object:HolidayProvider{override fun status(date: LocalDate,region: HolidayRegion)=HolidayState.WORKDAY;override fun getDataStatus(region: HolidayRegion,year: Int)=HolidayDataStatus(null,null,"preview",true)}
 val first=TimeEngine.weekStart(date,prefs.weekStart)
 return DashboardState(c,now,TimeEngine.activeSleep(c,now),false,TimeEngine.activeOrNextWork(c,now,h),TimeEngine.budget(c,date,1,now,h),TimeEngine.budget(c,first,7,now,h),(0L..6L).map{first.plusDays(it).let{d->d to TimeEngine.budget(c,d,1,now,h)}},emptyList(),emptyList(),date,null)
}
@Preview(name="Midnight",showBackground=true,widthDp=393,backgroundColor=0xFF0B1021)
@Preview(name="Persian",locale="fa",showBackground=true,widthDp=393,backgroundColor=0xFF0B1021)
@Preview(name="Large font",fontScale=1.7f,showBackground=true,widthDp=360)
@Composable fun BedtimeHeroPreview(){val s=previewState();SleepTheme(s.config.prefs){Surface{Box(Modifier.padding(20.dp)){BedtimeHero(s,{},{},{})}}}}
@Preview(name="Daylight",showBackground=true,widthDp=393)
@Composable fun DaylightPreview(){val s=previewState(Preferences(zoneId="Asia/Tehran",theme=ThemeMode.LIGHT));SleepTheme(s.config.prefs){Surface{WeeklyCard(s.week,s.days,s.config.prefs,Modifier.padding(20.dp))}}}
