import ComposeApp
import SwiftUI

/// "Welcome back" (S3.6): shown inside the lesson before the first card when `LessonViewModel.welcomeBack != nil`. Calm: no red, no
/// blame. This is the pure view; the lesson screen (S4) owns the view model and wires the two answers:
/// ```swift
/// if let welcome = lesson.welcomeBack { WelcomeBackView(ui: welcome, onCatchUp: { vm.chooseCatchUp() }, onLighter: { vm.chooseLighter() }) }
/// ```
struct WelcomeBackView: View {
    let ui: WelcomeBackUi
    let onCatchUp: () -> Void
    let onLighter: () -> Void

    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let c = copy.welcomeBack
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                Text(c.title).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                QhCard(padding: QhSpacing.cardLarge) {
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        Text(c.missed(days: ui.missedDays)).qhText({ $0.title2 })
                        Text(c.today(repetitions: ui.repetitions, minutes: ui.minutes, hasNew: ui.hasNew))
                            .qhText({ $0.body }, color: { $0.inkSecondary })
                        if ui.bestStreak > 0 {
                            Text(c.best(days: ui.bestStreak)).qhText({ $0.callout }, color: { $0.inkSecondary })
                        }
                    }
                }
                QhButton(c.catchUp, fullWidth: true, action: onCatchUp)
                QhButton(c.lighter, variant: .tonal, fullWidth: true, action: onLighter)
                Text(c.lighterNote).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.vertical, QhSpacing.x4)
        }
        .background(qh.colors.bg)
    }
}
