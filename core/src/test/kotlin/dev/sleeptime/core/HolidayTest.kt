package dev.sleeptime.core
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import java.time.LocalDate
class HolidayTest {
 private fun bundle()=Json.decodeFromString<HolidayBundle>(javaClass.getResource("/iran_holidays.json")!!.readText())
 @Test fun onlyOfficialIranHolidayRulesAreBundled(){val b=bundle().validate();assertEquals(28,b.rules.size);assertTrue(b.rules.all{it.type=="Iran"&&it.holiday})}
 @Test(expected=IllegalArgumentException::class) fun otherRegionsAreRejected(){val b=bundle();b.copy(rules=b.rules.mapIndexed{i,r->if(i==0)r.copy(type="IranFormer")else r}).validate()}
 @Test(expected=IllegalArgumentException::class) fun nonHolidaysAreRejected(){val b=bundle();b.copy(rules=b.rules.mapIndexed{i,r->if(i==0)r.copy(holiday=false)else r}).validate()}
 @Test(expected=IllegalArgumentException::class) fun unknownRecurrencesAreRejected(){val b=bundle();b.copy(rules=b.rules.mapIndexed{i,r->if(i==0)r.copy(rule="unrecognized")else r}).validate()}
 @Test fun nowruzConversion(){assertEquals(LocalDate.of(2026,3,21),PersianDates.toGregorian(1405,1,1));assertEquals(PersianDates.Date(1405,1,1),PersianDates.fromGregorian(LocalDate.of(2026,3,21)))}
 @Test fun leapYearConversion(){assertEquals(LocalDate.of(2025,3,20),PersianDates.toGregorian(1403,12,30))}
 @Test fun roundTripsAcrossYears(){for(year in 1390..1410)for(month in 1..12){val date=PersianDates.toGregorian(year,month,15);assertEquals(PersianDates.Date(year,month,15),PersianDates.fromGregorian(date))}}
 @Test fun publishedLunarHolidaysMoveEachYear(){val h=OfflineHolidayProvider(bundle());assertEquals(HolidayState.HOLIDAY,h.status(LocalDate.of(2026,6,24),HolidayRegion.IRAN));assertEquals(HolidayState.HOLIDAY,h.status(LocalDate.of(2026,11,13),HolidayRegion.IRAN))}
 @Test fun lunarEndOfMonthUsesActualLength(){val h=OfflineHolidayProvider(bundle());assertEquals(HolidayState.HOLIDAY,h.status(LocalDate.of(2026,8,13),HolidayRegion.IRAN));assertEquals(HolidayState.WORKDAY,h.status(LocalDate.of(2026,8,14),HolidayRegion.IRAN))}
 @Test fun unknownFutureYearIsNeverCalledAWorkday(){val h=OfflineHolidayProvider(bundle());assertEquals(HolidayState.UNKNOWN,h.status(LocalDate.of(2028,1,1),HolidayRegion.IRAN))}
 @Test fun validityMetadataIsRespected(){val b=bundle();val rules=b.rules.map {if(it.calendar=="Persian"&&it.month==1)it.copy(beginningPersianYear=1500)else it};val h=OfflineHolidayProvider(b.copy(rules=rules));assertEquals(HolidayState.WORKDAY,h.status(LocalDate.of(2026,3,23),HolidayRegion.IRAN))}
 @Test fun noHolidayNamesEnterTheAppBundle(){val text=javaClass.getResource("/iran_holidays.json")!!.readText();assertFalse(text.contains("title"));assertFalse(text.contains("description"))}
 @Test fun full1405PublishedCoverage(){val status=OfflineHolidayProvider(bundle()).getDataStatus(HolidayRegion.IRAN,2026);assertEquals(LocalDate.of(2026,3,21),status.validFrom);assertEquals(LocalDate.of(2027,3,20),status.validTo)}
 @Test fun usObservedNewYearCanFallInPreviousYear(){val h=OfflineHolidayProvider(bundle());assertEquals(HolidayState.HOLIDAY,h.status(LocalDate.of(2021,12,31),HolidayRegion.US));assertEquals(HolidayState.WORKDAY,h.status(LocalDate.of(2022,1,1),HolidayRegion.US))}
 @Test fun noRegionNeverCancelsWork(){assertEquals(HolidayState.WORKDAY,OfflineHolidayProvider(bundle()).status(LocalDate.of(2026,3,21),HolidayRegion.NONE))}
}
