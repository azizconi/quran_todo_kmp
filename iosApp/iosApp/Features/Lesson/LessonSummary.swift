import ComposeApp
import SwiftUI

/// The end of the lesson (`LessonSummary.kt`): the title (the header steps already show what was done), the streak, the forecast of the
/// plan and what waits tomorrow. The calm celebration plays only when the lesson was closed in this visit, not when it is opened again.
struct LessonSummaryView: View {
    let model: LessonModel
    let summary: LessonUiSummary
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @State private var burst = 0
    @State private var celebrated = false
    @State private var quizReady = false
    @State private var finishedSurah: Int?
    @State private var shown = false

    var body: some View {
        let c = copy.lessonCopy
        let extras = copy.summaryExtrasCopy
        let plan = summary.plan
        let steps = LessonCards.shared.steps(plan: plan)
        let done = steps.reduce(0) { $0 + Int($1.done) }
        let streak = Int(truncating: model.streak.value)
        let hardCount = Int(truncating: model.hardCount.value)
        let language = copy.language
        let tomorrowNew: String? = plan.tomorrow.newPassage.map { range in
            "\(model.surahTitle(Int(range.surah), language: language)) \(range.startAyah == range.endAyah ? "\(range.startAyah)" : "\(range.startAyah)–\(range.endAyah)")"
        }
        let forecastLine: String? = model.forecast.value.flatMap { forecast in
            if forecast.finished { return c.planFinished }
            if forecast.behindAyahs > 0 { return c.forecastBehind(ayahs: forecast.behindAyahs) }
            if let finish = forecast.finishEpochDay { return c.forecastFinish(date: copy.day(Int(truncating: finish))) }
            return nil
        }
        let durations = model.lessonDurations.value
        let takesLine = LessonDurations.shared.medianMinutes(stored: durations).map { extras.takesAbout(minutes: Int32(truncating: $0)) }
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: QhSpacing.x4) {
                    ZStack {
                        CelebrationBurst(trigger: burst, radius: 150)
                        Text(c.summaryTitle).qhText({ $0.headline }).multilineTextAlignment(.center)
                            .accessibilityAddTraits(.isHeader)
                    }
                    .scaleEffect(shown || reduced ? 1 : 0.94).opacity(shown || reduced ? 1 : 0)
                    Text(c.summaryCaption).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                        .opacity(shown || reduced ? 1 : 0)
                    if let surah = finishedSurah {
                        OrnamentDivider().padding(.top, QhSpacing.x2)
                        Text(extras.surahComplete(name: model.surahTitle(surah, language: language)))
                            .qhText({ $0.title2 }).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    QhCard(padding: QhSpacing.cardLarge) {
                        VStack(alignment: .leading, spacing: QhSpacing.x2) {
                            if streak > 0 { line(c.streak(days: Int32(streak))) }
                            if let forecastLine { line(forecastLine) }
                            // The real time of the lesson, once enough lessons were measured.
                            if let takesLine { line(takesLine) }
                            Text(c.tomorrow(newRange: tomorrowNew, dueCount: plan.tomorrow.dueCount)).qhText({ $0.title2 })
                        }
                    }
                    .offset(y: shown || reduced ? 0 : 14).opacity(shown || reduced ? 1 : 0)
                    ReminderAskCard()
                }
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x6).padding(.bottom, QhSpacing.x3)
            }
            .scrollBounceBehavior(.basedOnSize)
            VStack(spacing: 0) {
                if hardCount > 0 || quizReady {
                    // Secondary ways to go on, as text links above the one primary button.
                    HStack(spacing: QhSpacing.x2) {
                        if hardCount > 0 {
                            QhButton(copy.drillCopy.train(count: Int32(hardCount)), variant: .text, size: .m) { router.push(.hardDrill) }
                        }
                        if quizReady {
                            QhButton(copy.quiz.lessonButton(questions: Int32(QuizBuilder.shared.DEFAULT_COUNT)), variant: .text, size: .m) { router.push(.quiz) }
                        }
                    }
                }
                QhButton(c.toHome, fullWidth: true) { router.goHome() }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.bottom, QhSpacing.x3)
        }
        .task {
            withAnimation(reduced ? nil : QhMotion.enter(QhMotion.long)) { shown = true }
        }
        .task { for await ready in IosLessonBridge.shared.quizReady() { quizReady = ready.boolValue } }
        .task(id: done) {
            for await surah in IosLessonBridge.shared.finishedSurah(plan: summary.plan) {
                withQhAnimation(QhMotion.gentle) { finishedSurah = surah.map { Int(truncating: $0) } }
            }
        }
        .task(id: done) {
            // A small burst follows when the lesson was closed just now.
            try? await Task.sleep(for: .milliseconds(500))
            if summary.closedNow && !celebrated {
                celebrated = true
                burst += 1
            }
        }
    }

    private func line(_ text: String) -> some View {
        Text(text).qhText({ $0.callout }, color: { $0.inkSecondary })
    }
}
