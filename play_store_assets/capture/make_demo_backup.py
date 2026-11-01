#!/usr/bin/env python3
"""Makes the demo backup file of the store screenshots (format B5, `composeApp/.../hifz/backup/BackupFormat.kt`).

    python3 make_demo_backup.py [--today EPOCH_DAY] [--tz-offset SECONDS] [--out demo/quran-hifz-demo.json]

The story: a Juz Amma learner (plan from the end, 5 ayahs a day, circle of 7 days) who started 120 days ago, has learned the 20 surahs
114 .. 95 (138 ayahs), skipped some days and two longer breaks, marked a few ayahs as hard, studied yesterday and the day before, and has
NOT done today's lesson (no entry on today's day: the lesson card shows new, recent and old work).

The history is a simulation with a fixed seed that uses the real rules of the ladder (`Ladder.afterReview`: R1, R3, R7, R14, R30, OLD;
HARD stays, FORGOT goes back to R1), so the passages, their due days and the log agree with each other. Dates are epoch days relative to
`--today` (default: today of this computer, in its time zone), so make the file on the day of the capture and give the emulator the same
day. The file has no settings: importing it leaves the language, theme and reading settings of the phone as they are. All data is made up.
Import on the phone: Settings > Transfer and backup > Restore from file.
"""
import argparse
import json
import random
import sys
import time
from pathlib import Path

AYAHS = {114: 6, 113: 5, 112: 4, 111: 5, 110: 3, 109: 6, 108: 3, 107: 7, 106: 4, 105: 5, 104: 9, 103: 3, 102: 8, 101: 11,
         100: 11, 99: 8, 98: 8, 97: 5, 96: 19, 95: 8}
LADDER = ["R1", "R3", "R7", "R14", "R30"]
INTERVAL = {"R1": 1, "R3": 3, "R7": 7, "R14": 14, "R30": 30}
DAYS = 120
MANZIL_DAYS = 7
JUZ_AMMA_ORDER = list(range(114, 77, -1))  # 114 .. 78


def after_review(stage, grade, day, lapses):
    """The rules of Ladder.afterReview. Returns (stage, nextDue or None, lapses)."""
    def at(st, lp):
        return (st, day + INTERVAL[st], lp) if st in INTERVAL else ("OLD", None, lp)

    def rank(st):
        return LADDER.index(st)

    def of_rank(r):
        return LADDER[r] if r < len(LADDER) else "OLD"

    if stage == "OLD":
        if grade in ("GOOD", "EASY"):
            return "OLD", None, lapses
        return at("R3", lapses) if grade == "HARD" else at("R1", lapses + 1)
    if grade == "FORGOT":
        return at("R1", lapses + 1)
    if grade == "HARD":
        return at(stage, lapses)
    return at(of_rank(rank(stage) + (1 if grade == "GOOD" else 2)), lapses)


