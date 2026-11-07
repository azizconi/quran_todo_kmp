#!/usr/bin/env python3
"""Quality of word-level recitation checking by an on-device Whisper (task F7, prototype).

The question: when a reciter reads a passage right and the REFERENCE text is damaged, does the checker find the damage?
Errors are made on the reference, not on the audio (a real reciter does not make mistakes on demand):

  missed    a word that is not in the audio is inserted into the reference     -> the checker must say "missed" for it
  replaced  a word of the reference is changed to a similar one                -> "replaced"
  extra     a word is removed from the reference (the reciter "said too much") -> an "extra" word at that place
  clean     the reference is not touched                                       -> every reported error is a false error

Stages (each can be run alone, everything stays in --work, nothing of it goes to the repository):

  prepare    picks the passages, downloads the ayah audio of the reciters of the app (Islamic Network CDN), joins the ayahs of a
             passage into one 16 kHz mono wav (macOS `afconvert`; no ffmpeg needed)
  recognize  runs whisper.cpp CLI (--cli) with every model (--model name=path) on every passage, keeps text and wall time
  score      aligns, makes the errors, prints the tables (and writes <work>/results/report.md)

  python3 scripts/voice_eval.py all --work <dir> --cli <whisper-cli> --model q8_0=<ggml-q8_0.bin> --model q5_0=<ggml-q5_0.bin>

The word rules (normalize_word, the skeleton, the alignment) are the same as `common/voice/**` of the app (Kotlin); keep them in step.
Python 3.9+, standard library only.
"""
import argparse
import difflib
import json
import os
import random
import re
import statistics
import subprocess
import sys
import time
import urllib.request
import wave
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QURAN = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "quran", "quran-uthmani.tsv")
SIMILAR = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "quran", "similar.tsv")

# The reciters of the app (common/audio/Reciter.kt): the same CDN and editions.
RECITERS = [
    ("alafasy", "ar.alafasy", 128),
    ("husary", "ar.husary", 128),
    ("abdulbasit", "ar.abdulbasitmurattal", 192),
]
LONG_SURAHS = [2, 18, 36, 67]
AMMA_RANGE = range(78, 115)  # juz 'Amma
MAX_WORDS = 80  # a passage of the lesson is short; ~60 s of audio at the most


# -- the words: the SAME rules as common/similar/WordNormalizer.kt and common/voice/RecitationAlignment.kt -------------------------

def normalize_word(word):
    out = []
    for c in word:
        if c in "آأإٱ":
            c = "ا"
        elif c in "ىٮ":
            c = "ي"
        elif c == "ة":
            c = "ه"
        elif c in "ؤئ":
            c = "ء"
        code = ord(c)
        if 0x0621 <= code <= 0x063A or 0x0641 <= code <= 0x064A:
            out.append(c)
    return "".join(out)


def words_of(text):
    """The normalized words of a text (a token without a letter is no word)."""
    result = []
    for token in text.split():
        word = normalize_word(token)
        if word:
            result.append(word)
    return result


def skeleton(word):
    """What is left of a word when the spellings that the model chooses freely go: alef and hamza (written in many ways), and a
    doubled letter (the shadda is gone, the model may or may not write it twice)."""
    out = []
    for c in word:
        if c in "اء":
            continue
        if out and out[-1] == c:
            continue
        out.append(c)
    return "".join(out)


def edit_distance(a, b):
    previous = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        current = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            current[j] = min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (a[i - 1] != b[j - 1]))
        previous = current
    return previous[len(b)]


STRICT, FUZZY = "strict", "fuzzy"
FUZZY_MIN_LENGTH = 4  # a word has to be this long (skeleton) to be allowed one wrong letter


def same_word(reference, heard, mode):
    if reference == heard:
        return True
    a, b = skeleton(reference), skeleton(heard)
    if a == b:
        return True
    if mode == FUZZY and len(a) >= FUZZY_MIN_LENGTH and len(b) >= FUZZY_MIN_LENGTH and edit_distance(a, b) <= 1:
        return True
    return False


# Verdicts of a reference word.
OK, MISSED, REPLACED = "ok", "missed", "replaced"


