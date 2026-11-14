import ComposeApp
import SwiftUI
import UserNotifications

/// "Remind you about tomorrow's lesson?" on the summary of a finished lesson (`LessonReminderAsk.kt`), instead of asking during the
/// onboarding. It shows while the reminder is off and the question has never been answered (`ReminderAsk.shouldShow`).
///
/// - "Remind me" asks for the notification permission, then turns the reminder on at the chosen time through the Kotlin
///   `ReminderController` (the same path as the Settings switch, so the notifications are planned). A refusal replaces the card by the
///   note the settings use for it.
/// - "Not now" closes the card for good; Settings stay the only place to turn the reminder on later.
/// - The time chip starts at the current time rounded to the half hour; "Other" opens a time picker.
struct ReminderAskCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var enabled = FlowState(IosUi.shared.uiPrefs.reminderEnabled)
    @State private var asked = FlowState(IosUi.shared.uiPrefs.reminderAsked)
    @State private var minuteOfDay: Int
    @State private var denied = false
    @State private var shownLogged = false
    @State private var picking = false

    init() {
        let now = Calendar.current.dateComponents([.hour, .minute], from: Date())
        _minuteOfDay = State(initialValue: Int(ReminderAsk.shared.proposedMinute(minuteOfDay: Int32((now.hour ?? 20) * 60 + (now.minute ?? 0)))))
    }

    private var is24Hour: Bool {
        !(DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: Locale.current) ?? "").contains("a")
    }

    var body: some View {
        let extras = copy.summaryExtrasCopy
        let visible = ReminderAsk.shared.shouldShow(lessonComplete: true, reminderEnabled: enabled.value.boolValue, alreadyAnswered: asked.value.boolValue)
        Group {
            if denied {
                Text(copy.reminderCopy.deniedToast).qhText({ $0.callout }, color: { $0.inkSecondary })
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    .transition(.opacity)
            } else if visible {
                QhCard(padding: QhSpacing.cardLarge) {
                    Text(extras.askTitle).qhText({ $0.title2 })
                    HStack(spacing: QhSpacing.x2) {
                        QhChip(label: ReminderControllerKt.formatReminderTime(minuteOfDay: Int32(minuteOfDay), is24Hour: is24Hour), selected: true) {}
                        QhChip(label: extras.askOtherTime, selected: false) { picking = true }
                    }
                    .padding(.top, QhSpacing.x3)
                    HStack(spacing: QhSpacing.x2) {
                        QhButton(extras.askYes, size: .m, fullWidth: true) { Task { await accept() } }
                        QhButton(extras.askNo, variant: .text, size: .m, fullWidth: true) {
                            IosUi.shared.uiPrefs.setReminderAsked(value: true)
                            AppTelemetry.shared.logEvent(name: "reminder_ask_answer", params: ["answer": "no"])
                        }
                    }
                    .padding(.top, QhSpacing.x2)
                }
                .transition(.opacity.combined(with: .scale(scale: 0.97)))
                .onAppear {
                    guard !shownLogged else { return }
                    shownLogged = true
                    AppTelemetry.shared.logEvent(name: "reminder_ask_shown", params: [:])
                }
            }
        }
        .qhAnimation(QhMotion.gentle, value: visible)
        .qhAnimation(QhMotion.gentle, value: denied)
        .qhSheet(isPresented: $picking, detents: [.height(360)]) {
            ReminderTimePicker(minuteOfDay: $minuteOfDay, title: copy.reminderCopy.timeTitle, done: copy.lessonCopy.next) { picking = false }
        }
    }

    private func accept() async {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        IosUi.shared.uiPrefs.setReminderAsked(value: true)
        if granted {
            let controller = IosLessonBridge.shared.reminderController()
            controller.setMinuteOfDay(minuteOfDay: Int32(minuteOfDay), track: false)
            controller.setEnabled(enabled: true, track: true)
            AppTelemetry.shared.logEvent(name: "reminder_ask_answer", params: ["answer": "yes"])
        } else {
            denied = true
            AppTelemetry.shared.logEvent(name: "reminder_ask_answer", params: ["answer": "denied"])
        }
    }
}

private struct ReminderTimePicker: View {
    @Binding var minuteOfDay: Int
    let title: String
    let done: String
    let close: () -> Void

    var body: some View {
        let date = Binding<Date>(
            get: { Calendar.current.date(bySettingHour: minuteOfDay / 60, minute: minuteOfDay % 60, second: 0, of: Date()) ?? Date() },
            set: { let c = Calendar.current.dateComponents([.hour, .minute], from: $0); minuteOfDay = (c.hour ?? 0) * 60 + (c.minute ?? 0) }
        )
        VStack(spacing: QhSpacing.x3) {
            QhSheetHeader(title: title)
            DatePicker("", selection: date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel).labelsHidden()
            QhButton(done, fullWidth: true, action: close)
        }
    }
}
