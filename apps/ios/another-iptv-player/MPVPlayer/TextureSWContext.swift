import CoreGraphics
import CoreVideo
import Foundation

public final class TextureSWContext {
  public let pixelBuffer: CVPixelBuffer

  init?(size: CGSize) {
    guard let buffer = TextureSWContext.createPixelBuffer(size) else { return nil }
    self.pixelBuffer = buffer
  }

  deinit {
    TextureSWContext.disposePixelBuffer(pixelBuffer)
  }

  private static func createPixelBuffer(_ size: CGSize) -> CVPixelBuffer? {
    let attrs =
      [
        kCVPixelBufferMetalCompatibilityKey: true
      ] as CFDictionary

    var pixelBuffer: CVPixelBuffer?
    let cvret = CVPixelBufferCreate(
      kCFAllocatorDefault,
      Int(size.width),
      Int(size.height),
      kCVPixelFormatType_32BGRA,
      attrs,
      &pixelBuffer
    )
    if cvret != kCVReturnSuccess {
      Log.error("TextureSWContext", "CVPixelBufferCreate failed: \(cvret) (\(Int(size.width))x\(Int(size.height)))")
      return nil
    }
    return pixelBuffer
  }

  private static func disposePixelBuffer(_ pixelBuffer: CVPixelBuffer) {}
}
