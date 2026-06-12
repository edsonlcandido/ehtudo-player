package app.ehtudo.iptv.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.ehtudo.iptv.data.FavoriteRepository
import app.ehtudo.iptv.data.HiddenCategoryStore
import app.ehtudo.iptv.data.LastPlaylistStore
import app.ehtudo.iptv.data.PlayerPreferences
import app.ehtudo.iptv.data.PlaylistContentStore
import app.ehtudo.iptv.data.PlaylistRepository
import app.ehtudo.iptv.data.SeriesRepository
import app.ehtudo.iptv.data.VodRepository

/**
 * CompositionLocal for the app-wide [PlaylistRepository].
 *
 * Provided once at the activity root, then read by any screen that needs
 * playlist persistence — the Android equivalent of the iOS
 * `@Environment(\.appDatabase)` pattern.
 */
val LocalPlaylistRepository = staticCompositionLocalOf<PlaylistRepository> {
    error("PlaylistRepository not provided — wrap the UI in CompositionLocalProvider.")
}

/**
 * CompositionLocal for the app-wide [PlaylistContentStore] — the active
 * playlist's categories/streams cache. Mirrors iOS
 * `PlaylistContentStore.shared` reached via `@EnvironmentObject`.
 */
val LocalPlaylistContentStore = staticCompositionLocalOf<PlaylistContentStore> {
    error("PlaylistContentStore not provided — wrap the UI in CompositionLocalProvider.")
}

/** Per-movie reads / writes for [app.ehtudo.iptv.ui.dashboard.detail]. */
val LocalVodRepository = staticCompositionLocalOf<VodRepository> {
    error("VodRepository not provided — wrap the UI in CompositionLocalProvider.")
}

/** Per-series reads / writes for the series detail screen. */
val LocalSeriesRepository = staticCompositionLocalOf<SeriesRepository> {
    error("SeriesRepository not provided — wrap the UI in CompositionLocalProvider.")
}

/** Star toggle + favorites screen. */
val LocalFavoriteRepository = staticCompositionLocalOf<FavoriteRepository> {
    error("FavoriteRepository not provided — wrap the UI in CompositionLocalProvider.")
}

/** "Hide this category" preferences — drives the category picker + shelf filtering. */
val LocalHiddenCategoryStore = staticCompositionLocalOf<HiddenCategoryStore> {
    error("HiddenCategoryStore not provided — wrap the UI in CompositionLocalProvider.")
}

/** Last-opened playlist id — drives `AppNavigation`'s one-shot auto-load on launch. */
val LocalLastPlaylistStore = staticCompositionLocalOf<LastPlaylistStore> {
    error("LastPlaylistStore not provided — wrap the UI in CompositionLocalProvider.")
}

/** Player UX preferences (PiP, background play, long-press 2× speed). */
val LocalPlayerPreferences = staticCompositionLocalOf<PlayerPreferences> {
    error("PlayerPreferences not provided — wrap the UI in CompositionLocalProvider.")
}
