#if DEBUG
import ComposeApp
import SwiftUI

/// A harness for the playback of IOS-S2 (debug only, no design): plays a surah from the shared `PlaybackController` with a repeat
/// count and a speed, shows what the player does, and drives play/pause/next/previous. The lock screen (Now Playing) and the
/// remote commands are the system's. To show it, put `PlaybackDebugView()` in the `WindowGroup` of `iOSApp` for the test.
struct PlaybackDebugView: View {
    @State private var surah = 1
    @State private var repeatCount = 3
    @State private var speed: Float = 1.25
    @State private var echo = false
    @State private var status = "idle"
    private let timer = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Playback debug").font(.title2.bold())
            Text(status).font(.footnote.monospaced()).accessibilityIdentifier("playbackStatus")
            Stepper("Surah \(surah)", value: $surah, in: 1...114)
            Stepper("Repeat each ayah \(repeatCount)x", value: $repeatCount, in: 1...10)
            Stepper("Speed \(String(format: "%.2f", speed))", value: $speed, in: 0.5...2.0, step: 0.25)
                .onChange(of: speed) { _, new in IosPlaybackDebug.shared.setSpeed(speed: new, repeatCount: Int32(repeatCount)) }
            Toggle("Echo silences", isOn: $echo)
            HStack {
                Button("Play") {
                    if !IosPlaybackDebug.shared.play(surah: Int32(surah), repeatCount: Int32(repeatCount), speed: speed, echo: echo) {
                        status = "text of the surah is not loaded yet"
                    }
                }
                Button("Pause / resume") { IosPlaybackDebug.shared.toggle() }
                Button("Prev") { IosPlaybackDebug.shared.previous() }
                Button("Next") { IosPlaybackDebug.shared.next() }
                Button("Stop") { IosPlaybackDebug.shared.stop() }
            }
            .buttonStyle(.bordered)
            Spacer()
        }
        .padding()
        .onReceive(timer) { _ in status = IosPlaybackDebug.shared.status() }
    }
}

#Preview { PlaybackDebugView() }
#endif
