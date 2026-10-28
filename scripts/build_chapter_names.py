#!/usr/bin/env python3
"""Builds the bundled surah names for Tajik (Cyrillic) and Uzbek (Latin).

quran.com v4 answers `chapters?language=tg` and `language=uz` in English (all 114 names), so the app
ships its own names:

    composeApp/src/commonMain/composeResources/files/quran/chapter_names_tg.json
    composeApp/src/commonMain/composeResources/files/quran/chapter_names_uz.json
    docs/redesign/review/chapter-names-tg-uz.md   (the table for a native speaker to check)

The Tajik and Uzbek names are written by hand in NAMES below (transliteration of the Arabic name and the
meaning of the name); the Arabic name is taken from quran.com (`name_arabic`, language=en). They have NOT been
checked by a native speaker yet: after the review, fix NAMES and run this script again.

Usage:
    python3 scripts/build_chapter_names.py                  # fetch the Arabic names and write everything
    python3 scripts/build_chapter_names.py --arabic a.json  # use a saved quran.com response instead of the network

Only the standard library is used.

File format (JSON, UTF-8): {"language": "tg", "chapters": [{"number": 1, "arabic": "...",
"transliteration": "...", "translated": "..."}, ...]} with 114 chapters in order.

Conventions
  Tajik (Cyrillic): the Arabic article "ал-" is dropped (Фотиҳа, not Ал-Фотиҳа); letters ҳ ҷ қ ғ ӯ ӣ are used
  for ح ج ق غ و ي as in Tajik Quran editions; ъ for ع and hamza.
  Uzbek (Latin, the current alphabet): no article either (Fotiha); o‘ and g‘ are written with U+2018, the
  tutuq belgisi with U+2019 (An’om, Ra’d), exactly like the interface strings of the app.
"""

import argparse
import json
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "quran")
REVIEW = os.path.join(ROOT, "docs", "redesign", "review", "chapter-names-tg-uz.md")
CHAPTERS_URL = "https://api.quran.com/api/v4/chapters?language=en"

