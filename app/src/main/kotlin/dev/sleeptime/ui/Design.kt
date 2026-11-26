package dev.sleeptime.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.ExperimentalTextApi
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
val EarlyColor = Color(0xFF8E9CF5)
val LateColor = Color(0xFFF3A790)
val WarningAmber = Color(0xFFE5A93B)
val CardShape = RoundedCornerShape(28.dp)
val LocalReducedMotion = staticCompositionLocalOf { false }
private val HexAccent = Regex("#[0-9a-fA-F]{6}")

internal fun <T> motionSpec(reduced: Boolean, durationMillis: Int = 220): FiniteAnimationSpec<T> =
 if (reduced) snap() else tween(durationMillis, easing = FastOutSlowInEasing)

internal fun sectionEnter(reduced: Boolean): EnterTransition = if (reduced) EnterTransition.None else
 expandVertically(animationSpec = motionSpec(false), expandFrom = Alignment.Top) + fadeIn(motionSpec(false))

internal fun sectionExit(reduced: Boolean): ExitTransition = if (reduced) ExitTransition.None else
 shrinkVertically(animationSpec = motionSpec(false, 180), shrinkTowards = Alignment.Top) + fadeOut(motionSpec(false, 150))

@Composable internal fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
 val pressed by interactionSource.collectIsPressedAsState()
 val reduced = LocalReducedMotion.current
 val scale = animateFloatAsState(
  if (pressed && !reduced) 0.975f else 1f,
  animationSpec = motionSpec(reduced, if (pressed) 90 else 160),
  label = "button press"
 )
 return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
@OptIn(ExperimentalTextApi::class)
@Composable fun SleepTheme(prefs: Preferences, content: @Composable () -> Unit) {
 val dark = when(prefs.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; else -> true }
 val context = LocalContext.current
 val configuration = LocalConfiguration.current
 val colors = remember(dark,prefs.accent,prefs.theme,prefs.dynamicColor,context,configuration) {
  val accent = AccentPreset.entries.firstOrNull { it.id == prefs.accent }?.let { Color(it.swatch) }
   ?: if (prefs.accent.matches(HexAccent)) Color(android.graphics.Color.parseColor(prefs.accent)) else SleepColor
  val scheme = when {
   prefs.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 ->
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
   dark -> darkColorScheme(
    primary=accent,onPrimary=if(accent.luminance() > .179f) Color.Black else Color.White,
    primaryContainer=lerp(Color(0xFF25253B),accent,.16f),secondary=FreeColor,
    background=lerp(Color(0xFF0B1021),accent,.04f),onBackground=Color(0xFFF1F0FA),
    surface=lerp(Color(0xFF141B2F),accent,.06f),onSurface=Color(0xFFF1F0FA),
    surfaceVariant=Color(0xFF222A40),onSurfaceVariant=Color(0xFFA7ADC2),outline=Color(0xFF4F576C)
   )
   else -> lightColorScheme(
    primary=lerp(accent,Color.Black,.51f),onPrimary=Color.White,
    primaryContainer=lerp(Color.White,accent,.26f),secondary=Color(0xFF257F6C),
    background=lerp(Color(0xFFF9F8FC),accent,.09f),onBackground=Color(0xFF202335),
    surface=Color.White,onSurface=Color(0xFF202335),
    surfaceVariant=Color(0xFFEEECF4),onSurfaceVariant=Color(0xFF6B6F82),outline=Color(0xFFCAC7D5)
   )
  }
  if (prefs.theme == ThemeMode.AMOLED) scheme.copy(background=Color.Black,surface=Color(0xFF0E0E14)) else scheme
 }
 val persian = if (prefs.language == "system") configuration.locales[0].language == "fa" else prefs.language == "fa"
 val typography = remember(persian) { appTypography(persian) }
 CompositionLocalProvider(LocalReducedMotion provides (prefs.reduceMotion || !ValueAnimator.areAnimatorsEnabled())) { MaterialTheme(colorScheme=colors,typography=typography,shapes=Shapes(extraLarge=CardShape),content=content) }
}

