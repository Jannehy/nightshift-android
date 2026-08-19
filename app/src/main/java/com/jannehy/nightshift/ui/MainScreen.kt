package com.jannehy.nightshift.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.Session

/** The bar uses its own short labels: the screen titles ("Einstellungen") wrap
 *  onto two lines in a five-item bar on a narrow phone. */
private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    DOWNLOADS(R.string.nav_downloads, Icons.Default.Download),
    SEARCH(R.string.nav_search, Icons.Default.Search),
    SYNC(R.string.nav_sync, Icons.Default.Sync),
    NIGHTLY(R.string.nav_nightly, Icons.Default.NightsStay),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun MainScreen(session: Session, downloads: JobMonitor, nightly: JobMonitor) {
    var selected by remember { mutableStateOf(Tab.DOWNLOADS) }
    val tabs = remember(session.syncEnabled) {
        Tab.entries.filter { it != Tab.SYNC || session.syncEnabled }
    }

    LaunchedEffect(Unit) {
        downloads.restore()
        nightly.restore()
    }

    Scaffold(
        bottomBar = {
            // Left at the Material default: forcing the bar shorter clips its
            // fixed internal layout, and icons end up on top of their labels.
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(tab.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selected) {
                Tab.DOWNLOADS -> DownloadsScreen(session, downloads)
                Tab.SEARCH -> SearchScreen(session, downloads) { selected = Tab.DOWNLOADS }
                Tab.SYNC -> SyncScreen(session)
                Tab.NIGHTLY -> NightlyScreen(session, nightly)
                Tab.SETTINGS -> SettingsScreen(session)
            }
        }
    }
}
