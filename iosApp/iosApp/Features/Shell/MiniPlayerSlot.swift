import ComposeApp
import SwiftUI

/// The mini player above the tab bar on the three tab roots: a pill with the play button (ringed by the progress of the ayah), the surah,
/// "Ayah 12 - 1.25x" and a close button. Slides up with a spring when audio starts, down when it stops; drag it down to stop; a tap
/// opens the reader of the playing surah. The progress is read only inside the play button, so the clock redraws nothing else.
struct MiniPlayerSlot: View {
    let home: HomeModel
    @Environment(\.qhReduceMotion) private var reduced
    @State private var held: MiniPlayerUi?

    var body: some View {
        let player = home.player.value
        let shown = player ?? held
        ZStack {
            if let shown, player != nil {
                MiniPlayerPill(player: shown, progress: CGFloat(truncating: home.playbackProgress.value),
                               onToggle: { home.togglePlayback() }, onStop: { home.stopPlayback() })
                    .padding(.horizontal, 12).padding(.bottom, 8).padding(.top, 4)
                    .transition(reduced ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(reduced ? .easeOut(duration: 0.15) : QhMotion.bounds, value: player != nil)
        .onChange(of: player) { _, now in if let now { held = now } }
    }
}

/// The pill itself: shared by the tab roots (`MiniPlayerSlot`) and the mushaf pages.
struct MiniPlayerPill: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @Environment(\.dynamicTypeSize) private var typeSize
    let player: MiniPlayerUi
    let progress: CGFloat
    let onToggle: () -> Void
    let onStop: () -> Void
    @State private var drag: CGFloat = 0

    var body: some View {
        let h = copy.home
        let height: CGFloat = typeSize.isAccessibilitySize ? 96 : 64
        HStack(spacing: 0) {
            MiniPlayButton(player: player, progress: progress, onToggle: onToggle)
            VStack(alignment: .leading, spacing: 0) {
                Text(player.name).font(.custom("Manrope-SemiBold", size: 15)).foregroundStyle(qh.colors.ink).lineLimit(1)
                Text(player.hasError ? h.audioError : h.playerSub(ayah: player.ayah, speed: player.speed))
                    .qhText({ $0.callout }, color: { player.hasError ? $0.danger : $0.inkSecondary }).lineLimit(1)
            }
            .padding(.horizontal, QhSpacing.x3)
            .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                QhHaptics.shared.perform(.tick)
                onStop()
            } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkSecondary)
                    .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(h.stop)
        }
        .padding(.leading, QhSpacing.x2).padding(.trailing, QhSpacing.x1)
        .frame(maxWidth: 560, minHeight: height)
        .background(qh.colors.surface, in: Capsule())
        .overlay { Capsule().stroke(qh.colors.hairline, lineWidth: 1) }
        .qhSoftShadow()
        .contentShape(Capsule())
        .onTapGesture {
            QhHaptics.shared.perform(.tick)
            router.openReader(Int(player.surah), ayah: Int(player.ayah), origin: "player")
        }
        .offset(y: drag)
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { drag = max($0.translation.height, 0) }
                .onEnded { v in
                    if v.translation.height > 56 || v.predictedEndTranslation.height > 200 {
                        QhHaptics.shared.perform(.tick)
                        onStop()
                        drag = 0
                    } else {
                        withAnimation(reduced ? nil : QhMotion.snappy) { drag = 0 }
                    }
                }
        )
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: Text(h.openPlayer)) { router.openReader(Int(player.surah), ayah: Int(player.ayah), origin: "player") }
    }
}

/// Play / pause in a 36 pt disc inside a ring that fills as the ayah plays.
private struct MiniPlayButton: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    let player: MiniPlayerUi
    let progress: CGFloat
    let onToggle: () -> Void
    @State private var smooth: CGFloat = 0

    var body: some View {
        let label = player.isPlaying ? copy.home.pause : copy.home.play
        ZStack {
            Circle().stroke(qh.colors.hairline, lineWidth: 3)
            Circle().trim(from: 0, to: smooth).stroke(qh.colors.primary, style: StrokeStyle(lineWidth: 3, lineCap: .round)).rotationEffect(.degrees(-90))
            Circle().fill(qh.colors.primary).padding(5)
            Group {
                if player.hasError {
                    Image(systemName: "exclamationmark").font(.system(size: 14, weight: .bold))
                } else if player.isPreparing {
                    DotSpinner(color: qh.colors.onPrimary)
                } else {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 14, weight: .bold))
                        .id(player.isPlaying).transition(.scale(scale: 0.5).combined(with: .opacity))
                }
            }
            .foregroundStyle(qh.colors.onPrimary)
        }
        .frame(width: 44, height: 44)
        .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
        .contentShape(Circle())
        .pressable(haptic: .tick, shape: Circle(), highlight: false) { onToggle() }
        .animation(reduced ? nil : QhMotion.snappy, value: player.isPlaying)
        .onChange(of: progress) { old, now in
            if reduced || now < old { smooth = now } else { withAnimation(.linear(duration: 0.2)) { smooth = now } }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }
}

extension View {
    /// Puts the mini player slot above the tab bar. Apply to the ROOT view of each tab (not to pushed screens: they have their own dock).
    func miniPlayerInset(_ home: HomeModel) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerSlot(home: home) }
    }
}
