package app.ehtudo.iptv.ui.dashboard.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
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
 * VOD (movies) category detail — adaptive grid of 2:3 poster cards.
 * Tapping a poster opens [MovieDetailScreen] via [onOpenMovie].
 *
 * Per-screen search was removed — global search lives in the dashboard's
 * top-bar search icon and uses the same `CatalogTextSearch` matcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieCategoryDetailScreen(
    playlistId: String,
    categoryId: String,
    onBack: () -> Unit,
    onOpenMovie: (Int) -> Unit,
) {
    val contentStore = LocalPlaylistContentStore.current
    val categories by contentStore.vodCategories.collectAsStateWithLifecycle()
    val byCategory by contentStore.vodStreamsByCategoryId.collectAsStateWithLifecycle()
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
                emptyIcon = Icons.Default.Movie,
                emptyMessage = stringResource(R.string.empty_category_no_movies),
                itemKey = { it.id },
            ) { row ->
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
