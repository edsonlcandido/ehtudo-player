package app.ehtudo.iptv.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ehtudo.iptv.R
import app.ehtudo.iptv.model.PlaylistKind
import app.ehtudo.iptv.ui.dashboard.M3uDashboardScreen
import app.ehtudo.iptv.ui.dashboard.PlaylistDashboardScreen
import app.ehtudo.iptv.ui.downloads.DownloadsScreen
import app.ehtudo.iptv.ui.history.WatchHistoryScreen
import app.ehtudo.iptv.ui.search.SearchScreen
import app.ehtudo.iptv.ui.dashboard.category.LiveCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.category.MovieCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.category.SeriesCategoryDetailScreen
import app.ehtudo.iptv.ui.dashboard.detail.MovieDetailScreen
import app.ehtudo.iptv.ui.dashboard.detail.SeriesDetailScreen
import app.ehtudo.iptv.ui.player.PlayerScreen
import app.ehtudo.iptv.ui.player.PlayerViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Route names for the app's navigation graph.
 *
 * The single-tenant Eh!Iptv build auto-creates a default playlist; the
 * [SPLASH] route shows the launcher icon while the bootstrap loads and
 * then forwards to the [DASHBOARD] with the right initial tab baked in:
 *
 * - credentials present → Live TV (page 2 in the bottom bar)
 * - credentials blank  → Settings  (page 5)
 *
 * Pre-selecting the initial page avoids the "render Settings, then jump
 * to Live TV on first composition" flicker that the previous
 * `LaunchedEffect(playlist) { pagerState.scrollToPage(…) }` workaround
 * caused. iOS counterpart: `EhIPTVApp.swift` -> `_rootViewModel.appViewModel`.
 */
private object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val LIVE_CATEGORY = "live_category"
    const val VOD_CATEGORY = "vod_category"
    const val SERIES_CATEGORY = "series_category"
    const val PLAYER_MOVIE = "player/movie"
    const val PLAYER_SERIES = "player/series"
    const val PLAYER_LIVE = "player/live"
    const val PLAYER_M3U = "player/m3u"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"

    fun dashboard(playlistId: String, startTab: Int) = "$DASHBOARD/$playlistId/$startTab"

    fun movie(playlistId: String, streamId: Int) = "$MOVIE/$playlistId/$streamId"

    fun series(playlistId: String, seriesId: Int) = "$SERIES/$playlistId/$seriesId"

    fun liveCategory(playlistId: String, categoryId: String) =
        "$LIVE_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

    fun vodCategory(playlistId: String, categoryId: String) =
        "$VOD_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

    fun seriesCategory(playlistId: String, categoryId: String) =
        "$SERIES_CATEGORY/$playlistId/${Uri.encode(categoryId)}"

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
private const val ARG_EPISODE_ID = "episodeId"

private const val ARG_PLAYLIST_ID = "playlistId"
private const val ARG_START_TAB = "startTab"

/** Tab indices into the dashboard's bottom nav — must agree with
 *  `TAB_TITLE_IDS` in `PlaylistDashboardScreen.kt`. */
private const val TAB_INDEX_LIVE = 2
private const val TAB_INDEX_SETTINGS = 5

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playlistRepository = LocalPlaylistRepository.current
    val lastPlaylistStore = LocalLastPlaylistStore.current
    val scope = rememberCoroutineScope()

    var bootstrapError by remember { mutableStateOf<String?>(null) }

    suspend fun runBootstrap(proceed: (String, Int) -> Unit) {
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
        val startTab = if (target.username.isNotBlank() && target.password.isNotBlank()) {
            TAB_INDEX_LIVE
        } else {
            TAB_INDEX_SETTINGS
        }
        proceed(target.id, startTab)
    }

    val proceedToDashboard: (String, Int) -> Unit = { id, tab ->
        navController.navigate(Routes.dashboard(id, tab)) {
            popUpTo(Routes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        runBootstrap(proceedToDashboard)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(route = Routes.SPLASH) {
            SplashScreen(
                error = bootstrapError,
                onRetry = {
                    scope.launch { runBootstrap(proceedToDashboard) }
                },
            )
        }

        composable(
            route = "${Routes.DASHBOARD}/{$ARG_PLAYLIST_ID}/{$ARG_START_TAB}",
            arguments = listOf(
                navArgument(ARG_PLAYLIST_ID) {
                    type = NavType.StringType
                    nullable = false
                },
                navArgument(ARG_START_TAB) {
                    type = NavType.IntType
                    defaultValue = TAB_INDEX_SETTINGS
                },
            ),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(ARG_PLAYLIST_ID)
                ?: return@composable
            val startTabRaw = backStackEntry.arguments?.getInt(ARG_START_TAB)
                ?: TAB_INDEX_SETTINGS
            val startTab = startTabRaw.coerceIn(0, 5)

            var resolvedKind by androidx.compose.runtime.remember(id) {
                androidx.compose.runtime.mutableStateOf<PlaylistKind?>(null)
            }
            androidx.compose.runtime.LaunchedEffect(id) {
                resolvedKind = playlistRepository.find(id)?.kind ?: PlaylistKind.XTREAM
            }

            val backToSystem: () -> Unit = {
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
                        startTab = startTab,
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

/**
 * Black splash with the launcher icon. Shown while the bootstrap loads
 * the default playlist and decides which tab to land on. iOS
 * counterpart: `EhIPTVApp` -> the launch screen + bootstrap that
 * immediately decides whether to push the dashboard's Live TV tab or
 * Settings.
 */
@Composable
private fun SplashScreen(error: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ehiptv_logo),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit,
            )
            if (error != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(
                        if (error == "BOOTSTRAP_TIMEOUT") R.string.bootstrap_timeout
                        else R.string.bootstrap_error,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}
