#!/usr/bin/env python3
"""Checks the layout of the 604 pages of the mushaf (quran.com) and writes what the app keeps of it.

Task F3a (mushaf pages). What it does, in this order:

1. Fetches the layout of every page from the open API of quran.com (one request per page, 0.12 s apart, cached in CACHE_DIR so a second
   run needs no network):
       GET https://api.quran.com/api/v4/verses/by_page/{page}?words=true&word_fields=code_v1,line_number,page_number&per_page=50&fields=chapter_id
2. Checks the rule of the headers and Bismillahs (see MushafLayoutRules.kt) on all 604 pages and prints every exception:
   the lines of a page that hold no words are the header and the Bismillah of the surah that starts on it; Al-Fatihah and At-Tawbah have
   a header only; 21 times the header stands on the last line of the previous page.
3. Compares the page of every ayah with the bundled text (composeApp/src/commonMain/composeResources/files/quran/quran-uthmani.tsv).
4. Writes composeApp/src/commonMain/kotlin/tj/app/quran_todo/common/mushaf/MushafPages.kt (the table of the first ayah and the quarter of a
   hizb of every page) and the trimmed answers + page_shapes.txt used by the unit tests
   (composeApp/src/androidUnitTest/resources/mushaf/).

Usage:  python3 scripts/build_mushaf_pages.py CACHE_DIR [--no-write]
Needs only the standard library. Run from the root of the repository.
"""
import collections
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TSV = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/quran/quran-uthmani.tsv")
KOTLIN = os.path.join(ROOT, "composeApp/src/commonMain/kotlin/tj/app/quran_todo/common/mushaf/MushafPages.kt")
FIXTURES = os.path.join(ROOT, "composeApp/src/androidUnitTest/resources/mushaf")
URL = (
    "https://api.quran.com/api/v4/verses/by_page/{n}?words=true&word_fields=code_v1,line_number,page_number"
    "&per_page=50&fields=chapter_id"
)
FIXTURE_PAGES = [1, 2, 3, 50, 76, 77, 187, 586, 587, 604]

def fetch_all(cache):
    os.makedirs(cache, exist_ok=True)
    for n in range(1, 605):
        path = os.path.join(cache, f"p{n}.json")
        if os.path.exists(path) and os.path.getsize(path) > 100:
            continue
        for attempt in range(4):
            try:
                request = urllib.request.Request(URL.format(n=n), headers={"User-Agent": "QuranHifz-layout-check/1.0"})
                with urllib.request.urlopen(request, timeout=30) as response:
                    data = response.read()
                json.loads(data)
                with open(path, "wb") as out:
                    out.write(data)
                break
            except Exception:
                time.sleep(1.5 * (attempt + 1))
        else:
            sys.exit(f"page {n} could not be fetched")
        time.sleep(0.12)


def load_text():
    ayahs = {}
    with open(TSV, encoding="utf-8") as tsv:
        for line in tsv:
            if line.startswith("A\t"):
                c = line.rstrip("\n").split("\t")
                # A <number> <surah> <numberInSurah> <juz> <hizbQuarter> <manzil> <page> <ruku>
                ayahs[(int(c[2]), int(c[3]))] = (int(c[1]), int(c[4]), int(c[5]), int(c[7]))
    return ayahs


def check(cache, text):
    char_types = collections.Counter()
    exceptions = []
    layout_page = {}
    first_on_page = {}
    for n in range(1, 605):
        data = json.load(open(os.path.join(cache, f"p{n}.json")))
        assert data["pagination"]["total_pages"] == 1, f"page {n} needs more than one request"
        words = []
        for verse in data["verses"]:
            s, a = map(int, verse["verse_key"].split(":"))
            first_on_page.setdefault(n, (s, a))
            for w in verse["words"]:
                char_types[w["char_type_name"]] += 1
                assert w["page_number"] == n, f"a word of another page on page {n}"
                words.append((w["line_number"], s, a, w["position"]))
                if w["position"] == 1:
                    layout_page[(s, a)] = n
        lines = sorted({w[0] for w in words})
        cap = 8 if n <= 2 else 15
        starts = {s: line for (line, s, a, pos) in words if a == 1 and pos == 1}
        empty = [l for l in range(1, cap + 1) if l not in lines]
        expected = set()
        for s, line in starts.items():
            for k in range(1, (1 if s in (1, 9) else 2) + 1):
                expected.add(line - k)
        if set(empty) != {l for l in expected if l >= 1} or any(l < 1 for l in expected):
            exceptions.append((n, starts, empty, sorted(expected)))
    print("char types:", dict(char_types))
    print(f"pages that do not follow the rule as it stands: {len(exceptions)} (the 21 pages with the header on line 15 and the 21 "
          f"pages that follow them)")
    for n, starts, empty, expected in exceptions:
        print("  page", n, "starts", starts, "empty lines", empty, "rule expects", expected)
    mismatches = [k for k in text if layout_page.get(k) != text[k][3]]
    print("ayahs on another page than the bundled text says:", len(mismatches))
    return first_on_page


