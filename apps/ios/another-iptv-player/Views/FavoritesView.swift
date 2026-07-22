import SwiftUI
import GRDBQuery

struct FavoritesView: View {
    let playlist: Playlist

    @State private var selectedType: String
    @State private var searchText = ""
    @Environment(\.posterMetrics) private var posterMetrics
    @EnvironmentObject private var playerOverlay: PlayerOverlayController
    
    @Query<FavoriteLiveRequest> private var favoriteLive: [LiveStreamWithCategory]
    @Query<FavoriteVODRequest> private var favoriteVODs: [VODWithCategory]
    @Query<FavoriteSeriesRequest> private var favoriteSeries: [SeriesWithCategory]
    @Query<WatchProgressMapRequest> private var vodProgressMap: [String: Double]
    @Query<WatchProgressMapRequest> private var seriesProgressMap: [String: Double]

    init(playlist: Playlist, initialType: String = "vod") {
        self.playlist = playlist
        self._selectedType = State(initialValue: initialType)
        _favoriteLive = Query(FavoriteLiveRequest(playlistId: playlist.id), in: \.appDatabase)
        _favoriteVODs = Query(FavoriteVODRequest(playlistId: playlist.id), in: \.appDatabase)
        _favoriteSeries = Query(FavoriteSeriesRequest(playlistId: playlist.id), in: \.appDatabase)
        _vodProgressMap = Query(WatchProgressMapRequest(playlistId: playlist.id, type: "vod"), in: \.appDatabase)
        _seriesProgressMap = Query(WatchProgressMapRequest(playlistId: playlist.id, type: "series"), in: \.appDatabase)
    }
    
    private var gridColumns: [GridItem] {
        if selectedType == "live" {
            return [GridItem(.adaptive(minimum: posterMetrics.liveGridIconSize), spacing: posterMetrics.gridSpacing)]
        }
        return [GridItem(.adaptive(minimum: posterMetrics.categoryGridPosterWidth), spacing: posterMetrics.gridSpacing)]
    }
    
    var body: some View {
        VStack(spacing: 0) {
            Picker(L("favorites.type"), selection: $selectedType) {
                Text(L("dashboard.live")).tag("live")
                Text(L("dashboard.movies")).tag("vod")
                Text(L("dashboard.series")).tag("series")
            }
            .pickerStyle(.segmented)
            .padding()
            .background(.ultraThinMaterial)
            
            Group {
                if selectedType == "live" {
                    liveGrid
                } else if selectedType == "vod" {
                    vodGrid
                } else {
                    seriesGrid
                }
            }
        }
        .navigationTitle(L("favorites.title"))
        .navigationBarTitleDisplayMode(.large)
        .searchable(text: $searchText, prompt: L("favorites.search_placeholder"))
    }

    // MARK: - Search filtering (favori listeleri küçük olduğundan render başına filtre yeterli)

    private var trimmedQuery: String { searchText.trimmingCharacters(in: .whitespaces) }

    private var displayedLive: [LiveStreamWithCategory] {
        guard !trimmedQuery.isEmpty else { return favoriteLive }
        return favoriteLive.filter { CatalogTextSearch.matches(search: trimmedQuery, text: $0.stream.name) }
    }

    private var displayedVODs: [VODWithCategory] {
        guard !trimmedQuery.isEmpty else { return favoriteVODs }
        return favoriteVODs.filter { CatalogTextSearch.matches(search: trimmedQuery, text: $0.stream.name) }
    }

    private var displayedSeries: [SeriesWithCategory] {
        guard !trimmedQuery.isEmpty else { return favoriteSeries }
        return favoriteSeries.filter { CatalogTextSearch.matches(search: trimmedQuery, text: $0.series.name) }
    }

    private func presentFavoriteLive(stream: DBLiveStream, history: DBWatchHistory?) {
        // Diğer tüm canlı giriş noktaları gibi LivePlayerShell: favoriler arası
        // önceki/sonraki kanal ve yan panel çalışsın (çıplak PlayerView bunları kaybediyordu).
        let queue = displayedLive.map(\.stream)
        let sections = [LiveChannelCategorySection(
            id: "favorites",
            title: L("favorites.title"),
            streams: queue
        )]
        playerOverlay.present(playlistId: playlist.id) {
            LivePlayerShell(
                playlist: playlist,
                queue: queue,
                sections: sections,
                initialStream: stream,
                initialHistory: history,
                subtitle: nil
            )
        }
    }
    
    @ViewBuilder
    private var liveGrid: some View {
        if displayedLive.isEmpty {
            emptyState(icon: "tv", message: trimmedQuery.isEmpty ? L("favorites.empty.live") : L("list.no_result"))
        } else {
            ScrollView {
                LazyVGrid(columns: gridColumns, spacing: posterMetrics.gridRowSpacing) {
                    ForEach(displayedLive) { item in
                        LiveStreamCard(
                            playlistId: playlist.id,
                            stream: item.stream,
                            width: posterMetrics.liveGridIconSize,
                            iconSize: posterMetrics.liveGridIconSize,
                            imageLoadProfile: .grid,
                            onStreamSelected: { stream, history in
                                presentFavoriteLive(stream: stream, history: history)
                            }
                        )
                    }
                }
                .padding()
            }
        }
    }
    
    @ViewBuilder
    private var vodGrid: some View {
        if displayedVODs.isEmpty {
            emptyState(icon: "film", message: trimmedQuery.isEmpty ? L("favorites.empty.movie") : L("list.no_result"))
        } else {
            ScrollView {
                LazyVGrid(columns: gridColumns, spacing: posterMetrics.gridRowSpacing) {
                    ForEach(displayedVODs) { item in
                        NavigationLink(destination: MovieDetailView(playlist: playlist, movie: item.stream)) {
                            VODStreamCard(
                                playlistId: playlist.id,
                                stream: item.stream,
                                categoryName: item.categoryName,
                                posterWidth: posterMetrics.categoryGridPosterWidth,
                                posterHeight: posterMetrics.categoryGridPosterHeight,
                                imageLoadProfile: ImageLoadProfile.grid,
                                watchProgress: vodProgressMap[String(item.stream.streamId)]
                            )
                        }
                    }
                }
                .padding()
            }
        }
    }
    
    @ViewBuilder
    private var seriesGrid: some View {
        if displayedSeries.isEmpty {
            emptyState(icon: "play.tv", message: trimmedQuery.isEmpty ? L("favorites.empty.series") : L("list.no_result"))
        } else {
            ScrollView {
                LazyVGrid(columns: gridColumns, spacing: posterMetrics.gridRowSpacing) {
                    ForEach(displayedSeries) { item in
                        NavigationLink(destination: SeriesDetailView(playlist: playlist, series: item.series)) {
                            SeriesCard(
                                playlistId: playlist.id,
                                stream: item.series,
                                categoryName: item.categoryName,
                                posterWidth: posterMetrics.categoryGridPosterWidth,
                                posterHeight: posterMetrics.categoryGridPosterHeight,
                                imageLoadProfile: ImageLoadProfile.grid,
                                watchProgress: seriesProgressMap[String(item.series.seriesId)]
                            )
                        }
                    }
                }
                .padding()
            }
        }
    }
    
    private func emptyState(icon: String, message: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text(message)
                .font(.callout)
                .foregroundColor(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 50)
    }
}
