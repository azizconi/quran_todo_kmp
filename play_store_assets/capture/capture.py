#!/usr/bin/env python3
"""Captures the store screens from the debug build on the emulator.

2.0 set (the eight screens of screens_v3):

    ./demo_mode.sh                              # clean status bar, 12:00
    python3 capture.py ru setup                 # once per emulator state: fresh onboarding, demo backup restored, light theme,
                                                #   translation on, reader parked at Al-Mu'minun 18 (needs the emulator date = today)
    python3 capture.py <lang>                   # lang: ru en tg uz tr; switches the app language, then the eight files:
                                                #   01_home 02_reader 03_mushaf 04_tajweed 05_tafsir 06_plan 07_details 08_quiz
    python3 capture.py <lang> setlang reader quiz   # or only some steps (setlang home reader mushaf tajweed tafsir plan details quiz;
                                                #   `translation` puts the tr default translation back after trying others)
    ./run_all.sh                                # setup, then all five languages, then demo mode off

The demo data is `demo/quran-hifz-demo.json` made by make_demo_backup.py for the day of the capture
(`python3 make_demo_backup.py --out /tmp/e3_capture/demo.json`; the setup step pushes that file, or the committed one when missing).
Needs network: translations, tafsir and the tajweed markup are downloaded on first use (the emulator's DNS must work).

The older E3 set (onboarding seed home lesson progress plan reader dark; names 02_recall ... 08_dark) is still in this file as the
steps with the `e3_` prefix and is not used any more.

Raw 1080x2400 screenshots go to <out>/<lang>/NN_name.png (default out: play_store_assets/capture/raw).
The device must be the emulator DoruIman_API_35 with the debug APK installed; demo mode (clean status bar) is switched on
and off by demo_mode.sh. Everything is driven through the UI and `adb`; the app code is never touched.

The host can be slow: every wait polls the accessibility tree (uiautomator dump) instead of sleeping blindly.
"""
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
PKG = "tj.app.quran_todo"
DISPLAY = os.environ.get("DISPLAY_ID", "4619827259835644672")
OUT = os.environ.get("OUT", os.path.join(HERE, "raw"))
TMP = os.environ.get("TMP_DIR", "/tmp/e3_capture")
os.makedirs(TMP, exist_ok=True)

# Texts of the app in each language (from ui/i18n/*Copy.kt) that the driver has to find on screen.
TEXT = {
    "ru": dict(lang_y=1208, next="Далее", skip="Пропустить", later="Позже", notnow="Не сейчас", goal="Заучивать Коран", start="Начать урок", recall="Вспомнить", tab="Вспомни", reveal="Показать", ok="Помню",
               hard="Трудно", grade="Оцените отрывок", done="Готово", home="На главный"),
    "en": dict(lang_y=1397, next="Next", skip="Skip", later="Later", notnow="Not now", goal="Memorize the Quran", start="Start lesson", recall="Recall", tab="Recall", reveal="Reveal", ok="I remember",
               hard="Hard", grade="Grade the passage", done="Done", home="To home"),
    "tg": dict(lang_y=1586, next="Идома", skip="Гузаштан", later="Баъдтар", notnow="Ҳозир не", goal="Қуръонро аз бар кардан", start="Оғози дарс", recall="Ба ёд овардан", tab="Ба ёд ор", reveal="Нишон додан",
               ok="Дар ёд дорам", hard="Душвор", grade="Порчаро баҳо диҳед", done="Тайёр", home="Ба саҳифаи асосӣ"),
    "uz": dict(lang_y=1775, next="Davom etish", skip="O‘tkazish", later="Keyinroq", notnow="Hozir emas", goal="Qur’onni yodlash", start="Darsni boshlash", recall="Eslab aytish", tab="Eslab ayt", reveal="Ko‘rsatish",
               ok="Esimda", hard="Qiyin", grade="Parchani baholang", done="Tayyor", home="Bosh sahifaga"),
    "tr": dict(lang_y=1964, next="Devam", skip="Atla", later="Sonra", notnow="Şimdi değil", goal="Kur’an ezberlemek", start="Derse başla", recall="Hatırla", tab="Hatırla", reveal="Göster",
               ok="Hatırlıyorum", hard="Zor", grade="Bölümü değerlendir", done="Bitti", home="Ana sayfaya"),
}


