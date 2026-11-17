# Holiday provenance and updates

## Inputs

- Holiday definitions: https://github.com/persian-calendar/events, commit `503bb203ac50a0ca79f36840ef74e1eee7eabb1d`, `events.json`, **CC0 1.0**. The source metadata points to https://calendar.ut.ac.ir/documents/2139738/7092644/Calendar-1405.pdf.
- Historical Iranian lunar month starts: `roozbehp/qamari`, exact commit recorded in `third_party/qamari-commit.json`, calendar-center source, **CC0 1.0**.
- 1405 published lunar conversion anchors: factual transcription of the University of Tehran Calendar Center’s final 1405 calendar. The official URL was identified by the CC0 event metadata and the institute’s announcement (https://t.me/s/PRGeophysics?before=5052). The official host returned an HTML access page in the development environment; the same published primary document’s publicly visible transcription was cross-checked at https://www.scribd.com/document/990789768/Calendar-1405. Only date correspondences were transcribed, not event text, names, holiday definitions, or PDF contents.

The 1405 CSV contains 13 month-start anchors. Its final interval ends at the end of published coverage and has `complete=false`; this is **not** an invented month ending. End-of-month recurrence is evaluated only for complete months. The selected end-of-month holiday is in lunar month 2, whose full length is present.

`third_party/iran-lunar-1405.csv` is factual date data, made available under CC0. Published future dates can be revised after observations or official changes. The app does not extrapolate into an unvalidated Persian year or use a fixed Gregorian recurrence for lunar dates.

## Supported schema

The actual upstream root has `Source`, `#meta`, and `data`. The adapter selects only `Iran` + boolean `true`. The current selected records have Persian/Hijri calendars; the provider also handles Gregorian records. `simple` and `end of month` are supported. `beginningPersianYear` and `endPersianYear` control validity. Unsupported selected calendars, recurrence rules, or new metadata fail the transformation for review. Nonselected event categories never become rules. Holiday titles and descriptions are stripped before bundling.

## Update process

1. Fetch `events.json` at an exact reviewed upstream commit. Verify the CC0 license is unchanged.
2. Review the relevant official published calendar. Add validated month correspondences to a CSV with `year,month,start,end,complete`. Never infer the last month’s length; use a partial coverage boundary when necessary.
3. Generate a candidate:

```bash
python3 tools/update_holidays.py   --events third_party/events.json   --lunar third_party/iran-lunar-1405.csv   --year 1405   --revision 503bb203ac50a0ca79f36840ef74e1eee7eabb1d   --output candidate-holidays.json
```

4. Compare counts, metadata, date anchors, historical validity, end-of-month rules, and known official dates. Run `:core:test`; add new golden assertions before advancing coverage.
5. Ship the new bundled asset in a release, or import the validated nameless JSON through **Settings → Your data → Import validated holiday update**. Runtime validation checks schema, category, calendar/rule support, bounds, month lengths, and overlap before atomic cache replacement. Invalid cached data falls back to the bundled copy. The app makes no network request and needs no daily connection.

US rules are a separate implementation of the federal recurring/observed schedule described by OPM (https://www.opm.gov/policy-data-oversight/pay-leave/federal-holidays/) and 5 USC 6103. They include Friday/Monday observation and cross-year New Year’s observation. They do not include state holidays, ad-hoc executive closures, or the Washington-area Inauguration Day exception. Selecting no region disables automatic holiday cancellation.
