# Quran Hifz 2.0.0 — тексты для Google Play

- Версия: `versionName 2.0.0`, `versionCode 8`.
- Название выпуска в Play Console: `2.0.0 (8)`.
- Лимит «Что нового» — 500 символов на язык; теги языков в лимит не входят.
- Без эмодзи и без звёзд: маркер списка — `•`.
- Название, краткое и полное описание не меняются: они в `listing/<язык>.md`.
- Тексты tg, uz и tr-TR должен проверить носитель языка до публикации.

## Что нового — блок для вставки в Play Console

Play Console → выпуск → «Примечания к выпуску» → вставить блок целиком.
Если какой-то язык в Console не добавлен, удалите его блок вместе с тегами.

```
<ru-RU>
Quran Hifz 2.0 — большое обновление
• Урок дня: новое, недавнее и старое — готов каждый день, с планом и датой завершения
• Страницы мусхафа, таджвид в цвете и 12 чтецов с подсветкой слов
• Проверьте себя: тесты, похожие аяты, запись своего чтения
• Перевод на выбор и тафсир прямо у аята
• Подробный прогресс, отчёт для устаза, хатм в Рамадан и виджет
Всё бесплатно: без рекламы, подписки и аккаунта.
</ru-RU>
<en-US>
Quran Hifz 2.0 — a big update
• Lesson of the day: new, recent and old, ready every day, with a plan and a finish date
• Mushaf pages, tajweed colours and 12 reciters with word highlighting
• Check yourself: quizzes, similar ayahs, record your own recitation
• Choose your translation and read tafsir right by the ayah
• Detailed progress, a report for your teacher, Ramadan khatm and a widget
Everything is free: no ads, no subscription, no account.
</en-US>
<tg>
Quran Hifz 2.0 — навсозии калон
• Дарси рӯз: нав, навакак ва кӯҳна — ҳар рӯз тайёр, бо нақша ва санаи анҷом
• Саҳифаҳои мусҳаф, рангҳои таҷвид ва 12 қорӣ бо ранги калимаҳо
• Худро санҷед: тестҳо, оятҳои монанд, сабти қироати худ
• Интихоби тарҷума ва тафсир дар назди оят
• Пешрафти муфассал, ҳисобот барои устод, хатм дар Рамазон ва виджет
Ҳама чиз ройгон: бе реклама, обуна ва аккаунт.
</tg>
<uz>
Quran Hifz 2.0 — katta yangilanish
• Kunlik dars: yangi, yaqindagi va eski — har kuni tayyor, reja va tugash sanasi bilan
• Mushaf sahifalari, tajvid ranglari va so‘zlari ajraladigan 12 qori
• O‘zingizni tekshiring: testlar, o‘xshash oyatlar, o‘z qiroatingizni yozib olish
• Tarjimani tanlang, tafsir esa oyat yonida
• Batafsil natija, ustoz uchun hisobot, Ramazon xatmi va vidjet
Hammasi bepul: reklamasiz, obunasiz va akkauntsiz.
</uz>
<tr-TR>
Quran Hifz 2.0 — büyük güncelleme
• Günün dersi: yeni, yakın ve eski — her gün hazır, plan ve bitiş tarihiyle
• Mushaf sayfaları, renkli tecvid ve kelime vurgulu 12 kâri
• Kendinizi sınayın: sınavlar, benzer ayetler, kendi kıraatinizin kaydı
• Meal seçimi ve ayetin yanında tefsir
• Ayrıntılı ilerleme, hocanız için rapor, Ramazan hatmi ve widget
Her şey ücretsiz: reklam, abonelik ve hesap yok.
</tr-TR>
```

## Что ещё загрузить в этом выпуске

- Подписанный AAB: `composeApp/build/outputs/bundle/release/composeApp-release.aab` (после подписи, см. `docs/redesign/RELEASE.md`, пункт 2.1).
- Скриншоты: `localized/<язык>/phone/` и `localized/<язык>/feature-graphic-1024x500.png`.
- «Безопасность данных»: черновик `data-safety.md`; новое разрешение — микрофон (запись хранится только на телефоне).
- Порядок: сначала «Внутреннее тестирование», затем «Рабочая версия».
