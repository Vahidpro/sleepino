# Device verification checklist

Source tests cannot establish OEM alarm reliability or visual accessibility. Before distribution, run on physical Android 8/10/13/15+ devices and a recent emulator.

- Fresh install: skip onboarding sections; verify no fabricated plans/history. Enter sleep/work/exercise and edit copied/multiple/overnight intervals.
- English and Persian app languages on the opposite system language. Check RTL icons, mixed text, Persian/Latin numerals, Jalali/Gregorian, and 12/24-hour display.
- Check all eight palettes in light, dark, and AMOLED, plus dynamic colors. Cover 320dp screens, landscape, tablets, 200% fonts, TalkBack, reduced motion, and switch/checkbox touch targets.
- Swipe horizontally in both directions between Home and Reports and check that the active dot follows the gesture. Home fits a single screen and does not scroll. Swipe down from the top to open Settings.
- Verify the report title in Persian, choose a past week in the calendar, and check the date range and report records. Test the configured week start.
- Check the work and rest badge on a workday and a holiday. Sleep and snooze appear side by side at 30 minutes before bedtime, with cancellation available while snoozed.
- Verify the circular time picker and keyboard entry in English and Persian. Apply shared times to several days, edit one day individually, and check the untouched days.
- Set key preferences with Settings groups collapsed. Open and close groups and dropdowns, navigate between weeks, and save or dismiss an editor. Try confirmation, icon-only undo, snooze cancellation, and routine actions with Reduce motion and Android animations turned off.
- Bedtime before midnight, after midnight, disabled days, early confirmation, duplicate notification/tile taps, undo, custom snooze, and maximum counts.
- Deny/grant/revoke notification and exact-alarm access; inspect notification channel sound/vibration changes and custom SAF audio.
- Lock screen/Doze/reboot/clock/timezone changes, app dismissal, battery restrictions, and schedule changes with a pending alarm. Force-stop and relaunch to confirm documented recovery.
- Smart reminders with no usage access, no activity events, active/inactive device, and selected app in foreground at reminder time. At-bedtime minimum should remain scheduled.
- DND: existing independent rule, user exceptions, disable/delete the app’s rule, manual system changes, early sleep, snooze behavior, and end boundary.
- Holiday workday/nonworkday, holiday work override, unknown future coverage, valid/invalid holiday update, and time-budget totals for overlapping reservations.
- Routine completion and nightly ownership/reset, reorder/delete, process recreation, history, and weekly navigation.
- Export/import valid and corrupt backups, import canceled file selection, system permissions after restore, full reset, and process interruption during restore.
- Resize widgets, confirm/update timestamps, switch themes/languages, and use the Quick Settings tile while locked/unlocked.

Run instrumentation with `./gradlew :app:connectedDebugAndroidTest`. The tests cover the bedtime card, Persian digits, shared schedules, time input, appearance presets, and pager gestures. Use this checklist for system behavior.