def align(reference, heard, mode):
    """Word alignment by edit distance. Returns (verdicts, heard_for, extras):
    verdicts[i]    ok / missed / replaced for reference word i;
    heard_for[i]   the heard word of a replaced word;
    extras         [(gap, heard word)]: a heard word that has no partner, gap = how many reference words come before it.

    Besides the one-to-one steps there are two free ones, because the model often leaves out the space (and the speech of one ayah runs
    into the next): two words of the text for one heard word (``merge``) and one word of the text for two heard words (``split``).
    They need the SAME skeleton (no letter of tolerance): with it a short word would be swallowed by its neighbour."""
    n, m = len(reference), len(heard)
    inf = 10 ** 9
    cost = [[inf] * (m + 1) for _ in range(n + 1)]
    cost[0][0] = 0
    for i in range(n + 1):
        for j in range(m + 1):
            if i == 0 and j == 0:
                continue
            best = inf
            if i > 0 and j > 0:
                best = cost[i - 1][j - 1] + (0 if same_word(reference[i - 1], heard[j - 1], mode) else 1)
            if i > 1 and j > 0 and same_word(reference[i - 2] + reference[i - 1], heard[j - 1], STRICT):
                best = min(best, cost[i - 2][j - 1])
            if i > 0 and j > 1 and same_word(reference[i - 1], heard[j - 2] + heard[j - 1], STRICT):
                best = min(best, cost[i - 1][j - 2])
            if i > 0:
                best = min(best, cost[i - 1][j] + 1)
            if j > 0:
                best = min(best, cost[i][j - 1] + 1)
            cost[i][j] = best
    verdicts = [MISSED] * n
    heard_for = {}
    extras = []
    i, j = n, m
    while i > 0 or j > 0:
        if i > 0 and j > 0:
            step = 0 if same_word(reference[i - 1], heard[j - 1], mode) else 1
            if cost[i][j] == cost[i - 1][j - 1] + step:
                if step == 0:
                    verdicts[i - 1] = OK
                else:
                    verdicts[i - 1] = REPLACED
                    heard_for[i - 1] = heard[j - 1]
                i -= 1
                j -= 1
                continue
        if i > 1 and j > 0 and same_word(reference[i - 2] + reference[i - 1], heard[j - 1], STRICT) \
                and cost[i][j] == cost[i - 2][j - 1]:
            verdicts[i - 1] = verdicts[i - 2] = OK
            i -= 2
            j -= 1
            continue
        if i > 0 and j > 1 and same_word(reference[i - 1], heard[j - 2] + heard[j - 1], STRICT) \
                and cost[i][j] == cost[i - 1][j - 2]:
            verdicts[i - 1] = OK
            i -= 1
            j -= 2
            continue
        if i > 0 and cost[i][j] == cost[i - 1][j] + 1:
            verdicts[i - 1] = MISSED
            i -= 1
            continue
        extras.append((i, heard[j - 1]))
        j -= 1
    extras.reverse()
    return verdicts, heard_for, extras


def error_count(verdicts, extras):
    return sum(1 for v in verdicts if v != OK) + len(extras)


# -- the Quran text and the similar ayahs -------------------------------------------------------------------------------------------

def load_quran():
    ayahs = {}  # (surah, ayah) -> (global number, text without the Bismillah of the first ayah)
    with open(QURAN, encoding="utf-8") as handle:
        for line in handle:
            if not line.startswith("A\t"):
                continue
            fields = line.rstrip("\n").split("\t")
            number, surah, ayah, text = int(fields[1]), int(fields[2]), int(fields[3]), fields[-1]
            tokens = text.split()
            if ayah == 1 and 2 <= surah <= 114 and surah != 9 and len(tokens) > 4 and normalize_word(tokens[0]) == "بسم":
                tokens = tokens[4:]
            ayahs[(surah, ayah)] = (number, " ".join(tokens))
    return ayahs


def load_similar():
    pairs = defaultdict(list)  # (surah, ayah) -> [(surah, ayah)] best first
    with open(SIMILAR, encoding="utf-8") as handle:
        for line in handle:
            if not line.startswith("P\t"):
                continue
            fields = line.rstrip("\n").split("\t")
            key = tuple(int(x) for x in fields[1].split(":"))
            for item in fields[2:]:
                other = item.split("=")[0]
                pairs[key].append(tuple(int(x) for x in other.split(":")))
    return pairs


# -- prepare ------------------------------------------------------------------------------------------------------------------

