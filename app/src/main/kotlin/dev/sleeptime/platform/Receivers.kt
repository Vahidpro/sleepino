package dev.sleeptime.platform
import android.content.*
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint @InstallIn(SingletonComponent::class) interface PlatformEntryPoint { fun controller(): ReminderController }
fun controller(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext,PlatformEntryPoint::class.java).controller()
class AlarmReceiver: BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  val result = goAsync()
  CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
   try {
    val c = controller(context)
    when(intent.action) {
     ReminderController.ACTION_ALARM -> c.fire(intent.getStringExtra("owner").orEmpty(),intent.getStringExtra("event").orEmpty(),intent.getIntExtra("fingerprint",0))
     Notifications.ACTION_CONFIRM -> c.confirm(intent.getStringExtra("owner"))
     Notifications.ACTION_SNOOZE -> c.snooze(owner=intent.getStringExtra("owner"))
     else -> c.reconcile()
    }
   } finally { result.finish() }
  }
 }
}
class MaintenanceWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
 override suspend fun doWork(): Result = try { controller(applicationContext).reconcile(); dev.sleeptime.widgets.refreshWidgets(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
}
