package dev.sleeptime.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.sleeptime.MainActivity
import dev.sleeptime.R
import dev.sleeptime.core.*
import dev.sleeptime.data.*
import dev.sleeptime.platform.localizedContext
import dev.sleeptime.ui.clockText
import dev.sleeptime.ui.duration
import java.time.*

@EntryPoint @InstallIn(SingletonComponent::class) interface WidgetEntryPoint {fun store(): ConfigurationStore;fun holidays(): HolidayRepository;fun history(): HistoryDao;fun clock(): Clock}
class BedtimeWidget: GlanceAppWidget() {
 override val sizeMode=SizeMode.Responsive(setOf(DpSize(180.dp,120.dp),DpSize(280.dp,180.dp)))
 override suspend fun provideGlance(context: Context,id: GlanceId) {
  val entry=EntryPointAccessors.fromApplication(context.applicationContext,WidgetEntryPoint::class.java)
  val config=entry.store().get();val now=entry.clock().instant();val occurrence=TimeEngine.activeSleep(config,now);val confirmed=entry.history().all().any {it.key==occurrence?.key}
  val displayContext=localizedContext(context,config.prefs)
  val locale=displayContext.resources.configuration.locales[0]
  val title=displayContext.getString(if(confirmed)R.string.sleep_confirmed else R.string.widget_bedtime)
  val remaining=occurrence?.let { TimeEngine.remainingToBedtime(config, now, it, null, entry.holidays()) }
  val timer=remaining?.let { duration(displayContext, it.seconds, config.prefs) } ?: displayContext.getString(R.string.not_configured)
  val time=occurrence?.let {clockText(it.start,config.prefs,locale)}.orEmpty()
  provideContent {
   WidgetShell(config.prefs) {
    Text(title,style=TextStyle(fontSize=13.sp,fontWeight=FontWeight.Medium,color=ColorProvider(Color(0xFFBCA9F4))))
    Spacer(GlanceModifier.height(10.dp));Text(if(confirmed)displayContext.getString(R.string.rest_well)else timer,style=TextStyle(fontSize=22.sp,color=ColorProvider(widgetForeground())))
    Spacer(GlanceModifier.height(6.dp));Text(time,style=TextStyle(fontSize=12.sp,color=ColorProvider(widgetSecondary())))
    Text(displayContext.getString(R.string.as_of,clockText(now,config.prefs,locale)),style=TextStyle(fontSize=11.sp,color=ColorProvider(widgetSecondary())))
    if(occurrence!=null && !confirmed) {Spacer(GlanceModifier.height(8.dp));Button(displayContext.getString(R.string.go_to_sleep),onClick=actionStartActivity(Intent(context,MainActivity::class.java).setAction(MainActivity.ACTION_SLEEP)))}
   }
  }
 }
}
class BudgetWidget: GlanceAppWidget() {
 override val sizeMode=SizeMode.Responsive(setOf(DpSize(180.dp,120.dp),DpSize(280.dp,180.dp)))
 override suspend fun provideGlance(context: Context,id: GlanceId) {
  val entry=EntryPointAccessors.fromApplication(context.applicationContext,WidgetEntryPoint::class.java);val config=entry.store().get();val p=config.prefs;val now=entry.clock().instant();val date=now.atZone(p.zone()).toLocalDate()
  val displayContext=localizedContext(context,p)
  val locale=displayContext.resources.configuration.locales[0]
  val today=TimeEngine.budget(config,date,1,now,entry.holidays());val week=TimeEngine.budget(config,TimeEngine.weekStart(date,p.weekStart),7,now,entry.holidays())
  provideContent {
   WidgetShell(p) {
    Text(displayContext.getString(R.string.widget_budget),style=TextStyle(fontSize=13.sp,fontWeight=FontWeight.Medium,color=ColorProvider(Color(0xFF75CEB9))))
    Spacer(GlanceModifier.height(8.dp));Text(duration(displayContext,today.remainingFree.seconds,p),style=TextStyle(fontSize=22.sp,color=ColorProvider(widgetForeground())))
    Spacer(GlanceModifier.height(6.dp));Text(displayContext.getString(R.string.remaining_week,duration(displayContext,week.remainingFree.seconds,p)),style=TextStyle(fontSize=12.sp,color=ColorProvider(widgetSecondary())))
    Spacer(GlanceModifier.height(8.dp));Text(displayContext.getString(R.string.as_of,clockText(now,p,locale)),style=TextStyle(fontSize=11.sp,color=ColorProvider(widgetSecondary())))
   }
  }
 }
}
private val LocalWidgetDark=staticCompositionLocalOf {true}
@Composable private fun widgetForeground()=if(LocalWidgetDark.current)Color.White else Color(0xFF202335)
@Composable private fun widgetSecondary()=if(LocalWidgetDark.current)Color(0xFFB6BDD2)else Color(0xFF666A80)
@Composable private fun WidgetShell(p: Preferences,content: @Composable ColumnScope.()->Unit) {
 val systemDark=(LocalContext.current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
 val dark=when(p.theme){ThemeMode.SYSTEM->systemDark;ThemeMode.LIGHT->false;else->true}
 CompositionLocalProvider(LocalWidgetDark provides dark) {
  Column(GlanceModifier.fillMaxSize().background(if(p.theme==ThemeMode.AMOLED)Color.Black else if(dark)Color(0xFF151D30)else Color(0xFFF5F2FC)).cornerRadius(24.dp).padding(18.dp).clickable(actionStartActivity<MainActivity>()),content=content)
 }
}
class BedtimeWidgetReceiver: GlanceAppWidgetReceiver(){override val glanceAppWidget: GlanceAppWidget=BedtimeWidget()}
class BudgetWidgetReceiver: GlanceAppWidgetReceiver(){override val glanceAppWidget: GlanceAppWidget=BudgetWidget()}
suspend fun refreshWidgets(context: Context) {runCatching {BedtimeWidget().updateAll(context);BudgetWidget().updateAll(context)}}
