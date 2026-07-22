package app.ehtudo.iptv.ui.dashboard.detail

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ehtudo.iptv.R
import app.ehtudo.iptv.data.FavoriteRepository
import app.ehtudo.iptv.data.local.EpisodeEntity
import app.ehtudo.iptv.data.local.SeasonEntity
import app.ehtudo.iptv.data.local.SeriesEntity
import app.ehtudo.iptv.model.Playlist
import app.ehtudo.iptv.networking.XtreamApiClient
import app.ehtudo.iptv.ui.LocalFavoriteRepository
import app.ehtudo.iptv.ui.LocalPlaylistRepository
import app.ehtudo.iptv.ui.LocalSeriesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Kotlin port of iOS `SeriesDetailView`.
 *
 * Reactively reads the series row and its seasons from SQLite. On first
 * entry, if `seasonsLoaded == false`, kicks off `get_series_info` and
 * merges seasons + episodes + series metadata in a single Room transaction
 * (see `SeriesRepository.applyInfo`). Episodes for the selected season are
 * a separate Flow so picking a new season just swaps the subscription.
 *
 * Tapping an episode navigates into the native `PlayerScreen` (kind
 * `SERIES_EPISODE`); the global "İzle" plays the first episode of the
 * first season the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    playlistId: String,
    seriesId: Int,
    onBack: () -> Unit,
    onPlayEpisode: (episodeId: String) -> Unit,
) {
    val context = LocalContext.current
    val playlistRepository = LocalPlaylistRepository.current
    val seriesRepository = LocalSeriesRepository.current
    val favoriteRepository = LocalFavoriteRepository.current
    val scope = rememberCoroutineScope()

    val isFavorite by favoriteRepository.observeIsFavorite(seriesId, playlistId, FavoriteRepository.Type.SERIES)
        .collectAsStateWithLifecycle(initialValue = false)

    var playlist by remember(playlistId) { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(playlistId) {
        playlist = playlistRepository.find(playlistId)
    }

    val series by seriesRepository.observeSeries(seriesId, playlistId)
        .collectAsStateWithLifecycle(initialValue = null)
    val seasons by seriesRepository.observeSeasons(seriesId, playlistId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedSeasonId by remember(seriesId, playlistId) { mutableStateOf<String?>(null) }
    LaunchedEffect(seasons) {
        // Pick the first season once the list lands. iOS picks the season
        // containing the most-recently-watched episode; that lookup needs
        // watch-history queries which haven't been ported yet, so the
        // simpler iOS fallback (first season) is what we do today.
        if (selectedSeasonId == null) {
            selectedSeasonId = seasons.firstOrNull()?.id
        } else if (seasons.none { it.id == selectedSeasonId }) {
            // Seasons reshuffled after a re-sync — fall back to the first.
            selectedSeasonId = seasons.firstOrNull()?.id
        }
    }

    val episodes by remember(selectedSeasonId) {
        selectedSeasonId?.let { seriesRepository.observeEpisodes(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    var isFetchingInfo by remember(seriesId, playlistId) { mutableStateOf(false) }
    var fetchError by remember(seriesId, playlistId) { mutableStateOf<String?>(null) }
    // Bumped by the retry buttons (banner + full-screen ErrorState) to force
    // the LaunchedEffect below to re-run even when nothing else has changed.
    var retryToken by remember(seriesId, playlistId) { mutableIntStateOf(0) }

    fun retryFetch() {
        fetchError = null
        retryToken += 1
    }

    LaunchedEffect(playlist?.id, series?.seriesId, series?.seasonsLoaded, retryToken) {
        val pl = playlist ?: return@LaunchedEffect
        val current = series ?: return@LaunchedEffect
        if (current.seasonsLoaded || isFetchingInfo) return@LaunchedEffect
        isFetchingInfo = true
        fetchError = null
        try {
            val client = XtreamApiClient(pl)
            val info = client.getSeriesInfo(current.seriesId)
            seriesRepository.applyInfo(current.seriesId, pl.id, info)
        } catch (e: Throwable) {
            Log.w(TAG, "getSeriesInfo failed", e)
            fetchError = e.message
        } finally {
            isFetchingInfo = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = series?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            favoriteRepository.setFavorite(
                                streamId = seriesId,
                                playlistId = playlistId,
                                type = FavoriteRepository.Type.SERIES,
                                favorite = !isFavorite,
                            )
                        }
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) stringResource(R.string.detail_favorite_remove) else stringResource(R.string.detail_favorite_add),
                            tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { _ ->
        when {
            // Row is genuinely missing from the local DB and the metadata
            // fetch has failed. Show a full-screen error with a retry so
            // the user can recover without navigating away.
            series == null && fetchError != null -> ErrorState(
                message = fetchError ?: stringResource(R.string.settings_unknown_error),
                onRetry = ::retryFetch,
            )
            series == null -> LoadingState(message = stringResource(R.string.detail_loading_series))
            else -> {
                val pl = playlist
                SeriesDetailContent(
                    series = series!!,
                    playlist = pl,
                    seasons = seasons,
                    selectedSeasonId = selectedSeasonId,
                    onSelectSeason = { selectedSeasonId = it },
                    episodes = episodes,
                    seasonsLoading = isFetchingInfo,
                    metadataError = fetchError,
                    onRetryMetadata = ::retryFetch,
                    onDismissMetadata = { fetchError = null },
                    onWatchFirst = {
                        val first = seasons.firstOrNull() ?: return@SeriesDetailContent
                        scope.launch {
                            val ep = seriesRepository.firstEpisodeInSeason(first.id) ?: return@launch
                            onPlayEpisode(ep.id)
                        }
                    },
                    onPlayEpisode = { ep -> onPlayEpisode(ep.id) },
                    onTrailer = { trailerUrl ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)))
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.detail_trailer_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(
    series: SeriesEntity,
    playlist: Playlist?,
    seasons: List<SeasonEntity>,
    selectedSeasonId: String?,
    onSelectSeason: (String) -> Unit,
    episodes: List<EpisodeEntity>,
    seasonsLoading: Boolean,
    metadataError: String?,
    onRetryMetadata: () -> Unit,
    onDismissMetadata: () -> Unit,
    onWatchFirst: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onTrailer: (String) -> Unit,
) {
    val rating10 = series.rating5Based?.let { it * 2 }
    val heroConfig = DetailHeroConfig(
        title = series.name,
        backdropUrl = series.backdropPath,
        posterUrl = series.cover,
        year = DetailFormatting.year(series.releaseDate),
        runtime = DetailFormatting.seriesRuntime(series.episodeRunTime),
        rating10 = rating10,
        ratingText = series.rating,
        placeholderIcon = Icons.Default.Tv,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Persistent banner over the content while the metadata enrichment
        // fetch has failed. Replaces the previous behaviour of swapping the
        // whole screen to ErrorState, which flashed for a frame when the
        // Room flow re-emitted the row.
        if (metadataError != null) {
            MetadataErrorBanner(
                error = metadataError,
                onRetry = onRetryMetadata,
                onDismiss = onDismissMetadata,
            )
        }

        DetailHero(config = heroConfig)

        val genres = DetailFormatting.genreList(series.genre)
        if (genres.isNotEmpty()) {
            GenreChipRow(genres = genres)
        }

        DetailActionBar(
            primaryTitle = stringResource(R.string.detail_watch),
            onPrimary = onWatchFirst,
            primaryIcon = Icons.Default.PlayArrow,
            trailerTitle = series.youtubeTrailer.normaliseTrailer()?.let { stringResource(R.string.detail_trailer) },
            onTrailer = series.youtubeTrailer.normaliseTrailer()?.let { url -> { onTrailer(url) } },
        )

        val plot = series.plot?.trim().orEmpty()
        if (plot.isNotEmpty()) {
            DetailPlotBlock(plot = plot)
        }

        val director = series.director?.trim().orEmpty()
        if (director.isNotEmpty()) {
            DetailInfoTextBlock(label = stringResource(R.string.detail_director), value = director)
        }

        val cast = series.cast?.trim().orEmpty()
        if (cast.isNotEmpty()) {
            DetailInfoTextBlock(label = stringResource(R.string.detail_section_cast), value = cast, maxLines = 3)
        }

        // Seasons section.
        SeasonsSection(
            series = series,
            seasons = seasons,
            selectedSeasonId = selectedSeasonId,
            onSelectSeason = onSelectSeason,
            episodes = episodes,
            seasonsLoading = seasonsLoading,
            onPlayEpisode = onPlayEpisode,
        )

        if (playlist == null) {
            Text(
                text = stringResource(R.string.detail_loading_playlist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SeasonsSection(
    series: SeriesEntity,
    seasons: List<SeasonEntity>,
    selectedSeasonId: String?,
    onSelectSeason: (String) -> Unit,
    episodes: List<EpisodeEntity>,
    seasonsLoading: Boolean,
    onPlayEpisode: (EpisodeEntity) -> Unit,
) {
    if (seasons.isEmpty()) {
        val message = if (series.seasonsLoaded) {
            stringResource(R.string.detail_no_seasons)
        } else if (seasonsLoading) {
            stringResource(R.string.detail_seasons_loading)
        } else {
            stringResource(R.string.detail_seasons_failed)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_seasons),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val selected = seasons.firstOrNull { it.id == selectedSeasonId }
            selected?.episodeCount?.let { count ->
                Text(
                    text = stringResource(R.string.detail_episode_count_format, count),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SeasonTabBar(
            seasons = seasons,
            selectedId = selectedSeasonId,
            onSelect = onSelectSeason,
        )

        val selected = seasons.firstOrNull { it.id == selectedSeasonId }
        val overview = selected?.overview?.trim().orEmpty()
        if (overview.isNotEmpty()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        EpisodeList(episodes = episodes, onPlay = onPlayEpisode)
    }
}

// ---- Helpers ----

private fun String?.normaliseTrailer(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return if (raw.startsWith("http", ignoreCase = true)) raw
    else "https://www.youtube.com/watch?v=$raw"
}

@Composable
private fun LoadingState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
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
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

private const val TAG = "SeriesDetailScreen"
