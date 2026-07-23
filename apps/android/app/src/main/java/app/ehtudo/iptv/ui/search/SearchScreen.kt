package app.ehtudo.iptv.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ehtudo.iptv.R

/**
 * Search route reached from the dashboard's top-bar magnifier icon.
 *
 * Wraps [SearchBody] — the same composable that used to live on the
 * bottom nav as the first tab — with a [Scaffold] so the route has its
 * own top bar + back arrow. iOS counterpart: `SearchView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenLive: (streamId: Int) -> Unit,
    onOpenMovie: (streamId: Int) -> Unit,
    onOpenSeries: (seriesId: Int) -> Unit,
    onPlayM3uChannel: (channelId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SearchBody(
            playlistId = playlistId,
            onOpenMovie = onOpenMovie,
            onOpenSeries = onOpenSeries,
            onPlayLive = onOpenLive,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
