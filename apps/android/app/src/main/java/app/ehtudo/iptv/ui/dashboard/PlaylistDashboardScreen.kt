package app.ehtudo.iptv.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ehtudo.iptv.R
import app.ehtudo.iptv.data.local.CategoryEntity
import app.ehtudo.iptv.data.local.LiveStreamWithCategory
import app.ehtudo.iptv.data.local.SeriesWithCategory
import app.ehtudo.iptv.data.local.VodStreamWithCategory
import app.ehtudo.iptv.model.Playlist
import app.ehtudo.iptv.ui.LocalPlaylistContentStore
import app.ehtudo.iptv.ui.LocalPlaylistRepository
import kotlinx.coroutines.launch

/**
 * Top-level catalog screen for a playlist — three tabs (Live TV, Movies,
 * Series), each a vertical scroll of categories, each category a
 * horizontal shelf of cards.
 *
 * iOS counterpart: `DashboardView` + `LiveStreamsView` / `VODView` /
 * `SeriesView`. The Android layout collapses those into one screen because
 * the inner views are almost entirely shelf-of-shelves and don't earn
 * their own files until the category-detail / search views land.
 *
 * Bottom NavigationBar swaps the [HorizontalPager] page so swiping
 * sideways and tapping the bar agree on the active tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDashboardScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenLiveCategory: (String) -> Unit,
    onOpenVodCategory: (String) -> Unit,
    onOpenSeriesCategory: (String) -> Unit,
    onOpenFavorites: (String) -> Unit,
    onPlayLive: (streamId: Int) -> Unit,
    appVersion: String,
    // Optional callbacks that surface Downloads / Watch History from the
    // settings tab. Default to no-op so the existing call sites in tests /
    // previews keep working.
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onResumeEpisode: (episodeId: String) -> Unit = {},
) {
    val repository = LocalPlaylistRepository.current
    val store = LocalPlaylistContentStore.current
    val scope = rememberCoroutineScope()

    // Observe the playlist row reactively so credential updates from the
    // Settings tab are reflected here immediately without a restart.
    val playlist by repository.observeById(playlistId)
        .collectAsStateWithLifecycle(initialValue = null)

    // Trigger a catalog load when the playlist is first available and when
    // credentials transition from blank to non-blank (first-time setup).
    val hadCredentials = remember { mutableStateOf(false) }
    LaunchedEffect(playlist) {
        val pl = playlist ?: return@LaunchedEffect
        val nowHasCredentials = pl.username.isNotBlank() && pl.password.isNotBlank()
        if (nowHasCredentials && !hadCredentials.value) {
            hadCredentials.value = true
        }
        store.loadPlaylistSuspending(pl)
    }

    val isLoading by store.isLoading.collectAsStateWithLifecycle()
    val loadingMessage by store.loadingMessage.collectAsStateWithLifecycle()
    val loadError by store.loadError.collectAsStateWithLifecycle()
    val streamsLoaded by store.streamsLoaded.collectAsStateWithLifecycle()
    val liveCats by store.liveCategories.collectAsStateWithLifecycle()
    val vodCats by store.vodCategories.collectAsStateWithLifecycle()
    val seriesCats by store.seriesCategories.collectAsStateWithLifecycle()
    val liveByCategory by store.liveStreamsByCategoryId.collectAsStateWithLifecycle()
    val vodByCategory by store.vodStreamsByCategoryId.collectAsStateWithLifecycle()
    val seriesByCategory by store.seriesItemsByCategoryId.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 3) { TAB_COUNT }

    // The Eh!Iptv build auto-creates a default playlist with empty
    // credentials on first launch. Until the user fills them in we
    // don't try to load the catalog (the network call would fail), so
    // the body should always render the pager — not ErrorState or
    // LoadingState — and let the user land on the Settings tab.
    val hasCredentials = playlist?.let {
        it.username.isNotBlank() && it.password.isNotBlank()
    } ?: false

    // Picker sheet — opened from the list icon on content tabs. Holds the
    // type ("live" / "vod" / "series") of the active tab so the sheet's
    // hide/unhide writes the right namespace.
    val pickerSheetState = rememberModalBottomSheetState()
    var pickerType by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val tabTitleId = TAB_TITLE_IDS.getOrNull(pagerState.currentPage)
                    Text(
                        text = tabTitleId?.let { androidx.compose.ui.res.stringResource(it) }
                            ?: stringResource(app.ehtudo.iptv.R.string.screen_playlists),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    // Search is available on every tab.
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                    }
                    // Content-tab actions: jump to a category + favorites +
                    // refresh. The Settings tab has its own "İçeriği yeniden
                    // indir" row, so these are hidden there.
                    if (pagerState.currentPage < CONTENT_TAB_LIMIT) {
                        val typeForCurrentTab = tabTypeFor(pagerState.currentPage)
                        IconButton(onClick = { pickerType = typeForCurrentTab }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = stringResource(R.string.dashboard_categories_cd),
                            )
                        }
                        IconButton(onClick = { onOpenFavorites(typeForCurrentTab) }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = stringResource(R.string.dashboard_favorites_cd),
                                tint = Color(0xFFFFC107),
                            )
                        }
                        IconButton(
                            enabled = playlist != null && !isLoading,
                            onClick = {
                                val current = playlist ?: return@IconButton
                                scope.launch {
                                    store.syncFromNetworkReplacingLocal(current) {}
                                    store.reloadFromDatabase(current.id)
                                }
                            },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dashboard_refresh_cd))
                        }
                    }
                },
            )
        },
        bottomBar = {
            DashboardBottomBar(
                currentPage = pagerState.currentPage,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        },
    ) { innerPadding ->
        when {
            // Fresh install (or wiped credentials): skip the loading/error
            // fallbacks and let the user land on the Settings tab.
            !hasCredentials ->
                DashboardPager(
                    pagerState = pagerState,
                    playlistId = playlistId,
                    liveCats = liveCats,
                    vodCats = vodCats,
                    seriesCats = seriesCats,
                    liveByCategory = liveByCategory,
                    vodByCategory = vodByCategory,
                    seriesByCategory = seriesByCategory,
                    streamsLoaded = streamsLoaded,
                    hasCredentials = false,
                    appVersion = appVersion,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onOpenMovie = onOpenMovie,
                    onOpenSeries = onOpenSeries,
                    onOpenLiveCategory = onOpenLiveCategory,
                    onOpenVodCategory = onOpenVodCategory,
                    onOpenSeriesCategory = onOpenSeriesCategory,
                    onPlayLive = onPlayLive,
                    onResumeEpisode = onResumeEpisode,
                    onOpenDownloads = onOpenDownloads,
                    onOpenHistory = onOpenHistory,
                )
            loadError != null && liveCats.isEmpty() && vodCats.isEmpty() && seriesCats.isEmpty() ->
                ErrorState(
                    modifier = Modifier.padding(innerPadding),
                    message = loadError ?: stringResource(R.string.settings_unknown_error),
                    onRetry = {
                        val current = playlist ?: return@ErrorState
                        scope.launch { store.loadPlaylistSuspending(current) }
                    },
                )
            isLoading && liveCats.isEmpty() && vodCats.isEmpty() && seriesCats.isEmpty() ->
                LoadingState(
                    modifier = Modifier.padding(innerPadding),
                    message = loadingMessage,
                )
            else ->
                DashboardPager(
                    pagerState = pagerState,
                    playlistId = playlistId,
                    liveCats = liveCats,
                    vodCats = vodCats,
                    seriesCats = seriesCats,
                    liveByCategory = liveByCategory,
                    vodByCategory = vodByCategory,
                    seriesByCategory = seriesByCategory,
                    streamsLoaded = streamsLoaded,
                    hasCredentials = true,
                    appVersion = appVersion,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onOpenMovie = onOpenMovie,
                    onOpenSeries = onOpenSeries,
                    onOpenLiveCategory = onOpenLiveCategory,
                    onOpenVodCategory = onOpenVodCategory,
                    onOpenSeriesCategory = onOpenSeriesCategory,
                    onPlayLive = onPlayLive,
                    onResumeEpisode = onResumeEpisode,
                    onOpenDownloads = onOpenDownloads,
                    onOpenHistory = onOpenHistory,
                )
        }
    }

    val activePickerType = pickerType
    if (activePickerType != null) {
        // Pick the categories + per-category counts for whichever type the
        // top-bar action exposed. The sheet reads its own hide state via
        // CompositionLocal, so no extra plumbing is needed here.
        val (categoriesForSheet, countsForSheet) = when (activePickerType) {
            "live" -> liveCats to liveByCategory.mapValues { it.value.size }
            "vod" -> vodCats to vodByCategory.mapValues { it.value.size }
            else -> seriesCats to seriesByCategory.mapValues { it.value.size }
        }
        CategoryPickerSheet(
            title = pickerTitleFor(activePickerType),
            playlistId = playlistId,
            contentType = activePickerType,
            categories = categoriesForSheet,
            itemCountsByCategoryId = countsForSheet,
            sheetState = pickerSheetState,
            onSelect = {
                // Jump-to-category scroll lands in a follow-up; closing the
                // sheet on selection still matches iOS UX.
                scope.launch { pickerSheetState.hide() }.invokeOnCompletion {
                    pickerType = null
                }
            },
            onDismiss = { pickerType = null },
        )
    }
}

/**
 * The five-tab pager (Live / Movies / Series / Settings / Search). Pulled
 * out of [PlaylistDashboardScreen] so the `when` body can pick between
 * the regular flow, the loading state, the error state, and the
 * no-credentials flow without duplicating ~60 lines of `HorizontalPager`
 * branches.
 *
 * The pager is only ever rendered when the dashboard has decided to
 * show it (i.e. the loading/error/no-creds fallbacks have already
 * been considered). `userScrollEnabled = false` matches the original
 * behaviour — tab changes only happen through the bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    playlistId: String,
    liveCats: List<CategoryEntity>,
    vodCats: List<CategoryEntity>,
    seriesCats: List<CategoryEntity>,
    liveByCategory: Map<String, List<LiveStreamWithCategory>>,
    vodByCategory: Map<String, List<VodStreamWithCategory>>,
    seriesByCategory: Map<String, List<SeriesWithCategory>>,
    streamsLoaded: Boolean,
    hasCredentials: Boolean,
    appVersion: String,
    modifier: Modifier = Modifier,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenLiveCategory: (String) -> Unit,
    onOpenVodCategory: (String) -> Unit,
    onOpenSeriesCategory: (String) -> Unit,
    onPlayLive: (Int) -> Unit,
    onResumeEpisode: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        // Tab geçişi sadece alt bar'daki tıklamayla; swipe ile
        // kazara yan tab'a kaymak istemiyoruz. animateScrollToPage
        // bu bayrak false iken de çalışmaya devam eder.
        userScrollEnabled = false,
    ) { page ->
        when (page) {
            0 -> LiveTabBody(
                playlistId = playlistId,
                categories = liveCats,
                byCategoryId = liveByCategory,
                streamsLoading = !streamsLoaded,
                hasCredentials = hasCredentials,
                onOpenCategory = onOpenLiveCategory,
                onPlayChannel = onPlayLive,
                onResumeMovie = onOpenMovie,
                onResumeEpisode = onResumeEpisode,
            )
            1 -> MoviesTabBody(
                playlistId = playlistId,
                categories = vodCats,
                byCategoryId = vodByCategory,
                streamsLoading = !streamsLoaded,
                hasCredentials = hasCredentials,
                onOpenMovie = onOpenMovie,
                onOpenCategory = onOpenVodCategory,
                onResumeMovie = onOpenMovie,
                onResumeEpisode = onResumeEpisode,
                onPlayLive = onPlayLive,
            )
            2 -> SeriesTabBody(
                playlistId = playlistId,
                categories = seriesCats,
                byCategoryId = seriesByCategory,
                streamsLoading = !streamsLoaded,
                hasCredentials = hasCredentials,
                onOpenSeries = onOpenSeries,
                onOpenCategory = onOpenSeriesCategory,
                onResumeMovie = onOpenMovie,
                onResumeEpisode = onResumeEpisode,
                onPlayLive = onPlayLive,
            )
            3 -> app.ehtudo.iptv.ui.settings.PlaylistSettingsBody(
                playlistId = playlistId,
                appVersion = appVersion,
                onOpenDownloads = onOpenDownloads,
                onOpenHistory = onOpenHistory,
                modifier = Modifier.fillMaxSize(),
            )
            4 -> app.ehtudo.iptv.ui.search.SearchBody(
                playlistId = playlistId,
                onOpenMovie = onOpenMovie,
                onOpenSeries = onOpenSeries,
                onPlayLive = onPlayLive,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val TAB_COUNT = 5
// Tab titles resolved via stringResource at compose time — see TAB_TITLE_IDS.
private val TAB_TITLE_IDS = intArrayOf(
    app.ehtudo.iptv.R.string.screen_live_tv,
    app.ehtudo.iptv.R.string.screen_movies,
    app.ehtudo.iptv.R.string.screen_series,
    app.ehtudo.iptv.R.string.screen_settings,
    app.ehtudo.iptv.R.string.screen_search,
)
// Settings (3) and Search (4) hide the content-tab top-bar actions.
private const val CONTENT_TAB_LIMIT = 3

/** Index → iOS-compatible type discriminator. */
private fun tabTypeFor(page: Int): String = when (page) {
    0 -> "live"
    1 -> "vod"
    else -> "series"
}

