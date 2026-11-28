/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.ModuleEnabledKey
import dev.citali.lunartune.constants.ModuleMatchToleranceKey
import dev.citali.lunartune.constants.ModulePicksKey
import dev.citali.lunartune.constants.ModulesEnabledKey
import dev.citali.lunartune.constants.StreamSourceOrderKey
import dev.citali.lunartune.modules.addon.AddonStore
import dev.citali.lunartune.modules.AccountState
import dev.citali.lunartune.modules.FieldKind
import dev.citali.lunartune.modules.ModuleField
import dev.citali.lunartune.modules.NO_ACCOUNT_LABEL
import dev.citali.lunartune.modules.SourceModule
import dev.citali.lunartune.modules.StreamSourceManager
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tolerance slider bounds, in seconds. */
// how often the page re-reads what each module last said about itself
private const val MODULE_STATE_REFRESH_MS = 10_000L
// how often a module's own page re-checks a module that isn't answering
private const val MODULE_RECHECK_MS = 15_000L
private const val MIN_TOLERANCE = 2
private const val MAX_TOLERANCE = 20
private const val DEFAULT_TOLERANCE = 10

/**
 * The modules page: what can serve audio, in what order, and what else can be
 * added.
 *
 * The list is deliberately two groups — the modules compiled into this build,
 * and the addons the user has added. They behave identically once playback
 * starts, but only one of the groups can be removed, and a page that mixed them
 * would have to explain that per row instead of once.
 *
 * Addons speak the BitChord addon protocol: three HTTP routes, JSON in and JSON
 * out, and no code that runs here.
 */
