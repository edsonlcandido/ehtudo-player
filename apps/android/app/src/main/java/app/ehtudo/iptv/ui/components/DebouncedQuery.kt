package app.ehtudo.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

/**
 * Debounced string state used by every in-app search box.
 *
 * Mirrors iOS `.searchable(...)`'s 250 ms debounce so we don't kick off a
 * filter pass on every keypress. The returned `input` reacts immediately
 * (so the TextField stays responsive); the returned `debounced` lags so
 * callers can drive heavy filters off `produceState` instead of doing
 * `.filter` on the UI thread.
 */
@Composable
fun rememberDebouncedQuery(initial: String = "", debounceMs: Long = 250L): DebouncedQuery {
    var input by remember { mutableStateOf(initial) }
    val flow = remember { MutableStateFlow(initial) }
    LaunchedEffect(input) { flow.value = input }
    val debounced by produceState(initialValue = initial, key1 = initial) {
        flow.debounce { q -> if (q.isBlank()) 0L else debounceMs }
            .collect { value = it }
    }
    return DebouncedQuery(input = input, debounced = debounced, set = { input = it })
}

class DebouncedQuery(val input: String, val debounced: String, val set: (String) -> Unit)
