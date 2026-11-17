package dev.sleeptime.platform
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

class DeviceActivity @Inject constructor(@param:ApplicationContext private val context: Context) {
 fun hasAccess(): Boolean {
  val ops = context.getSystemService(AppOpsManager::class.java)
  @Suppress("DEPRECATION") val result = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)
  return result == AppOpsManager.MODE_ALLOWED
 }
 data class Snapshot(val lastInteraction: Instant?, val foregroundPackage: String?)
 fun snapshot(now: Instant): Snapshot {
  if (!hasAccess()) return Snapshot(null,null)
  return runCatching {
   val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now.minusSeconds(10800).toEpochMilli(),now.toEpochMilli())
   val event = UsageEvents.Event(); var last: Instant? = null; var foreground: String? = null
   while (events.hasNextEvent()) {
    events.getNextEvent(event)
    @Suppress("DEPRECATION") when(event.eventType) {
     UsageEvents.Event.MOVE_TO_FOREGROUND -> { last = Instant.ofEpochMilli(event.timeStamp); foreground = event.packageName }
     UsageEvents.Event.USER_INTERACTION -> last = Instant.ofEpochMilli(event.timeStamp)
     UsageEvents.Event.SCREEN_NON_INTERACTIVE -> foreground = null
    }
   }
   Snapshot(last,foreground)
  }.getOrDefault(Snapshot(null,null))
 }
}
