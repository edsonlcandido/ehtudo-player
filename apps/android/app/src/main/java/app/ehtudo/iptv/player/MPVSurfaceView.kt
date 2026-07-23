package app.ehtudo.iptv.player

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose binding that hands a [Surface] to [player] and pulls it back on
 * tear-down. Mirrors iOS `MPVPlayerVideoSurface` (which hosts a
 * MetalLayer fed by `NativeVideoOutput`) but on Android libmpv renders
 * directly into our `Surface`, so there's no intermediate texture
 * pipeline beyond what `TextureView` already does.
 *
 * Uses [TextureView] (not [android.view.SurfaceView]) on purpose: MIUI /
 * HyperOS have a long-standing bug where `SurfaceView` with
 * `setZOrderMediaOverlay(true)` ends up drawing the video surface ABOVE
 * Compose's overlay layer, covering the transport controls. `TextureView`
 * is a regular Android View, so it lives inside the same View tree as
 * the Compose overlay — the Compose UI rendered after it naturally
 * appears on top, with no z-order racing against a separate Surface
 * window.
 *
 * The trade-off is a small GPU round-trip per frame (decoded frames go
 * through SurfaceFlinger into our texture, then into the GL compositor
 * alongside the rest of the View tree). For 1080p H.264 IPTV that's a
 * fraction of a millisecond on the POCO F3 and effectively free.
 */
@Composable
fun MPVSurfaceView(
    player: MPVPlayer,
    modifier: Modifier = Modifier,
) {
    val w by player.videoDisplayWidth.collectAsState()
    val h by player.videoDisplayHeight.collectAsState()

    val aspect = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f

    // Reuse the Surface across config changes — MPV's MediaCodec output
    // buffer queue is tied to a specific Surface, swapping it forces a
    // surface reinit and a ~1s black frame. Keeping one Surface per
    // lifetime of this Composable avoids that.
    val surfaceRef = remember { object { var s: Surface? = null } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            // Repeated recompositions otherwise tear down + recreate the
            // TextureView, which forces libmpv to reinit its VO on every
            // state change. The factory + DisposableEffect pair below
            // already manages attach/detach correctly.
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            texture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val surface = Surface(texture)
                            surfaceRef.s = surface
                            player.attachSurface(surface)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            texture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            // No-op: libmpv reads the window size from the
                            // ANativeWindow on each frame; nothing to push.
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            player.detachSurface()
                            surfaceRef.s?.release()
                            surfaceRef.s = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                            // No-op — frame updates are read by the system
                            // compositor, not by us.
                        }
                    }
                }
            },
            // `fillMaxSize` inside an `aspectRatio` box: aspectRatio sets
            // the container's bounds to match the video's aspect, fillMaxSize
            // makes the TextureView fill those bounds.
            modifier = Modifier
                .aspectRatio(aspect)
                .fillMaxSize(),
        )
    }

    // Belt-and-suspenders cleanup: even if the TextureView's lifecycle
    // somehow leaks the listener (e.g. Compose tree torn down before
    // surfaceTextureDestroyed fires), this ensures libmpv never holds a
    // dangling ANativeWindow.
    DisposableEffect(player) {
        onDispose {
            player.detachSurface()
            surfaceRef.s?.release()
            surfaceRef.s = null
        }
    }
}