# ---------------------------------------------------------------- adb helpers
def adb(*args, check=True, text=True):
    return subprocess.run([ADB, *args], capture_output=True, text=text, check=check).stdout


def sh(cmd):
    return adb("shell", cmd)


def tap(x, y):
    adb("shell", "input", "tap", str(int(x)), str(int(y)))


def swipe(x1, y1, x2, y2, ms=900):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def key(name):
    adb("shell", "input", "keyevent", name)


def screenshot(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-d", DISPLAY, "-p"], capture_output=True, check=True).stdout)
    print("  shot", os.path.relpath(path, OUT))


def dump():
    """Visible nodes: list of dicts(text, desc, clickable, x1, y1, x2, y2)."""
    for _ in range(4):
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
        start = xml.find("<?xml")
        if start < 0:
            time.sleep(2)
            continue
        xml = xml[start:xml.rfind("</hierarchy>") + len("</hierarchy>")]
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            time.sleep(2)
            continue
        nodes = []
        for n in root.iter("node"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            nodes.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""), clickable=n.get("clickable") == "true",
                              pkg=n.get("package", ""), x1=x1, y1=y1, x2=x2, y2=y2))
        return nodes
    return []


def find(nodes, label, exact=True):
    out = []
    for n in nodes:
        for t in (n["text"], n["desc"]):
            if t and ((t == label) if exact else (label in t)):
                out.append(n)
                break
    return out


def wait_text(label, timeout=60, exact=True):
    t0 = time.time()
    while time.time() - t0 < timeout:
        hit = find(dump(), label, exact)
        if hit:
            return hit
        time.sleep(1.5)
    raise TimeoutError(f"'{label}' not on screen after {timeout}s")


def tap_node(n):
    tap((n["x1"] + n["x2"]) / 2, (n["y1"] + n["y2"]) / 2)


def tap_text(label, timeout=60, lowest=True):
    hits = wait_text(label, timeout)
    hits.sort(key=lambda n: n["y1"])
    tap_node(hits[-1] if lowest else hits[0])
    time.sleep(1.5)


def signature():
    return tuple(sorted({n["text"] for n in dump() if n["text"]}))


def advance(label, tries=3, wait=40):
    """Taps the button and makes sure the screen changed and came to rest (a tap that lands in a transition is lost on a slow host)."""
    before = signature()
    for _ in range(tries):
        tap_text(label)
        t0 = time.time()
        prev = None
        while time.time() - t0 < wait:
            time.sleep(1.5)
            cur = signature()
            if cur != before:
                time.sleep(6)   # first change seen: let the transition finish; never tap again
                return
            prev = cur
    raise RuntimeError(f"screen did not change after tapping {label}")


def settle(sec=4):
    time.sleep(sec)


# ---------------------------------------------------------------- state helpers
def today():
    now = int(sh("date +%s").strip())
    tz = sh("date +%z").strip()
    off = (int(tz[1:3]) * 3600 + int(tz[3:5]) * 60) * (1 if tz[0] == "+" else -1)
    return (now + off) // 86400, off


def launch(wait=20):
    sh(f"am start -n {PKG}/.MainActivity")
    time.sleep(wait)


def fresh_install_state():
    sh(f"pm clear {PKG}")
    # the tours (spotlights) are one-time: mark them seen, the store shots must be clean
    sp = os.path.join(TMP, "spotlights.xml")
    with open(sp, "w") as f:
        f.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for k in ("home", "reader", "lesson", "lesson_peek", "lesson_grade", "player"):
            f.write(f'    <boolean name="seen_{k}" value="true" />\n')
        f.write("</map>\n")
    adb("push", sp, "/data/local/tmp/spotlights.xml")
    sh(f"run-as {PKG} sh -c 'mkdir -p shared_prefs; cp /data/local/tmp/spotlights.xml shared_prefs/quran_hifz_spotlights.xml'")


def pull_db():
    sh(f"am force-stop {PKG}")
    time.sleep(1)
    db = os.path.join(TMP, "quran_todo.db")
    for suffix in ("", "-wal", "-shm"):
        data = subprocess.run([ADB, "exec-out", "run-as", PKG, "cat", f"databases/quran_todo.db{suffix}"],
                              capture_output=True, check=True).stdout
        with open(db + suffix, "wb") as f:
            f.write(data)
    subprocess.run(["sqlite3", db, "PRAGMA wal_checkpoint(TRUNCATE);"], check=True, capture_output=True)
    for suffix in ("-wal", "-shm"):
        if os.path.exists(db + suffix):
            os.remove(db + suffix)
    return db


