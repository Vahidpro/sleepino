# Visual system

The home page is a single, non-scrolling bedtime card. Quiet, always-visible corner icons open Reports and Settings on opposite sides of the date header, keeping the sky canvas and bottom area uncluttered. It shows the date, a bold hour-and-minute countdown, and a small work or rest badge. A larger moon and more stars fill the night sky. The sky follows the local hour, and the card turns red at bedtime. Sleep and snooze sit side by side from 30 minutes before bedtime. After reporting bedtime, undo appears as an icon with an accessible label.

Swipe horizontally (or tap the Reports / Back icons) for sleep consistency, weekly time balance, and the bedtime log. The report week switcher accepts a date with a Jalali-aware picker and shows its week range. Settings uses scannable grouped panels with live value summaries and focused bottom sheets for reminders, bedtime Do Not Disturb, late-night app reminders, timezone search, and schedule editors. The sleep and activity editors apply bedtime, wake time, or duration presets to several weekdays inline before saving.

Persian uses bundled Estedad with automatic Persian digits. English uses the Android system sans serif. Layout direction follows app language. Calendar, timezone, and holiday region remain independent preferences. Controls wrap or stack at narrow widths and larger font sizes.

Compose previews are in `ui/Previews.kt`. Sample preview plans are isolated there. The application never installs preview data into user storage.

## Performance

Keep startup and touch response fast. Leave schedule and historical report calculations off the UI thread. Reuse the schedule and hour/minute clock cadence when adding time-driven UI. Cache decorative paths and brushes and keep them independent of clock ticks. Check release builds with R8 enabled when measuring startup or scroll performance.

## Motion and action feedback

Every new or changed user action needs immediate visual feedback and a clear result. Use Material press feedback on tappable controls and a short transition when the action changes visible state. Show confirmation, failure, and selection through text, an icon, or control state so the result remains clear when motion is off.

- For dropdowns and collapsible groups, animate opening and closing from the trigger and update its chevron or expanded state. Keep the current selection visible.
- For navigation, dialogs, and sheets, use Compose or Material transitions. Save or apply the action once and use animation to reflect the outcome. Keep outgoing controls from accepting stale input.
- For Dashboard and Statistics updates, fade changed statuses and animate layout or progress where it helps users follow the change. Keep timers and other frequent updates steady. Avoid replaying page animations during refreshes or recomposition.
- Reuse `motionSpec`, `sectionEnter`, `sectionExit`, and `pressScale` from `ui/Design.kt`. Keep most custom transitions around 150–220 ms, with a quicker response for pressing a button. Use `LocalReducedMotion` so custom transitions snap or skip when the app’s Reduce motion setting is on or Android animations are disabled. Material controls keep their platform feedback and follow Android’s animation settings.
- Check new motion in both directions, on repeated taps, with Reduce motion enabled, and with the Android animation scale set to off. Verify that focus, touch targets, and readable status feedback are available throughout.
