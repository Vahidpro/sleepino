#!/usr/bin/env python3
"""Build a fail-closed, nameless holiday bundle from pinned CC0 upstream data.
Review a newer UT calendar and update the lunar CSV before advancing its year.
This tool does not infer lunar month starts or scrape holiday websites.
"""
import argparse, csv, datetime as dt, hashlib, json, pathlib, re

def build(events_path, lunar_paths, year, revision):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("An exact upstream commit SHA is required")
    source = json.loads(pathlib.Path(events_path).read_text())
    rules = []
    for row in source["data"]:
        if row.get("type") != "Iran" or row.get("holiday") is not True:
            continue
        if row.get("calendar") not in {"Persian", "Gregorian", "Hijri"}:
            raise ValueError(f"Unsupported selected calendar: {row.get('calendar')}")
        if row.get("rule") not in {"simple", "end of month"}:
            raise ValueError(f"Unsupported selected recurrence: {row.get('rule')}")
        meta = row.get("metadata", {})
        known = {"wikipedia", "beginningPersianYear", "beginningPersianYearReference", "endPersianYear", "endPersianYearReference"}
        if set(meta) - known:
            raise ValueError(f"New selected metadata requires adapter review: {set(meta)-known}")
        clean = {k:row[k] for k in ["type","holiday","calendar","rule","month","day"] if k in row}
        clean.update({k:meta[k] for k in ["beginningPersianYear","endPersianYear"] if k in meta})
        rules.append(clean)
    if not 20 <= len(rules) <= 100:
        raise ValueError("Unexpected selected holiday count; review upstream")
    lunar = []
    for path in lunar_paths:
        with open(path) as stream:
            for row in csv.DictReader(stream):
                start, end = dt.date.fromisoformat(row["start"]), dt.date.fromisoformat(row["end"])
                complete = row.get("complete", "true").lower() == "true"
                days = (end-start).days
                if not (days in (29,30) if complete else 1 <= days <= 30):
                    raise ValueError("Invalid lunar month length")
                lunar.append(dict(year=int(row["year"]),month=int(row["month"]),start=str(start),end=str(end),complete=complete))
    lunar.sort(key=lambda row:row["start"])
    for a,b in zip(lunar,lunar[1:]):
        if a["end"] > b["start"]:
            raise ValueError("Duplicate or overlapping lunar months")
        if a["end"] == b["start"] and a["complete"]:
            expected = (a["year"]+1,1) if a["month"]==12 else (a["year"],a["month"]+1)
            if expected != (b["year"],b["month"]):
                raise ValueError("Lunar month order is inconsistent")
    return dict(schema=1,revision=revision,generatedAt=str(dt.date.today()),sourcePersianYear=year,rules=rules,lunar=lunar)

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--events",required=True)
    parser.add_argument("--lunar",required=True,nargs="+")
    parser.add_argument("--year",required=True,type=int)
    parser.add_argument("--revision",required=True)
    parser.add_argument("--output",required=True)
    args=parser.parse_args()
    bundle=build(args.events,args.lunar,args.year,args.revision)
    target=pathlib.Path(args.output)
    encoded=(json.dumps(bundle,ensure_ascii=False,indent=2)+"\n").encode()
    temporary=target.with_suffix(".tmp")
    temporary.write_bytes(encoded);temporary.replace(target)
    print(f"Validated {len(bundle['rules'])} rules, {len(bundle['lunar'])} lunar intervals; SHA-256 {hashlib.sha256(encoded).hexdigest()}")
