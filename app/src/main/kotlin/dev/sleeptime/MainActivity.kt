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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
           dashboard = { onReports -> Dashboard(state, vm, openSettings, onReports, { editor = it }, confirmed) },
           reports = { onBack -> StatisticsScreen(state, vm, onBack = onBack, onSettings = openSettings) }
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
 dashboard: @Composable (onReports: () -> Unit) -> Unit,
 reports: @Composable (onBack: () -> Unit) -> Unit
) {
 val pager = rememberPagerState { 2 }
 val scope = rememberCoroutineScope()
 val reduced = LocalReducedMotion.current
 fun goToPage(page: Int) {
  scope.launch {
   if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page)
  }
 }
 HorizontalPager(
  state = pager,
  modifier = Modifier.fillMaxSize()
 ) { page ->
  if (page == 0) dashboard { goToPage(1) } else reports { goToPage(0) }
 }
}