@Composable
fun SourcesSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (orderRaw) = rememberPreference(StreamSourceOrderKey, "")
    val (masterEnabled, onMasterEnabledChange) = rememberPreference(ModulesEnabledKey, true)

    var tab by rememberSaveable { mutableStateOf(ModulesTab.Installed) }
    var selectedModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<SourceModule?>(null) }
    var addedVersion by remember { mutableStateOf(0) }

    val orderedModules =
        remember(orderRaw, addedVersion) {
            val saved = orderRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val all = StreamSourceManager.allModules()
            val known = all.map { it.info.id }
            (saved.filter { it in known } + known.filter { it !in saved })
                .mapNotNull { id -> all.firstOrNull { it.info.id == id } }
        }
    val builtIn = orderedModules.filterNot { it.info.id.startsWith(StreamSourceManager.ADDON_ID_PREFIX) }
    val added = orderedModules.filter { it.info.id.startsWith(StreamSourceManager.ADDON_ID_PREFIX) }

    var lastStreamVersion by remember { mutableStateOf(0) }
    var moduleStates by remember { mutableStateOf<Map<String, AccountState>>(emptyMap()) }
    LaunchedEffect(orderedModules) {
        while (true) {
            moduleStates =
                orderedModules
                    .mapNotNull { module -> modules@
                        runCatching { module.accountState() }.getOrNull()?.let { module.info.id to it }
                    }.toMap()
            delay(MODULE_STATE_REFRESH_MS)
        }
    }
    val enabledCount = remember(orderRaw, addedVersion, masterEnabled) {
        orderedModules.count { StreamSourceManager.isEnabled(it.info.id) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            ModulesHeader(
                title = stringResource(R.string.sources_page_title),
                subtitle = stringResource(R.string.modules_page_subtitle),
            )

            Spacer(Modifier.height(10.dp))

            ModuleMasterCard(
                enabled = masterEnabled,
                enabledCount = enabledCount,
                installedCount = builtIn.size + added.size,
                catalogCount = 0,
                onEnabledChange = onMasterEnabledChange,
            )

            Spacer(Modifier.height(16.dp))

            ModulesTabs(selected = tab, onSelect = { tab = it })

            Spacer(Modifier.height(8.dp))

            ModuleTabContent(tab) { current ->
                when (current) {
                    ModulesTab.Installed ->
                        InstalledTab(
                            builtIn = builtIn,
                            added = added,
                            masterEnabled = masterEnabled,
                            moduleStates = moduleStates,
                            lastStreamVersion = lastStreamVersion,
                            onOpen = { selectedModuleId = it },
                            onMoved = { id, direction ->
                                scope.launch { StreamSourceManager.move(context, id, direction) }
                            },
                            onRemove = { pendingRemove = it },
                        )
                    ModulesTab.Catalogs ->
                        CatalogsTab(onAdd = { tab = ModulesTab.Installed; addOpen = true })
                }
            }

            if (tab == ModulesTab.Installed) {
                ModulesFab(onClick = { addOpen = true })
            }

            Text(
                text = stringResource(R.string.stream_sources_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )

            TrackMatchingSection()

            message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    selectedModuleId?.let { moduleId ->
        StreamSourceManager.module(moduleId)?.let { module ->
            ModuleDetailSheet(
                module = module,
                onDismiss = { selectedModuleId = null },
                onLastStreamCleared = { lastStreamVersion++ },
            )
        }
    }

    if (addOpen) {
        TextEntryDialogSimple(
            title = stringResource(R.string.modules_add_addon_title),
            label = stringResource(R.string.modules_addon_url_label),
            confirm = stringResource(R.string.modules_add_addon_confirm),
            onDismiss = { addOpen = false },
        ) { address ->
            addOpen = false
            busy = true
            message = null
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        AddonStore.add(context, address)
                    }
                busy = false
                result
                    .onSuccess { info ->
                        StreamSourceManager.reloadLoaded()
                        addedVersion++
                        message = context.getString(R.string.modules_addon_added, info.name)
                    }.onFailure { error ->
                        message =
                            context.getString(
                                R.string.modules_addon_failed,
                                error.message ?: context.getString(R.string.remote_modules_failed),
                            )
                    }
            }
        }
    }

    pendingRemove?.let { module ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.modules_addon_remove_title, module.info.name)) },
            text = { Text(stringResource(R.string.modules_addon_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        AddonStore.remove(context, module.info.id)
                        scope.launch {
                            StreamSourceManager.reloadLoaded()
                            addedVersion++
                        }
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

/** A module row: its own icon, whether it is on, and what it does. */
@Composable
private fun SourceCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showStatusDot: Boolean,
    healthy: Boolean = true,
    meta: List<String> = emptyList(),
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp)),
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showStatusDot) {
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !healthy -> MaterialTheme.colorScheme.error
                                            enabled -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                    ),
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (meta.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        meta.forEachIndexed { index, label ->
                            StatusPill(
                                text = label,
                                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                content =
                                    if (index == 0) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * How far a candidate's length may drift before the matcher stops believing it is
 * the same recording. Wide enough for intro/outro trims, narrow enough that a
 * different edit does not slip through.
 */
/** the small heading that separates one group of settings from the next */
@Composable
private fun SourcesSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun TrackMatchingSection() {
    val (tolerance, onToleranceChange) = rememberPreference(ModuleMatchToleranceKey, DEFAULT_TOLERANCE)
    val sliderValue = tolerance.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE).toFloat()

    SourcesSectionLabel(stringResource(R.string.sources_track_matching))

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sources_length_tolerance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.sources_length_tolerance_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                StatusPill(
                    text = stringResource(R.string.sources_tolerance_value, sliderValue.toInt()),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { onToleranceChange(it.toInt()) },
                valueRange = MIN_TOLERANCE.toFloat()..MAX_TOLERANCE.toFloat(),
                steps = MAX_TOLERANCE - MIN_TOLERANCE - 1,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    Spacer(Modifier.height(12.dp))
}

/** A module's own page: what it is, whether it is on, and its quality choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleDetailSheet(
    module: SourceModule,
    onDismiss: () -> Unit,
    onLastStreamCleared: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (enabled, onEnabledChange) = rememberPreference(ModuleEnabledKey(module.info.id), false)
    val (picksRaw, onPicksChange) = rememberPreference(ModulePicksKey(module.info.id), "")
    val picks =
        remember(picksRaw) {
            picksRaw.split(";").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }.toMap()
        }
    var accountState by remember { mutableStateOf<AccountState?>(null) }
    var verifying by remember { mutableStateOf(false) }
    // what the module last served, kept in state so it can be cleared from here
    var lastStream by remember(module) { mutableStateOf(module.lastStreamLabel()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(module.info.id) {
        // the page keeps itself current: a module that stopped answering is asked again on
        // its own, so it comes back on the page without anyone pressing anything
        while (true) {
            val stored =
                withContext(Dispatchers.IO) { runCatching { module.accountState() }.getOrNull() }
            if (stored != null) accountState = stored
            if (stored is AccountState.Expired) {
                val probed =
                    withContext(Dispatchers.IO) { runCatching { module.verifyAccount() }.getOrNull() }
                if (probed != null) accountState = probed
            }
            delay(MODULE_RECHECK_MS)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(moduleBrandMark(module.info.id)),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.info.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = module.info.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            val stateText =
                when (val state = accountState) {
                    is AccountState.SignedIn -> state.label
                    is AccountState.Expired -> state.reason
                    is AccountState.SignedOut -> stringResource(R.string.sources_not_connected)
                    null -> stringResource(R.string.sources_checking)
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (accountState) {
                                    is AccountState.SignedIn -> MaterialTheme.colorScheme.primary
                                    is AccountState.Expired -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                )
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (verifying) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    TextButton(
                        onClick = {
                            verifying = true
                            scope.launch {
                                // network work: keep it off the main thread
                                accountState =
                                    withContext(Dispatchers.IO) {
                                        runCatching { module.verifyAccount() }.getOrNull()
                                    }
                                verifying = false
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.check_connection))
                    }
                }
            }

            // settings the module asks for, e.g. Monochrome's server
            module.fields.forEach { field ->
                when (field.kind) {
                    FieldKind.Text -> ModuleTextField(module = module, field = field)
                    FieldKind.Toggle -> ModuleToggleField(module = module, field = field)
                    FieldKind.Select -> Unit
                }
            }

            lastStream?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.sources_last_stream),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            module.clearLastStream()
                            lastStream = null
                            onLastStreamCleared()
                        },
                    ) {
                        Text(stringResource(R.string.sources_clear_last_stream))
                    }
                }
            }

            module.choices.forEach { choice ->
                val selected = picks[choice.id] ?: choice.options.firstOrNull()?.id
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choice.options.forEach { option ->
                        val isSelected = option.id == selected
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                    ).padding(horizontal = 14.dp, vertical = 8.dp)
                                    .clickable {
                                        val updated = picks.toMutableMap().apply { put(choice.id, option.id) }
                                        onPicksChange(updated.entries.joinToString(";") { "${it.key}=${it.value}" })
                                    },
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
            }

            module.info.sourceUrl?.let { url ->
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        onDismiss()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.open_module_source))
                }
            }
        }
    }
}

/** A text setting (an endpoint, mostly): typed, saved with the button beside it. */
@Composable
private fun ModuleTextField(
    module: SourceModule,
    field: ModuleField,
) {
    var saved by remember(field.id, module) { mutableStateOf(module.fieldValue(field)) }
    var draft by remember(field.id, module) { mutableStateOf(saved) }
    val changed = draft.trim() != saved

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            placeholder = { Text(field.hint.ifBlank { field.default }) },
            trailingIcon = {
                TextButton(
                    enabled = changed,
                    onClick = {
                        module.setFieldValue(field, draft)
                        saved = draft.trim().trimEnd('/')
                    },
                ) {
                    Text(stringResource(R.string.module_field_save))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (saved != field.default) {
                TextButton(
                    onClick = {
                        module.setFieldValue(field, null)
                        saved = field.default
                        draft = field.default
                    },
                ) {
                    Text(stringResource(R.string.module_field_reset))
                }
            }
        }
    }
}

/** A boolean setting, saved as it is flipped. */
@Composable
private fun ModuleToggleField(
    module: SourceModule,
    field: ModuleField,
) {
    var checked by remember(field.id, module) {
        mutableStateOf(module.fieldValue(field) == "true")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (field.description.isNotBlank()) {
                Text(
                    text = field.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                module.setFieldValue(field, it.toString())
            },
        )
    }
}
