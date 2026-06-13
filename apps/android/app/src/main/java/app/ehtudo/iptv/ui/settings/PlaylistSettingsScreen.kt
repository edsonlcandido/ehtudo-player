package app.ehtudo.iptv.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ehtudo.iptv.R
import app.ehtudo.iptv.data.PlaylistContentStore
import app.ehtudo.iptv.model.Playlist
import app.ehtudo.iptv.networking.XtreamApiClient
import app.ehtudo.iptv.networking.XtreamAuthResponse
import app.ehtudo.iptv.ui.LocalDeviceIdProvider
import app.ehtudo.iptv.ui.LocalPlaylistContentStore
import app.ehtudo.iptv.ui.LocalPlaylistRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Settings tab body — Kotlin port of iOS `PlaylistSettingsView`, scoped to
 * what the Android app already has wired. For the Eh!Iptv single-tenant
 * build the playlist is auto-created with a fixed server URL, so the only
 * user-editable bits are the credentials. The card layout keeps the
 * original structure (sync, subscription, stats, content, player,
 * library, about) but the "Playlist Info" card is replaced with editable
 * credentials + a device id block, and the legacy server-timezone card
 * is dropped.
 *
 * Designed to render inside the dashboard's HorizontalPager — therefore
 * has no top bar / Scaffold of its own; padding is owned by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSettingsBody(
    playlistId: String,
    appVersion: String,
    onOpenDownloads: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playlistRepository = LocalPlaylistRepository.current
    val contentStore = LocalPlaylistContentStore.current
    val deviceIdProvider = LocalDeviceIdProvider.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deviceId = remember { deviceIdProvider.get() }
    val deviceIdCopiedMessage = stringResource(R.string.config_device_id_copied)

    var playlist by remember(playlistId) { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(playlistId) {
        playlist = playlistRepository.find(playlistId)
    }

    var authResponse by remember(playlistId) { mutableStateOf<XtreamAuthResponse?>(null) }
    var isLoadingAuth by remember(playlistId) { mutableStateOf(true) }
    var authError by remember(playlistId) { mutableStateOf<String?>(null) }

    var stats by remember(playlistId) { mutableStateOf<PlaylistContentStore.CatalogStats?>(null) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }

    // Editable credentials — the original iOS screen keeps them as
    // read-only InfoRows, but for the Eh!Iptv build the user has to
    // supply them, so they live in text fields that drive the Save
    // button at the bottom of the credentials card.
    var username by remember(playlistId) { mutableStateOf("") }
    var password by remember(playlistId) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var fieldsInitialized by remember(playlistId) { mutableStateOf(false) }
    // Editable copy of the playlist row; updated after a successful save.
    var savedUsername by remember(playlistId) { mutableStateOf("") }
    var savedPassword by remember(playlistId) { mutableStateOf("") }
    LaunchedEffect(playlist?.id) {
        if (playlist != null && !fieldsInitialized) {
            username = playlist!!.username
            password = playlist!!.password
            savedUsername = playlist!!.username
            savedPassword = playlist!!.password
            fieldsInitialized = true
        }
    }
    val credentialsDirty = username.trim() != savedUsername.trim() ||
        password != savedPassword
    val credentialsValid = username.trim().isNotEmpty() && password.isNotEmpty()
    val credentialsSavable = credentialsDirty && credentialsValid && !isSyncing

    // Tracks a successful save so the user gets a snackbar confirmation.
    var showSavedSnackbar by remember { mutableStateOf(false) }

    suspend fun reloadStats() {
        stats = contentStore.fetchStats(playlistId)
    }

    suspend fun reloadAuth() {
        val pl = playlist ?: return
        isLoadingAuth = true
        authError = null
        runCatching { XtreamApiClient(pl).verify() }
            .onSuccess { authResponse = it }
            .onFailure { authError = it.message }
        isLoadingAuth = false
    }

    // Initial load: stats + auth.
    LaunchedEffect(playlistId, playlist?.id) {
        if (playlist == null) return@LaunchedEffect
        reloadStats()
        reloadAuth()
    }

    fun saveCredentials() {
        val current = playlist ?: return
        if (!credentialsSavable) return
        focusManager.clearFocus()
        scope.launch {
            isSyncing = true
            syncError = null
            val updated = current.copy(
                username = username.trim(),
                password = password,
            )
            try {
                val response = runCatching { XtreamApiClient(updated).verify() }
                    .getOrElse {
                        syncError = it.message
                        isSyncing = false
                        return@launch
                    }
                if (response.userInfo?.auth != 1) {
                    syncError = context.getString(R.string.config_auth_failed)
                    isSyncing = false
                    return@launch
                }
                playlistRepository.update(updated)
                playlist = updated
                savedUsername = updated.username
                savedPassword = updated.password
                contentStore.syncFromNetworkReplacingLocal(updated) { msg ->
                    syncMessage = msg
                }
                reloadStats()
                contentStore.reloadFromDatabaseIfActive(updated.id)
                authResponse = response
                authError = null
                showSavedSnackbar = true
            } finally {
                isSyncing = false
                syncMessage = null
            }
        }
    }

    if (playlist == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }
    val pl = playlist!!

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero logo — the first thing the user sees on the
            // configuration tab.
            Image(
                painter = painterResource(R.drawable.ic_ehiptv_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .padding(top = 8.dp),
            )

            // Sync section.
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_re_download)) },
                    supportingContent = {
                        if (isSyncing && syncMessage != null) {
                            Text(
                                text = syncMessage!!,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (syncError != null) {
                            Text(
                                text = syncError!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(stringResource(R.string.settings_re_download_subtitle))
                        }
                    },
                    trailingContent = {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    },
                    colors = clickableListItemColors(),
                    modifier = Modifier.clickableEnabled(!isSyncing) {
                        scope.launch {
                            isSyncing = true
                            syncError = null
                            try {
                                contentStore.syncFromNetworkReplacingLocal(pl) { msg ->
                                    syncMessage = msg
                                }
                                reloadStats()
                                contentStore.reloadFromDatabaseIfActive(pl.id)
                            } catch (e: Throwable) {
                                syncError = e.message
                            } finally {
                                isSyncing = false
                                syncMessage = null
                            }
                        }
                    },
                )
            }

            // Editable credentials. The server URL is intentionally not
            // exposed — the auto-created playlist already points at
            // AppConfig.SERVER_URL.
            SettingsCard(title = stringResource(R.string.config_section_credentials)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.config_field_username)) },
                    singleLine = true,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.config_field_password)) },
                    singleLine = true,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { saveCredentials() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.config_password_hide)
                                } else {
                                    stringResource(R.string.config_password_show)
                                },
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = ::saveCredentials,
                        enabled = credentialsSavable,
                    ) {
                        Text(stringResource(R.string.config_save))
                    }
                }
            }

            // Device ID — readable + copyable, for support / pairing.
            SettingsCard(title = stringResource(R.string.config_device_id_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.config_device_id_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = deviceId,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("device_id", deviceId))
                            scope.launch { snackbarHostState.showSnackbar(deviceIdCopiedMessage) }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.config_device_id_copy_cd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Subscription section.
            SettingsCard(title = stringResource(R.string.settings_card_subscription)) {
                when {
                    isLoadingAuth -> CenteredHint(stringResource(R.string.settings_loading_info))
                    authError != null -> CenteredHint(
                        text = authError ?: stringResource(R.string.settings_unknown_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    authResponse?.userInfo != null -> {
                        val user = authResponse!!.userInfo!!
                        InfoRow(label = stringResource(R.string.settings_subscription_status), value = remainingDaysText(LocalContext.current, user.expDate))
                        Divider()
                        InfoRow(
                            label = stringResource(R.string.settings_active_connection),
                            value = user.activeCons ?: "—",
                        )
                        Divider()
                        InfoRow(
                            label = stringResource(R.string.settings_max_connection),
                            value = user.maxConnections.takeUnless { it.isNullOrEmpty() } ?: stringResource(R.string.settings_unlimited),
                        )
                    }
                    else -> CenteredHint(stringResource(R.string.settings_subscription_unknown))
                }
            }

            // Stats section.
            SettingsCard(title = stringResource(R.string.settings_card_stats)) {
                val s = stats
                if (s == null) {
                    CenteredHint(stringResource(R.string.common_loading))
                } else {
                    InfoRow(label = stringResource(R.string.settings_live_count), value = s.liveCount.toString())
                    Divider()
                    InfoRow(label = stringResource(R.string.settings_vod_count), value = s.vodCount.toString())
                    Divider()
                    InfoRow(label = stringResource(R.string.settings_series_count), value = s.seriesCount.toString())
                }
            }

            // Content management.
            SettingsCard(title = stringResource(R.string.settings_card_content)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_adult_filter)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_adult_filter_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            enabled = !isSyncing,
                            checked = pl.filterAdultContent,
                            onCheckedChange = { newValue ->
                                scope.launch {
                                    // Persist the flag first, then re-sync so
                                    // categories/streams reflect the new filter.
                                    val updated = pl.copy(filterAdultContent = newValue)
                                    playlistRepository.update(updated)
                                    playlist = updated
                                    isSyncing = true
                                    syncError = null
                                    try {
                                        contentStore.syncFromNetworkReplacingLocal(updated) { msg ->
                                            syncMessage = msg
                                        }
                                        reloadStats()
                                        contentStore.reloadFromDatabaseIfActive(updated.id)
                                    } catch (e: Throwable) {
                                        syncError = e.message
                                    } finally {
                                        isSyncing = false
                                        syncMessage = null
                                    }
                                }
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }

            // Player preferences — PiP, background playback, 2× long-press.
            // iOS registers these as defaults at app init; we mirror them
            // here with one source-of-truth `PlayerPreferences`.
            SettingsCard(title = stringResource(R.string.settings_card_player)) {
                val playerPrefs = app.ehtudo.iptv.ui.LocalPlayerPreferences.current
                val pip by playerPrefs.pipEnabled.collectAsState()
                val bg by playerPrefs.continuePlayingInBackground.collectAsState()
                val longPress by playerPrefs.speedUpOnLongPress.collectAsState()

                SwitchRow(
                    title = stringResource(R.string.settings_player_pip),
                    subtitle = stringResource(R.string.settings_player_pip_subtitle),
                    checked = pip,
                    onChange = { playerPrefs.setPipEnabled(it) },
                )
                Divider()
                SwitchRow(
                    title = stringResource(R.string.settings_player_bg),
                    subtitle = stringResource(R.string.settings_player_bg_subtitle),
                    checked = bg,
                    onChange = { playerPrefs.setContinuePlayingInBackground(it) },
                )
                Divider()
                SwitchRow(
                    title = stringResource(R.string.settings_player_long_press),
                    subtitle = stringResource(R.string.settings_player_long_press_subtitle),
                    checked = longPress,
                    onChange = { playerPrefs.setSpeedUpOnLongPress(it) },
                )
            }

            // Library — Downloads + Watch History entry points. iOS surfaces
            // these on the dashboard side bar; the Android dashboard funnels
            // them through Settings so the top app bar stays uncluttered.
            SettingsCard(title = stringResource(R.string.settings_card_library)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_library_downloads)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_library_downloads_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    colors = clickableListItemColors(),
                    modifier = Modifier.clickableEnabled(true) { onOpenDownloads() },
                )
                Divider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_library_history)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_library_history_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    colors = clickableListItemColors(),
                    modifier = Modifier.clickableEnabled(true) { onOpenHistory() },
                )
            }

            // About.
            SettingsCard(title = stringResource(R.string.settings_card_about)) {
                InfoRow(label = stringResource(R.string.settings_version), value = appVersion)
                Divider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.config_about_url_label)) },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.config_about_url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    colors = clickableListItemColors(),
                    modifier = Modifier.clickableEnabled(true) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(context.getString(R.string.config_about_url)),
                                ),
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Show the "saved" snackbar outside the Scaffold so it floats over
    // the entire screen — including the dashboard content underneath.
    LaunchedEffect(showSavedSnackbar) {
        if (showSavedSnackbar) {
            snackbarHostState.showSnackbar(context.getString(R.string.config_saved_ok))
            showSavedSnackbar = false
        }
    }
}

/** Calculates remaining-days text from a UNIX-seconds string. iOS parity. */
private fun remainingDaysText(context: android.content.Context, expDateRaw: String?): String {
    val ts = expDateRaw?.toLongOrNull()
        ?: return context.getString(R.string.settings_unlimited_or_unknown)
    if (ts == 0L) return context.getString(R.string.settings_unlimited)
    val expiry = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, expiry).toInt()
    return when {
        days < 0 -> context.getString(R.string.settings_expired)
        days == 0 -> context.getString(R.string.settings_expires_today)
        else -> context.getString(R.string.settings_days_left, days)
    }
}

// ---- Small layout helpers ----

@Composable
private fun SettingsCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun CenteredHint(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun clickableListItemColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface,
)

private fun Modifier.clickableEnabled(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable(onClick = onClick) else this
