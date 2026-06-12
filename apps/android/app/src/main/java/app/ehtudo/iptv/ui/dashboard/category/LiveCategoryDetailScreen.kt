package app.ehtudo.iptv.ui.dashboard.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ehtudo.iptv.R
import app.ehtudo.iptv.ui.LocalPlaylistContentStore
import app.ehtudo.iptv.ui.dashboard.LiveStreamCard

/**
 * Live-channel category detail — adaptive grid of square logo cards.
 *
 * iOS counterpart: `LiveCategoryDetailView`. Tapping a card hands the
 * `streamId` up via [onPlayChannel]; navigation routes to the native
 * `PlayerScreen` (kind `LIVE`).
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

    var query by remember { mutableStateOf("") }
    val filtered = remember(allItems, query) {
        allItems.filterByQuery(query) { it.stream.name }
    }

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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CategorySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.category_search_live),
            )
            CategoryGrid(
                items = filtered,
                minCellSize = 110.dp,
                emptyIcon = if (query.isBlank()) Icons.Default.LiveTv else Icons.Default.Search,
                emptyMessage = if (query.isBlank()) stringResource(R.string.empty_category_no_live) else stringResource(R.string.empty_category_search_no_live),
                itemKey = { it.id },
            ) { row ->
                LiveStreamCard(
                    name = row.stream.name,
                    iconUrl = row.stream.streamIcon,
                    onClick = { onPlayChannel(row.stream.streamId) },
                )
            }
        }
    }
}