# (number, tg name, tg meaning, uz name, uz meaning)
NAMES = [
    (1, "Фотиҳа", "Кушоянда", "Fotiha", "Ochuvchi"),
    (2, "Бақара", "Модагов", "Baqara", "Sigir"),
    (3, "Оли Имрон", "Хонадони Имрон", "Oli Imron", "Imron oilasi"),
    (4, "Нисо", "Занон", "Niso", "Ayollar"),
    (5, "Моида", "Дастурхон", "Moida", "Dasturxon"),
    (6, "Анъом", "Чорпоён", "An’om", "Chorva mollar"),
    (7, "Аъроф", "Баландиҳо", "A’rof", "Balandliklar"),
    (8, "Анфол", "Ғаниматҳо", "Anfol", "O‘ljalar"),
    (9, "Тавба", "Тавба", "Tavba", "Tavba"),
    (10, "Юнус", "Юнус", "Yunus", "Yunus"),
    (11, "Ҳуд", "Ҳуд", "Hud", "Hud"),
    (12, "Юсуф", "Юсуф", "Yusuf", "Yusuf"),
    (13, "Раъд", "Раъд", "Ra’d", "Momaqaldiroq"),
    (14, "Иброҳим", "Иброҳим", "Ibrohim", "Ibrohim"),
    (15, "Ҳиҷр", "Сангистони Ҳиҷр", "Hijr", "Toshloq o‘lka"),
    (16, "Наҳл", "Занбӯри асал", "Nahl", "Asalari"),
    (17, "Исро", "Сафари шабона", "Isro", "Tungi sayr"),
    (18, "Каҳф", "Ғор", "Kahf", "G‘or"),
    (19, "Марям", "Марям", "Maryam", "Maryam"),
    (20, "Тоҳо", "Тоҳо", "Toha", "Toha"),
    (21, "Анбиё", "Паёмбарон", "Anbiyo", "Payg‘ambarlar"),
    (22, "Ҳаҷ", "Ҳаҷ", "Haj", "Haj"),
    (23, "Мӯъминун", "Мӯъминон", "Mo‘minun", "Mo‘minlar"),
    (24, "Нур", "Нур", "Nur", "Nur"),
    (25, "Фурқон", "Ҷудокунанда", "Furqon", "Farqlovchi"),
    (26, "Шуаро", "Шоирон", "Shuaro", "Shoirlar"),
    (27, "Намл", "Мӯрчагон", "Naml", "Chumolilar"),
    (28, "Қасас", "Қиссаҳо", "Qasas", "Qissalar"),
    (29, "Анкабут", "Тортанак", "Ankabut", "O‘rgimchak"),
    (30, "Рум", "Румиён", "Rum", "Rumliklar"),
    (31, "Луқмон", "Луқмон", "Luqmon", "Luqmon"),
    (32, "Саҷда", "Саҷда", "Sajda", "Sajda"),
    (33, "Аҳзоб", "Гурӯҳҳо", "Ahzob", "To‘dalar"),
    (34, "Сабо", "Сабо", "Saba", "Saba"),
    (35, "Фотир", "Офаридгор", "Fotir", "Yaratuvchi"),
    (36, "Ёсин", "Ёсин", "Yosin", "Yosin"),
    (37, "Соффот", "Саф кашидагон", "Soffot", "Saf tortganlar"),
    (38, "Сод", "Ҳарфи «Сод»", "Sod", "Sod harfi"),
    (39, "Зумар", "Гурӯҳҳо", "Zumar", "Guruhlar"),
    (40, "Ғофир", "Омӯрзанда", "G‘ofir", "Kechiruvchi"),
    (41, "Фуссилат", "Баёни муфассал", "Fussilat", "Batafsil bayon etilgan"),
    (42, "Шӯро", "Машварат", "Shuro", "Kengash"),
    (43, "Зухруф", "Зеварҳои тиллоӣ", "Zuxruf", "Zeb-ziynat"),
    (44, "Духон", "Дуд", "Duxon", "Tutun"),
    (45, "Ҷосия", "Ба зону афтодагон", "Josiya", "Tiz cho‘kkanlar"),
    (46, "Аҳқоф", "Қумтеппаҳо", "Ahqof", "Qum tepaliklar"),
    (47, "Муҳаммад", "Муҳаммад", "Muhammad", "Muhammad"),
    (48, "Фатҳ", "Пирӯзӣ", "Fath", "G‘alaba"),
    (49, "Ҳуҷурот", "Ҳуҷраҳо", "Hujurot", "Hujralar"),
    (50, "Қоф", "Ҳарфи «Қоф»", "Qof", "Qof harfi"),
    (51, "Зориёт", "Бодҳои пошанда", "Zoriyot", "Sovuruvchi shamollar"),
    (52, "Тур", "Кӯҳи Тур", "Tur", "Tur tog‘i"),
    (53, "Наҷм", "Ситора", "Najm", "Yulduz"),
    (54, "Қамар", "Моҳ", "Qamar", "Oy"),
    (55, "Раҳмон", "Меҳрубон", "Rahmon", "Mehribon"),
    (56, "Воқеа", "Ҳодисаи ногузир", "Voqe’a", "Voqea"),
    (57, "Ҳадид", "Оҳан", "Hadid", "Temir"),
    (58, "Муҷодала", "Мубоҳисакунанда", "Mujodala", "Bahslashgan ayol"),
    (59, "Ҳашр", "Бадарға", "Hashr", "Surgun"),
    (60, "Мумтаҳана", "Имтиҳоншаванда", "Mumtahana", "Imtihon qilinadigan ayol"),
    (61, "Саф", "Сафҳо", "Saf", "Saflar"),
    (62, "Ҷумъа", "Рӯзи ҷумъа", "Juma", "Juma kuni"),
    (63, "Мунофиқун", "Мунофиқон", "Munofiqun", "Munofiqlar"),
    (64, "Тағобун", "Зиёнкорӣ", "Tag‘obun", "Aldanish"),
    (65, "Талоқ", "Талоқ", "Taloq", "Taloq"),
    (66, "Таҳрим", "Манъ кардан", "Tahrim", "Man etish"),
    (67, "Мулк", "Подшоҳӣ", "Mulk", "Hukmronlik"),
    (68, "Қалам", "Қалам", "Qalam", "Qalam"),
    (69, "Ҳоққа", "Ҳақиқат", "Hoqqa", "Haqiqat"),
    (70, "Маъориҷ", "Зинаҳо", "Ma’orij", "Zinapoyalar"),
    (71, "Нуҳ", "Нуҳ", "Nuh", "Nuh"),
    (72, "Ҷин", "Ҷинҳо", "Jin", "Jinlar"),
    (73, "Музаммил", "Ҷомапечида", "Muzzammil", "O‘ranib olgan"),
    (74, "Муддассир", "Ҷомапӯш", "Muddassir", "Burkanib olgan"),
    (75, "Қиёмат", "Рӯзи қиёмат", "Qiyomat", "Qiyomat"),
    (76, "Инсон", "Инсон", "Inson", "Inson"),
    (77, "Мурсалот", "Фиристодагон", "Mursalot", "Yuborilganlar"),
    (78, "Набаъ", "Хабари бузург", "Naba’", "Xabar"),
    (79, "Нозиъот", "Кашандагон", "Nozi’ot", "Tortib oluvchilar"),
    (80, "Абаса", "Рӯ турш кард", "Abasa", "Qovog‘ini soldi"),
    (81, "Таквир", "Печонида шудан", "Takvir", "O‘ralish"),
    (82, "Инфитор", "Шикофта шудан", "Infitor", "Yorilish"),
    (83, "Мутаффифин", "Камфурӯшон", "Mutaffifin", "Kamsitguvchilar"),
    (84, "Иншиқоқ", "Дарида шудан", "Inshiqoq", "Bo‘linish"),
    (85, "Буруҷ", "Бурҷҳо", "Buruj", "Burjlar"),
    (86, "Ториқ", "Омадаи шабона", "Toriq", "Tungi yo‘lovchi"),
    (87, "Аъло", "Баландтарин", "A’lo", "Eng oliy"),
    (88, "Ғошия", "Фарогир", "G‘oshiya", "Qoplab oluvchi"),
    (89, "Фаҷр", "Субҳдам", "Fajr", "Tong"),
    (90, "Балад", "Шаҳр", "Balad", "Shahar"),
    (91, "Шамс", "Офтоб", "Shams", "Quyosh"),
    (92, "Лайл", "Шаб", "Layl", "Tun"),
    (93, "Зуҳо", "Чоштгоҳ", "Zuho", "Chosht"),
    (94, "Шарҳ", "Кушоиш", "Sharh", "Kengaytirish"),
    (95, "Тин", "Анҷир", "Tin", "Anjir"),
    (96, "Алақ", "Лахтаи хун", "Alaq", "Laxta qon"),
    (97, "Қадр", "Шаби қадр", "Qadr", "Qadr kechasi"),
    (98, "Баййина", "Далели равшан", "Bayyina", "Ochiq dalil"),
    (99, "Залзала", "Зилзила", "Zalzala", "Zilzila"),
    (100, "Одиёт", "Давандагон", "Odiyot", "Chopqir otlar"),
    (101, "Қориъа", "Ҳодисаи кӯбанда", "Qori’a", "Dahshat"),
    (102, "Такосур", "Фузунхоҳӣ", "Takosur", "Mol-dunyo talashish"),
    (103, "Аср", "Замон", "Asr", "Zamon"),
    (104, "Ҳумаза", "Ғайбатчӣ", "Humaza", "G‘iybatchi"),
    (105, "Фил", "Фил", "Fil", "Fil"),
    (106, "Қурайш", "Қурайш", "Quraysh", "Quraysh"),
    (107, "Моъун", "Эҳсони андак", "Mo‘un", "Ehson"),
    (108, "Кавсар", "Фаровонӣ", "Kavsar", "Mo‘l-ko‘lchilik"),
    (109, "Кофирун", "Кофирон", "Kofirun", "Kofirlar"),
    (110, "Наср", "Ёрӣ", "Nasr", "Yordam"),
    (111, "Масад", "Ресмони хурмо", "Masad", "Xurmo tolasi"),
    (112, "Ихлос", "Покии эътиқод", "Ixlos", "Sofdillik"),
    (113, "Фалақ", "Бомдод", "Falaq", "Tong yorishi"),
    (114, "Нос", "Мардум", "Nos", "Odamlar"),
]


