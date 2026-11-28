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
import dev.citali.lunartune.modules.addon.AddonStore
import dev.citali.lunartune.utils.rememberPreference

/**
 * The modules this app can play from, in the order they are tried.
 *
 * Built-in modules come first as their own group and carry no remove action:
 * they are compiled into the build, so a delete button would be a lie. Added
 * addons can be moved and removed, and the order they are in is the order
 * playback consults them.
 */
@Composable
fun InstalledTab(
    builtIn: List<SourceModule>,
    added: List<SourceModule>,
    masterEnabled: Boolean,
    moduleStates: Map<String, AccountState>,
    lastStreamVersion: Int,
    onOpen: (String) -> Unit,
    onMoved: (String, Int) -> Unit,
    onRemove: (SourceModule) -> Unit,
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
                    val position = builtIn.indexOf(module)
                    ModuleRow(
                        module = module,
                        state = moduleStates[module.info.id],
                        lastStreamVersion = lastStreamVersion,
                        masterEnabled = masterEnabled,
                        canMoveUp = position > 0,
                        canMoveDown = position < builtIn.lastIndex,
                        subtitle = moduleSubtitle(module, moduleStates[module.info.id], lastStreamVersion, masterEnabled),
                        onOpen = { onOpen(module.info.id) },
                        onMove = { direction -> onMoved(module.info.id, direction) },
                        onRemove = null,
                    )
                }
            }
        }

        if (added.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            ModulesGroupLabel(
                label = stringResource(R.string.modules_addons_label),
                count = added.size,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                added.forEach { module ->
                    val position = builtIn.size + added.indexOf(module)
                    ModuleRow(
                        module = module,
                        state = moduleStates[module.info.id],
                        lastStreamVersion = lastStreamVersion,
                        masterEnabled = masterEnabled,
                        canMoveUp = position > 0,
                        canMoveDown = position < builtIn.size + added.lastIndex,
                        subtitle = moduleSubtitle(module, moduleStates[module.info.id], lastStreamVersion, masterEnabled),
                        onOpen = { onOpen(module.info.id) },
                        onMove = { direction -> onMoved(module.info.id, direction) },
                        onRemove = { onRemove(module) },
                    )
                }
            }
        }

        if (builtIn.isEmpty() && added.isEmpty()) {
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
 * The picture for a module's row.
 *
 * Each shipped module has its own mark, drawn as a vector so it stays sharp at
 * any density. An addon has no mark of its own, and cannot ship one — it is a
 * server, not a package — so it gets its initial on a tinted tile rather than a
 * generic icon that would say nothing about which one it is.
 */
@Composable
private fun ModuleLogo(
    id: String,
    name: String,
    size: Int = 44,
) {
    val brand = moduleBrandIcon(id)
    if (brand != null) {
        Box(
            modifier =
                Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape((size * 0.34f).dp)),
        ) {
            Icon(
                painter = painterResource(brand),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
            )
        }
        return
    }
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size * 0.34f).dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** the brand mark for a module that has one, by its id */
internal fun moduleBrandIcon(id: String): Int? =
    when (id) {
        "jiosaavn" -> R.drawable.ic_source_jiosaavn
        "monochrome" -> R.drawable.ic_source_monochrome
        else -> null
    }

/**
 * A module row: what it is, whether it is on, and where it sits in the order.
 *
 * The reorder controls are arrows rather than a drag handle because the list is
 * short and a drag inside a vertical scroll fights the scroll.
 */
@Composable
private fun ModuleRow(
    module: SourceModule,
    state: AccountState?,
    lastStreamVersion: Int,
    masterEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    subtitle: String,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val (enabled, onEnabledChange) = rememberPreference(ModuleEnabledKey(module.info.id), false)
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
        ModuleLogo(id = module.info.id, name = module.info.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.info.name,
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

/**
 * The catalogs tab, which in this build is a starting point rather than a feed.
 *
 * BitChord addons are servers, and no public directory of them exists — the
 * community lists that circulated were GitHub repos whose files have since gone.
 * So instead of inventing a catalog format nobody publishes, the page offers the
 * addresses that do exist and the shape an address has to have, and says plainly
 * that an addon is something you host or are given.
 */
@Composable
fun CatalogsTab(
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        ModulesEmptyState(
            title = stringResource(R.string.modules_catalog_empty_title),
            body = stringResource(R.string.modules_catalog_empty_body),
        )

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
                        .clickable(onClick = onAdd)
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

/** the id an addon's address turns into, for whatever the page needs to key on */
internal fun addonId(address: String): String = AddonStore.idFor(address)

/**
 * The mark to show for a module, falling back to the app's own tile.
 *
 * The rows draw a letter tile for addons because they can also print the name
 * beside it; the detail sheet shows the mark on its own, so it needs something
 * to draw for an addon too.
 */
internal fun moduleBrandMark(id: String): Int = moduleBrandIcon(id) ?: R.drawable.ic_source_monochrome
