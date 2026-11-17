# Architecture and time semantics

`:core` is a pure Kotlin/JVM module with immutable serializable models, calendar/date conversion, holiday providers, schedule resolvers, interval algebra, budget calculation, and reminder state transitions. It depends on no Android API. `Clock` is injected at the platform boundary; calculation methods accept an explicit instant.

`:app` contains feature-focused Compose presentation, Hilt wiring, DataStore configuration, Room history, and Android integrations. `AppViewModel` exposes immutable `StateFlow` UI state and explicit actions. Countdowns derive from `Instant` differences; collection is lifecycle-aware. Budget geometry is cached per minute/configuration, while the visible timer updates each second. No service is kept alive for countdown digits.

## Interval rules

- Recurring times are local wall-clock times in either the current system zone or an explicitly selected IANA zone. Confirmation records store absolute epoch milliseconds and the planned timestamp snapshot.
- A sleep day names its **evening**. Bedtimes before 12:00 resolve on the following calendar day: Thursday 00:30 means Friday morning. A wake time after bedtime is on that physical date; otherwise it resolves the next date. Target-duration mode reserves an absolute duration instead.
- Overnight work/exercise intervals start on their configured weekday and end the following date when the end clock time is earlier. Work breaks remove only their overlapping work portions. A break can still be occupied by exercise or sleep.
- Work is clipped into actual calendar-day slices. A date override replaces that day’s work, including recurring carry-in. Otherwise a recognized holiday cancels that day’s work slices. Otherwise recurring schedules apply. Explicit work on a holiday reinstates work; an empty explicit list is leave. Sleep and exercise remain independent.
- Sleep/exercise overrides take priority over recurring schedules. Removing an override restores recurring behavior.
- Date boundaries use `atStartOfDay(zone)`. DST gaps shift forward by their gap; overlaps use the earlier offset, consistently with `java.time`. A sleep window collapsed by a gap is clamped to zero duration, never negative. Days/weeks are never assumed to contain exactly 24/168 hours.
- Reserved spans are clipped, merged, and unioned before subtraction. Remaining free time intersects free spans with `[now, end)`. Past weeks have zero remaining time; future weeks have all their free time remaining.
- The stacked visualization partitions overlaps in this order: sleep → work → exercise → free. This makes its segment sum equal the actual window length. Workday counts use scheduled work independently of chart precedence.

## Persistence and integration

DataStore holds configuration and the durable active reminder session; Room holds reported bedtime and routine-completion history. Initial database schema is v1 and is checked in. Future upgrades must add explicit migrations rather than destructive fallback.

The scheduler maintains one next Android alarm and recalculates after each event or configuration change. Alarm payloads carry a configuration fingerprint; stale events are rejected. Boot, package update, clock, timezone, and exact-access broadcasts trigger reconstruction. A 12-hour WorkManager sweep is recovery, not the primary timer.

Reported bedtime is not actual sleep. On-time means within ±15 minutes. History stores its original planned timestamp. For unreported nights, adherence uses the currently configured weekly schedule; changing that schedule can change the denominator for those nights. No sleep quality/duration is inferred from confirmations or device inactivity.
