/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AppLockBiometricUnlockKey
import dev.citali.lunartune.constants.AppLockEnabledKey
import dev.citali.lunartune.constants.AppLockPinHashKey
import dev.citali.lunartune.constants.AppLockPinLength
import dev.citali.lunartune.constants.AppLockPinLengthKey
import dev.citali.lunartune.constants.AppLockType
import dev.citali.lunartune.constants.AppLockTypeKey
import dev.citali.lunartune.ui.component.DefaultDialog
import dev.citali.lunartune.ui.component.IconButton as AppIconButton
import dev.citali.lunartune.ui.component.PinLockScreen
import dev.citali.lunartune.ui.component.PreferenceEntry
import dev.citali.lunartune.ui.component.PreferenceGroup
import dev.citali.lunartune.ui.component.SegmentedPreference
import dev.citali.lunartune.ui.component.SwitchPreference
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.SecurityUtils
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference

/**
 * Picks how the app is locked: the device screen lock, a custom PIN, or nothing.
 *
 * Ported from June — DenserMeerkat/June (GPL-3.0) — and rebuilt with the
 * settings components the rest of the app uses, so it inherits the theme and
 * dynamic colour like every other settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(navController: NavController) {
    val context = LocalContext.current
    val (lockEnabled, onLockEnabledChange) = rememberPreference(AppLockEnabledKey, defaultValue = false)
    val (lockType, onLockTypeChange) = rememberEnumPreference(AppLockTypeKey, defaultValue = AppLockType.NONE)
    val (pinHash, onPinHashChange) = rememberPreference(AppLockPinHashKey, defaultValue = "")
    val (pinLength, onPinLengthChange) =
        rememberEnumPreference(AppLockPinLengthKey, defaultValue = AppLockPinLength.SIX)
    val (biometricUnlock, onBiometricUnlockChange) =
        rememberPreference(AppLockBiometricUnlockKey, defaultValue = false)

    val pinSet = pinHash.isNotBlank()
    val screenLockAvailable = remember { SecurityUtils.canUseScreenLock(context) }
    var pendingChange by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Switching away from a configured PIN throws it away, so confirm first.
    val applyLockChange: (() -> Unit) -> Unit = { action ->
        if (lockEnabled && lockType == AppLockType.PIN && pinSet) {
            pendingChange = action
        } else {
            action()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lock_your_music)) },
                navigationIcon = {
                    AppIconButton(
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
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            Text(
                text = stringResource(R.string.lock_music_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            PreferenceGroup(title = stringResource(R.string.ways_to_lock)) {
                item {
                    LockMethodEntry(
                        title = stringResource(R.string.same_as_screen_lock),
                        description = stringResource(R.string.same_as_screen_lock_desc),
                        icon = { Icon(painterResource(R.drawable.fingerprint), null) },
                        selected = lockEnabled && lockType == AppLockType.BIOMETRIC,
                        onClick = {
                            applyLockChange {
                                if (!screenLockAvailable) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.fingerprint_unavailable),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } else {
                                    onLockTypeChange(AppLockType.BIOMETRIC)
                                    onLockEnabledChange(true)
                                }
                            }
                        },
                    )
                }

                item {
                    LockMethodEntry(
                        title = stringResource(R.string.custom_pin),
                        description = if (pinSet) stringResource(R.string.pin_digits, pinLength.digits) else null,
                        icon = { Icon(painterResource(R.drawable.password), null) },
                        selected = lockEnabled && lockType == AppLockType.PIN && pinSet,
                        onClick = { navController.navigate("settings/pin_setup") },
                    )
                }

                item {
                    LockMethodEntry(
                        title = stringResource(R.string.no_lock),
                        icon = { Icon(painterResource(R.drawable.no_encryption), null) },
                        selected = !lockEnabled || (lockType == AppLockType.PIN && !pinSet),
                        onClick = {
                            applyLockChange {
                                onLockEnabledChange(false)
                                onLockTypeChange(AppLockType.NONE)
                            }
                        },
                    )
                }
            }

            if (lockEnabled && lockType == AppLockType.PIN && pinSet) {
                Spacer(modifier = Modifier.height(16.dp))

                ImportantNote(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                PreferenceGroup {
                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.unlock_with_fingerprint)) },
                            description = stringResource(R.string.unlock_with_fingerprint_desc),
                            icon = { Icon(painterResource(R.drawable.fingerprint), null) },
                            checked = biometricUnlock && screenLockAvailable,
                            onCheckedChange = { enabled ->
                                if (!enabled || screenLockAvailable) {
                                    onBiometricUnlockChange(enabled)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.fingerprint_unavailable),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.change_pin)) },
                            icon = { Icon(painterResource(R.drawable.password), null) },
                            onClick = { navController.navigate("settings/pin_setup") },
                        )
                    }

                    item {
                        SegmentedPreference(
                            title = { Text(stringResource(R.string.pin_length)) },
                            description = stringResource(R.string.pin_length_desc),
                            icon = { Icon(painterResource(R.drawable.password), null) },
                            selectedValue = pinLength,
                            values = listOf(AppLockPinLength.FOUR, AppLockPinLength.SIX),
                            valueText = { stringResource(R.string.pin_digits, it.digits) },
                            onValueSelected = { length ->
                                if (length != pinLength) {
                                    onPinLengthChange(length)
                                    onPinHashChange("")
                                    navController.navigate("settings/pin_setup")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (pendingChange != null) {
        DefaultDialog(
            onDismiss = { pendingChange = null },
            title = { Text(stringResource(R.string.change_lock_title)) },
            buttons = {
                TextButton(onClick = { pendingChange = null }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        pendingChange?.invoke()
                        pendingChange = null
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.change_lock_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LockMethodEntry(
    title: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    PreferenceEntry(
        title = { Text(title) },
        description = description,
        icon = icon,
        trailingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
        onClick = onClick,
    )
}

@Composable
private fun ImportantNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.app_lock_important),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_lock_pin_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class SetupStep { CREATE, CONFIRM }

/**
 * Two step PIN creation: type it, then type it again.
 *
 * Ported from June — DenserMeerkat/June (GPL-3.0).
 */
