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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.ReverbDampingKey
import dev.citali.lunartune.constants.ReverbDryKey
import dev.citali.lunartune.constants.ReverbEnabledKey
import dev.citali.lunartune.constants.ReverbLowCutKey
import dev.citali.lunartune.constants.ReverbMixKey
import dev.citali.lunartune.constants.ReverbPreDelayKey
import dev.citali.lunartune.constants.ReverbPresetKey
import dev.citali.lunartune.constants.ReverbRoomSizeKey
import dev.citali.lunartune.constants.ReverbWetKey
import dev.citali.lunartune.constants.ReverbWidthKey
import dev.citali.lunartune.playback.LOW_CUT_MAX_HZ
import dev.citali.lunartune.playback.LOW_CUT_MIN_HZ
import dev.citali.lunartune.playback.ReverbPreset
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.menu.TempoPitchControls
import dev.citali.lunartune.ui.menu.rememberTempoPitchController
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFxSettings(navController: NavController) {
    val tempoPitch = rememberTempoPitchController()

    val (reverbEnabled, onReverbEnabledChange) = rememberPreference(ReverbEnabledKey, defaultValue = false)
    val (presetValue, onPresetChange) = rememberPreference(ReverbPresetKey, defaultValue = ReverbPreset.ROOM.storageValue)
    val (mix, onMixChange) = rememberPreference(ReverbMixKey, defaultValue = ReverbPreset.ROOM.mix)
    val (roomSize, onRoomSizeChange) = rememberPreference(ReverbRoomSizeKey, defaultValue = ReverbPreset.ROOM.roomSize)
    val (damping, onDampingChange) = rememberPreference(ReverbDampingKey, defaultValue = ReverbPreset.ROOM.damping)
    val (wet, onWetChange) = rememberPreference(ReverbWetKey, defaultValue = ReverbPreset.ROOM.wet)
    val (dry, onDryChange) = rememberPreference(ReverbDryKey, defaultValue = ReverbPreset.ROOM.dry)
    val (width, onWidthChange) = rememberPreference(ReverbWidthKey, defaultValue = ReverbPreset.ROOM.width)
    val (preDelay, onPreDelayChange) = rememberPreference(ReverbPreDelayKey, defaultValue = ReverbPreset.ROOM.preDelayMs)
    val (lowCut, onLowCutChange) = rememberPreference(ReverbLowCutKey, defaultValue = ReverbPreset.ROOM.lowCutHz)
    val selectedPreset = ReverbPreset.fromStorage(presetValue)

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
                    if (tempoPitch != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            TempoPitchControls(controller = tempoPitch)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = tempoPitch::reset) {
                                    Text(stringResource(R.string.reset))
                                }
                            }
                        }
                    }
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
                                    onWetChange(preset.wet)
                                    onDryChange(preset.dry)
                                    onWidthChange(preset.width)
                                    onPreDelayChange(preset.preDelayMs)
                                    onLowCutChange(preset.lowCutHz)
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
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onMixChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_room_size),
                        value = roomSize,
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onRoomSizeChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_damping),
                        value = damping,
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onDampingChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_wet),
                        value = wet,
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onWetChange,
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.reverb_fine_tune).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_dry),
                        value = dry,
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onDryChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_width),
                        value = width,
                        range = 0..100,
                        formatValue = { "$it%" },
                        enabled = reverbEnabled,
                        onCommit = onWidthChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_pre_delay),
                        value = preDelay,
                        range = 0..100,
                        formatValue = { "$it ms" },
                        enabled = reverbEnabled,
                        onCommit = onPreDelayChange,
                    )
                }

                item {
                    ReverbSliderRow(
                        title = stringResource(R.string.reverb_low_cut),
                        value = lowCut,
                        range = LOW_CUT_MIN_HZ..LOW_CUT_MAX_HZ,
                        formatValue = { "$it Hz" },
                        enabled = reverbEnabled,
                        onCommit = onLowCutChange,
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
 * An integer slider. It moves freely while dragging and saves once the finger lifts,
 * so the audio effect is not rebuilt on every frame.
 */
@Composable
private fun ReverbSliderRow(
    title: String,
    value: Int,
    range: IntRange,
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
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}
