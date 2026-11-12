"""Hisnlabs tajweed-v2 (phoneme CTC) on the passages of voice_eval.py, the same damage test, word verdicts in phoneme space.
  voice_eval_hisn.py hear  <work> <model-int8.onnx> <config.json> scripts/voice_eval.py     -> <work>/results/hisn_heard.json
  voice_eval_hisn.py score <work> scripts/voice_eval.py                                     -> tables

Run `voice_eval.py prepare --work <work>` first. Needs: pip install numpy onnxruntime faster-whisper quran-transcript levenshtein.
The word rule here is the first draft of 2026-11-10 (Levenshtein.editops, the phonetizer run on the damaged text); the rule of the
app is `voice_phonetic_ref.py` (its own alignment with fixed tie-breaking), which gives 0.23 false errors a passage on the same audio.
"""
import importlib.util, json, os, random, statistics, sys, time, wave
from collections import defaultdict
import numpy as np

stage, work = sys.argv[1], sys.argv[2]
eval_path = sys.argv[-1]
spec = importlib.util.spec_from_file_location("voice_eval", eval_path); ve = importlib.util.module_from_spec(spec); spec.loader.exec_module(ve)
passages = json.load(open(os.path.join(work, "passages.json"), encoding="utf-8"))
HEARD = os.path.join(work, "results", "hisn_heard.json")

