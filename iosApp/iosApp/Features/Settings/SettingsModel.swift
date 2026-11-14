import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `SettingsViewModel` with its flows as observable values, and the pieces of the settings screen that live in Swift:
/// the reading font and size (stored through `IosTransferApply`), the reminder (switch + time) and the backup events.
@MainActor
@Observable
final class SettingsModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.settings() }
    @ObservationIgnored private var events: FlowEvents<SettingsEvent>?
    @ObservationIgnored private var pendingTask: FlowEvents<BackupSettings?>?

    let reader: FlowState<SurahReaderPreferences>
    let keepScreenOn: FlowState<KotlinBoolean>
    let haptics: FlowState<KotlinBoolean>
    let reduceMotion: FlowState<KotlinBoolean>
    let analytics: FlowState<KotlinBoolean>
    let lastExportAt: FlowState<KotlinLong?>
    let importPrompt: FlowState<ImportPrompt?>
    let busy: FlowState<KotlinBoolean>
    let reciter: FlowState<Reciter>
    let reminderEnabled: FlowState<KotlinBoolean>
    let reminderMinute: FlowState<KotlinInt>

    /// The reading font (`ReadingFontStyle.storageValue`) and size, not observable in Kotlin: kept here and written through.
    var fontStorage: String = IosTransferApply.shared.readingFontStorageValue()
    var fontSize: Int = Int(IosTransferApply.shared.readingFontSize())

    /// Notification permission is denied in the system while the reminder is on (the "blocked" note).
    var notificationsBlocked = false

    var vm: SettingsViewModel { holder.vm }

    init() {
        let vm = holder.vm
        let prefs = IosUi.shared.uiPrefs
        reader = FlowState(vm.readerPreferences)
        keepScreenOn = FlowState(vm.keepScreenOn)
        haptics = FlowState(vm.hapticsEnabled)
        reduceMotion = FlowState(vm.reduceMotion)
        analytics = FlowState(vm.analyticsEnabled)
        lastExportAt = FlowState(vm.lastExportAt)
        importPrompt = FlowState(vm.importPrompt)
        busy = FlowState(vm.transferBusy)
        reciter = FlowState(prefs.reciter)
        reminderEnabled = FlowState(prefs.reminderEnabled)
        reminderMinute = FlowState(prefs.reminderMinuteOfDay)
    }

    /// Toasts for the backup events, the share sheet for "Send a copy", and the settings of an imported file. Call once from the screen.
    func bind(env: AppEnvironment, onShare: @escaping (String, String) -> Void) {
        guard events == nil else { return }
        let toaster = env.toaster
        let copy = env.copy
        events = FlowEvents(vm.events) { event in
            let transfer = copy.s7Settings.transfer
            switch onEnum(of: event) {
            case .exportSaved: toaster.show(transfer.exportSaved, kind: .success)
            case .exportFailed: toaster.show(transfer.exportFailed, kind: .error)
            case let .shareReady(ready): onShare(ready.location, ready.fileName)
            case let .importRejected(rejected):
                let text: String
                switch rejected.problem.ordinal {
                case 0: text = transfer.importNotBackup
                case 1: text = transfer.importNewer
                case 2: text = transfer.importCorrupt
                case 3: text = transfer.importOutOfRange
                default: text = transfer.importUnreadable
                }
                toaster.show(text, kind: .error)
            case .importDone: toaster.show(transfer.importDone, kind: .success)
            case .importFailed: toaster.show(transfer.importFailed, kind: .error)
            }
        }
        pendingTask = FlowEvents(vm.pendingSettings) { [weak self] pending in
            guard let self, let pending else { return }
            self.applyImported(pending, env: env)
        }
    }

    private func applyImported(_ settings: BackupSettings, env: AppEnvironment) {
        IosTransferApply.shared.apply(
            settings: settings, viewModel: vm,
            setLanguage: { env.setLanguage($0) },
            setThemeMode: { mode in env.setThemeMode(mode == .dark ? .dark : .light) },
            setPalette: { palette in
                switch palette {
                case .sand: env.setPalette(.sand)
                case .ocean: env.setPalette(.ocean)
                default: env.setPalette(.forest)
                }
            }
        )
        // "System" comes last in the file's order; the reveal above already ran for light / dark.
        if settings.themeFollowsSystem?.boolValue == true { env.setThemeMode(.system, reveal: false) }
        fontStorage = IosTransferApply.shared.readingFontStorageValue()
        fontSize = Int(IosTransferApply.shared.readingFontSize())
        vm.consumePendingSettings()
    }

    // MARK: Reading font

    func setFont(_ storage: String) {
        fontStorage = storage
        IosTransferApply.shared.setReadingFont(storageValue: storage)
    }

    func setFontSize(_ size: Int) {
        fontSize = size
        IosTransferApply.shared.setReadingFontSize(value: Int32(size))
    }

    // MARK: Reader preferences

    func setShowTranslation(_ on: Bool) { IosTransferApply.shared.setShowTranslation(viewModel: vm, value: on) }
    func setHighlight(_ on: Bool) { IosTransferApply.shared.setHighlightPlayingAyah(viewModel: vm, value: on) }
    func setAutoplay(_ on: Bool) { IosTransferApply.shared.setAutoplayOnOpen(viewModel: vm, value: on) }

    // MARK: Reminder

    /// Turns the reminder on after the notification permission is granted (asked now); returns false (switch stays off) on refusal.
    func setReminder(_ on: Bool) async -> Bool {
        if !on {
            vm.setReminderEnabled(value: false)
            return true
        }
        let granted = await NotificationPermission.request()
        notificationsBlocked = !granted
        if granted { vm.setReminderEnabled(value: true) }
        return granted
    }

    func setReminderMinute(_ minute: Int) { vm.setReminderMinuteOfDay(value: Int32(minute)) }

    func refreshNotificationStatus() async {
        let denied = await NotificationPermission.status() == .denied
        notificationsBlocked = reminderEnabled.value.boolValue && denied
    }
}
