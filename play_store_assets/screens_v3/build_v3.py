#!/usr/bin/env python3
"""Google Play phone screenshots, style v3 (dark poster: one huge word with a coloured dot, a two-line subtitle,
a device that bleeds off the bottom edge, and one UI card of the screen lifted out of the device and enlarged).

    python3 screens_v3/build_v3.py <lang> [--out DIR] [--only N ...] [--debug]

Input : capture/raw/<lang>/NN_name.png        1080x2400 emulator captures
        screens_v3/copy/<lang>.json           texts and per-screen overrides for the language (see copy/ru.json)
Output: <out>/<store locale>/phone/phone-NN-<name>-1080x1920.png   (default out: screens_v3/out)

Rules of the owner: no star shapes anywhere (the dot is a circle; no sparkles in the pill); no Arabic in the headline
(PIL cannot shape it — Arabic only ever comes from the captures themselves).
The canvas is drawn at 2x and scaled down, so every shape is antialiased.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RAW = ROOT / "capture" / "raw"

LOCALES = {"ru": "ru-RU", "en": "en-US", "tg": "tg", "uz": "uz", "tr": "tr-TR"}

FONT_BLACK = "/Library/Fonts/SF-Pro-Display-Heavy.otf"
FONT_BOLD = "/Library/Fonts/SF-Pro-Display-Bold.otf"

W, H = 1080, 1920          # final size (9:16, Play's recommended phone ratio)
S = 2                      # supersampling factor
BG = (5, 14, 12)           # near-black with a green cast
WHITE = (244, 247, 245)

# Accent of each screen: (dot / second subtitle line, glow A, glow B). Order alternates warm and cool, like the reference.
PALETTE = {
    "emerald": ((61, 220, 151), (16, 120, 90), (30, 70, 160)),
    "gold": ((242, 193, 78), (150, 96, 20), (20, 110, 90)),
    "cyan": ((76, 200, 222), (14, 98, 130), (70, 50, 150)),
    "rose": ((255, 112, 140), (150, 30, 70), (90, 50, 170)),
    "violet": ((172, 148, 255), (80, 52, 170), (20, 110, 110)),
    "sky": ((96, 160, 255), (30, 70, 180), (16, 120, 90)),
    "lime": ((140, 230, 120), (40, 120, 50), (20, 90, 140)),
    "amber": ((255, 170, 80), (160, 80, 20), (110, 40, 120)),
}

# Screen layout, language-independent (the captures have the same layout in every language).
# raw: capture stem. pop: card lifted out of the device, in raw 1080x2400 pixels (x0, y0, x1, y1).
# tilt: rotation of the card in degrees. Overridable per language in copy/<lang>.json -> "screens"[i].
SCREENS = [
    dict(raw="01_home", name="lesson", accent="emerald", pop=(55, 400, 1025, 1212), tilt=-2.0),
    dict(raw="02_reader", name="reader", accent="gold", pop=(0, 775, 1080, 1425), tilt=0.0),
    dict(raw="03_mushaf", name="mushaf", accent="cyan", pop=(0, 885, 1080, 1290), tilt=2.0),
    dict(raw="04_tajweed", name="tajweed", accent="rose", pop=(0, 850, 1080, 1290), tilt=-1.5),
    dict(raw="05_tafsir", name="tafsir", accent="violet", pop=(55, 587, 1025, 1150), tilt=1.5),
    dict(raw="06_plan", name="plan", accent="sky", pop=(57, 440, 1025, 925), tilt=-2.0),
    dict(raw="07_details", name="progress", accent="lime", pop=(52, 762, 1028, 1178), tilt=1.5),
    dict(raw="08_quiz", name="quiz", accent="amber", pop=(52, 795, 1028, 1015), tilt=-2.5),
]

# Geometry at final size (multiplied by S when drawing).
MARGIN = 72
HEAD_TOP = 92              # top of the huge word's cap height area
HEAD_MAX, HEAD_MIN = 236, 120
SUB_SIZE_MAX, SUB_SIZE_MIN = 66, 46
DEV_W = 800                # device outer width
DEV_GAP = 64               # gap between the subtitle and the device top
DEV_TOP_MIN = 520
BEZEL = 16
RIM = 5
DEV_R = 104                # outer corner radius
POP_W = 960                # width of the lifted card


def f(path, size):
    return ImageFont.truetype(path, int(size * S))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def soft_blob(size, center, radius, color, alpha):
    """Radial glow as an RGBA layer of the canvas size."""
    layer = Image.new("RGBA", size, color + (0,))
    d = int(radius * 2)
    blob = Image.radial_gradient("L").resize((d, d))
    blob = blob.point(lambda v: max(0, 255 - int(v * 1.42)) * alpha // 255)
    layer.paste(Image.new("RGBA", (d, d), color + (255,)), (int(center[0] - radius), int(center[1] - radius)), blob)
    return layer


def background(accent):
    dot, ga, gb = PALETTE[accent]
    size = (W * S, H * S)
    img = Image.new("RGBA", size, BG + (255,))
    # aurora: two big colour fields left and right of the device, a faint one under the title
    img.alpha_composite(soft_blob(size, (-80 * S, 1180 * S), 820 * S, ga, 235))
    img.alpha_composite(soft_blob(size, (1180 * S, 1380 * S), 820 * S, gb, 225))
    img.alpha_composite(soft_blob(size, (540 * S, 1900 * S), 700 * S, lerp(ga, gb, 0.5), 120))
    img.alpha_composite(soft_blob(size, (900 * S, 120 * S), 520 * S, ga, 55))
    # darken the top so the title stays crisp
    top = Image.linear_gradient("L").resize((W * S, 760 * S)).point(lambda v: int((255 - v) * 0.55))
    shade = Image.new("RGBA", (W * S, 760 * S), BG + (255,))
    shade.putalpha(top)
    img.alpha_composite(shade, (0, 0))
    return img


def draw_title(img, word, sub1, sub2, accent, pill=None):
    dot_color = PALETTE[accent][0]
    sub2_color = lerp(dot_color, WHITE, 0.25)
    d = ImageDraw.Draw(img)
    max_w = (W - 2 * MARGIN) * S
    y = HEAD_TOP * S

    if pill:
        pf = f(FONT_BOLD, 30)
        tw = d.textlength(pill, font=pf)
        ph, pad = 54 * S, 26 * S
        x0, y0 = MARGIN * S, (HEAD_TOP - 40) * S
        d.rounded_rectangle((x0, y0, x0 + tw + 2 * pad + 22 * S, y0 + ph), radius=ph // 2,
                            fill=lerp(BG, dot_color, 0.18) + (255,), outline=dot_color + (255,), width=2 * S)
        cx, cy, r = x0 + pad + 6 * S, y0 + ph // 2, 7 * S
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=dot_color)
        d.text((x0 + pad + 22 * S, y0 + ph // 2), pill, font=pf, fill=dot_color, anchor="lm")
        y += 44 * S

    # the huge word: largest size that fits the width together with its dot
    size = HEAD_MAX
    while size > HEAD_MIN:
        hf = f(FONT_BLACK, size)
        r = size * 0.115 * S
        if d.textlength(word, font=hf) * 0.96 + r * 2.6 <= max_w:
            break
        size -= 4
    hf = f(FONT_BLACK, size)
    asc = hf.getmetrics()[0]
    base = y + int(asc * 0.80)
    x = MARGIN * S - int(size * 0.03 * S)
    # tight tracking: draw letter by letter at 96% advance
    for ch in word:
        d.text((x, base), ch, font=hf, fill=WHITE, anchor="ls")
        x += d.textlength(ch, font=hf) * 0.96
    r = size * 0.115 * S
    d.ellipse((x + r * 0.35, base - 2 * r, x + r * 2.35, base), fill=dot_color)

    # subtitle: two lines, the second in the accent colour
    sy = base + int(size * 0.20 * S)
    ss = SUB_SIZE_MAX
    while ss > SUB_SIZE_MIN:
        sf = f(FONT_BOLD, ss)
        if max(d.textlength(sub1, font=sf), d.textlength(sub2, font=sf)) <= max_w:
            break
        ss -= 2
    sf = f(FONT_BOLD, ss)
    lh = int(ss * 1.12 * S)
    d.text((MARGIN * S, sy), sub1, font=sf, fill=WHITE, anchor="lt")
    d.text((MARGIN * S, sy + lh), sub2, font=sf, fill=sub2_color, anchor="lt")
    return sy + 2 * lh  # bottom of the text block (2x px)


def rounded(img, radius):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def shadow_of(size, radius, blur, alpha, spread=0):
    pad = blur * 3
    sh = Image.new("RGBA", (size[0] + 2 * pad, size[1] + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((pad - spread, pad - spread, pad + size[0] + spread, pad + size[1] + spread),
                                         radius=radius, fill=(0, 0, 0, alpha))
    return sh.filter(ImageFilter.GaussianBlur(blur)), pad


def device(img, shot, top):
    """Android phone, flat sides, thin metallic rim, punch-hole camera; bleeds off the bottom edge."""
    dw = DEV_W * S
    x0 = (W * S - dw) // 2
    sw = dw - 2 * (BEZEL + RIM) * S
    scale = sw / shot.width
    sh = int(shot.height * scale)
    dh = sh + 2 * (BEZEL + RIM) * S
    R = DEV_R * S

    sdw, pad = shadow_of((dw, dh), R, 50 * S, 190)
    img.alpha_composite(sdw, (x0 - pad, top - pad + 30 * S))

    body = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    # rim: vertical metallic gradient
    rim = Image.linear_gradient("L").resize((dw, dh)).point(lambda v: 150 + int(60 * abs((v / 255) - 0.35)))
    rim_rgb = Image.merge("RGBA", (rim, rim.point(lambda v: v + 4), rim.point(lambda v: v + 2), Image.new("L", (dw, dh), 255)))
    m = Image.new("L", (dw, dh), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, dw - 1, dh - 1), radius=R, fill=255)
    body.paste(rim_rgb, (0, 0), m)
    rr = RIM * S
    bd.rounded_rectangle((rr, rr, dw - 1 - rr, dh - 1 - rr), radius=R - rr, fill=(10, 12, 12, 255))
    bd.rounded_rectangle((rr + 2 * S, rr + 2 * S, dw - 1 - rr - 2 * S, dh - 1 - rr - 2 * S), radius=R - rr - 2 * S,
                         outline=(60, 66, 64, 255), width=1 * S)
    screen = rounded(shot.resize((sw, sh), Image.Resampling.LANCZOS), R - (BEZEL + RIM) * S)
    off = (BEZEL + RIM) * S
    body.alpha_composite(screen, (off, off))
    # punch-hole camera in the status bar, centred
    cx, cy, cr = dw // 2, off + int(34 * scale * S / S), 15 * S
    bd.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(6, 6, 8, 255))
    bd.ellipse((cx - cr // 2.4, cy - cr // 2.4, cx + cr // 2.4, cy + cr // 2.4), fill=(26, 30, 46, 255))
    img.alpha_composite(body, (x0, top))
    # side buttons on the right edge
    d = ImageDraw.Draw(img)
    bx = x0 + dw - 1 * S
    for y1, y2 in ((330, 470), (540, 620)):
        d.rounded_rectangle((bx, top + y1 * S, bx + 6 * S, top + y2 * S), radius=3 * S, fill=(150, 156, 154, 255))
    return x0 + off, top + off, scale  # screen origin and raw->canvas scale


def pop_card(img, shot, box, tilt, screen_xy, scale):
    """Crops `box` of the capture, enlarges it to POP_W and lays it over the device at the same height, with a shadow."""
    x0, y0, x1, y1 = box
    crop = shot.crop(box)
    pw = POP_W * S
    ph = int(crop.height * pw / crop.width)
    card = rounded(crop.resize((pw, ph), Image.Resampling.LANCZOS), 40 * S)
    ImageDraw.Draw(card).rounded_rectangle((0, 0, pw - 1, ph - 1), radius=40 * S, outline=(255, 255, 255, 70), width=2 * S)
    # centre of the card follows the centre of the cropped area inside the device
    cy = screen_xy[1] + int(((y0 + y1) / 2) * scale)
    cx = W * S // 2
    sdw, pad = shadow_of((pw, ph), 40 * S, 44 * S, 200)
    layer = Image.new("RGBA", (pw + 2 * pad, ph + 2 * pad), (0, 0, 0, 0))
    layer.alpha_composite(sdw, (0, 24 * S))
    layer.alpha_composite(card, (pad, pad))
    if tilt:
        layer = layer.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=True)
    img.alpha_composite(layer, (cx - layer.width // 2, cy - layer.height // 2))


def render(lang, i, copy):
    sc = dict(SCREENS[i])
    t = copy["screens"][i]
    sc.update(t.get("override", {}))
    shot = Image.open(RAW / lang / f"{sc['raw']}.png").convert("RGB")
    img = background(sc["accent"])
    text_bottom = draw_title(img, t["word"], t["sub1"], t["sub2"], sc["accent"], t.get("pill"))
    sx, sy, scale = device(img, shot, max(text_bottom + DEV_GAP * S, DEV_TOP_MIN * S))
    if sc.get("pop"):
        pop_card(img, shot, tuple(sc["pop"]), sc.get("tilt", 0.0), (sx, sy), scale)
    return img.resize((W, H), Image.Resampling.LANCZOS).convert("RGB"), sc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("lang", choices=list(LOCALES))
    ap.add_argument("--out", default=str(HERE / "out"))
    ap.add_argument("--only", type=int, nargs="*", help="1-based screen numbers")
    a = ap.parse_args()
    copy = json.loads((HERE / "copy" / f"{a.lang}.json").read_text(encoding="utf-8"))
    out = Path(a.out) / LOCALES[a.lang] / "phone"
    out.mkdir(parents=True, exist_ok=True)
    for i in range(len(SCREENS)):
        if a.only and (i + 1) not in a.only:
            continue
        img, sc = render(a.lang, i, copy)
        fn = out / f"phone-{i + 1:02d}-{sc['name']}-1080x1920.png"
        img.save(fn, optimize=True)
        print("wrote", fn)


if __name__ == "__main__":
    main()
