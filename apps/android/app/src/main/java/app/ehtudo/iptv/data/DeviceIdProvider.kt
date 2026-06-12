package app.ehtudo.iptv.data

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import java.util.UUID

/**
 * Returns a stable identifier for this install, used to pin a device to an
 * Xtream subscription. We prefer `Settings.Secure.ANDROID_ID` because it is
 * already stable per app-signing-key + device + user (Android 8+) and we
 * don't have to manage a SharedPreferences entry on first launch.
 *
 * On emulators / pre-O devices `ANDROID_ID` is the well-known placeholder
 * `"9774d56d682e549c"` — that string is reserved and conveys no identity.
 * In that case we fall back to a UUID we persist in our own prefs file the
 * first time it is read. The same UUID is returned on every subsequent
 * read, until the user clears app data.
 *
 * The fallback is intentionally lazy: the UUID is generated only when the
 * system value is missing, so devices that already have a real ANDROID_ID
 * never see the SharedPreferences round-trip.
 */
class DeviceIdProvider(context: Context) {

    private val appContext = context.applicationContext
    private val fallbackPrefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /** Returns the stable device id, or generates + persists a UUID on demand. */
    fun get(): String {
        val systemId = try {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID,
            )
        } catch (e: SecurityException) {
            // ANDROID_ID has been a no-permission read since API 26, but a
            // future profile/permission change could still deny us; treat
            // the same as a missing value.
            null
        }
        if (!systemId.isNullOrBlank() && systemId != PLACEHOLDER_ANDROID_ID) {
            return systemId
        }
        fallbackPrefs.getString(KEY, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        fallbackPrefs.edit { putString(KEY, generated) }
        return generated
    }

    companion object {
        /** Known placeholder `ANDROID_ID` value returned by the emulator image. */
        private const val PLACEHOLDER_ANDROID_ID = "9774d56d682e549c"

        private const val PREFS_NAME = "device_id_prefs"
        private const val KEY = "id"
    }
}
