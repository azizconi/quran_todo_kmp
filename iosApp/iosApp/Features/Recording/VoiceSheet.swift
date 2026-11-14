import AVFoundation
import ComposeApp
import Observation
import SwiftUI

/// "My reading": the recordings of a passage (newest first, with duration and play / pause, swipe to delete with "Undo"), "Record
/// another" and "Listen to the reciter"; while it records the time, the loudness and a big Stop. The microphone is asked for at the
/// first press of a record button; a refusal says so and, when final, offers the settings of the phone. Closing the sheet keeps the
/// recording and stops the playback.
@MainActor
@Observable
final class VoiceModel {
    let key: PassageKey
    private(set) var notes: [VoiceNote] = []
    private(set) var recorder: RecorderState = RecorderStateIdle.shared
    private(set) var player = IosReaderBridge.shared.voice.player.value
    var denial: Denial?
    var pendingDelete: VoiceNote?
    @ObservationIgnored private var tasks: [Task<Void, Never>] = []

    enum Denial { case once, forGood }

    init(key: PassageKey) {
        self.key = key
        let voice = IosReaderBridge.shared.voice
        recorder = voice.recorder.value
        tasks.append(Task { [weak self] in do { for try await v in voice.notes(key: key) { self?.notes = v } } catch {} })
        tasks.append(Task { [weak self] in do { for try await v in voice.recorder { self?.recorder = v } } catch {} })
        tasks.append(Task { [weak self] in do { for try await v in voice.player { self?.player = v } } catch {} })
    }

    var voice: VoiceService { IosReaderBridge.shared.voice }

    func startRecording(toaster: Toaster, failed: String) {
        let status = AVAudioApplication.shared.recordPermission
        switch status {
        case .granted: begin(toaster, failed)
        case .undetermined:
            AVAudioApplication.requestRecordPermission { granted in
                Task { @MainActor in if granted { self.begin(toaster, failed) } else { self.denial = .once } }
            }
        default: denial = .forGood
        }
    }

    private func begin(_ toaster: Toaster, _ failed: String) {
        denial = nil
        if voice.start(key: key) == StartResult.failed { QhHaptics.shared.perform(.reject); toaster.show(failed, kind: .error) }
    }

    func stopRecording() {
        _ = voice.stop()
        QhHaptics.shared.perform(.success)
    }

    func commitPending() {
        if let p = pendingDelete { voice.deleteNow(recording: p.recording); pendingDelete = nil }
    }

    func close() {
        commitPending()
        voice.stopPlayback()
        if recorder is RecorderStateRecording { _ = voice.stop() }
        tasks.forEach { $0.cancel() }
    }
}

