import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `QuizViewModel` ("Check yourself" and "Tell apart") with its state.
@MainActor
@Observable
final class QuizModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.quiz() }
    let ui: FlowState<QuizUi>
    let chapterNames: FlowState<[KotlinInt: ChapterNameModel]>
    var vm: QuizViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        chapterNames = FlowState(holder.vm.chapterNames)
        holder.vm.onLanguage(language: IosUi.shared.language.value)
    }

    func title(_ surah: Int, language: AppLanguage) -> String {
        vm.surahTitle(number: Int32(surah), language: language, names: chapterNames.value)
    }

    func arabicName(_ surah: Int) -> String { vm.surahArabicName(number: Int32(surah), names: chapterNames.value) }

    /// The ayah as the reader draws it, or nil while the text is missing.
    func display(_ surah: Int, _ ayah: Int) -> QuranAyahDisplay? {
        vm.ayahWords(surah: Int32(surah), ayah: Int32(ayah)).map { QuranAyahDisplay.make(surah: surah, ayah: ayah, databaseText: $0.text) }
    }

    /// "Al-Mulk 12".
    func place(_ surah: Int, _ ayah: Int, language: AppLanguage) -> String {
        HomeCopyKt.passageRangeLabel(surahName: title(surah, language: language), start: Int32(ayah), end: Int32(ayah))
    }
}

/// What the screen asks: the usual five kinds, or only "Tell apart" (all pairs, or the group of one ayah).
enum QuizScreenMode: Hashable {
    case mixed
    case tellApart(surah: Int, ayah: Int)
}

/// The quiz "Check" and "Tell apart" (`QuizScreen.kt`, `Dest.Quiz`, `Dest.TellApart`): questions one page at a time, sliding like the
/// lessons' cards, then the result. A missed ayah becomes difficult at once; nothing is written to the review log.
struct QuizScreen: View {
    let mode: QuizScreenMode
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var model = QuizModel()
    @State private var started = false

