#!/usr/bin/env python3
"""Builds the index of similar ayahs (mutashabihat, task F6) from the Quran text that ships inside the app.

Source: composeApp/src/commonMain/composeResources/files/quran/quran-uthmani.tsv (Tanzil text, see build_quran_asset.py). Nothing else is used:
no file of QUL or of any other service goes into the repository or the app, the index is computed from our own text.
Output: composeApp/src/commonMain/composeResources/files/quran/similar.tsv

Usage:
    python3 scripts/build_mutashabihat.py            # build the index and write it, print the check
    python3 scripts/build_mutashabihat.py --check    # print the check, and whether the committed index equals a fresh build (no write)
    python3 scripts/build_mutashabihat.py --stdout   # build and print the check, write nothing

Only the standard library is used.

How the words are compared (the SAME rules are in Kotlin, common/similar/WordNormalizer.kt; keep them equal):
  * an ayah is split by white space; the Bismillah that opens the first ayah of the surahs 2-114 (except 9) is dropped (its first four
    words, as `cutBismillah` of the reader does); a token that has no letter (the sign of a quarter of a hizb, a pause mark) is no word;
  * a word is normalized: every mark is removed (harakat, the dagger alef, Quranic annotation signs, tatweel), the letters that differ
    only in spelling are made one: alef with hamza or madda and alef wasla -> alef, alef maqsura -> yeh, teh marbuta -> heh, waw and
    yeh with hamza -> hamza.

What is "similar":
  * two ayahs of >= 3 words each, with a common run (a stretch of words that follow each other in both) of >= 4 words, or of >= 3
    words when the run covers >= 60 % of the shorter ayah;
  * the run must not be a formula: a run that occurs in more than FORMULA_DF ayahs ("inna Allaha ghafurun rahim"), or a run of only 3
    words that occurs in more than FORMULA_DF_SHORT ayahs ("wa ma adraka ma"), does not make a pair on its own, the pair needs another
    run that is not a formula;
  * a pair with a score below MIN_SCORE (a small part of two long ayahs) needs a run that is in at most RARE_DF ayahs;
  * the score is the share (0-100) of the words of the shorter ayah that are matched by the longest common subsequence (LCS);
  * ayahs with exactly the same words that occur >= 3 times are a REPEAT GROUP (the refrain of Ar-Rahman, of Al-Mursalat, ...): they are
    not listed as pairs of each other, the group is one line.
  * every ayah keeps its MAX_PER_AYAH best pairs; pairs are symmetric (a pair is taken best first and only while both ayahs have room).

File format (UTF-8, LF, no BOM; lines starting with '#' and empty lines are comments; fields are separated by a TAB):

    R <count> <ayah> <ayah> ...      a repeat group: the ayahs (as "surah:ayah") that have the same words, in the order of the Quran
    P <ayah> <ayah>=<score> ...      the similar ayahs of one ayah, best first, score 0-100; ayahs with none have no line
"""

import argparse
import collections
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QURAN = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "quran", "quran-uthmani.tsv")
OUTPUT = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "quran", "similar.tsv")

AYAH_COUNT = 6236
MIN_WORDS = 3  # an ayah with fewer words is never similar to anything
RUN_LONG = 4  # a common run of this many words is enough ...
RUN_SHORT = 3  # ... or of this many when it covers RUN_SHARE of the shorter ayah
RUN_SHARE = 0.60
FORMULA_DF = 15  # a run that is in more ayahs than this is a formula
FORMULA_DF_SHORT = 8  # ... and a run of only 3 words that is in more ayahs than this
WIDE_LCS = 8  # two or three runs of 3 words that together are >= WIDE_SHARE of the shorter ayah and >= this many words
WIDE_SHARE = 0.55
MIN_SCORE = 30  # a pair with a lower score needs a rare run (RARE_DF), see below
RARE_DF = 4  # a 3-word run that is in at most this many ayahs is specific ...
RARE_LCS = 5  # ... and with this many matched words in all it makes a pair, when the pair has a score of RARE_SCORE or the phrase RARE_LETTERS letters
RARE_SCORE = 35
RARE_LETTERS = 15
REPEAT_MIN = 3  # identical ayahs that occur this many times are a repeat group
MAX_PER_AYAH = 10
CANDIDATE_DF = 80  # a 3-gram of words that is in more ayahs than this does not look for candidates (it is a formula for sure)

