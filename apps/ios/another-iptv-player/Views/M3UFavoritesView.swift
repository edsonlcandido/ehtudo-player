import SwiftUI

/// M3U favorileri grid görünümü. `M3UFavoriteStore` üstünden reaktif günceller.
///
/// 310K+ kanallık kataloglarda body başına tam liste taraması yapmamak için favori ve
/// arama sonuçları @State'te tutulur, `.task(id:)` ile (debounce'lu) yeniden hesaplanır.
struct M3UFavoritesView: View {
    let playlist: Playlist

    @ObservedObject private var store = M3UContentStore.shared
    @ObservedObject private var favorites = M3UFavoriteStore.shared
    @EnvironmentObject private var playerOverlay: PlayerOverlayController

    @State private var searchText = ""
    @State private var debouncedQuery = ""
    @State private var debounceTask: Task<Void, Never>?
    @State private var favoriteChannels: [DBM3UChannel] = []
    @State private var filtered: [DBM3UChannel] = []

    /// Favori kümesi veya katalog değişince yeniden hesaplama tetiği.
    private var recomputeKey: String {
        "\(favorites.favoriteIds.count)-\(favorites.favoriteIds.hashValue)-\(store.channels.count)-\(debouncedQuery)"
    }

    var body: some View {
        Group {
            if favoriteChannels.isEmpty {
                ContentUnavailableView(
                    L("favorites.empty.title"),
                    systemImage: "star",
                    description: Text(L("favorites.empty.message"))
                )
            } else if filtered.isEmpty {
                ContentUnavailableView(
                    L("favorites.empty.no_result.title"),
                    systemImage: "magnifyingglass",
                    description: Text(L("category_picker.not_found.message"))
                )
            } else {
                M3UGroupGridContent(items: filtered) { channel in
                    present(channel)
                }
            }
        }
        .searchable(text: $searchText, prompt: L("favorites.search_placeholder"))
        .onChange(of: searchText) { _, new in
            debounceTask?.cancel()
            let trimmed = new.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                debouncedQuery = ""
                return
            }
            debounceTask = Task {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard !Task.isCancelled else { return }
                await MainActor.run { debouncedQuery = new }
            }
        }
        .task(id: recomputeKey) { await recompute() }
        .onDisappear { debounceTask?.cancel(); debounceTask = nil }
        .navigationTitle(L("favorites.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }

    private func recompute() async {
        let channels = store.channels
        let ids = favorites.favoriteIds
        let q = debouncedQuery.trimmingCharacters(in: .whitespaces)
        let result = await Task.detached(priority: .userInitiated) { () -> ([DBM3UChannel], [DBM3UChannel]) in
            // Sıralama: DB'deki kanal listesindeki orijinal sıra (sortIndex) korunur.
            let favs = channels.filter { ids.contains($0.id) }
            let filteredList = q.isEmpty
                ? favs
                : favs.filter { CatalogTextSearch.matches(search: q, text: $0.name) }
            return (favs, filteredList)
        }.value
        guard !Task.isCancelled else { return }
        favoriteChannels = result.0
        filtered = result.1
    }

    private func present(_ channel: DBM3UChannel) {
        guard M3UParser.sanitizedURL(from: channel.url) != nil else { return }
        // Favorilerden oynatırken queue = favori listesi (prev/next favoriler arasında geçer).
        playerOverlay.present {
            M3UPlayerShell(
                playlist: playlist,
                channel: channel,
                queue: filtered
            )
        }
    }
}
