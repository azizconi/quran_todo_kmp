#if DEBUG
import ComposeApp
import SwiftUI
import UIKit

/// A test harness for the platform services of IOS-S2 (not a screen of the app): every row runs one service and logs the result.
struct ServicesDebugView: View {
    @State private var log: [String] = []
    @State private var pageFontName: String?
    private let auto = ProcessInfo.processInfo.arguments.contains("-servicesAutoTest")

    var body: some View {
        NavigationStack {
            List {
                Section("Reminders") {
                    button("Test reminder in 60 s") { await reminder() }
                    button("Plan 14 days now") { IosServicesRuntime.shared.onForeground(); add("planned (see pending)") }
                    button("Pending notifications") { await pending() }
                }
                Section("Recording") {
                    button("Record 5 s, then play") { await recordAndPlay() }
                }
                Section("Transfer and report") {
                    button("Write + read a backup file") { await transfer() }
                    button("Render report PDF and share") { await reportPdf() }
                    button("Write widget snapshot") { await widget() }
                }
                Section("Downloads") {
                    button("Download surah 108 audio") { await download() }
                    button("Downloaded files") { downloaded() }
                    button("Delete all downloads") { add("freed \(IosServicesDebug.shared.downloads().deleteAll()) bytes") }
                }
                Section("Mushaf page") {
                    button("Load page 1 and its font") { await mushaf(1) }
                    if let pageFontName {
                        Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ")
                            .font(.custom(pageFontName, size: 28))
                    }
                }
                Section("Log") {
                    ForEach(Array(log.enumerated()), id: \.offset) { _, line in
                        Text(line).font(.footnote.monospaced())
                    }
                }
            }
            .navigationTitle("Services")
        }
        .task {
            // `-servicesAutoTest` runs every check once and writes the log to Documents/services_debug.log (for the simulator runs).
            if ProcessInfo.processInfo.arguments.contains("-servicesReminderOnly") {
                await reminder()
                for _ in 0..<6 { await pending(); try? await Task.sleep(nanoseconds: 20_000_000_000) }
                add("DONE")
                return
            }
            if ProcessInfo.processInfo.arguments.contains("-servicesPlanOnly") {
                let prefs = IosUi.shared.uiPrefs
                let now = Calendar.current.dateComponents([.hour, .minute], from: Date().addingTimeInterval(120))
                prefs.setReminderMinuteOfDay(value: Int32((now.hour ?? 0) * 60 + (now.minute ?? 0)))
                prefs.setReminderEnabled(value: true)
                IosServicesRuntime.shared.onForeground()
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                await pending()
                for _ in 0..<5 { try? await Task.sleep(nanoseconds: 30_000_000_000); await pending() }
                add("DONE")
                return
            }
            guard auto else { return }
            await reminder()
            await pending()
            await widget()
            await transfer()
            await reportPdf()
            await mushaf(1)
            await download()
            await recordAndPlay()
            add("DONE")
        }
    }

    private func button(_ title: String, _ action: @escaping () async -> Void) -> some View {
        Button(title) { Task { await action() } }
    }

    private func add(_ line: String) {
        DispatchQueue.main.async {
            log.insert(line, at: 0)
            let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("services_debug.log")
            try? log.reversed().joined(separator: "\n").write(to: url, atomically: true, encoding: .utf8)
        }
    }

    private func reminder() async {
        let granted = await NotificationPermission.request()
        add("notifications permission: \(granted)")
        IosServicesRuntime.shared.scheduleTestReminder(seconds: 60) { ok in add("test reminder scheduled: \(ok.boolValue)") }
    }

    private func pending() async {
        let requests = await UNUserNotificationCenter.current().pendingNotificationRequests()
        let delivered = await UNUserNotificationCenter.current().deliveredNotifications()
        add("pending: \(requests.count), delivered: \(delivered.count) \(delivered.first?.request.content.title ?? "")")
        for request in requests.sorted(by: { $0.identifier < $1.identifier }).prefix(3) {
            add("  \(request.identifier): \(request.content.title) | \(request.content.body)")
        }
    }

