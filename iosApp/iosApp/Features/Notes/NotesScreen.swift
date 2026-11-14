import ComposeApp
import Observation
import SwiftUI

@MainActor
@Observable
final class NotesModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.notes() }
    let ui: FlowState<NotesUi>
    var vm: NotesViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        holder.vm.setLanguage(value: IosUi.shared.language.value)
    }
}

/// "My notes" (`Dest.Notes`, F5a): every note of an ayah, newest first, with a search over the text. A tap opens the reader at the ayah
/// (through the entry point that knows the mode "Pages"), a swipe deletes the note and offers "Undo" for five seconds.
struct NotesScreen: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @State private var model = NotesModel()
    @State private var query = ""
    @State private var undo: NoteItem?

    var body: some View {
        let t = copy.analytics
        let ui = model.ui.value
        ZStack(alignment: .bottom) {
            Group {
                if !ui.ready {
                    VStack { Skeleton(shape: qh.shapes.lgShape).frame(height: 96).padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x2); Spacer() }
                } else if ui.total == 0 {
                    QhEmptyState(title: t.notesEmptyTitle, message: t.notesEmptyBody).frame(maxHeight: .infinity)
                } else {
                    list(ui, t)
                }
            }
            if let undo {
                undoBar(undo, t)
                    .padding(.horizontal, QhSpacing.screen).padding(.bottom, QhSpacing.x4)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: undo.key) {
                        try? await Task.sleep(nanoseconds: 5_000_000_000)
                        withQhAnimation(QhMotion.snappy) { self.undo = nil }
                    }
            }
        }
        .background(qh.colors.bg)
        .navigationTitle(t.notesTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .toolbar(.hidden, for: .tabBar)
        .onChange(of: copy.language) { _, l in model.vm.setLanguage(value: l) }
    }

    private func list(_ ui: NotesUi, _ t: AnalyticsCopy) -> some View {
        List {
            Section {
                QhSearchField(text: $query, placeholder: t.notesSearch, clearLabel: t.notesSearchClear)
                    .listRowInsets(EdgeInsets(top: 0, leading: QhSpacing.screen, bottom: QhSpacing.x2, trailing: QhSpacing.screen))
                    .listRowBackground(Color.clear).listRowSeparator(.hidden)
                    .onChange(of: query) { _, q in model.vm.setQuery(value: q) }
                if ui.items.isEmpty {
                    QhEmptyState(title: t.notesNoMatchTitle, message: t.notesNoMatchBody)
                        .listRowBackground(Color.clear).listRowSeparator(.hidden)
                }
                ForEach(ui.items, id: \.key) { note in
                    row(note, ui, t)
                        .listRowInsets(EdgeInsets(top: QhSpacing.x1, leading: QhSpacing.screen, bottom: QhSpacing.x1, trailing: QhSpacing.screen))
                        .listRowBackground(Color.clear).listRowSeparator(.hidden)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) { delete(note) } label: { Label(t.notesDeleteDescription, systemImage: "trash") }
                        }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, 80, for: .scrollContent)
    }

    private func row(_ note: NoteItem, _ ui: NotesUi, _ t: AnalyticsCopy) -> some View {
        let title = t.notesAyah(name: ui.nameOf(surah: note.surah), ayah: note.ayah)
        let date = dateLabel(note.updatedAtMillis)
        return QhCard(onTap: { router.openReader(Int(note.surah), ayah: Int(note.ayah), origin: "search") }) {
            HStack {
                Text(title).qhText({ $0.title2 }).lineLimit(1)
                Spacer(minLength: QhSpacing.x2)
                Text(date).qhText({ $0.label }, color: { $0.inkSecondary })
            }
            Text(note.text).qhText({ $0.body }, color: { $0.inkSecondary }).lineLimit(2).padding(.top, QhSpacing.x1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(note.text). \(date)")
        .accessibilityAction(named: Text(t.notesDeleteDescription)) { delete(note) }
    }

    private func delete(_ note: NoteItem) {
        QhHaptics.shared.perform(.longPress)
        model.vm.delete(item: note)
        withQhAnimation(QhMotion.snappy) { undo = note }
    }

    private func undoBar(_ note: NoteItem, _ t: AnalyticsCopy) -> some View {
        HStack {
            Text(t.notesDeleted).qhText({ $0.callout }, color: { $0.bg }).padding(.vertical, QhSpacing.x3)
            Spacer(minLength: 0)
            Button {
                QhHaptics.shared.perform(.tick)
                model.vm.restore(item: note)
                withQhAnimation(QhMotion.snappy) { undo = nil }
            } label: {
                Text(t.notesUndo).qhText({ $0.label }, color: { $0.gold }).padding(.horizontal, QhSpacing.x3).frame(minHeight: QhSpacing.minTouch)
            }
        }
        .padding(.leading, QhSpacing.x4).padding(.trailing, QhSpacing.x2)
        .background(qh.colors.ink, in: RoundedRectangle(cornerRadius: QhSpacing.x4, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func dateLabel(_ millis: Int64) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: copy.language.name.lowercased())
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        formatter.doesRelativeDateFormatting = true
        return formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }
}
