package app.ehtudo.iptv.ui.dashboard.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ehtudo.iptv.R
import app.ehtudo.iptv.ui.LocalPlaylistContentStore
import app.ehtudo.iptv.ui.dashboard.ImageKind
import app.ehtudo.iptv.ui.dashboard.PosterCard

/**
 * Series category detail — adaptive grid of 2:3 poster cards. Tapping a
 * poster opens [SeriesDetailScreen] via [onOpenSeries].
 *
 * Per-screen search was removed — use the global Search tab in the bottom
 * nav (first item) instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesCategoryDetailScreen(
    playlistId: String,
    categoryId: String,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    val contentStore = LocalPlaylistContentStore.current
    val categories by contentStore.seriesCategories.collectAsStateWithLifecycle()
    val byCategory by contentStore.seriesItemsByCategoryId.collectAsStateWithLifecycle()
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CategoryGrid(
                items = allItems,
                minCellSize = 110.dp,
                emptyIcon = Icons.Default.Tv,
                emptyMessage = stringResource(R.string.empty_category_no_series),
                itemKey = { it.id },
            ) { row ->
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