# -- Normalization (keep equal to common/similar/WordNormalizer.kt) --------------------------------------------------

ALEF_LIKE = {"آ", "أ", "إ", "ٱ"}  # alef with madda, hamza above, hamza below; alef wasla
YEH_LIKE = {"ى", "ٮ"}  # alef maqsura, dotless beh
HAMZA_CARRIERS = {"ؤ", "ئ"}  # waw with hamza, yeh with hamza


def normalize_word(word):
    out = []
    for c in word:
        if c in ALEF_LIKE:
            c = "ا"
        elif c in YEH_LIKE:
            c = "ي"
        elif c == "ة":
            c = "ه"
        elif c in HAMZA_CARRIERS:
            c = "ء"
        code = ord(c)
        # What stays is a letter of the Arabic block: hamza..ghain (0621-063A) and feh..yeh (0641-064A). Everything else is a mark, a
        # sign or tatweel and goes (harakat 064B-065F, dagger alef 0670, Quranic signs 06D6-06ED, tatweel 0640, joiners).
        if 0x0621 <= code <= 0x063A or 0x0641 <= code <= 0x064A:
            out.append(c)
    return "".join(out)


def split_words(surah, ayah, text):
    tokens = text.split()
    if ayah == 1 and 2 <= surah <= 114 and surah != 9 and len(tokens) > 4 and normalize_word(tokens[0]) == "بسم":
        tokens = tokens[4:]
    words = [normalize_word(t) for t in tokens]
    return [w for w in words if w]


def load_quran():
    ayahs = []  # (surah, ayah, words, text)
    with open(QURAN, encoding="utf-8") as handle:
        for line in handle:
            if not line.startswith("A\t"):
                continue
            fields = line.rstrip("\n").split("\t")
            surah, ayah, text = int(fields[2]), int(fields[3]), fields[-1]
            ayahs.append((surah, ayah, split_words(surah, ayah, text), text))
    if len(ayahs) != AYAH_COUNT:
        sys.exit("expected %d ayahs, found %d" % (AYAH_COUNT, len(ayahs)))
    return ayahs


# -- Comparison -------------------------------------------------------------------------------------------------------

def common_runs(a, b):
    """Maximal common runs of two word lists: (start in a, start in b, length), length >= RUN_SHORT, and the LCS length."""
    n, m = len(a), len(b)
    prev = [0] * (m + 1)
    lcs_prev = [0] * (m + 1)
    runs = []
    for i in range(1, n + 1):
        cur = [0] * (m + 1)
        lcs_cur = [0] * (m + 1)
        ai = a[i - 1]
        for j in range(1, m + 1):
            if ai == b[j - 1]:
                cur[j] = prev[j - 1] + 1
                lcs_cur[j] = lcs_prev[j - 1] + 1
            else:
                lcs_cur[j] = lcs_prev[j] if lcs_prev[j] >= lcs_cur[j - 1] else lcs_cur[j - 1]
            length = cur[j]
            # a run ends at (i, j) when it cannot be extended by (i+1, j+1)
            if length >= RUN_SHORT and not (i < n and j < m and a[i] == b[j]):
                runs.append((i - length, j - length, length))
        prev, lcs_prev = cur, lcs_cur
    return runs, lcs_prev[m]


