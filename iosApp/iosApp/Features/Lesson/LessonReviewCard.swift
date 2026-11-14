import ComposeApp
import SwiftUI

/// One repetition of the Recent or Old block ("2 of 5"): first the card of the passage with "listen first", then "Recall". Grading it
/// writes the repetition and the lesson moves to the next card (`LessonReviewCard.kt`).
struct ReviewCard: View {
    let model: LessonModel
    let work: LessonUiWorking
    let card: LessonCardReview
    @Environment(Copy.self) private var copy

    var body: some View {
        if work.progress.phase == .recall {
            VStack(spacing: 0) {
                PassageHeader(model: model, work: work, overline: Self.overline(card, copy: copy))
                RecallStage(model: model, work: work).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else {
            ReviewIntro(model: model, work: work, card: card)
        }
    }

    static func overline(_ card: LessonCardReview, copy: Copy) -> String {
        let c = copy.lessonCopy
        let block = card.block == .old ? c.stepOld : c.stepRecent
        return "\(block) · \(c.stepProgress(done: card.position, total: card.total))"
    }
}

private struct ReviewIntro: View {
    let model: LessonModel
    let work: LessonUiWorking
    let card: LessonCardReview
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let c = copy.lessonCopy
        let range = card.range
        let title = model.surahTitle(Int(range.surah), language: copy.language)
        let arabic = model.surahArabic(Int(range.surah))
        let audio = model.audioState.value
        let item = card.item
        let listened = card.block == .old && model.listenedToday.value.contains(KotlinLong(value: item.passageId))
        ScrollView {
            VStack(spacing: QhSpacing.x5) {
                QhCard(style: .hero, padding: QhSpacing.cardLarge) {
                    VStack(spacing: 0) {
                        Text(ReviewCard.overline(card, copy: copy).uppercased()).qhText({ $0.overline }, color: { $0.gold })
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if !arabic.isEmpty {
                            Text(verbatim: arabic)
                                .qhArabic(qh.type.quranHeader(LessonArabic.face, size: scaledArabicSize(38, readingFontSize: Int(LessonArabic.size))),
                                          color: qh.colors.primary)
                                .environment(\.layoutDirection, .rightToLeft)
                                .padding(.top, QhSpacing.x4)
                                .accessibilityHidden(true)
                        }
                        Text(title).qhText({ $0.headline }).multilineTextAlignment(.center)
                            .padding(.top, QhSpacing.x2).accessibilityAddTraits(.isHeader)
                        Text("\(c.range(start: range.startAyah, end: range.endAyah)) · \(c.ayahCount(count: range.ayahCount))")
                            .qhText({ $0.body }, color: { $0.inkSecondary })
                        if listened {
                            // Listening is a help: the card is still to be recalled, it only says the passage was heard today.
                            Text(copy.listen.listenedBadge).qhText({ $0.callout }, color: { $0.learned }).padding(.top, QhSpacing.x3)
                        }
                        if item.overdueDays > 0 {
                            Text(c.overdue(days: item.overdueDays)).qhText({ $0.callout }, color: { $0.due }).padding(.top, QhSpacing.x3)
                        }
                        if !item.hardAyahs.isEmpty {
                            Text(c.hardCount(count: Int32(item.hardAyahs.count))).qhText({ $0.callout }, color: { $0.due })
                                .padding(.top, item.overdueDays > 0 ? QhSpacing.x1 : QhSpacing.x3)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x4).padding(.bottom, QhSpacing.x3)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: QhSpacing.x3) {
                QhButton(audio.playing ? c.listenPause : c.listenFirst, variant: .text, size: .m,
                         icon: audio.playing ? "pause.fill" : "headphones", fullWidth: true) { model.vm.toggleListenOnce(work: work) }
                QhButton(c.startRecall, fullWidth: true) { model.vm.next(work: work) }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
            .background(qh.colors.bg)
        }
    }
}