    var body: some View {
        let ui = model.ui.value
        let c = copy.quiz
        let session: QuizSession? = { if case let .playing(p) = onEnum(of: ui) { return p.session } else { return nil } }()
        VStack(spacing: 0) {
            HStack(spacing: QhSpacing.x1) {
                QhIconButton(icon: "chevron.backward", label: c.back, filled: false) { router.pop() }
                Text(isTell ? copy.similarKotlin.tellApart : c.title).qhText({ $0.title1 }).lineLimit(1)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if let session, !session.finished {
                    Text(c.progress(position: session.position + 1, total: session.total))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                }
            }
            .padding(.leading, 4).padding(.trailing, QhSpacing.screen)
            .frame(height: 56)
            if let session {
                QhLinearProgress(progress: Double(session.finished ? session.total : Int32(session.answers.count)) / Double(max(session.total, 1)),
                                 height: 4, color: qh.colors.learned)
                    .padding(.horizontal, QhSpacing.screen)
            }
            ZStack {
                switch onEnum(of: ui) {
                case .loading:
                    VStack(alignment: .leading, spacing: QhSpacing.x4) {
                        Text(c.loadingMessage).qhText({ $0.callout }, color: { $0.inkSecondary })
                        Skeleton(shape: qh.shapes.lgShape).frame(height: 160)
                        Skeleton().frame(width: 180, height: 20)
                    }
                    .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                case .tooLittle:
                    QhEmptyState(title: c.emptyTitle, message: c.emptyBody, actionLabel: c.back) { router.pop() }
                case .noPairs:
                    QhEmptyState(title: copy.similarKotlin.emptyTitle, message: copy.similarKotlin.emptyBody, actionLabel: c.back) { router.pop() }
                case let .playing(state):
                    QuizPage(model: model, state: state, tellApartMode: isTell)
                        .id(pageKey(state))
                        .transition(QhMotion.reduced ? .opacity : .asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                                                                removal: .move(edge: .leading).combined(with: .opacity)))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .animation(QhMotion.reduced ? .easeOut(duration: QhMotion.short) : QhMotion.emphasized(QhMotion.long), value: pageKey(ui))
        }
        .background(qh.colors.bg.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .task {
            guard !started else { return }
            started = true
            switch mode {
            case .mixed: model.vm.begin(asked: QuizModeMixed.shared)
            case let .tellApart(surah, ayah):
                let focus = surah > 0 ? AyahKey(surah: Int32(surah), ayah: Int32(ayah)) : nil
                model.vm.begin(asked: QuizModeTellApart(focus: focus))
            }
        }
        .onChange(of: copy.language) { _, language in model.vm.onLanguage(language: language) }
    }

    private var isTell: Bool { if case .tellApart = mode { return true } else { return false } }

    private func pageKey(_ ui: QuizUi) -> String {
        if case let .playing(p) = onEnum(of: ui) { return pageKey(p) }
        return "other"
    }

    private func pageKey(_ p: QuizUiPlaying) -> String {
        p.session.finished ? "\(p.round)-result" : "\(p.round)-\(p.session.position)"
    }
}

extension Copy {
    var similarKotlin: SimilarCopy { IosCopy.shared.similarCopy() }
}

// MARK: - A page

/// The question on screen (or the result), by its kind.
private struct QuizPage: View {
    let model: QuizModel
    let state: QuizUiPlaying
    let tellApartMode: Bool

    var body: some View {
        let session = state.session
        if let question = session.current {
            switch onEnum(of: question) {
            case let .tellApartQuestion(q): TellApartPage(model: model, session: session, question: q)
            case let .orderQuestion(q): OrderPage(model: model, session: session, question: q)
            case let .whereQuestion(q): WherePage(model: model, session: session, question: q)
            default: SelfGradedPage(model: model, session: session, question: question)
            }
        } else {
            QuizResultPage(model: model, state: state, tellApartMode: tellApartMode)
        }
    }
}

/// A question page: the kind and what to do on top, the body that scrolls, and the buttons pinned under it.
struct QuestionFrame<Body: View, Bottom: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    let hint: String
    @ViewBuilder var content: () -> Body
    @ViewBuilder var bottom: () -> Bottom

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                VStack(alignment: .leading, spacing: QhSpacing.x1) {
                    Text(title).qhText({ $0.headline }).accessibilityAddTraits(.isHeader)
                    Text(hint).qhText({ $0.body }, color: { $0.inkSecondary })
                }
                content()
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: QhSpacing.x2, content: bottom)
                .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
                .background(qh.colors.bg.ignoresSafeArea(edges: .bottom))
        }
    }
}

func kindTitle(_ q: QuizQuestion, _ c: QuizCopy) -> String {
    switch onEnum(of: q) {
    case .continueQuestion: return c.continueTitle
    case .nextQuestion: return c.nextTitle
    case .startQuestion: return c.startTitle
    case .orderQuestion: return c.orderTitle
    case .whereQuestion: return c.whereTitle
    case .tellApartQuestion: return c.tellApartTitle
    }
}

func kindHint(_ q: QuizQuestion, _ c: QuizCopy) -> String {
    switch onEnum(of: q) {
    case .continueQuestion: return c.continueHint
    case .nextQuestion: return c.nextHint
    case .startQuestion: return c.startHint
    case .orderQuestion: return c.orderHint
    case .whereQuestion: return c.whereHint
    case .tellApartQuestion: return c.tellApartHint
    }
}

/// The Arabic of an ayah with a veil over a part of it: the first `visibleWords` words are always sharp, the rest is blurred until
/// `revealed`, when the veil melts in 320 ms (at once under Reduce Motion). Words in `accent` are drawn in the accent colour.
struct QuizVeiledText: View {
    @Environment(\.qhReduceMotion) private var reduced
    let ayah: QuranAyahDisplay
    let visibleWords: Int
    let revealed: Bool
    var accent: Set<Int> = []
    let hiddenLabel: String
    @State private var hidden: CGFloat