/** Per-type display string for the picker sheet header. */
@Composable
private fun pickerTitleFor(type: String): String = stringResource(
    when (type) {
        "live" -> app.ehtudo.iptv.R.string.picker_live_categories
        "vod" -> app.ehtudo.iptv.R.string.picker_vod_categories
        else -> app.ehtudo.iptv.R.string.picker_series_categories
    },
)

@Composable
private fun DashboardBottomBar(currentPage: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        DashboardTab(
            selected = currentPage == 0,
            icon = Icons.Default.LiveTv,
            label = stringResource(app.ehtudo.iptv.R.string.screen_live_tv),
            onClick = { onSelect(0) },
        )
        DashboardTab(
            selected = currentPage == 1,
            icon = Icons.Default.Movie,
            label = stringResource(app.ehtudo.iptv.R.string.screen_movies),
            onClick = { onSelect(1) },
        )
        DashboardTab(
            selected = currentPage == 2,
            icon = Icons.Default.Tv,
            label = stringResource(app.ehtudo.iptv.R.string.screen_series),
            onClick = { onSelect(2) },
        )
        DashboardTab(
            selected = currentPage == 3,
            icon = Icons.Default.Settings,
            label = stringResource(app.ehtudo.iptv.R.string.screen_settings),
            onClick = { onSelect(3) },
        )
        DashboardTab(
            selected = currentPage == 4,
            icon = Icons.Default.Search,
            label = stringResource(app.ehtudo.iptv.R.string.screen_search),
            onClick = { onSelect(4) },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DashboardTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}

// ---- Tab bodies ----

@Composable
private fun LiveTabBody(
    playlistId: String,
    categories: List<CategoryEntity>,
    byCategoryId: Map<String, List<LiveStreamWithCategory>>,
    streamsLoading: Boolean,
    hasCredentials: Boolean,
    onOpenCategory: (String) -> Unit,
    onPlayChannel: (Int) -> Unit,
    onResumeMovie: (Int) -> Unit = {},
    onResumeEpisode: (String) -> Unit = {},
) {
    val hiddenStore = app.ehtudo.iptv.ui.LocalHiddenCategoryStore.current
    val hiddenIds by hiddenStore.observeHidden(playlistId, "live")
        .collectAsStateWithLifecycle(initialValue = hiddenStore.hiddenIds(playlistId, "live"))
    val visibleCategories = remember(categories, hiddenIds) {
        categories.filter { it.id !in hiddenIds }
    }
    if (visibleCategories.isEmpty()) {
        val msg = when {
            categories.isEmpty() && !hasCredentials ->
                stringResource(app.ehtudo.iptv.R.string.empty_configure_credentials_prompt)
            categories.isEmpty() ->
                stringResource(app.ehtudo.iptv.R.string.empty_no_categories)
            else ->
                stringResource(app.ehtudo.iptv.R.string.empty_categories_hidden)
        }
        EmptyTab(message = msg)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "continue-watching") {
            ContinueWatchingShelf(
                playlistId = playlistId,
                onResumeMovie = onResumeMovie,
                onResumeSeries = onResumeEpisode,
                onPlayLive = onPlayChannel,
            )
        }
        items(visibleCategories, key = { "live_${it.id}" }) { category ->
            val items = byCategoryId[category.id].orEmpty()
            CategoryShelf(
                title = category.name,
                itemCount = items.size,
                streamsLoading = streamsLoading,
                onHeaderClick = { onOpenCategory(category.id) },
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.id }) { row ->
                        LiveStreamCard(
                            name = row.stream.name,
                            iconUrl = row.stream.streamIcon,
                            onClick = { onPlayChannel(row.stream.streamId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviesTabBody(
    playlistId: String,
    categories: List<CategoryEntity>,
    byCategoryId: Map<String, List<VodStreamWithCategory>>,
    streamsLoading: Boolean,
    hasCredentials: Boolean,
    onOpenMovie: (Int) -> Unit,
    onOpenCategory: (String) -> Unit,
    onResumeMovie: (Int) -> Unit = {},
    onResumeEpisode: (String) -> Unit = {},
    onPlayLive: (Int) -> Unit = {},
) {
    val hiddenStore = app.ehtudo.iptv.ui.LocalHiddenCategoryStore.current
    val hiddenIds by hiddenStore.observeHidden(playlistId, "vod")
        .collectAsStateWithLifecycle(initialValue = hiddenStore.hiddenIds(playlistId, "vod"))
    val visibleCategories = remember(categories, hiddenIds) {
        categories.filter { it.id !in hiddenIds }
    }
    if (visibleCategories.isEmpty()) {
        val msg = when {
            categories.isEmpty() && !hasCredentials ->
                stringResource(app.ehtudo.iptv.R.string.empty_configure_credentials_prompt)
            categories.isEmpty() ->
                stringResource(app.ehtudo.iptv.R.string.empty_no_categories)
            else ->
                stringResource(app.ehtudo.iptv.R.string.empty_categories_hidden)
        }
        EmptyTab(message = msg)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "continue-watching") {
            ContinueWatchingShelf(
                playlistId = playlistId,
                onResumeMovie = onResumeMovie,
                onResumeSeries = onResumeEpisode,
                onPlayLive = onPlayLive,
            )
        }
        item(key = "recently-added") {
            RecentlyAddedShelf(
                playlistId = playlistId,
                onOpenMovie = onOpenMovie,
            )
        }
        items(visibleCategories, key = { "vod_${it.id}" }) { category ->
            val items = byCategoryId[category.id].orEmpty()
            CategoryShelf(
                title = category.name,
                itemCount = items.size,
                streamsLoading = streamsLoading,
                onHeaderClick = { onOpenCategory(category.id) },
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.id }) { row ->
                        PosterCard(
                            name = row.stream.name,
                            coverUrl = row.stream.streamIcon,
                            kind = ImageKind.Movie,
                            onClick = { onOpenMovie(row.stream.streamId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesTabBody(
    playlistId: String,
    categories: List<CategoryEntity>,
    byCategoryId: Map<String, List<SeriesWithCategory>>,
    streamsLoading: Boolean,
    hasCredentials: Boolean,
    onOpenSeries: (Int) -> Unit,
    onOpenCategory: (String) -> Unit,
    onResumeMovie: (Int) -> Unit = {},
    onResumeEpisode: (String) -> Unit = {},
    onPlayLive: (Int) -> Unit = {},
) {
    val hiddenStore = app.ehtudo.iptv.ui.LocalHiddenCategoryStore.current
    val hiddenIds by hiddenStore.observeHidden(playlistId, "series")
        .collectAsStateWithLifecycle(initialValue = hiddenStore.hiddenIds(playlistId, "series"))
    val visibleCategories = remember(categories, hiddenIds) {
        categories.filter { it.id !in hiddenIds }
    }
    if (visibleCategories.isEmpty()) {
        val msg = when {
            categories.isEmpty() && !hasCredentials ->
                stringResource(app.ehtudo.iptv.R.string.empty_configure_credentials_prompt)
            categories.isEmpty() ->
                stringResource(app.ehtudo.iptv.R.string.empty_no_categories)
            else ->
                stringResource(app.ehtudo.iptv.R.string.empty_categories_hidden)
        }
        EmptyTab(message = msg)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "continue-watching") {
            ContinueWatchingShelf(
                playlistId = playlistId,
                onResumeMovie = onResumeMovie,
                onResumeSeries = onResumeEpisode,
                onPlayLive = onPlayLive,
            )
        }
        items(visibleCategories, key = { "series_${it.id}" }) { category ->
            val items = byCategoryId[category.id].orEmpty()
            CategoryShelf(
                title = category.name,
                itemCount = items.size,
                streamsLoading = streamsLoading,
                onHeaderClick = { onOpenCategory(category.id) },
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.id }) { row ->
                        PosterCard(
                            name = row.series.name,
                            coverUrl = row.series.cover,
                            kind = ImageKind.Series,
                            onClick = { onOpenSeries(row.series.seriesId) },
                        )
                    }
                }
            }
        }
    }
}

// ---- State views ----

@Composable
private fun EmptyTab(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier, message: String?) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            if (!message.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(modifier: Modifier = Modifier, message: String, onRetry: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.dashboard_retry))
            }
        }
    }
}
