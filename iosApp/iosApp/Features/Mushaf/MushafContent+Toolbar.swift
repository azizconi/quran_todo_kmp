import ComposeApp
import SwiftUI

extension MushafContent {
    @ToolbarContentBuilder func toolbar(page: Int) -> some ToolbarContent {
        ToolbarItem(placement: .principal) {
            let first = MushafPages.shared.firstAyahOf(page: Int32(page))
            VStack(spacing: 0) {
                Button { gotoSheet = true } label: {
                    Text(model.name(Int(first.surah))).qhText({ $0.title2 }).lineLimit(1)
                }
                .buttonStyle(.plain)
                Text(copy.mushaf.placeText(place: model.place(of: page))).qhText({ $0.label }, color: { $0.inkTertiary }).lineLimit(1)
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { QhHaptics.shared.perform(.tick); listen(page: page) } label: {
                Image(systemName: model.anything.value.boolValue ? "pause.fill" : "play.fill").font(.system(size: 16, weight: .medium))
                    .foregroundStyle(qh.colors.ink).frame(minWidth: 32, minHeight: 44)
            }
            .accessibilityLabel(copy.goTo.labelPlay)
            Button { startCheck(from: nil) } label: {
                Image(systemName: "eye").font(.system(size: 17, weight: .medium)).foregroundStyle(qh.colors.ink).frame(minWidth: 32, minHeight: 44)
            }
            .accessibilityLabel(copy.hifz.eyeAction)
            Button {
                IosUi.shared.uiPrefs.setReaderLayout(value: ReaderLayout.list)
                let first = MushafPages.shared.firstAyahOf(page: Int32(page))
                router.replaceTop(with: .reader(Int(first.surah), ayah: Int(selected?.ayah ?? Int(first.ayah)), origin: "pages"))
            } label: {
                Image(systemName: "text.alignright").font(.system(size: 17, weight: .medium)).foregroundStyle(qh.colors.ink).frame(minWidth: 32, minHeight: 44)
            }
            .accessibilityLabel(copy.pages.openAsText)
        }
    }
}
