package app.ehtudo.iptv.data

/**
 * App-wide build-time constants. Anything that the user is not allowed to
 * change lives here so the rest of the codebase can read it from a single
 * source of truth.
 *
 * The credentials (username/password) and the device id are the only knobs
 * the end user can tweak — and those live in the SQLite `playlist` row plus
 * [DeviceIdProvider]. The server URL is fixed by the distributor.
 */
object AppConfig {
    /** Base URL of the Xtream Codes API. Prepended with `http://` because
     *  the provider does not terminate TLS — `usesCleartextTraffic` is on. */
    const val SERVER_URL = "http://dnstv.top"

    /** Display name for the auto-created default playlist. */
    const val DEFAULT_PLAYLIST_NAME = "Eh!Iptv"
}
