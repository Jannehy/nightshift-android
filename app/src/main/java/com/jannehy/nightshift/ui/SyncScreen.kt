package com.jannehy.nightshift.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.*
import kotlinx.coroutines.launch

/** Playlists the nightly job keeps up to date – the sync registry plus
 *  spotDL's own sync files. */
@Composable
fun SyncScreen(session: Session) {
    var items by remember { mutableStateOf<List<SyncItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SyncItem?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val client = session.api ?: return
        loading = true
        try {
            items = client.syncPlaylists().entries
        } catch (e: ApiException.Unauthorized) {
            session.handleUnauthorized()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.tab_sync),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { load() } }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
            }
        }

        error?.let { ErrorBanner(it) { error = null } }

        when {
            loading && items.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Default.Sync, null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.nothing_in_sync),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.nothing_in_sync_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                val grouped = items.groupBy { it.source }
                    .toSortedMap(compareBy { SOURCE_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 })
                grouped.forEach { (_, group) ->
                    item {
                        Text(group.first().sourceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    items(group.sortedBy { it.name.lowercase() }, key = { it.id }) { entry ->
                        SyncRow(
                            item = entry,
                            isAdmin = session.isAdmin,
                            onEdit = { editing = entry },
                            onRemove = {
                                scope.launch {
                                    runCatching {
                                        session.api?.removeSyncItem(entry.url, entry.file)
                                    }.onSuccess { items = items.filterNot { it.id == entry.id } }
                                        .onFailure { error = it.message }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        SyncEditDialog(
            session = session,
            item = item,
            onDismiss = { editing = null },
            onSave = { owner, isPublic ->
                scope.launch {
                    runCatching {
                        session.api?.updateSyncMeta(item.url, item.file, owner, isPublic)
                    }.onFailure { error = it.message }
                    editing = null
                    load()
                }
            },
        )
    }
}

private val SOURCE_ORDER = listOf("spotify", "soundcloud", "youtube")

@Composable
private fun SyncRow(item: SyncItem, isAdmin: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (item.source) {
                    "soundcloud" -> Icons.Default.Cloud
                    "youtube" -> Icons.Default.PlayCircle
                    else -> Icons.Default.QueueMusic
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.owner?.takeIf { it.isNotEmpty() }
                        ?: stringResource(
                            if (item.isPublic) R.string.visibility_public
                            else R.string.visibility_private),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isAdmin) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Tune, null) }
            }
            if (item.canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SyncEditDialog(
    session: Session,
    item: SyncItem,
    onDismiss: () -> Unit,
    onSave: (String?, Boolean) -> Unit,
) {
    var isPublic by remember { mutableStateOf(item.isPublic) }
    var owner by remember { mutableStateOf(item.owner.orEmpty()) }
    var users by remember { mutableStateOf<List<NightshiftUser>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        users = runCatching { session.api?.users() }.getOrNull().orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.visibility_public), Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                Text(stringResource(R.string.visibility_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Box {
                    OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                        Text("${stringResource(R.string.owner)}: " +
                            owner.ifEmpty { stringResource(R.string.none) })
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.none)) },
                            onClick = { owner = ""; expanded = false })
                        users.forEach { user ->
                            DropdownMenuItem(
                                text = { Text(user.username) },
                                onClick = { owner = user.username; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(owner.ifEmpty { null }, isPublic) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
