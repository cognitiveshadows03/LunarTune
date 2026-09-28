/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.component.EnumListPreference
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.ui.theme.MotionBounceKey
import dev.citali.lunartune.ui.theme.MotionPreset
import dev.citali.lunartune.ui.theme.MotionReducedKey
import dev.citali.lunartune.ui.theme.MotionSpeedKey
import dev.citali.lunartune.ui.theme.MotionTuning
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val MotionPressKey = booleanPreferencesKey("motionPress")
val MotionSheetsKey = booleanPreferencesKey("motionSheets")
val MotionMiniKey = booleanPreferencesKey("motionMini")
enum class TabTransitionStyle {
    FADE,
    BLOOM,
    SPRING_SLIDE,
}

val TabTransitionKey = stringPreferencesKey("tabTransition")
val MotionBarKey = booleanPreferencesKey("motionBar")
val MotionListsKey = booleanPreferencesKey("motionLists")
val MotionMicroKey = booleanPreferencesKey("motionMicro")

@Composable
fun MotionSettings(navController: NavController) {
    val (motionPress, onMotionPressChange) = rememberPreference(MotionPressKey, defaultValue = false)
    val (motionSheets, onMotionSheetsChange) = rememberPreference(MotionSheetsKey, defaultValue = false)
    val (motionMini, onMotionMiniChange) = rememberPreference(MotionMiniKey, defaultValue = false)
    val (tabTransition, onTabTransitionChange) =
        rememberEnumPreference(TabTransitionKey, defaultValue = TabTransitionStyle.BLOOM)
    val (motionBar, onMotionBarChange) = rememberPreference(MotionBarKey, defaultValue = false)
    val (motionLists, onMotionListsChange) = rememberPreference(MotionListsKey, defaultValue = false)
    val (motionMicro, onMotionMicroChange) = rememberPreference(MotionMicroKey, defaultValue = false)
    val context = LocalContext.current
    val motionScope = rememberCoroutineScope()
    val (speed, onSpeedChange) = rememberPreference(MotionSpeedKey, defaultValue = MotionTuning.SPEED_DEFAULT)
    val (bounce, onBounceChange) = rememberPreference(MotionBounceKey, defaultValue = MotionTuning.BOUNCE_DEFAULT)
    val (reduced, onReducedChange) = rememberPreference(MotionReducedKey, defaultValue = false)
    val labPreset = remember(speed, bounce, reduced) { MotionTuning.derivePreset(speed, bounce, reduced) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.motion_settings_title)) },
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
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ).windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.motion_lab_title)) {
                item {
                    MotionPresetChips(
                        selected = labPreset,
                        onSelect = { preset ->
                            motionScope.launch {
                                MotionTuning.applyPreset(context.dataStore, preset)
                            }
                        },
                    )
                }

                item {
                    MotionPercentSlider(
                        title = stringResource(R.string.motion_speed),
                        description = stringResource(R.string.motion_speed_desc),
                        value = speed,
                        range = MotionTuning.SPEED_MIN..MotionTuning.SPEED_MAX,
                        onValueChange = {
                            onSpeedChange(it)
                            if (reduced) onReducedChange(false)
                        },
                    )
                }

                item {
                    MotionPercentSlider(
                        title = stringResource(R.string.motion_bounce),
                        description = stringResource(R.string.motion_bounce_desc),
                        value = bounce,
                        range = MotionTuning.BOUNCE_MIN..MotionTuning.BOUNCE_MAX,
                        onValueChange = {
                            onBounceChange(it)
                            if (reduced) onReducedChange(false)
                        },
                    )
                }

                item {
                    MotionPreviewCard(specKey = "$speed:$bounce:$reduced")
                }
            }

            PreferenceGroup(title = stringResource(R.string.motion_settings_title)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_press)) },
                        description = stringResource(R.string.motion_press_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionPress,
                        onCheckedChange = onMotionPressChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_sheets)) },
                        description = stringResource(R.string.motion_sheets_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionSheets,
                        onCheckedChange = onMotionSheetsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_mini)) },
                        description = stringResource(R.string.motion_mini_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionMini,
                        onCheckedChange = onMotionMiniChange,
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.motion_nav)) },
                        description = stringResource(R.string.motion_nav_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        selectedValue = tabTransition,
                        onValueSelected = onTabTransitionChange,
                        valueText = {
                            when (it) {
                                TabTransitionStyle.FADE -> stringResource(R.string.tab_transition_fade)
                                TabTransitionStyle.BLOOM -> stringResource(R.string.tab_transition_bloom)
                                TabTransitionStyle.SPRING_SLIDE -> stringResource(R.string.tab_transition_spring)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_bar)) },
                        description = stringResource(R.string.motion_bar_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionBar,
                        onCheckedChange = onMotionBarChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_lists)) },
                        description = stringResource(R.string.motion_lists_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionLists,
                        onCheckedChange = onMotionListsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.motion_micro)) },
                        description = stringResource(R.string.motion_micro_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = motionMicro,
                        onCheckedChange = onMotionMicroChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionPresetChips(
    selected: MotionPreset,
    onSelect: (MotionPreset) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.motion_preset),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                MotionPreset.SNAPPY,
                MotionPreset.BALANCED,
                MotionPreset.BUTTERY,
                MotionPreset.REDUCED,
            ).forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onSelect(preset) },
                    label = { Text(text = preset.label()) },
                )
            }
            if (selected == MotionPreset.CUSTOM) {
                FilterChip(
                    selected = true,
                    enabled = false,
                    onClick = {},
                    label = { Text(text = stringResource(R.string.motion_preset_custom)) },
                )
            }
        }
    }
}

@Composable
private fun MotionPreset.label(): String =
    when (this) {
        MotionPreset.SNAPPY -> stringResource(R.string.motion_preset_snappy)
        MotionPreset.BALANCED -> stringResource(R.string.motion_preset_balanced)
        MotionPreset.BUTTERY -> stringResource(R.string.motion_preset_buttery)
        MotionPreset.REDUCED -> stringResource(R.string.motion_preset_reduced)
        MotionPreset.CUSTOM -> stringResource(R.string.motion_preset_custom)
    }

@Composable
private fun MotionPercentSlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

private val MotionPreviewTravel = 120.dp

@Composable
private fun MotionPreviewCard(specKey: String) {
    var toggled by remember { mutableStateOf(false) }
    val dotOffset by animateDpAsState(
        targetValue = if (toggled) MotionPreviewTravel else 0.dp,
        animationSpec = remember(specKey) { LunarMotion.bouncy<Dp>() },
        label = "motionLabPreview",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.motion_preview),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                .clickable { toggled = !toggled },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 12.dp + dotOffset)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.motion_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