@OptIn(ExperimentalTextApi::class)
internal fun appTypography(persian: Boolean): Typography {
 val font = if (!persian) FontFamily.SansSerif else FontFamily(
  Font(R.font.estedad,FontWeight.Normal),Font(R.font.estedad,FontWeight.Medium),
  Font(R.font.estedad,FontWeight.SemiBold),Font(R.font.estedad,FontWeight.Bold),
  Font(R.font.estedad,FontWeight.Black,variationSettings=FontVariation.Settings(FontVariation.weight(FontWeight.Black.weight)))
 )
 val base = Typography()
 return Typography(
  displayLarge=base.displayLarge.copy(fontFamily=font,fontWeight=FontWeight.Medium,fontFeatureSettings="tnum"),
  displayMedium=base.displayMedium.copy(fontFamily=font,fontFeatureSettings="tnum"),
  displaySmall=base.displaySmall.copy(fontFamily=font,fontFeatureSettings="tnum"),
  headlineLarge=base.headlineLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  headlineMedium=base.headlineMedium.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  headlineSmall=base.headlineSmall.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  titleLarge=base.titleLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  titleMedium=base.titleMedium.copy(fontFamily=font,fontWeight=FontWeight.Medium),
  titleSmall=base.titleSmall.copy(fontFamily=font),
  bodyLarge=base.bodyLarge.copy(fontFamily=font,lineHeight=27.sp),
  bodyMedium=base.bodyMedium.copy(fontFamily=font,lineHeight=23.sp),
  bodySmall=base.bodySmall.copy(fontFamily=font,lineHeight=20.sp),
  labelLarge=base.labelLarge.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
  labelMedium=base.labelMedium.copy(fontFamily=font),
  labelSmall=base.labelSmall.copy(fontFamily=font)
 )
}
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
 Surface(modifier,shape=CardShape,color=MaterialTheme.colorScheme.surface) { Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
}
@Composable fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Text(text,style=MaterialTheme.typography.labelMedium,letterSpacing=if(LocalConfiguration.current.locales[0].language=="fa") 0.sp else 1.5.sp,color=color)
@Composable fun SectionLabel(text: String, action: (@Composable () -> Unit)? = null) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(text,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)); action?.invoke() }
}
@Composable fun AppIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier.size(22.dp)) { Icon(icon,label?.let { stringResource(it) },modifier,tint=tint) }
fun number(value: Any, p: Preferences, locale: Locale = Locale.getDefault()): String =
 number(value, if (p.language == "system") locale else Locale.forLanguageTag(p.language))

fun number(value: Any, locale: Locale): String {
 val text = value.toString()
 return if (locale.language == "fa") buildString(text.length) {
  text.forEach { char -> append(char.digitToIntOrNull()?.let { "۰۱۲۳۴۵۶۷۸۹"[it] } ?: char) }
 } else text
}

fun duration(context: Context, seconds: Long, p: Preferences): String {
 val min = seconds.coerceAtLeast(0)/60
 val locale = context.resources.configuration.locales[0]
 val hours = min / 60
 val minutes = min % 60
 val hoursText = number(hours, p, locale)
 val minutesText = number(minutes, p, locale)
 return when {
  minutes == 0L -> context.getString(R.string.hours_only, hoursText)
  hours == 0L -> context.getString(R.string.minutes_only, minutesText)
  else -> context.getString(R.string.hours_minutes, hoursText, minutesText)
 }
}
fun clockText(time: Instant, p: Preferences, locale: Locale): String = number(DateTimeFormatter.ofPattern(if(p.clock24) "HH:mm" else "h:mm a",locale).withZone(p.zone()).format(time),p,locale)
fun minutesText(minutes: Int, p: Preferences, locale: Locale): String = number(LocalTime.of(minutes/60,minutes%60).format(DateTimeFormatter.ofPattern(if(p.clock24) "HH:mm" else "h:mm a",locale)),p,locale)
fun isJalali(p: Preferences, locale: Locale): Boolean = p.jalali || locale.language == "fa" || p.language == "fa"
fun jalaliMonthName(month: Int, locale: Locale): String {
 val fa=arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
 val en=arrayOf("Farvardin","Ordibehesht","Khordad","Tir","Mordad","Shahrivar","Mehr","Aban","Azar","Dey","Bahman","Esfand")
 return if(locale.language=="fa") fa[month-1] else en[month-1]
}
fun dateText(date: LocalDate, p: Preferences, locale: Locale, short: Boolean=false): String {
 if(!isJalali(p, locale)) return number(date.format(DateTimeFormatter.ofPattern(if(short) "MMM d" else "EEEE, MMMM d",locale)),p,locale)
 val d=PersianDates.fromGregorian(date)
 val month=jalaliMonthName(d.month, locale)
 return (if(short) "" else date.dayOfWeek.getDisplayName(TextStyle.FULL,locale)+"، ")+number(d.day,p,locale)+" "+month+(if(short) "" else " "+number(d.year,p,locale))
}
fun kindTitle(kind: Kind?): Int = when(kind) { Kind.SLEEP->R.string.sleep;Kind.WORK->R.string.work;Kind.EXERCISE->R.string.exercise;null->R.string.free }
fun kindColor(kind: Kind?): Color = when(kind) { Kind.SLEEP->SleepColor;Kind.WORK->WorkColor;Kind.EXERCISE->ExerciseColor;null->FreeColor }
