import SwiftUI
import UIKit

/// Epoch days (the calendar of the Kotlin layer: whole days since 1970-01-01 of the local calendar date) and `Date`, for the date pickers
/// of the plan, the khatm and the report. A day is shown at local noon so no time zone shifts it.
enum EpochDay {
    private static var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        return calendar
    }

    static func date(_ epochDay: Int) -> Date {
        let parts = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400))
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        var noon = parts
        noon.hour = 12
        return local.date(from: noon) ?? Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400)
    }

    static func day(_ date: Date) -> Int {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        let parts = local.dateComponents([.year, .month, .day], from: date)
        let midnight = utc.date(from: parts) ?? date
        return Int((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }
}

/// The date picker of "finish by" (plan and khatm): a sheet with the graphical calendar; only days after today can be chosen.
struct TargetDateSheet: View {
    @Environment(\.qh) private var qh
    let title: String
    let initialDay: Int
    let today: Int
    let okLabel: String
    let cancelLabel: String
    let onPick: (Int) -> Void
    let onCancel: () -> Void
    @State private var date: Date

    init(title: String, initialDay: Int, today: Int, okLabel: String, cancelLabel: String,
         onPick: @escaping (Int) -> Void, onCancel: @escaping () -> Void) {
        self.title = title; self.initialDay = initialDay; self.today = today
        self.okLabel = okLabel; self.cancelLabel = cancelLabel; self.onPick = onPick; self.onCancel = onCancel
        _date = State(initialValue: EpochDay.date(max(initialDay, today + 1)))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                DatePicker("", selection: $date, in: EpochDay.date(today + 1)..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .labelsHidden()
                    .tint(qh.colors.primary)
                    .padding(.horizontal, QhSpacing.x3)
            }
            .background(qh.colors.bg)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(cancelLabel, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) { Button(okLabel) { onPick(EpochDay.day(date)) } }
            }
        }
        .presentationDetents([.large])
        .presentationCornerRadius(28)
    }
}

/// Turns the swipe-back gesture of the navigation stack off while [enabled] is false (a screen that asks before it is left).
struct SwipeBackGuard: UIViewControllerRepresentable {
    let enabled: Bool

    func makeUIViewController(context: Context) -> Probe { Probe() }

    func updateUIViewController(_ controller: Probe, context: Context) {
        controller.enabled = enabled
        controller.apply()
    }

    final class Probe: UIViewController {
        var enabled = true
        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            apply()
        }
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            apply()
        }
        func apply() {
            guard let nav = navigationController, let gesture = nav.interactivePopGestureRecognizer else { return }
            gesture.delegate = nil
            gesture.isEnabled = enabled
        }
    }
}
