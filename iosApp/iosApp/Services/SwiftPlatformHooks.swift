import ComposeApp
import StoreKit
import UIKit
import WidgetKit

/// The Swift-only Apple APIs that the Kotlin services call (see `IosPlatformHooks`).
final class SwiftPlatformHooks: NSObject, IosPlatformHooks {
    func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    func renderReportPdf(doc: ReportDoc, path: String) -> Bool {
        ReportPdfRenderer.render(doc, to: URL(fileURLWithPath: path))
    }

    func presentShare(location: String, title: String) {
        guard let url = URL(string: location) else { return }
        DispatchQueue.main.async { ShareSheet.present(items: [url]) }
    }

    func requestReview() {
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes
                .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene else { return }
            if #available(iOS 18.0, *) {
                AppStore.requestReview(in: scene)
            } else {
                SKStoreReviewController.requestReview(in: scene)
            }
        }
    }
}

/// The share sheet over the top view controller (a report, a backup copy, an ayah image).
enum ShareSheet {
    @MainActor
    static func present(items: [Any]) {
        guard let root = topViewController() else { return }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        // iPad: a popover needs an anchor.
        controller.popoverPresentationController?.sourceView = root.view
        controller.popoverPresentationController?.sourceRect = CGRect(x: root.view.bounds.midX, y: root.view.bounds.maxY - 1, width: 1, height: 1)
        root.present(controller, animated: true)
    }

    @MainActor
    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive } ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
