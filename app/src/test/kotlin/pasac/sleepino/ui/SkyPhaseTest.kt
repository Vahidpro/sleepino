package pasac.sleepino.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SkyPhaseTest {
 @Test fun timeOfDayResolvesAllSixSolarAndLunarPhases() {
  assertEquals(SkyPhase.NIGHT, resolveSkyPhase(hour = 2, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.DAWN, resolveSkyPhase(hour = 5, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.MORNING, resolveSkyPhase(hour = 8, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.DAY, resolveSkyPhase(hour = 13, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.GOLDEN_HOUR, resolveSkyPhase(hour = 16, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.TWILIGHT, resolveSkyPhase(hour = 18, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
  assertEquals(SkyPhase.NIGHT, resolveSkyPhase(hour = 21, confirmed = false, pastBedtime = false, snoozed = false, windDown = false))
 }

 @Test fun sleepLifecycleStatesTakePriorityOverTimeOfDay() {
  assertEquals(SkyPhase.WIND_DOWN, resolveSkyPhase(hour = 14, confirmed = false, pastBedtime = false, snoozed = false, windDown = true))
  assertEquals(SkyPhase.WIND_DOWN, resolveSkyPhase(hour = 23, confirmed = false, pastBedtime = true, snoozed = true, windDown = false))
  assertEquals(SkyPhase.OVERDUE, resolveSkyPhase(hour = 23, confirmed = false, pastBedtime = true, snoozed = false, windDown = false))
  assertEquals(SkyPhase.RESTING, resolveSkyPhase(hour = 23, confirmed = true, pastBedtime = true, snoozed = false, windDown = true))
 }
}
