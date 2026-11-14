#!/usr/bin/env python3
"""App Store screenshots in the same poster style as the Google Play set v3 (screens_v3/build_v3.py): one huge word with a
coloured dot, a two-line subtitle, an iPhone that bleeds off the bottom edge and one UI card lifted out of the screen.

    python3 build_app_store.py <lang|all> [--raw DIR] [--out DIR]

Input : <raw>/<lang>/NN_name.png   1320x2868 simulator captures (iPhone 17 Pro Max), see capture_ios.sh
        ../screens_v3/copy/<lang>.json   the same words and subtitles as the Android set
Output: <out>/<lang>/6.9/NN-name.png (1320x2868) and <out>/<lang>/6.5/NN-name.png (1242x2688)
The Dynamic Island and status bar are in the captures, so the frame only draws the bezel. No stars anywhere.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "screens_v3"))
import build_v3 as B  # noqa: E402

B.W, B.H = 1320, 2868
B.MARGIN = 92
B.HEAD_TOP = 118
B.HEAD_MAX, B.HEAD_MIN = 290, 150
B.SUB_SIZE_MAX, B.SUB_SIZE_MIN = 82, 56
B.DEV_W = 1110
B.DEV_GAP = 84
B.DEV_TOP_MIN = 650
B.BEZEL, B.RIM, B.DEV_R = 14, 6, 150
B.POP_W = 1180

# (raw stem, store name, copy index, accent, pop box in raw 1320x2868 px, tilt)
SCREENS = [
    ("01_lesson", "lesson", 0, "emerald", (45, 330, 1275, 1530), -2.0),
    ("02_reader", "reader", 1, "gold", (0, 360, 1320, 1230), 0.0),
    ("03_mushaf", "mushaf", 2, "cyan", (0, 330, 1320, 900), 2.0),
    ("04_tajweed", "tajweed", 3, "rose", (0, 360, 1320, 1230), -1.5),
    ("05_tafsir", "tafsir", 4, "violet", (50, 430, 1270, 1900), 1.5),
    ("06_plan", "plan", 5, "sky", (60, 350, 1260, 1650), -2.0),
    ("07_progress", "progress", 6, "lime", (50, 360, 1270, 1470), 1.5),
    ("08_quiz", "quiz", 7, "amber", (60, 380, 1260, 1700), -2.5),
]
POP = {lang: {"01_lesson": (45, 330, 1275, 1590)} for lang in ("uz",)}  # {lang: {stem: box}} for languages whose text wraps differently
LANGS = ["ru", "tg", "uz", "en", "tr"]


def device(img, shot, top):
    S = B.S
    dw = B.DEV_W * S
    x0 = (B.W * S - dw) // 2
    off = (B.BEZEL + B.RIM) * S
    sw = dw - 2 * off
    scale = sw / shot.width
    sh = int(shot.height * scale)
    dh = sh + 2 * off
    R = B.DEV_R * S
    sdw, pad = B.shadow_of((dw, dh), R, 60 * S, 200)
    img.alpha_composite(sdw, (x0 - pad, top - pad + 34 * S))
    body = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    rim = Image.linear_gradient("L").resize((dw, dh)).point(lambda v: 150 + int(60 * abs((v / 255) - 0.35)))
    rim_rgb = Image.merge("RGBA", (rim, rim.point(lambda v: v + 4), rim.point(lambda v: v + 2), Image.new("L", (dw, dh), 255)))
    m = Image.new("L", (dw, dh), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, dw - 1, dh - 1), radius=R, fill=255)
    body.paste(rim_rgb, (0, 0), m)
    rr = B.RIM * S
    bd.rounded_rectangle((rr, rr, dw - 1 - rr, dh - 1 - rr), radius=R - rr, fill=(10, 12, 12, 255))
    screen = B.rounded(shot.resize((sw, sh), Image.Resampling.LANCZOS), R - off)
    body.alpha_composite(screen, (off, off))
    img.alpha_composite(body, (x0, top))
    d = ImageDraw.Draw(img)
    bx = x0 + dw - 1 * S
    for y1, y2 in ((420, 560), (600, 740)):
        d.rounded_rectangle((bx, top + y1 * S, bx + 7 * S, top + y2 * S), radius=3 * S, fill=(150, 156, 154, 255))
    return x0 + off, top + off, scale


B.device = device


def build(lang: str, raw_dir: Path, out_dir: Path) -> None:
    copy = json.loads((HERE.parent / "screens_v3" / "copy" / f"{lang}.json").read_text(encoding="utf-8"))
    for stem, name, ci, accent, box, tilt in SCREENS:
        raw = raw_dir / lang / f"{stem}.png"
        if not raw.exists():
            print("MISSING", raw)
            continue
        t = copy["screens"][ci]
        box = POP.get(lang, {}).get(stem, box)
        shot = Image.open(raw).convert("RGB")
        img = B.background(accent)
        text_bottom = B.draw_title(img, t["word"], t["sub1"], t["sub2"], accent, t.get("pill"))
        sx, sy, scale = device(img, shot, max(text_bottom + B.DEV_GAP * B.S, B.DEV_TOP_MIN * B.S))
        B.pop_card(img, shot, box, tilt, (sx, sy), scale)
        full = img.resize((B.W, B.H), Image.Resampling.LANCZOS).convert("RGB")
        n = f"{ci + 1:02d}-{name}.png"
        for label, size in (("6.9", (1320, 2868)), ("6.5", (1242, 2688))):
            d = out_dir / lang / label
            d.mkdir(parents=True, exist_ok=True)
            if size == (1320, 2868):
                im = full
            else:
                k = size[0] / full.width
                im = full.resize((size[0], round(full.height * k)), Image.Resampling.LANCZOS).crop((0, 0, size[0], size[1]))
            im.save(d / n, optimize=True)
        print("wrote", lang, n)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("lang")
    ap.add_argument("--raw", default="/private/tmp/s8-shots/store")
    ap.add_argument("--out", default=str(HERE))
    a = ap.parse_args()
    for lg in (LANGS if a.lang == "all" else [a.lang]):
        build(lg, Path(a.raw), Path(a.out))
