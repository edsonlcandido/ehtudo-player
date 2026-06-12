package app.ehtudo.iptv.data

import app.ehtudo.iptv.data.local.PlaylistDao
import app.ehtudo.iptv.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Repository over the SQLite-backed `playlist` table.
 *
 * UI reads [observeAll] as a Flow (one source of truth, auto-refreshed by
 * Room) and goes through the suspend mutators for inserts / updates / deletes.
 * Wraps [PlaylistDao] so the rest of the app stays free of Room types.
 */
class PlaylistRepository(private val dao: PlaylistDao) {

    fun observeAll(): Flow<List<Playlist>> = dao.observeAll()

    suspend fun find(id: String): Playlist? = dao.findById(id)

    suspend fun first(): Playlist? = dao.first()

    suspend fun add(playlist: Playlist) = dao.insert(playlist)

    suspend fun update(playlist: Playlist) = dao.update(playlist)

    suspend fun remove(id: String) = dao.deleteById(id)

    /**
     * Returns the first playlist in the table, or creates + returns a
     * default one with the fixed server URL (see [AppConfig.SERVER_URL])
     * if the table is empty. The Eh!Iptv build is single-tenant: the
     * user is never offered a way to add playlists, so this is the
     * single entry point that guarantees a row exists by the time the
     * dashboard tries to read it.
     */
    suspend fun firstOrCreateDefault(): Playlist {
        val existing = dao.first()
        if (existing != null) return existing
        val created = Playlist.create(
            name = AppConfig.DEFAULT_PLAYLIST_NAME,
            serverUrl = AppConfig.SERVER_URL,
            kind = app.ehtudo.iptv.model.PlaylistKind.XTREAM,
        )
        dao.insert(created)
        return created
    }
}
