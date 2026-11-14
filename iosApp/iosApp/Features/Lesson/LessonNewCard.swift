import ComposeApp
import SwiftUI

/// The new passage: "Listen", "Read", "Recall" (`LessonNewCard.kt`). The phase switch at the top moves between them; "Next" goes on.
/// The result is written when the passage is graded at the end of "Recall".
struct NewPassageCard: View {
    let model: LessonModel
    let work: LessonUiWorking
    @Environment(Copy.self) private var copy

    var body: some View {
        let phase = work.progress.phase
        VStack(spacing: 0) {
            PassageHeader(model: model, work: work, overline: copy.lessonCopy.stepNew)
            PhaseSwitch(phase: phase) { model.vm.goTo(work: work, phase: $0) }
            ZStack {
                if work.ayahs.isEmpty {
                    PassageLoading()
                } else {
                    switch phase {
                    case .listen: ListenPhase(model: model, work: work)
                    case .read: ReadPhase(model: model, work: work)
                    default: RecallStage(model: model, work: work)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

/// The surah and the range of the card: the name, the Arabic name on the other side, "Ayahs 1-5 · 5 ayahs".
struct PassageHeader: View {
    let model: LessonModel
    let work: LessonUiWorking
    let overline: String?
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let range = work.card.range
        let title = model.surahTitle(Int(range.surah), language: copy.language)
        let arabic = model.surahArabic(Int(range.surah))
        let c = copy.lessonCopy
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 0) {
                if let overline { Text(overline.uppercased()).qhText({ $0.overline }, color: { $0.gold }) }
                Text(title).qhText({ $0.title1 }).lineLimit(2).accessibilityAddTraits(.isHeader)
                Text("\(c.range(start: range.startAyah, end: range.endAyah)) · \(c.ayahCount(count: range.ayahCount))")
                    .qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            Spacer(minLength: QhSpacing.x3)
            if !arabic.isEmpty {
                Text(verbatim: arabic).qhArabic(qh.type.quranInline(LessonArabic.face, size: 26), color: qh.colors.primary)
                    .environment(\.layoutDirection, .rightToLeft)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
    }
}

private struct PhaseSwitch: View {
    @Environment(Copy.self) private var copy
    let phase: CardPhase
    let onSelect: (CardPhase) -> Void

    var body: some View {
        let phases: [CardPhase] = [.listen, .read, .recall]
        let c = copy.lessonCopy
        QhSegmentedControl(
            options: [c.phaseListen, c.phaseRead, c.phaseRecall],
            selection: Binding(get: { max(phases.firstIndex(of: phase) ?? 0, 0) }, set: { onSelect(phases[$0]) })
        )
        .padding(.horizontal, QhSpacing.screen).padding(.bottom, QhSpacing.x2)
    }
}

struct PassageLoading: View {
    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            Skeleton().frame(width: 300, height: 70)
            Skeleton().frame(width: 300, height: 70)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
    }
}

// MARK: - Listen

/// The prefs the Listen settings sheet edits (`UiPrefs`), as observable values.
@MainActor
@Observable
final class LessonListenPrefs {
    let ayahRepeats = FlowState(IosUi.shared.uiPrefs.lessonAyahRepeats)
    let passageRepeats = FlowState(IosUi.shared.uiPrefs.lessonPassageRepeats)
    let echo = FlowState(IosUi.shared.uiPrefs.lessonEcho)
}

private struct ListenPhase: View {
    let model: LessonModel
    let work: LessonUiWorking
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var prefs = LessonListenPrefs()
    @State private var settingsOpen = false

    var body: some View {
        let c = copy.lessonCopy
        let audio = model.audioState.value
        let progress = model.listenProgress.value
        let running = progress.flatMap { $0.done ? nil : $0 }
        let ayahRepeats = Int(truncating: prefs.ayahRepeats.value)
        let passageRepeats = Int(truncating: prefs.passageRepeats.value)
        let line = running?.place.map { $0.label(copy: copy.lessonListenCopy) } ?? c.listenHint(ayahRepeats: Int32(ayahRepeats), passageRepeats: Int32(passageRepeats))
        VStack(spacing: 0) {
            PhaseTopLine(model: model, text: line, progress: running.map { Double($0.fraction) })
            PassageList(model: model, work: work, current: audio.active ? audio.ayah.map { Int(truncating: $0) } : nil, onTap: nil, similarHints: true)
            VStack(spacing: QhSpacing.x3) {
                YourTurnLine(yourTurn: copy.listen.yourTurn, secondsShort: copy.listen.secondsShort,
                             remaining: Int(audio.silenceSeconds), active: audio.yourTurn)
                HStack(spacing: QhSpacing.x6) {
                    RoundAction(icon: "slider.horizontal.3", label: c.listenSettings) { settingsOpen = true }
                    ZStack {
                        EchoRing(remaining: Int(audio.silenceSeconds), total: Int(audio.silenceTotalSeconds), active: audio.yourTurn)
                        PlayButton(playing: audio.playing, label: audio.playing ? c.listenPause : c.listenPlay) { model.vm.toggleListen(work: work) }
                    }
                    .frame(width: 76, height: 76)
                    RoundAction(icon: "arrow.counterclockwise", label: c.listenReplay) { model.vm.replayListen(work: work) }
                }
                QhButton(c.next, fullWidth: true) { model.vm.next(work: work) }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
        }
        // The whole sequence has played to its end by itself: on to "Read" (leaving, pausing or restarting never gets here).
        .onChange(of: progress?.done == true) { _, finished in if finished { model.vm.onListenFinished(work: work) } }
        .qhSheet(isPresented: $settingsOpen, detents: [.large]) { ListenSettingsSheet(prefs: prefs) }
    }
}

struct PlayButton: View {
    @Environment(\.qh) private var qh
    var size: CGFloat = 64
    let playing: Bool
    let label: String
    let action: () -> Void

    var body: some View {
        Image(systemName: playing ? "pause.fill" : "play.fill")
            .font(.system(size: size * 0.45, weight: .bold))
            .contentTransition(.symbolEffect(.replace))
            .foregroundStyle(qh.colors.onPrimary)
            .frame(width: size, height: size)
            .background(qh.colors.primary, in: Circle())
            .pressable(shape: Circle(), highlight: false, action: action)
            .qhAnimation(QhMotion.snappy, value: playing)
            .accessibilityLabel(label)
    }
}

struct RoundAction: View {
    @Environment(\.qh) private var qh
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: QhSpacing.iconBar - 3, weight: .semibold))
            .foregroundStyle(qh.colors.ink)
            .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
            .background(qh.colors.surfaceMuted, in: Circle())
            .pressable(shape: Circle(), highlight: false, action: action)
            .accessibilityLabel(label)
    }
}

/// How many times every ayah and the whole passage sound in "Listen", and the echo ("I repeat after the reciter").
private struct ListenSettingsSheet: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let prefs: LessonListenPrefs

    var body: some View {
        let c = copy.lessonCopy
        let l = copy.listen
        let ayahOptions = Array(1...5), passageOptions = Array(1...4)
        let ayahRepeats = Int(truncating: prefs.ayahRepeats.value), passageRepeats = Int(truncating: prefs.passageRepeats.value)
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                Text(c.settingsTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                Text(c.ayahRepeatsLabel).qhText({ $0.title2 })
                QhSegmentedControl(options: ayahOptions.map(String.init), selection: Binding(
                    get: { max(ayahOptions.firstIndex(of: ayahRepeats) ?? 0, 0) },
                    set: { IosUi.shared.uiPrefs.setLessonAyahRepeats(value: Int32(ayahOptions[$0])) }))
                Text(c.passageRepeatsLabel).qhText({ $0.title2 }).padding(.top, QhSpacing.x2)
                QhSegmentedControl(options: passageOptions.map(String.init), selection: Binding(
                    get: { max(passageOptions.firstIndex(of: passageRepeats) ?? 0, 0) },
                    set: { IosUi.shared.uiPrefs.setLessonPassageRepeats(value: Int32(passageOptions[$0])) }))
                Text(c.listenHint(ayahRepeats: Int32(ayahRepeats), passageRepeats: Int32(passageRepeats)))
                    .qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
                QhCard(padding: 0) {
                    QhSwitchRow(title: l.echoSwitch,
                                isOn: Binding(get: { prefs.echo.value.boolValue }, set: { IosUi.shared.uiPrefs.setLessonEcho(value: $0) }),
                                description: l.echoSwitchHint)
                }
                .padding(.vertical, QhSpacing.x2)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}

// MARK: - Read

private struct ReadPhase: View {
    let model: LessonModel
    let work: LessonUiWorking
    @Environment(Copy.self) private var copy

    var body: some View {
        let audio = model.audioState.value
        VStack(spacing: 0) {
            PhaseTopLine(model: model, text: copy.lessonCopy.readHint, progress: nil)
            PassageList(model: model, work: work, current: audio.active ? audio.ayah.map { Int(truncating: $0) } : nil,
                        onTap: { model.vm.toggleListenAyah(work: work, inSurah: Int32($0)) }, similarHints: true)
            QhButton(copy.lessonCopy.next, fullWidth: true) { model.vm.next(work: work) }
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
        }
    }
}

/// The top line of "Listen" and "Read": a hint, or where the listening is with a thin bar under it, and the "Translation" chip.
private struct PhaseTopLine: View {
    let model: LessonModel
    let text: String
    let progress: Double?
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let on = model.translationOn.value.boolValue
        HStack(spacing: QhSpacing.x3) {
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                Text(text).qhText({ $0.callout }, color: { $0.inkSecondary })
                    .contentTransition(.opacity).qhAnimation(QhMotion.snappy, value: text)
                if let progress { ListenBar(fraction: progress) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            QhChip(label: copy.lessonListenCopy.translationChip, selected: on) { model.vm.setTranslationOn(on: !on) }
        }
        .padding(.horizontal, QhSpacing.screen)
    }
}

/// A thin linear bar over the whole listening sequence; follows the sequence over 250 ms.
private struct ListenBar: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let fraction: Double

    var body: some View {
        let shown = min(max(fraction, 0), 1)
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(qh.colors.surfaceMuted)
                Capsule().fill(qh.colors.primary).frame(width: geo.size.width * shown)
            }
        }
        .frame(height: 4)
        .animation(reduced ? nil : .easeOut(duration: 0.25), value: shown)
        .accessibilityElement(children: .ignore)
        .accessibilityValue(Text("\(Int(shown * 100)) %"))
    }
}

// MARK: - The passage list

/// The passage as the reader shows it: a rosette with the number, the Arabic text. `current` is the ayah being heard (a wash and a
/// gold bar, and the list scrolls it into the middle); a tap on an ayah calls `onTap` when it is given. The translation under every
/// ayah while the chip is on, with the reader's "Not loaded - Retry" first when the load failed.
struct PassageList: View {
    let model: LessonModel
    let work: LessonUiWorking
    let current: Int?
    let onTap: ((Int) -> Void)?
    var similarHints = false
    @Environment(Copy.self) private var copy
    @Environment(\.qhReduceMotion) private var reduced

