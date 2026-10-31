#!/usr/bin/env python3
"""Checks the alignment of the tajweed markup onto the bundled Quran text on ALL 6236 ayahs (task F3b).

The app colours the rules of recitation (idgham, ikhfa, madd, ...) in the text of the reader and of the lesson. The rules come from the
markup of api.alquran.cloud `quran-tajweed`; that markup has a text of its own, which is NOT the bundled text (after the markup is taken
off, only 774 of the 6236 ayahs are equal), so the app aligns the two texts letter by letter and moves every rule onto OUR letters
(composeApp/src/commonMain/kotlin/tj/app/quran_todo/common/tajweed/). This script checks that alignment on the whole Quran.

It does not re-implement anything: it fetches the real answer (one request, about 2.5 MB), puts it where the check expects it and runs
the Kotlin check with the real functions of the app (`splitBismillah`, `TajweedMarkup`, `TajweedAlignment`):

    composeApp/src/androidUnitTest/.../ui/tajweed/TajweedFullQuranCheck.kt

The check is not part of the normal unit tests (the data comes from the network): without the file it is skipped.

Usage:
    python3 scripts/check_tajweed_alignment.py                  # download (or reuse the file from the last run), check, print the report
    python3 scripts/check_tajweed_alignment.py --refresh        # download again
    python3 scripts/check_tajweed_alignment.py --input q.json   # use an already downloaded answer
    python3 scripts/check_tajweed_alignment.py --report-only    # print the report of the last run

Prints, and writes to composeApp/build/tajweed-check/report.txt:
  * the share of the rules of the markup that reach our text (the goal is 99.5 %) and the rules that are lost, by ayah;
  * the moved ranges whose letters differ from the letters of the rule in their text (by skeleton: letters only, no harakat, the
    spellings of one letter made equal), expected 0;
  * the letters that have no partner in the other text, the ayahs with many of them;
  * that every example of the legend is the rule it says it is.

Only the standard library is used. Gradle runs from the root of the repository with -Xmx3g, as everywhere in this project.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.request

API_URL = "https://api.alquran.cloud/v1/quran/quran-tajweed"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHECK_DIR = os.path.join(ROOT, "composeApp", "build", "tajweed-check")
DATA = os.path.join(CHECK_DIR, "quran-tajweed.json")
REPORT = os.path.join(CHECK_DIR, "report.txt")
SURAH_COUNT = 114
AYAH_COUNT = 6236
TEST = "tj.app.quran_todo.ui.tajweed.TajweedFullQuranCheck"


def download(target):
    request = urllib.request.Request(API_URL, headers={"User-Agent": "quran-hifz-tajweed-check"})
    with urllib.request.urlopen(request, timeout=120) as response:
        body = response.read()
    with open(target, "wb") as handle:
        handle.write(body)
    print("downloaded %s (%d bytes)" % (API_URL, len(body)))


def verify(path):
    """The answer must be a whole Quran: 114 surahs, 6236 ayahs, each with text."""
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    surahs = data["data"]["surahs"]
    ayahs = sum(len(surah["ayahs"]) for surah in surahs)
    if len(surahs) != SURAH_COUNT or ayahs != AYAH_COUNT:
        sys.exit("%s is not a whole Quran: %d surahs, %d ayahs" % (path, len(surahs), ayahs))
    if any(not ayah["text"] for surah in surahs for ayah in surah["ayahs"]):
        sys.exit("%s has an ayah without text" % path)
    print("%s: %d surahs, %d ayahs" % (path, len(surahs), ayahs))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--input", help="an already downloaded answer of quran-tajweed")
    parser.add_argument("--refresh", action="store_true", help="download again even if the file of the last run is there")
    parser.add_argument("--report-only", action="store_true", help="only print the report of the last run")
    args = parser.parse_args()

    if args.report_only:
        if not os.path.isfile(REPORT):
            sys.exit("no report yet: run the script without --report-only")
        print(open(REPORT, encoding="utf-8").read())
        return

    os.makedirs(CHECK_DIR, exist_ok=True)
    if args.input:
        verify(args.input)
        shutil.copyfile(args.input, DATA)
    else:
        if args.refresh or not os.path.isfile(DATA):
            download(DATA)
        verify(DATA)
    if os.path.isfile(REPORT):
        os.remove(REPORT)

    # The check is a unit test: clean its result first, or Gradle takes the last run for up to date (the data is not an input of the task).
    command = [
        os.path.join(ROOT, "gradlew"),
        "-Dorg.gradle.jvmargs=-Xmx3g",
        ":composeApp:cleanTestDebugUnitTest",
        ":composeApp:testDebugUnitTest",
        "--tests",
        TEST,
    ]
    print("running:", " ".join(command[1:]))
    code = subprocess.call(command, cwd=ROOT)
    if os.path.isfile(REPORT):
        print()
        print(open(REPORT, encoding="utf-8").read())
    else:
        print("the check wrote no report (the data was not found or the build failed)")
        code = code or 1
    sys.exit(code)


if __name__ == "__main__":
    main()
