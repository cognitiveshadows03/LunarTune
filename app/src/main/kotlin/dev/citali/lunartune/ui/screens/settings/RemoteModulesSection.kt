/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.R
import dev.citali.lunartune.modules.StreamSourceManager
import dev.citali.lunartune.modules.remote.InstalledModule
import dev.citali.lunartune.modules.remote.RemoteModuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "add a module" half of the sources page.
 *
 * A module here is a document, not code: the user pastes or fetches one and it
 * joins the list above as an ordinary source. That keeps the page's promise —
 * everything that can serve audio is in one list, in one order — while making the
 * thing that is *not* in the build visible and removable in the same place.
 *
 * Anything that fails to load keeps its row and says why. A module that silently
 * vanished would leave the user with no way to tell "I mistyped the address" from
 * "this build cannot run it".
 */
@Composable
fun RemoteModulesSection(userAgent: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installed by remember { mutableStateOf(RemoteModuleStore.installed()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var addFromUrl by remember { mutableStateOf(false) }
    var addFromText by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<InstalledModule?>(null) }

    fun refresh() {
        installed = RemoteModuleStore.installed()
        StreamSourceManager.reloadLoaded()
    }

    SourcesSectionLabel(stringResource(R.string.remote_modules_title))

    Text(
        text = stringResource(R.string.remote_modules_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        installed.forEach { module ->
            LoadedModuleCard(
                module = module,
                onRemove = { pendingRemove = module },
            )
        }

        if (installed.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.remote_modules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip(
                label = stringResource(R.string.remote_modules_add_url),
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { addFromUrl = true }
            ActionChip(
                label = stringResource(R.string.remote_modules_add_text),
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { addFromText = true }
        }

        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.remote_modules_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (addFromUrl) {
        TextEntryDialog(
            title = stringResource(R.string.remote_modules_add_url),
            label = stringResource(R.string.remote_modules_url_label),
            confirm = stringResource(R.string.remote_modules_install),
            onDismiss = { addFromUrl = false },
        ) { url ->
            addFromUrl = false
            busy = true
            message = null
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        RemoteModuleStore.installFromUrl(context, url, userAgent)
                    }
                busy = false
                result
                    .onSuccess { document ->
                        message = null
                        refresh()
                        message = context.getString(R.string.remote_modules_added, document.name)
                    }.onFailure { error ->
                        message = error.message ?: context.getString(R.string.remote_modules_failed)
                    }
            }
        }
    }

    if (addFromText) {
        TextEntryDialog(
            title = stringResource(R.string.remote_modules_add_text),
            label = stringResource(R.string.remote_modules_text_label),
            confirm = stringResource(R.string.remote_modules_install),
            singleLine = false,
            onDismiss = { addFromText = false },
        ) { text ->
            addFromText = false
            val result = RemoteModuleStore.install(context, text)
            result
                .onSuccess { document ->
                    refresh()
                    message = context.getString(R.string.remote_modules_added, document.name)
                }.onFailure { error ->
                    message = error.message ?: context.getString(R.string.remote_modules_failed)
                }
        }
    }

    pendingRemove?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.remote_modules_remove_title, module.document?.name ?: module.id)) },
            text = { Text(stringResource(R.string.remote_modules_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        RemoteModuleStore.remove(context, module.id)
                        refresh()
                        message = null
                    },
                ) {
                    Text(stringResource(R.string.remote_modules_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LoadedModuleCard(
    module: InstalledModule,
    onRemove: () -> Unit,
) {
    val broken = !module.usable
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (broken) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (broken) R.drawable.info else R.drawable.download),
                    contentDescription = null,
                    tint =
                        if (broken) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.document?.name ?: module.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        module.error
                            ?: buildString {
                                append(stringResource(R.string.remote_modules_version, module.document?.version.orEmpty()))
                                val author = module.document?.author
                                if (!author.isNullOrBlank()) append(" · $author")
                                append(" · ")
                                append(stringResource(R.string.remote_modules_needs_enabling))
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (broken) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.remote_modules_remove),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRemove)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier =
            modifier.then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    confirm: String,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
