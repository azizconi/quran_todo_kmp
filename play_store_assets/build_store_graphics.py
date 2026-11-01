#!/usr/bin/env python3
"""Builds the Google Play graphics of the redesign (E3) from the raw emulator captures.

    python3 build_store_graphics.py [lang ...]          lang: ru en tg uz tr (default: all)

Input : capture/raw/<lang>/NN_name.png        1080x2400 emulator screenshots (see capture/capture.py)
Output: localized/<store locale>/phone/phone-NN-<name>-1080x1920.png      8 screens
        localized/<store locale>/feature-graphic-1024x500.png
        play-store-export-manifest.json

Format: 1080x1920 (9:16). Play accepts phone screenshots from 9:16 up to 2:1 on the long side; the capture itself is
1080x2400 (20:9), which is wider than 2:1, so it is shown inside a device frame that bleeds off the bottom edge instead of
being cropped. Headlines are set in SF Pro Display Bold (covers Cyrillic, Tajik ӣ ӯ ҳ ҷ қ ғ and Uzbek/Turkish Latin).
The feature graphic needs Arabic shaping, which PIL cannot do here: capture/render_calligraphy.swift (Core Text) draws the
"حفظ" calligraphy once into capture/hifz_calligraphy_gold.png. No stars anywhere: the ornaments are circles and soft glows.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
RAW = Path(os.environ.get("RAW_DIR", ROOT / "capture" / "raw"))
OUT = ROOT / "localized"
CALLIGRAPHY = ROOT / "capture" / "hifz_calligraphy_gold.png"

FONT_BOLD = "/Library/Fonts/SF-Pro-Display-Bold.otf"
FONT_SEMI = "/Library/Fonts/SF-Pro-Display-Semibold.otf"
FONT_REG = "/Library/Fonts/SF-Pro-Display-Regular.otf"

LOCALES = {"ru": "ru-RU", "en": "en-US", "tg": "tg", "uz": "uz", "tr": "tr-TR"}

INK = (14, 58, 46)
IVORY = (247, 244, 237)
GOLD = (190, 146, 56)

# (file, store name, dark?) in store order
SCREENS = [
    ("01_home", "home", False),
    ("02_recall", "recall", False),
    ("03_summary", "lesson-done", False),
    ("04_plan", "plan", False),
    ("05_reader", "reader", False),
    ("06_progress", "progress", False),
    ("07_ready", "onboarding", False),
    ("08_dark", "dark", True),
]

# 2.0.0 set (R2) for the languages that were captured again with the new screens: lesson, reader, mushaf pages, tajweed,
# tafsir, plan, detailed progress, quiz. Raw names: capture/raw/<lang>/NN_name.png. Not more than 8 per language.
SCREENS_V2 = [
    ("01_home", "lesson", False),
    ("02_reader", "reader", False),
    ("03_mushaf", "mushaf", False),
    ("04_tajweed", "tajweed", False),
    ("05_tafsir", "tafsir", False),
    ("06_plan", "plan", False),
    ("07_details", "progress", False),
    ("08_quiz", "quiz", False),
]
V2_LANGS = {"ru", "en"}   # tg, uz and tr still use the E3 set (SCREENS) until their new screens are captured


def screens_for(lang: str):
    return SCREENS_V2 if lang in V2_LANGS else SCREENS


HEADLINES_V2 = {
    "ru": ["Каждый день — готовый урок", "Слушайте и видите каждое слово", "Мусхаф как в печатном Коране",
           "Таджвид — цветом", "Тафсир всегда под рукой", "Знаете, когда закончите", "Честный прогресс",
           "Проверьте себя"],
    "en": ["A ready lesson every day", "Hear it and see every word", "A mushaf like the printed Quran",
           "Tajweed in colour", "Tafsir always at hand", "Know when you will finish", "Honest progress",
           "Check yourself"],
}

# Short benefit headlines, same order as SCREENS. tg, uz and tr are for native-speaker review.
HEADLINES = {
    "ru": ["Каждый день — готовый урок", "Вспомните, затем проверьте себя", "Повторение в нужный день",
           "Знаете, когда закончите", "Красивый мусхаф и чтецы", "Честный прогресс", "План готов за минуту",
           "Удобно днём и ночью"],
    "en": ["A ready lesson every day", "Recall first, then check yourself", "Reviews on the right day",
           "Know when you will finish", "A beautiful mushaf and reciters", "Honest progress", "Your plan in a minute",
           "Easy on the eyes at night"],
    "tg": ["Ҳар рӯз — дарси тайёр", "Аввал ба ёд оред, баъд санҷед", "Такрор дар рӯзи лозим",
           "Медонед, кай ба охир мерасонед", "Мусҳафи зебо ва қориён", "Пешрафти ҳақиқӣ", "Нақша дар як дақиқа",
           "Шабу рӯз бароҳат"],
    "uz": ["Har kuni — tayyor dars", "Avval eslang, keyin tekshiring", "Takror — kerakli kunda",
           "Qachon tugatishingizni bilasiz", "Chiroyli mushaf va qorilar", "Halol natija", "Reja bir daqiqada",
           "Kechayu kunduz qulay"],
    "tr": ["Her gün hazır bir ders", "Önce hatırla, sonra kontrol et", "Tekrar tam zamanında",
           "Ne zaman bitireceğini bil", "Güzel mushaf ve kâriler", "Dürüst ilerleme", "Planın bir dakikada hazır",
           "Gece gündüz rahat"],
}

# The promise (first line of the store listing and of the app) for the feature graphic.
PROMISE = {
    "ru": "Заучивайте Коран каждый день — понемногу и без забывания",
    "en": "Memorize the Quran every day — a little at a time, and never forget it",
    "tg": "Қуръонро ҳар рӯз аз бар кунед — андак-андак ва бе фаромӯшӣ",
    "uz": "Qur’onni har kuni yodlang — oz-ozdan va unutmasdan",
    "tr": "Kur’an’ı her gün ezberleyin — azar azar ve unutmadan",
}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, cur = [], ""
    for word in text.split():
        cand = f"{cur} {word}".strip()
        if not cur or draw.textlength(cand, font=fnt) <= width:
            cur = cand
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def balanced(draw, text, fnt, width):
    """One line if it fits, otherwise the split into two lines with the most even widths (no lonely last word)."""
    if draw.textlength(text, font=fnt) <= width:
        return [text]
    words = text.split()
    best, best_w = None, 10 ** 9
    for k in range(1, len(words)):
        a, b = " ".join(words[:k]), " ".join(words[k:])
        w = max(draw.textlength(a, font=fnt), draw.textlength(b, font=fnt))
        if w < best_w:
            best, best_w = [a, b], w
    return best if best_w <= width else wrap(draw, text, fnt, width)


def fit_text(draw, text, path, width, max_lines, max_size, min_size):
    for size in range(max_size, min_size - 1, -2):
        fnt = font(path, size)
        lines = balanced(draw, text, fnt, width) if max_lines == 2 else wrap(draw, text, fnt, width)
        if len(lines) <= max_lines:
            return fnt, lines
    fnt = font(path, min_size)
    return fnt, wrap(draw, text, fnt, width)


def vertical_gradient(size, top, bottom):
    w, h = size
    base = Image.new("RGB", size, top)
    mask = Image.linear_gradient("L").resize((w, h))
    return Image.composite(Image.new("RGB", size, bottom), base, mask)


def glow(canvas: Image.Image, center, radius, color, alpha):
    layer = Image.new("RGBA", canvas.size, color + (0,))
    blob = Image.radial_gradient("L").resize((radius * 2, radius * 2))
    # the gradient reaches 255 at the corners of its square; scale it so the glow is already 0 at the edge midpoints
    blob = blob.point(lambda v: max(0, 255 - int(v * 1.4142)) * alpha // 255)
    layer.paste(Image.new("RGBA", blob.size, color + (255,)), (center[0] - radius, center[1] - radius), blob)
    canvas.alpha_composite(layer)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def build_screen(raw: Path, headline: str, dark: bool) -> Image.Image:
    W, H = 1080, 1920
    if dark:
        bg = vertical_gradient((W, H), (16, 44, 38), (7, 20, 17)).convert("RGBA")
        glow(bg, (880, 260), 520, (60, 150, 120), 70)
        title_color, bezel = IVORY, (38, 48, 45)
    else:
        bg = vertical_gradient((W, H), (222, 238, 229), (247, 244, 236)).convert("RGBA")
        glow(bg, (940, 180), 560, (214, 180, 100), 80)
        glow(bg, (60, 700), 520, (140, 205, 176), 70)
        title_color, bezel = INK, (22, 28, 26)

    draw = ImageDraw.Draw(bg)
    fnt, lines = fit_text(draw, headline, FONT_BOLD, 920, 2, 76, 54)
    line_h = int(fnt.size * 1.16)
    y = 96 + (2 - len(lines)) * line_h // 2
    for line in lines:
        w = draw.textlength(line, font=fnt)
        draw.text(((W - w) / 2, y), line, font=fnt, fill=title_color)
        y += line_h

    # device: screenshot scaled to 780 wide, bezel 16, bleeding off the bottom edge
    shot = Image.open(raw).convert("RGBA")
    sw = 780
    sh = round(shot.height * sw / shot.width)
    shot = shot.resize((sw, sh), Image.Resampling.LANCZOS)
    shot.putalpha(rounded_mask((sw, sh), 54))
    bz = 16
    dev_w, dev_h = sw + 2 * bz, sh + 2 * bz
    x0, y0 = (W - dev_w) // 2, 318
    shadow = Image.new("RGBA", (dev_w + 160, dev_h + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((80, 80, 80 + dev_w, 80 + dev_h), radius=78, fill=(0, 0, 0, 120 if not dark else 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(36))
    bg.alpha_composite(shadow, (x0 - 80, y0 - 80 + 26))
    dev = Image.new("RGBA", (dev_w, dev_h), (0, 0, 0, 0))
    ImageDraw.Draw(dev).rounded_rectangle((0, 0, dev_w - 1, dev_h - 1), radius=70, fill=bezel + (255,))
    ImageDraw.Draw(dev).rounded_rectangle((2, 2, dev_w - 3, dev_h - 3), radius=68, outline=(255, 255, 255, 40), width=2)
    dev.alpha_composite(shot, (bz, bz))
    bg.alpha_composite(dev, (x0, y0))
    return bg.convert("RGB")


def build_feature(lang: str) -> Image.Image:
    W, H = 1024, 500
    img = vertical_gradient((W, H), (16, 98, 77), (8, 58, 47)).convert("RGBA")
    draw = ImageDraw.Draw(img)
    # concentric rings behind the calligraphy (circles only)
    cx, cy = 790, 262
    for i, r in enumerate((250, 205, 160)):
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(255, 255, 255, 26 + i * 8), width=2)
    glow(img, (cx, cy), 330, (214, 180, 100), 60)
    if CALLIGRAPHY.exists():
        cal = Image.open(CALLIGRAPHY).convert("RGBA")
        target_w = 420
        cal = cal.resize((target_w, round(cal.height * target_w / cal.width)), Image.Resampling.LANCZOS)
        img.alpha_composite(cal, (cx - cal.width // 2, cy - cal.height // 2 - 6))
    draw = ImageDraw.Draw(img)
    draw.text((56, 96), "Quran Hifz", font=font(FONT_BOLD, 60), fill=(247, 244, 237))
    draw.rounded_rectangle((58, 176, 138, 180), radius=2, fill=GOLD)
    fnt, lines = fit_text(draw, PROMISE[lang], FONT_SEMI, 480, 5, 40, 26)
    y = 208
    for line in lines:
        draw.text((56, y), line, font=fnt, fill=(226, 238, 231))
        y += int(fnt.size * 1.3)
    return img.convert("RGB")


def heads(lang: str):
    return HEADLINES_V2[lang] if lang in V2_LANGS else HEADLINES[lang]


def main(langs: list[str]) -> None:
    manifest = {
        "version": "redesign-android-2.0.0",
        "languages": [LOCALES[l] for l in LOCALES],
        "source": "Emulator DoruIman_API_35 (1080x2400), debug build of redesign/android, demo mode status bar, light theme "
                  "(screen 8 dark); captured by capture/capture.py with the demo history of capture/seed_demo.py",
        "phone_size": [1080, 1920],
        "feature_graphic_size": [1024, 500],
        "review_needed": ["tg", "uz", "tr-TR"],
        "exports": {},
    }
    for lang in LOCALES:
        loc = LOCALES[lang]
        entries = []
        if lang in langs:
            phone = OUT / loc / "phone"
            phone.mkdir(parents=True, exist_ok=True)
            for old in phone.glob("*.png"):
                old.unlink()
            for i, (stem, name, dark) in enumerate(screens_for(lang)):
                raw = RAW / lang / f"{stem}.png"
                if not raw.exists():
                    print(f"  MISSING {raw}")
                    continue
                img = build_screen(raw, heads(lang)[i], dark)
                fn = f"phone-{i + 1:02d}-{name}-1080x1920.png"
                img.save(phone / fn, optimize=True)
                print("  wrote", loc, fn)
            build_feature(lang).save(OUT / loc / "feature-graphic-1024x500.png", optimize=True)
            print("  wrote", loc, "feature-graphic")
        entries.append({"file": f"localized/{loc}/feature-graphic-1024x500.png", "size": [1024, 500]})
        for i, (stem, name, dark) in enumerate(screens_for(lang)):
            entries.append({
                "file": f"localized/{loc}/phone/phone-{i + 1:02d}-{name}-1080x1920.png",
                "size": [1080, 1920],
                "source": f"capture/raw/{lang}/{stem}.png",
                "headline": heads(lang)[i],
            })
        manifest["exports"][loc] = entries
    (ROOT / "play-store-export-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:] or list(LOCALES))