    init(ayah: QuranAyahDisplay, visibleWords: Int, revealed: Bool, accent: Set<Int> = [], hiddenLabel: String) {
        self.ayah = ayah; self.visibleWords = visibleWords; self.revealed = revealed; self.accent = accent; self.hiddenLabel = hiddenLabel
        _hidden = State(initialValue: revealed || visibleWords >= ayah.wordCount ? 0 : 1)
    }

    var body: some View {
        let count = ayah.wordCount
        let shown = min(max(visibleWords, 0), count)
        let goal: CGFloat = revealed || shown >= count ? 0 : 1
        QuranText(ayah: ayah, face: LessonArabic.face, readingSize: LessonArabic.lessonSize, accentWords: revealed ? accent : [],
                  veil: QuranVeil(hidden: hidden, shown: Set(0 ..< shown)))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(goal == 1 ? (shown > 0 ? ayah.wordTexts.prefix(shown).joined(separator: " ") + " " : "") + hiddenLabel : ayah.text)
            .onChange(of: goal) { _, next in
                if reduced { hidden = next } else { withAnimation(QhMotion.enter(0.32)) { hidden = next } }
            }
    }
}

struct QuizAyahCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View { QhCard(padding: QhSpacing.cardLarge, content: content) }
}

// MARK: - Continue, next, start

private struct SelfGradedPage: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: QuizModel
    let session: QuizSession
    let question: QuizQuestion

    var body: some View {
        let c = copy.quiz
        let revealed = session.revealed
        QuestionFrame(title: kindTitle(question, c), hint: kindHint(question, c)) {
            switch onEnum(of: question) {
            case let .continueQuestion(q):
                if let ayah = model.display(Int(q.surah), Int(q.ayah)) {
                    QuizAyahCard { QuizVeiledText(ayah: ayah, visibleWords: Int(q.shownWords), revealed: revealed, hiddenLabel: c.hiddenText) }
                    if revealed { whereLabel(Int(q.surah), Int(q.ayah)) }
                }
            case let .nextQuestion(q):
                if let shown = model.display(Int(q.surah), Int(q.ayah)) {
                    QuizAyahCard { QuizVeiledText(ayah: shown, visibleWords: .max, revealed: true, hiddenLabel: c.hiddenText) }
                }
                Text(c.whatNext).qhText({ $0.title2 }, color: { $0.inkSecondary })
                if let answer = model.display(Int(q.surah), Int(q.answerAyah)) {
                    QuizAyahCard { QuizVeiledText(ayah: answer, visibleWords: 0, revealed: revealed, hiddenLabel: c.hiddenText) }
                    if revealed { whereLabel(Int(q.surah), Int(q.answerAyah)) }
                }
            case let .startQuestion(q):
                QhCard(style: .hero, padding: QhSpacing.cardLarge) {
                    VStack(spacing: QhSpacing.x2) {
                        let arabic = model.arabicName(Int(q.surah))
                        if !arabic.isEmpty {
                            Text(arabic).font(.custom(LessonArabic.face.postScriptName, fixedSize: 38)).foregroundStyle(qh.colors.primary)
                                .environment(\.layoutDirection, .rightToLeft).accessibilityHidden(true)
                        }
                        Text(model.title(Int(q.surah), language: copy.language)).qhText({ $0.title1 }).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                }
                if let answer = model.display(Int(q.surah), 1) {
                    QuizAyahCard { QuizVeiledText(ayah: answer, visibleWords: 0, revealed: revealed, hiddenLabel: c.hiddenText) }
                }
            default: EmptyView()
            }
        } bottom: {
            if !revealed {
                QhButton(c.show, fullWidth: true) { withQhAnimation { model.vm.reveal() } }
            } else {
                HStack(spacing: QhSpacing.x3) {
                    QhButton(c.didNotKnow, variant: .tonal, fullWidth: true) { model.vm.answerSelf(knew: false) }
                    QhButton(c.knew, fullWidth: true) { QhHaptics.shared.perform(.segment); model.vm.answerSelf(knew: true) }
                }
            }
        }
    }

    private func whereLabel(_ surah: Int, _ ayah: Int) -> some View {
        Text(model.place(surah, ayah, language: copy.language)).qhText({ $0.callout }, color: { $0.inkSecondary })
    }
}

