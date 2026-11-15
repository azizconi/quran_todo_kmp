#!/usr/bin/env python3
"""Builds App Store metadata (en-US, ru, tr) from the Google Play listings.

App Store has no Tajik or Uzbek store locale; the app itself keeps all 5 languages.
Output: play_store_assets/app_store/<locale>/{name,subtitle,keywords,promotional_text,description}.txt
"""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent
LISTING = ROOT.parent / "listing"

LOCALES = {
    "en-US": dict(
        src="en-US.md",
        subtitle="Daily lesson, review, mushaf",
        keywords="memorize,hafiz,tajweed,mushaf,juz amma,murajaah,sabaq,manzil,koran,reciter,islam,muslim,revision",
        drop=["record your own recitation", "Anonymous statistics"],
        add_after=("No account needed.", "• The app collects no data: no analytics, no crash reports."),
    ),
    "ru": dict(
        src="ru-RU.md",
        subtitle="Урок дня, повторение, мусхаф",
        keywords="хифз,заучивание,хафиз,таджвид,мусхаф,джуз амма,повторение,чтец,ислам,мусульманин,сура,аят",
        drop=["Свою запись можно сделать", "Анонимная статистика"],
        add_after=None,
    ),
    "tr": dict(
        src="tr-TR.md",
        subtitle="Günün dersi, tekrar, mushaf",
        keywords="hafızlık,ezber,ezberleme,tecvid,mushaf,amme cüzü,tekrar,kâri,islam,müslüman,sure,ayet,kuran",
        drop=["kendi okuyuşunuzu kaydedip", "Anonim istatistikler"],
        add_after=None,
    ),
}
NO_DATA = {
    "ru": "• Приложение не собирает данные: ни статистики, ни отчётов о сбоях.",
    "tr": "• Uygulama veri toplamaz: istatistik ve çökme raporu yok.",
}


def section(text, name):
    m = re.search(rf"^## {name}\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    return m.group(1).strip()


for loc, cfg in LOCALES.items():
    text = (LISTING / cfg["src"]).read_text()
    desc_lines = []
    for line in section(text, "Full description").splitlines():
        if any(d in line for d in cfg["drop"]):
            if "statist" in line.lower() or "статист" in line.lower() or "istatist" in line.lower():
                desc_lines.append(NO_DATA.get(loc, "• The app collects no data: no analytics and no crash reports."))
            continue
        desc_lines.append(line)
    desc = "\n".join(desc_lines)
    out = {
        "name": section(text, "Title"),
        "subtitle": cfg["subtitle"],
        "keywords": cfg["keywords"],
        "promotional_text": section(text, "Short description"),
        "description": desc,
    }
    limits = {"name": 30, "subtitle": 30, "promotional_text": 170, "description": 4000}
    for k, lim in limits.items():
        assert len(out[k]) <= lim, (loc, k, len(out[k]))
    assert len(out["keywords"]) <= 100, (loc, "keywords", len(out["keywords"]))
    d = ROOT / loc
    d.mkdir(exist_ok=True)
    for k, v in out.items():
        (d / f"{k}.txt").write_text(v + "\n")
    print(loc, {k: len(v) for k, v in out.items()}, "kw bytes", len(out["keywords"].encode()))
