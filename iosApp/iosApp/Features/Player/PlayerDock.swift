import ComposeApp
import SwiftUI

// MARK: - Play button

/// The round play / pause button. While the pause between ayahs runs a ring around it counts the time down; while audio is being
/// prepared the icon gives way to three dots. The icon morphs with scale and rotation.
struct DockPlayButton: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    let size: CGFloat
    let action: () -> Void

    var body: some View {
        let ui = model.player.value
        let playing = ui.isPlaying && ui.active
        let ring: CGFloat = 5
        ZStack {
            PauseRing(model: model, active: ui.active && playing)
            ZStack {
                Circle().fill(qh.colors.primary)
                if ui.isPreparing && ui.active {
                    DotSpinner(color: qh.colors.onPrimary)
                } else {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.system(size: size * 0.4, weight: .bold))
                        .foregroundStyle(qh.colors.onPrimary)
                        .id(playing)
                        .transition(.scale(scale: 0.5).combined(with: .opacity))
                }
            }
            .frame(width: size, height: size)
            .animation(reduced ? nil : QhMotion.snappy, value: playing)
            .pressable(haptic: .tick, shape: Circle(), highlight: false, action: action)
        }
        .frame(width: size + ring * 2, height: size + ring * 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(playing ? copy.reader.pause : copy.reader.play)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { action() }
    }
}

/// The countdown ring of the pause between ayahs: full when the pause starts, empty when it ends.
private struct PauseRing: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let model: ReaderModel
    let active: Bool
    @State private var sweep: CGFloat = 0

    var body: some View {
        let remaining = Int(truncating: model.pauseRemaining.value)
        let total = Int(truncating: model.pauseTotal.value)
        let visible = active && total > 0 && remaining > 0
        ZStack {
            Circle().stroke(qh.colors.primary.opacity(0.2), lineWidth: 3)
            Circle().trim(from: 0, to: sweep).stroke(qh.colors.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round)).rotationEffect(.degrees(-90))
        }
        .padding(1.5)
        .opacity(visible || sweep > 0 ? 1 : 0)
        .onChange(of: remaining) { _, now in update(remaining: now, total: total, visible: visible) }
        .onChange(of: visible) { _, now in update(remaining: remaining, total: total, visible: now) }
    }

    private func update(remaining: Int, total: Int, visible: Bool) {
        guard visible, total > 0 else { sweep = 0; return }
        if remaining >= total { sweep = 1 }
        let target = CGFloat(max(remaining - 1, 0)) / CGFloat(total)
        if reduced { sweep = target } else { withAnimation(.linear(duration: 1)) { sweep = target } }
    }
}

// MARK: - Status line