def pick_passages(quran, count, seed):
    rng = random.Random(seed)
    last = {}
    for (surah, ayah) in quran:
        last[surah] = max(last.get(surah, 0), ayah)
    passages = []
    for group, surahs, share in (("amma", list(AMMA_RANGE), count // 2), ("long", LONG_SURAHS, count - count // 2)):
        made = 0
        guard = 0
        seen = set()
        while made < share and guard < 10000:
            guard += 1
            surah = rng.choice(surahs)
            size = rng.choice([3, 3, 4, 4, 5])
            if last[surah] < size + 2:
                size = max(2, last[surah] - 2)
            first = rng.randint(2, max(2, last[surah] - size + 1))  # never the first ayah: its Bismillah is a different story
            keys = [(surah, a) for a in range(first, min(first + size, last[surah] + 1))]
            if len(keys) < 2 or (surah, first) in seen:
                continue
            words = sum(len(words_of(quran[k][1])) for k in keys)
            if words > MAX_WORDS or words < 8:
                continue
            seen.add((surah, first))
            reciter = RECITERS[(len(passages)) % len(RECITERS)][0]
            passages.append({"id": "p%03d" % len(passages), "group": group, "surah": surah, "first": first,
                             "last": keys[-1][1], "reciter": reciter})
            made += 1
    return passages


def download(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "quran-hifz-voice-eval"})
            with urllib.request.urlopen(request, timeout=60) as response, open(path, "wb") as out:
                out.write(response.read())
            return
        except Exception as error:  # noqa: BLE001 - a network is a network
            time.sleep(1 + attempt)
            last = error
    raise RuntimeError("cannot download %s: %s" % (url, last))


def to_wav(source, target):
    if os.path.exists(target) and os.path.getsize(target) > 0:
        return
    subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", source, target], check=True)


def join_wavs(parts, target, gap_seconds=0.25):
    """The ayahs of a passage in one file; a short pause between them (a reciter breathes)."""
    frames = []
    for part in parts:
        with wave.open(part, "rb") as handle:
            assert handle.getframerate() == 16000 and handle.getnchannels() == 1 and handle.getsampwidth() == 2
            frames.append(handle.readframes(handle.getnframes()))
    silence = b"\x00\x00" * int(16000 * gap_seconds)
    with wave.open(target, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(16000)
        out.writeframes(silence.join(frames))
    return sum(len(f) for f in frames) / 2 / 16000


def prepare(args):
    quran = load_quran()
    passages = pick_passages(quran, args.count, args.seed)
    audio = os.path.join(args.work, "audio")
    os.makedirs(audio, exist_ok=True)
    reciters = {name: (edition, bitrate) for name, edition, bitrate in RECITERS}

    def fetch(item):
        reciter, number = item
        edition, bitrate = reciters[reciter]
        mp3 = os.path.join(audio, "%s_%d.mp3" % (reciter, number))
        wav = os.path.join(audio, "%s_%d.wav" % (reciter, number))
        download("https://cdn.islamic.network/quran/audio/%d/%s/%d.mp3" % (bitrate, edition, number), mp3)
        to_wav(mp3, wav)

    wanted = sorted({(p["reciter"], quran[(p["surah"], a)][0]) for p in passages for a in range(p["first"], p["last"] + 1)})
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(fetch, wanted))
    for index, passage in enumerate(passages):
        parts = []
        for ayah in range(passage["first"], passage["last"] + 1):
            number = quran[(passage["surah"], ayah)][0]
            parts.append(os.path.join(audio, "%s_%d.wav" % (passage["reciter"], number)))
        target = os.path.join(audio, passage["id"] + ".wav")
        passage["seconds"] = round(join_wavs(parts, target), 2)
        passage["wav"] = target
        print("prepared %s %s %d:%d-%d %.1fs" % (passage["id"], passage["reciter"], passage["surah"], passage["first"],
                                                  passage["last"], passage["seconds"]), flush=True)
    with open(os.path.join(args.work, "passages.json"), "w", encoding="utf-8") as handle:
        json.dump(passages, handle, ensure_ascii=False, indent=1)


# -- cutting at silences (what the app does before the model: the model is trained on single ayahs and runs ayahs together) -----------

FRAME = 320  # 20 ms at 16 kHz
MIN_SILENCE_FRAMES = 12  # 240 ms
MIN_CHUNK_SECONDS = 2.0


def split_at_silences(path, out_prefix):
    """Cuts the wav at pauses (energy below 3% of the loud part, 240 ms or longer); pieces shorter than 2 s are joined to the next one.
    The same rules as common/voice/SilenceSplitter.kt. Returns the paths of the pieces."""
    import array
    with wave.open(path, "rb") as handle:
        raw = handle.readframes(handle.getnframes())
    samples = array.array("h")
    samples.frombytes(raw)
    frames = len(samples) // FRAME
    rms = [(sum(x * x for x in samples[i * FRAME:(i + 1) * FRAME]) / FRAME) ** 0.5 for i in range(frames)]
    loud = sorted(rms)[int(frames * 0.9)] if frames else 0
    quiet = [r < 0.03 * loud for r in rms]
    # pauses: [start, end) in frames
    pauses = []
    i = 0
    while i < frames:
        if quiet[i]:
            j = i
            while j < frames and quiet[j]:
                j += 1
            if j - i >= MIN_SILENCE_FRAMES and i > 0 and j < frames:
                pauses.append((i, j))
            i = j
        else:
            i += 1
    cuts = [0] + [(a + b) // 2 for a, b in pauses] + [frames]
    pieces = []
    start = cuts[0]
    for k in range(1, len(cuts)):
        if (cuts[k] - start) * FRAME / 16000 >= MIN_CHUNK_SECONDS or k == len(cuts) - 1:
            pieces.append([start, cuts[k]])
            start = cuts[k]
    if len(pieces) > 1 and (pieces[-1][1] - pieces[-1][0]) * FRAME / 16000 < MIN_CHUNK_SECONDS:
        last = pieces.pop()
        pieces[-1][1] = last[1]
    result = []
    for k, (a, b) in enumerate(pieces):
        target = "%s_%02d.wav" % (out_prefix, k)
        with wave.open(target, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(16000)
            out.writeframes(samples[a * FRAME:b * FRAME].tobytes())
        result.append(target)
    return result


def add_noise(path, target, snr_db, seed):
    """A copy of the wav with white noise at the given signal-to-noise ratio (a quiet room is ~30 dB, a street 10-15 dB): the
    reciters were recorded in a studio, a person at home was not."""
    import array
    rng = random.Random(seed)
    with wave.open(path, "rb") as handle:
        raw = handle.readframes(handle.getnframes())
    samples = array.array("h")
    samples.frombytes(raw)
    power = sum(x * x for x in samples) / max(1, len(samples))
    sigma = (power / (10 ** (snr_db / 10))) ** 0.5
    gauss = rng.gauss
    noisy = array.array("h", [max(-32768, min(32767, int(x + gauss(0, sigma)))) for x in samples])
    with wave.open(target, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(16000)
        out.writeframes(noisy.tobytes())


# -- recognize ----------------------------------------------------------------------------------------------------------------

def recognize(args):
    with open(os.path.join(args.work, "passages.json"), encoding="utf-8") as handle:
        passages = json.load(handle)
    results = os.path.join(args.work, "results")
    os.makedirs(results, exist_ok=True)
    for spec in args.model:
        name, path = spec.split("=", 1)
        out_path = os.path.join(results, "hyp_%s%s%s.json" % (name, "+split" if args.split else "",
                                                              "+n%d" % args.noise if args.noise else ""))
        hyp = {}
        if os.path.exists(out_path):
            with open(out_path, encoding="utf-8") as handle:
                hyp = json.load(handle)
        for index, passage in enumerate(passages):
            if passage["id"] in hyp:
                continue
            if args.noise and index % 2:
                continue  # noise is slow to make: every second passage
            if args.noise:
                noisy = os.path.join(args.work, "audio", passage["id"] + "_n%d.wav" % args.noise)
                if not os.path.exists(noisy):
                    add_noise(passage["wav"], noisy, args.noise, passage["id"])
                passage = dict(passage, wav=noisy)
            base = [args.cli, "-m", path, "-l", "ar", "-nt", "-t", str(args.threads), "-bs", "1", "-bo", "1", "-mc", "0"]
            if args.prompt:
                base += ["--prompt", args.prompt]
            if args.split:
                chunks_dir = os.path.join(args.work, "chunks")
                os.makedirs(chunks_dir, exist_ok=True)
                files = split_at_silences(passage["wav"], os.path.join(chunks_dir, passage["id"] + ("_n%d" % args.noise if args.noise else "")))
            else:
                files = [passage["wav"]]
            texts = []
            wall = 0.0
            for wav in files:
                started = time.time()
                run = subprocess.run(base + ["-f", wav], capture_output=True, text=True, encoding="utf-8", errors="replace")
                wall += time.time() - started
                texts.append(" ".join(run.stdout.split()))
            text = " ".join(texts)
            hyp[passage["id"]] = {"text": text, "wall": round(wall, 2), "chunks": len(files)}
            print("%s %s %.1fs/%.1fs chunks=%d  %s" % (name, passage["id"], wall, passage["seconds"], len(files), text[:60]), flush=True)
            with open(out_path, "w", encoding="utf-8") as handle:
                json.dump(hyp, handle, ensure_ascii=False, indent=1)


# -- the damage -------------------------------------------------------------------------------------------------------------------

def neighbour_words(quran, passage, rng, mode):
    """Words of the next and the previous ayah (not of the passage): a word that is not in the audio."""
    result = []
    for ayah in (passage["last"] + 1, passage["first"] - 1):
        if (passage["surah"], ayah) in quran:
            result += words_of(quran[(passage["surah"], ayah)][1])
    own = [w for a in range(passage["first"], passage["last"] + 1) for w in words_of(quran[(passage["surah"], a)][1])]
    return [w for w in result if len(skeleton(w)) >= 2 and not any(same_word(w, o, FUZZY) for o in own)]


def make_missed(reference, passage, quran, rng):
    pool = neighbour_words(quran, passage, rng, STRICT)
    if not pool or len(reference) < 4:
        return None
    position = rng.randint(1, len(reference) - 1)  # not before the first word
    word = rng.choice(pool)
    damaged = reference[:position] + [word] + reference[position:]
    return damaged, position


def similar_substitutions(passage, quran, similar):
    """(position in the passage, the similar word) from the similar ayahs of F6: words that stand in the same place in a similar ayah."""
    options = []
    offset = 0
    for ayah in range(passage["first"], passage["last"] + 1):
        mine = words_of(quran[(passage["surah"], ayah)][1])
        for other in similar.get((passage["surah"], ayah), [])[:3]:
            theirs = words_of(quran[other][1])
            matcher = difflib.SequenceMatcher(None, mine, theirs, autojunk=False)
            for tag, i1, i2, j1, j2 in matcher.get_opcodes():
                if tag == "replace" and i2 - i1 == 1 and j2 - j1 == 1:
                    options.append((offset + i1, theirs[j1]))
        offset += len(mine)
    return options


def make_replaced(reference, passage, quran, similar, rng):
    options = [(p, w) for p, w in similar_substitutions(passage, quran, similar)
               if len(skeleton(reference[p])) >= 2 and not same_word(reference[p], w, FUZZY)]
    source = "similar"
    if options:
        position, word = rng.choice(options)
    else:
        # No similar ayah in this passage: a word of the neighbouring ayah in the place of one word.
        pool = neighbour_words(quran, passage, rng, STRICT)
        candidates = [i for i, w in enumerate(reference) if len(skeleton(w)) >= 2]
        if not pool or not candidates:
            return None
        position, word, source = rng.choice(candidates), rng.choice(pool), "neighbour"
    damaged = list(reference)
    damaged[position] = word
    return damaged, position, source


def make_extra(reference, rng):
    candidates = [i for i, w in enumerate(reference) if len(skeleton(w)) >= 2 and 0 < i < len(reference) - 1]
    if len(reference) < 5 or not candidates:
        return None
    position = rng.choice(candidates)
    return reference[:position] + reference[position + 1:], position, reference[position]


# -- score ----------------------------------------------------------------------------------------------------------------------

def score(args):
    quran = load_quran()
    similar = load_similar()
    with open(os.path.join(args.work, "passages.json"), encoding="utf-8") as handle:
        passages = json.load(handle)
    results_dir = os.path.join(args.work, "results")
    report = []

    def say(line=""):
        print(line)
        report.append(line)

    for spec in args.model:
        name = spec.split("=", 1)[0]
        with open(os.path.join(results_dir, "hyp_%s.json" % name), encoding="utf-8") as handle:
            hyp = json.load(handle)
        for mode in (STRICT, FUZZY):
            stats = defaultdict(lambda: defaultdict(list))  # slice -> metric -> values
            details = []
            for passage in passages:
                if passage["id"] not in hyp:
                    continue
                rng = random.Random("%s-%s" % (args.seed, passage["id"]))  # the same damage for both models and both modes
                reference = [w for a in range(passage["first"], passage["last"] + 1) for w in words_of(quran[(passage["surah"], a)][1])]
                heard = words_of(hyp[passage["id"]]["text"])
                slices = ["all", passage["group"], passage["reciter"], "%s/%s" % (passage["group"], passage["reciter"])]

                def record(metric, value):
                    for s in slices:
                        stats[s][metric].append(value)

                verdicts, _, extras = align(reference, heard, mode)
                record("false", error_count(verdicts, extras))
                record("wer", error_count(verdicts, extras) / max(1, len(reference)))
                record("rtf", hyp[passage["id"]]["wall"] / passage["seconds"])
                record("seconds", passage["seconds"])

                made = make_missed(reference, passage, quran, rng)
                if made:
                    damaged, position = made
                    v, _, _ = align(damaged, heard, mode)
                    found = v[position] == MISSED
                    record("missed", 1 if found else 0)
                    record("anyerr_missed", 1 if v[position] != OK else 0)
                    if not found:
                        details.append((passage["id"], "missed", damaged[position], v[position]))
                made = make_replaced(reference, passage, quran, similar, rng)
                if made:
                    damaged, position, source = made
                    v, h, _ = align(damaged, heard, mode)
                    found = v[position] == REPLACED
                    record("replaced", 1 if found else 0)
                    record("anyerr_replaced", 1 if v[position] != OK else 0)
                    record("replaced_" + source, 1 if found else 0)
                    if not found:
                        details.append((passage["id"], "replaced/" + source, damaged[position], v[position]))
                made = make_extra(reference, rng)
                if made:
                    damaged, position, word = made
                    v, _, e = align(damaged, heard, mode)
                    found = any(w == word or same_word(word, w, mode) for gap, w in e if abs(gap - position) <= 1)
                    record("extra", 1 if found else 0)
                    if not found:
                        details.append((passage["id"], "extra", word, "-"))

            say("")
            say("### model %s, comparison %s" % (name, mode))
            say("")
            say("| slice | passages | seconds (mean) | missed found | replaced found | extra found | false errors / passage | clean passages | word error rate | time / audio (Mac CPU) |")
            say("|---|---|---|---|---|---|---|---|---|---|")
            order = ["all", "amma", "long"] + [r[0] for r in RECITERS] + sorted(s for s in stats if "/" in s)
            for s in order:
                if s not in stats:
                    continue
                m = stats[s]

                def pct(key):
                    values = m.get(key, [])
                    return "%.0f%% (%d/%d)" % (100 * sum(values) / len(values), sum(values), len(values)) if values else "-"

                falses = m["false"]
                say("| %s | %d | %.0f | %s | %s | %s | %.2f | %d/%d | %.1f%% | %.2f |" % (
                    s, len(falses), statistics.mean(m["seconds"]), pct("missed"), pct("replaced"), pct("extra"),
                    statistics.mean(falses), sum(1 for f in falses if f == 0), len(falses),
                    100 * statistics.mean(m["wer"]), statistics.mean(m["rtf"])))
            if args.verbose:
                say("")
                say("misses (%s):" % mode)
                for item in details[:60]:
                    say("  %s %s %s -> %s" % item)
            say("")
            say("replaced by source: " + ", ".join("%s %d/%d" % (key[9:], sum(v), len(v))
                                                   for key, v in sorted(stats["all"].items()) if key.startswith("replaced_")))
            say("flagged as ANY error at the damaged place (right kind or not): " + ", ".join(
                "%s %d/%d" % (key[7:], sum(v), len(v)) for key, v in sorted(stats["all"].items()) if key.startswith("anyerr_")))
    with open(os.path.join(results_dir, "report.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(report) + "\n")


# -- real audio with a real error: words cut out of, and pasted into, the recitation ------------------------------------------------
#
# The test above damages the TEXT. It cannot show the danger that matters most for a model trained on the Quran: that it "hears" the
# word that is supposed to be there (a language-model habit) and so never reports a mistake. Here the audio is damaged instead: with the
# word timings of quran.com the audio of a word is replaced by the audio of another word (replace), cut out (omit) or a foreign word is
# put in between two words (insert). The text stays the true text of the passage (3 ayahs, the damage is in the middle one).

QURAN_COM = "https://verses.quran.com/Alafasy/mp3/%03d%03d.mp3"
SEGMENTS = "https://api.quran.com/api/v4/recitations/7/by_ayah/%d:%d?fields=segments"


def read_wav(path):
    with wave.open(path, "rb") as handle:
        return handle.readframes(handle.getnframes())


def fetch_ayah(work, surah, ayah):
    """(pcm bytes at 16 kHz mono, [(start_ms, end_ms)] per word) of an ayah of Alafasy from quran.com."""
    folder = os.path.join(work, "splice")
    os.makedirs(folder, exist_ok=True)
    base = "%s/%03d%03d" % (folder, surah, ayah)
    download(QURAN_COM % (surah, ayah), base + ".mp3")
    to_wav(base + ".mp3", base + ".wav")
    meta = base + ".json"
    if not os.path.exists(meta):
        request = urllib.request.Request(SEGMENTS % (surah, ayah), headers={"User-Agent": "quran-hifz-voice-eval"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read())
        with open(meta, "w") as handle:
            json.dump(data["audio_files"][0]["segments"], handle)
    with open(meta) as handle:
        segments = json.load(handle)
    return read_wav(base + ".wav"), [(seg[2], seg[3]) for seg in segments]


def ms(milliseconds):
    return int(16 * milliseconds) * 2  # byte offset in 16 kHz 16 bit mono


def splice(args):
    quran = load_quran()
    rng = random.Random(args.seed)
    last = {}
    for (surah, ayah) in quran:
        last[surah] = max(last.get(surah, 0), ayah)
    cases = []
    folder = os.path.join(args.work, "splice")
    os.makedirs(folder, exist_ok=True)
    wanted = args.count
    attempts = 0
    while len(cases) < wanted * 3 and attempts < 2000:
        attempts += 1
        group = "amma" if len(cases) // 3 % 2 == 0 else "long"
        surah = rng.choice(list(AMMA_RANGE) if group == "amma" else LONG_SURAHS)
        if last[surah] < 5:
            continue
        middle = rng.randint(3, last[surah] - 1)
        try:
            audio = {a: fetch_ayah(args.work, surah, a) for a in (middle - 1, middle, middle + 1, middle - 2)}
        except Exception as error:  # noqa: BLE001
            print("skip %d:%d %s" % (surah, middle, error))
            continue
        words = words_of(quran[(surah, middle)][1])
        pcm, spans = audio[middle]
        if len(spans) != len(words) or len(words) < 5:
            continue
        donor_pcm, donor_spans = audio[middle - 2]
        donor_words = words_of(quran[(surah, middle - 2)][1])
        if len(donor_spans) != len(donor_words):
            continue
        k = rng.randint(1, len(words) - 3)
        start, end = spans[k]
        # a donor word that is a different word and not much longer than the one it replaces
        donors = [i for i, w in enumerate(donor_words) if not same_word(w, words[k], FUZZY) and len(skeleton(w)) >= 2
                  and 200 <= donor_spans[i][1] - donor_spans[i][0] <= max(600, 1.6 * (end - start))]
        if not donors:
            continue
        d = rng.choice(donors)
        piece = donor_pcm[ms(donor_spans[d][0]):ms(donor_spans[d][1])]
        head, tail = pcm[:ms(start)], pcm[ms(end):]
        variants = {
            "replace": head + piece + tail,
            "omit": head + tail,
            "insert": head + pcm[ms(start):ms(end)] + piece + tail,
        }
        before, after = audio[middle - 1][0], audio[middle + 1][0]
        reference = (words_of(quran[(surah, middle - 1)][1]) + words + words_of(quran[(surah, middle + 1)][1]))
        offset = len(words_of(quran[(surah, middle - 1)][1]))
        gap = b"\x00\x00" * 4000
        for kind, damaged in variants.items():
            wav = "%s/c%03d_%s.wav" % (folder, len(cases) // 3, kind)
            with wave.open(wav, "wb") as out:
                out.setnchannels(1)
                out.setsampwidth(2)
                out.setframerate(16000)
                out.writeframes(gap.join([before, damaged, after]))
            cases.append({"id": "c%03d_%s" % (len(cases) // 3, kind), "kind": kind, "group": group, "surah": surah, "ayah": middle,
                          "position": offset + k, "donor": donor_words[d], "original": words[k], "wav": wav, "reference": reference})
        # the control: the same three ayahs untouched
        if len(cases) % 3 == 0:
            pass
    with open(os.path.join(folder, "cases.json"), "w", encoding="utf-8") as handle:
        json.dump(cases, handle, ensure_ascii=False, indent=1)
    print("%d cases" % len(cases))
    results = {}
    for spec in args.model:
        name, path = spec.split("=", 1)
        hyp = {}
        for case in cases:
            files = split_at_silences(case["wav"], case["wav"][:-4] + "_p") if args.split else [case["wav"]]
            texts = []
            for wav in files:
                run = subprocess.run([args.cli, "-m", path, "-f", wav, "-l", "ar", "-nt", "-t", str(args.threads), "-bs", "1", "-bo", "1", "-mc", "0"],
                                     capture_output=True, text=True, encoding="utf-8", errors="replace")
                texts.append(" ".join(run.stdout.split()))
            hyp[case["id"]] = " ".join(texts)
        results[name] = hyp
        with open(os.path.join(folder, "hyp_%s%s.json" % (name, "+split" if args.split else "")), "w", encoding="utf-8") as handle:
            json.dump(hyp, handle, ensure_ascii=False, indent=1)
    report = []
    for name, hyp in results.items():
        for mode in (STRICT, FUZZY):
            stat = defaultdict(lambda: defaultdict(list))
            for case in cases:
                heard = words_of(hyp[case["id"]])
                v, h, extras = align(case["reference"], heard, mode)
                k = case["position"]
                if case["kind"] == "replace":
                    found = v[k] != OK
                    exact = v[k] == REPLACED
                elif case["kind"] == "omit":
                    found = v[k] != OK
                    exact = v[k] == MISSED
                else:
                    found = any(k <= g <= k + 2 for g, _ in extras) or v[k] != OK or v[min(k + 1, len(v) - 1)] != OK
                    exact = any(k <= g <= k + 2 for g, _ in extras)
                # false errors: everything flagged that is not at the damaged place (3 words around it are forgiven)
                others = sum(1 for i, x in enumerate(v) if x != OK and abs(i - k) > 1) + sum(1 for g, _ in extras if abs(g - k) > 2)
                for key in ("all", case["group"]):
                    stat[key][case["kind"] + "_found"].append(1 if found else 0)
                    stat[key][case["kind"] + "_exact"].append(1 if exact else 0)
                    stat[key]["false_" + case["kind"]].append(others)
            report.append("")
            report.append("### real audio, damaged in the audio: model %s, comparison %s, %s" % (name, mode, "split at pauses" if args.split else "whole"))
            report.append("")
            report.append("| slice | cases | replace found (right kind) | omit found (right kind) | insert found (right kind) | false errors / passage |")
            report.append("|---|---|---|---|---|---|")
            for key in ("all", "amma", "long"):
                m = stat[key]
                if not m:
                    continue

                def pc(kind):
                    f, e = m[kind + "_found"], m[kind + "_exact"]
                    return "%d%% (%d/%d), %d%%" % (100 * sum(f) / len(f), sum(f), len(f), 100 * sum(e) / len(e))

                falses = m["false_replace"] + m["false_omit"] + m["false_insert"]
                report.append("| %s | %d | %s | %s | %s | %.2f |" % (key, len(m["replace_found"]), pc("replace"), pc("omit"), pc("insert"),
                                                                     statistics.mean(falses)))
    print("\n".join(report))
    with open(os.path.join(folder, "report.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(report) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=["prepare", "recognize", "score", "all", "splice"])
    parser.add_argument("--work", required=True, help="a directory outside the repository: audio, hypotheses, report")
    parser.add_argument("--cli", help="whisper.cpp whisper-cli")
    parser.add_argument("--model", action="append", default=[], help="name=path of a ggml model (several)")
    parser.add_argument("--count", type=int, default=100, help="passages (splice: damaged ayahs, 3 variants each)")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--prompt", default="")
    parser.add_argument("--noise", type=int, default=0, help="add white noise at this SNR in dB (every second passage only)")
    parser.add_argument("--split", action="store_true", help="cut the passage at pauses and recognize the pieces one by one")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)
    if args.stage == "splice":
        splice(args)
        return
    if args.stage in ("prepare", "all"):
        prepare(args)
    if args.stage in ("recognize", "all"):
        recognize(args)
    if args.stage in ("score", "all"):
        score(args)


if __name__ == "__main__":
    sys.exit(main())
