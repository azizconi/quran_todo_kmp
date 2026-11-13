# Quran Hifz 2.0.0 — тексты для Google Play

- Версия: `versionName 2.0.0`, `versionCode 8`.
- Название выпуска в Play Console: `2.0.0 (8)`.
- Лимит «Что нового» — 500 символов на язык; теги языков в лимит не входят.
- Без эмодзи и без звёзд: маркер списка — `•`.
- Без строк «бесплатно / без рекламы» (правило владельца); блоки совпадают с разделом «What's new» в `listing/<язык>.md`.
- Название и краткое описание не меняются; в полном описании обновлены названия разделов под новый вид (2026-11-13). Всё в `listing/<язык>.md`.
- Тексты tg, uz и tr-TR должен проверить носитель языка до публикации.

## Что нового — блок для вставки в Play Console

Play Console → выпуск → «Примечания к выпуску» → вставить блок целиком.
Если какой-то язык в Console не добавлен, удалите его блок вместе с тегами.

```
<ru-RU>
Quran Hifz 2.0 — большое обновление
• Новый вид: три раздела — Урок, Коран и Прогресс
• Урок дня: новое, недавнее и старое, с планом и датой завершения
• Страницы мусхафа, таджвид в цвете и 12 чтецов с подсветкой слов
• Под аятом: «Слушать», «Повтор» и «Ещё»; переход к любому аяту
• Тест, похожие аяты и запись своего чтения
• Прогресс по плану, отчёт для устаза, хатм в Рамадан и виджет
</ru-RU>
<en-US>
Quran Hifz 2.0 — a big update
• A new look: three sections — Lesson, Quran and Progress
• Lesson of the day: new, recent and old, with a plan and a finish date
• Mushaf pages, tajweed colours and 12 reciters with word highlighting
• Under the ayah: Listen, Repeat and More; go to any ayah
• Test, similar ayahs and recording your own recitation
• Progress by plan, a report for your teacher, Ramadan khatm and a widget
</en-US>
<tg>
Quran Hifz 2.0 — навсозии калон
• Намуди нав: се бахш — Дарс, Қуръон ва Пешрафт
• Дарси рӯз: нав, навакак ва кӯҳна, бо нақша ва санаи анҷом
• Саҳифаҳои мусҳаф, рангҳои таҷвид ва 12 қорӣ бо ранги калимаҳо
• Дар зери оят: «Шунидан», «Такрор» ва «Бештар»; гузаштан ба ҳар оят
• Тест, оятҳои монанд ва сабти қироати худ
• Пешрафт аз рӯи нақша, ҳисобот барои устод, хатм дар Рамазон ва виджет
</tg>
<uz>
Quran Hifz 2.0 — katta yangilanish
• Yangi ko‘rinish: uchta bo‘lim — Dars, Qur’on va Natija
• Kunlik dars: yangi, yaqinda va eski, reja va tugash sanasi bilan
• Mushaf sahifalari, tajvid ranglari va so‘zlari ajraladigan 12 qori
• Oyat ostida: «Tinglash», «Takror» va «Yana»; istalgan oyatga o‘tish
• Test, o‘xshash oyatlar va o‘z qiroatingizni yozib olish
• Reja bo‘yicha natija, ustoz uchun hisobot, Ramazon xatmi va vidjet
</uz>
<tr-TR>
Quran Hifz 2.0 — büyük güncelleme
• Yeni görünüm: üç bölüm — Ders, Kur’an ve İlerleme
• Günün dersi: yeni, yakın zamanda ve eski, plan ve bitiş tarihiyle
• Mushaf sayfaları, renkli tecvid ve kelime vurgulu 12 kâri
• Ayetin altında: «Dinle», «Tekrar» ve «Diğer»; istediğiniz ayete gidin
• Test, benzer ayetler ve kendi kıraatinizin kaydı
• Plana göre ilerleme, hocanız için rapor, Ramazan hatmi ve widget
</tr-TR>
```

## Что ещё загрузить в этом выпуске

- Подписанный AAB: `composeApp/build/outputs/bundle/release/composeApp-release.aab` (после подписи, см. `docs/redesign/RELEASE.md`, пункт 2.1).
- Скриншоты: `localized/<язык>/phone/` и `localized/<язык>/feature-graphic-1024x500.png`.
- «Безопасность данных»: черновик `data-safety.md`; новое разрешение — микрофон (запись хранится только на телефоне).
- Порядок: сначала «Внутреннее тестирование», затем «Рабочая версия».
