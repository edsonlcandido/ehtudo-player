package app.ehtudo.iptv.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ehtudo.iptv.ui.dashboard.M3uDashboardScreen
import app.ehtudo.iptv.ui.dashboard.PlaylistDashboardScreen
import app.ehtudo.iptv.ui.downloads.DownloadsScreen
import app.ehtudo.iptv.ui.history.WatchHistoryScreen
import app.ehtudo.iptv.ui.search.SearchScreen
import app.ehtudo.iptv.model.PlaylistKind
import app.ehtudo.iptv.ui.dashboard.category.LiveCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.category.MovieCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.category.SeriesCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.detail.MovieDetailScreen
import app.ehtudo.iptv.ui.dashboard.detail.SeriesDetailScreen
import app.ehtudo.iptv.ui.favorites.FavoritesScreen
import app.ehtudo.iptv.ui.player.PlayerScreen
import app.ehtudo.iptv.ui.player.PlayerViewModel

/**
 * Route names for the app's navigation graph.
 *
 * Eh!Iptv is a single-tenant build: there's exactly one playlist, auto-
 * created with a fixed server URL. The legacy "add Xtream / add M3U" routes
 * have been removed and replaced with a transient bootstrap screen that
 * either creates the default playlist on first launch or jumps straight
 * to the dashboard.
 */
private object Routes {
    const val BOOTSTRAP = "bootstrap"
    const val DASHBOARD = "dashboard"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val LIVE_CATEGORY = "live_category"
    const val VOD_CATEGORY = "vod_category"
    const val SERIES_CATEGORY = "series_category"
    const val FAVORITES = "favorites"
    const val PLAYER_MOVIE = "player/movie"
    const val PLAYER_SERIES = "player/series"
    const val PLAYER_LIVE = "player/live"
    const val PLAYER_M3U = "player/m3u"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"

    fun dashboard(playlistId: String) = "$DASHBOARD/$playlistId"

    fun movie(playlistId: String, streamId: Int) = "$MOVIE/$playlistId/$streamId"

    fun series(playlistId: String, seriesId: Int) = "$SERIES/$playlistId/$seriesId"

    fun liveCategory(playlistId: String, categoryId: String) =
        "$LIVE_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

    fun vodCategory(playlistId: String, categoryId: String) =
        "$VOD_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

    fun seriesCategory(playlistId: String, categoryId: String) =
        "$SERIES_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

    fun favorites(playlistId: String, type: String) = "$FAVORITES/$playlistId/$type"

    fun playerMovie(playlistId: String, streamId: Int) =
        "$PLAYER_MOVIE/$playlistId/$streamId"

    fun playerSeries(playlistId: String, episodeId: String) =
        "$PLAYER_SERIES/$playlistId/${Uri.encode(episodeId)}"

    fun playerLive(playlistId: String, streamId: Int) =
        "$PLAYER_LIVE/$playlistId/$streamId"

    fun playerM3u(playlistId: String, channelId: String) =
        "$PLAYER_M3U/$playlistId/${Uri.encode(channelId)}"

    fun search(playlistId: String) = "$SEARCH/$playlistId"
    fun history(playlistId: String) = "$HISTORY/$playlistId"
    fun downloads(playlistId: String) = "$DOWNLOADS/$playlistId"
}

private const val ARG_CHANNEL_ID = "channelId"

private const val ARG_STREAM_ID = "streamId"
private const val ARG_SERIES_ID = "seriesId"
private const val ARG_CATEGORY_ID = "categoryId"
private const val ARG_FAV_TYPE = "type"
private const val ARG_EPISODE_ID = "episodeId"

