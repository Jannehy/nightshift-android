package com.jannehy.nightshift.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.Session

@Composable
fun NightlyScreen(session: Session, monitor: JobMonitor) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tab_nightly), style = MaterialTheme.typography.headlineSmall)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.NightsStay, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(stringResource(R.string.scheduled),
                        style = MaterialTheme.typography.titleSmall)
                    Text(scheduleText(session.me?.nightlySchedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.nightly_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { monitor.start { session.api!!.startNightly() } },
                enabled = !monitor.state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.run_now))
            }
        }

        if (monitor.state != JobMonitor.State.Idle || monitor.lines.isNotEmpty()) {
            SectionCard {
                JobStatusView(monitor)
                LogView(monitor.lines, height = 320.dp)
            }
        }

        monitor.errorMessage?.let { ErrorBanner(it) { monitor.errorMessage = null } }
    }
}

/** Turns the handful of cron shapes Nightshift actually uses into plain text;
 *  anything else falls back to the raw expression. */
@Composable
private fun scheduleText(cron: String?): String {
    if (cron.isNullOrBlank()) return stringResource(R.string.unknown)
    val parts = cron.split(" ")
    if (parts.size != 5) return cron
    val minute = parts[0].toIntOrNull()
    val hour = parts[1].toIntOrNull()
    if (minute == null || hour == null || parts[2] != "*" || parts[3] != "*" || parts[4] != "*") {
        return cron
    }
    return stringResource(R.string.daily_at, "%02d:%02d".format(hour, minute))
}