def build():
    ayahs = load_quran()
    keys = ["%d:%d" % (s, a) for s, a, _, _ in ayahs]
    words = [w for _, _, w, _ in ayahs]

    # Repeat groups: the same words, REPEAT_MIN times or more, MIN_WORDS words or more.
    by_text = collections.defaultdict(list)
    for index, w in enumerate(words):
        if len(w) >= MIN_WORDS:
            by_text[tuple(w)].append(index)
    groups = [members for members in by_text.values() if len(members) >= REPEAT_MIN]
    groups.sort(key=lambda members: members[0])
    group_of = {}
    for number, members in enumerate(groups):
        for index in members:
            group_of[index] = number

    # Index of the 3-grams of words.
    grams = collections.defaultdict(list)
    for index, w in enumerate(words):
        if len(w) < MIN_WORDS:
            continue
        seen = set()
        for k in range(len(w) - 2):
            gram = (w[k], w[k + 1], w[k + 2])
            if gram not in seen:
                seen.add(gram)
                grams[gram].append(index)

    df_cache = {}

    def run_df(run):
        """In how many ayahs the run occurs (it is looked for among the ayahs of its rarest 3-gram)."""
        cached = df_cache.get(run)
        if cached is not None:
            return cached
        rarest = min((tuple(run[k:k + 3]) for k in range(len(run) - 2)), key=lambda gram: len(grams[gram]))
        total = 0
        size = len(run)
        for index in grams[rarest]:
            w = words[index]
            for k in range(len(w) - size + 1):
                if tuple(w[k:k + size]) == run:
                    total += 1
                    break
        df_cache[run] = total
        return total

    # Candidates: ayahs that share a 3-gram that is not a formula for sure.
    candidates = set()
    for members in grams.values():
        if len(members) < 2 or len(members) > CANDIDATE_DF:
            continue
        for x in range(len(members)):
            for y in range(x + 1, len(members)):
                candidates.add((members[x], members[y]))

    pairs = []  # (score, a, b)
    for a_index, b_index in candidates:
        if a_index in group_of and group_of[a_index] == group_of.get(b_index):
            continue
        a, b = words[a_index], words[b_index]
        shorter = min(len(a), len(b))
        runs, lcs = common_runs(a, b)
        score = round(100 * lcs / shorter)
        ok = False
        for start, _, length in runs:
            df = run_df(tuple(a[start:start + length]))
            # A run of 3 words is a formula sooner than a run of 4 or more: three words of a short ayah are easily a stock phrase.
            if df > (FORMULA_DF if length >= RUN_LONG else FORMULA_DF_SHORT):
                continue
            # A weak likeness (a small part of two long ayahs) counts only when the shared run is rare: a phrase of a few ayahs.
            if score < MIN_SCORE and df > RARE_DF:
                continue
            if length >= RUN_LONG or length >= RUN_SHARE * shorter:
                ok = True  # the rule of the task: a long run, or a run that is most of the shorter ayah
            elif lcs >= WIDE_LCS and lcs >= WIDE_SHARE * shorter:
                ok = True  # wide: two or three short runs that make most of the shorter ayah (2:58 / 7:161)
            elif df <= RARE_DF and lcs >= RARE_LCS and (score >= RARE_SCORE or sum(map(len, a[start:start + length])) >= RARE_LETTERS):
                ok = True  # a rare phrase of 3 words that only a few ayahs have, with a few more words around it (6:151 / 17:31)
            if ok:
                break
        if ok:
            pairs.append((score, a_index, b_index))

    # Symmetric, at most MAX_PER_AYAH per ayah, best pairs first.
    pairs.sort(key=lambda p: (-p[0], p[1], p[2]))
    room = collections.Counter()
    similar = collections.defaultdict(list)
    kept = []
    for score, a_index, b_index in pairs:
        if room[a_index] >= MAX_PER_AYAH or room[b_index] >= MAX_PER_AYAH:
            continue
        room[a_index] += 1
        room[b_index] += 1
        similar[a_index].append((b_index, score))
        similar[b_index].append((a_index, score))
        kept.append((score, a_index, b_index))
    return keys, words, groups, similar, kept


