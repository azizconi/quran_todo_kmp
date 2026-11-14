import ComposeApp
import Observation
import SwiftUI

/// Memorize mode of the reader ("Check yourself", `HifzMode` of Android): placeholder until the recall adapter is written.
@MainActor
@Observable
final class ReaderRecall {
    static func begin(model: ReaderModel, start: Int, set: (ReaderRecall?) -> Void) {}
}
