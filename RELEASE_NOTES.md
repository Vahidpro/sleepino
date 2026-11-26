# Sleepino 1.0.0 Release Notes

We are thrilled to announce **Sleepino 1.0.0** — the first official release of the native, offline Android bedtime and personal time-budget assistant built with Kotlin and Jetpack Compose.

---

## 🌟 What's New in Version 1.0.0

### 🌙 Celestial Bedtime Dashboard
- **Dynamic Sky Countdown**: A full-page celestial bedtime countdown featuring daytime skies, starry nights, and overdue alerts.
- **Sleep & Snooze Controls**: Convenient actions available during the last 30 minutes before bed and throughout overdue periods.
- **Fluid Motion**: Hand-crafted celestial transitions (sun, moon, twinkling stars, drifting clouds, and birds) with reduced-motion support.
- **Intuitive Gestures**: Swipe horizontally to navigate weekly reports and swipe down from the top to access Settings.

### ⏰ Flexible Schedule Management
- **Independent Schedules**: Dedicated weekly planners for sleep, work, and exercise.
- **Circular Time Picker**: Quick setup for standard bedtimes and wake times, with full support for day-by-day custom adjustments.
- **Overnight & Shift Support**: Seamless handling of overnight shifts, mid-shift breaks, custom target sleep durations, and date overrides.

### 🔔 Smart Reminders & Sleep Protection
- **Multi-Stage Reminders**: Bounded overdue notifications, customizable advance alerts, and cancellable snooze.
- **Reliable Scheduling**: `AlarmManager` with exact-alarm access, automatic rescheduling across device reboots and timezone shifts, plus a `WorkManager` background recovery sweep.
- **App-Owned Do Not Disturb**: Automated DND rule on Android 10+ that respects user preferences and existing system rules.
- **Wellbeing Awareness**: Optional distracting-app alerts near bedtime with direct shortcuts to Android Digital Wellbeing settings.
- **Duplicate-Safe Confirmation**: One-tap bedtime confirmation with an instant undo action.

### 🌍 Localization & Calendar System
- **Bilingual Experience**: Complete English and Persian (فارسی) support with native LTR and RTL layouts.
- **Persian Typography & Digits**: Beautiful Estedad typography paired with automatic Persian digits.
- **Jalali & Gregorian Dates**: Built-in Solar Hijri (Jalali) and Gregorian calendars, with 12-hour and 24-hour clock formats.
- **Accurate Holiday Engine**: Astronomical Iranian lunar calendar mappings and bundled recurrence holiday rules.

### 🎨 Personalization & Theming
- **Curated Color Palettes**: 8 distinctive color presets.
- **Display Modes**: Light, Dark, true AMOLED black, and dynamic Material You system theming.
- **Accessibility**: First-class reduced motion and high-contrast support.

### 📱 Android System Integrations
- **Home Screen Widgets**: Jetpack Glance widgets for quick bedtime countdowns and free-time budget tracking.
- **Quick Settings Tile**: One-tap sleep confirmation tile directly in your Android notification shade.

### 🔒 100% Private & Offline-First
- **Zero Tracking**: No account required, no ads, no analytics, and no external servers.
- **Local Storage**: All data stays on your device using encrypted Room database storage and DataStore preferences.
- **Data Portability**: Full JSON backup and restore via Android's system document picker.

---

### 📦 Installation
Download `sleepino-v1.0.0.apk` attached below and install it directly on any device running Android 8.0 (API 26) or higher.
