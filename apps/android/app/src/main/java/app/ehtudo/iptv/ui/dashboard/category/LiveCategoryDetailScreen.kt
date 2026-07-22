package app.ehtudo.iptv.ui.dashboard.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ehtudo.iptv.R
import app.ehtudo.iptv.ui.LocalPlaylistContentStore

/**
 * Live-channel category detail — two-line list of channels (square logo on
 * the left, name + category on the right). Replaces the previous adaptive
 * grid so users can read long Portuguese-language channel names without
 * truncation; see [LiveChannelList] for the row layout.
 *
 * VOD / Series category details keep the poster grid because cover art is
 * central to discovery there.
 *
 * iOS counterpart: `LiveCategoryDetailView` in `LiveChannels`.
 *
 * Per-screen search was removed — global search lives in the dashboard's
 * bottom nav (first tab) and uses the same `CatalogTextSearch` matcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCategoryDetailScreen(
    playlistId: String,
    categoryId: String,
    onBack: () -> Unit,
    onPlayChannel: (streamId: Int) -> Unit,
) {
    val contentStore = LocalPlaylistContentStore.current

    val categories by contentStore.liveCategories.collectAsStateWithLifecycle()
    val byCategory by contentStore.liveStreamsByCategoryId.collectAsStateWithLifecycle()
    val category = categories.firstOrNull { it.id == categoryId }
    val allItems = byCategory[categoryId].orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category?.name ?: stringResource(R.string.category_label_fallback),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LiveChannelList(
            items = allItems,
            playlistId = playlistId,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            emptyIcon = Icons.Default.LiveTv,
            emptyMessage = stringResource(R.string.empty_category_no_live),
            onClick = { row -> onPlayChannel(row.stream.streamId) },
        )
    }
}
