"""Local aggregate-only audit of existing research exports; never prints SMS values.
No network access. Parser accept IDs come from the supplied research verdict file.
"""
import collections
import datetime as dt
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1] / "clauds work"
MONTHS = {m: i for i, m in enumerate(
    ("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"), 1)}
DATE_PATTERNS = [
    ("compact ddMonyy", r"\b(\d{1,2})(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(\d{2}|\d{4})\b"),
    ("separated day-month-name-year", r"\b(\d{1,2})[- /](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- /](\d{2}|\d{4})\b"),
    ("numeric day/month/year", r"\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})\b"),
    ("ISO year-month-day", r"\b(\d{4})-(\d{2})-(\d{2})\b"),
]
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))

def study(folder):
    rows = json.loads((folder / "raw/sms.json").read_text(encoding="utf-8"))
    verdicts = {}
    for line in (folder / "output/parser_verdicts.tsv").read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) > 1 and parts[1] != "NULL":
            verdicts[parts[0]] = parts[1:]
    counts = collections.Counter()
    dates = collections.Counter()
    alignment = collections.Counter()
    fields = collections.Counter()
    for row in rows:
        if row["type"] != 1:
            continue
        counts["received"] += 1
        parsed = verdicts.get(str(row["_id"]))
        if not parsed:
            continue
        counts["accepted_by_research_v4"] += 1
        body = row["body"]
        date = None
        for label, rx in DATE_PATTERNS:
            match = re.search(rx, body, re.I)
            if not match:
                continue
            dates[label] += 1
            a, b, c = match.groups()
            try:
                if label.startswith("ISO"):
                    year, month, day = int(a), int(b), int(c)
                else:
                    day, month, year = int(a), MONTHS[b.lower()[:3]] if b.isalpha() else int(b), int(c)
                    if year < 100:
                        year += 2000
                date = dt.date(year, month, day)
            except (ValueError, KeyError):
                counts["date_like_but_invalid"] += 1
            break
        clock = re.search(r"(?<![\d.])(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?!\d)", body)
        counts["body_date_present"] += date is not None
        counts["body_clock_present"] += clock is not None
        counts["body_date_and_clock"] += date is not None and clock is not None
        counts["body_explicit_timezone"] += bool(re.search(r"\b(?:IST|UTC|GMT)\b|[+]05:30", body, re.I))
        if date:
            arrival = dt.datetime.fromtimestamp(int(row["date"]) / 1000, IST).date()
            delta = (arrival - date).days
            alignment["same_calendar_day_IST" if delta == 0 else "arrival_1_day_after" if delta == 1 else "arrival_before_body_date" if delta < 0 else "arrival_2plus_days_after"] += 1
        for label, rx in {
            "account_wording": r"\b(?:a\s*/\s*c|account|acct|acc|ac)\b",
            "balance_wording": r"\b(?:bal|balance)\b",
            "card_wording_including_ads": r"\bcard\b",
            "reference_extracted": None,
            "counterparty_extracted": None,
            "url": r"https?://|www\.",
        }.items():
            present = len(parsed) > 4 and bool(parsed[4]) if label == "reference_extracted" else len(parsed) > 2 and parsed[2] != "Unknown merchant" if label == "counterparty_extracted" else bool(re.search(rx, body, re.I))
            fields[label] += present
    return {"dataset": folder.name, "counts": dict(counts), "date_formats": dict(dates), "body_date_vs_sms_arrival_not_event_validation": dict(alignment), "field_presence": dict(fields)}

if __name__ == "__main__":
    folders = [ROOT] + sorted(p for p in ROOT.iterdir() if p.is_dir() and p.name.startswith("device"))
    for folder in folders:
        if (folder / "output/parser_verdicts.tsv").exists():
            print(json.dumps(study(folder), ensure_ascii=True))
