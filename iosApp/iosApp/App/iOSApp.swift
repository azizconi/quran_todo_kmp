import ComposeApp
import SwiftUI

@main
struct iOSApp: App {

    init() {
        KoinInitializerKt.InitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            // Temporary root until the app shell (IOS-S3): the design catalog.
            QhThemeRoot(settings: .shared) {
                DesignCatalogView()
            }
            .qhTracksTouch()
        }
    }
}
