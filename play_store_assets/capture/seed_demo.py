#!/usr/bin/env python3
"""Seeds a freshly onboarded database with a believable five-day history of a Juz Amma learner.

Usage: seed_demo.py <quran_todo.db> <today_epoch_day> <tz_offset_seconds>

The onboarding (11 known surahs, plan Juz Amma from the end, 5 ayahs a day) must already be done: the script only
adds what "a few lessons later" looks like, so every screenshot of every language shows the same story:

  T-5  learned Al-Humazah 1-5
  T-4  learned Al-Humazah 6-9, repeated 1-5
  T-3  learned Al-Asr 1-3
  T-2  learned At-Takathur 1-5, repeated Al-Asr and Al-Humazah 6-9
  T-1  learned At-Takathur 6-8, repeated At-Takathur 1-5 and Al-Humazah 1-5
  today: new Al-Qari'a 1-5, recent At-Takathur 6-8, old: two short surahs  (a streak of 5 days before the lesson)

Only test data of the demo build is written; nothing here touches the app code.
"""
import sqlite3
import sys

db_path, today, tz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
T = today


def ts(day, hour=19, minute=30):
    return (day * 86400 + hour * 3600 + minute * 60 - tz) * 1000


con = sqlite3.connect(db_path)
cur = con.cursor()

# the plan starts five days ago
cur.execute("UPDATE hifz_plan SET startedEpochDay=?", (T - 5,))

# the manzil circle: only two short surahs are eligible today (last repetition >= manzilDays = 7 days ago)
old_last = {1: T - 3, 105: T - 2, 106: T - 1, 107: T - 4, 108: T - 9, 109: T - 3, 110: T - 10, 111: T - 5, 112: T - 2, 113: T - 6, 114: T - 4}
for surah, last in old_last.items():
    cur.execute("UPDATE hifz_passage SET lastReviewedEpochDay=? WHERE surah=? AND stage='OLD'", (last, surah))
cur.execute("DELETE FROM review_log")
cur.execute("DELETE FROM passage_hard")
cur.execute("DELETE FROM hifz_passage WHERE stage<>'OLD'")


def passage(surah, start, end, learned, stage, due, last):
    cur.execute(
        "INSERT INTO hifz_passage(surah,startAyah,endAyah,learnedEpochDay,stage,nextDueEpochDay,lastReviewedEpochDay,lapses)"
        " VALUES (?,?,?,?,?,?,?,0)",
        (surah, start, end, learned, stage, due, last),
    )
    return cur.lastrowid


def log(pid, surah, start, end, kind, grade, day, minute=0):
    cur.execute(
        "INSERT INTO review_log(passageId,surah,startAyah,endAyah,kind,grade,epochDay,timestamp) VALUES (?,?,?,?,?,?,?,?)",
        (pid, surah, start, end, kind, grade, day, ts(day, 19, 30 + minute)),
    )


A = passage(104, 1, 5, T - 5, "R7", T + 6, T - 1)
B = passage(104, 6, 9, T - 4, "R3", T + 1, T - 2)
C = passage(103, 1, 3, T - 3, "R3", T + 1, T - 2)
D = passage(102, 1, 5, T - 2, "R3", T + 2, T - 1)
E = passage(102, 6, 8, T - 1, "R1", T, T - 1)

cur.execute("INSERT INTO passage_hard(passageId,ayahInSurah,clearsAfter) VALUES (?,?,?)", (A, 2, 1))
cur.execute("INSERT INTO passage_hard(passageId,ayahInSurah,clearsAfter) VALUES (?,?,?)", (D, 4, 2))

log(A, 104, 1, 5, "NEW", "HARD", T - 5)
log(1, 1, 1, 7, "OLD", "GOOD", T - 5, 4)
log(B, 104, 6, 9, "NEW", "GOOD", T - 4)
log(A, 104, 1, 5, "RECENT", "GOOD", T - 4, 5)
log(C, 103, 1, 3, "NEW", "EASY", T - 3)
log(D, 102, 1, 5, "NEW", "GOOD", T - 2)
log(C, 103, 1, 3, "RECENT", "GOOD", T - 2, 6)
log(B, 104, 6, 9, "RECENT", "GOOD", T - 2, 8)
log(E, 102, 6, 8, "NEW", "GOOD", T - 1)
log(D, 102, 1, 5, "RECENT", "HARD", T - 1, 6)
log(A, 104, 1, 5, "RECENT", "GOOD", T - 1, 8)

# derived statuses, like the app writes them: the surahs of the learned passages
for surah in (102, 103, 104):
    cur.execute("INSERT OR REPLACE INTO surah_todo(surahNumber,status) VALUES (?, 'LEARNED')", (surah,))
rows = cur.execute(
    "SELECT number, surahNumber FROM ayahs WHERE (surahNumber=104 AND numberInSurah BETWEEN 1 AND 9)"
    " OR surahNumber=103 OR (surahNumber=102 AND numberInSurah BETWEEN 1 AND 8)"
).fetchall()
for number, surah in rows:
    cur.execute(
        "INSERT OR REPLACE INTO ayah_todo(ayahNumber,surahNumber,status,updatedAt) VALUES (?,?,?,?)",
        (number, surah, "LEARNED", ts(T - 3)),
    )
con.commit()
con.close()
print("seeded", len(rows), "ayahs; today =", T)
