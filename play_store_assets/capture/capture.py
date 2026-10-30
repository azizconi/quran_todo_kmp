#!/usr/bin/env python3
"""Captures the eight store screens in one language from the debug build on the emulator.

    python3 capture.py <lang> [step ...]        lang: ru en tg uz tr
    steps (default: all, in this order): onboarding seed home lesson progress plan reader dark

Raw 1080x2400 screenshots go to <out>/<lang>/NN_name.png (default out: play_store_assets/capture/raw, not committed).
The device must be the emulator DoruIman_API_35 with the debug APK installed; demo mode (clean status bar) is switched on
and off by demo_mode.sh. Everything is driven through the UI and `adb`; the app code is never touched.

The host can be slow: every wait polls the accessibility tree (uiautomator dump) instead of sleeping blindly.
"""
import os
import re
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
    screenshot(f"{OUT}/{lang}/07_ready.png")
    advance(t["later"], wait=20)      # home without a lesson


def step_seed(lang, t):
    print("seed")
    d, off = today()
    db = pull_db()
    n = subprocess.run(["sqlite3", db, "select count(*) from surah_todo; select count(*) from hifz_plan; select count(*) from hifz_passage"],
                       capture_output=True, text=True, check=True).stdout.split()
    if n != ["12", "1", "11"]:
        raise RuntimeError(f"onboarding left an unexpected state (known surahs, plans, passages): {n}")
    subprocess.run([sys.executable, os.path.join(HERE, "seed_demo.py"), db, str(d), str(off)], check=True)
    push_db(db)


def step_home(lang, t):
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


def step_lesson(lang, t):
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


def step_relaunch(lang, t):
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


def step_progress(lang, t):
    print("progress")
    tap(920, 148); settle(8)
    screenshot(f"{OUT}/{lang}/06_progress_top.png")
    scroll_until(lambda ns: (plan_row(ns) or {}).get("y1"), 1380, 90)
    settle(2)
    screenshot(f"{OUT}/{lang}/06_progress.png")


def step_plan(lang, t):
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


def step_reader(lang, t):
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


def step_dark(lang, t):
    print("dark")
    sh("cmd uimode night yes"); settle(10)
    screenshot(f"{OUT}/{lang}/08_dark.png")
    sh("cmd uimode night no"); settle(5)


STEPS = ["onboarding", "seed", "home", "lesson", "progress", "plan", "reader", "dark"]

if __name__ == "__main__":
    lang = sys.argv[1]
    steps = sys.argv[2:] or STEPS
    t = TEXT[lang]
    for s in steps:
        globals()["step_" + s](lang, t)
    print("done", lang)
