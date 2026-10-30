// Renders a line of (Arabic) text with a font file to a transparent PNG, shaped by Core Text.
// PIL on this machine has no raqm, so it cannot join Arabic letters; the feature graphic uses this for "حفظ".
//
//   swiftc -O render_calligraphy.swift -o /tmp/render_calligraphy
//   /tmp/render_calligraphy <font.ttf> <text> <size> <RRGGBB> <out.png>
import AppKit
import CoreText

let args = CommandLine.arguments
guard args.count == 6 else {
    FileHandle.standardError.write("usage: render_calligraphy font text size RRGGBB out.png\n".data(using: .utf8)!)
    exit(1)
}
let fontURL = URL(fileURLWithPath: args[1])
let text = args[2]
let size = CGFloat(Double(args[3])!)
let hex = UInt32(args[4], radix: 16)!
let color = NSColor(
    red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)

guard let provider = CGDataProvider(url: fontURL as CFURL), let cgFont = CGFont(provider) else { exit(2) }
let ctFont = CTFontCreateWithGraphicsFont(cgFont, size, nil, nil)
let attrs: [NSAttributedString.Key: Any] = [
    NSAttributedString.Key(kCTFontAttributeName as String): ctFont,
    NSAttributedString.Key(kCTForegroundColorAttributeName as String): color.cgColor,
]
let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
let bounds = CTLineGetImageBounds(line, nil)
let pad: CGFloat = size * 0.15
let w = Int(ceil(bounds.width + pad * 2)), h = Int(ceil(bounds.height + pad * 2))
guard let ctx = CGContext(
    data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
else { exit(3) }
ctx.textPosition = CGPoint(x: pad - bounds.minX, y: pad - bounds.minY)
CTLineDraw(line, ctx)
let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[5]))
print("rendered \(w)x\(h)")