def load_arabic(path):
    if path:
        with open(path, encoding="utf-8") as handle:
            payload = json.load(handle)
    else:
        request = urllib.request.Request(CHAPTERS_URL, headers={"User-Agent": "quran-hifz-asset-builder"})
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
    names = {chapter["id"]: chapter["name_arabic"].strip() for chapter in payload["chapters"]}
    if sorted(names) != list(range(1, 115)):
        raise ValueError("quran.com did not return 114 chapters")
    return names


def check_names():
    assert [row[0] for row in NAMES] == list(range(1, 115)), "NAMES must list surahs 1..114 in order"
    for number, tg_name, tg_meaning, uz_name, uz_meaning in NAMES:
        for value in (tg_name, tg_meaning, uz_name, uz_meaning):
            assert value.strip() == value and value, "empty or padded name at %d" % number
        assert not any("a" <= ch.lower() <= "z" for ch in tg_name + tg_meaning), "Latin letters in Tajik name %d" % number
        assert not any(0x0400 <= ord(ch) <= 0x04FF for ch in uz_name + uz_meaning), "Cyrillic in Uzbek name %d" % number


def write_json(path, language, arabic, column):
    chapters = []
    for row in NAMES:
        chapters.append(
            {
                "number": row[0],
                "arabic": arabic[row[0]],
                "transliteration": row[column],
                "translated": row[column + 1],
            }
        )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"language": language, "chapters": chapters}, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    print("wrote", path)