// MARK: - Put in order

/// "Correct" with a check, "Not quite" with a cross: shape and words, not only colour.
struct QuizVerdict: View {
    @Environment(\.qh) private var qh
    let right: Bool
    let copy: QuizCopy

    var body: some View {
        let tint = right ? qh.colors.learned : qh.colors.danger
        HStack(spacing: QhSpacing.x2) {
            Image(systemName: right ? "checkmark" : "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(tint)
            Text(right ? copy.right : copy.wrong).qhText({ $0.title2 }, color: { _ in tint })
        }
        .transition(.scale(scale: 0.9).combined(with: .opacity))
        .accessibilityElement(children: .combine)
    }
}

private enum Tone { case neutral, right, wrong }

private struct OrderPage: View {
    @Environment(Copy.self) private var copy
    let model: QuizModel
    let session: QuizSession
    let question: OrderQuestion

    var body: some View {
        let c = copy.quiz
        let answer = session.currentAnswer
        let last = session.position == session.total - 1
        let picks = session.picks.map(\.intValue)
        let tone: Tone = answer == nil ? .neutral : (answer!.knew ? .right : .wrong)
        QuestionFrame(title: kindTitle(question, c), hint: kindHint(question, c)) {
            if let answer { QuizVerdict(right: answer.knew, copy: c) }
            VStack(spacing: QhSpacing.x3) {
                ForEach(question.shuffled.map(\.intValue), id: \.self) { ayah in
                    OrderCard(ayah: model.display(Int(question.surah), ayah), number: ayah, place: (picks.firstIndex(of: ayah) ?? -1) + 1, tone: tone,
                              enabled: answer == nil) { QhHaptics.shared.perform(.segment); model.vm.pick(ayah: Int32(ayah)) }
                }
            }
            .animation(QhMotion.reduced ? nil : QhMotion.snappy, value: picks)
            if let answer, !answer.knew {
                Text(c.correctOrder).qhText({ $0.title2 }).accessibilityAddTraits(.isHeader)
                VStack(spacing: QhSpacing.x3) {
                    ForEach(Array(question.inOrder.map(\.intValue).enumerated()), id: \.offset) { index, ayah in
                        OrderCard(ayah: model.display(Int(question.surah), ayah), number: ayah, place: index + 1, tone: .right, enabled: false) {}
                    }
                }
            }
        } bottom: {
            if answer == nil {
                QhButton(c.check, fullWidth: true) { QhHaptics.shared.perform(.success); withQhAnimation { model.vm.checkOrder() } }
                    .disabled(picks.count != question.inOrder.count)
                QhButton(c.clearOrder, variant: .text, size: .m, fullWidth: true) { withQhAnimation { model.vm.clearPicks() } }
                    .disabled(picks.isEmpty)
            } else {
                QhButton(last ? c.seeResult : c.next, fullWidth: true) { model.vm.next() }
            }
        }
    }
}

/// An ayah of a "put in order" question: tap it to give it the next number, tap again to take the number back.
private struct OrderCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ayah: QuranAyahDisplay?
    let number: Int
    let place: Int
    let tone: Tone
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        let picked = place > 0
        let border: Color = {
            switch tone {
            case .right: return qh.colors.learned
            case .wrong: return qh.colors.danger.opacity(0.6)
            case .neutral: return picked ? qh.colors.primary : qh.colors.hairline
            }
        }()
        let card = HStack(alignment: .top, spacing: QhSpacing.x3) {
            PlaceBadge(place: place, tone: tone)
            if let ayah { QuranText(ayah: ayah, face: LessonArabic.face, readingSize: LessonArabic.lessonSize).frame(maxWidth: .infinity, alignment: .trailing) }
        }
        .padding(QhSpacing.card)
        .background(qh.colors.surface, in: qh.shapes.lgShape)
        .overlay { qh.shapes.lgShape.strokeBorder(border, lineWidth: picked || tone != .neutral ? 2 : 1) }
        let description = picked ? "\(copy.quiz.ayah(number: Int32(number))). \(copy.quiz.orderPlace(place: Int32(place)))" : copy.quiz.ayah(number: Int32(number))
        if enabled {
            card.pressable(haptic: nil, shape: qh.shapes.lgShape, highlight: false, action: action)
                .accessibilityElement(children: .ignore).accessibilityLabel(description).accessibilityAddTraits(.isButton)
        } else {
            card.accessibilityElement(children: .ignore).accessibilityLabel(description)
        }
    }
}

