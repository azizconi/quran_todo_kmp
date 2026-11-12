#!/usr/bin/env python3
"""The reference of the phonetic recitation check (task F7): the SAME rules as `common/voice/PhoneticCheck.kt` of the app; keep them
in step (the Kotlin tests read cases made by `build_voice_phonetics.py golden`).

The model (Hisnlabs/tajweed-v2) writes the recitation in the Quran phonetic script, one sign per sound, a long sound as a repeated
sign. Both the expected script and the heard one are "collapsed" (a run of one sign becomes one sign): the length of a madd or a
ghunnah is not judged here. Python 3.9+, standard library only.
"""

OK, MISSED, REPLACED = "ok", "missed", "replaced"
THRESHOLD = 0.34   # a word is wrong when this share of its sounds is wrong
EXTRA_MIN = 2      # so many foreign sounds between two words are an extra word
MISSED_SHARE = 0.6 # a wrong word is "missed" when this share of its sounds is simply absent ...
REPLACED_MIN = 2   # ... and fewer than this many foreign sounds stand in its place
GUARD = 2          # live: the last sounds of the heard script may still change, a word ending this close to them is not shown yet


def collapse(text):
    out = []
    for c in text:
        if not out or out[-1] != c:
            out.append(c)
    return "".join(out)


def join(words):
    """Collapsed words ("-" = a word without a sound of its own) -> the expected script and [start, end) of every word. A word that
    starts with the sign the script ends with shares that sign (the script stays collapsed)."""
    text, spans = "", []
    for word in words:
        if word == "-" or not word:
            spans.append((len(text), len(text)))
        elif text and text[-1] == word[0]:
            spans.append((len(text) - 1, len(text) + len(word) - 1))
            text += word[1:]
        else:
            spans.append((len(text), len(text) + len(word)))
            text += word
    return text, spans


def cost_table(expected, heard):
    n, m = len(expected), len(heard)
    cost = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        cost[i][0] = i
    for j in range(1, m + 1):
        cost[0][j] = j
    for i in range(1, n + 1):
        row, above, e = cost[i], cost[i - 1], expected[i - 1]
        for j in range(1, m + 1):
            best = above[j - 1] + (e != heard[j - 1])
            if above[j] + 1 < best:
                best = above[j] + 1
            if row[j - 1] + 1 < best:
                best = row[j - 1] + 1
            row[j] = best
    return cost


def trace(cost, expected, heard, end):
    """Walks back from (end, len(heard)): the diagonal step first, then a missing expected sign, then a foreign heard one.
    -> bad[i] (0 right, 1 another sign, 2 absent) for i < end, ins[k] = foreign signs before expected position k (k <= end)."""
    bad, ins = [0] * end, [0] * (end + 1)
    i, j = end, len(heard)
    while i > 0 or j > 0:
        if i > 0 and j > 0 and cost[i][j] == cost[i - 1][j - 1] + (expected[i - 1] != heard[j - 1]):
            bad[i - 1] = 1 if expected[i - 1] != heard[j - 1] else 0
            i -= 1
            j -= 1
        elif i > 0 and cost[i][j] == cost[i - 1][j] + 1:
            bad[i - 1] = 2
            i -= 1
        else:
            ins[i] += 1
            j -= 1
    return bad, ins


def verdicts_of(spans, bad, ins, end, threshold=THRESHOLD, extra_min=EXTRA_MIN):
    """The words whose span ends at or before `end` -> (verdicts, extras [(gap = words before it, foreign signs)])."""
    verdicts, used = [], set()
    count = 0
    for start, stop in spans:
        if stop > end:
            break
        count += 1
        length = max(1, stop - start)
        subs = sum(1 for k in range(start, stop) if bad[k] == 1)
        dels = sum(1 for k in range(start, stop) if bad[k] == 2)
        inside = sum(ins[k] for k in range(start + 1, stop))
        if (subs + dels + inside) / length < threshold:
            verdicts.append(OK)
            continue
        around = ins[start] + inside + ins[stop]
        if dels >= MISSED_SHARE * length and around < REPLACED_MIN:
            verdicts.append(MISSED)
        else:
            verdicts.append(REPLACED)
            used.add(start)
            used.add(stop)
    first = {}
    for index in range(count - 1, -1, -1):
        first[spans[index][0]] = index
    extras = []
    for k in range(end + 1):
        if ins[k] >= extra_min and k not in used and (k in first or k == end):
            extras.append((first.get(k, count), ins[k]))
    return verdicts, extras


def judge(words, heard, threshold=THRESHOLD, extra_min=EXTRA_MIN):
    """The whole passage against the whole heard script (already collapsed)."""
    expected, spans = join(words)
    bad, ins = trace(cost_table(expected, heard), expected, heard, len(expected))
    return verdicts_of(spans, bad, ins, len(expected), threshold, extra_min)


def follow(words, heard, guard=GUARD):
    """Live: how far the reciter has come. The heard script (collapsed) is matched with the beginning of the expected one: the
    beginning that costs the least (the shortest one of equals). -> (words shown, their verdicts)."""
    expected, spans = join(words)
    if not heard:
        return 0, []
    cost = cost_table(expected, heard)
    m = len(heard)
    end = min(range(len(expected) + 1), key=lambda i: (cost[i][m], i))
    bad, ins = trace(cost, expected, heard, end)
    limit = end if end == len(expected) else end - guard
    verdicts, _ = verdicts_of(spans, bad, ins, max(0, limit))
    return len(verdicts), verdicts
