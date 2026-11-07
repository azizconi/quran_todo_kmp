#!/usr/bin/env python3
"""Concepts for the new Google Play icon (512x512) and feature graphic (1024x500), in the v3 screenshot style.

    python3 brand_v3/build_brand.py [--out DIR]

Output: <out>/icon-<name>-512.png, <out>/banner-<name>-1024x500.png and <out>/sheet.png (all concepts on one page).
The Arabic word is drawn by Core Text (capture/render_calligraphy.swift), because PIL here cannot shape Arabic:
brand_v3/cal-kufi.png and brand_v3/cal-thuluth.png are white renders of the word in Diwan Kufi / Diwan Thuluth.
Play icon rules: full-bleed square, no rounded corners or shadow of our own (Play applies the mask), key content in the
central ~70%. No stars anywhere (owner rule).
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RAW = ROOT / "capture" / "raw"
FONT_BLACK = "/Library/Fonts/SF-Pro-Display-Heavy.otf"
FONT_BOLD = "/Library/Fonts/SF-Pro-Display-Bold.otf"

BG = (5, 14, 12)
WHITE = (244, 247, 245)
EMERALD = (61, 220, 151)
EMERALD_D = (16, 120, 90)
GOLD = (242, 193, 78)
GOLD_D = (176, 120, 34)
IVORY = (250, 244, 228)
BLUE = (30, 70, 160)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def blob(size, center, radius, color, alpha):
    layer = Image.new("RGBA", size, color + (0,))
    d = int(radius * 2)
    g = Image.radial_gradient("L").resize((d, d)).point(lambda v: max(0, 255 - int(v * 1.42)) * alpha // 255)
    layer.paste(Image.new("RGBA", (d, d), color + (255,)), (int(center[0] - radius), int(center[1] - radius)), g)
    return layer


def vgrad(size, top, bottom):
    g = Image.linear_gradient("L").resize(size)
    return Image.composite(Image.new("RGB", size, bottom), Image.new("RGB", size, top), g).convert("RGBA")


def tint(mask_img, top, bottom):
    """Fills the alpha of `mask_img` with a vertical gradient."""
    a = mask_img.getchannel("A")
    out = vgrad(mask_img.size, top, bottom)
    out.putalpha(a)
    return out


def glow(layer, radius, color, strength):
    a = layer.getchannel("A").filter(ImageFilter.GaussianBlur(radius)).point(lambda v: min(255, int(v * strength)))
    g = Image.new("RGBA", layer.size, color + (0,))
    g.putalpha(a)
    return g


def fit(img, max_w, max_h):
    k = min(max_w / img.width, max_h / img.height)
    return img.resize((int(img.width * k), int(img.height * k)), Image.Resampling.LANCZOS)


def arc_ring(size, center, r, width, start, extent, c0, c1):
    """Thick arc with round caps, colour going from c0 to c1 along the arc."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 240
    for i in range(steps + 1):
        t = i / steps
        ang = math.radians(start + extent * t)
        x, y = center[0] + r * math.cos(ang), center[1] + r * math.sin(ang)
        c = lerp(c0, c1, t)
        d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=c + (255,))
    return layer


# ---------------------------------------------------------------- icons (drawn at 2048, saved at 512)
N = 2048
SKY = (96, 160, 255)


def aurora_icon():
    """Same light as the screenshots: near-black with an emerald field low-left and a blue one top-right."""
    img = Image.new("RGBA", (N, N), BG + (255,))
    img.alpha_composite(blob((N, N), (int(N * 0.0), int(N * 1.05)), N * 1.0, EMERALD_D, 255))
    img.alpha_composite(blob((N, N), (int(N * 1.05), int(N * -0.05)), N * 0.85, BLUE, 220))
    return img


