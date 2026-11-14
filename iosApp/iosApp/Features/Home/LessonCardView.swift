import ComposeApp
import SwiftUI

/// The "Today's lesson" card of the Lesson tab (`LessonCard.kt`): up to three lines with check dots (New, Recent, Old), the time
/// estimate and one button that starts, continues or (when everything is done) only reports the lesson; with no plan it invites to make
/// one, with a finished plan it offers a new one, with a paused plan it says until when and offers "Continue now", and after missed
/// days it greets with "Welcome back". "Listen to the old" opens the queue sheet. A long press leads to "My plan".
struct LessonCardView: View {
    let card: LessonCardUi
    let home: HomeModel
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var confirmResume = false
    @State private var listenOpen = false

    var body: some View {
        Group {
            switch card.state {
            case .paused: pausedCard
            case .noPlan:
                if card.quietInvite {
                    PlanInvitation(icon: "graduationcap", title: nil, message: copy.lessonCard.quietInvite, action: copy.home.lessonMakePlan,
                                   primary: false) { router.push(.plan) }
                } else {
                    PlanInvitation(icon: "graduationcap", title: copy.home.lessonInviteTitle, message: copy.home.lessonInviteBody,
                                   action: copy.home.lessonMakePlan, primary: true) { router.push(.plan) }
                }
            case .planFinished:
                PlanInvitation(icon: "leaf", title: copy.home.lessonPlanFinished, message: copy.home.lessonPlanFinishedBody,
                               action: copy.home.lessonNewPlan, primary: true) { router.push(.plan) }
            default:
                lessonBody.qhZoomSource(QhZoomID.lesson)
            }
        }
        .qhSheet(isPresented: $listenOpen, detents: [.large]) { ListenOldSheet(onDismiss: { listenOpen = false }) }
        .alert(copy.pause.resumeTitle, isPresented: $confirmResume) {
            Button(copy.pause.resumeConfirm) { home.resumePlan() }
            Button(copy.pause.cancel, role: .cancel) {}
        } message: {
            Text(copy.pause.resumeBody)
        }
    }

    // MARK: Paused

    /// "Plan paused until 11 November", when the lessons return, and "Continue now" (which asks to confirm: it moves the calendar back).
    /// A tap on the card opens "My plan" (where the pause is extended).
    private var pausedCard: some View {
        let until = Int(card.pausedUntilEpochDay)
        return PlanInvitation(icon: "pause.fill", title: copy.pause.cardTitle(untilDate: copy.day(until)),
                              message: copy.pause.cardBody(resumeDate: copy.day(until + 1)), action: copy.pause.resumeNow, primary: true,
                              onCardTap: { router.push(.plan) }) { confirmResume = true }
    }

    // MARK: Start / Continue / Done / Returning

    private var lessonBody: some View {
        let h = copy.home
        let done = card.state == .done
        let returning = card.state == .returning
        let listening = home.listening.value
        return QhCard(style: .hero, padding: QhSpacing.cardLarge, onTap: { router.push(.lesson) }, onLongPress: { router.push(.plan) }) {
            HStack(alignment: .firstTextBaseline) {
                Text(h.lessonTitle.uppercased()).qhText({ $0.overline }, color: { $0.primary }).lineLimit(1)
                Spacer(minLength: QhSpacing.x2)
                if !done && card.minutes > 0 {
                    Text(card.state == .continue ? h.lessonMinutesLeft(n: card.minutes) : h.lessonMinutes(n: card.minutes))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                if returning {
                    Text(copy.welcomeBack.title).qhText({ $0.title1 }).padding(.top, QhSpacing.x1)
                    Text(copy.welcomeBack.missed(days: card.missedDays)).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(card.lines.enumerated()), id: \.offset) { _, line in LessonLineRow(line: line) }
                }
                .padding(.top, QhSpacing.x1)
                // One line each for the two words of the method: what "Recent" and "Old" mean.
                if card.lines.contains(where: { $0.kind == .recent }) {
                    Text(copy.lessonCard.recentHint).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
                }
                if card.lines.contains(where: { $0.kind == .old }) {
                    Text(copy.lessonCard.oldHint).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
                }
                if card.catchingUp {
                    HStack(alignment: .top, spacing: QhSpacing.x2) {
                        Image(systemName: "clock.arrow.circlepath").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.due)
                            .padding(.top, 2)
                        Text(h.lessonCatchingUp).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                    .padding(.top, QhSpacing.x2)
                }
                if let until = card.lighterUntilEpochDay {
                    Text(copy.welcomeBack.lighterLine(date: copy.day(Int(truncating: until))))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x2)
                }
                Color.clear.frame(height: QhSpacing.x4)
                if done {
                    // "Lesson done" is a status, not a button. The summary is the link under "Tomorrow".
                    HStack(spacing: QhSpacing.x2) {
                        Image(systemName: "checkmark").font(.system(size: 17, weight: .bold)).foregroundStyle(qh.colors.learned)
                        Text(h.lessonDone).qhText({ $0.title2 })
                    }
                    .accessibilityElement(children: .combine)
                    if let tomorrow = card.tomorrow {
                        Text(TomorrowTextKt.tomorrowText(tomorrow: tomorrow, copy: h))
                            .qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x2)
                    }
                    QhButton(copy.lessonCard.lessonSummary, variant: .text, size: .m, fullWidth: true) { router.push(.lesson) }
                } else {
                    QhButton(card.state == .continue ? h.lessonContinue : (returning ? copy.lessonCard.continueMemorizing : h.lessonStart),
                             icon: "play.fill", fullWidth: true) { router.push(.lesson) }
                }
                if let old = card.oldListen {
                    QhButton(listening.map { copy.listen.oldPlaying(position: $0.position, total: $0.total) } ?? copy.listen.oldButton(minutes: old.minutes),
                             variant: .text, size: .m, icon: "headphones", fullWidth: true) { listenOpen = true }
                        .padding(.top, QhSpacing.x2)
                }
            }
        }
    }
}

