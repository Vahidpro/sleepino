package pasac.sleepino
import android.app.Application
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import pasac.sleepino.platform.MaintenanceWorker
import java.util.concurrent.TimeUnit
@HiltAndroidApp class SleepTimeApplication: Application() {
 @javax.inject.Inject lateinit var backup: pasac.sleepino.data.BackupManager
 override fun onCreate() {
  super.onCreate()
  kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { backup.recoverPending() }
  WorkManager.getInstance(this).enqueueUniquePeriodicWork("schedule-reconciliation",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<MaintenanceWorker>(12,TimeUnit.HOURS).build())
 }
}
