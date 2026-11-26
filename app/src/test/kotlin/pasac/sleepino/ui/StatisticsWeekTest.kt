package pasac.sleepino.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsWeekTest {
 @Test fun utcPickerDateHonorsConfiguredFirstDayOfWeek() {
  val selection=Instant.parse("2027-01-02T00:00:00Z")
  assertEquals(LocalDate.of(2027,1,1),selection.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate())

  assertEquals(LocalDate.of(2027,1,2),weekStartFromCalendarSelection(selection.toEpochMilli(),DayOfWeek.SATURDAY.value))
  assertEquals(LocalDate.of(2026,12,28),weekStartFromCalendarSelection(selection.toEpochMilli(),DayOfWeek.MONDAY.value))
 }
}
