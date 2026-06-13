package app.ehtudo.iptv.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ehtudo.iptv.R
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
    val scope = rememberCoroutineScope()

    // The bootstrap is a one-shot per process: it asks the repository for
    // the default playlist (auto-creates one on a fresh install), persists
    // its id as "last opened", and navigates to the dashboard. We guard the
    // whole thing with an 8s timeout and an error state so a slow or stuck
    // database (corrupt SQLite, Room initialization hang, etc.) can no
    // longer leave the user on a permanent spinner.
    //
    // Earlier revisions tried to drive this from the `playlists` Flow with
    // `hasBootstrapped` as a key, but Room re-emits immediately after
    // `firstOrCreateDefault` inserts the new row — that flips the key and
    // cancels the in-flight coroutine, so the navigation never happens.
    // Using `LaunchedEffect(Unit)` + a one-shot suspend call sidesteps the
    // race entirely.
    var bootstrapError by remember { mutableStateOf<String?>(null) }

    suspend fun runBootstrap() {
        bootstrapError = null
        val target = try {
            withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                playlistRepository.firstOrCreateDefault()
            }
        } catch (e: TimeoutCancellationException) {
            bootstrapError = "BOOTSTRAP_TIMEOUT"
            Log.w(TAG, "Bootstrap timed out after ${BOOTSTRAP_TIMEOUT_MS}ms")
            return
        } catch (e: Throwable) {
            bootstrapError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Bootstrap failed", e)
            return
        }
        lastPlaylistStore.write(target.id)
        navController.navigate(Routes.dashboard(target.id)) {
            popUpTo(Routes.BOOTSTRAP) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        runBootstrap()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.BOOTSTRAP,
    ) {
        composable(route = Routes.BOOTSTRAP) {
            BootstrapScreen(
                error = bootstrapError,
                onRetry = { scope.launch { runBootstrap() } },
            )
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

/** How long we wait for `firstOrCreateDefault` to return before giving up
 *  and showing the user a "tap to retry" error state. Catches a stuck
 *  database (corrupt SQLite file, Room initialization hang, etc.) — the
 *  8s budget is generous on purpose since the operation is normally a
 *  single Room insert that completes in a few milliseconds. */
private const val BOOTSTRAP_TIMEOUT_MS = 8_000L

private const val TAG = "AppNavigation"

/** Splash shown while the bootstrap launches the default playlist.
 *
 *  In the happy path this is a single CircularProgressIndicator that
 *  disappears within a few frames once navigation completes. When the
 *  bootstrap times out or throws, it surfaces the error and a retry
 *  button so the user isn't stuck on a permanent spinner. */
@Composable
private fun BootstrapScreen(error: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            if (error == null) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = stringResource(
                        if (error == "BOOTSTRAP_TIMEOUT") R.string.bootstrap_timeout
                        else R.string.bootstrap_error,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (error != "BOOTSTRAP_TIMEOUT") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}
