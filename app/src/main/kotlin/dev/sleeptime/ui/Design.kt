package dev.sleeptime.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import dev.sleeptime.R
import dev.sleeptime.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val SleepColor = Color(0xFFAF9CF7)
val WorkColor = Color(0xFF7BB6EF)
val ExerciseColor = Color(0xFFE9B27C)
val FreeColor = Color(0xFF75CEB9)
val CardShape = RoundedCornerShape(28.dp)
val LocalReducedMotion = staticCompositionLocalOf { false }
@Composable fun SleepTheme(prefs: Preferences, content: @Composable () -> Unit) {
 val dark = when(prefs.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; else -> true }
 val accent = when(prefs.accent) { "teal" -> FreeColor; "blue" -> WorkColor; else -> if(prefs.accent.matches(Regex("#[0-9a-fA-F]{6}"))) Color(android.graphics.Color.parseColor(prefs.accent)) else SleepColor }
 var colors = if(dark) darkColorScheme(primary=accent,onPrimary=Color(0xFF211C38),primaryContainer=Color(0xFF36304E),secondary=FreeColor,background=Color(0xFF0B1021),onBackground=Color(0xFFF1F0FA),surface=Color(0xFF141B2F),onSurface=Color(0xFFF1F0FA),surfaceVariant=Color(0xFF222A40),onSurfaceVariant=Color(0xFFA7ADC2),outline=Color(0xFF4F576C)) else lightColorScheme(primary=if(prefs.accent=="violet")Color(0xFF625198)else lerp(accent,Color.Black,.30f),onPrimary=Color.White,primaryContainer=Color(0xFFE8E0FA),secondary=Color(0xFF257F6C),background=Color(0xFFF6F5FA),onBackground=Color(0xFF202335),surface=Color.White,onSurface=Color(0xFF202335),surfaceVariant=Color(0xFFEEECF4),onSurfaceVariant=Color(0xFF6B6F82),outline=Color(0xFFCAC7D5))
 if(prefs.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31) colors = if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
 if(prefs.theme == ThemeMode.AMOLED) colors = colors.copy(background=Color.Black,surface=Color(0xFF0E0E14))
 val locale = LocalConfiguration.current.locales[0]
 val fontResource=R.font.estedad
 val font = if(locale.language!="fa") FontFamily.SansSerif else FontFamily(listOf(Font(fontResource,FontWeight.Normal),Font(fontResource,FontWeight.Medium),Font(fontResource,FontWeight.SemiBold),Font(fontResource,FontWeight.Bold)))
 val base = Typography()
 val typography = Typography(
  displayLarge=base.displayLarge.copy(fontFamily=font,fontWeight=FontWeight.Medium,fontFeatureSettings="tnum"),
  displayMedium=base.displayMedium.copy(fontFamily=font,fontFeatureSettings="tnum"),
  headlineLarge=base.headlineLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  headlineMedium=base.headlineMedium.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  titleLarge=base.titleLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  titleMedium=base.titleMedium.copy(fontFamily=font,fontWeight=FontWeight.Medium),
  titleSmall=base.titleSmall.copy(fontFamily=font),
  bodyLarge=base.bodyLarge.copy(fontFamily=font,lineHeight=27.sp),
  bodyMedium=base.bodyMedium.copy(fontFamily=font,lineHeight=23.sp),
  bodySmall=base.bodySmall.copy(fontFamily=font,lineHeight=20.sp),
  labelLarge=base.labelLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  labelMedium=base.labelMedium.copy(fontFamily=font),labelSmall=base.labelSmall.copy(fontFamily=font))
 CompositionLocalProvider(LocalReducedMotion provides (prefs.reduceMotion || !ValueAnimator.areAnimatorsEnabled())) { MaterialTheme(colorScheme=colors,typography=typography,shapes=Shapes(extraLarge=CardShape),content=content) }
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
 Surface(modifier,shape=CardShape,color=MaterialTheme.colorScheme.surface) { Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
}
@Composable fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Text(text,style=MaterialTheme.typography.labelMedium,letterSpacing=if(LocalConfiguration.current.locales[0].language=="fa") 0.sp else 1.5.sp,color=color)
@Composable fun SectionLabel(text: String, action: (@Composable () -> Unit)? = null) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(text,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); action?.invoke() }
}
@Composable fun AppIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier.size(22.dp)) { Icon(icon,label?.let { stringResource(it) },modifier,tint=tint) }
fun number(value: Any, p: Preferences): String {
 val s=value.toString(); return if(p.persianNumerals) s.map { if(it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it }.joinToString("") else s
}
fun duration(context: Context, seconds: Long, p: Preferences): String {
 val min = seconds.coerceAtLeast(0)/60
 return context.getString(R.string.hours_minutes,number(min/60,p),number(min%60,p))
}
fun clockText(time: Instant, p: Preferences, locale: Locale): String = number(DateTimeFormatter.ofPattern(if(p.clock24) "HH:mm" else "h:mm a",locale).withZone(p.zone()).format(time),p)
fun minutesText(minutes: Int, p: Preferences, locale: Locale): String = number(LocalTime.of(minutes/60,minutes%60).format(DateTimeFormatter.ofPattern(if(p.clock24) "HH:mm" else "h:mm a",locale)),p)
fun dateText(date: LocalDate, p: Preferences, locale: Locale, short: Boolean=false): String {
 if(!p.jalali) return number(date.format(DateTimeFormatter.ofPattern(if(short) "MMM d" else "EEEE, MMMM d",locale)),p)
 val d=PersianDates.fromGregorian(date)
 val fa=arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
 val en=arrayOf("Farvardin","Ordibehesht","Khordad","Tir","Mordad","Shahrivar","Mehr","Aban","Azar","Dey","Bahman","Esfand")
 val month=if(locale.language=="fa") fa[d.month-1] else en[d.month-1]
 return (if(short) "" else date.dayOfWeek.getDisplayName(TextStyle.FULL,locale)+"، ")+number(d.day,p)+" "+month+(if(short) "" else " "+number(d.year,p))
}
fun routineText(context: Context, item: RoutineItem): String = if(item.defaultKey == null) item.text else context.getString(when(item.defaultKey) {1->R.string.routine_phone;2->R.string.routine_teeth;3->R.string.routine_clothes;else->R.string.routine_rest})
fun kindTitle(kind: Kind?): Int = when(kind) { Kind.SLEEP->R.string.sleep;Kind.WORK->R.string.work;Kind.EXERCISE->R.string.exercise;null->R.string.free }
fun kindColor(kind: Kind?): Color = when(kind) { Kind.SLEEP->SleepColor;Kind.WORK->WorkColor;Kind.EXERCISE->ExerciseColor;null->FreeColor }
