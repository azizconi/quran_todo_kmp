#!/usr/bin/env python3
"""Builds what the phonetic voice check (task F7, prototype) reads on the phone, and the cases of its Kotlin tests.

  asset   <out dir>            quran-phonetics.tsv (every ayah: the collapsed phonetic script of each word, the ayah read alone and
                               ended with a stop) and mel-80.bin (Whisper's 80 mel filters, float32 LE, 80 x 201)
  golden  <out dir> <work>     phonetic_cases.json (judge / follow on real heard scripts of `voice_eval_hisn.py hear`) and
                               mel_case.wav + mel_case.bin (a clip and its log-mel as the model wants it)

Needs: pip install numpy faster-whisper quran-transcript (the phonetizer of obadx, MIT). The words are those of the app's text
(`quran-uthmani.tsv` without the Bismillah of a first ayah); an ayah whose words the two texts split differently gets "!".
"""
import json, os, random, sys, wave
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voice_eval as ve
import voice_phonetic_ref as ref

LEAD = int(16000 * 0.4)  # the model was trained on clips with silence at both ends


def phonetic_words(key, quran):
    from quran_transcript import Aya, quran_phonetizer, MoshafAttributes
    moshaf = MoshafAttributes(rewaya="hafs", madd_monfasel_len=4, madd_mottasel_len=4, madd_mottasel_waqf=4, madd_aared_len=4)
    raw = [w for w in Aya(key[0], key[1]).get().uthmani.split(" ") if ve.normalize_word(w)]
    if len(raw) != len(ve.words_of(quran[key][1])):
        return None
    o = quran_phonetizer(" ".join(raw), moshaf, remove_spaces=True)
    words, pos = [], 0
    for w in raw:
        spans = [m.pos for m in o.mappings[pos:pos + len(w)] if m.pos[1] > m.pos[0]]
        words.append(ref.collapse(o.phonemes[min(s[0] for s in spans):max(s[1] for s in spans)]) if spans else "-")
        pos += len(w) + 1
    return words


def asset(out):
    from faster_whisper.feature_extractor import FeatureExtractor
    quran = ve.load_quran()
    os.makedirs(out, exist_ok=True)
    bad = 0
    with open(os.path.join(out, "quran-phonetics.tsv"), "w", encoding="utf-8") as handle:
        for key in sorted(quran):
            try:
                words = phonetic_words(key, quran)
            except Exception as error:  # noqa: BLE001
                print("phonetizer failed", key, error)
                words = None
            bad += words is None
            handle.write("%d\t%d\t%s\n" % (key[0], key[1], " ".join(words) if words else "!"))
    FeatureExtractor(feature_size=80).mel_filters.astype("<f4").tofile(os.path.join(out, "mel-80.bin"))
    print("ayahs without a script:", bad, "of", len(quran))


def golden(out, work):
    from faster_whisper.feature_extractor import FeatureExtractor
    quran = ve.load_quran()
    os.makedirs(out, exist_ok=True)
    passages = json.load(open(os.path.join(work, "passages.json"), encoding="utf-8"))
    heard_all = json.load(open(os.path.join(work, "results", "hisn_heard.json"), encoding="utf-8"))
    rng = random.Random(7)
    cases = []
    for p in rng.sample(passages, 14):
        words = []
        for a in range(p["first"], p["last"] + 1):
            w = phonetic_words((p["surah"], a), quran)
            if w is None:
                break
            words += w
        else:
            heard = ref.collapse("".join(heard_all[p["id"]]["parts"]))
            k = rng.randrange(1, len(words) - 1)
            variants = {"clean": words, "missed": words[:k] + [words[(k * 7) % len(words)]] + words[k:],
                        "extra": words[:k] + words[k + 1:], "replaced": words[:k] + [words[k][::-1]] + words[k + 1:]}
            for name, variant in variants.items():
                verdicts, extras = ref.judge(variant, heard)
                cases.append({"kind": "judge", "id": p["id"] + "/" + name, "words": variant, "heard": heard,
                              "verdicts": verdicts, "extras": [list(e) for e in extras]})
            for share in (0.0, 0.15, 0.4, 0.75, 1.0):
                part = heard[:int(len(heard) * share)]
                shown, verdicts = ref.follow(words, part)
                cases.append({"kind": "follow", "id": "%s/%.2f" % (p["id"], share), "words": words, "heard": part,
                              "shown": shown, "verdicts": verdicts})
    json.dump(cases, open(os.path.join(out, "phonetic_cases.json"), "w", encoding="utf-8"), ensure_ascii=False)
    with wave.open(passages[3]["wav"]) as f:
        pcm = f.readframes(16000 * 3)
    with wave.open(os.path.join(out, "mel_case.wav"), "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(16000); f.writeframes(pcm)
    x = np.frombuffer(pcm, "<i2").astype(np.float32) / 32768
    x = np.concatenate([np.zeros(LEAD, np.float32), x, np.zeros(LEAD, np.float32)])
    mel = FeatureExtractor(feature_size=80)(x, padding=0)
    mel = mel[:, : min(3000, mel.shape[1] // 2 * 2)]
    mel.astype("<f4").tofile(os.path.join(out, "mel_case.bin"))
    print("cases:", len(cases), "mel:", mel.shape)


if __name__ == "__main__":
    asset(sys.argv[2]) if sys.argv[1] == "asset" else golden(sys.argv[2], sys.argv[3])