/// The line under "Ayah 12 of 286": the reciter, a non-default speed, the repeated passage and the sleep timer; "Your turn - 3 s" during
/// the pause between ayahs; "No connection - Retry" after an audio error.
struct DockStatusLine: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel

    var body: some View {
        let ui = model.player.value
        let r = copy.reader
        let waiting = Int(truncating: model.pauseRemaining.value)
        Group {
            if ui.hasError {
                Button {
                    QhHaptics.shared.perform(.reject)
                    model.vm.retry()
                } label: {
                    Text(r.noConnectionRetry).qhText({ $0.callout }, color: { $0.danger }).lineLimit(1)
                        .frame(minHeight: 28)
                }
                .buttonStyle(.plain)
            } else if ui.active, waiting > 0 {
                Text("\(r.yourTurn) \u{00B7} \(waiting) \(r.secondsShort)").qhText({ $0.callout }, color: { $0.primary }).lineLimit(1)
                    .contentTransition(.numericText())
            } else {
                HStack(spacing: 0) {
                    Text(summary).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                    SleepBadge(model: model)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var summary: String {
        let prefs = model.prefs.value, practice = model.practice.value
        let language = copy.language
        let repeatCopy = copy.playerRepeat
        var out = model.reciter.value.shortName(language: language)
        if let queue = model.listenQueue.value {
            out = "\(copy.listenCopy.dockQueue(position: queue.position + 1, total: Int32(queue.items.count))) \u{00B7} \(out)"
            if prefs.playbackSpeed != 1 { out += " \u{00B7} \(speedText(prefs.playbackSpeed))" }
            if queue.echo { out += " \u{00B7} \(copy.listenCopy.echoChip) \(repeatText(Int(queue.echoRepeats)))" }
            return out
        }
        if prefs.playbackSpeed != 1 { out += " \u{00B7} \(speedText(prefs.playbackSpeed))" }
        if practice.echo { out += " \u{00B7} \(copy.listenCopy.echoChip) \(repeatText(Int(practice.echoRepeats)))" }
        let mode = RepeatModes.shared.derive(state: RepeatState(ayahRepeat: prefs.repeatCount, practice: practice))
        if mode == RepeatMode.passage {
            out += " \u{00B7} " + repeatCopy.passageStatus(range: "\(practice.startAyah)\u{2013}\(practice.endAyah)", count: repeatText(Int(practice.rangeRepeatCount)))
        } else if mode == RepeatMode.ayah {
            out += " \u{00B7} " + repeatCopy.ayahStatus(count: repeatText(Int(prefs.repeatCount)))
        }
        return out
    }
}

private struct SleepBadge: View {
    @Environment(\.qh) private var qh
    let model: ReaderModel

    var body: some View {
        let timer = model.sleepTimer.value
        if !(timer is SleepTimerOff) {
            HStack(spacing: 2) {
                Image(systemName: "moon.zzz.fill").font(.system(size: 11)).foregroundStyle(qh.colors.gold)
                if timer is SleepTimerMinutes {
                    Text("\((Int(truncating: model.sleepRemaining.value) + 59) / 60)").qhText({ $0.callout }, color: { $0.inkSecondary }).monospacedDigit()
                }
            }
            .padding(.leading, 8)
            .accessibilityHidden(true)
        }
    }
}

// MARK: - The collapsed dock

/// The player at the bottom of the reader: a 76 pt bar (the ayah, a status line and the transport). A tap on the left part or a drag
/// up opens the expanded player (a sheet with detents, Decision 2); the audio position is read only inside the progress line.
struct PlayerDockBar: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    let expandable: Bool
    let onPlay: () -> Void
    let onExpand: () -> Void

    var body: some View {
        let ui = model.player.value
        let r = copy.reader
        let number = Int(ui.index) + 1
        let total = model.ayahs.count
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                HStack(spacing: 12) {
                    AyahRosette(number: number, size: 44)
                    VStack(alignment: .leading, spacing: 0) {
                        ViewThatFits(in: .horizontal) {
                            counter(number, total, full: true, r)
                            counter(number, total, full: false, r)
                        }
                        DockStatusLine(model: model)
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture { if expandable { QhHaptics.shared.perform(.tick); onExpand() } }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("\(r.ayah) \(number) \(r.ofWord) \(total)")
                .accessibilityHint(r.expandPlayer)
                Spacer(minLength: 4)
                HStack(spacing: 0) {
                    QhIconButton(icon: "backward.fill", label: r.previous, filled: false) { model.vm.previousAyah() }
                    DockPlayButton(model: model, size: 48, action: onPlay)
                    QhIconButton(icon: "forward.fill", label: r.next, filled: false) { model.vm.nextAyah() }
                }
            }
            .padding(.leading, 16).padding(.trailing, 4).padding(.top, 10).padding(.bottom, 2)
            .frame(minHeight: 76)
        }
        .frame(maxWidth: .infinity)
        .background(alignment: .top) { qh.colors.readerSurface.clipShape(qh.shapes.xlTop).ignoresSafeArea(edges: .bottom) }
        .overlay(alignment: .top) {
            ZStack(alignment: .top) {
                DockProgressLine(model: model)
                if expandable {
                    Capsule().fill(qh.colors.hairline).frame(width: 32, height: 4).padding(.top, 6)
                }
            }
        }
        .overlay(alignment: .top) { qh.shapes.xlTop.stroke(qh.colors.hairline, lineWidth: 1).allowsHitTesting(false) }
        .gesture(DragGesture(minimumDistance: 14).onEnded { v in
            if expandable, v.translation.height < -36 { onExpand() }
        })
    }

    private func counter(_ number: Int, _ total: Int, full: Bool, _ r: ReaderCopy) -> some View {
        HStack(spacing: 0) {
            if full { Text("\(r.ayah) ").qhText({ $0.title2 }).lineLimit(1) }
            AnimatedCounter(value: number, style: { $0.title2 })
            Text(full ? " \(r.ofWord) \(total)" : " / \(total)").qhText({ $0.title2 }).lineLimit(1)
        }
        .fixedSize()
    }
}

/// 2 pt line along the top edge of the dock: how far the ayah has got. The only part of the bar that follows the clock.
private struct DockProgressLine: View {
    @Environment(\.qh) private var qh
    let model: ReaderModel

    var body: some View {
        let fraction = CGFloat(truncating: model.audioProgress.value)
        GeometryReader { g in
            if fraction > 0 {
                Rectangle().fill(qh.colors.primary).frame(width: g.size.width * fraction, height: 2)
            }
        }
        .frame(height: 2)
        .clipShape(qh.shapes.xlTop)
        .accessibilityHidden(true)
    }
}

// MARK: - The expanded player (sheet)

struct PlayerDockSheet: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    let onPlay: () -> Void

    var body: some View {
        let ui = model.player.value
        let r = copy.reader
        let number = Int(ui.index) + 1
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                AyahRosette(number: number, size: 44)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 0) {
                        Text("\(r.ayah) ").qhText({ $0.title2 })
                        AnimatedCounter(value: number, style: { $0.title2 })
                        Text(" \(r.ofWord) \(model.ayahs.count)").qhText({ $0.title2 })
                    }
                    DockStatusLine(model: model)
                }
                Spacer(minLength: 0)
                QhIconButton(icon: "chevron.down", label: r.collapsePlayer) { dismiss() }
            }
            .padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 6)
            ScrollView {
                DockBody(model: model, onPlay: onPlay)
                    .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x8)
            }
        }
        .background(qh.colors.readerSurface)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackgroundInteraction(.enabled(upThrough: .medium))
    }
}
