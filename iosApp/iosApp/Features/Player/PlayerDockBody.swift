import ComposeApp
import SwiftUI

/// The expanded part of the player: the current ayah large, its position, the transport and the practice controls (reciter, repeat,
/// pause, auto-advance, echo, speed, sleep timer). It scrolls inside the sheet.
struct DockBody: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    let onPlay: () -> Void

    var body: some View {
        let r = copy.reader
        let prefs = model.prefs.value, practice = model.practice.value
        VStack(alignment: .leading, spacing: QhSpacing.x6) {
            PlayerGuide()
            NowPlayingCard(model: model)
            HStack(spacing: QhSpacing.x6) {
                Spacer(minLength: 0)
                QhIconButton(icon: "backward.fill", label: r.previous, filled: false) { model.vm.previousAyah() }
                DockPlayButton(model: model, size: 72, action: onPlay)
                QhIconButton(icon: "forward.fill", label: r.next, filled: false) { model.vm.nextAyah() }
                Spacer(minLength: 0)
            }
            ReciterRow(model: model)
            RepeatBlock(model: model)
            ChipSection(title: r.pauseBetween, hint: r.pauseHint,
                        options: [0, 3, 5, 10].map { ($0, $0 == 0 ? r.none : "\($0) \(r.secondsShort)") },
                        selected: Int(practice.pauseSeconds)) { model.vm.setPauseSeconds(seconds: Int32($0)) }
            QhCard(padding: 0) {
                QhSwitchRow(title: copy.playerRepeat.autoAdvanceTitle,
                            isOn: Binding(get: { prefs.autoAdvanceAyahs }, set: { model.vm.setAutoAdvance(on: $0) }),
                            description: copy.playerRepeat.autoAdvanceHint, icon: "forward.end.fill", iconTint: qh.colors.gold)
            }
            ChipSection(title: copy.listenCopy.echoTitle, hint: copy.listenCopy.echoHint,
                        options: [(0, r.none)] + [1, 2, 3].map { ($0, repeatText($0)) },
                        selected: practice.echo ? Int(practice.echoRepeats) : 0) { model.vm.setEcho(repeats: Int32($0)) }
            ChipSection(title: r.speed, hint: nil,
                        options: [Float(0.75), 1, 1.25, 1.5, 2].map { (Int($0 * 100), speedText($0)) },
                        selected: Int(prefs.playbackSpeed * 100)) { model.vm.setPlaybackSpeed(speed: Float($0) / 100) }
            SleepSection(model: model)
        }
    }
}

// MARK: - Now playing

private struct NowPlayingCard: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let ui = model.player.value
        let index = Int(ui.index)
        let prefs = ReadingPrefs.shared
        VStack(alignment: .leading, spacing: 0) {
            Group {
                if let display = model.display(at: index, tajweed: prefs.tajweed) {
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        FollowedQuranText(ayah: display, face: prefs.face, size: CGFloat(min(prefs.size, 30)), tajweed: prefs.tajweed,
                                          playing: ui.isPlaying, tappable: false) { _, _ in }
                            .allowsHitTesting(false)
                        if model.prefs.value.showTranslation, let t = model.ayahs.indices.contains(index) ? model.translation(model.ayahs[index]) : nil, !t.isEmpty {
                            Text(t).qhText({ $0.body }, color: { $0.inkSecondary }).lineLimit(4)
                        }
                    }
                    .id(index)
                    .transition(.opacity)
                }
            }
            .animation(reduced ? nil : .easeInOut(duration: 0.22), value: index)
            Spacer().frame(height: QhSpacing.x4)
            AudioPositionRow(model: model)
        }
        .padding(QhSpacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(qh.colors.readerBg, in: qh.shapes.mdShape)
    }
}

/// "0:12 ---- 0:35": the only part of the dock that follows the audio clock.
private struct AudioPositionRow: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let time = model.audioTime.value
        let progress = CGFloat(truncating: model.audioProgress.value)
        HStack(spacing: 10) {
            Text(audioTimeText(Int(time.positionSeconds))).qhText({ $0.callout }, color: { $0.inkSecondary }).monospacedDigit()
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(qh.colors.surfaceStrong)
                    if progress > 0 { Capsule().fill(qh.colors.primary).frame(width: max(g.size.width * progress, 4)) }
                }
            }
            .frame(height: 4)
            Text(audioTimeText(Int(time.durationSeconds))).qhText({ $0.callout }, color: { $0.inkSecondary }).monospacedDigit()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy.reader.audioPosition)
        .accessibilityValue("\(Int(progress * 100)) %")
    }
}