    private func recordAndPlay() async {
        let voice = IosServicesDebug.shared.voice()
        let key = PassageKey(surah: 1, startAyah: 1, endAyah: 7)
        // A sample in Documents (put there by the test script) is played instead of recording: the simulator may have no microphone.
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let sample = docs.appendingPathComponent("sample.m4a")
        if FileManager.default.fileExists(atPath: sample.path) {
            let folder = (NSSearchPathForDirectoriesInDomains(.applicationSupportDirectory, .userDomainMask, true)[0] as NSString).appendingPathComponent("recordings/1_1_7")
            try? FileManager.default.createDirectory(atPath: folder, withIntermediateDirectories: true)
            let target = (folder as NSString).appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000)).m4a")
            try? FileManager.default.copyItem(atPath: sample.path, toPath: target)
            for await n in voice.count(key: key) { add("recordings of the passage: \(n)"); break }
            for await notes in voice.notes(key: key) { add("notes: \(notes.count), first duration \(notes.first?.durationMs ?? 0) ms"); break }
            voice.toggle(path: target)
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            add("playing: \(voice.player.value.playing) pos \(voice.player.value.positionMs) of \(voice.player.value.durationMs)")
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            add("after: playing \(voice.player.value.playing) pos \(voice.player.value.positionMs)")
            return
        }
        guard await MicrophonePermission.request() else { add("microphone denied"); return }
        add("start: \(voice.start(key: key))")
        try? await Task.sleep(nanoseconds: 5_000_000_000)
        if let recording = voice.stop() {
            add("recorded \(recording.sizeBytes) bytes at \(recording.path)")
            voice.toggle(path: recording.path)
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            add("playing: \(voice.player.value.playing) pos \(voice.player.value.positionMs) of \(voice.player.value.durationMs)")
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            add("after: playing \(voice.player.value.playing)")
        } else {
            add("nothing recorded")
        }
    }

    private func transfer() async {
        let files = IosServicesDebug.shared.transfer()
        do {
            let location = try await files.createShareFile(fileName: "debug-backup.json", text: "{\"format\":1,\"ayah\":\"бисмиллах\"}")
            add("export: \(location)")
            let outcome = try await files.readText(location: location)
            add("import: \(outcome)")
            if !auto, let url = TransferAccess.url(of: location) { await MainActor.run { ShareSheet.present(items: [url]) } }
        } catch {
            add("transfer failed: \(error)")
        }
    }

    private func reportPdf() async {
        let doc = ReportDoc(
            title: "Report for the teacher", period: "24 Oct – 30 Oct", percentText: "7%", fraction: 0.07,
            caption: "Memorized in total: 412 of 6236 ayahs",
            rows: [
                ReportRow(label: "New", value: "12 ayahs", lines: ["An-Naba 1-5", "Al-Masad 1-5"], bulleted: true),
                ReportRow(label: "Reviewed", value: "40 ayahs · 3 repetitions", lines: ["Surahs: Al-Fatiha, An-Nas"], bulleted: false),
                ReportRow(label: "Active days", value: "5/7", lines: [], bulleted: false),
            ],
            strip: [0, 2, 5, 3, 0, 1, 4].map { KotlinInt(int: $0) }, stripLabel: "Days of the period"
        )
        do {
            let location = try await IosServicesDebug.shared.reports().createPdf(doc: doc, fileName: "debug-report.pdf")
            add("pdf: \(location ?? "nil")")
            if let location, let url = URL(string: location) {
                let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
                add("pdf bytes: \(size)")
                if !auto { await MainActor.run { ShareSheet.present(items: [url]) } }
            }
        } catch {
            add("pdf failed: \(error)")
        }
    }

    private func widget() async {
        IosServicesRuntime.shared.writeWidgetSnapshot { path in add("widget snapshot: \(path ?? "nil")") }
    }

    private func download() async {
        let service = IosServicesDebug.shared.downloads()
        let request = IosServicesDebug.shared.surahRequest(surah: 108)
        service.prefs.setWifiOnly(value: false)
        add("enqueue: \(service.enqueue(request: request, estimatedBytes: 100_000))")
        for _ in 0..<40 {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            let files = service.storage.snapshot()
            let state = service.queue.state.value
            add("files \(files.count), queue \(state.requests.count), failed \(state.failures.count)")
            if state.requests.isEmpty { break }
        }
    }

    private func downloaded() {
        let files = IosServicesDebug.shared.downloads().storage.snapshot()
        add("downloaded: \(files.count) files, \(files.values.reduce(0) { $0 + $1.int64Value }) bytes")
    }

    private func mushaf(_ page: Int32) async {
        do {
            let loaded = try await IosServicesDebug.shared.mushaf().load(page: page, force: false)
            guard let loaded else { add("page \(page): not loaded"); return }
            let name = MushafFonts.register(path: loaded.fontPath)
            add("page \(page): \(loaded.page.lines.count) lines, font \(name ?? "nil")")
            await MainActor.run { pageFontName = name }
        } catch {
            add("mushaf failed: \(error)")
        }
    }
}
#endif
