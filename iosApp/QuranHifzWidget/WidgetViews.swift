import SwiftUI
import WidgetKit

/// A pointed (mihrab) arch outline: the one ornament of the widget. No stars.
struct ArchShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width, h = rect.height
        p.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + h * 0.42))
        p.addCurve(to: CGPoint(x: rect.minX + w / 2, y: rect.minY),
                   control1: CGPoint(x: rect.minX, y: rect.minY + h * 0.18),
                   control2: CGPoint(x: rect.minX + w * 0.36, y: rect.minY + h * 0.12))
        p.addCurve(to: CGPoint(x: rect.maxX, y: rect.minY + h * 0.42),
                   control1: CGPoint(x: rect.minX + w * 0.64, y: rect.minY + h * 0.12),
                   control2: CGPoint(x: rect.maxX, y: rect.minY + h * 0.18))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        return p
    }
}

/// The widget's face for every family. Pure SwiftUI so it can be previewed and rendered outside the extension.
struct LessonWidgetView: View {
    let snapshot: LessonSnapshot
    let family: WidgetFamily
    var dark: Bool

    private var palette: WidgetPalette { .of(snapshot.palette, dark: dark) }

    var body: some View {
        switch family {
        case .accessoryRectangular: rectangular
        case .accessoryCircular: circular
        case .systemMedium: medium
        default: small
        }
    }

    // MARK: Home screen

    private var small: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(snapshot.title)
                .font(.system(size: 12, weight: .bold, design: .rounded)).foregroundStyle(palette.primary)
                .lineLimit(2).minimumScaleFactor(0.8)
            if !snapshot.detail.isEmpty {
                Text(snapshot.detail).font(.system(size: 13, weight: .medium)).foregroundStyle(palette.ink).lineLimit(3)
            }
            Spacer(minLength: 0)
            if let streak = snapshot.streak {
                Text(streak).font(.system(size: 11, weight: .semibold)).foregroundStyle(palette.gold).lineLimit(1)
            }
            if let action = snapshot.action { pill(action) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(14)
        .background(face)
    }

    private var medium: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(snapshot.title)
                    .font(.system(size: 13, weight: .bold, design: .rounded)).foregroundStyle(palette.primary).lineLimit(1)
                if !snapshot.detail.isEmpty {
                    Text(snapshot.detail).font(.system(size: 16, weight: .semibold)).foregroundStyle(palette.ink).lineLimit(3)
                }
                Spacer(minLength: 0)
                if let streak = snapshot.streak {
                    HStack(spacing: 5) {
                        Image(systemName: "flame.fill").font(.system(size: 11))
                        Text(streak).font(.system(size: 12, weight: .semibold)).lineLimit(1)
                    }
                    .foregroundStyle(palette.gold)
                }
            }
            if let action = snapshot.action {
                VStack {
                    Spacer(minLength: 0)
                    pill(action).fixedSize()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(16)
        .background(face)
    }

    private func pill(_ text: String) -> some View {
        HStack(spacing: 5) {
            Image(systemName: "play.fill").font(.system(size: 10, weight: .bold))
            Text(text).font(.system(size: 13, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
        }
        .foregroundStyle(palette.onPrimary)
        .padding(.horizontal, 12).padding(.vertical, 7)
        .background(palette.primary, in: Capsule())
    }

    /// Container colour, a faint arch and a gold hairline: the card of the app's lesson, small.
    private var face: some View {
        ZStack(alignment: .bottomTrailing) {
            LinearGradient(colors: [palette.primaryContainer, palette.surface], startPoint: .topLeading, endPoint: .bottomTrailing)
            ArchShape()
                .stroke(palette.gold.opacity(dark ? 0.22 : 0.28), lineWidth: 1.2)
                .frame(width: 70, height: 84)
                .offset(x: 18, y: 14)
            ArchShape()
                .stroke(palette.primary.opacity(0.12), lineWidth: 1)
                .frame(width: 54, height: 66)
                .offset(x: 10, y: 14)
        }
    }

    // MARK: Lock screen

    var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(snapshot.title).font(.system(size: 13, weight: .bold)).lineLimit(1).widgetAccentable()
            if !snapshot.detail.isEmpty { Text(snapshot.detail).font(.system(size: 12)).lineLimit(2) }
            if let streak = snapshot.streak { Text(streak).font(.system(size: 11, weight: .medium)).opacity(0.8).lineLimit(1) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    var circular: some View {
        ZStack {
            AccessoryWidgetBackground()
            VStack(spacing: 0) {
                Image(systemName: "book.closed.fill").font(.system(size: 17)).widgetAccentable()
                if let days = snapshot.streak?.split(separator: " ").first {
                    Text(days).font(.system(size: 13, weight: .bold)).minimumScaleFactor(0.7)
                }
            }
        }
    }
}
