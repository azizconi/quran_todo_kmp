#!/usr/bin/env python3
"""Turns play_store_assets/privacy-policy-{en,ru}.md into the GitHub Pages site (branch gh-pages):
privacy/index.html (English), privacy/ru.html (Russian) and a root index.html that points at the English page.

    python3 play_store_assets/make_policy_pages.py OUT_DIR

Only the Markdown the policies use is handled: #-headings, paragraphs, "- " lists, **bold**, `code`, bare URLs.
"""
import html
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSS = """:root{color-scheme:light dark;--bg:#faf8f3;--ink:#1d2a26;--muted:#56635e;--accent:#0f6b52;--line:#e2ddd2}
@media (prefers-color-scheme:dark){:root{--bg:#0f1513;--ink:#e8eeeb;--muted:#a3b1ab;--accent:#5fd0a6;--line:#26322e}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:32px 16px 64px}h1{font-size:2rem;line-height:1.2}h2{margin-top:2.2em;border-top:1px solid var(--line);padding-top:1.2em}
h3{margin-top:1.6em}a{color:var(--accent)}code{font-size:.9em;background:var(--line);padding:1px 5px;border-radius:4px;overflow-wrap:anywhere}
nav{font-size:.95rem;color:var(--muted)}li{margin:.3em 0}"""


def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"(?<![\"=>])(https?://[^\s<)]+[^\s<).,;])", r'<a href="\1">\1</a>', s)
    s = re.sub(r"([\w.+-]+@[\w-]+\.[\w.]+)", r'<a href="mailto:\1">\1</a>', s)
    return s


def convert(md):
    out, para, items = [], [], []

    def flush():
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>")
            para.clear()
        if items:
            out.append("<ul>" + "".join(f"<li>{inline(i)}</li>" for i in items) + "</ul>")
            items.clear()

    for line in md.splitlines():
        m = re.match(r"(#{1,4}) (.*)", line)
        if m:
            flush()
            n = len(m.group(1))
            out.append(f"<h{n}>{inline(m.group(2))}</h{n}>")
        elif line.startswith("- "):
            if para:
                flush()
            items.append(line[2:])
        elif not line.strip():
            flush()
        elif items:
            items[-1] += " " + line.strip()
        else:
            para.append(line.strip())
    flush()
    return "\n".join(out)


def page(lang, title, other, body):
    return f"""<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title><style>{CSS}</style></head>
<body><main><nav>{other}</nav>
{body}
</main></body></html>
"""


def main(out):
    out = Path(out)
    (out / "privacy").mkdir(parents=True, exist_ok=True)
    en = convert((HERE / "privacy-policy-en.md").read_text(encoding="utf-8"))
    ru = convert((HERE / "privacy-policy-ru.md").read_text(encoding="utf-8"))
    (out / "privacy" / "index.html").write_text(
        page("en", "Quran Hifz — Privacy Policy", '<a href="ru.html">Русская версия</a>', en), encoding="utf-8")
    (out / "privacy" / "ru.html").write_text(
        page("ru", "Quran Hifz — Политика конфиденциальности", '<a href="./">English version</a>', ru), encoding="utf-8")
    (out / "index.html").write_text(
        '<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=privacy/">'
        '<title>Quran Hifz</title><a href="privacy/">Privacy Policy</a>\n', encoding="utf-8")
    (out / ".nojekyll").write_text("", encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1])
