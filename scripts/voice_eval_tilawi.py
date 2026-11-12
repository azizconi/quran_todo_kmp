"""Tilawi FastConformer on the passages of voice_eval.py: writes hyp_tilawi.json and hyp_tilawi+split.json.
  voice_eval_tilawi.py <work> <dir with fastconformer_full_mixed.onnx and vocab.json> scripts/voice_eval.py
Then: voice_eval.py score --work <work> --model tilawi --model tilawi+split. Needs: pip install numpy onnxruntime.
"""
import importlib.util, json, os, sys, time, wave
import numpy as np, onnxruntime as ort

work, model_dir, eval_path = sys.argv[1], sys.argv[2], sys.argv[3]
spec = importlib.util.spec_from_file_location("voice_eval", eval_path); ve = importlib.util.module_from_spec(spec); spec.loader.exec_module(ve)
vocab = json.load(open(os.path.join(model_dir, "vocab.json"), encoding="utf-8"))
opts = ort.SessionOptions(); opts.intra_op_num_threads = 4
sess = ort.InferenceSession(os.path.join(model_dir, "fastconformer_full_mixed.onnx"), opts, providers=["CPUExecutionProvider"])
print([(i.name, i.shape, i.type) for i in sess.get_inputs()], [(o.name, o.shape) for o in sess.get_outputs()], flush=True)
BLANK = 1024

def read(path):
    with wave.open(path) as f:
        return np.frombuffer(f.readframes(f.getnframes()), "<i2").astype(np.float32) / 32768

def hear(path):
    x = read(path)
    lp = sess.run(None, {"audio_signal": x[None], "length": np.array([len(x)], np.int64)})[0][0]
    ids, out, prev = lp.argmax(-1), [], BLANK
    for i in ids:
        if i != prev and i != BLANK: out.append(vocab[str(int(i))])
        prev = i
    return "".join(out).replace("▁", " ").strip()

passages = json.load(open(os.path.join(work, "passages.json"), encoding="utf-8"))
os.makedirs(os.path.join(work, "results"), exist_ok=True); os.makedirs(os.path.join(work, "chunks"), exist_ok=True)
for split in (False, True):
    hyp = {}
    for p in passages:
        files = ve.split_at_silences(p["wav"], os.path.join(work, "chunks", p["id"])) if split else [p["wav"]]
        t = time.time(); text = " ".join(hear(f) for f in files); wall = time.time() - t
        hyp[p["id"]] = {"text": text, "wall": round(wall, 2), "chunks": len(files)}
        print(p["id"], split, "%.1f/%.1f" % (wall, p["seconds"]), text[:70], flush=True)
    json.dump(hyp, open(os.path.join(work, "results", "hyp_tilawi%s.json" % ("+split" if split else "")), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
