from __future__ import annotations

import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parent
CAPTURES = ROOT / "current_app_captures"
PHONE_OUT = ROOT / "screenshots" / "phone"
LOCALIZED_OUT = ROOT / "localized"
BG = ROOT / "marketing_v2" / "background-forest-ai.png"
ICON = ROOT / "icon-512.png"

FONT_REG = "/Library/Fonts/SF-Pro-Display-Regular.otf"
FONT_MED = "/Library/Fonts/SF-Pro-Display-Medium.otf"
FONT_BOLD = "/Library/Fonts/SF-Pro-Rounded-Bold.otf"

IVORY = (247, 244, 237, 255)
SAND = (219, 186, 111, 255)
MINT = (151, 214, 183, 255)
MUTED = (205, 224, 214, 255)
DEVICE = (7, 29, 26, 255)


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def fit_cover(image: Image.Image, size: tuple[int, int], centering: tuple[float, float] = (0.5, 0.5)) -> Image.Image:
    return ImageOps.fit(
        image.convert("RGBA"),
        size,
        method=Image.Resampling.LANCZOS,
        centering=centering,
    )


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size[0] - 1, size[1] - 1),
        radius=radius,
        fill=255,
    )
    return mask


def add_shadow(base: Image.Image, size: tuple[int, int], xy: tuple[int, int], radius: int, alpha: int) -> None:
    mask = rounded_mask(size, radius).filter(ImageFilter.GaussianBlur(28))
    mask = mask.point(lambda value: value * alpha // 255)
    shadow = Image.new("RGBA", size, (0, 0, 0, 255))
    shadow.putalpha(mask)
    base.alpha_composite(shadow, (xy[0], xy[1] + 30))


def draw_wrapped(
    draw: ImageDraw.ImageDraw,
    text: str,
    xy: tuple[int, int],
    max_width: int,
    fnt: ImageFont.FreeTypeFont,
    fill: tuple[int, int, int, int],
    spacing: int,
) -> int:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if not current or draw.textbbox((0, 0), candidate, font=fnt)[2] <= max_width:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)

    x, y = xy
    box = draw.textbbox((0, 0), "Ag", font=fnt)
    line_height = box[3] - box[1]
    for line in lines:
        draw.text((x, y), line, font=fnt, fill=fill)
        y += line_height + spacing
    return y


def add_copy(canvas: Image.Image, eyebrow: str, title: str, subtitle: str) -> None:
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((72, 68, 72 + 250, 116), radius=24, fill=(29, 94, 77, 210))
    draw.text((94, 78), eyebrow.upper(), font=font(FONT_MED, 20), fill=SAND)
    title_bottom = draw_wrapped(
        draw,
        title,
        (74, 132),
        932,
        font(FONT_BOLD, 60),
        IVORY,
        spacing=0,
    )
    draw_wrapped(
        draw,
        subtitle,
        (76, title_bottom + 15),
        900,
        font(FONT_REG, 27),
        MUTED,
        spacing=4,
    )


def add_current_phone(canvas: Image.Image, capture_path: Path, display_w: int = 760) -> None:
    display_h = round(display_w * 2400 / 1080)
    device_w = display_w + 34
    device_h = display_h + 34
    x = (canvas.width - device_w) // 2
    y = 405

    add_shadow(canvas, (device_w, device_h), (x, y), 54, 105)
    device = Image.new("RGBA", (device_w, device_h), DEVICE)
    draw = ImageDraw.Draw(device)
    draw.rounded_rectangle(
        (1, 1, device_w - 2, device_h - 2),
        radius=54,
        outline=(220, 188, 113, 230),
        width=3,
    )
    draw.rounded_rectangle(
        (11, 11, device_w - 12, device_h - 12),
        radius=47,
        outline=(89, 151, 127, 180),
        width=2,
    )

    screen = Image.open(capture_path).convert("RGBA").resize(
        (display_w, display_h),
        Image.Resampling.LANCZOS,
    )
    screen.putalpha(rounded_mask(screen.size, 42))
    device.alpha_composite(screen, (17, 17))
    device.putalpha(rounded_mask(device.size, 54))
    canvas.alpha_composite(device, (x, y))


def make_phone_preview(
    output_dir: Path,
    output_name: str,
    eyebrow: str,
    title: str,
    subtitle: str,
    capture_name: str,
    phone_width: int = 760,
) -> None:
    canvas = fit_cover(Image.open(BG), (1080, 1920), centering=(0.48, 0.45))
    overlay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(overlay).rectangle((0, 0, 1080, 470), fill=(3, 35, 30, 78))
    canvas.alpha_composite(overlay)
    add_copy(canvas, eyebrow, title, subtitle)
    add_current_phone(canvas, CAPTURES / capture_name, display_w=phone_width)
    output_dir.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output_dir / output_name, "PNG", optimize=True)


