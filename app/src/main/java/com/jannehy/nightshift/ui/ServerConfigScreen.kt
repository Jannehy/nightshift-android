package com.jannehy.nightshift.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jannehy.nightshift.R
import com.jannehy.nightshift.core.Session
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** One editable value from config.yaml. */
private data class Field(
    val section: String,
    val key: String,
    val original: JsonPrimitive,
    var text: String,
    var flag: Boolean,
) {
    val isBool: Boolean get() = original.booleanOrNull != null && !original.isString

    /** The server replaces stored secrets with a sentinel instead of shipping them. */
    val isSecret: Boolean get() = original.isString && original.content == "__SET__"

    val isNumeric: Boolean
        get() = !original.isString && original.booleanOrNull == null

    val label: String get() = key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    val isChanged: Boolean get() = when {
        isBool -> flag != (original.booleanOrNull ?: false)
        isSecret -> text.isNotEmpty()
        else -> text != original.content
    }

    /** Re-encode in the kind the server sent, so an int stays an int in the YAML. */
    val newValue: JsonPrimitive
        get() = when {
            isBool -> JsonPrimitive(flag)
            original.isString -> JsonPrimitive(text)
            original.intOrNull != null -> text.toIntOrNull()?.let { JsonPrimitive(it) }
                ?: JsonPrimitive(text)
            original.doubleOrNull != null -> text.toDoubleOrNull()?.let { JsonPrimitive(it) }
                ?: JsonPrimitive(text)
            else -> JsonPrimitive(text)
        }
}

/** Order the web settings page uses; unknown sections follow alphabetically. */
private val SECTION_ORDER = listOf("server", "library", "downloads", "nightly",
                                   "sync", "navidrome", "beets", "logging")

@Composable
fun ServerConfigScreen(session: Session, onDismiss: () -> Unit) {
    var fields by remember { mutableStateOf<List<Field>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        runCatching { session.api?.config() }
            .onSuccess { config ->
                fields = config.orEmpty().entries
                    .sortedWith(compareBy({ SECTION_ORDER.indexOf(it.key).takeIf { i -> i >= 0 } ?: 99 },
                                          { it.key }))
                    .flatMap { (section, values) ->
                        values.entries.sortedBy { it.key }.map { (key, raw) ->
                            val isBool = raw.booleanOrNull != null && !raw.isString
                            val isSecret = raw.isString && raw.content == "__SET__"
                            Field(section, key, raw,
                                text = if (isBool || isSecret) "" else raw.content,
                                flag = raw.booleanOrNull ?: false)
                        }
                    }
                revision++
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val changed = remember(revision) { { fields.any { it.isChanged } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { ErrorBanner(it) { error = null } }
                if (loading) {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
                }
                var currentSection: String? = null
                fields.forEach { field ->
                    if (field.section != currentSection) {
                        currentSection = field.section
                        Text(field.section.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    FieldRow(field) { revision++ }
                }
                TextButton(onClick = { confirmReset = true }) {
                    Text(stringResource(R.string.reset_defaults),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !loading && changed(),
                onClick = {
                    scope.launch {
                        saving = true
                        val updates = fields.filter { it.isChanged }
                            .groupBy { it.section }
                            .mapValues { (_, list) -> list.associate { it.key to it.newValue } }
                        runCatching { session.api?.saveConfig(updates) }
                            .onSuccess { load(); session.refreshMe() }
                            .onFailure { error = it.message }
                        saving = false
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_defaults)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        runCatching { session.api?.resetConfig() }
                            .onSuccess { load(); session.refreshMe() }
                            .onFailure { error = it.message }
                    }
                }) { Text(stringResource(R.string.reset_defaults)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FieldRow(field: Field, onChange: () -> Unit) {
    if (field.isBool) {
        var checked by remember { mutableStateOf(field.flag) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = {
                checked = it; field.flag = it; onChange()
            })
        }
    } else {
        var value by remember { mutableStateOf(field.text) }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; field.text = it; onChange() },
            label = { Text(field.label) },
            placeholder = if (field.isSecret) {
                { Text(stringResource(R.string.unchanged)) }
            } else null,
            singleLine = true,
            visualTransformation = if (field.isSecret) PasswordVisualTransformation()
                                   else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