def push_db(db):
    sh(f"am force-stop {PKG}")
    adb("push", db, "/data/local/tmp/q.db")
    sh(f"run-as {PKG} sh -c 'rm -f databases/quran_todo.db-wal databases/quran_todo.db-shm; cp /data/local/tmp/q.db databases/quran_todo.db'")


# ---------------------------------------------------------------- steps
def step_onboarding(lang, t):
    print("onboarding")
    fresh_install_state()
    launch(25)
    tap(540, t["lang_y"]); settle(2)
    advance(t["next"])                 # language -> the slides
    advance(t["skip"])                 # skip the slides
    if not find(dump(), t["goal"]):
        advance(t["next"])             # Skip may land on the last slide
    tap_text(t["goal"]); settle(3)       # goal: memorize
    advance(t["next"])
    for x, y in [(290, 594), (790, 594), (290, 762), (790, 762), (290, 930), (790, 930), (290, 1098), (790, 1098),
                 (290, 1266), (790, 1266), (290, 1434)]:   # Al-Fatiha and An-Nas ... Al-Fil
        tap(x, y); time.sleep(1.5)
    settle(3)
    advance(t["next"])                 # known surahs -> what to learn
    advance(t["next"])                 # preset: Juz Amma (preselected) -> pace
    advance(t["next"])                 # pace: 5 ayahs a day -> reminder
    advance(t["notnow"])               # reminder: not now
    advance(t["next"])                 # reading look -> the plan is ready
    wait_text(t["later"], 30)
    settle(4)
    advance(t["later"], wait=20)      # home without a lesson


def e3_seed(lang, t):
    print("seed")
    d, off = today()
    db = pull_db()
    n = subprocess.run(["sqlite3", db, "select count(*) from surah_todo; select count(*) from hifz_plan; select count(*) from hifz_passage"],
                       capture_output=True, text=True, check=True).stdout.split()
    if n != ["12", "1", "11"]:
        raise RuntimeError(f"onboarding left an unexpected state (known surahs, plans, passages): {n}")
    subprocess.run([sys.executable, os.path.join(HERE, "seed_demo.py"), db, str(d), str(off)], check=True)
    push_db(db)


def e3_home(lang, t):
    print("home")
    sh(f"am force-stop {PKG}")
    launch(10)
    wait_text(t["start"], 90)
    settle(4)
    screenshot(f"{OUT}/{lang}/01_home.png")


def lesson_loop(lang, t, shot_at):
    """Works through every card of today's lesson. shot_at: number of the revealed New ayah to photograph (3)."""
    revealed_new = 0
    taken = False
    hard_done = False
    for _ in range(120):
        nodes = dump()
        if find(nodes, t["home"]):
            return
        if find(nodes, t["grade"]):
            tap_text(t["done"]); settle(3); continue
        rev = find(nodes, t["reveal"])
        if rev:
            tap_node(rev[0]); settle(2.5)
            revealed_new += 1
            if revealed_new == shot_at and not taken:
                settle(2)
                screenshot(f"{OUT}/{lang}/02_recall.png")
                taken = True
            hits = wait_text(t["hard"] if (revealed_new == 2 and not hard_done) else t["ok"], 30)
            if revealed_new == 2 and not hard_done:
                hard_done = True
            hits.sort(key=lambda n: n["y1"])
            tap_node(hits[-1]); settle(3)
            continue
        rc = [n for n in find(nodes, t["recall"]) if n["clickable"] or True]
        # tab "Recall" of the new step is a tab, the card button of a repetition has the longer verb; both are tapped once
        if rc and not find(nodes, t["ok"]):
            # in the new step the Recall tab selects the recall mode, in a repetition the button starts it
            lowest = sorted(rc, key=lambda n: n["y1"])[-1]
            tap_node(lowest); settle(3)
            continue
        time.sleep(2)
    raise RuntimeError("lesson did not finish")


def e3_lesson(lang, t):
    print("lesson")
    tap_text(t["start"], 60); settle(6)
    # the new step opens on Listen; switch to Recall
    tabs = wait_text(t["tab"], 60)
    tabs.sort(key=lambda n: n["y1"])
    tap_node(tabs[0]); settle(4)
    lesson_loop(lang, t, 3)
    settle(5)
    screenshot(f"{OUT}/{lang}/03_summary.png")
    tap_text(t["home"]); settle(6)


