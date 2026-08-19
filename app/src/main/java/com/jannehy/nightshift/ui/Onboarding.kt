package com.jannehy.nightshift.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.Api
import com.jannehy.nightshift.core.Session
import kotlinx.coroutines.launch

@Composable
fun ServerSetupScreen(session: Session) {
    var address by remember { mutableStateOf(session.serverAddress) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        NightshiftMark(Modifier.size(72.dp), MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.server_address_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Button(
            onClick = { scope.launch { session.connect(address) } },
            enabled = address.isNotBlank() && !session.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (session.busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.connect))
        }

        session.errorMessage?.let { ErrorBanner(errorText(it)) }
    }
}

@Composable
fun SetupRequiredScreen(session: Session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        NightshiftMark(Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.setup_unfinished),
            style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_unfinished_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Api.normalize(session.serverAddress)?.let { base ->
            Button(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("$base/setup")))
            }) { Text(stringResource(R.string.setup_unfinished)) }
        }
        OutlinedButton(onClick = { scope.launch { session.bootstrap() } }) {
            Text(stringResource(R.string.check_again))
        }
        TextButton(onClick = { scope.launch { session.logout(forgetServer = true) } }) {
            Text(stringResource(R.string.use_other_server))
        }
    }
}

@Composable
fun LoginScreen(session: Session) {
    var username by remember { mutableStateOf(session.prefs.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        NightshiftMark(Modifier.size(72.dp), MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.headlineMedium)
        Text(session.serverAddress, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { scope.launch { session.login(username.trim(), password) } },
            enabled = username.isNotBlank() && password.isNotEmpty() && !session.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (session.busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.sign_in))
        }

        session.errorMessage?.let { ErrorBanner(errorText(it)) }

        TextButton(onClick = { scope.launch { session.logout(forgetServer = true) } }) {
            Text(stringResource(R.string.use_other_server))
        }
    }
}

/** Session stores short markers for its own failures; everything else is the
 *  message the server or the network produced. */
@Composable
fun errorText(raw: String): String = when (raw) {
    "invalid" -> stringResource(R.string.invalid_url)
    "host" -> stringResource(R.string.error_host)
    "refused" -> stringResource(R.string.error_refused)
    "timeout" -> stringResource(R.string.error_timeout)
    "tls" -> stringResource(R.string.error_tls)
    "auth" -> stringResource(R.string.error_auth)
    "unreachable" -> stringResource(R.string.server_unreachable)
    else -> raw
}
