import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `LessonViewModel` of the lesson screen with its flows as observable values (docs/redesign/ios/BRIDGE.md).
/// Created by `LessonScreen` (`@State`); the view model lives and dies with the screen.
@MainActor
@Observable
final class LessonModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.lesson() }
    @ObservationIgnored private var events: FlowEvents<LessonEvent>?
    @ObservationIgnored private var progressTask: Task<Void, Never>?

    let ui: FlowState<LessonUi>
    let welcomeBack: FlowState<WelcomeBackUi?>
    let lighterWeekUntil: FlowState<KotlinInt?>
    let listenedToday: FlowState<Set<KotlinLong>>
    let translationOn: FlowState<KotlinBoolean>
    let translations: FlowState<[KotlinInt: String]>
    let translationFailedSurah: FlowState<KotlinInt?>
    let chapterNames: FlowState<[KotlinInt: ChapterNameModel]>
    let isSaving: FlowState<KotlinBoolean>
    let hintMode: FlowState<ComposeApp.HifzHintMode>
    let listeningCurrent: FlowState<KotlinBoolean>
    let audioState: FlowState<RecallAudioState>
    let listenProgress: FlowState<ListenProgress?>
    let hardCount: FlowState<KotlinInt>
    let streak: FlowState<KotlinInt>
    let forecast: FlowState<PlanForecast?>
    let lessonDurations: FlowState<[KotlinInt]>

    var vm: LessonViewModel { holder.vm }

    init() {
        let vm = holder.vm
        ui = FlowState(vm.ui)
        welcomeBack = FlowState(vm.welcomeBack)
        lighterWeekUntil = FlowState(vm.lighterWeekUntil)
        listenedToday = FlowState(vm.listenedToday)
        translationOn = FlowState(vm.translationOn)
        translations = FlowState(vm.translations)
        translationFailedSurah = FlowState(vm.translationFailedSurah)
        chapterNames = FlowState(vm.chapterNames)
        isSaving = FlowState(vm.isSaving)
        hintMode = FlowState(vm.hintMode)
        listeningCurrent = FlowState(vm.listeningCurrent)
        audioState = FlowState(vm.audioState)
        listenProgress = FlowState(vm.listenProgress)
        hardCount = FlowState(vm.hardCount)
        streak = FlowState(vm.streak)
        forecast = FlowState(vm.forecast)
        lessonDurations = FlowState(vm.lessonDurations)
        vm.onLanguage(language: IosUi.shared.language.value)
    }

    func bind(toaster: Toaster, copy: Copy) {
        guard events == nil else { return }
        events = FlowEvents(vm.eventFlow) { event in
            switch onEnum(of: event) {
            case .saveFailed: toaster.show(copy.lessonCopy.saveFailed, kind: .error)
            }
        }
    }

    /// The position inside the card survives a relaunch as text (`progressText` -> `restore`), kept by `FlowState`-less task.
    func startProgressSaving(save: @escaping (String) -> Void) {
        guard progressTask == nil else { return }
        let flow = vm.progressText
        progressTask = Task {
            do { for try await text in flow { save(text) } } catch {}
        }
    }

    func restore(_ text: String) { if !text.isEmpty { vm.restore(text: text) } }

    func surahTitle(_ number: Int, language: AppLanguage) -> String {
        vm.surahTitle(number: Int32(number), language: language, names: chapterNames.value)
    }

    func surahArabic(_ number: Int) -> String { vm.surahArabicName(number: Int32(number), names: chapterNames.value) }

    @ObservationIgnored private var displayCache: [String: [QuranAyahDisplay]] = [:]

    /// The display models of the card's ayahs, built once per card (the Bismillah cut, the words split).
    func displays(for work: LessonUiWorking) -> [QuranAyahDisplay] {
        let key = "\(work.card.key)/\(work.ayahs.count)"
        if let hit = displayCache[key] { return hit }
        let made = work.ayahs.map(\.display)
        displayCache[key] = made
        return made
    }

    func name(_ number: Int, copy: Copy) -> String { surahTitle(number, language: copy.language) }

    deinit {}
}