def kotlin_table(values):
    return "\n".join("        " + ", ".join(str(v) for v in values[i:i + 16]) + "," for i in range(0, len(values), 16))


def write_kotlin(text):
    first = {}
    for key, (number, juz, quarter, page) in sorted(text.items(), key=lambda item: item[1][0]):
        first.setdefault(page, (number, quarter))
    starts = [first[p][0] for p in range(1, 605)]
    quarters = [first[p][1] for p in range(1, 605)]
    with open(KOTLIN, encoding="utf-8") as existing:
        source = existing.read()
    head, rest = source.split("    private val firstAyahNumber = intArrayOf(\n", 1)
    _, rest = rest.split("    )\n", 1)
    middle, rest = rest.split("    private val firstHizbQuarter = intArrayOf(\n", 1)
    _, tail = rest.split("    )\n", 1)
    updated = (
        head + "    private val firstAyahNumber = intArrayOf(\n" + kotlin_table(starts) + "\n    )\n" + middle
        + "    private val firstHizbQuarter = intArrayOf(\n" + kotlin_table(quarters) + "\n    )\n" + tail
    )
    with open(KOTLIN, "w", encoding="utf-8") as out:
        out.write(updated)
    print("written", KOTLIN)


def write_fixtures(cache):
    os.makedirs(FIXTURES, exist_ok=True)
    for n in FIXTURE_PAGES:
        d = json.load(open(os.path.join(cache, f"p{n}.json")))
        slim = {
            "verses": [
                {
                    "verse_number": v["verse_number"],
                    "verse_key": v["verse_key"],
                    "chapter_id": v["chapter_id"],
                    "page_number": v["page_number"],
                    "words": [
                        {k: w[k] for k in ("position", "char_type_name", "code_v1", "line_number", "page_number")}
                        for w in v["words"]
                    ],
                }
                for v in d["verses"]
            ],
            "pagination": d["pagination"],
        }
        with open(os.path.join(FIXTURES, f"page_{n}.json"), "w", encoding="utf-8") as out:
            json.dump(slim, out, ensure_ascii=False, separators=(",", ":"))
    rows = []
    for n in range(1, 605):
        d = json.load(open(os.path.join(cache, f"p{n}.json")))
        words = [(w["line_number"], int(v["verse_key"].split(":")[0]), int(v["verse_key"].split(":")[1]), w["position"], w["char_type_name"])
                 for v in d["verses"] for w in v["words"]]
        lines = sorted({w[0] for w in words})
        starts = sorted((line, s) for (line, s, a, pos, t) in words if a == 1 and pos == 1)
        last = words[-1]
        rows.append("{}|{}|{}|{}:{}:{}".format(
            n, ",".join(map(str, lines)), ",".join(f"{s}@{line}" for line, s in starts), last[1], last[2], 1 if last[4] == "end" else 0))
    with open(os.path.join(FIXTURES, "page_shapes.txt"), "w") as out:
        out.write("# page|lines with words|surah@line of every surah that starts on the page|surah:ayah:isEnd of the last word\n")
        out.write("\n".join(rows) + "\n")
    print("written", FIXTURES)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cache = sys.argv[1]
    fetch_all(cache)
    text = load_text()
    check(cache, text)
    if "--no-write" not in sys.argv:
        write_kotlin(text)
        write_fixtures(cache)


if __name__ == "__main__":
    main()
