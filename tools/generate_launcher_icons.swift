// Generates Android launcher icon PNGs from the iOS 1024x1024 app icon.
// Usage: swift tools/generate_launcher_icons.swift <app-icon.png> <res_dir>
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let arguments = CommandLine.arguments
guard arguments.count >= 3 else {
    fatalError("usage: generate_launcher_icons.swift <app-icon.png> <res_dir>")
}
let sourcePath = arguments[1]
let resDir = arguments[2]

guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: sourcePath) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("cannot read \(sourcePath)")
}

func write(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path) as CFURL
    guard let destination = CGImageDestinationCreateWithURL(url, UTType.png.identifier as CFString, 1, nil) else {
        fatalError("cannot create \(path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("cannot write \(path)") }
}

func canvas(size: Int) -> CGContext {
    guard let context = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { fatalError("cannot create context") }
    context.interpolationQuality = .high
    return context
}

/// Legacy square icon at a given pixel size.
func plainIcon(_ pixels: Int) -> CGImage {
    let context = canvas(size: pixels)
    context.draw(image, in: CGRect(x: 0, y: 0, width: pixels, height: pixels))
    return context.makeImage()!
}

/// Adaptive foreground: full 108dp canvas with the artwork scaled into the 72dp safe zone.
func foregroundIcon(_ pixels: Int) -> CGImage {
    let context = canvas(size: pixels)
    let content = Int(Double(pixels) * 2.0 / 3.0)
    let offset = (pixels - content) / 2
    context.draw(image, in: CGRect(x: offset, y: offset, width: content, height: content))
    return context.makeImage()!
}

let densities: [(folder: String, legacy: Int, foreground: Int)] = [
    ("mipmap-mdpi", 48, 108),
    ("mipmap-hdpi", 72, 162),
    ("mipmap-xhdpi", 96, 216),
    ("mipmap-xxhdpi", 144, 324),
    ("mipmap-xxxhdpi", 192, 432),
]

for density in densities {
    let directory = "\(resDir)/\(density.folder)"
    try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
    let icon = plainIcon(density.legacy)
    write(icon, to: "\(directory)/ic_launcher.png")
    write(icon, to: "\(directory)/ic_launcher_round.png")
    write(foregroundIcon(density.foreground), to: "\(directory)/ic_launcher_foreground.png")
    print("\(density.folder): legacy \(density.legacy)px, foreground \(density.foreground)px")
}
