// Triple-buffer style swap chain (same design as MPVPlayer).

public final class SwappableObjectManager<T> {
  private let lock = NSRecursiveLock()
  private var available: [T]
  private var ready: [T] = []
  private var _current: T?
  /// `current`'tan yeni inen nesne: dış tüketici (AVSampleBufferDisplayLayer, IOSurface'ı
  /// doğrudan tarar) bir kare daha gösteriyor olabilir. Hemen `available`'a dönerse mpv
  /// ekranda duran buffer'ın üzerine çizer (yırtılma/flicker). Bir sonraki demotion'a
  /// kadar bekletilir.
  private var cooling: T?

  init(objects: [T], skipCheckArgs: Bool = false) {
    if !skipCheckArgs {
      SwappableObjectManager.checkArgs(objects)
    }
    available = objects
  }

  public func reinit(objects: [T], skipCheckArgs: Bool = false) {
    if !skipCheckArgs {
      SwappableObjectManager.checkArgs(objects)
    }
    lock.lock()
    defer { lock.unlock() }
    available = objects
    ready = []
    _current = nil
    cooling = nil
  }

  public func nextAvailable() -> T? {
    lock.lock()
    defer { lock.unlock() }
    return available.count > 0 ? available.removeFirst() : nil
  }

  public func pushAsReady(_ object: T) {
    lock.lock()
    defer { lock.unlock() }
    ready.append(object)
    updateCurrent()
  }

  public var current: T? {
    lock.lock()
    defer { lock.unlock() }
    return _current
  }

  private func updateCurrent() {
    lock.lock()
    defer { lock.unlock() }

    let next: T? = ready.count > 0 ? ready.removeFirst() : nil
    if next == nil { return }

    let old: T? = _current
    _current = next

    if old == nil { return }
    if let cooled = cooling { available.append(cooled) }
    cooling = old
  }

  private static func checkArgs(_ objects: [T]) {
    if objects.count < 2 {
      Log.error("SwappableObjectManager", "require at least two objects to work")
    }
  }

  /// Explicit teardown avoids Swift 6 implicit-generic-deinit IRGen issues under default actor isolation.
  deinit {
    lock.lock()
    available.removeAll(keepingCapacity: false)
    ready.removeAll(keepingCapacity: false)
    _current = nil
    cooling = nil
    lock.unlock()
  }
}