private const val ARG_PLAYLIST_ID = "playlistId"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playlistRepository = LocalPlaylistRepository.current
    val lastPlaylistStore = LocalLastPlaylistStore.current

    // One-shot per process. The bootstrap route observes `playlists` and:
    //   1. waits for the first non-null emission
    //   2. auto-creates the default playlist if the table is empty
    //   3. navigates to the dashboard and pops itself off the stack
    var hasBootstrapped by rememberSaveable { mutableStateOf(false) }
    val playlists by playlistRepository.observeAll().collectAsState(initial = null)

    // Visible transient state so the bootstrap screen can show a hint
    // when the DB is taking longer than a single frame to respond.
    val bootstrapState = remember { mutableStateOf<BootstrapState>(BootstrapState.Waiting) }

    LaunchedEffect(playlists, hasBootstrapped) {
        if (hasBootstrapped) return@LaunchedEffect
        val list = playlists ?: return@LaunchedEffect
        hasBootstrapped = true

        // Honor the "last opened" id only if it still exists in the DB.
        // A dangling reference would otherwise leave the dashboard stuck
        // on its own loading spinner forever.
        val remembered = lastPlaylistStore.read()
        val target = when {
            remembered != null && list.any { it.id == remembered } -> list.first { it.id == remembered }
            else -> {
                if (remembered != null) lastPlaylistStore.clear()
                list.firstOrNull() ?: run {
                    bootstrapState.value = BootstrapState.Creating
                    playlistRepository.firstOrCreateDefault()
                }
            }
        }
        lastPlaylistStore.write(target.id)
        navController.navigate(Routes.dashboard(target.id)) {
            popUpTo(Routes.BOOTSTRAP) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.BOOTSTRAP,
    ) {
        composable(route = Routes.BOOTSTRAP) {
            BootstrapScreen(state = bootstrapState.value)
        }

        composable(
            route = "${Routes.DASHBOARD}/{$ARG_PLAYLIST_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable

            // Branch on playlist kind: Xtream dashboards have the
            // Live/VOD/Series triple, M3U has one flat channel list.
            var resolvedKind by androidx.compose.runtime.remember(id) {
                androidx.compose.runtime.mutableStateOf<PlaylistKind?>(null)
            }
            androidx.compose.runtime.LaunchedEffect(id) {
                resolvedKind = playlistRepository.find(id)?.kind ?: PlaylistKind.XTREAM
            }

            val backToSystem: () -> Unit = {
                // In the single-tenant build there's nowhere to go "back"
                // to. The dashboard is the only entry on the back stack
                // and the BOOTSTRAP route has already been popped.
                navController.popBackStack()
            }

            when (resolvedKind) {
                PlaylistKind.M3U -> {
                    M3uDashboardScreen(
                        playlistId = id,
                        onBack = backToSystem,
                        onPlayChannel = { channelId, _, _ ->
                            navController.navigate(Routes.playerM3u(id, channelId))
                        },
                    )
                }
                else -> {
                    PlaylistDashboardScreen(
                        playlistId = id,
                        onBack = backToSystem,
                        onOpenMovie = { streamId ->
                            navController.navigate(Routes.movie(id, streamId))
                        },
                        onOpenSeries = { seriesId ->
                            navController.navigate(Routes.series(id, seriesId))
                        },
                        onOpenLiveCategory = { catId ->
                            navController.navigate(Routes.liveCategory(id, catId))
                        },
                        onOpenVodCategory = { catId ->
                            navController.navigate(Routes.vodCategory(id, catId))
                        },
                        onOpenSeriesCategory = { catId ->
                            navController.navigate(Routes.seriesCategory(id, catId))
                        },
                        onOpenFavorites = { type ->
                            navController.navigate(Routes.favorites(id, type))
                        },
                        onPlayLive = { streamId ->
                            navController.navigate(Routes.playerLive(id, streamId))
                        },
                        onOpenDownloads = {
                            navController.navigate(Routes.downloads(id))
                        },
                        onOpenHistory = {
                            navController.navigate(Routes.history(id))
                        },
                        onOpenSearch = {
                            navController.navigate(Routes.search(id))
                        },
                        onResumeEpisode = { eid ->
                            navController.navigate(Routes.playerSeries(id, eid))
                        },
                        appVersion = app.ehtudo.iptv.BuildConfig.VERSION_NAME,
                    )
                }
            }
        }

        composable(
            route = "${Routes.MOVIE}/{$ARG_PLAYLIST_ID}/{$ARG_STREAM_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(ARG_STREAM_ID) {
                    type = NavType.IntType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val streamId = backStackEntry.arguments?.getInt(ARG_STREAM_ID)
                ?: return@composable
            MovieDetailScreen(
                playlistId = playlistId,
                streamId = streamId,
                onBack = { navController.popBackStack() },
                onPlay = {
                    navController.navigate(Routes.playerMovie(playlistId, streamId))
                },
            )
        }

        composable(
            route = "${Routes.PLAYER_MOVIE}/{$ARG_PLAYLIST_ID}/{$ARG_STREAM_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_STREAM_ID) { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val streamId = backStackEntry.arguments?.getInt(ARG_STREAM_ID)
                ?: return@composable
            PlayerScreen(
                playlistId = playlistId,
                streamRef = streamId.toString(),
                kind = PlayerViewModel.Kind.MOVIE,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.PLAYER_SERIES}/{$ARG_PLAYLIST_ID}/{$ARG_EPISODE_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_EPISODE_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val episodeId = backStackEntry.arguments?.getString(ARG_EPISODE_ID)
                ?: return@composable
            PlayerScreen(
                playlistId = playlistId,
                streamRef = episodeId,
                kind = PlayerViewModel.Kind.SERIES_EPISODE,
                onBack = { navController.popBackStack() },
                onPlayNextEpisode = { nextId ->
                    // Replace the current player entry so back goes to the
                    // series detail rather than the previous episode.
                    navController.navigate(Routes.playerSeries(playlistId, nextId)) {
                        popUpTo("${Routes.PLAYER_SERIES}/{$ARG_PLAYLIST_ID}/{$ARG_EPISODE_ID}") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = "${Routes.PLAYER_M3U}/{$ARG_PLAYLIST_ID}/{$ARG_CHANNEL_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_CHANNEL_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val channelId = backStackEntry.arguments?.getString(ARG_CHANNEL_ID)
                ?: return@composable
            PlayerScreen(
                playlistId = playlistId,
                streamRef = channelId,
                kind = PlayerViewModel.Kind.M3U_CHANNEL,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.PLAYER_LIVE}/{$ARG_PLAYLIST_ID}/{$ARG_STREAM_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_STREAM_ID) { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val streamId = backStackEntry.arguments?.getInt(ARG_STREAM_ID)
                ?: return@composable
            PlayerScreen(
                playlistId = playlistId,
                streamRef = streamId.toString(),
                kind = PlayerViewModel.Kind.LIVE,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.SERIES}/{$ARG_PLAYLIST_ID}/{$ARG_SERIES_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(ARG_SERIES_ID) {
                    type = NavType.IntType
                },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val seriesId = backStackEntry.arguments?.getInt(ARG_SERIES_ID)
                ?: return@composable
            SeriesDetailScreen(
                playlistId = playlistId,
                seriesId = seriesId,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { episodeId ->
                    navController.navigate(Routes.playerSeries(playlistId, episodeId))
                },
            )
        }

        composable(
            route = "${Routes.LIVE_CATEGORY}/{$ARG_PLAYLIST_ID}/{$ARG_CATEGORY_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_CATEGORY_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val categoryId = backStackEntry.arguments?.getString(ARG_CATEGORY_ID)
                ?: return@composable
            LiveCategoryDetailScreen(
                playlistId = playlistId,
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onPlayChannel = { streamId ->
                    navController.navigate(Routes.playerLive(playlistId, streamId))
                },
            )
        }

        composable(
            route = "${Routes.VOD_CATEGORY}/{$ARG_PLAYLIST_ID}/{$ARG_CATEGORY_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_CATEGORY_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val categoryId = backStackEntry.arguments?.getString(ARG_CATEGORY_ID)
                ?: return@composable
            MovieCategoryDetailScreen(
                playlistId = playlistId,
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onOpenMovie = { streamId ->
                    navController.navigate(Routes.movie(playlistId, streamId))
                },
            )
        }

        composable(
            route = "${Routes.SERIES_CATEGORY}/{$ARG_PLAYLIST_ID}/{$ARG_CATEGORY_ID}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_CATEGORY_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val categoryId = backStackEntry.arguments?.getString(ARG_CATEGORY_ID)
                ?: return@composable
            SeriesCategoryDetailScreen(
                playlistId = playlistId,
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onOpenSeries = { seriesId ->
                    navController.navigate(Routes.series(playlistId, seriesId))
                },
            )
        }

        composable(
            route = "${Routes.FAVORITES}/{$ARG_PLAYLIST_ID}/{$ARG_FAV_TYPE}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType },
                navArgument(ARG_FAV_TYPE) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val type = backStackEntry.arguments?.getString(ARG_FAV_TYPE) ?: "vod"
            FavoritesScreen(
                playlistId = playlistId,
                initialType = type,
                onBack = { navController.popBackStack() },
                onOpenMovie = { streamId ->
                    navController.navigate(Routes.movie(playlistId, streamId))
                },
                onOpenSeries = { seriesId ->
                    navController.navigate(Routes.series(playlistId, seriesId))
                },
                onPlayLive = { streamId ->
                    navController.navigate(Routes.playerLive(playlistId, streamId))
                },
            )
        }


        composable(
            route = "${Routes.SEARCH}/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID) ?: return@composable
            SearchScreen(
                playlistId = id,
                onBack = { navController.popBackStack() },
                onOpenLive = { sid -> navController.navigate(Routes.playerLive(id, sid)) },
                onOpenMovie = { sid -> navController.navigate(Routes.movie(id, sid)) },
                onOpenSeries = { sid -> navController.navigate(Routes.series(id, sid)) },
                onPlayM3uChannel = { cid -> navController.navigate(Routes.playerM3u(id, cid)) },
            )
        }

        composable(
            route = "${Routes.HISTORY}/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID) ?: return@composable
            WatchHistoryScreen(
                playlistId = id,
                onBack = { navController.popBackStack() },
                onResumeMovie = { sid -> navController.navigate(Routes.movie(id, sid)) },
                onResumeSeries = { eid -> navController.navigate(Routes.playerSeries(id, eid)) },
                onPlayLive = { sid -> navController.navigate(Routes.playerLive(id, sid)) },
            )
        }

        composable(
            route = "${Routes.DOWNLOADS}/{$ARG_PLAYLIST_ID}",
            arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID) ?: return@composable
            DownloadsScreen(
                playlistId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** What the [BootstrapScreen] is currently doing — drives the label. */
private enum class BootstrapState { Waiting, Creating }

/** Splash shown while the bootstrap launches the default playlist. */
@Composable
private fun BootstrapScreen(state: BootstrapState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    }
}
