package dev.sleeptime.core
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class ReminderTest {
 private val c=Configuration(prefs=Preferences(zoneId="UTC"),sleep=listOf(SleepRule(1)))
 private val o=TimeEngine.sleep(c,LocalDate.of(2026,11,16))!!
 @Test fun beforeAndAtBedtime(){val events=ReminderEngine.events(c,o,null);assertEquals(o.start.minusSeconds(1800),events.first().at);assertTrue(events.any{it.at==o.start})}
 @Test fun overdueRepeatsAreBounded(){val events=ReminderEngine.events(c,o,null);assertEquals(4,events.count{it.key.startsWith("repeat:")});assertTrue(events.all{it.at<o.end})}
 @Test fun completedSessionHasNoReminders(){assertTrue(ReminderEngine.events(c,o,ActiveSession(o.key,o.start.toEpochMilli(),completed=true)).isEmpty())}
 @Test fun snoozeDoesNotChangeRecurringBedtime(){val now=o.start;val session=ReminderEngine.snooze(c,ActiveSession(o.key,o.start.toEpochMilli()),now,10)!!;val events=ReminderEngine.events(c,o,session);assertEquals(o.start.plusSeconds(600),events.first{it.key.startsWith("snooze")}.at);assertEquals(1380,c.sleep.single().bedtime)}
 @Test fun cancellingSnoozeRestoresPendingRemindersWithoutReplayingDeliveredOnes() {
  val current=ActiveSession(o.key,o.start.toEpochMilli(),emitted=setOf("stage:1"))
  val snoozed=ReminderEngine.snooze(c,current,o.start.minusSeconds(1800),20)!!
  val restored=ReminderEngine.cancelSnooze(snoozed,o.start.minusSeconds(900))!!
  assertNull(restored.snoozedUntil)
  assertEquals(1,restored.snoozes)
  val keys=ReminderEngine.events(c,o,restored).map { it.key }
  assertTrue("stage:2" in keys)
  assertFalse("stage:1" in keys)
  assertFalse(keys.any { it.startsWith("snooze:") })
  assertNull(ReminderEngine.cancelSnooze(snoozed,o.start))
 }
 @Test fun snoozeLimitEnforced(){assertNull(ReminderEngine.snooze(c,ActiveSession(o.key,o.start.toEpochMilli(),snoozes=3),o.start,10))}
 @Test fun alreadyDeliveredEventsAreNotRepeated(){val events=ReminderEngine.events(c,o,ActiveSession(o.key,o.start.toEpochMilli(),emitted=setOf("stage:1")));assertFalse(events.any{it.key=="stage:1"})}
 @Test fun deniedActivityAccessFallsBackToScheduled(){val event=ReminderEngine.events(c,o,null).last();assertTrue(ReminderEngine.shouldEmit(event,true,null,event.at,20))}
 @Test fun inactiveDeviceSuppressesOnlyOverdue(){val events=ReminderEngine.events(c,o,null);assertFalse(ReminderEngine.shouldEmit(events.last(),true,o.start.minusSeconds(3600),o.start.plusSeconds(3600),20));assertTrue(ReminderEngine.shouldEmit(events.first(),true,o.start.minusSeconds(3600),o.start,20))}
 @Test fun activeDeviceKeepsOverdueReminder(){val e=ReminderEngine.events(c,o,null).last();assertTrue(ReminderEngine.shouldEmit(e,true,e.at.minusSeconds(20),e.at,20))}
 @Test fun scheduleChangeResolvesNewEventInstants(){val changed=c.copy(sleep=listOf(SleepRule(1,bedtime=1320)));val next=TimeEngine.sleep(changed,o.owner)!!;assertNotEquals(ReminderEngine.events(c,o,null).first().at,ReminderEngine.events(changed,next,null).first().at)}

 @Test fun minimumScheduledReminderSurvivesEmptyStageList() {val cfg=c.copy(reminders=emptyList());assertTrue(ReminderEngine.events(cfg,o,null).any{it.at==o.start && !it.overdue})}
 @Test fun minimumScheduledReminderCanBeDisabled() {val cfg=c.copy(reminders=emptyList(),prefs=c.prefs.copy(minimumScheduledReminder=false,maxRepeats=0));assertTrue(ReminderEngine.events(cfg,o,null).isEmpty())}
}