/// "New: An-Naba 1-5", "Recent: 3 passages", "Old: Al-Mulk 1-15 +2 · Listened": a check dot (empty until done) and the line.
private struct LessonLineRow: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let line: LessonLineUi

    var body: some View {
        let text = Self.text(line, copy: copy)
        HStack(spacing: QhSpacing.x3) {
            CheckDot(done: line.done)
            Text(text).qhText({ $0.body }, color: { line.done ? $0.inkSecondary : $0.ink }).lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(.vertical, QhSpacing.x1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }

    static func text(_ line: LessonLineUi, copy: Copy) -> String {
        let range = HomeCopyKt.passageRangeLabel(surahName: line.surahName, start: line.startAyah, end: line.endAyah)
        let h = copy.home
        var text: String
        switch line.kind {
        case .theNew: text = h.lessonNew(range: range)
        case .recent: text = h.lessonRecent(count: line.count, doneCount: line.doneCount)
        default: text = h.lessonOld(range: line.count > 1 ? "\(range) +\(line.count - 1)" : range)
        }
        // Listening is shown, it does not make the line done.
        if line.kind == .old && line.listened { text += " · \(copy.listen.listenedBadge)" }
        return text
    }
}

/// A 22pt circle: empty until the line is done, then solid with a check that pops in (shape and fill, not only colour).
private struct CheckDot: View {
    @Environment(\.qh) private var qh
    let done: Bool

    var body: some View {
        ZStack {
            Circle().fill(done ? qh.colors.learned : .clear)
            Circle().strokeBorder(done ? qh.colors.learned : qh.colors.inkTertiary, lineWidth: 1.5)
            Image(systemName: "checkmark").font(.system(size: 11, weight: .heavy)).foregroundStyle(qh.colors.onLearned)
                .scaleEffect(done ? 1 : 0.3).opacity(done ? 1 : 0)
        }
        .frame(width: 22, height: 22)
        .qhAnimation(QhMotion.bouncy, value: done)
        .accessibilityHidden(true)
    }
}

/// The invitation to make a plan, the "plan complete" card and the paused card: an icon, a title, a line and one button.
private struct PlanInvitation: View {
    @Environment(\.qh) private var qh
    let icon: String
    let title: String?
    let message: String
    let action: String
    var primary = true
    var onCardTap: (() -> Void)?
    let onAction: () -> Void

    var body: some View {
        QhCard(style: .hero, padding: QhSpacing.cardLarge, onTap: onCardTap) {
            HStack(spacing: QhSpacing.x4) {
                Image(systemName: icon).font(.system(size: 22, weight: .semibold)).foregroundStyle(qh.colors.gold)
                    .frame(width: 48, height: 48).background(qh.colors.goldContainer, in: Circle())
                VStack(alignment: .leading, spacing: QhSpacing.x1) {
                    if let title { Text(title).qhText({ $0.title1 }) }
                    Text(message).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            }
            QhButton(action, variant: primary ? .primary : .tonal, fullWidth: true, action: onAction).padding(.top, QhSpacing.x4)
        }
    }
}