private struct PlaceBadge: View {
    @Environment(\.qh) private var qh
    let place: Int
    let tone: Tone

    var body: some View {
        let c = qh.colors
        let fill: Color = place == 0 ? .clear : (tone == .right ? c.learned : (tone == .wrong ? c.danger.opacity(0.85) : c.primary))
        let on: Color = tone == .right ? c.onLearned : (tone == .wrong ? c.onDanger : c.onPrimary)
        ZStack {
            Circle().fill(fill)
            if place == 0 { Circle().strokeBorder(c.inkTertiary, lineWidth: 1.5) }
            if place > 0 { Text("\(place)").qhText({ $0.title2 }, color: { _ in on }).qhTabular() }
        }
        .frame(width: 32, height: 32)
        .scaleEffect(place > 0 ? 1 : 0.94)
        .animation(QhMotion.reduced ? nil : QhMotion.bouncy, value: place)
    }
}

// MARK: - Where is this ayah

private struct WherePage: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: QuizModel
    let session: QuizSession
    let question: WhereQuestion

    var body: some View {
        let c = copy.quiz
        let answer = session.currentAnswer
        let last = session.position == session.total - 1
        QuestionFrame(title: kindTitle(question, c), hint: kindHint(question, c)) {
            if let ayah = model.display(Int(question.surah), Int(question.ayah)) {
                QuizAyahCard { QuizVeiledText(ayah: ayah, visibleWords: .max, revealed: true, hiddenLabel: c.hiddenText) }
            }
            if let answer { QuizVerdict(right: answer.knew, copy: c) }
            VStack(spacing: QhSpacing.x3) {
                ForEach(question.options.map(\.intValue), id: \.self) { surah in
                    OptionRow(name: model.title(surah, language: copy.language), arabic: model.arabicName(surah), state: state(surah, answer: answer)) {
                        QhHaptics.shared.perform(.segment)
                        withQhAnimation { model.vm.choose(surah: Int32(surah)) }
                    }
                }
            }
            if let answer, !answer.knew {
                Text(c.itIsSurah(name: model.title(Int(question.surah), language: copy.language))).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
        } bottom: {
            if answer != nil { QhButton(last ? c.seeResult : c.next, fullWidth: true) { model.vm.next() } }
        }
    }

    private func state(_ surah: Int, answer: QuizAnswer?) -> OptionState {
        if answer == nil { return .open }
        if surah == Int(question.surah) { return .correct }
        if surah == session.chosen?.intValue { return .wrong }
        return .dim
    }
}

private enum OptionState { case open, correct, wrong, dim }

/// One of the four surahs: after the choice the right one is marked (a check and a green frame), a wrong choice softly red.
private struct OptionRow: View {
    @Environment(Copy.self) private var copyEnv
    @Environment(\.qh) private var qh
    let name: String
    let arabic: String
    let state: OptionState
    let action: () -> Void

