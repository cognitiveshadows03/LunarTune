/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.PlaybackSpeedKey
import dev.citali.lunartune.constants.ReverbDampingKey
import dev.citali.lunartune.constants.ReverbEnabledKey
import dev.citali.lunartune.constants.ReverbMixKey
import dev.citali.lunartune.constants.ReverbPreDelayKey
import dev.citali.lunartune.constants.ReverbPresetKey
import dev.citali.lunartune.constants.ReverbRoomSizeKey
import dev.citali.lunartune.playback.ReverbPreset
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.menu.TempoPitchDialog
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFxSettings(navController: NavController) {
    val playbackSpeed by rememberPreference(PlaybackSpeedKey, 1f)
    var showTempoDialog by rememberSaveable { mutableStateOf(false) }

    val (reverbEnabled, onReverbEnabledChange) = rememberPreference(ReverbEnabledKey, defaultValue = false)
    val (presetValue, onPresetChange) = rememberPreference(ReverbPresetKey, defaultValue = ReverbPreset.ROOM.storageValue)
    val (mix, onMixChange) = rememberPreference(ReverbMixKey, defaultValue = ReverbPreset.ROOM.mix)
    val (roomSize, onRoomSizeChange) = rememberPreference(ReverbRoomSizeKey, defaultValue = ReverbPreset.ROOM.roomSize)
    val (damping, onDampingChange) = rememberPreference(ReverbDampingKey, defaultValue = ReverbPreset.ROOM.damping)
    val (preDelay, onPreDelayChange) = rememberPreference(ReverbPreDelayKey, defaultValue = ReverbPreset.ROOM.preDelayMs)
    val selectedPreset = ReverbPreset.fromStorage(presetValue)

    if (showTempoDialog) {
        TempoPitchDialog(onDismiss = { showTempoDialog = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.audio_fx_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.settings_playback_title)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.playback_speed_label)) },
                        description = String.format(Locale.US, "%.2fx", playbackSpeed),
                        icon = { Icon(painterResource(R.drawable.speed), null) },
                        onClick = { showTempoDialog = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.reverb_title)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.reverb_title)) },
                        description = stringResource(R.string.reverb_desc),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        checked = reverbEnabled,
                        onCheckedChange = onReverbEnabledChange,
                    )
                }

                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReverbPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                enabled = reverbEnabled,
                                onClick = {
                                    onPresetChange(preset.storageValue)
                                    onMixChange(preset.mix)
                                    onRoomSizeChange(preset.roomSize)
                                    onDampingChange(preset.damping)
                                    onPreDelayChange(preset.preDelayMs)
                                },
                                label = { Text(stringResource(presetLabelRes(preset))) },
                            )
                        }
                    }
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_mix),
                        value = mix,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onMixChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_room_size),
                        value = roomSize,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onRoomSizeChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_damping),
                        value = damping,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onDampingChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_pre_delay),
                        value = preDelay,
                        formatValue = { "$it ms" },
                        enabled = reverbEnabled,
                        onCommit = onPreDelayChange,
                    )
                }
            }
        }
    }
}

private fun presetLabelRes(preset: ReverbPreset): Int =
    when (preset) {
        ReverbPreset.ROOM -> R.string.reverb_preset_room
        ReverbPreset.STUDIO -> R.string.reverb_preset_studio
        ReverbPreset.PLATE -> R.string.reverb_preset_plate
        ReverbPreset.HALL -> R.string.reverb_preset_hall
    }

/**
 * A 0–100 slider. It moves freely while dragging and saves once the finger lifts,
 * so the audio effect is not rebuilt on every frame.
 */
@Composable
private fun ReverbSliderRow(
    title: String,
    value: Int,
    formatValue: (Int) -> String,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = formatValue(sliderValue.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCommit(sliderValue.roundToInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
        )
    }
}
