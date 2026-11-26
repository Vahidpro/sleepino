# sleepino

A native, offline Android bedtime and personal time-budget assistant. Kotlin + Jetpack Compose; Persian and English. The visual direction is calm, spacious, and dashboard-first: a large celestial bedtime card, gentle violet/teal accents, clear time summaries, and focused bottom-sheet editors. No account, ads, analytics, or backend.

## Open and run

1. Install a current stable Android Studio with **JDK 21**, Android SDK **36**, and Build Tools **36.0.0**.
2. Open this repository as the Gradle project and allow dependency sync.
3. Run the `app` configuration on Android **8.0 / API 26 or newer**.

From a terminal:

```bash
./gradlew :core:test :app:lintDebug
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. No signing secrets are needed. For an unsigned release use `./gradlew :app:assembleRelease`; configure your own signing for distribution. Branding lives in `app/src/main/res/values*/strings.xml`, the launcher resources, and `ui/Design.kt`.

The Actions workflow runs manually, for pull requests, and for version tags. A normal source push does not automatically build an APK. Start **Android verification and build → Run workflow** whenever you want an artifact.

## Features

- A full-page bedtime countdown with a daytime sky, a starry night, and a red overdue state. Sleep and snooze actions appear during the last half hour and remain available after bedtime. Swipe horizontally for reports and select the week to show. Swipe down from the top for Settings.
- Independent weekly sleep, work, and exercise schedules. Set a single bedtime and wake time for selected weekdays with the circular picker, or edit days individually. Overnight intervals, work breaks, target sleep duration, and date overrides are supported.
- Custom reminder stages, bounded overdue reminders, cancellable snooze, duplicate-safe reported-bedtime confirmation, and icon-only undo.
- AlarmManager scheduling with optional exact-alarm access, boot/timezone/clock rescheduling, and a WorkManager recovery sweep.
- Optional activity-aware overdue suppression with scheduled fallback. Optional distracting-app awareness at reminder times, plus a shortcut to Android wellbeing settings.
- An app-owned DND rule on Android 10+, preserving other rules. User-disabled or deleted rules are respected.
- Room bedtime/routine history, DataStore configuration, JSON backup/restore through Android’s file picker, and a locally imported holiday update.
- English and Persian with RTL/LTR layouts, Estedad and system sans serif, automatic Persian digits in Persian, Jalali dates, 12/24-hour clocks, eight color presets, light/dark/AMOLED modes, dynamic colors, and reduced motion.
- Bedtime and free-time home widgets, plus a Quick Settings sleep-confirmation tile.

No production history or personal schedule is prefilled. Onboarding can be completed with sections unconfigured; schedules are saved only through explicit editing.

## Holiday data

Holiday rules come **only** from [`persian-calendar/events`](https://github.com/persian-calendar/events), commit **503bb203ac50a0ca79f36840ef74e1eee7eabb1d**, filtering exactly `type == "Iran" && holiday == true`. The bundled adapter has **28 rules**, supports simple and end-of-month recurrence, honors beginning/end Persian-year metadata, and removes all names/descriptions. No GPL app or calendar implementation is copied.

Lunar dates follow Iranian mappings, not the Saudi `HijrahDate` calendar. Published **1405** date-conversion anchors were transcribed from the University of Tehran Calendar Center’s 1405 calendar; older mapping data comes from the CC0 `roozbehp/qamari` calendar-center dataset. The published-year coverage is **2026-03-21 through 2027-03-20**, inclusive. Published future lunar dates remain subject to official revision; they are not claims of confirmed future moon sightings.

Outside validated coverage, status is **unknown**, and the dashboard explicitly labels the budget provisional. Unknown days retain planned work rather than inventing holidays or asserting they are ordinary workdays. See [holiday provenance and updates](docs/HOLIDAYS.md).

## Performance

Responsive startup, swipes, and settings controls are product requirements. The home view refreshes at minute boundaries and at snooze expiration. It reuses daily and weekly schedule calculations between clock updates, and the reports page loads other weeks off the UI thread. Settings loads installed apps when their picker opens. Judge startup and gesture performance on a release build with R8 enabled.

## Verification

The JVM time engine has automated tests for overlapping reservations, overnight work, sleep ownership, holidays/overrides, every week start, remaining budgets, 23/25-hour days, DST gaps/overlaps, date conversion, validity limits, and reminder transitions. Android compilation and lint should be run with the commands above. Device/emulator permission, OEM notification, DND, widget, and accessibility checks are listed in [the device checklist](docs/DEVICE_TESTING.md); no device certification is implied by source-level checks.

See [visual design and motion](docs/DESIGN.md), [architecture](docs/ARCHITECTURE.md), [platform behavior](docs/PLATFORM.md), [privacy](PRIVACY.md), and [third-party acknowledgments](NOTICE.md).

The application source is private and has no open-source license grant. Third-party files retain their individual licenses.