    var body: some View {
        let c = qh.colors
        let border: Color = state == .correct ? c.learned : (state == .wrong ? c.danger.opacity(0.6) : c.hairline)
        let background: Color = state == .correct ? c.learnedContainer : (state == .wrong ? c.danger.opacity(0.1) : c.surface)
        let row = HStack(spacing: QhSpacing.x3) {
            Text(name).qhText({ $0.title2 }).frame(maxWidth: .infinity, alignment: .leading)
            if !arabic.isEmpty {
                Text(arabic).font(.custom(QhQuranFace.amiri.postScriptName, fixedSize: 20)).foregroundStyle(c.inkSecondary)
                    .environment(\.layoutDirection, .rightToLeft).accessibilityHidden(true)
            }
            if state == .correct { Image(systemName: "checkmark").font(.system(size: 17, weight: .bold)).foregroundStyle(c.learned) }
            if state == .wrong { Image(systemName: "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(c.danger) }
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
        .frame(minHeight: QhSpacing.minTouch)
        .background(background, in: qh.shapes.lgShape)
        .overlay { qh.shapes.lgShape.strokeBorder(border, lineWidth: state == .correct || state == .wrong ? 2 : 1) }
        .opacity(state == .dim ? 0.6 : 1)
        if state == .open {
            row.pressable(haptic: nil, shape: qh.shapes.lgShape, highlight: false, action: action)
                .accessibilityElement(children: .ignore).accessibilityLabel(name).accessibilityAddTraits(.isButton)
        } else {
            row.accessibilityElement(children: .ignore)
                .accessibilityLabel("\(name)\(state == .correct ? ", " + copyEnv.quiz.right : "")")
        }
    }
}

// MARK: - Tell apart

/// Two similar places side by side, both shown only up to their common start and veiled after it. "Show" reveals both with the words that
/// differ in the accent colour; "Told apart" or "Mixed up" (which marks both ayahs difficult).
private struct TellApartPage: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: QuizModel
    let session: QuizSession
    let question: TellApartQuestion

    var body: some View {
        let c = copy.quiz
        let s = copy.similarKotlin
        let revealed = session.revealed
        let diff = revealed ? model.vm.tellApartDiff(question: question) : nil
        let places = [(Int(question.surah), Int(question.ayah), diff?.a), (Int(question.otherSurah), Int(question.otherAyah), diff?.b)]
        QuestionFrame(title: s.drillTitle, hint: s.drillHint) {
            ForEach(Array(places.enumerated()), id: \.offset) { index, place in
                QuizAyahCard {
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        Text(revealed ? model.place(place.0, place.1, language: copy.language) : s.place(number: Int32(index + 1)))
                            .qhText({ $0.callout }, color: { $0.inkSecondary })
                        if let ayah = model.display(place.0, place.1) {
                            let accent = Set((place.2?.differs ?? []).enumerated().filter { $0.element.boolValue }.map(\.offset))
                            QuizVeiledText(ayah: ayah, visibleWords: Int(question.shownWords), revealed: revealed, accent: accent, hiddenLabel: c.hiddenText)
                        }
                    }
                }
            }
            if revealed { Text(s.differencesNote).qhText({ $0.callout }, color: { $0.inkSecondary }) }
        } bottom: {
            if !revealed {
                QhButton(c.show, fullWidth: true) { withQhAnimation { model.vm.reveal() } }
            } else {
                HStack(spacing: QhSpacing.x3) {
                    QhButton(s.mixedUp, variant: .tonal, fullWidth: true) { model.vm.answerSelf(knew: false) }
                    QhButton(s.knewApart, fullWidth: true) { QhHaptics.shared.perform(.segment); model.vm.answerSelf(knew: true) }
                }
            }
        }
    }
}

// MARK: - Result

private struct QuizResultPage: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: QuizModel
    let state: QuizUiPlaying
    let tellApartMode: Bool
    @State private var shown = 0