// MARK: - Reciter

private struct ReciterRow: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    @State private var open = false

    var body: some View {
        QhCard(padding: 0) {
            QhValueRow(title: copy.playerRepeat.reciterRow, value: model.reciter.value.shortName(language: copy.language),
                       icon: "person.wave.2.fill", iconTint: qh.colors.gold) { open = true }
        }
        .sheet(isPresented: $open) { ReciterPickerSheet(model: model) }
    }
}

/// The reciters: the same choice as in the settings (`UiPrefs.setReciter`); applies to the next ayah that sounds.
struct ReciterPickerSheet: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let current = model.reciter.value
        VStack(alignment: .leading, spacing: 0) {
            QhSheetHeader(title: copy.playerRepeat.reciterRow, closeLabel: copy.reader.cancelAction) { dismiss() }
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x4)
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Reciters.shared.all, id: \.id) { reciter in
                        HStack {
                            Text(reciter.name(language: copy.language)).qhText({ $0.title2 })
                            Spacer()
                            if reciter.id == current.id { Image(systemName: "checkmark").foregroundStyle(qh.colors.primary).fontWeight(.bold) }
                        }
                        .padding(.horizontal, QhSpacing.screen).frame(minHeight: 56)
                        .pressable(haptic: .segment) { IosUi.shared.uiPrefs.setReciter(value: reciter); model.vm.reloadPrefs() }
                        .accessibilityAddTraits(reciter.id == current.id ? .isSelected : [])
                    }
                }
            }
        }
        .background(qh.colors.surface)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
    }
}

// MARK: - Repeat

private struct RepeatBlock: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    @State private var chosen = 0
    @State private var from = 1
    @State private var to = 1
    @State private var count = 5

    private static let modes: [RepeatMode] = [RepeatMode.none, RepeatMode.ayah, RepeatMode.passage]

    var body: some View {
        let t = copy.playerRepeat
        let prefs = model.prefs.value, practice = model.practice.value
        let total = max(model.ayahs.count, 1)
        let derived = RepeatModes.shared.derive(state: RepeatState(ayahRepeat: prefs.repeatCount, practice: practice))
        let derivedIndex = Self.modes.firstIndex(of: derived) ?? 0
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(t.repeatTitle)
            QhSegmentedControl(options: [t.modeNone, t.modeAyah, t.modePassage],
                               selection: Binding(get: { chosen }, set: { chosen = $0; model.vm.selectRepeatMode(mode: Self.modes[$0]) }))
            switch chosen {
            case 1:
                Text(t.ayahHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                let counts = [1, 3, 5, -1]
                QhSegmentedControl(options: counts.map(repeatText),
                                   selection: Binding(get: { max(counts.firstIndex(of: Int(prefs.repeatCount)) ?? 0, 0) },
                                                      set: { model.vm.setAyahRepeat(count: Int32(counts[$0])) }))
            case 2:
                passage(t, practice: practice, total: total)
            default: EmptyView()
            }
        }
        .onAppear {
            chosen = derivedIndex
            from = practice.rangeEnabled ? Int(practice.startAyah) : min(max(Int(model.player.value.index) + 1, 1), total)
            to = practice.rangeEnabled ? Int(practice.endAyah) : from
            if practice.rangeEnabled { count = Int(practice.rangeRepeatCount) }
        }
        .onChange(of: derivedIndex) { _, now in if now != 0 { chosen = now } }
        .onChange(of: practice.rangeEnabled) { _, on in
            if on { from = Int(practice.startAyah); to = Int(practice.endAyah); count = Int(practice.rangeRepeatCount) }
        }
        .qhAnimation(QhMotion.gentle, value: chosen)
    }

    @ViewBuilder private func passage(_ t: PlayerRepeatCopy, practice: ReaderPracticeSettings, total: Int) -> some View {
        let r = copy.reader
        let counts = [1, 3, 5, -1]
        let applied = practice.rangeEnabled && Int(practice.startAyah) == from && Int(practice.endAyah) == max(to, from) && Int(practice.rangeRepeatCount) == count
        Text(t.passageHint).qhText({ $0.callout }, color: { $0.inkSecondary })
        HStack(spacing: QhSpacing.x6) {
            AyahWheel(label: r.fromAyah, minimum: 1, maximum: total, value: Binding(get: { from }, set: { from = $0; if to < $0 { to = $0 } }))
            AyahWheel(label: r.toAyah, minimum: from, maximum: total, value: Binding(get: { max(to, from) }, set: { to = $0 }))
        }
        Text("\(r.ayah) \(from)\u{2013}\(max(to, from))").qhText({ $0.title2 }, color: { $0.primary }).frame(maxWidth: .infinity)
        QhSegmentedControl(options: counts.map(repeatText),
                           selection: Binding(get: { counts.firstIndex(of: count) ?? 2 }, set: { count = counts[$0] }))
        QhButton(applied ? r.stopRepeat : r.startRepeat, variant: applied ? .tonal : .primary, size: .m, fullWidth: true) {
            if applied { model.vm.stopPassage() } else { model.vm.applyPassage(from: Int32(from), to: Int32(max(to, from)), repeatCount: Int32(count)) }
        }
    }
}

