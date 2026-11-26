package pasac.sleepino.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Borkowski's break-year algorithm. jalaali-js (MIT) is credited in NOTICE. */
object PersianDates {
 data class Date(val year: Int, val month: Int, val day: Int)
 private val breaks = intArrayOf(-61,9,38,199,426,686,756,818,1111,1181,1210,1635,2060,2097,2192,2262,2324,2394,2456,3178)
 fun newYear(year: Int): LocalDate {
  require(year in -61..3177)
  val gy = year + 621; var leapJ = -14; var jp = breaks[0]; var jump = 0
  for (i in 1 until breaks.size) {
   val jm = breaks[i]; jump = jm - jp
   if (year < jm) break
   leapJ += jump / 33 * 8 + jump % 33 / 4; jp = jm
  }
  val n = year - jp
  leapJ += n / 33 * 8 + (n % 33 + 3) / 4
  if (jump % 33 == 4 && jump - n == 4) leapJ++
  val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
  return LocalDate.of(gy, 3, 20 + leapJ - leapG)
 }
 fun fromGregorian(date: LocalDate): Date {
  var year = date.year - 621
  if (date < newYear(year)) year--
  val offset = ChronoUnit.DAYS.between(newYear(year), date).toInt()
  val month = if (offset < 186) offset / 31 + 1 else (offset - 186) / 30 + 7
  val day = if (offset < 186) offset % 31 + 1 else (offset - 186) % 30 + 1
  return Date(year, month, day)
 }
 fun toGregorian(year: Int, month: Int, day: Int): LocalDate {
  require(month in 1..12)
  val count = if (month <= 6) 31 else if (month <= 11) 30 else ChronoUnit.DAYS.between(newYear(year), newYear(year + 1)).toInt() - 336
  require(day in 1..count)
  return newYear(year).plusDays((if (month <= 6) (month - 1) * 31 else 186 + (month - 7) * 30).toLong() + day - 1)
 }
}