if stage == "hear":
    import onnxruntime as ort
    from faster_whisper.feature_extractor import FeatureExtractor
    meta = json.load(open(sys.argv[4], encoding="utf-8"))
    opts = ort.SessionOptions(); opts.intra_op_num_threads = 4
    sess = ort.InferenceSession(sys.argv[3], opts, providers=["CPUExecutionProvider"])
    fe = FeatureExtractor(feature_size=80)
    LEAD = int(16000 * 0.4)

    def hear(x):
        x = np.concatenate([np.zeros(LEAD, np.float32), x, np.zeros(LEAD, np.float32)])
        mel = fe(x, padding=0); mel = mel[:, : min(3000, mel.shape[1] // 2 * 2)]
        lp = sess.run(None, {"mel": mel[None].astype(np.float32), "riwaya": np.array([0], np.int64)})[0][0]
        out, prev = [], 0
        for i in lp.argmax(-1):
            if i != prev and i != 0: out.append(meta["phonemes"][i - 1])
            prev = i
        return "".join(out)

    def read(path):
        with wave.open(path) as f:
            return np.frombuffer(f.readframes(f.getnframes()), "<i2").astype(np.float32) / 32768

    os.makedirs(os.path.join(work, "chunks"), exist_ok=True)
    result = {}
    for p in passages:
        files = ve.split_at_silences(p["wav"], os.path.join(work, "chunks", p["id"]))
        parts, wall, longest = [], 0.0, 0.0
        for f in files:
            x = read(f); longest = max(longest, len(x) / 16000)
            for k in range(0, len(x), 28 * 16000):  # the model takes 30 s at the most
                t = time.time(); parts.append(hear(x[k:k + 28 * 16000])); wall += time.time() - t
        result[p["id"]] = {"parts": parts, "wall": round(wall, 2), "chunks": len(files), "longest": round(longest, 1)}
        print(p["id"], "%.2f/%.1f" % (wall, p["seconds"]), len(files), " ".join(parts)[:80], flush=True)
    json.dump(result, open(HEARD, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    sys.exit()

import Levenshtein
from quran_transcript import Aya, quran_phonetizer, MoshafAttributes
MOSHAF = MoshafAttributes(rewaya="hafs", madd_monfasel_len=4, madd_mottasel_len=4, madd_mottasel_waqf=4, madd_aared_len=4)
quran = ve.load_quran(); similar = ve.load_similar()
heard_all = json.load(open(HEARD, encoding="utf-8"))

raw_cache = {}
def raw_words(key):
    """the phonetizer's Uthmani words of an ayah, one per word of the app's text (None: the two texts split it differently)"""
    if key not in raw_cache:
        raw = Aya(key[0], key[1]).get().uthmani.split(" ")
        raw = [w for w in raw if ve.normalize_word(w)]
        raw_cache[key] = raw if len(raw) == len(ve.words_of(quran[key][1])) else None
    return raw_cache[key]

def collapse(text, spans=None):
    out, pos = [], []
    for c in text:
        if not out or out[-1] != c: out.append(c)
        pos.append(len(out) - 1)
    if spans is None: return "".join(out)
    return "".join(out), [(pos[s], pos[e - 1] + 1) if e > s else (pos[s] if s < len(pos) else len(out),) * 2 for s, e in spans]

ph_cache = {}
def phonetize(words):
    key = tuple(words)
    if key in ph_cache: return ph_cache[key]
    o = quran_phonetizer(" ".join(words), MOSHAF, remove_spaces=True)
    spans, pos = [], 0
    for w in words:
        ps = [m.pos for m in o.mappings[pos:pos + len(w)] if m.pos[1] > m.pos[0]]
        spans.append((min(p[0] for p in ps), max(p[1] for p in ps)) if ps else (spans[-1][1] if spans else 0,) * 2)
        pos += len(w) + 1
    ph_cache[key] = (o.phonemes, spans)
    return ph_cache[key]

def expected(items):
    """items: [(raw word, ayah)] -> the collapsed phonetic script and the span of every word; every ayah ends with a stop"""
    text, spans, i = "", [], 0
    while i < len(items):
        j = i
        while j < len(items) and items[j][1] == items[i][1]: j += 1
        ph, sp = phonetize([w for w, _ in items[i:j]])
        spans += [(a + len(text), b + len(text)) for a, b in sp]; text += ph; i = j
    return collapse(text, spans)

def judge(items, heard, thr, extra_min):
    """-> verdicts per word, extras [(gap, length)]"""
    exp, spans = expected(items)
    n = len(exp)
    bad = [0] * n          # 1 substituted, 2 deleted
    ins = [0] * (n + 1)    # heard sounds inserted before expected position k
    for tag, i, j in Levenshtein.editops(exp, heard):
        if tag == "replace": bad[i] = 1
        elif tag == "delete": bad[i] = 2
        else: ins[i] += 1
    verdicts, extras, used = [], [], set()
    for w, (s, e) in enumerate(spans):
        length = max(1, e - s)
        subs = sum(1 for k in range(s, e) if bad[k] == 1); dels = sum(1 for k in range(s, e) if bad[k] == 2)
        inside = sum(ins[k] for k in range(s + 1, e))
        if (subs + dels + inside) / length < thr:
            verdicts.append(ve.OK); continue
        around = ins[s] + inside + (ins[e] if e <= n else 0)
        if dels >= 0.6 * length and around < 2:
            verdicts.append(ve.MISSED)
        else:
            verdicts.append(ve.REPLACED); used.update((s, e))
    starts = {s: w for w, (s, e) in reversed(list(enumerate(spans)))}
    for k in range(n + 1):
        if ins[k] >= extra_min and k not in used and (k in starts or k == n):
            extras.append((starts.get(k, len(spans)), ins[k]))
    return verdicts, extras

def run(thr, extra_min):
    stats = defaultdict(lambda: defaultdict(list)); skipped = 0; failed = 0
    for p in passages:
        keys = [(p["surah"], a) for a in range(p["first"], p["last"] + 1)]
        if any(raw_words(k) is None for k in keys): skipped += 1; continue
        rng = random.Random("%s-%s" % (7, p["id"]))
        reference = [w for k in keys for w in ve.words_of(quran[k][1])]
        items = [(w, k) for k in keys for w in raw_words(k)]
        heard = collapse("".join(heard_all[p["id"]]["parts"]))
        to_raw = {}
        for k in list(quran):
            if k[0] == p["surah"] and abs(k[1] - p["first"]) < 12 and raw_words(k):
                to_raw.update(zip(ve.words_of(quran[k][1]), raw_words(k)))
        for k in keys:
            for other in similar.get(k, [])[:3]:
                if raw_words(other): to_raw.update(zip(ve.words_of(quran[other][1]), raw_words(other)))
        slices = ["all", p["group"], p["reciter"]]
        def record(metric, value):
            for s in slices: stats[s][metric].append(value)
        v, ex = judge(items, heard, thr, extra_min)
        record("false", sum(1 for x in v if x != ve.OK) + len(ex)); record("words", len(v))
        record("rtf", heard_all[p["id"]]["wall"] / p["seconds"])
        try:
            made = ve.make_missed(reference, p, quran, rng)
            if made and made[0][made[1]] in to_raw:
                damaged, pos = made
                d = items[:pos] + [(to_raw[damaged[pos]], items[min(pos, len(items) - 1)][1])] + items[pos:]
                v, _ = judge(d, heard, thr, extra_min)
                record("missed", int(v[pos] == ve.MISSED)); record("any_missed", int(v[pos] != ve.OK))
            made = ve.make_replaced(reference, p, quran, similar, rng)
            if made and made[0][made[1]] in to_raw:
                damaged, pos, source = made
                d = list(items); d[pos] = (to_raw[damaged[pos]], items[pos][1])
                v, _ = judge(d, heard, thr, extra_min)
                record("replaced", int(v[pos] == ve.REPLACED)); record("any_replaced", int(v[pos] != ve.OK))
            made = ve.make_extra(reference, rng)
            if made:
                damaged, pos, word = made
                d = items[:pos] + items[pos + 1:]
                v, ex = judge(d, heard, thr, extra_min)
                near = any(abs(g - pos) <= 1 for g, _ in ex) or any(v[k] != ve.OK for k in (pos - 1, pos) if 0 <= k < len(v))
                record("extra", int(any(abs(g - pos) <= 1 for g, _ in ex))); record("any_extra", int(near))
        except Exception as error:  # the phonetizer on a text that is not the Quran's
            failed += 1; print("  phonetizer failed on", p["id"], type(error).__name__, str(error)[:80])
    print("\n### hisn, a word is wrong from %.0f%% of its sounds, an extra word from %d sounds (skipped %d passages, phonetizer failed on %d)" % (100 * thr, extra_min, skipped, failed))
    print("| slice | passages | missed found (exact kind / any error) | replaced found | extra found | false errors / passage | clean passages | false per 100 words | time / audio |")
    print("|---|---|---|---|---|---|---|---|---|")
    for s in ["all", "amma", "long", "alafasy", "husary", "abdulbasit"]:
        m = stats[s]
        def pct(a):
            x, y = m.get(a, []), m.get("any_" + a, [])
            return "%.0f%% / %.0f%% (n=%d)" % (100 * sum(x) / len(x), 100 * sum(y) / len(y), len(x)) if x else "-"
        f = m["false"]
        print("| %s | %d | %s | %s | %s | %.2f | %d/%d | %.1f | %.3f |" % (s, len(f), pct("missed"), pct("replaced"), pct("extra"),
              statistics.mean(f), sum(1 for x in f if x == 0), len(f), 100 * sum(f) / sum(m["words"]), statistics.mean(m["rtf"])))

for thr, extra_min in ((0.5, 3), (0.4, 3), (0.34, 2)):
    run(thr, extra_min)
