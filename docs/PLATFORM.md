# Android behavior and limitations

- The manifest uses `SCHEDULE_EXACT_ALARM`, not the restricted automatically granted `USE_EXACT_ALARM`. Precise access is optional and requested from an explicit settings action. Without it, `setAndAllowWhileIdle` provides an inexact fallback.
- Even exact alarms are subject to Android/OEM constraints. Doze throttles frequent alarms. Force-stopped apps cannot recover until launched again. Notification/channel permissions can prevent visible notifications. The app does not promise guaranteed delivery.
- Reminder stages and overdue repeats are bounded. Smart activity checks only suppress overdue reminders; unavailable access/data falls back to normal scheduling. A user can retain a minimum at-bedtime stage. There is no inference that inactivity equals sleep.
- Usage access is checked through AppOps. Recent events are scanned only when a reminder fires; no usage database is stored or uploaded. Distraction awareness is sampled at these moments. It does not intercept app launches, hard-block other apps, use overlays, Accessibility services, or device administration.
- DND uses a named app-owned automatic rule on Android 10+. Turning off its contribution does not disable independent DND rules. User-disabled/deleted rules stay disabled; toggling the feature off/on permits a new rule. Older Android versions expose settings guidance rather than changing global interruption state. The operating system combines rules and decides final policy. Manual system-wide changes and OEM-specific DND behavior require device verification.
- Snooze changes the current session only. It cannot exceed its count limit or extend beyond the planned wake boundary. Confirmation stops that session’s reminders while retaining future nights. A Room primary key rejects duplicate confirmation from repeated notification/tile clicks.
- Widgets are snapshots refreshed by Android, explicit edits/actions, and periodic maintenance. They never run a second-by-second background loop; their update timestamp is displayed. The foreground dashboard alone has a per-second countdown.
- Backup/import uses Storage Access Framework. No broad storage permission is requested. Imported preferences do not grant notification, usage, exact-alarm, or DND access.
- Alarm reconstruction is resilient to ordinary app closure, reboot after user unlock, time changes, timezone changes, and schedule edits. OS force-stop and vendor task-killers remain outside the app’s control.

Primary Android references:
https://developer.android.com/develop/background-work/services/alarms
https://developer.android.com/about/versions/15/behavior-changes-15#dnd-changes
https://developer.android.com/reference/android/app/NotificationManager
