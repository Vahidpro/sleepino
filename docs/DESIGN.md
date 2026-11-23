# Visual system

The home page is a single, non-scrolling bedtime card. It shows the date, a bold hour-and-minute countdown, and a small work or rest badge. A larger moon and more stars fill the night sky. The sky follows the local hour, and the card turns red at bedtime. Sleep and snooze sit side by side from 30 minutes before bedtime. After reporting bedtime, undo appears as an icon with an accessible label.

Swipe horizontally for weekly budgets, adherence, history, and routine. The selected dot slides with the page gesture. The report week selector accepts a date and shows its week range. Swipe down from the top to open Settings. The pager exposes the same action to assistive technology. Collapsed settings groups show their most-used controls and eight color presets. The circular sleep schedule picker can apply bedtime and wake time to several weekdays.

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