@Composable
fun PinSetupScreen(navController: NavController) {
    val (lockEnabled, onLockEnabledChange) = rememberPreference(AppLockEnabledKey, defaultValue = false)
    val (lockType, onLockTypeChange) = rememberEnumPreference(AppLockTypeKey, defaultValue = AppLockType.NONE)
    val (pinHash, onPinHashChange) = rememberPreference(AppLockPinHashKey, defaultValue = "")
    val (pinLength) = rememberEnumPreference(AppLockPinLengthKey, defaultValue = AppLockPinLength.SIX)

    var step by remember { mutableStateOf(SetupStep.CREATE) }
    var firstPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    key(step) {
        PinLockScreen(
            title =
                if (step == SetupStep.CREATE) {
                    stringResource(R.string.create_pin)
                } else {
                    stringResource(R.string.confirm_pin)
                },
            isError = isError,
            maxPinLength = pinLength.digits,
            onPinSubmitted = { pin ->
                when (step) {
                    SetupStep.CREATE -> {
                        firstPin = pin
                        step = SetupStep.CONFIRM
                        isError = false
                    }

                    SetupStep.CONFIRM -> {
                        if (pin == firstPin) {
                            onPinHashChange(SecurityUtils.hashPin(pin))
                            onLockTypeChange(AppLockType.PIN)
                            if (!lockEnabled || lockType != AppLockType.PIN) {
                                onLockEnabledChange(true)
                            }
                            navController.navigateUp()
                        } else {
                            isError = true
                            step = SetupStep.CREATE
                            firstPin = ""
                        }
                    }
                }
            },
        )
    }
}