def render(keys, groups, similar):
    lines = [
        "# Similar ayahs (mutashabihat): computed by scripts/build_mutashabihat.py from quran-uthmani.tsv (Tanzil text). Do not edit by hand.",
        "# R <count> <ayah>...: ayahs with the same words. P <ayah> <ayah>=<score>...: the similar ayahs of one ayah, best first. Format: see the script.",
    ]
    for members in groups:
        lines.append("R\t%d\t%s" % (len(members), "\t".join(keys[i] for i in members)))
    for index in sorted(similar):
        entries = sorted(similar[index], key=lambda e: (-e[1], e[0]))
        lines.append("P\t%s\t%s" % (keys[index], "\t".join("%s=%d" % (keys[j], score) for j, score in entries)))
    return "\n".join(lines) + "\n"


# -- Check ------------------------------------------------------------------------------------------------------------

REFERENCE_PAIRS = [
    ("2:35", "7:19"), ("2:58", "7:161"), ("2:49", "7:141"), ("2:49", "14:6"), ("7:141", "14:6"),
    ("2:173", "16:115"), ("2:173", "6:145"), ("2:62", "5:69"), ("3:126", "8:10"), ("2:136", "3:84"),
    ("2:61", "3:112"), ("2:120", "2:145"), ("3:62", "38:65"), ("3:11", "8:52"),
    ("2:57", "7:160"), ("5:6", "4:43"), ("14:35", "2:126"), ("6:151", "17:31"), ("7:80", "27:54"),
    ("11:84", "7:85"), ("7:59", "11:50"), ("10:61", "34:3"), ("2:40", "2:47"), ("7:73", "11:61"),
]


def report(keys, words, groups, similar, kept, seed=20260101):
    index_of = {k: i for i, k in enumerate(keys)}
    grouped = {m for g in groups for m in g}
    out = []
    out.append("ayahs with similar ayahs: %d of %d" % (len(similar), AYAH_COUNT))
    out.append("pairs: %d" % len(kept))
    out.append("repeat groups: %d (ayahs in them: %d)" % (len(groups), len(grouped)))
    biggest = sorted(groups, key=lambda g: -len(g))[:6]
    out.append("biggest groups: " + "; ".join("%d x %s..." % (len(g), keys[g[0]]) for g in biggest))
    out.append("ayahs per count of similar: " + ", ".join("%d: %d" % (n, c) for n, c in sorted(collections.Counter(len(v) for v in similar.values()).items())))
    out.append("reference pairs:")
    found = 0
    for a, b in REFERENCE_PAIRS:
        ia, ib = index_of[a], index_of[b]
        score = next((s for j, s in similar.get(ia, []) if j == ib), None)
        same_group = any(ia in g and ib in g for g in groups)
        state = "found, score %d" % score if score is not None else ("same repeat group" if same_group else "MISSING")
        found += score is not None or same_group
        out.append("  %-7s %-7s %s" % (a, b, state))
    out.append("reference pairs found: %d of %d" % (found, len(REFERENCE_PAIRS)))
    rng = random.Random(seed)
    sample = rng.sample(kept, 20)
    out.append("20 random pairs (score, ayah, ayah, the longest common run):")
    for score, a_index, b_index in sample:
        a, b = words[a_index], words[b_index]
        runs, _ = common_runs(a, b)
        start, _, length = max(runs, key=lambda r: r[2])
        out.append("  %3d  %-8s %-8s  %s" % (score, keys[a_index], keys[b_index], " ".join(a[start:start + length])))
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--check", action="store_true", help="print the check and compare with the committed index, write nothing")
    parser.add_argument("--stdout", action="store_true", help="build and print the check, write nothing")
    args = parser.parse_args()
    keys, words, groups, similar, kept = build()
    text = render(keys, groups, similar)
    if args.check or args.stdout:
        if args.check and os.path.isfile(OUTPUT):
            with open(OUTPUT, encoding="utf-8") as handle:
                print("committed index equals a fresh build: %s" % (handle.read() == text))
    else:
        with open(OUTPUT, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        print("wrote %s" % OUTPUT)
    print(report(keys, words, groups, similar, kept))
    print("size: %d bytes" % len(text.encode("utf-8")))


if __name__ == "__main__":
    main()