def chunks():
    """The passages in learning order: a surah of up to 7 ayahs whole, a longer one in even parts of about 5 ayahs."""
    out = []
    for surah in range(114, 94, -1):
        n = AYAHS[surah]
        if n <= 7:
            out.append((surah, 1, n))
            continue
        parts = -(-n // 5)
        size, extra = divmod(n, parts)
        start = 1
        for i in range(parts):
            end = start + size + (1 if i < extra else 0) - 1
            out.append((surah, start, end))
            start = end + 1
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--today", type=int, default=int((time.time() + time.localtime().tm_gmtoff) // 86400))
    ap.add_argument("--tz-offset", type=int, default=time.localtime().tm_gmtoff)
    ap.add_argument("--out", default=str(Path(__file__).resolve().parent / "demo" / "quran-hifz-demo.json"))
    args = ap.parse_args()
    today, tz = args.today, args.tz_offset
    rng = random.Random(20261101)

    def ts(day, hour, minute):
        return (day * 86400 + hour * 3600 + minute * 60 - tz) * 1000

    start_day = today - DAYS
    # active days: skip about 14 %, two longer breaks, always study the last 9 days before today (a live streak)
    breaks = set(range(start_day + 22, start_day + 29)) | set(range(start_day + 63, start_day + 67))
    active = []
    for d in range(start_day, today):
        if d >= today - 9:
            active.append(d)
        elif d not in breaks and rng.random() > 0.14:
            active.append(d)
    queue = chunks()
    # the last chunk is learned 3 days before today; the others are spread over the active days in between
    learn_days = active[: len(active) - 3]
    step = len(learn_days) / len(queue)
    new_on = {learn_days[min(len(learn_days) - 1, int(i * step))]: i for i in range(len(queue))}

    passages = []   # dict: id, surah, startAyah, endAyah, learned, stage, next, last, lapses, hard{ayah: clears}
    log = []

    def add_log(p, kind, grade, day):
        minute = rng.randrange(0, 50)
        log.append({"id": len(log) + 1, "passageId": p["id"], "surah": p["surah"], "startAyah": p["startAyah"],
                    "endAyah": p["endAyah"], "kind": kind, "grade": grade, "epochDay": day,
                    "timestamp": ts(day, 7 + rng.randrange(0, 3) if rng.random() < 0.5 else 19, minute)})

    def grade_of(hardish):
        r = rng.random()
        if hardish:
            return "HARD" if r < 0.3 else ("GOOD" if r < 0.9 else "FORGOT")
        return "EASY" if r < 0.25 else ("GOOD" if r < 0.92 else ("HARD" if r < 0.98 else "FORGOT"))

    for d in active:
        if d in new_on:
            surah, a, b = queue[new_on[d]]
            p = {"id": len(passages) + 1, "surah": surah, "startAyah": a, "endAyah": b, "learned": d, "stage": "R1",
                 "next": d + 1, "last": d, "lapses": 0, "hard": {}, "hardish": rng.random() < 0.25}
            passages.append(p)
            add_log(p, "NEW", "HARD" if p["hardish"] else "GOOD", d)
            if p["hardish"]:
                p["hard"][rng.randrange(a, b + 1)] = 2
        done = 0
        for p in passages:
            if p["learned"] == d or done >= 4:
                continue
            ladder = p["stage"] != "OLD"
            if ladder and p["next"] <= d:
                kind = "RECENT"
            elif not ladder and d - p["last"] >= MANZIL_DAYS + rng.randrange(0, 5) and done < 2:
                kind = "OLD"
            else:
                continue
            g = grade_of(p["hardish"])
            st, nxt, lp = after_review(p["stage"], g, d, p["lapses"])
            ok = g in ("GOOD", "EASY")
            if ok:
                p["hard"] = {a: c - 1 for a, c in p["hard"].items() if c - 1 > 0}
            if g in ("HARD", "FORGOT") and rng.random() < 0.5:
                p["hard"][rng.randrange(p["startAyah"], p["endAyah"] + 1)] = 2
            p.update(stage=st, next=nxt, last=d, lapses=lp)
            add_log(p, kind, g, d)
            done += 1

    # a few ayahs the learner marked hard lately (the "hard places" of the progress screens)
    for p in sorted(passages, key=lambda q: q["last"])[-9:]:
        if not p["hard"] and p["endAyah"] > p["startAyah"]:
            p["hard"][rng.randrange(p["startAyah"], p["endAyah"] + 1)] = 2

    # keep the file well-formed: ladder stages need a due day, OLD ones none
    for p in passages:
        if p["stage"] == "OLD":
            p["next"] = None
    out = {
        "format": "quran-hifz-backup",
        "formatVersion": 1,
        "appVersion": "2.0.0 (8)",
        "exportedAt": ts(today, 12, 0),
        "dbVersion": 11,
        "hifz": {
            "plan": {"scope": "JUZ_30", "surahOrder": JUZ_AMMA_ORDER, "dailyNewAyahs": 5, "startedEpochDay": start_day,
                     "manzilDays": MANZIL_DAYS},
            "passages": [
                {k: v for k, v in {
                    "id": p["id"], "surah": p["surah"], "startAyah": p["startAyah"], "endAyah": p["endAyah"],
                    "learnedEpochDay": p["learned"], "stage": p["stage"], "nextDueEpochDay": p["next"],
                    "lastReviewedEpochDay": p["last"], "lapses": p["lapses"]}.items() if v is not None}
                for p in passages],
            "hard": [{"passageId": p["id"], "ayah": a, "clearsAfter": c}
                     for p in passages for a, c in sorted(p["hard"].items()) if c > 0],
            "reviewLog": log,
            "meta": {"legacy_import": "done"},
        },
        "notes": [],
    }
    check(out, today)
    path = Path(args.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    learned = sum(p["endAyah"] - p["startAyah"] + 1 for p in passages)
    days = sorted({e["epochDay"] for e in log})
    due_today = [p for p in passages if p["stage"] != "OLD" and p["next"] <= today]
    old_eligible = [p for p in passages if p["stage"] == "OLD" and today - p["last"] >= MANZIL_DAYS]
    print(f"wrote {path}: {len(passages)} passages, {learned} ayahs, {len(set(p['surah'] for p in passages))} surahs, "
          f"{len(log)} log entries on {len(days)} days, {len(out['hifz']['hard'])} hard ayahs; "
          f"today={today}: ladder due {len(due_today)}, old eligible {len(old_eligible)}, entries today {sum(1 for e in log if e['epochDay'] == today)}")


def check(file, today):
    """The rules of BackupValidator, so a bad file never leaves the script."""
    ps = file["hifz"]["passages"]
    by_surah = {}
    for p in ps:
        assert 1 <= p["startAyah"] <= p["endAyah"] <= AYAHS[p["surah"]], p
        assert p["stage"] == "OLD" or "nextDueEpochDay" in p, p
        by_surah.setdefault(p["surah"], []).append(p)
    for items in by_surah.values():
        items.sort(key=lambda p: p["startAyah"])
        for a, b in zip(items, items[1:]):
            assert b["startAyah"] > a["endAyah"], "overlap"
    ids = {p["id"] for p in ps}
    for h in file["hifz"]["hard"]:
        p = next(p for p in ps if p["id"] == h["passageId"])
        assert p["startAyah"] <= h["ayah"] <= p["endAyah"] and 1 <= h["clearsAfter"] <= 2, h
    for e in file["hifz"]["reviewLog"]:
        assert e["passageId"] in ids and e["epochDay"] < today, e   # nothing today: the lesson is not done


if __name__ == "__main__":
    sys.exit(main())
