package com.jannehy.nightshift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.Accents
import com.jannehy.nightshift.core.NightshiftUser
import com.jannehy.nightshift.core.Session
import com.jannehy.nightshift.core.VersionInfo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(session: Session) {
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)

        SectionCard {
            Text(stringResource(R.string.account), style = MaterialTheme.typography.titleSmall)
            LabelRow(stringResource(R.string.user), session.me?.user?.username ?: "–")
            LabelRow(stringResource(R.string.role), stringResource(
                if (session.isAdmin) R.string.administrator else R.string.user))
            TextButton(onClick = { showPassword = true }) {
                Text(stringResource(R.string.change_password))
            }
        }

        SectionCard {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.accent_colour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccentPicker(session)
        }

        SectionCard {
            Text(stringResource(R.string.server), style = MaterialTheme.typography.titleSmall)
            LabelRow(stringResource(R.string.address), session.serverAddress)
            LabelRow(stringResource(R.string.version),
                session.serverVersion?.version ?: stringResource(R.string.older_than_13))
            LabelRow(stringResource(R.string.tab_sync), stringResource(
                if (session.syncEnabled) R.string.state_on else R.string.state_off))
            LabelRow("Navidrome", stringResource(
                if (session.navidromeEnabled) R.string.connected else R.string.state_off))
            if (session.serverVersion?.isSupported != true) {
                Text(stringResource(R.string.old_server_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (session.isAdmin) {
            SectionCard {
                Text(stringResource(R.string.administration),
                    style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showUsers = true }) {
                    Text(stringResource(R.string.users))
                }
                TextButton(onClick = { showConfig = true }) {
                    Text(stringResource(R.string.server_settings))
                }
            }
        }

        SectionCard {
            TextButton(onClick = { scope.launch { session.logout() } }) {
                Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { scope.launch { session.logout(forgetServer = true) } }) {
                Text(stringResource(R.string.sign_out_forget),
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showPassword) PasswordDialog(session, null) { showPassword = false }
    if (showUsers) UsersDialog(session) { showUsers = false }
    if (showConfig) ServerConfigScreen(session) { showConfig = false }
}

@Composable
private fun LabelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccentPicker(session: Session) {
    FlowRowSimple {
        Accents.swatches.forEach { (name, hex) ->
            val selected = hex.equals(session.accentHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorFromHex(hex))
                    .border(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape)
                    .clickable { session.setAccent(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, name, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A plain wrapping row – FlowRow is still experimental in this Compose version. */
@Composable
private fun FlowRowSimple(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun PasswordDialog(session: Session, username: String?, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(password, { password = it },
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(repeat, { repeat = it },
                    label = { Text(stringResource(R.string.repeat_password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.length >= 4 && password == repeat,
                onClick = {
                    scope.launch {
                        runCatching { session.api?.changePassword(username, password) }
                            .onSuccess {
                                val account = username ?: session.me?.user?.username
                                account?.let { session.prefs.savePassword(it, password) }
                                onDismiss()
                            }
                            .onFailure { error = it.message }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun UsersDialog(session: Session, onDismiss: () -> Unit) {
    var users by remember { mutableStateOf<List<NightshiftUser>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { session.api?.users() }
            .onSuccess { users = it.orEmpty() }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.users)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { ErrorBanner(it) { error = null } }
                users.forEach { user ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.username)
                            Text(stringResource(
                                if (user.isAdmin) R.string.administrator else R.string.user),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { passwordFor = user.username }) {
                            Text(stringResource(R.string.change_password))
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { session.api?.deleteUser(user.username) }
                                    .onSuccess { load() }
                                    .onFailure { error = it.message }
                            }
                        }) { Text(stringResource(R.string.delete),
                                  color = MaterialTheme.colorScheme.error) }
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(R.string.new_user))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (creating) {
        CreateUserDialog(session, onDismiss = { creating = false },
            onCreated = { scope.launch { load() } })
    }

    passwordFor?.let { target ->
        PasswordDialog(session, target) { passwordFor = null }
    }
}

@Composable
private fun CreateUserDialog(session: Session, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(username, { username = it },
                    label = { Text(stringResource(R.string.username)) }, singleLine = true)
                OutlinedTextField(password, { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.administrator), Modifier.weight(1f))
                    Switch(checked = admin, onCheckedChange = { admin = it })
                }
                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = username.isNotBlank() && password.length >= 4,
                onClick = {
                    scope.launch {
                        runCatching {
                            session.api?.createUser(username.trim(), password,
                                if (admin) "admin" else "user")
                        }.onSuccess { onCreated(); onDismiss() }
                            .onFailure { error = it.message }
                    }
                },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
