package dev.sleeptime.platform
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sleeptime.MainActivity
import dev.sleeptime.R
import dev.sleeptime.core.*
import javax.inject.Inject

class Notifications @Inject constructor(@param:ApplicationContext private val context: Context) {
 fun allowed() = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
 fun show(config: Configuration, event: ReminderEvent, distracted: Boolean) {
  if (!allowed()) return
  val p = config.prefs
  val displayContext=localizedContext(context,p)
  // Channel settings are immutable. New sound/vibration choices get a new, bounded identity.
  val hasSound = !p.silent
  val importance = if (!hasSound && !p.vibration) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
  val channelId = "bedtime_${listOf(p.soundUri,p.vibration,p.silent).hashCode()}"
  val manager = context.getSystemService(NotificationManager::class.java)
  val channel = NotificationChannel(channelId,displayContext.getString(R.string.bedtime_reminders),importance)
  val sound = when { !hasSound -> null; p.soundUri == "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); else -> Uri.parse(p.soundUri) }
  channel.setSound(sound, if (sound != null) AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build() else null)
  channel.enableVibration(p.vibration)
  manager.createNotificationChannel(channel)
  val open = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  fun action(name: String, code: Int) = PendingIntent.getBroadcast(context,code,Intent(context,AlarmReceiver::class.java).setAction(name).putExtra("owner",event.occurrenceKey),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val text = event.text.ifBlank { displayContext.getString(when { distracted -> R.string.distraction_notice; event.overdue -> R.string.overdue_notice; event.at.toEpochMilli() < (java.time.LocalDate.parse(event.occurrenceKey).atStartOfDay(config.prefs.zone()).toInstant().toEpochMilli()) -> R.string.prepare_notice; event.key.startsWith("stage") && config.reminders.firstOrNull { "stage:${it.id}" == event.key }?.offsetMinutes?.let { it < 0 } == true -> R.string.prepare_notice; else -> R.string.sleep_notice }) }
  val notification = NotificationCompat.Builder(context,channelId).setSmallIcon(R.drawable.ic_moon).setContentTitle(displayContext.getString(R.string.app_name)).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(false)
   .addAction(0,displayContext.getString(R.string.go_to_sleep),action(ACTION_CONFIRM,1))
   .addAction(0,displayContext.getString(R.string.snooze),action(ACTION_SNOOZE,2)).build()
  @Suppress("MissingPermission") manager.notify(42,notification)
 }
 fun dismiss() = context.getSystemService(NotificationManager::class.java).cancel(42)
 companion object { const val ACTION_CONFIRM = "dev.sleeptime.CONFIRM"; const val ACTION_SNOOZE = "dev.sleeptime.SNOOZE" }
}

fun localizedContext(context: Context,p: Preferences): Context {
 if(p.language=="system")return context
 val c=android.content.res.Configuration(context.resources.configuration);c.setLocale(java.util.Locale.forLanguageTag(p.language));return context.createConfigurationContext(c)
}
