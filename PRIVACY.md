# Privacy

sleepino has no account system, analytics SDK, ads, trackers, backend, or Internet permission. Schedules, routine completions, reported bedtimes, preferences, and reminder state stay in app-private local storage. Android automatic cloud backup is disabled; explicit JSON export writes only to the user-selected document destination, which may itself be a cloud provider chosen by the user.

Device usage access is optional. The application scans recent Android usage events at scheduled reminder time to find the latest interaction/foreground package. It does not retain event histories or upload activity. Inactivity is not treated as verified sleep. App selection only enumerates launchable applications and does not request broad `QUERY_ALL_PACKAGES` access.

Notification, exact-alarm, DND, usage, and custom audio document access are requested only through user-facing controls. Each capability degrades without permission. Imported settings cannot grant system access.

Backups contain personal schedules and reported history in unencrypted JSON; choose their destination accordingly. A full reset removes local schedules and history. Uninstalling removes app-private data; exported files remain under the user’s control.

The application is a planning assistant and records reported bedtime. It does not measure medical sleep data, actual sleep duration, or sleep quality.
