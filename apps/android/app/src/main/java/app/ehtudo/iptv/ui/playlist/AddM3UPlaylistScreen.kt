package app.ehtudo.iptv.ui.playlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ehtudo.iptv.AnotherIptvPlayerApp
import app.ehtudo.iptv.R
import androidx.compose.ui.res.stringResource
import app.ehtudo.iptv.model.Playlist
import app.ehtudo.iptv.model.PlaylistKind
import app.ehtudo.iptv.networking.M3UParser
import app.ehtudo.iptv.networking.M3UService
import app.ehtudo.iptv.networking.M3UServiceException
import app.ehtudo.iptv.networking.M3UParserException
import app.ehtudo.iptv.ui.LocalPlaylistRepository
import app.ehtudo.iptv.ui.components.ModalSlideContainer
import app.ehtudo.iptv.ui.components.SavingOverlay
import app.ehtudo.iptv.ui.components.SectionHeader
import app.ehtudo.iptv.ui.theme.AnotherIptvPlayerTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Form for adding an M3U / M3U8 playlist — the Android counterpart of the
 * iOS `AddM3UPlaylistView`. The source can be a remote URL or a local file;
 * the two are mutually exclusive. Import is mocked for now (no parsing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddM3UPlaylistScreen(
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    editingPlaylistId: String? = null,
) {
    val repository = LocalPlaylistRepository.current
    var editing by remember(editingPlaylistId) { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(editingPlaylistId) {
        editing = editingPlaylistId?.let { repository.find(it) }
    }

    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var url by remember(editing?.id) { mutableStateOf(editing?.serverUrl ?: "") }
    var localFileName by remember { mutableStateOf<String?>(null) }
    var localFileUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val msgImporting = stringResource(R.string.add_m3u_importing)
    val msgReadingFile = stringResource(R.string.add_m3u_reading_file)
    val msgConnecting = stringResource(R.string.add_m3u_connecting)
    val msgParsing = stringResource(R.string.add_m3u_parsing)
    val msgInvalidUrl = stringResource(R.string.add_m3u_invalid_url)
    val msgFileReadFailed = stringResource(R.string.add_m3u_file_read_failed)
    val msgEmpty = stringResource(R.string.add_m3u_empty)
    val msgNoChannels = stringResource(R.string.add_m3u_no_channels)
    val msgNetworkErrorFmt = stringResource(R.string.add_m3u_network_error)
    val msgServerErrorFmt = stringResource(R.string.add_m3u_server_error)
    val msgSavingFmt = stringResource(R.string.add_m3u_saving_with_count)
    val channelFallback = stringResource(R.string.m3u_channel_fallback)
    val defaultFilename = stringResource(R.string.m3u_default_filename)
    var importStatus by remember { mutableStateOf(msgImporting) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val app = context.applicationContext as AnotherIptvPlayerApp
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val hasLocalFile = localFileUri != null

    // SAF picker. `OpenDocument` (vs. GetContent) gives us a persistable Uri
    // we could later renew on each launch; for now we read the bytes immediately.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            localFileUri = uri
            localFileName = fileDisplayName(context, uri, defaultFilename)
        }
    }

    val isValid = name.isNotBlank() && (hasLocalFile || url.isNotBlank())

    fun save() {
        if (!isValid || isSaving) return
        focusManager.clearFocus()
        errorMessage = null
        isSaving = true
        importStatus = if (hasLocalFile) msgReadingFile else msgConnecting
        scope.launch {
            try {
                val service = M3UService(contentResolver = context.contentResolver)
                val rawText = if (hasLocalFile) {
                    service.readLocal(localFileUri!!)
                } else {
                    service.fetchRemote(url.trim())
                }
                importStatus = msgParsing
                val parsed = M3UParser.parseAsync(rawText, channelFallback)

                val current = editing
                val playlist = Playlist.create(
                    id = current?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    serverUrl = if (hasLocalFile) "" else url.trim(),
                    kind = PlaylistKind.M3U,
                    m3uEpgUrl = parsed.epgUrl,
                    createdAt = current?.createdAt ?: System.currentTimeMillis(),
                )
                if (current != null) {
                    repository.update(playlist)
                } else {
                    repository.add(playlist)
                }
                importStatus = msgSavingFmt.format(parsed.channels.size)
                app.m3uImporter.replace(
                    playlist = playlist,
                    channels = parsed.channels,
                    epgUrl = parsed.epgUrl,
                    clearServerUrl = hasLocalFile,
                )
                isSaving = false
                onSaved(playlist.id)
            } catch (t: Throwable) {
                isSaving = false
                errorMessage = when (t) {
                    is M3UServiceException.InvalidUrl -> msgInvalidUrl
                    is M3UServiceException.Network -> msgNetworkErrorFmt.format(t.message.orEmpty())
                    is M3UServiceException.Server -> msgServerErrorFmt.format(t.code)
                    is M3UServiceException.FileRead -> msgFileReadFailed
                    is M3UParserException.Empty -> msgEmpty
                    is M3UParserException.NoChannelsFound -> msgNoChannels
                    else -> t.message ?: t.javaClass.simpleName
                }
            }
        }
    }

    ModalSlideContainer {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (editing != null) stringResource(R.string.m3u_screen_title_edit)
                            else stringResource(R.string.m3u_screen_title_new)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel, enabled = !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.m3u_close_cd))
                        }
                    },
                    actions = {
                        TextButton(onClick = ::save, enabled = isValid && !isSaving) {
                            Text(stringResource(R.string.common_save))
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader(stringResource(R.string.m3u_section_info))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )

                Spacer(Modifier.height(4.dp))
                SectionHeader(stringResource(R.string.m3u_section_source))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.m3u_field_url)) },
                    placeholder = { Text(stringResource(R.string.m3u_url_placeholder)) },
                    singleLine = true,
                    enabled = !hasLocalFile,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )

                OrDivider()

                if (hasLocalFile) {
                    SelectedFileCard(
                        fileName = localFileName.orEmpty(),
                        onRemove = {
                            localFileName = null
                            localFileUri = null
                        },
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            // `OpenDocument` filters by MIME, not extension —
                            // we accept anything text-like so providers serving
                            // M3U as `application/octet-stream` still pass.
                            filePicker.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.m3u_choose_file))
                    }
                }

                errorMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.m3u_form_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (isSaving) {
            SavingOverlay(importStatus)
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.common_or),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SelectedFileCard(fileName: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.m3u_selected_file),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.m3u_remove_file_cd))
            }
        }
    }
}

/** Resolves a human-readable file name from a content [Uri]. */
private fun fileDisplayName(context: Context, uri: Uri, defaultFilename: String): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: defaultFilename
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(index)
                }
            }
    }
    return name
}

@Preview(showBackground = true)
@Composable
private fun AddM3UPlaylistScreenPreview() {
    AnotherIptvPlayerTheme {
        CompositionLocalProvider(
            LocalPlaylistRepository provides PreviewPlaylistRepository(),
        ) {
            AddM3UPlaylistScreen(onCancel = {}, onSaved = { })
        }
    }
}
