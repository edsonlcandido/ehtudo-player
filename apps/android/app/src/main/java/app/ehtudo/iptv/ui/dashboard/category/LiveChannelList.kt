package app.ehtudo.iptv.ui.dashboard.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import app.ehtudo.iptv.R
import app.ehtudo.iptv.data.FavoriteRepository
import app.ehtudo.iptv.data.local.LiveStreamWithCategory
import app.ehtudo.iptv.ui.LocalFavoriteRepository
import kotlinx.coroutines.launch

/**
 * Two-line channel row — square logo on the left, name + category in the
 * middle, favorite star on the right. Mirrors the iOS
 * `LiveChannelListRow` in `LiveCategoryDetailView`, which switched from
 * the icon-grid to a denser list so users can read long
 * Portuguese-language channel names without truncation.
 *
 * Only used by [LiveCategoryDetailScreen] — VOD and Series keep the poster
 * grid because title length and cover art are central to discovery.
 */
@Composable
fun LiveChannelList(
    items: List<LiveStreamWithCategory>,
    playlistId: String,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector = Icons.Default.LiveTv,
    emptyMessage: String = stringResource(R.string.empty_category_no_live),
    onClick: (LiveStreamWithCategory) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(modifier = modifier, icon = emptyIcon, message = emptyMessage)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = { it.id }) { row ->
            LiveChannelListRow(
                row = row,
                playlistId = playlistId,
                onClick = { onClick(row) },
            )
        }
    }
}

@Composable
private fun LiveChannelListRow(
    row: LiveStreamWithCategory,
    playlistId: String,
    onClick: () -> Unit,
) {
    val favoriteRepository = LocalFavoriteRepository.current
    val scope = rememberCoroutineScope()
    val isFavorite by favoriteRepository
        .observeIsFavorite(row.stream.streamId, playlistId, FavoriteRepository.Type.LIVE)
        .collectAsStateWithLifecycle(initialValue = false)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val logo = row.stream.streamIcon
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.stream.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = row.categoryName
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    scope.launch {
                        favoriteRepository.setFavorite(
                            streamId = row.stream.streamId,
                            playlistId = playlistId,
                            type = FavoriteRepository.Type.LIVE,
                            favorite = !isFavorite,
                        )
                    }
                },
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.detail_favorite_remove else R.string.detail_favorite_add,
                    ),
                    tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, icon: ImageVector, message: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