def step_setlang(lang, t):
    """Switches the app language without redoing the demo: the data is the same in every language, only the text differs."""
    sh(f"am force-stop {PKG}")
    f = os.path.join(TMP, "prefs.xml")
    data = subprocess.run([ADB, "exec-out", "run-as", PKG, "cat", "shared_prefs/quran_todo_prefs.xml"], capture_output=True, check=True).stdout.decode()
    data = re.sub(r'(<string name="language_code">)[a-z]+(</string>)', rf"\g<1>{lang}\g<2>", data)
    with open(f, "w") as fh:
        fh.write(data)
    adb("push", f, "/data/local/tmp/prefs.xml")
    sh(f"run-as {PKG} sh -c 'cp /data/local/tmp/prefs.xml shared_prefs/quran_todo_prefs.xml'")


def e3_relaunch(lang, t):
    sh(f"am force-stop {PKG}")
    launch(10)
    settle(8)


def plan_row(nodes):
    """The wide clickable card "Juz Amma: you will finish ..." of the Progress screen."""
    c = [n for n in nodes if n["clickable"] and n["x2"] - n["x1"] > 900 and 150 < n["y2"] - n["y1"] < 300 and n["y1"] > 300]
    return sorted(c, key=lambda n: n["y1"])[0] if c else None


def scroll_until(locate, target, tol, tries=7):
    """Scrolls (from the page margin, so no picker is touched) until locate(nodes) is within tol of target."""
    for _ in range(tries):
        y = locate(dump())
        if y is None:
            swipe(25, 1800, 25, 1000, 1200); settle(3); continue
        delta = y - target
        if abs(delta) <= tol:
            return
        d = max(-900, min(900, delta))
        if d > 0:
            swipe(25, 1800, 25, 1800 - d, 1200)
        else:
            swipe(25, 700, 25, 700 - d, 1200)
        settle(3)
    raise RuntimeError("could not scroll to the target")


def e3_progress(lang, t):
    print("progress")
    tap(920, 148); settle(8)
    screenshot(f"{OUT}/{lang}/06_progress_top.png")
    scroll_until(lambda ns: (plan_row(ns) or {}).get("y1"), 1380, 90)
    settle(2)
    screenshot(f"{OUT}/{lang}/06_progress.png")


def e3_plan(lang, t):
    print("plan")
    row = plan_row(dump())
    if not row:
        raise RuntimeError("plan row not found")
    tap_node(row); settle(8)
    screenshot(f"{OUT}/{lang}/04_plan_top.png")
    five = lambda ns: next((n["y1"] for n in ns if n["text"] == "5" and 400 < (n["x1"] + n["x2"]) / 2 < 680), None)
    scroll_until(five, 770, 60)
    settle(2)
    screenshot(f"{OUT}/{lang}/04_plan.png")
    key("KEYCODE_BACK"); settle(3)
    key("KEYCODE_BACK"); settle(4)


def e3_reader(lang, t):
    print("reader")
    sh(f"am force-stop {PKG}")
    launch(10)
    settle(8)
    # "Read" on the hero card: the left one of the two buttons under the first surah
    btn = [n for n in dump() if n["clickable"] and n["x1"] < 100 and 380 < n["x2"] - n["x1"] < 560 and n["y1"] > 1200]
    if not btn:
        raise RuntimeError("Read button not found")
    btn.sort(key=lambda n: n["y1"])
    tap_node(btn[0]); settle(12)
    screenshot(f"{OUT}/{lang}/05_reader.png")


def e3_dark(lang, t):
    print("dark")
    sh("cmd uimode night yes"); settle(10)
    screenshot(f"{OUT}/{lang}/08_dark.png")
    sh("cmd uimode night no"); settle(5)



# ================================================================= the 2.0 set
I18N = os.path.join(HERE, "..", "..", "composeApp", "src", "androidMain", "kotlin", "tj", "app", "quran_todo", "ui", "i18n")


