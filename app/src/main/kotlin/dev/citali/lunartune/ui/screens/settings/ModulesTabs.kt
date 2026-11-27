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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.ModuleEnabledKey
import dev.citali.lunartune.modules.AccountState
import dev.citali.lunartune.modules.NO_ACCOUNT_LABEL
import dev.citali.lunartune.modules.SourceModule
import dev.citali.lunartune.modules.remote.CatalogEntry
import dev.citali.lunartune.modules.remote.InstalledCatalog
import dev.citali.lunartune.modules.remote.InstalledModule
import dev.citali.lunartune.modules.remote.RemoteModule
import dev.citali.lunartune.utils.rememberPreference

/**
 * The modules this app can play from, in the order they are tried.
 *
 * Built-in modules come first as their own group and carry no remove action: they
 * are compiled into the build, so a delete button would be a lie. Loaded ones can
 * be moved, and the order they are in is the order playback consults them.
 */
@Composable
fun InstalledTab(
    builtIn: List<SourceModule>,
    loaded: List<RemoteModule>,
    brokenLoaded: List<InstalledModule>,
    masterEnabled: Boolean,
    moduleStates: Map<String, AccountState>,
    lastStreamVersion: Int,
    orderRaw: String,
    onOpen: (String) -> Unit,
    onMoved: (String, Int) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        if (builtIn.isNotEmpty()) {
            ModulesGroupLabel(
                label = stringResource(R.string.modules_built_in_label),
                count = builtIn.size,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                builtIn.forEach { module ->
                    val position = loaded.size.let { _ -> builtIn.indexOf(module) }
                    ModuleRow(
                        title = module.info.name,
                        subtitle = moduleSubtitle(
                            module = module,
                            state = moduleStates[module.info.id],
                            lastStreamVersion = lastStreamVersion,
                            masterEnabled = masterEnabled,
                        ),
                        enabledKey = module.info.id,
                        canMoveUp = position > 0,
                        canMoveDown = position < builtIn.lastIndex,
                        onOpen = { onOpen(module.info.id) },
                        onMove = { direction -> onMoved(module.info.id, direction) },
                    )
                }
            }
        }

        if (loaded.isNotEmpty() || brokenLoaded.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            ModulesGroupLabel(
                label = stringResource(R.string.modules_loaded_label),
                count = loaded.size + brokenLoaded.size,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                loaded.forEach { module ->
                    val position = builtIn.size + loaded.indexOf(module)
                    ModuleRow(
                        title = module.info.name,
                        subtitle = moduleSubtitle(
                            module = module,
                            state = moduleStates[module.info.id],
                            lastStreamVersion = lastStreamVersion,
                            masterEnabled = masterEnabled,
                        ),
                        enabledKey = module.info.id,
                        canMoveUp = position > 0,
                        canMoveDown = position < builtIn.size + loaded.lastIndex,
                        onOpen = { onOpen(module.info.id) },
                        onMove = { direction -> onMoved(module.info.id, direction) },
                    )
                }
                brokenLoaded.forEach { broken ->
                    BrokenModuleRow(broken)
                }
            }
        }

        if (builtIn.isEmpty() && loaded.isEmpty() && brokenLoaded.isEmpty()) {
            ModulesEmptyState(
                title = stringResource(R.string.modules_loaded_empty_title),
                body = stringResource(R.string.modules_loaded_empty_body),
            )
        }
    }
}

/** one line describing what a module is currently doing */
@Composable
private fun moduleSubtitle(
    module: SourceModule,
    state: AccountState?,
    lastStreamVersion: Int,
    masterEnabled: Boolean,
): String {
    val enabled = rememberPreference(ModuleEnabledKey(module.info.id), false).value
    val lastStream = remember(module, lastStreamVersion) { module.lastStreamLabel() }
    val down = state as? AccountState.Expired
    return when {
        !masterEnabled -> stringResource(R.string.modules_off_because_master)
        down != null -> down.reason
        enabled && lastStream != null -> lastStream
        else -> {
            val account = remember(module) { runCatching { module.accountState() }.getOrNull() }
            (account as? AccountState.SignedIn)?.label ?: NO_ACCOUNT_LABEL
        }
    }
}

@Composable
private fun ModulesGroupLabel(
    label: String,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A module row: what it is, whether it is on, and where it sits in the order.
 *
 * The reorder controls are arrows rather than a drag handle because the list is
 * short and a drag inside a vertical scroll fights the scroll — an arrow is
 * unambiguous and works one-handed.
 */
@Composable
private fun ModuleRow(
    title: String,
    subtitle: String,
    enabledKey: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val (enabled, onEnabledChange) = rememberPreference(ModuleEnabledKey(enabledKey), false)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.add_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MoveArrow(up = true, enabled = canMoveUp) { onMove(-1) }
            MoveArrow(up = false, enabled = canMoveDown) { onMove(1) }
        }
        Spacer(Modifier.width(4.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun MoveArrow(
    up: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        painter = painterResource(if (up) R.drawable.expand_less else R.drawable.expand_more),
        contentDescription =
            stringResource(if (up) R.string.modules_move_up else R.string.modules_move_down),
        tint =
            if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            },
        modifier =
            Modifier
                .size(20.dp)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** an installed document that no longer parses: shown, explained, removable */
@Composable
private fun BrokenModuleRow(module: InstalledModule) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.id,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = module.error ?: stringResource(R.string.remote_modules_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Catalogs: lists of modules maintained by someone else.
 *
 * Adding one shows what it offers and nothing more. Installing an entry is a
 * separate press with a confirmation that says what the module claims to do,
 * because "I added a list" and "I installed an audio provider" should never be
 * the same gesture.
 */
@Composable
fun CatalogsTab(
    catalogs: List<InstalledCatalog>,
    busy: Boolean,
    onAdd: () -> Unit,
    onRefresh: (String) -> Unit,
    onRemove: (String) -> Unit,
    onInstall: (CatalogEntry) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        if (catalogs.isEmpty()) {
            ModulesEmptyState(
                title = stringResource(R.string.modules_catalog_empty_title),
                body = stringResource(R.string.modules_catalog_empty_body),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                catalogs.forEach { catalog ->
                    CatalogCard(
                        catalog = catalog,
                        busy = busy,
                        onRefresh = { onRefresh(catalog.url) },
                        onRemove = { onRemove(catalog.url) },
                        onInstall = onInstall,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(enabled = !busy, onClick = onAdd)
                        .padding(vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.modules_catalog_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Text(
            text = stringResource(R.string.modules_catalog_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CatalogCard(
    catalog: InstalledCatalog,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onInstall: (CatalogEntry) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = catalog.name.ifBlank { catalog.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        catalog.error
                            ?: stringResource(R.string.modules_catalog_entries, catalog.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (catalog.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = catalog.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.modules_catalog_refresh),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy, onClick = onRefresh)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (catalog.entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            catalog.entries.forEach { entry ->
                CatalogEntryRow(entry = entry, onInstall = { onInstall(entry) })
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.modules_catalog_remove),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy, onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CatalogEntryRow(
    entry: CatalogEntry,
    onInstall: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        if (entry.version.isNotBlank()) append("v${entry.version}")
                        if (entry.author.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(entry.author)
                        }
                        if (entry.labels.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(entry.labels.joinToString(", "))
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.remote_modules_install),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