// MARK: - Chips

private struct ChipSection: View {
    let title: String
    let hint: String?
    let options: [(Int, String)]
    let selected: Int
    let onSelect: (Int) -> Void
    @Environment(\.qh) private var qh

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSectionHeader(title)
            if let hint { Text(hint).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            WrapLayout(spacing: QhSpacing.x2, lineSpacing: QhSpacing.x1) {
                ForEach(options, id: \.0) { option in
                    QhChip(label: option.1, selected: option.0 == selected) { onSelect(option.0) }
                }
            }
        }
    }
}

private struct SleepSection: View {
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let r = copy.reader
        let timer = model.sleepTimer.value
        let current: Int = timer is SleepTimerOff ? 0 : (timer is SleepTimerEndOfSurah ? -1 : Int((timer as? SleepTimerMinutes)?.minutes ?? 0))
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            ChipSection(title: r.sleepTimer, hint: nil,
                        options: [(0, r.sleepOff), (-1, r.sleepEndOfSurah), (15, r.sleepMinutes(minutes: 15)), (30, r.sleepMinutes(minutes: 30))],
                        selected: current) { value in
                switch value {
                case 0: model.vm.setSleepTimer(timer: SleepTimerOff.shared)
                case -1: model.vm.setSleepTimer(timer: SleepTimerEndOfSurah.shared)
                default: model.vm.setSleepTimer(timer: SleepTimerMinutes(minutes: Int32(value)))
                }
            }
            if timer is SleepTimerMinutes {
                Text(r.sleepLeft(time: audioTimeText(Int(truncating: model.sleepRemaining.value))))
                    .qhText({ $0.callout }, color: { $0.inkSecondary }).monospacedDigit()
            }
        }
    }
}

// MARK: - One-time guide

private struct PlayerGuide: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var seen = Self.isSeen
    @State private var step = 0

    private static var isSeen: Bool {
        let storage = UserSettingsStorage.shared
        return storage.isPlayerGuideSeen(feature: "reader_dock")?.boolValue == true
            || (storage.isPlayerGuideSeen(feature: "Range")?.boolValue == true && storage.isPlayerGuideSeen(feature: "Pause")?.boolValue == true)
    }

    var body: some View {
        if !seen {
            let t = PlayerFeatureCopyKt.playerFeatureCopy(language: copy.language)
            let last = step >= 2
            QhCard {
                HStack(spacing: QhSpacing.x4) {
                    Image("PlayerGuideArt").resizable().scaledToFill()
                        .frame(width: 288, height: 96, alignment: .leading)
                        .offset(x: -CGFloat(step) * 96 + 96)
                        .frame(width: 96, height: 96).clipped()
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(t.guideTitle(step: Int32(step))).qhText({ $0.title2 })
                        Text(t.guideBody(step: Int32(step))).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                    .id(step).transition(.opacity)
                }
                HStack { Spacer(); QhButton(last ? t.done : t.next, variant: .tonal, size: .s) {
                    if last {
                        UserSettingsStorage.shared.savePlayerGuideSeen(seen: true, feature: "reader_dock")
                        withQhAnimation(QhMotion.gentle) { seen = true }
                    } else { withQhAnimation(QhMotion.snappy) { step += 1 } }
                } }
                .padding(.top, QhSpacing.x2)
            }
            .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .top)))
        }
    }
}
