# Visual system

The dashboard is the product’s center, with no bottom navigation. The bedtime hero uses a quiet navy/violet gradient, subtle concentric celestial details, large fixed-width numerals, and one clear sleep action. Work and free-time summaries follow in adaptive cards. The weekly section uses a union-correct segmented bar and tappable daily bars; the routine stays compact and collapsible.

Surfaces use 28dp corners, the hero 36dp, generous 20–26dp content padding, and accessible minimum-height controls. Violet denotes sleep, blue work, warm sand exercise, and teal free time. Light, dark, and true-black share the same hierarchy; wallpaper colors are optional.

Persian uses bundled Estedad and English uses Android’s modern system sans serif. Layout direction follows app language, while countdown digits retain LTR order. Numerals, calendar, timezone, and holiday region are independent preferences. Core controls wrap/stack on small screens; timers fit their available width while retaining accessible semantics.

Compose previews are in `ui/Previews.kt`. Sample preview plans are isolated there; the application never installs preview data into user storage. Reduced motion disables decorative/structural transitions, and platform animation settings are respected.
