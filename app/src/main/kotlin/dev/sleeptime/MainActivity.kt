package dev.sleeptime

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint
import dev.sleeptime.core.Preferences
import dev.sleeptime.ui.*
import kotlinx.coroutines.launch

@AndroidEntryPoint class MainActivity: AppCompatActivity() {
 private val vm: AppViewModel by viewModels()
 private var requestedAction by mutableStateOf<String?>(null)
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState);enableEdgeToEdge()
  requestedAction=if(savedInstanceState==null)intent.action else null
  setContent {
   val s by vm.state.collectAsStateWithLifecycle()
   val prefs=s?.config?.prefs?:Preferences()
   SleepTheme(prefs) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
     if(s==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
     else {
      val state=s!!;val nav=rememberNavController();val firstRoute=remember {if(prefs.onboarded)"dashboard"else "welcome"};var editor by rememberSaveable{mutableStateOf<String?>(null)}
      val snackbar=remember{SnackbarHostState()};val scope=rememberCoroutineScope();val resources=LocalResources.current
      fun confirmed(key: String){scope.launch{val result=snackbar.showSnackbar(resources.getString(R.string.sleep_confirmed),resources.getString(R.string.undo),duration=SnackbarDuration.Long);if(result==SnackbarResult.ActionPerformed)vm.undo(key)}}
      LaunchedEffect(vm,resources){vm.message.collect {snackbar.showSnackbar(resources.getString(it))}}
      LaunchedEffect(prefs.language){val locales=if(prefs.language=="system")LocaleListCompat.getEmptyLocaleList()else LocaleListCompat.forLanguageTags(prefs.language);if(AppCompatDelegate.getApplicationLocales()!=locales)AppCompatDelegate.setApplicationLocales(locales)}
      LaunchedEffect(requestedAction){if(requestedAction==ACTION_SLEEP){requestedAction=null;vm.confirm(::confirmed)}}
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME){vm.resume()}
      Scaffold(snackbarHost={SnackbarHost(snackbar)},containerColor=MaterialTheme.colorScheme.background) {padding->
       Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
        NavHost(nav,startDestination=firstRoute,modifier=Modifier.fillMaxSize()) {
         composable("welcome"){Onboarding(state,vm,{editor=it}){nav.navigate("dashboard"){popUpTo("welcome"){inclusive=true}}}}
         composable("dashboard"){Dashboard(state,vm,{nav.navigate("settings")},{nav.navigate("statistics")},{editor=it},::confirmed)}
         composable("settings"){SettingsScreen(state,vm,{nav.popBackStack()},{editor=it})}
         composable("statistics"){StatisticsScreen(state,vm){nav.popBackStack()}}
        }
        editor?.let {EditorSheet(it,state,vm){editor=null}}
       }
      }
     }
    }
   }
  }
 }
 override fun onNewIntent(intent: Intent){super.onNewIntent(intent);setIntent(intent);requestedAction=intent.action}
 companion object {const val ACTION_SLEEP="dev.sleeptime.SLEEP_FROM_SHORTCUT"}
}
