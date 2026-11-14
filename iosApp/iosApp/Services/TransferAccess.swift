import Foundation

/// Glue between the document picker / the file exporter and `TransferFiles` of the shared layer (which reads and writes plain paths).
///
/// - Import: `.fileImporter` gives a security-scoped url; `TransferAccess.stage(_:)` copies it into a temporary file and returns the
///   `file://` string to give to the view model (`importBackup(location:)`).
/// - Export: the view model's `createShareFile` writes the copy to a temporary file and gives its location; show it with
///   `ShareSheet.present(items: [URL(string: location)!])` or `.fileExporter` over `TransferAccess.url(of:)`.
enum TransferAccess {
    /// Copies the picked file into `tmp/transfer-in/` while its security scope is open; returns the `file://` location, nil on failure.
    static func stage(_ picked: URL) -> String? {
        let scoped = picked.startAccessingSecurityScopedResource()
        defer { if scoped { picked.stopAccessingSecurityScopedResource() } }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("transfer-in", isDirectory: true)
        try? FileManager.default.removeItem(at: folder)
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let target = folder.appendingPathComponent(picked.lastPathComponent)
            try FileManager.default.copyItem(at: picked, to: target)
            return target.absoluteString
        } catch {
            return nil
        }
    }

    /// The url of a location that `createShareFile` returned.
    static func url(of location: String) -> URL? { URL(string: location) }

    /// Removes the staged import.
    static func clearStaged() {
        try? FileManager.default.removeItem(at: FileManager.default.temporaryDirectory.appendingPathComponent("transfer-in", isDirectory: true))
    }
}