def shade_3d(layer, light=(-1, -1), depth=26):
    """Makes a flat shape look rounded: light rim on the lit side, darker on the other."""
    a = layer.getchannel("A")
    dx, dy = light
    hi = ImageChops.subtract(a, ImageChops.offset(a, int(-dx * depth), int(-dy * depth))).filter(ImageFilter.GaussianBlur(depth * 0.8))
    lo = ImageChops.subtract(a, ImageChops.offset(a, int(dx * depth), int(dy * depth))).filter(ImageFilter.GaussianBlur(depth * 0.8))
    hi = ImageChops.multiply(hi, a)
    lo = ImageChops.multiply(lo, a)
    out = layer.copy()
    w = Image.new("RGBA", layer.size, (255, 255, 255, 0)); w.putalpha(hi.point(lambda v: int(v * 0.55)))
    k = Image.new("RGBA", layer.size, (0, 0, 0, 0)); k.putalpha(lo.point(lambda v: int(v * 0.45)))
    out.alpha_composite(w)
    out.alpha_composite(k)
    return out


def end_dot(img, c, r, ang_deg, size, color):
    a = math.radians(ang_deg)
    x, y = c[0] + r * math.cos(a), c[1] + r * math.sin(a)
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(lay).ellipse((x - size, y - size, x + size, y + size), fill=color + (255,))
    img.alpha_composite(glow(lay, size * 0.9, color, 1.4))
    img.alpha_composite(lay)


def calligraphy(name, box_w, box_h, top, bottom):
    cal = Image.open(HERE / name).convert("RGBA")
    return tint(fit(cal, box_w, box_h), top, bottom)


