import ComposeApp
import SwiftUI

@main
struct iOSApp: App {

    init() {
        KoinInitializerKt.InitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}