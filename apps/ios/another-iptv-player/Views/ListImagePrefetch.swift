import Foundation
import Nuke
import SwiftUI
import UIKit

enum ListImagePrefetch {
    static let maxBatch = 48

    private static let prefetcher = ImagePrefetcher(
        pipeline: .shared,
        destination: .memoryCache,
        maxConcurrentRequestCount: 3
    )

    /// Grid veya raf öncesi sınırlı sayıda URL'yi önbelleğe alır.
    ///
    /// Nuke memory-cache anahtarı Resize işlemcisinin boyut + contentMode'unu içerir;
    /// prefetch isteği render isteğiyle BİREBİR aynı kurulmazsa cache hiç isabet etmez
    /// ve her görsel iki kez decode edilir. Bu yüzden çağıran, kartın gerçek render
    /// parametrelerini (CachedImage'a verdiği width/height/contentMode/loadProfile)
    /// geçirir ve istek CachedImage.request ile üretilir.
    static func start(
        urls: [URL],
        width: CGFloat,
        height: CGFloat,
        contentMode: SwiftUI.ContentMode = .fit,
        loadProfile: ImageLoadProfile = .grid
    ) {
        let slice = Array(urls.prefix(maxBatch))
        guard !slice.isEmpty else { return }
        let requests = slice.map {
            CachedImage.request(
                url: $0,
                width: width,
                height: height,
                contentMode: contentMode,
                loadProfile: loadProfile
            )
        }
        prefetcher.startPrefetching(with: requests)
    }
}
