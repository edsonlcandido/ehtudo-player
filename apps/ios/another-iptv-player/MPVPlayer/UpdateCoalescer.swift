import Foundation

/// `mpv_render_context_set_update_callback` mpv'nin iç thread'inde her yeni karede tetiklenir.
/// Callback'i main queue'ya sıçratmak, kare zamanlamasını main thread doluluğuna (SwiftUI diff,
/// scroll, overlay açılışı) bağlıyordu — tam da kullanıcı etkileşimindeyken kare düşürüyordu.
/// `NativeVideoOutput.scheduleWorkerRenderDrain` zaten kilit altında coalescing yapar
/// (pendingWorkerRender/workerDrainRunning); burada senkron çağırmak yeterli ve güvenlidir —
/// kilit alma + Worker.enqueue kısa, bloklamayan işlerdir.
final class UpdateCoalescer {
  private let callback: () -> Void

  init(callback: @escaping () -> Void) {
    self.callback = callback
  }

  func schedule() {
    callback()
  }
}
