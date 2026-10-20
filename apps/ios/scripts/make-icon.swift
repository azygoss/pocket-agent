// SPDX-License-Identifier: GPL-3.0-or-later
// Regenerates the App Store icon: swift scripts/make-icon.swift <out.png>
import AppKit

let size = 1024.0
let img = NSImage(size: NSSize(width: size, height: size))
img.lockFocus()
let ctx = NSGraphicsContext.current!.cgContext
let colors = [NSColor(srgbRed: 0.24, green: 0.38, blue: 0.98, alpha: 1).cgColor,
              NSColor(srgbRed: 0.56, green: 0.30, blue: 0.96, alpha: 1).cgColor] as CFArray
let grad = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: [0, 1])!
ctx.drawLinearGradient(grad, start: CGPoint(x: 0, y: size), end: CGPoint(x: size, y: 0), options: [])
// terminal window card
let card = CGRect(x: 172, y: 232, width: 680, height: 560)
ctx.setFillColor(NSColor(srgbRed: 0.06, green: 0.07, blue: 0.11, alpha: 0.92).cgColor)
ctx.addPath(CGPath(roundedRect: card, cornerWidth: 88, cornerHeight: 88, transform: nil))
ctx.fillPath()
for (i, c) in [NSColor.systemRed, .systemYellow, .systemGreen].enumerated() {
    ctx.setFillColor(c.cgColor)
    ctx.fillEllipse(in: CGRect(x: 250 + Double(i) * 62, y: 690, width: 36, height: 36))
}
// prompt chevron and cursor
ctx.setStrokeColor(NSColor(srgbRed: 0.45, green: 0.95, blue: 0.75, alpha: 1).cgColor)
ctx.setLineWidth(52)
ctx.setLineCap(.round)
ctx.setLineJoin(.round)
ctx.move(to: CGPoint(x: 300, y: 560))
ctx.addLine(to: CGPoint(x: 420, y: 470))
ctx.addLine(to: CGPoint(x: 300, y: 380))
ctx.strokePath()
ctx.setFillColor(NSColor.white.cgColor)
ctx.addPath(CGPath(roundedRect: CGRect(x: 480, y: 350, width: 230, height: 52), cornerWidth: 26, cornerHeight: 26, transform: nil))
ctx.fillPath()
img.unlockFocus()
let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
// App Store icons must be opaque RGB without alpha.
let out = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8,
                           samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: out)
rep.draw(in: NSRect(x: 0, y: 0, width: 1024, height: 1024))
NSGraphicsContext.restoreGraphicsState()
try! out.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
