import ComposeApp
import SwiftUI

/// PLACEHOLDER of the "Today's lesson" card (S4 replaces this file's body with the real faces: lines with check dots, minutes,
/// tomorrow, lighter week, listen the old, paused / plan complete / returning). The call site in `LessonTabView` stays:
/// `LessonCardView(card: LessonCardUi, home: HomeModel)`. Opens the lesson, or the plan when there is none.
struct LessonCardView: View {
    let card: LessonCardUi
    let home: HomeModel
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let h = copy.home
        QhCard(style: .hero, padding: QhSpacing.cardLarge) {
            switch card.state {
            case .noPlan:
                text(h.lessonInviteTitle, h.lessonInviteBody)
                QhButton(h.lessonMakePlan, fullWidth: true) { router.push(.plan) }.padding(.top, QhSpacing.x4)
            case .planFinished:
                text(h.lessonPlanFinished, h.lessonPlanFinishedBody)
                QhButton(h.lessonNewPlan, fullWidth: true) { router.push(.plan) }.padding(.top, QhSpacing.x4)
            case .paused:
                text(h.lessonTitle, nil)
                QhButton(h.myPlan, variant: .tonal, fullWidth: true) { router.push(.plan) }.padding(.top, QhSpacing.x4)
            default:
                Text(h.lessonTitle.uppercased()).qhText({ $0.overline }, color: { $0.gold })
                ForEach(Array(card.lines.enumerated()), id: \.offset) { _, line in
                    Text("\(line.surahName) \(line.startAyah)-\(line.endAyah)")
                        .qhText({ $0.title2 }).padding(.top, QhSpacing.x2)
                }
                if card.minutes > 0 {
                    Text(h.lessonMinutes(n: card.minutes)).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x2)
                }
                QhButton(card.state == .continue ? h.lessonContinue : (card.state == .done ? h.lessonDone : h.lessonStart),
                         fullWidth: true) { router.push(.lesson) }
                    .padding(.top, QhSpacing.x4)
            }
        }
    }

    @ViewBuilder private func text(_ title: String, _ body: String?) -> some View {
        Text(title).qhText({ $0.headline })
        if let body { Text(body).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x2) }
    }
}
