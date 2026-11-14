import ComposeApp
import SwiftUI
import UserNotifications

/// The "Reminder" section of Settings (Android `ReminderSection`): a switch, the time of the day, a note under it, and a notice when
/// the reminder is on but notifications are off in the system. Turning it on asks the notification permission first; a refusal puts
/// the switch back and says why.
struct ReminderSection: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.toaster) private var toaster
    @Environment(\.scenePhase) private var scenePhase
    let model: SettingsModel
    @State private var showTime = false

    var body: some View {
        let r = copy.reminderCopy
        let on = model.reminderEnabled.value.boolValue
        SettingsSection(title: r.sectionTitle, footnote: r.footnote) {
            QhSwitchRow(title: r.toggleTitle, isOn: enabledBinding(r), icon: "bell", iconTint: qh.colors.primary)
            if on {
                QhRowDivider()
                QhValueRow(title: r.timeTitle, value: Self.timeText(Int(model.reminderMinute.value.int32Value)),
                           icon: "clock", iconTint: qh.colors.gold) { showTime = true }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                QhRowDivider()
                ReminderDaysRow(model: model)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
            if on && model.notificationsBlocked {
                QhRowDivider()
                VStack(alignment: .leading, spacing: QhSpacing.x2) {
                    Text(r.blockedTitle).qhText({ $0.title2 }, color: { $0.danger })
                    Text(r.blockedBody).qhText({ $0.callout }, color: { $0.inkSecondary })
                    QhButton(r.openSettings, variant: .tonal, size: .s) {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
                .padding(QhSpacing.card)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .combine)
            }
        }
        .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: on)
        .sheet(isPresented: $showTime) { ReminderTimeSheet(model: model) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await model.refreshNotificationStatus() } }
        }
    }

    private func enabledBinding(_ r: ReminderCopy) -> Binding<Bool> {
        Binding(
            get: { model.reminderEnabled.value.boolValue },
            set: { on in
                Task {
                    let ok = await model.setReminder(on)
                    if !ok { toaster.show(r.deniedToast, kind: .error) }
                }
            }
        )
    }

    /// "20:00" or "8:00 PM" the way the phone writes times.
    static func timeText(_ minuteOfDay: Int) -> String {
        date(minuteOfDay).formatted(date: .omitted, time: .shortened)
    }

    static func date(_ minuteOfDay: Int) -> Date {
        let minute = min(max(minuteOfDay, 0), 24 * 60 - 1)
        return Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
    }
}

/// The weekdays of the reminder as seven round chips, Monday first (the order of the bits of `UiPrefs.reminderDays`).
private struct ReminderDaysRow: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: SettingsModel

    var body: some View {
        let r = copy.reminderCopy
        let mask = Int(model.reminderDays.value.int32Value)
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            Text(r.daysTitle).qhText({ $0.body }, color: { $0.ink })
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    let on = mask & (1 << i) != 0
                    Button { model.toggleReminderDay(i) } label: {
                        Text(r.weekdays[i])
                            .font(.system(size: 13, weight: .semibold))
                            .minimumScaleFactor(0.7).lineLimit(1)
                            .foregroundStyle(on ? qh.colors.onPrimary : qh.colors.inkSecondary)
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .background(Capsule().fill(on ? qh.colors.primary : qh.colors.primary.opacity(0.1)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(r.weekdays[i])
                    .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                }
            }
        }
        .padding(QhSpacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The time of the reminder: a wheel in a small sheet; "Done" keeps it.
struct ReminderTimeSheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    let model: SettingsModel
    @State private var time = Date()

    var body: some View {
        let r = copy.reminderCopy
        VStack(spacing: QhSpacing.x3) {
            QhSheetHeader(title: r.timeDialogTitle)
            DatePicker("", selection: $time, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
            QhButton(r.done, fullWidth: true) {
                let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
                model.setReminderMinute((parts.hour ?? 20) * 60 + (parts.minute ?? 0))
                dismiss()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x4)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.height(380)])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(qh.colors.surface)
        .onAppear { time = ReminderSection.date(Int(model.reminderMinute.value.int32Value)) }
    }
}