def write_review(path, arabic):
    lines = [
        "# Названия сур для tg и uz: список на проверку носителем",
        "",
        "Файлы `chapter_names_tg.json` (кириллица) и `chapter_names_uz.json` (латиница) собраны скриптом "
        "`scripts/build_chapter_names.py` из таблицы `NAMES` в нём. Названия составлены вручную и **ещё не проверены носителем**.",
        "Исправления вносятся в `NAMES`, затем скрипт запускается заново (он перезапишет оба JSON и этот файл).",
        "",
        "Формат ячейки: «написание названия / смысл названия». Договорённости: арабский артикль «ал-» не пишется "
        "(Фотиҳа, Fotiha); таджикский — ҳ ҷ қ ғ ӯ, знак ъ для ع и хамзы; узбекский — o‘ и g‘ через U+2018, "
        "тутуқ белгиси через U+2019 (An’om, Ra’d).",
        "",
        "| № | Арабское | Тоҷикӣ (tg) | O‘zbek (uz) |",
        "|---|---|---|---|",
    ]
    for number, tg_name, tg_meaning, uz_name, uz_meaning in NAMES:
        lines.append("| %d | %s | %s / %s | %s / %s |" % (number, arabic[number], tg_name, tg_meaning, uz_name, uz_meaning))
    lines.append("")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))
    print("wrote", path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--arabic", help="a saved response of %s" % CHAPTERS_URL)
    args = parser.parse_args()

    check_names()
    arabic = load_arabic(args.arabic)
    write_json(os.path.join(RES, "chapter_names_tg.json"), "tg", arabic, 1)
    write_json(os.path.join(RES, "chapter_names_uz.json"), "uz", arabic, 3)
    write_review(REVIEW, arabic)
    return 0


if __name__ == "__main__":
    sys.exit(main())
