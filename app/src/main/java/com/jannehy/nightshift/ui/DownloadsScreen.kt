package com.jannehy.nightshift.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.CookieStatus
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.NavidromeUser
import com.jannehy.nightshift.core.Session
import kotlinx.coroutines.launch

private val SUPPORTED = listOf("spotify.com", "soundcloud.com", "youtube.com", "youtu.be")

@Composable
fun DownloadsScreen(session: Session, monitor: JobMonitor) {
    var link by remember { mutableStateOf("") }
    var keepInSync by remember { mutableStateOf(false) }
    var ownerId by remember { mutableStateOf<String?>(null) }
    var ndUsers by remember { mutableStateOf<List<NavidromeUser>>(emptyList()) }
    var cookieWarnings by remember { mutableStateOf<List<CookieStatus>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Cheap and cached on the server side, so it can run on every visit.
    LaunchedEffect(Unit) {
        runCatching { session.api?.cookieStatus() }.getOrNull()?.let {
            cookieWarnings = it.cookies.filter { entry -> entry.needsAttention }
        }
    }

    LaunchedEffect(session.navidromeEnabled) {
        if (session.navidromeEnabled && ndUsers.isEmpty()) {
            runCatching { session.api?.navidromeUsers() }.getOrNull()
                ?.takeIf { it.enabled }?.let { ndUsers = it.users }
        }
    }

    val supported = SUPPORTED.any { link.lowercase().contains(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_downloads),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f))
            QueueIndicator(monitor.queue)
        }

        CookieBanner(cookieWarnings)

        SectionCard {
            Text(stringResource(R.string.paste_link),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboardText(context)?.let { link = it } }) {
                    Icon(Icons.Default.ContentPaste, contentDescription =
                        stringResource(R.string.paste_from_clipboard))
                }
            }

            if (link.isNotEmpty() && !supported) {
                Text(stringResource(R.string.unsupported_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            if (session.syncEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.keep_in_sync))
                        Text(stringResource(R.string.keep_in_sync_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = keepInSync, onCheckedChange = { keepInSync = it })
                }
            }

            if (session.navidromeEnabled && ndUsers.isNotEmpty()) {
                OwnerPicker(ndUsers, ownerId) { ownerId = it }
            }

            Button(
                onClick = {
                    val target = link.trim()
                    val owner = ownerId
                    val sync = keepInSync && session.syncEnabled
                    monitor.start {
                        session.api!!.startDownload(target, owner, sync)
                    }
                    link = ""
                },
                enabled = supported && !monitor.state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.start_download)) }
        }

        if (monitor.state != JobMonitor.State.Idle || monitor.lines.isNotEmpty()) {
            SectionCard {
                JobStatusView(monitor)
                ConsoleView(monitor.lines, isRunning = monitor.state.isBusy)
            }
        }

        monitor.errorMessage?.let {
            ErrorBanner(it) { monitor.errorMessage = null }
        }
    }
}

@Composable
private fun OwnerPicker(users: List<NavidromeUser>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = users.firstOrNull { it.id == selected }
        ?.let { it.name.ifEmpty { it.userName } }
        ?: stringResource(R.string.visibility_public)

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${stringResource(R.string.playlist_owner)}: $label")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.visibility_public)) },
                onClick = { onSelect(null); expanded = false },
            )
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.name.ifEmpty { user.userName }) },
                    onClick = { onSelect(user.id); expanded = false },
                )
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return manager?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString()
}