def icon_glass(scale=1.0, background=True):
    """The chosen icon: glossy ring (emerald to gold) with a glowing gold dot in its gap, white calligraphy inside.
    scale < 1 shrinks the artwork around the centre (adaptive launcher foreground has to sit in the safe zone)."""
    img = aurora_icon() if background else Image.new("RGBA", (N, N), (0, 0, 0, 0))
    c, r, w = (N // 2, N // 2), int(N * 0.33 * scale), int(N * 0.105 * scale)
    track = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(track).ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), outline=(255, 255, 255, 26), width=w)
    img.alpha_composite(track)
    ring = shade_3d(arc_ring((N, N), c, r, w, 125, 290, EMERALD, GOLD), depth=int(34 * scale))
    img.alpha_composite(glow(ring, 70 * scale, EMERALD, 0.55))
    img.alpha_composite(ring)
    end_dot(img, c, r, 125 + 290 + 34, w * 0.42, GOLD)
    cal = calligraphy("cal-thuluth.png", r * 1.40, r * 1.05, (255, 255, 255), IVORY)
    lay = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    lay.alpha_composite(cal, (c[0] - cal.width // 2, c[1] - cal.height // 2 + int(N * 0.012 * scale)))
    img.alpha_composite(glow(lay, 40 * scale, (0, 0, 0), 0.9))
    img.alpha_composite(lay)
    return img


def icon_rings():
    """Three arcs: new, recent, old (the lesson of the day), each in its accent colour."""
    img = aurora_icon()
    c = (N // 2, N // 2)
    w = int(N * 0.072)
    for r, ext, col0, col1 in ((0.355, 300, EMERALD, (150, 240, 190)), (0.255, 240, GOLD, (255, 222, 140)),
                               (0.155, 190, SKY, (170, 205, 255))):
        rr = int(N * r)
        tr = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        ImageDraw.Draw(tr).ellipse((c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr), outline=col0 + (40,), width=w)
        img.alpha_composite(tr)
        arc = shade_3d(arc_ring((N, N), c, rr, w, -90, ext, col0, col1), depth=22)
        img.alpha_composite(glow(arc, 40, col0, 0.5))
        img.alpha_composite(arc)
    return img


def icon_light():
    img = Image.new("RGBA", (N, N), IVORY + (255,))
    img.alpha_composite(blob((N, N), (int(N * 0.1), int(N * 0.95)), N * 0.9, (205, 236, 220), 255))
    img.alpha_composite(blob((N, N), (int(N * 0.95), int(N * 0.05)), N * 0.7, (255, 236, 196), 255))
    c, r, w = (N // 2, N // 2), int(N * 0.33), int(N * 0.10)
    track = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(track).ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), outline=(16, 120, 90, 28), width=w)
    img.alpha_composite(track)
    ring = shade_3d(arc_ring((N, N), c, r, w, 125, 290, (12, 110, 80), (40, 190, 130)), depth=30)
    sh = glow(ring, 40, (0, 60, 40), 0.35)
    img.alpha_composite(ImageChops.offset(sh, 0, 30))
    img.alpha_composite(ring)
    end_dot(img, c, r, 125 + 290 + 34, w * 0.42, (232, 170, 40))
    cal = calligraphy("cal-thuluth.png", r * 1.40, r * 1.05, (10, 92, 66), (8, 70, 52))
    img.alpha_composite(cal, (c[0] - cal.width // 2, c[1] - cal.height // 2 + int(N * 0.012)))
    return img


def icon_medal():
    img = aurora_icon()
    c, r = (N // 2, N // 2), int(N * 0.36)
    disc = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    m = Image.new("L", (N, N), 0)
    ImageDraw.Draw(m).ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), fill=255)
    fill = vgrad((N, N), (34, 170, 120), (8, 70, 52))
    disc.paste(fill, (0, 0), m)
    disc = shade_3d(disc, depth=40)
    img.alpha_composite(glow(disc, 90, EMERALD, 0.6))
    img.alpha_composite(disc)
    rr, w = int(r * 0.86), int(N * 0.038)
    arc = arc_ring((N, N), c, rr, w, 125, 290, (255, 230, 160), GOLD)
    img.alpha_composite(arc)
    end_dot(img, c, rr, 125 + 290 + 30, w * 0.75, (255, 236, 180))
    cal = calligraphy("cal-thuluth.png", rr * 1.35, rr * 1.0, (255, 246, 220), GOLD)
    lay = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    lay.alpha_composite(cal, (c[0] - cal.width // 2, c[1] - cal.height // 2 + int(N * 0.012)))
    img.alpha_composite(ImageChops.offset(glow(lay, 20, (0, 0, 0), 1.0), 0, 14))
    img.alpha_composite(lay)
    return img


ICONS = {"glass": icon_glass, "rings": icon_rings, "light": icon_light, "medal": icon_medal}

# ---------------------------------------------------------------- banners: the v3 screenshot layout, landscape
BW, BH, S = 1024, 500, 2
PAL = {
    "emerald": ((61, 220, 151), (16, 120, 90), (30, 70, 160)),
    "gold": ((242, 193, 78), (150, 96, 20), (20, 110, 90)),
    "cyan": ((76, 200, 222), (14, 98, 130), (70, 50, 150)),
}
WORD = {"ru": "Хифз", "en": "Hifz", "tr": "Hıfz", "tg": "Ҳифз", "uz": "Hifz"}
SUB = {
    "ru": ("Коран наизусть —", "бесплатно, без рекламы."),
    "en": ("Memorize the Quran —", "free, no ads."),
    "tr": ("Kur’an’ı ezberleyin —", "ücretsiz, reklamsız."),
    "tg": ("Қуръонро аз ёд кунед —", "ройгон, бе реклама."),
    "uz": ("Qur’onni yodlang —", "bepul, reklamasiz."),
}
LOCALES = {"ru": "ru-RU", "en": "en-US", "tr": "tr-TR", "tg": "tg", "uz": "uz"}
RES = ROOT.parent / "composeApp" / "src" / "androidMain" / "res"


def final():
    """Writes the approved set: Play icon, lesson banner per language, Android launcher icons."""
    icon = icon_glass().resize((512, 512), Image.Resampling.LANCZOS).convert("RGB")
    icon.save(ROOT / "icon-512.png")
    for lang, loc in LOCALES.items():
        b = banner_lesson(lang).resize((BW, BH), Image.Resampling.LANCZOS).convert("RGB")
        (ROOT / "localized" / loc).mkdir(parents=True, exist_ok=True)
        b.save(ROOT / "localized" / loc / "feature-graphic-1024x500.png")
    # adaptive icon: 108dp canvas, the artwork has to stay inside the 66dp safe circle
    bg = aurora_icon().resize((432, 432), Image.Resampling.LANCZOS)
    fg = icon_glass(scale=0.80, background=False).resize((432, 432), Image.Resampling.LANCZOS)
    bg.save(RES / "drawable" / "ic_launcher_adaptive_background.png")
    fg.save(RES / "drawable" / "ic_launcher_adaptive_foreground.png")
    full = icon_glass()
    for dpi, s in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        im = full.resize((s, s), Image.Resampling.LANCZOS)
        sq = Image.new("L", (s, s), 0)
        ImageDraw.Draw(sq).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.22), fill=255)
        rd = Image.new("L", (s, s), 0)
        ImageDraw.Draw(rd).ellipse((0, 0, s - 1, s - 1), fill=255)
        a = im.copy(); a.putalpha(sq); a.save(RES / f"mipmap-{dpi}" / "ic_launcher.png")
        b = im.copy(); b.putalpha(rd); b.save(RES / f"mipmap-{dpi}" / "ic_launcher_round.png")
    print("final set written")


def banner_bg(accent):
    dot, ga, gb = PAL[accent]
    size = (BW * S, BH * S)
    img = Image.new("RGBA", size, BG + (255,))
    img.alpha_composite(blob(size, (-40 * S, 560 * S), 560 * S, ga, 235))
    img.alpha_composite(blob(size, (1060 * S, 360 * S), 520 * S, gb, 225))
    img.alpha_composite(blob(size, (700 * S, 620 * S), 420 * S, lerp(ga, gb, 0.5), 120))
    return img


def banner_title(img, lang, accent, x=60, top=118):
    dot = PAL[accent][0]
    d = ImageDraw.Draw(img)
    word = WORD[lang]
    s1, s2 = SUB[lang]
    size = 150
    hf = ImageFont.truetype(FONT_BLACK, size * S)
    base = top * S + int(size * 0.74 * S)
    xx = x * S - int(size * 0.03 * S)
    for ch in word:
        d.text((xx, base), ch, font=hf, fill=WHITE, anchor="ls")
        xx += d.textlength(ch, font=hf) * 0.96
    rr = size * 0.115 * S
    d.ellipse((xx + rr * 0.35, base - 2 * rr, xx + rr * 2.35, base), fill=dot)
    ss = 38
    sf = ImageFont.truetype(FONT_BOLD, ss * S)
    while max(d.textlength(s1, font=sf), d.textlength(s2, font=sf)) > 470 * S:
        ss -= 1
        sf = ImageFont.truetype(FONT_BOLD, ss * S)
    sy = base + 30 * S
    d.text((x * S, sy), s1, font=sf, fill=WHITE, anchor="lt")
    d.text((x * S, sy + int(ss * 1.14 * S)), s2, font=sf, fill=lerp(dot, WHITE, 0.25), anchor="lt")


def rounded(img, radius):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, img.size[0] - 1, img.size[1] - 1), radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def phone(shot, width):
    bez, rim = 8 * S, 3 * S
    sw = width - 2 * (bez + rim)
    sh = int(shot.height * sw / shot.width)
    h = sh + 2 * (bez + rim)
    R = 50 * S
    body = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    rim_l = Image.linear_gradient("L").resize((width, h)).point(lambda v: 150 + int(60 * abs((v / 255) - 0.35)))
    m = Image.new("L", (width, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, width - 1, h - 1), radius=R, fill=255)
    body.paste(Image.merge("RGBA", (rim_l, rim_l, rim_l, Image.new("L", (width, h), 255))), (0, 0), m)
    d.rounded_rectangle((rim, rim, width - 1 - rim, h - 1 - rim), radius=R - rim, fill=(10, 12, 12, 255))
    body.alpha_composite(rounded(shot.resize((sw, sh), Image.Resampling.LANCZOS), R - bez - rim), (bez + rim, bez + rim))
    cx, cy, cr = width // 2, bez + rim + 17 * S, 7 * S
    d.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(6, 6, 8, 255))
    return body, sw / shot.width, bez + rim


def with_shadow(layer, blur, alpha, dy):
    a = layer.getchannel("A").point(lambda v: v * alpha // 255).filter(ImageFilter.GaussianBlur(blur))
    pad = blur * 3
    out = Image.new("RGBA", (layer.width + 2 * pad, layer.height + 2 * pad + dy), (0, 0, 0, 0))
    sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sh.putalpha(a)
    out.alpha_composite(sh, (pad, pad + dy))
    out.alpha_composite(layer, (pad, pad))
    return out, pad


def place_device(img, shot, cx, top, width, tilt=0.0):
    body, k, off = phone(shot, width * S)
    lay, pad = with_shadow(body, 26 * S, 200, 16 * S)
    if tilt:
        lay = lay.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=True)
    img.alpha_composite(lay, (cx * S - lay.width // 2, top * S - pad))
    return k


def place_card(img, shot, box, cx, cy, width, tilt):
    crop = shot.crop(box)
    pw = width * S
    ph = int(crop.height * pw / crop.width)
    card = rounded(crop.resize((pw, ph), Image.Resampling.LANCZOS), 22 * S)
    ImageDraw.Draw(card).rounded_rectangle((0, 0, pw - 1, ph - 1), radius=22 * S, outline=(255, 255, 255, 70), width=2 * S)
    lay, pad = with_shadow(card, 24 * S, 210, 14 * S)
    lay = lay.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=True)
    img.alpha_composite(lay, (cx * S - lay.width // 2, cy * S - lay.height // 2))


def banner_lesson(lang):
    img = banner_bg("emerald")
    banner_title(img, lang, "emerald")
    shot = Image.open(RAW / lang / "01_home.png").convert("RGB")
    place_device(img, shot, 800, 46, 290)
    place_card(img, shot, (55, 400, 1025, 1212), 790, 248, 360, -2.0)
    return img


def banner_listen(lang):
    img = banner_bg("gold")
    banner_title(img, lang, "gold")
    shot = Image.open(RAW / lang / "02_reader.png").convert("RGB")
    place_device(img, shot, 800, 46, 290)
    place_card(img, shot, (0, 775, 1080, 1425), 790, 250, 400, 0.0)
    return img


def banner_duo(lang):
    img = banner_bg("cyan")
    banner_title(img, lang, "cyan")
    back = Image.open(RAW / lang / "03_mushaf.png").convert("RGB")
    front = Image.open(RAW / lang / "01_home.png").convert("RGB")
    place_device(img, back, 900, 70, 250, -8)
    place_device(img, front, 720, 40, 270, 6)
    return img


BANNERS = {"lesson": banner_lesson, "listen": banner_listen, "duo": banner_duo}


def sheet(icons, banners, out):
    """All concepts on one page: icons with Play's rounded mask, then banners."""
    W = 1024 * 2 + 120
    ih = 360
    H = 80 + ih + 80 + (500 + 60) * ((len(banners) + 1) // 2) + 40
    pg = Image.new("RGB", (W, H), (24, 28, 27))
    d = ImageDraw.Draw(pg)
    lf = ImageFont.truetype(FONT_BOLD, 30)
    x = 40
    for name, im in icons.items():
        sm = im.resize((ih, ih), Image.Resampling.LANCZOS)
        m = Image.new("L", (ih, ih), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, ih - 1, ih - 1), radius=int(ih * 0.2), fill=255)
        pg.paste(sm, (x, 80), m)
        d.text((x, 30), name, font=lf, fill=WHITE)
        # small sizes as they appear in the store list and on the launcher
        for k, s in enumerate((96, 48)):
            t = im.resize((s, s), Image.Resampling.LANCZOS)
            mm = Image.new("L", (s, s), 0)
            ImageDraw.Draw(mm).ellipse((0, 0, s - 1, s - 1), fill=255)
            pg.paste(t, (x + ih + 20, 80 + k * 120), mm)
        x += ih + 160
    y = 80 + ih + 80
    for i, (name, im) in enumerate(banners.items()):
        bx = 40 + (i % 2) * (1024 + 40)
        by = y + (i // 2) * 560
        d.text((bx, by - 40), name, font=lf, fill=WHITE)
        pg.paste(im, (bx, by))
    pg.save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(HERE / "concepts"))
    ap.add_argument("--lang", default="ru")
    ap.add_argument("--final", action="store_true", help="write the approved icon, banners and launcher icons")
    a = ap.parse_args()
    if a.final:
        final()
        return
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    icons = {}
    for name, fn in ICONS.items():
        im = fn().resize((512, 512), Image.Resampling.LANCZOS).convert("RGB")
        im.save(out / f"icon-{name}-512.png")
        icons[name] = im
    banners = {}
    for name, fn in BANNERS.items():
        im = fn(a.lang).resize((BW, BH), Image.Resampling.LANCZOS).convert("RGB")
        im.save(out / f"banner-{name}-{a.lang}-1024x500.png")
        banners[name] = im
    sheet(icons, banners, out / f"sheet-{a.lang}.png")
    print("written to", out)


if __name__ == "__main__":
    main()
