package app.ehtudo.iptv.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ehtudo.iptv.R
import app.ehtudo.iptv.player.LocalPlayerActivityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Compose port of iOS `PlayerControlCenterStyleEdgeSliders` +
 * `PlayerMediaKitStyleTouchOverlay`.
 *
 * Vertical drag on the left half adjusts brightness via the Activity window's
 * `screenBrightness` attribute (the system-level brightness override iOS uses
 * `UIScreen.main.brightness` for); drag on the right half adjusts volume via
 * `AudioManager.STREAM_MUSIC`. Long-press anywhere triggers 2× playback speed
 * for the duration of the press.
 *
 * A floating chip in the centre shows the current value during the gesture.
 *
 * Long-press is implemented with a manual [awaitEachGesture] loop, NOT
 * `detectTapGestures`: the built-in helper consumes pointer events whenever
 * it sees a `pointer-down`, even for short taps that never trigger
 * `onLongPress`. That meant every short tap on the player's -10s / +10s /
 * play buttons and on the seek slider was being eaten by this layer, with
 * the composables below never receiving the click. The custom loop only
 * "consumes" the gesture once it's actually a long press (or a drag that
 * flipped ownership to the brightness/volume handler), leaving plain taps
 * free to fall through to the controls in [PlayerOverlay].
 */
@Composable
fun PlayerGestureLayer(
    onSetPlaybackRate: (Double) -> Unit,
    modifier: Modifier = Modifier,
    speedUpRate: Double = 2.0,
    normalRate: Double = 1.0,
) {
    val context = LocalContext.current
    val activityState = LocalPlayerActivityState.current
    val scope = rememberCoroutineScope()

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume,
        )
    }
    var brightness by remember {
        mutableFloatStateOf(activityState.brightnessOverride.value ?: 0.5f)
    }

    // Toast-style overlay state: which slider is active + the value.
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(700)
            feedback = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Two invisible vertical-drag zones — left half = brightness, right
        // half = volume. `detectVerticalDragGestures` only claims events
        // when the pointer actually drags vertically past the touch slop,
        // so taps on the buttons / slider below pass straight through.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(maxVolume) {
                        detectVerticalDragGestures { _, delta ->
                            // -delta because a downward drag should decrease.
                            brightness = (brightness - delta / size.height.toFloat()).coerceIn(0f, 1f)
                            activityState.brightnessOverride.value = brightness
                            feedback = GestureFeedback.Brightness(brightness)
                        }
                    }
                    .pointerInput(speedUpRate, normalRate) {
                        awaitLongPressOrRelease(
                            scope = scope,
                            speedUpRate = speedUpRate,
                            normalRate = normalRate,
                            onSpeedUp = {
                                onSetPlaybackRate(speedUpRate)
                                feedback = GestureFeedback.Speed(speedUpRate)
                            },
                            onRelease = {
                                onSetPlaybackRate(normalRate)
                            },
                        )
                    },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(maxVolume) {
                        detectVerticalDragGestures { _, delta ->
                            volume = (volume - delta / size.height.toFloat()).coerceIn(0f, 1f)
                            val target = (volume * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                target,
                                0,
                            )
                            feedback = GestureFeedback.Volume(volume)
                        }
                    }
                    .pointerInput(speedUpRate, normalRate) {
                        awaitLongPressOrRelease(
                            scope = scope,
                            speedUpRate = speedUpRate,
                            normalRate = normalRate,
                            onSpeedUp = {
                                onSetPlaybackRate(speedUpRate)
                                feedback = GestureFeedback.Speed(speedUpRate)
                            },
                            onRelease = {
                                onSetPlaybackRate(normalRate)
                            },
                        )
                    },
            )
        }

        feedback?.let { fb ->
            FeedbackChip(fb, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Long-press detector that does NOT consume short taps.
 *
 * Behaviour:
 * - `pointer-down`: arm a delayed `onSpeedUp` callback for
 *   `viewConfiguration.longPressTimeoutMillis` from now.
 * - `pointer-up` before the deadline: cancel the long-press timer and fire
 *   `onRelease`. Compose never marks the down event as consumed, so the
 *   buttons / slider below receive the tap as if this layer weren't here.
 * - `pointer-move` past the vertical touch slop: cancel the long-press
 *   timer and bail without consuming. The sibling
 *   `detectVerticalDragGestures` modifier then takes over the drag.
 * - Long-press fires: stay in a release loop until the pointer lifts, then
 *   fire `onRelease` to drop back to normal speed.
 *
 * Hosted on the same composable as the brightness/volume drag handlers so
 * the long-press gesture is available on the same surface — but it never
 * fights them for taps.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitLongPressOrRelease(
    scope: kotlinx.coroutines.CoroutineScope,
    speedUpRate: Double,
    normalRate: Double,
    onSpeedUp: () -> Unit,
    onRelease: () -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        // requireUnconsumed = true: skip downs that the IconButton / Slider
        // below us already claimed. That keeps a long-press on a button from
        // accidentally triggering speed-up (the button's clickable cancels
        // its onClick on long press, but our detector would otherwise have
        // happily fired `onSpeedUp()` 500 ms later with no button feedback
        // to compensate — confusing UX).
        val down = awaitFirstDown(requireUnconsumed = true)
        var longPressFired = false

        val longPressJob = scope.launch {
            delay(longPressTimeout)
            longPressFired = true
            onSpeedUp()
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    // Released before long-press fired (or after — handled
                    // by the inner loop below). Plain tap case doesn't
                    // consume, so the underlying IconButton / Slider fires.
                    break
                }
                if (change.positionChange().y.absoluteValue > touchSlop) {
                    // Became a vertical drag — the sibling
                    // detectVerticalDragGestures owns it now. Don't fire
                    // long-press and don't block the drag.
                    break
                }
                if (longPressFired) {
                    // Long-press already fired; wait until the user lets
                    // go so we can drop the playback rate back to normal.
                    while (true) {
                        val releaseEvent = awaitPointerEvent()
                        val releaseChange = releaseEvent.changes
                            .firstOrNull { it.id == down.id }
                            ?: break
                        if (!releaseChange.pressed) break
                    }
                    break
                }
            }
        } finally {
            longPressJob.cancel()
        }

        // Always drop back to the normal rate once the gesture ends —
        // symmetric with the previous `detectTapGestures.onPress` reset.
        onRelease()
    }
}

private sealed class GestureFeedback {
    data class Brightness(val value: Float) : GestureFeedback()
    data class Volume(val value: Float) : GestureFeedback()
    data class Speed(val rate: Double) : GestureFeedback()
}

@Composable
private fun FeedbackChip(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC1F1F1F),
        modifier = modifier,
    ) {
        when (feedback) {
            is GestureFeedback.Brightness -> SliderRow(
                icon = Icons.Default.BrightnessHigh,
                label = stringResource(R.string.player_brightness),
                fraction = feedback.value,
            )
            is GestureFeedback.Volume -> SliderRow(
                icon = Icons.Default.VolumeUp,
                label = stringResource(R.string.player_volume),
                fraction = feedback.value,
            )
            is GestureFeedback.Speed -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⏩  ${feedback.rate}×", color = Color.White)
            }
        }
    }
}

@Composable
private fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fraction: Float,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("  $label", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x55FFFFFF)),
        )
    }
}