    var body: some View {
        let c = copy.quiz
        let s = copy.similarKotlin
        let session = state.session
        let known = Int(session.known)
        let total = Int(session.total)
        let missed = session.answers.filter { !$0.knew }
        let marked = session.answers.contains { !$0.hardAyahs.isEmpty }
        ScrollView {
            VStack(spacing: QhSpacing.x4) {
                QhProgressRing(progress: total == 0 ? 1 : Double(known) / Double(total), size: 148, lineWidth: 12, color: qh.colors.learned) {
                    HStack(spacing: 2) {
                        AnimatedCounter(value: shown, style: { $0.headline })
                        Text(" / \(total)").qhText({ $0.title2 }, color: { $0.inkSecondary })
                    }
                }
                Text(c.result(known: Int32(known), total: Int32(total))).qhText({ $0.headline }).multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                if missed.isEmpty {
                    Text(c.allRight).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                } else {
                    Text(c.missedHeader).qhText({ $0.title2 }).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(Array(missed.enumerated()), id: \.offset) { _, answer in missedCard(answer.question) }
                    if marked { Text(c.markedNote).qhText({ $0.callout }, color: { $0.inkSecondary }).frame(maxWidth: .infinity, alignment: .leading) }
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: QhSpacing.x2) {
                QhButton(c.moreQuestions(questions: 5), fullWidth: true) { model.vm.again() }
                if !tellApartMode && state.pairs > 0 {
                    QhButton(s.entry(pairs: state.pairs), variant: .tonal, size: .m, icon: "arrow.left.arrow.right", fullWidth: true) {
                        router.replaceTop(with: .tellApart(surah: 0, ayah: 0))
                    }
                }
                QhButton(c.done, variant: .tonal, size: .m, fullWidth: true) { router.pop() }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x3)
            .background(qh.colors.bg.ignoresSafeArea(edges: .bottom))
        }
        .task {
            QhHaptics.shared.perform(.success)
            try? await Task.sleep(for: .milliseconds(240))
            withAnimation(QhMotion.reduced ? nil : QhMotion.gentle) { shown = known }
        }
    }

    private func missedCard(_ question: QuizQuestion) -> some View {
        let c = copy.quiz
        let title: String = {
            if let tell = question as? TellApartQuestion {
                return "\(model.place(Int(tell.surah), Int(tell.ayah), language: copy.language)) · \(model.place(Int(tell.otherSurah), Int(tell.otherAyah), language: copy.language))"
            }
            let range = missedRange(question)
            return HomeCopyKt.passageRangeLabel(surahName: model.title(Int(question.surah), language: copy.language), start: Int32(range.0), end: Int32(range.1))
        }()
        return QhCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).qhText({ $0.title2 })
                Text(kindTitle(question, c)).qhText({ $0.callout }, color: { $0.inkSecondary })
                QhButton(c.openInReader, variant: .text, size: .s) {
                    router.openReader(Int(question.surah), ayah: Int(question.focusAyah), origin: "quiz")
                }
                .padding(.top, QhSpacing.x2)
            }
        }
    }

    /// The ayahs of the answer of a question, for the line of the missed list: "Al-Mulk 12", "Al-Mulk 4-6".
    private func missedRange(_ q: QuizQuestion) -> (Int, Int) {
        switch onEnum(of: q) {
        case let .continueQuestion(x): return (Int(x.ayah), Int(x.ayah))
        case let .nextQuestion(x): return (Int(x.answerAyah), Int(x.answerAyah))
        case .startQuestion: return (1, 1)
        case let .orderQuestion(x): return (x.inOrder.first.map { $0.intValue } ?? 1, x.inOrder.last.map { $0.intValue } ?? 1)
        case let .whereQuestion(x): return (Int(x.ayah), Int(x.ayah))
        case let .tellApartQuestion(x): return (Int(x.ayah), Int(x.ayah))
        }
    }
}
