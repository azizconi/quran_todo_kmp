import ComposeApp
import SwiftUI

/// The "Lesson" tab: greeting + "Lesson of the day" with the settings gear, the lesson card, the plan row, the "Check" row and the
/// reading-plan entry. Port of `LessonTab.kt`. The lesson card is `LessonCardView` (S4 replaces it); everything around it is here.
struct LessonTabView: View {
    let home: HomeModel
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var stagger = StaggerState(screen: "lessonTab")

    var body: some View {
        let ui = home.ui.value
        QhLargeTitleScroll(title: copy.home.lessonTitle, overline: copy.home.greeting) {
            Color.clear.frame(width: 8, height: 1)
        } actions: {
            QhIconLabelButton(icon: "gearshape", label: copy.home.settings) { router.push(.settings) }
                .padding(.trailing, QhSpacing.x3)
        } content: {
            VStack(spacing: QhSpacing.x2) {
                switch ui.phase {
                case .error:
                    QhEmptyState(title: copy.home.loadErrorTitle, message: copy.home.loadErrorBody, actionLabel: copy.home.retry) { home.retry() }
                        .frame(minHeight: 360)
                default:
                    if let card = ui.lesson {
                        LessonCardView(card: card, home: home)
                            .staggeredEnter(index: 0, state: stagger)
                        if let row = ui.planRow {
                            PlanRowView(row: row) { router.push(.plan) }
                                .staggeredEnter(index: 1, state: stagger)
                        }
                        if ui.quizReady {
                            QuizRowView { router.push(.quiz) }
                                .staggeredEnter(index: 2, state: stagger)
                        }
                        KhatmHomeCards(onOpenKhatm: { router.push(.khatm(pickTarget: false)) },
                                       onOpenKhatmPicker: { router.push(.khatm(pickTarget: true)) },
                                       onOpenMushaf: { router.push(.mushaf(page: $0, ayahKey: nil)) })
                            .staggeredEnter(index: 3, state: stagger)
                    } else {
                        Skeleton(shape: qh.shapes.lgShape).frame(height: 196)
                    }
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x6)
        }
        .toolbar(.hidden, for: .navigationBar)
        .miniPlayerInset(home)
        .onAppear { if ui.phase == .content && ui.lesson != nil { stagger.markPlayed() } }
        .onChange(of: ui.lesson == nil) { _, missing in if !missing && ui.phase == .content { stagger.markPlayed() } }
    }
}

/// "Juz Amma · covered 10 of 564 · finish ~2 March · Change": the visible way to "My plan".
struct PlanRowView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var typeSize
    let row: PlanRowUi
    let onTap: () -> Void

    var body: some View {
        let finish = row.finishEpochDay.map { copy.plan.dateLabel(epochDay: Int32(truncating: $0), today: row.today) }
        let text = copy.tabs.planRow(name: copy.progress.scopeName(scope: row.scope), covered: row.covered, total: row.total, finish: finish)
        QhCard(padding: 0, onTap: onTap) {
            // At accessibility sizes the action goes under the text instead of being squeezed beside it.
            let layout = typeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: QhSpacing.x1))
                : AnyLayout(HStackLayout(spacing: QhSpacing.x3))
            layout {
                Text(text).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(typeSize.isAccessibilitySize ? nil : 3)
                if !typeSize.isAccessibilitySize { Spacer(minLength: QhSpacing.x2) }
                Text(copy.tabs.planChange).qhText({ $0.title2 }, color: { $0.primary }).lineLimit(1)
            }
            .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x2)
            .frame(minHeight: QhSpacing.minTouch)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy.tabs.planRowDescription(text: text))
        .accessibilityAddTraits(.isButton)
    }
}

/// "Check · 5 questions on what you know": a calm row under the lesson, available while the plan is paused too.
struct QuizRowView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let onTap: () -> Void

    var body: some View {
        let sub = copy.quiz.entrySubtitle(questions: 5)
        QhCard(padding: 0, onTap: onTap) {
            HStack(spacing: QhSpacing.x3) {
                QhIconTile(icon: "checklist", tint: qh.colors.primary)
                VStack(alignment: .leading, spacing: 0) {
                    Text(copy.quiz.title).qhText({ $0.title2 }).lineLimit(1)
                    Text(sub).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2)
                }
                Spacer(minLength: QhSpacing.x2)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
            }
            .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(copy.quiz.title), \(sub)")
        .accessibilityAddTraits(.isButton)
    }
}
