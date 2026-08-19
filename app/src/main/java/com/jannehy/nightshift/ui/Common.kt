package com.jannehy.nightshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.QueueStatus

/** Monospaced live log that sticks to the bottom while new lines arrive. */
@Composable
fun LogView(lines: List<String>, modifier: Modifier = Modifier, height: Dp = 260.dp) {
    val listState = rememberLazyListState()

    // A restored log arrives complete, so the interesting end would otherwise
    // sit far below the fold.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = logColour(line),
            )
        }
    }
}

@Composable
private fun logColour(line: String): Color {
    val lower = line.lowercase()
    return when {
        line.startsWith("=== DONE") || line.startsWith("✓") || lower.contains("downloaded") ->
            Color(0xFF4CAF50)
        line.startsWith("=== FAILED") || lower.contains("error") || lower.contains("failed") ->
            MaterialTheme.colorScheme.error
        line.startsWith("===") || line.startsWith("━") ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** Progress, status line and queue position of a running job. */
@Composable
fun JobStatusView(monitor: JobMonitor) {
    val state = monitor.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is JobMonitor.State.Idle ->
                    Icon(Icons.Default.RadioButtonUnchecked, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                is JobMonitor.State.Queued ->
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.tertiary)
                is JobMonitor.State.Running ->
                    Icon(Icons.Default.Downloading, null, tint = MaterialTheme.colorScheme.primary)
                is JobMonitor.State.Finished ->
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                is JobMonitor.State.Failed ->
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            }
            Text(headline(state), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f))
            monitor.trackCounter?.let {
                Text(it, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.isBusy) {
            val progress = monitor.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        monitor.statusMessage?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        monitor.queue?.pending?.takeIf { it.isNotEmpty() }?.let {
            Text(stringResource(R.string.jobs_waiting, it.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun headline(state: JobMonitor.State): String = when (state) {
    is JobMonitor.State.Idle -> stringResource(R.string.idle)
    is JobMonitor.State.Queued ->
        if (state.position > 0) stringResource(R.string.queued_ahead, state.position)
        else stringResource(R.string.starting)
    is JobMonitor.State.Running -> stringResource(R.string.running)
    is JobMonitor.State.Finished ->
        state.message.ifEmpty { stringResource(R.string.done) }
    is JobMonitor.State.Failed ->
        state.message.ifEmpty { stringResource(R.string.failed) }
}

/** Inline error strip – used where a dialog would interrupt too much. */
@Composable
fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f))
        onDismiss?.let {
            IconButton(onClick = it, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Compact queue readout for the top bar. */
@Composable
fun QueueIndicator(queue: QueueStatus?) {
    if (queue == null || queue.isEmpty) return
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(end = 12.dp)) {
        Icon(Icons.Default.FormatListNumbered, null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${queue.count}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}
