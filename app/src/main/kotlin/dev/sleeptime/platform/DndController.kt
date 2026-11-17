package dev.sleeptime.platform
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sleeptime.MainActivity
import dev.sleeptime.R
import javax.inject.Inject
import javax.inject.Singleton

/** Only controls this application's named automatic rule. Other DND rules are never disabled. */
@Singleton class DndController @Inject constructor(@param:ApplicationContext private val context: Context) {
 private val manager get() = context.getSystemService(NotificationManager::class.java)
 private val saved = context.getSharedPreferences("dnd_rule",Context.MODE_PRIVATE)
 fun supported() = Build.VERSION.SDK_INT >= 29
 fun allowed() = manager.isNotificationPolicyAccessGranted
 fun setActive(active: Boolean) {
  if (!supported() || !allowed()) return
  runCatching {
   var id = saved.getString("id",null)
   var rule = id?.let { manager.getAutomaticZenRule(it) }
   if (!active && id == null) return
   if (id != null && rule == null) { if (!active) return; saved.edit().putBoolean("removed",true).apply(); return }
   if (saved.getBoolean("removed",false)) return // deletion by the user wins
   if (id == null) {
    val uri = Uri.parse("condition://${context.packageName}/bedtime")
    rule = AutomaticZenRule(context.getString(R.string.bedtime_mode),null,ComponentName(context,MainActivity::class.java),uri,null,NotificationManager.INTERRUPTION_FILTER_PRIORITY,true)
    id = manager.addAutomaticZenRule(rule)
    saved.edit().putString("id",id).apply()
   }
   if (rule?.isEnabled == false) return // a user-disabled rule stays disabled
   val conditionUri = Uri.parse("condition://${context.packageName}/bedtime")
   manager.setAutomaticZenRuleState(id!!,Condition(conditionUri,context.getString(R.string.bedtime_mode),if(active) Condition.STATE_TRUE else Condition.STATE_FALSE))
  }
 }
 fun enableNewRule() { saved.edit().putBoolean("removed",false).apply() }
}