def copy_text(file, lang, key):
    """A string of the app's own copy file (i18n/<file>.kt) in the language, so the driver finds buttons by the app's words."""
    src = open(os.path.join(I18N, file + ".kt"), encoding="utf-8").read()
    heads = list(re.finditer(r"AppLanguage\.([A-Z]{2}) -> \w+Copy\(", src))
    for i, m in enumerate(heads):
        if m.group(1).lower() == lang:
            block = src[m.end(): heads[i + 1].start() if i + 1 < len(heads) else len(src)]
            hit = re.search(r'\b' + key + r' = "((?:[^"\\]|\\.)*)"', block)
            if hit:
                return hit.group(1).replace('\\"', '"').replace("\\\\", "\\")
    raise KeyError(f"{file}.{key} [{lang}]")


def px(path, x, y):
    from PIL import Image
    with Image.open(path) as im:
        return im.convert("RGB").getpixel((x, y))


def grab(name="grab.png"):
    """A screenshot into the temp folder (for analysis, not for output)."""
    p = os.path.join(TMP, name)
    with open(p, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-d", DISPLAY, "-p"], capture_output=True, check=True).stdout)
    return p


def word_highlight_x(path, y0=940, y1=1220):
    """Centre x of the highlight of a playing word (a soft green box, about 190,211,201) in the rows y0..y1, or None."""
    from PIL import Image
    with Image.open(path) as im:
        im = im.convert("RGB").crop((60, y0, 1020, y1))
        w = im.size[0]
        xs = [i % w for i, (r, g, b) in enumerate(im.getdata()) if abs(r - 190) < 7 and abs(g - 211) < 7 and abs(b - 201) < 7]
    if len(xs) < 2500:
        return None
    return 60 + sum(xs) / len(xs)


def align(locate, target, tol, tries=30):
    """Scrolls the page until locate(nodes) (a y) is within tol of target. Short moves are slow swipes: a quick short drag is a tap."""
    for _ in range(tries):
        y = locate(dump())
        if y is None:
            swipe(25, 2100, 25, 600, 1300); settle(2); continue            # not on screen yet: one screen down (the badge is never skipped)
        delta = y - target
        print("  align", y, "->", target)
        if abs(delta) <= tol:
            return y
        d = abs(delta) + 22                      # the first ~20 px of a drag are the touch slop
        if abs(delta) > 60:
            d = min(d, 1000)
            swipe(25, 1800, 25, 1800 - d, 1200) if delta > 0 else swipe(25, 600, 25, 600 + d, 1200)
        else:
            swipe(25, 1200, 25, 1200 - d, 450) if delta > 0 else swipe(25, 1200, 25, 1200 + d, 450)
        settle(2.5)
    raise RuntimeError("could not align the page")


def ayah_badge_y(nodes, n):
    """Top of the number badge of ayah n in the list reader."""
    for nd in nodes:
        if nd["text"] == str(n) and nd["x1"] < 130 and 230 < nd["y1"] < 2050 and nd["x2"] - nd["x1"] < 80:
            return nd["y1"]
    return None


def home_ready(t, timeout=90):
    wait_text(t["start"], timeout)
    settle(3)


def launch_home(t):
    sh(f"am force-stop {PKG}")
    launch(8)
    home_ready(t)


def back_home(t, tries=6):
    for _ in range(tries):
        if find(dump(), t["start"]):
            return
        key("KEYCODE_BACK"); settle(2.5)
    raise RuntimeError("could not get back to the home screen")


def tap_desc(label, timeout=30):
    t0 = time.time()
    while time.time() - t0 < timeout:
        hit = [n for n in dump() if n["desc"] == label or n["text"] == label]
        if hit:
            tap_node(hit[0]); time.sleep(1.5)
            return hit[0]
        time.sleep(1.5)
    raise TimeoutError(label)


def open_reader(t):
    """Home -> "Read" on the Continue card (Al-Mu'minun), the list reader at the saved ayah."""
    for n in dump():
        if n["text"] == "المؤمنون" and n["x1"] > 700 and n["y1"] > 1000:
            tap(346, n["y1"] + 299)
            break
    else:
        raise RuntimeError("Continue card not found")
    for _ in range(40):
        time.sleep(1.5)
        ns = dump()
        if ayah_badge_y(ns, 18) is not None:
            break
        if any(n["y1"] < 200 and n["x1"] == 600 for n in ns) or any(n["y1"] < 200 and n["x1"] == 978 for n in ns):
            break
    settle(2)
    ns = dump()
    if not any(n["x1"] == 600 and 100 < n["y1"] < 140 for n in ns):    # the "Aa" button exists only in the list mode
        tap(1006, 148); settle(4)                                       # the pages mode was left on: back to the list


def park_on_18():
    """Ayah 18 with its page marker 19 px above the ru reference (the header then reads 18 / 118 and the Continue card says ayah 18)."""
    align(lambda ns: ayah_badge_y(ns, 18), 328, 9)


def settings_switch(title, want):
    """In the "Aa" sheet: the switch of the row `title`. Reads its state from the screenshot (the knob is on the right when it is on)."""
    n = wait_text(title, 10)[0]
    y = (n["y1"] + n["y2"]) // 2
    p = grab()
    on = sum(1 for dx in range(860, 980, 6) if abs(px(p, dx, y)[0] - 16) < 25 and abs(px(p, dx, y)[1] - 109) < 30) > 6
    if on != want:
        tap(916, y); settle(3)


def step_setup(lang, t):
    """Fresh app state: onboarding in Russian, the demo backup restored, light theme, translation on, tajweed downloaded and off."""
    t = TEXT["ru"]
    step_onboarding("ru", t)
    demo = os.path.join(TMP, "demo.json")
    if not os.path.exists(demo):
        demo = os.path.join(HERE, "demo", "quran-hifz-demo.json")
    adb("push", demo, "/sdcard/Download/quran-hifz-demo.json")
    sh("settings put global sysui_demo_allowed 1")
    wait_text(t["start"], 60)
    tap(1006, 148); settle(4)                                   # settings
    tap_text(copy_text("SettingsCopy", "ru", "themeLight")); settle(2)
    for _ in range(8):
        if find(dump(), copy_text("TransferCopy", "ru", "restoreTitle")):
            break
        swipe(25, 1900, 25, 400, 1000); settle(2)
    tap_text(copy_text("TransferCopy", "ru", "restoreTitle")); settle(5)
    tap_text("quran-hifz-demo.json"); settle(3)                 # the system file picker
    tap_text(copy_text("TransferCopy", "ru", "replace")); settle(6)
    key("KEYCODE_BACK"); settle(3)
    home_ready(t)
    # reader at Al-Mu'minun: translation on, tajweed on once (downloads the markup), then off
    for _ in range(10):
        if find(dump(), "Аль-Муминун"):
            break
        swipe(25, 1900, 25, 400, 1200); settle(2)
    tap_text("Аль-Муминун"); settle(6)
    tap(629, 148); settle(3)                                    # "Aa"
    settings_switch("Перевод", True)
    settings_switch(copy_text("TajweedCopy", "ru", "switchTitle"), True)
    settle(12)                                                  # the markup downloads
    settings_switch(copy_text("TajweedCopy", "ru", "switchTitle"), False)
    key("KEYCODE_BACK"); settle(3)
    park_on_18()
    key("KEYCODE_BACK"); settle(3)


def step_home(lang, t):
    print("home")
    launch_home(t)
    screenshot(f"{OUT}/{lang}/01_home.png")


def step_reader(lang, t):
    print("reader")
    launch_home(t)
    open_reader(t)
    park_on_18()
    # play ayah 18, then the first frame with a highlighted word of the playing ayah
    tap(540, 560); settle(2)                                    # selects ayah 18: the bar says "ayah 18"
    tap(856, 2246); settle(1)                                   # play
    best = None
    for _ in range(120):
        p = grab()
        x = word_highlight_x(p)
        if x is not None:
            if best is None:
                best = os.path.join(TMP, "first_word.png")
                shutil.copy(p, best)                            # fallback: the first highlighted word
            if 330 < x < 820:                                   # a word in the middle of the line, like the reference
                best = p
                break
    if best is None:
        raise RuntimeError("no highlighted word while playing")
    p = best
    os.makedirs(f"{OUT}/{lang}", exist_ok=True)
    shutil.copy(p, f"{OUT}/{lang}/02_reader.png")
    print("  shot", f"{lang}/02_reader.png")
    tap(856, 2246); settle(2)                                   # pause


def reader_ready(t):
    """Fresh start in the list reader of Al-Mu'minun, ayah 18 parked at the top like in the reference."""
    launch_home(t)
    open_reader(t)
    park_on_18()


def pages_after_playing_18():
    """Ayah 18 becomes the current one by playing it for a moment; the mushaf pages then open on page 343 (the list reader's
    own position is ayah 17, page 342), and the stop button of the mini player gives the clean page of the reference."""
    tap(540, 560); settle(2)                                    # selects ayah 18
    tap(856, 2246); settle(4)                                   # play ... pause
    tap(856, 2246); settle(2)
    tap(755, 148)                                               # the book icon: mushaf pages
    wait_text("343", 40)
    settle(3)
    tap(974, 2148); settle(3)                                   # the cross of the mini player: stop, the page is clean


def step_mushaf(lang, t):
    print("mushaf")
    reader_ready(t)
    pages_after_playing_18()
    screenshot(f"{OUT}/{lang}/03_mushaf.png")
    tap(1006, 148); settle(3)                                   # back to the list


def set_tajweed(lang, want):
    tap(629, 148); settle(3)                                    # "Aa"
    settings_switch(copy_text("TajweedCopy", lang, "switchTitle"), want)
    key("KEYCODE_BACK"); settle(3)


def step_tajweed(lang, t):
    print("tajweed")
    reader_ready(t)
    pages_after_playing_18()                                    # ayah 18 is now the current one: the bar says "ayah 18", paused
    tap(1006, 148); settle(4)
    park_on_18()
    set_tajweed(lang, True)
    settle(3)
    park_on_18()
    screenshot(f"{OUT}/{lang}/04_tajweed.png")
    set_tajweed(lang, False)


def step_translation(lang, t):
    """Puts the language's default translation back (a trial of the other editions leaves the choice on): only tr needs it."""
    names = dict(tr="Elmalılı")
    if lang not in names:
        return
    print("translation")
    reader_ready(t)
    tap(629, 148); settle(3)                                    # "Aa"
    row = [n for n in dump() if ":" in n["text"] and 1400 < n["y1"] < 1600 and n["x1"] < 200]
    if not row:
        raise RuntimeError("translation row not found")
    tap_node(row[0]); settle(4)
    tap_text(names[lang]); settle(6)
    key("KEYCODE_BACK"); settle(3)


TAFSIR_CHIP = dict(ru="chipSaadi", en="chipIbnKathir", tg="chipSaadi", uz="chipMokhtasarUz", tr="chipMokhtasarTr")


def step_tafsir(lang, t):
    print("tafsir")
    reader_ready(t)
    swipe(540, 560, 540, 560, 1000); settle(3)                  # long press on ayah 18: its sheet
    tap_text(copy_text("TafsirCopy", lang, "sheetRow")); settle(4)
    # the source of the language (the choice is remembered, so it is tapped every time): ru, tg: as-Saadi; en: Ibn Kathir;
    # uz, tr: the Mukhtasar of QuranEnc in the language
    chip = copy_text("TafsirCopy", lang, TAFSIR_CHIP[lang])
    hits = [n for n in dump() if chip in n["text"] and 1150 < n["y1"] < 1350]
    if not hits:
        raise RuntimeError("tafsir chip not found: " + chip)
    tap_node(hits[0]); settle(6)
    loading = copy_text("TafsirCopy", lang, "loading")
    for _ in range(40):
        ns = dump()
        if not find(ns, loading) and any(len(n["text"]) > 200 for n in ns):
            break
        time.sleep(2)
    else:
        raise RuntimeError("tafsir did not load")
    settle(3)
    screenshot(f"{OUT}/{lang}/05_tafsir.png")


def step_plan(lang, t):
    print("plan")
    launch_home(t)
    tap(1006, 148); settle(4)                                   # settings
    title = copy_text("PlanCopy", lang, "title")
    for _ in range(6):                                          # the row "My plan" is in the middle of the settings
        hits = [n for n in dump() if n["text"] == title and 300 < n["y1"] < 2200]
        if hits:
            tap_node(hits[0]); break
        swipe(25, 1900, 25, 700, 1200); settle(2)
    else:
        raise RuntimeError("plan row not found")
    settle(6)
    wait_text(title, 30)
    settle(3)
    screenshot(f"{OUT}/{lang}/06_plan.png")


DETAILS_FIRST_PERCENT_Y = 774        # top of the first retention percentage ("88%") in the ru capture


def step_details(lang, t):
    print("details")
    launch_home(t)
    tap(880, 148); settle(6)                                    # the progress ring
    title = copy_text("AnalyticsCopy", lang, "detailsTitle")
    for _ in range(6):
        hits = [n for n in dump() if n["text"] == title and 150 < n["y1"] < 2100 and n["x1"] > 150]
        if hits:
            tap_node(hits[0]); break
        swipe(25, 1900, 25, 700, 1200); settle(2)
    else:
        raise RuntimeError("details row not found")
    want = copy_text("AnalyticsCopy", lang, "retentionTitle").upper()
    for _ in range(30):                                         # the section label is set in capitals
        if any(n["text"].upper() == want for n in dump()):
            break
        time.sleep(1.5)
    else:
        raise RuntimeError("details screen did not open")
    settle(5)
    # the hint under "Retention" takes two lines in ru, uz, tr and three in en, tg: the bars of those two are 53 px lower than in ru
    # (a scroll to fix that would leave the large title half faded; the template's crop for them is set per language instead)
    ns = dump()
    first = next((n["y1"] for n in sorted(ns, key=lambda n: n["y1"]) if re.fullmatch(r"%?\d+%?", n["text"]) and n["y1"] > 500), None)
    print("  first retention percentage at y =", first, "(ru:", DETAILS_FIRST_PERCENT_Y, ")")
    settle(2)
    screenshot(f"{OUT}/{lang}/07_details.png")


def quiz_cards(ns):
    """The ayah cards of a "put in order" question, top to bottom (a card has several nodes: one per y)."""
    wide = sorted([n for n in ns if n["x2"] - n["x1"] > 900 and 150 < n["y2"] - n["y1"] < 450 and 450 < n["y1"] < 1700], key=lambda n: n["y1"])
    cards = []
    for n in wide:
        if not cards or n["y1"] - cards[-1]["y1"] > 60:
            cards.append(n)
    return cards


def quiz_advance(ns, Q):
    """Answers the question on screen the quickest way (the quiz is only walked through to reach the "put in order" question)."""
    for key_ in ("show", "knew"):
        hit = find(ns, Q(key_))
        if hit:
            tap_node(hit[0]); return True
    for key_ in ("next", "seeResult"):
        hit = find(ns, Q(key_))
        if hit:
            tap_node(hit[0]); return True
    options = [n for n in ns if n["desc"] and n["x1"] == 53 and n["x2"] == 1027 and 130 < n["y2"] - n["y1"] < 190]   # "where is this ayah"
    if options:
        tap_node(options[0]); return True
    return False


def step_quiz(lang, t):
    """The quiz has five random questions: it is walked through until a "put in order" question comes up (about 1 quiz in 3)."""
    print("quiz")
    Q = lambda k: copy_text("QuizCopy", lang, k)
    title, order = Q("title"), Q("orderTitle")
    progress = Q("progressPattern").replace("{a}", r"\d+").replace("{b}", r"\d+")
    for attempt in range(25):
        launch_home(t)
        hits = [n for n in dump() if n["text"] == title and 1000 < n["y1"] < 1500]
        if not hits:
            raise RuntimeError("quiz row not found")
        tap_node(hits[0])
        for _ in range(40):
            time.sleep(1.5)
            if any(re.fullmatch(progress, n["text"]) for n in dump()):
                break
        settle(2)
        for action in range(30):                                 # a question takes a few taps: show, knew / answer, next
            ns = dump()
            if find(ns, order):
                cards = quiz_cards(ns)
                if len(cards) == 3 and all(c["y2"] - c["y1"] < 280 for c in cards):     # the three-ayah variant, one line per card
                    for i in (1, 2, 0):                          # numbers 3, 1, 2 from the top, like the reference
                        tap_node(cards[i]); settle(2)
                    settle(2)
                    screenshot(f"{OUT}/{lang}/08_quiz.png")
                    return
                break
            if not quiz_advance(ns, Q):
                break
            settle(3)
        print("  attempt", attempt + 1, "no order question")
    raise RuntimeError("no order question in 25 quizzes")


STEPS = ["home", "reader", "mushaf", "tajweed", "tafsir", "plan", "details", "quiz"]

if __name__ == "__main__":
    lang = sys.argv[1]
    steps = sys.argv[2:]
    if not steps:
        steps = ["setlang"] + STEPS
    t = TEXT[lang]
    for s in steps:
        fn = globals().get("step_" + s) or globals()["e3_" + s]
        fn(lang, t)
    print("done", lang)
