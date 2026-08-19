package com.jannehy.nightshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.core.JobMonitor
import com.jannehy.nightshift.core.Session
import com.jannehy.nightshift.ui.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = Session(applicationContext)
        val downloads = JobMonitor(session, JobMonitor.Source.DOWNLOAD)
        val nightly = JobMonitor(session, JobMonitor.Source.NIGHTLY)

        setContent {
            NightshiftTheme(accentHex = session.accentHex) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RootScreen(session, downloads, nightly)
                }
            }
        }
    }
}

/** Mirrors the server's access gate: no server -> setup -> login -> app. */
@Composable
fun RootScreen(session: Session, downloads: JobMonitor, nightly: JobMonitor) {
    LaunchedEffect(Unit) { session.bootstrap() }

    when (session.phase) {
        Session.Phase.LAUNCHING -> LaunchScreen()
        Session.Phase.NEEDS_SERVER -> ServerSetupScreen(session)
        Session.Phase.NEEDS_SETUP -> SetupRequiredScreen(session)
        Session.Phase.NEEDS_LOGIN -> LoginScreen(session)
        Session.Phase.READY -> MainScreen(session, downloads, nightly)
    }
}

@Composable
private fun LaunchScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NightshiftMark(
            modifier = Modifier.size(104.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}