    var body: some View {
        let on = model.translationOn.value.boolValue
        let map = model.translations.value
        let surah = Int(work.card.range.surah)
        let missing = on && Int(truncating: model.translationFailedSurah.value ?? -1) == surah && map.isEmpty
        let ayahs = work.ayahs
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(Array(ayahs.enumerated()), id: \.element.number) { index, ayah in
                        PassageRow(ayah: ayah, marked: Int(ayah.numberInSurah) == current, onTap: onTap,
                                   translation: on ? map[KotlinInt(int: ayah.number)] : nil,
                                   translationMissing: missing && index == 0) {
                            model.vm.ensureTranslations(work: work, language: copy.language, force: true)
                        }
                        .id(Int(ayah.numberInSurah))
                    }
                }
                .padding(.bottom, QhSpacing.x4)
            }
            .onChange(of: current) { _, ayah in
                guard let ayah else { return }
                if reduced { proxy.scrollTo(ayah, anchor: .center) } else { withAnimation(QhMotion.gentle) { proxy.scrollTo(ayah, anchor: .center) } }
            }
        }
        // The translation is loaded when the chip is on, the card changes or the language does (the reader's rule: once per surah).
        .task(id: TranslationKey(on: on, surah: surah, count: ayahs.count, language: copy.language.name)) {
            if on { model.vm.ensureTranslations(work: work, language: copy.language, force: false) }
        }
    }

    private struct TranslationKey: Equatable { let on: Bool; let surah: Int; let count: Int; let language: String }
}