struct VoiceSheet: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @State private var model: VoiceModel
    let title: String
    let startRecording: Bool
    let onListenReciter: () -> Void

    init(key: PassageKey, title: String, startRecording: Bool, onListenReciter: @escaping () -> Void) {
        _model = State(initialValue: VoiceModel(key: key))
        self.title = title; self.startRecording = startRecording; self.onListenReciter = onListenReciter
    }

    var body: some View {
        let t = copy.voice
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            Text(t.myReading).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
            Text(title).qhText({ $0.callout }, color: { $0.inkSecondary })
            if let denial = model.denial { denied(denial, t) }
            else if let rec = model.recorder as? RecorderStateRecording { recording(rec, t) }
            else { list(t) }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .qhSheetStyle([.medium, .large])
        .task { if startRecording { model.startRecording(toaster: env.toaster, failed: t.startFailed) } }
        .onDisappear { model.close() }
    }

    private func list(_ t: VoiceCopy) -> some View {
        let notes = model.notes.filter { $0.recording.path != model.pendingDelete?.recording.path }
        return VStack(alignment: .leading, spacing: QhSpacing.x3) {
            Text(t.sheetHint).qhText({ $0.callout }, color: { $0.inkSecondary })
            if notes.isEmpty { Text(t.emptyList).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.vertical, QhSpacing.x2) }
            List {
                ForEach(notes, id: \.recording.path) { note in
                    row(note, t).listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { delete(note) } label: { Label(t.delete, systemImage: "trash") }
                        }
                }
            }
            .listStyle(.plain).scrollContentBackground(.hidden).frame(minHeight: CGFloat(min(notes.count, 4)) * 72)
            if model.pendingDelete != nil { undoBar(t) }
            QhButton(notes.isEmpty ? t.record : t.recordMore, variant: .primary, size: .m, icon: "mic.fill", fullWidth: true) {
                model.commitPending()
                model.startRecording(toaster: env.toaster, failed: t.startFailed)
            }
            QhButton(t.listenReciter, variant: .tonal, size: .m, icon: "headphones", fullWidth: true) { onListenReciter() }
        }
    }

    private func row(_ note: VoiceNote, _ t: VoiceCopy) -> some View {
        let loaded = model.player.path == note.recording.path
        let playing = loaded && model.player.playing
        let progress = loaded && model.player.durationMs > 0 ? Double(model.player.positionMs) / Double(model.player.durationMs) : 0
        return HStack(spacing: QhSpacing.x3) {
            ZStack {
                Circle().stroke(qh.colors.hairline, lineWidth: 3)
                Circle().trim(from: 0, to: progress).stroke(qh.colors.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round)).rotationEffect(.degrees(-90))
                Image(systemName: playing ? "pause.fill" : "play.fill").font(.system(size: 14, weight: .bold)).foregroundStyle(qh.colors.primary)
            }
            .frame(width: 44, height: 44)
            .pressable(haptic: .tick, shape: Circle()) { model.voice.toggle(path: note.recording.path) }
            .accessibilityLabel(playing ? t.pause : t.play)
            VStack(alignment: .leading, spacing: 2) {
                Text(momentText(note.recording.epochMillis)).qhText({ $0.title2 })
                Text(voiceClock(note.durationMs)).qhText({ $0.callout }, color: { $0.inkSecondary }).monospacedDigit()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.x3).frame(minHeight: 60)
        .background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
    }

    private func momentText(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: copy.language.name.lowercased())
        f.dateStyle = .medium; f.timeStyle = .short; f.doesRelativeDateFormatting = true
        return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private func delete(_ note: VoiceNote) {
        model.commitPending()
        QhHaptics.shared.perform(.longPress)
        withQhAnimation(QhMotion.snappy) { model.pendingDelete = note }
        Task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if model.pendingDelete?.recording.path == note.recording.path { model.commitPending() }
        }
    }

    private func undoBar(_ t: VoiceCopy) -> some View {
        HStack {
            Text(t.deleted).qhText({ $0.callout }, color: { $0.bg })
            Spacer()
            Button { withQhAnimation(QhMotion.snappy) { model.pendingDelete = nil } } label: {
                Text(t.undo).qhText({ $0.label }, color: { $0.gold }).frame(minHeight: QhSpacing.minTouch)
            }
        }
        .padding(.horizontal, QhSpacing.x4).background(qh.colors.ink, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func recording(_ rec: RecorderStateRecording, _ t: VoiceCopy) -> some View {
        VStack(spacing: QhSpacing.x4) {
            HStack(spacing: QhSpacing.x2) {
                PulsingDot()
                Text(t.recording).qhText({ $0.title2 }, color: { $0.danger })
            }
            Text(voiceClock(rec.elapsedMs)).font(.custom("Manrope-Bold", size: 44)).monospacedDigit().contentTransition(.numericText())
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(qh.colors.surfaceStrong)
                    Capsule().fill(qh.colors.primary).frame(width: g.size.width * CGFloat(min(max(rec.level, 0), 1) * 0.96 + 0.04))
                        .animation(.linear(duration: 0.1), value: rec.level)
                }
            }
            .frame(height: 6).accessibilityLabel(t.loudness)
            Text(t.recordingHint).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
            QhButton(t.stop, variant: .danger, size: .l, icon: "stop.fill", fullWidth: true) { model.stopRecording() }
        }
        .frame(maxWidth: .infinity).padding(.top, QhSpacing.x4)
    }

    private func denied(_ denial: VoiceModel.Denial, _ t: VoiceCopy) -> some View {
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            Text(t.deniedTitle).qhText({ $0.title2 })
            Text(t.deniedBody).qhText({ $0.body }, color: { $0.inkSecondary })
            if denial == .forGood {
                QhButton(t.openSettings, size: .m, fullWidth: true) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
            } else {
                QhButton(t.tryAgain, size: .m, fullWidth: true) { model.startRecording(toaster: env.toaster, failed: t.startFailed) }
            }
        }
    }
}

private struct PulsingDot: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @State private var on = false
    var body: some View {
        Circle().fill(qh.colors.danger).frame(width: 12, height: 12).opacity(on || reduced ? 1 : 0.35)
            .onAppear { guard !reduced else { return }; withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) { on = true } }
            .accessibilityHidden(true)
    }
}

/// "1:05" (`VoiceFormat.clock`).
func voiceClock(_ ms: Int64) -> String {
    let total = max(ms, 0) / 1000
    return "\(total / 60):\(String(format: "%02d", Int(total % 60)))"
}
