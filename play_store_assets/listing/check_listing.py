#!/usr/bin/env python3
"""Checks the Google Play listing texts: lengths against the store limits and the "no stars" rule. Exit code 1 on a problem."""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIMITS = {"Title": 30, "Short description": 80, "Full description": 4000, "What's new": 500}
STARS = set("★☆✦✧✩✪✫✬✭✮✯✰⭐🌟۞✡⍟⭑⭒※")  # the owner forbids star shapes everywhere, in text too
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿]")

bad = 0
for path in sorted(HERE.glob("*.md")):
    text = path.read_text(encoding="utf-8")
    sections = {}
    for m in re.finditer(r"^## (.+?)\n(.*?)(?=^## |\Z)", text, re.S | re.M):
        sections[m.group(1).strip()] = m.group(2).strip()
    row = []
    for name, limit in LIMITS.items():
        body = sections.get(name)
        if body is None:
            print(f"{path.name}: missing section {name}")
            bad += 1
            continue
        n = len(body)
        flag = "" if n <= limit else "  <-- TOO LONG"
        if n > limit:
            bad += 1
        row.append(f"{name} {n}/{limit}{flag}")
    stars = sorted(set(text) & STARS)
    if stars:
        print(f"{path.name}: star glyphs found: {stars}")
        bad += 1
    if EMOJI.search(text):
        print(f"{path.name}: emoji found")
        bad += 1
    review = "needs native review" if "NEEDS NATIVE-SPEAKER REVIEW" in text else "-"
    print(f"{path.name:10} " + " | ".join(row) + f" | {review}")
sys.exit(1 if bad else 0)
