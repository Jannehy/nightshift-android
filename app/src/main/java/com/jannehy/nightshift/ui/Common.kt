package com.jannehy.nightshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.CookieStatus
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.QueueStatus

/**
 * How a log line reads at a glance.
 *
 * A track the catalogue does not have is an outcome, not a failure; counting it
 * as an error made a finished run look broken. The cases match the web
 * interface, so the same run does not describe itself differently depending on
 * where it is watched.
 */
enum class LogKind { OK, WARNING, ERROR, PLAIN }

fun logKindOf(line: String): LogKind {
    val lower = line.lowercase()
    return when {
        line.startsWith("=== DONE") || line.startsWith("✓") -> LogKind.OK
        lower.contains("no results found") || lower.contains("lookuperror") ||
            lower.contains("could not be downloaded") -> LogKind.WARNING
        line.startsWith("=== FAILED") || line.contains("✗") || line.contains("⚠") ||
            lower.contains("error") || lower.contains("failed") -> LogKind.ERROR
        lower.contains("downloaded") -> LogKind.OK
        else -> LogKind.PLAIN
    }
}

/**
 * The log, folded away.
 *
 * While something runs, two questions matter: is it still going, and did
 * anything go wrong. The header answers both. The lines are for whoever wants
 * them, and start hidden - on a phone they otherwise fill the screen.
 */
@Composable
fun ConsoleView(
    lines: List<String>,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    var open by remember { mutableStateOf(false) }
    val errors = lines.count { logKindOf(it) == LogKind.ERROR }
    val missing = lines.count { logKindOf(it) == LogKind.WARNING }

    val summary = buildList {
        add(stringResource(if (isRunning) R.string.console_running else R.string.console_done))
        if (errors > 0) add(pluralOrOne(errors, R.string.console_error_one, R.string.console_error_many))
        if (missing > 0) add(pluralOrOne(missing, R.string.console_missing_one, R.string.console_missing_many))
    }.joinToString(" · ")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.console_title), style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    errors > 0 -> MaterialTheme.colorScheme.error
                    missing > 0 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                lines.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            LogView(lines, height = height)
        }
    }
}

@Composable
private fun pluralOrOne(count: Int, one: Int, many: Int): String =
    if (count == 1) stringResource(one) else stringResource(many, count)

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
private fun logColour(line: String): Color = when (logKindOf(line)) {
    LogKind.OK -> Color(0xFF4CAF50)
    LogKind.WARNING -> MaterialTheme.colorScheme.tertiary
    LogKind.ERROR -> MaterialTheme.colorScheme.error
    LogKind.PLAIN ->
        if (line.startsWith("===") || line.startsWith("━"))
            MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
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

/**
 * Standing notice for cookie files that stopped working.
 *
 * Expired cookies are the quietest failure Nightshift has: downloads that need
 * a signed-in session come back censored or not at all, and the run still
 * reports success. Saying it on the first screen is the whole point.
 */
@Composable
fun CookieBanner(entries: List<CookieStatus>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val urgent = entries.any { it.isUrgent }
    val tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    val title = when {
        entries.any { it.state == "signed_out" } -> stringResource(R.string.cookies_signedout_title)
        entries.any { it.state == "expired" } -> stringResource(R.string.cookies_expired_title)
        entries.any { it.state == "missing" } -> stringResource(R.string.cookies_missing_title)
        else -> stringResource(R.string.cookies_soon_title)
    }
    // Resolved through the context, not stringResource: the lambda below is a
    // plain one, and a @Composable call may not happen inside it.
    val res = LocalContext.current
    val detail = entries.joinToString(" · ") { entry ->
        when (entry.state) {
            "signed_out" -> res.getString(R.string.cookies_signedout_line, entry.kind)
            "expired" -> res.getString(R.string.cookies_expired_line, entry.kind)
            "missing" -> res.getString(R.string.cookies_missing_line, entry.kind)
            else -> res.getString(R.string.cookies_soon_line, entry.kind,
                Math.round(entry.daysLeft ?: 0.0).toInt())
        }
    } + " — " + stringResource(R.string.cookies_hint)

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = tint,
            modifier = Modifier.size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