private struct PassageRow: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let ayah: AyahEntity
    let marked: Bool
    let onTap: ((Int) -> Void)?
    let translation: String?
    let translationMissing: Bool
    let retry: () -> Void

    var body: some View {
        let number = Int(ayah.numberInSurah)
        let row = HStack(alignment: .top, spacing: QhSpacing.x3) {
            AyahRosette(number: number, size: 30).padding(.top, QhSpacing.x2)
            VStack(alignment: .leading, spacing: QhSpacing.x1) {
                QuranText(ayah: ayah.display, face: LessonArabic.face, readingSize: LessonArabic.lessonSize)
                    .equatable()
                if let translation, !translation.isEmpty {
                    Text(translation).qhText({ $0.callout }, color: { $0.inkSecondary })
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else if translationMissing {
                    HStack(spacing: 0) {
                        Text("\(copy.readerCopy.translationNotLoaded) · ").qhText({ $0.callout }, color: { $0.inkSecondary })
                        Button(action: retry) {
                            Text(copy.readerCopy.retry).qhText({ $0.callout }, color: { $0.primary })
                                .frame(minHeight: QhSpacing.minTouch)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x2)
        .frame(minHeight: QhSpacing.minTouch)
        .background { Highlight(marked: marked) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(marked ? .isSelected : [])
        if let onTap {
            row.pressable(scale: 0.995, action: { onTap(number) })
        } else {
            row
        }
    }
}

/// The wash and the gold bar of the ayah being heard: both fade in 200 ms, the bar grows from its middle.
private struct Highlight: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let marked: Bool

    var body: some View {
        ZStack(alignment: .leading) {
            qh.colors.readerHighlight.opacity(marked ? 1 : 0)
            Capsule().fill(qh.colors.gold).frame(width: 3).scaleEffect(y: marked ? 1 : 0.01).opacity(marked ? 1 : 0).padding(.vertical, 2)
        }
        .animation(reduced ? nil : .easeOut(duration: 0.2), value: marked)
    }
}
