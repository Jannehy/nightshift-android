package com.jannehy.nightshift.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(session: Session, monitor: JobMonitor, onQueued: () -> Unit) {
    var term by remember { mutableStateOf("") }
    var entity by remember { mutableStateOf(SearchEntity.SONG) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val player = remember { PreviewPlayer() }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    fun runSearch() {
        val client = session.api ?: return
        if (term.isBlank()) return
        scope.launch {
            searching = true
            error = null
            try {
                results = client.search(term.trim(), entity)
            } catch (e: ApiException.Unauthorized) {
                session.handleUnauthorized()
            } catch (e: Exception) {
                results = emptyList()
                error = e.message
            } finally {
                searching = false
                searched = true
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.tab_search),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            QueueIndicator(monitor.queue)
        }

        OutlinedTextField(
            value = term,
            onValueChange = { term = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SearchEntity.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = entity == option,
                    onClick = { entity = option; if (term.isNotBlank()) runSearch() },
                    shape = SegmentedButtonDefaults.itemShape(index, SearchEntity.entries.size),
                ) {
                    Text(when (option) {
                        SearchEntity.SONG -> stringResource(R.string.kind_tracks)
                        SearchEntity.ALBUM -> stringResource(R.string.kind_albums)
                        SearchEntity.ARTIST -> stringResource(R.string.kind_artists)
                    })
                }
            }
        }

        Button(onClick = { runSearch() }, enabled = term.isNotBlank() && !searching,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.tab_search))
        }

        error?.let { Spacer(Modifier.height(8.dp)); ErrorBanner(it) { error = null } }

        when {
            searching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(if (searched) R.string.no_results else R.string.search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(results, key = { it.stableId }) { result ->
                    ResultCard(
                        result = result,
                        playing = player.isPlaying(result.preview),
                        onPreview = { player.toggle(result.preview) },
                        onDownload = {
                            player.stop()
                            val client = session.api ?: return@ResultCard
                            monitor.start {
                                if (result.kind == "album" && result.id != null) {
                                    client.downloadAlbum(result.id)
                                } else {
                                    client.downloadFromQuery(result.query)
                                }
                            }
                            onQueued()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: SearchResult,
    playing: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
) {
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                AsyncImage(
                    model = result.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp)),
                )
                if (!result.preview.isNullOrEmpty()) {
                    IconButton(onClick = onPreview, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Icon(
                            if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = null,
                        )
                    }
                }
            }
            Text(result.title, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle(result), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(
                    if (result.kind == "album") R.string.album else R.string.download))
            }
        }
    }
}

@Composable
private fun subtitle(result: SearchResult): String {
    val parts = mutableListOf<String>()
    if (result.kind != "artist") parts += result.artist
    result.durationText?.let { parts += it }
    result.trackCount?.let { parts += stringResource(R.string.tracks_count, it) }
    result.genre?.let { parts += it }
    if (result.kind == "album") result.releaseYear?.let { parts += it }
    return parts.filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Plays the 30-second iTunes previews. One at a time. */
private class PreviewPlayer {
    private var player: MediaPlayer? = null
    private var current by mutableStateOf<String?>(null)

    fun isPlaying(url: String?): Boolean = url != null && url == current

    fun toggle(url: String?) {
        if (url.isNullOrEmpty()) return
        if (current == url) { stop(); return }
        stop()
        player = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stop() }
            prepareAsync()
        }
        current = url
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        current = null
    }
}
