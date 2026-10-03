/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.DefaultStreamSources
import dev.citali.lunartune.constants.MonochromeEnabledKey
import dev.citali.lunartune.constants.MonochromeInstanceKey
import dev.citali.lunartune.constants.PlayerStreamClient
import dev.citali.lunartune.constants.PlayerStreamClientKey
import dev.citali.lunartune.constants.StreamSourcesEnabledKey
import dev.citali.lunartune.constants.StreamSourcesOrderKey
import dev.citali.lunartune.constants.deserializeStreamSourcesOrder
import dev.citali.lunartune.monochrome.MonochromeAudioProvider
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference

@Composable
fun StreamSourcesSettings(navController: NavController) {
    val defaultEnabled = DefaultStreamSources.map { it.name }.toSet()
    val (orderRaw, onOrderRawChange) = rememberPreference(StreamSourcesOrderKey, defaultValue = "")
    val (enabledRaw, onEnabledRawChange) = rememberPreference(StreamSourcesEnabledKey, defaultValue = defaultEnabled)
    val (preferred, onPreferredChange) =
        rememberEnumPreference(PlayerStreamClientKey, defaultValue = PlayerStreamClient.WEB_REMIX)
    val (monochromeEnabled, onMonochromeEnabledChange) =
        rememberPreference(MonochromeEnabledKey, defaultValue = false)
    val (monochromeInstance, onMonochromeInstanceChange) =
        rememberPreference(MonochromeInstanceKey, defaultValue = MonochromeAudioProvider.DEFAULT_INSTANCE)
    var showInstanceDialog by remember { mutableStateOf(false) }
    var instanceDraft by remember { mutableStateOf(monochromeInstance) }
    var monochromeStatus by remember {
        mutableStateOf<MonochromeAudioProvider.MonochromeStatus?>(null)
    }
    var statusCheckTick by remember { mutableStateOf(0) }
    LaunchedEffect(monochromeInstance, statusCheckTick) {
        monochromeStatus = null
        monochromeStatus =
            MonochromeAudioProvider.checkStatus(
                monochromeInstance.ifBlank { MonochromeAudioProvider.DEFAULT_INSTANCE },
            )
    }

    val order = deserializeStreamSourcesOrder(orderRaw)
    val enabled =
        enabledRaw
            .mapNotNull { name -> PlayerStreamClient.entries.find { it.name == name } }
            .filter { it in DefaultStreamSources }
            .ifEmpty { DefaultStreamSources }
            .toSet()
    val orderedEnabled = order.filter { it in enabled }

    LaunchedEffect(orderedEnabled) {
        val nextPreferred = orderedEnabled.firstOrNull() ?: PlayerStreamClient.WEB_REMIX
        if (preferred != nextPreferred) {
            onPreferredChange(nextPreferred)
        }
    }

    fun persist(
        nextOrder: List<PlayerStreamClient>,
        nextEnabled: Set<PlayerStreamClient>,
    ) {
        onOrderRawChange(nextOrder.joinToString(",") { it.name })
        onEnabledRawChange(nextEnabled.map { it.name }.toSet())
    }

    fun setEnabled(
        client: PlayerStreamClient,
        isEnabled: Boolean,
    ) {
        val nextEnabled =
            if (isEnabled) {
                enabled + client
            } else {
                (enabled - client).ifEmpty { setOf(PlayerStreamClient.WEB_REMIX) }
            }
        val nextOrder =
            if (isEnabled && client !in order) {
                order + client
            } else {
                order
            }
        persist(deserializeStreamSourcesOrder(nextOrder.joinToString(",") { it.name }), nextEnabled)
    }

    fun promote(client: PlayerStreamClient) {
        persist(listOf(client) + order.filterNot { it == client }, enabled)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stream_sources_title)) },
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
            Text(
                text = stringResource(R.string.stream_sources_order),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                orderedEnabled.forEachIndexed { index, client ->
                    FilterChip(
                        selected = index == 0,
                        onClick = { promote(client) },
                        label = { Text("${index + 1}. ${client.chipLabel()}") },
                    )
                }
            }

            StreamSourceGroup(title = stringResource(R.string.stream_sources_web)) {
                StreamSourceRow(
                    client = PlayerStreamClient.WEB_REMIX,
                    title = stringResource(R.string.stream_client_web_remix_title),
                    body = stringResource(R.string.stream_client_web_remix_body),
                    checked = PlayerStreamClient.WEB_REMIX in enabled,
                    onCheckedChange = { setEnabled(PlayerStreamClient.WEB_REMIX, it) },
                )
                StreamSourceRow(
                    client = PlayerStreamClient.TVHTML5,
                    title = stringResource(R.string.stream_client_tvhtml5_title),
                    body = stringResource(R.string.stream_client_tvhtml5_body),
                    checked = PlayerStreamClient.TVHTML5 in enabled,
                    onCheckedChange = { setEnabled(PlayerStreamClient.TVHTML5, it) },
                )
            }

            StreamSourceGroup(title = stringResource(R.string.stream_sources_native)) {
                StreamSourceRow(
                    client = PlayerStreamClient.VISION_OS,
                    title = stringResource(R.string.stream_client_visionos_title),
                    body = stringResource(R.string.stream_client_visionos_body),
                    checked = PlayerStreamClient.VISION_OS in enabled,
                    onCheckedChange = { setEnabled(PlayerStreamClient.VISION_OS, it) },
                )
                StreamSourceRow(
                    client = PlayerStreamClient.ANDROID_VR,
                    title = stringResource(R.string.stream_client_android_vr_title),
                    body = stringResource(R.string.stream_client_android_vr_body),
                    checked = PlayerStreamClient.ANDROID_VR in enabled,
                    onCheckedChange = { setEnabled(PlayerStreamClient.ANDROID_VR, it) },
                )
            }

            StreamSourceGroup(title = stringResource(R.string.stream_sources_creator)) {
                StreamSourceRow(
                    client = PlayerStreamClient.WEB_CREATOR,
                    title = stringResource(R.string.stream_client_web_creator_title),
                    body = stringResource(R.string.stream_client_web_creator_body),
                    checked = PlayerStreamClient.WEB_CREATOR in enabled,
                    onCheckedChange = { setEnabled(PlayerStreamClient.WEB_CREATOR, it) },
                )
            }

            PreferenceGroup(title = stringResource(R.string.lossless_source)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.monochrome_lossless)) },
                        description = stringResource(R.string.monochrome_lossless_description),
                        checked = monochromeEnabled,
                        onCheckedChange = onMonochromeEnabledChange,
                    )
                }
                item {
                    val statusText =
                        when (monochromeStatus) {
                            null -> stringResource(R.string.monochrome_status_checking)
                            MonochromeAudioProvider.MonochromeStatus.ACTIVE ->
                                stringResource(R.string.monochrome_status_active)
                            MonochromeAudioProvider.MonochromeStatus.MAINTENANCE ->
                                stringResource(R.string.monochrome_status_maintenance)
                            MonochromeAudioProvider.MonochromeStatus.DOWN ->
                                stringResource(R.string.monochrome_status_down)
                            MonochromeAudioProvider.MonochromeStatus.UNKNOWN ->
                                stringResource(R.string.monochrome_status_unknown)
                        }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.monochrome_status)) },
                        description = statusText,
                        onClick = { statusCheckTick++ },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.monochrome_instance)) },
                        description = MonochromeAudioProvider.effectiveInstance(monochromeInstance),
                        onClick = {
                            instanceDraft = MonochromeAudioProvider.effectiveInstance(monochromeInstance)
                            showInstanceDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showInstanceDialog) {
        BasicAlertDialog(onDismissRequest = { showInstanceDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.monochrome_instance_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = instanceDraft,
                        onValueChange = { instanceDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.monochrome_instance_dialog_hint)) },
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { showInstanceDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                onMonochromeInstanceChange(
                                    instanceDraft.trim().ifBlank { MonochromeAudioProvider.DEFAULT_INSTANCE },
                                )
                                showInstanceDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.monochrome_instance_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamSourceGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun StreamSourceRow(
    client: PlayerStreamClient,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlayerStreamClient.chipLabel(): String =
    when (this) {
        PlayerStreamClient.VISION_OS -> stringResource(R.string.stream_client_visionos_title)
        PlayerStreamClient.ANDROID_VR -> stringResource(R.string.stream_client_android_vr_title)
        PlayerStreamClient.WEB_REMIX -> stringResource(R.string.stream_client_web_remix_title)
        PlayerStreamClient.TVHTML5 -> stringResource(R.string.stream_client_tvhtml5_title)
        PlayerStreamClient.WEB_CREATOR -> stringResource(R.string.stream_client_web_creator_title)
        else -> name
    }