def add_banner_phone(canvas: Image.Image, capture_name: str, xy: tuple[int, int], width: int, alpha: int = 255) -> None:
    source = Image.open(CAPTURES / capture_name).convert("RGBA")
    height = round(width * source.height / source.width)
    phone = source.resize((width, height), Image.Resampling.LANCZOS)
    phone.putalpha(rounded_mask(phone.size, 24).point(lambda value: value * alpha // 255))
    add_shadow(canvas, phone.size, xy, 24, 70)
    canvas.alpha_composite(phone, xy)


def make_feature_graphic(
    output_path: Path,
    headline_top: str,
    headline_bottom: str,
    subtitle_top: str,
    subtitle_bottom: str,
    badge: str,
    library_capture: str,
    reader_capture: str,
) -> None:
    canvas = fit_cover(Image.open(BG), (1024, 500), centering=(0.48, 0.5))
    shade = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shade).rounded_rectangle((36, 36, 622, 464), radius=36, fill=(4, 42, 36, 105))
    canvas.alpha_composite(shade)

    icon = Image.open(ICON).convert("RGBA").resize((86, 86), Image.Resampling.LANCZOS)
    icon.putalpha(rounded_mask(icon.size, 23))
    canvas.alpha_composite(icon, (66, 68))

    draw = ImageDraw.Draw(canvas)
    draw.text((178, 73), "QURAN HIFZ", font=font(FONT_MED, 21), fill=SAND)
    draw.text((178, 111), headline_top, font=font(FONT_BOLD, 48), fill=IVORY)
    draw.text((66, 176), headline_bottom, font=font(FONT_BOLD, 48), fill=IVORY)
    draw.text((68, 251), subtitle_top, font=font(FONT_REG, 24), fill=MUTED)
    draw.text((68, 284), subtitle_bottom, font=font(FONT_REG, 24), fill=MUTED)
    draw.rounded_rectangle((66, 351, 390, 405), radius=27, fill=(29, 111, 89, 230))
    draw.text((91, 363), badge, font=font(FONT_MED, 21), fill=IVORY)

    add_banner_phone(canvas, library_capture, (682, 46), 215, 235)
    add_banner_phone(canvas, reader_capture, (838, 102), 170, 245)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output_path, "PNG", optimize=True)


LOCALES = {
    "ru-RU": {
        "captures": {
            "library": "quran-library-learning-current.png",
            "reader": "quran-reader-learning-current.png",
            "status": "quran-status-sheet-current.png",
            "settings": "quran-settings-appearance-current.png",
        },
        "banner": {
            "headline_top": "Коран. Хифз.",
            "headline_bottom": "Каждый день.",
            "subtitle_top": "Читайте, слушайте и отмечайте",
            "subtitle_bottom": "свой прогресс.",
            "badge": "Спокойно и по плану",
        },
        "screens": [
            ("Библиотека", "Коран — всегда под рукой", "Поиск, статусы и быстрый возврат к чтению.", "library", 760),
            ("Чтение и аудио", "Читайте. Слушайте. Запоминайте.", "Красивый текст Корана и управление аудио на одном экране.", "reader", 760),
            ("Прогресс", "Отмечайте свой путь", "Новая сура, в процессе или уже выучена — всё видно сразу.", "status", 680),
            ("Офлайн и данные", "Читайте даже без сети", "Сохраняйте аудио и синхронизируйте свой прогресс.", "settings", 760),
        ],
    },
    "en-US": {
        "captures": {
            "library": "en-quran-library-learning-current.png",
            "reader": "en-quran-reader-learning-current.png",
            "status": "en-quran-status-sheet-current.png",
            "settings": "en-quran-settings-appearance-current.png",
        },
        "banner": {
            "headline_top": "Quran. Hifz.",
            "headline_bottom": "Every day.",
            "subtitle_top": "Read, listen, and track",
            "subtitle_bottom": "your progress.",
            "badge": "Calmly, step by step",
        },
        "screens": [
            ("Library", "Quran, always within reach", "Search, track status, and return to reading in one tap.", "library", 760),
            ("Reading & audio", "Read. Listen. Memorize.", "Beautiful Quran text and simple audio controls in one place.", "reader", 760),
            ("Progress", "See your progress", "Not started, learning, or learned — every surah stays clear.", "status", 680),
            ("Offline & data", "Learn even offline", "Save audio and keep your progress in sync.", "settings", 760),
        ],
    },
}


def generate_locale(locale: str, config: dict) -> list[dict]:
    locale_dir = LOCALIZED_OUT / locale
    phone_dir = locale_dir / "phone"
    captures = config["captures"]
    banner = config["banner"]
    feature_path = locale_dir / "feature-graphic-1024x500.png"
    make_feature_graphic(
        feature_path,
        **banner,
        library_capture=captures["library"],
        reader_capture=captures["reader"],
    )

    assets = [{"file": str(feature_path.relative_to(ROOT)), "size": [1024, 500]}]
    names = [
        "phone-01-home-1080x1920.png",
        "phone-02-review-1080x1920.png",
        "phone-03-focus-1080x1920.png",
        "phone-04-settings-1080x1920.png",
    ]
    for output_name, (eyebrow, title, subtitle, capture_key, phone_width) in zip(names, config["screens"]):
        make_phone_preview(
            phone_dir,
            output_name,
            eyebrow,
            title,
            subtitle,
            captures[capture_key],
            phone_width=phone_width,
        )
        assets.append({
            "file": str((phone_dir / output_name).relative_to(ROOT)),
            "size": [1080, 1920],
            "source": captures[capture_key],
        })
    return assets


def main() -> None:
    exports = {locale: generate_locale(locale, config) for locale, config in LOCALES.items()}

    # Keep the legacy paths as the Russian default export for existing tooling.
    PHONE_OUT.mkdir(parents=True, exist_ok=True)
    shutil.copy2(LOCALIZED_OUT / "ru-RU" / "feature-graphic-1024x500.png", ROOT / "feature-graphic-1024x500.png")
    for source in (LOCALIZED_OUT / "ru-RU" / "phone").glob("*.png"):
        shutil.copy2(source, PHONE_OUT / source.name)

    manifest = {
        "version": "current-app-play-store-2026-08-04",
        "languages": ["en-US", "ru-RU"],
        "source": "Fresh Android emulator captures from the current debug build in each locale",
        "exports": exports,
        "typography": {"headings": "SF Pro Rounded Bold", "descriptions": "SF Pro Display Regular"},
        "provenance": "UI pixels are preserved from current app captures. Marketing copy, dimensions, framing, and exports are deterministic.",
    }
    (ROOT / "play-store-export-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
