import SwiftUI
import WidgetKit

struct LessonEntry: TimelineEntry {
    let date: Date
    let snapshot: LessonSnapshot
}

/// One entry now and the next one at midnight (the day changes what the lesson is); the app reloads the timeline whenever the lesson,
/// the plan, the language or the palette changes.
struct LessonProvider: TimelineProvider {
    func placeholder(in context: Context) -> LessonEntry { LessonEntry(date: .now, snapshot: .sample) }

    func getSnapshot(in context: Context, completion: @escaping (LessonEntry) -> Void) {
        completion(LessonEntry(date: .now, snapshot: context.isPreview ? .sample : (LessonSnapshot.load() ?? .sample)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LessonEntry>) -> Void) {
        let snapshot = LessonSnapshot.load() ?? .empty
        let midnight = Calendar.current.nextDate(after: .now, matching: DateComponents(hour: 0, minute: 5), matchingPolicy: .nextTime)
            ?? .now.addingTimeInterval(3600 * 6)
        completion(Timeline(entries: [LessonEntry(date: .now, snapshot: snapshot)], policy: .after(midnight)))
    }
}

struct LessonWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    let entry: LessonEntry

    var body: some View {
        LessonWidgetView(snapshot: entry.snapshot, family: family, dark: scheme == .dark)
            .containerBackground(for: .widget) {
                let palette = WidgetPalette.of(entry.snapshot.palette, dark: scheme == .dark)
                if family == .accessoryRectangular || family == .accessoryCircular { Color.clear } else { palette.surface }
            }
            .widgetURL(entry.snapshot.opensLesson ? URL(string: "quranhifz://lesson") : nil)
    }
}

struct LessonWidget: Widget {
    let kind = "LessonWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LessonProvider()) { entry in
            LessonWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Lesson of the day")
        .description("Today's lesson and your streak.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
        .contentMarginsDisabled()
    }
}

@main
struct QuranHifzWidgetBundle: WidgetBundle {
    var body: some Widget { LessonWidget() }
}

#Preview("Small", as: .systemSmall) { LessonWidget() } timeline: { LessonEntry(date: .now, snapshot: .sample) }
#Preview("Medium", as: .systemMedium) { LessonWidget() } timeline: { LessonEntry(date: .now, snapshot: .sample) }
#Preview("Lock", as: .accessoryRectangular) { LessonWidget() } timeline: { LessonEntry(date: .now, snapshot: .sample) }
