package dev.sleeptime

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import dev.sleeptime.core.Preferences
import dev.sleeptime.ui.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@AndroidEntryPoint class MainActivity: AppCompatActivity() {
 private val vm: AppViewModel by viewModels()
 private var requestedAction by mutableStateOf<String?>(null)
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge()
  requestedAction=if(savedInstanceState==null)intent.action else null
  setContent {
   val s by vm.state.collectAsStateWithLifecycle()
   val prefs=s?.config?.prefs?:Preferences()
   AppLanguageResources(prefs.language) { SleepTheme(prefs) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
     if(s==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
     else {
      val state=s!!;val nav=rememberNavController();val firstRoute=remember {if (prefs.onboarded) "dashboard" else "welcome"};var editor by rememberSaveable{mutableStateOf<String?>(null)}
      val snackbar=remember{SnackbarHostState()};val scope=rememberCoroutineScope();val resources=LocalResources.current
      val confirmed: (String) -> Unit = {
       scope.launch { snackbar.showSnackbar(resources.getString(R.string.sleep_confirmed)) }
      }
      LaunchedEffect(vm,resources){vm.message.collect {snackbar.showSnackbar(resources.getString(it))}}
      LaunchedEffect(prefs.language){val locales=if(prefs.language=="system")LocaleListCompat.getEmptyLocaleList()else LocaleListCompat.forLanguageTags(prefs.language);if(AppCompatDelegate.getApplicationLocales()!=locales)AppCompatDelegate.setApplicationLocales(locales)}
      LaunchedEffect(requestedAction){if(requestedAction==ACTION_SLEEP){requestedAction=null;vm.confirm(confirmed)}}
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME){vm.resume()}
      Scaffold(snackbarHost={SnackbarHost(snackbar)},containerColor=MaterialTheme.colorScheme.background) {padding->
       Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
        val reduced = LocalReducedMotion.current
        NavHost(
         nav, startDestination = firstRoute, modifier = Modifier.fillMaxSize(),
         enterTransition = { if (reduced) EnterTransition.None else fadeIn(motionSpec(false)) },
         exitTransition = { if (reduced) ExitTransition.None else fadeOut(motionSpec(false, 180)) }
        ) {
         composable("welcome") {
          val begin = dropUnlessResumed { nav.navigate("dashboard") { popUpTo("welcome") { inclusive = true } } }
          Onboarding(state, vm, { editor = it }, begin)
         }
         composable("dashboard") {
          val openSettings = dropUnlessResumed { nav.navigate("settings") { launchSingleTop = true } }
          HomePages(
           onSettings = openSettings,
           dashboard = { Dashboard(state, vm, openSettings, { editor = it }, confirmed) },
           reports = { StatisticsScreen(state, vm) { editor = it } }
          )
         }
         composable("settings") {
          SettingsScreen(state, vm, dropUnlessResumed { nav.popBackStack() }, { editor = it })
         }
        }
        editor?.let {EditorSheet(it,state,vm){editor=null}}
       }
      }
     }
    }
   } }
  }
 }
 override fun onNewIntent(intent: Intent){super.onNewIntent(intent);setIntent(intent);requestedAction=intent.action}
 companion object {const val ACTION_SLEEP="dev.sleeptime.SLEEP_FROM_SHORTCUT"}
}

@Composable
internal fun AppLanguageResources(language: String, content: @Composable () -> Unit) {
 val context = LocalContext.current
 val configuration = LocalConfiguration.current
 val resources = LocalResources.current
 // Compose can render before AppCompat reapplies the selected locale to the activity.
 val localized = remember(context,configuration,language) {
  if (language == "system" || language == configuration.locales[0].language) null
  else context.createConfigurationContext(Configuration(configuration).apply {
   setLocale(Locale.forLanguageTag(language))
  }).resources
 }
 val direction = if (localized == null) LocalLayoutDirection.current
  else if (localized.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl
  else LayoutDirection.Ltr
 CompositionLocalProvider(
  LocalResources provides (localized ?: resources),
  LocalConfiguration provides (localized?.configuration ?: configuration),
  LocalLayoutDirection provides direction,
  content = content
 )
}

@Composable
internal fun HomePages(
 onSettings: () -> Unit,
 dashboard: @Composable () -> Unit,
 reports: @Composable () -> Unit
) {
 val pager = rememberPagerState { 2 }
 val openSettings by rememberUpdatedState(onSettings)
 val settingsLabel = stringResource(R.string.settings)
 Box(Modifier.fillMaxSize()) {
  HorizontalPager(
   state = pager,
   modifier = Modifier.fillMaxSize().semantics {
    customActions = listOf(CustomAccessibilityAction(settingsLabel) { openSettings(); true })
   }.pointerInput(Unit) {
    awaitEachGesture {
     val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
     if (down.position.y > 72.dp.toPx()) return@awaitEachGesture
     while (true) {
      val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
      if (change == null || !change.pressed) break
      val distance = change.position - down.position
      if (distance.y < -viewConfiguration.touchSlop ||
       (abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y))
      ) break
      if (distance.y > 44.dp.toPx() && distance.y > 1.4f * abs(distance.x)) {
       change.consume()
       openSettings()
       break
      }
     }
    }
   }
  ) { page ->
   if (page == 0) dashboard() else reports()
  }
  val current = pager.settledPage
  val description = stringResource(if (current == 0) R.string.dashboard else R.string.statistics)
  val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
  val reduced = LocalReducedMotion.current
  Box(
   Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
    .size(width = 56.dp, height = 24.dp).background(Color(0xB30B1527), CircleShape)
    .semantics { contentDescription = description }
    .drawBehind {
     val gap = 18.dp.toPx()
     val position = if (reduced) pager.settledPage.toFloat()
      else (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
     drawCircle(Color.White.copy(alpha = .4f), 3.dp.toPx(), center - Offset(gap / 2, 0f))
     drawCircle(Color.White.copy(alpha = .4f), 3.dp.toPx(), center + Offset(gap / 2, 0f))
     val radius = 3.dp.toPx()
     val width = 14.dp.toPx()
     val selectedX = center.x + (position - .5f) * gap * direction
     drawRoundRect(Color.White, Offset(selectedX - width / 2, center.y - radius),
      Size(width, radius * 2), CornerRadius(radius))
    }
  )
 }
}
