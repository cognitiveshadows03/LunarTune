/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import dev.citali.lunartune.constants.AppLockPinLength
import dev.citali.lunartune.constants.AppLockPinLengthKey
import dev.citali.lunartune.constants.AppLockType
import dev.citali.lunartune.utils.SecurityUtils
import dev.citali.lunartune.utils.rememberEnumPreference
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.citali.lunartune.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full screen PIN pad used to unlock the app and to set a new PIN.
 *
 * Ported from June — DenserMeerkat/June (GPL-3.0) — and restyled to follow the
 * app theme, so the filled dots and the keypad pick up whatever the user's
 * dynamic colour or custom theme colour is.
 */
@Composable
fun PinLockScreen(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.enter_pin),
    isError: Boolean = false,
    maxPinLength: Int = 6,
    onBiometricClick: (() -> Unit)? = null,
    onPinSubmitted: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var waveTrigger by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isError) {
        if (isError) {
            pin = ""
            waveTrigger++
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }

    LaunchedEffect(pin) {
        if (pin.length == maxPinLength) {
            delay(DELAY_AUTO_SUBMIT)
            onPinSubmitted(pin)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val buttonSize = if (screenWidth < 360.dp) 64.dp else if (screenWidth < 400.dp) 72.dp else 80.dp
        val horizontalGap = if (screenWidth < 360.dp) 12.dp else if (screenWidth < 400.dp) 18.dp else 24.dp
        val verticalGap = if (screenHeight < 640.dp) 8.dp else 16.dp

        val spacerHeight1 = if (screenHeight < 640.dp) 12.dp else 24.dp
        val spacerHeight2 = if (screenHeight < 640.dp) 24.dp else 48.dp
        val middleSpacerHeight = if (screenHeight < 640.dp) 24.dp else 48.dp

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(vertical = if (screenHeight < 640.dp) 24.dp else 48.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconDisplay()

                Spacer(modifier = Modifier.height(spacerHeight1))

                Text(
                    text = if (isError) stringResource(R.string.wrong_pin_try_again) else title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(spacerHeight2))

                PinIndicatorRow(pinLength = pin.length, maxPinLength = maxPinLength)
            }

            Spacer(modifier = Modifier.height(middleSpacerHeight))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(verticalGap),
            ) {
                NumberPad(
                    pinLength = pin.length,
                    waveTrigger = waveTrigger,
                    buttonSize = buttonSize,
                    horizontalGap = horizontalGap,
                    verticalGap = verticalGap,
                    onBiometricClick = onBiometricClick,
                    onNumberClick = { number ->
                        if (pin.length < maxPinLength) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pin += number
                        }
                    },
                    onDeleteClick = {
                        if (pin.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pin = pin.dropLast(1)
                        }
                    },
                    onClearAll = {
                        if (pin.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pin = ""
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppIconDisplay() {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scale(1.25f),
        )
    }
}

@Composable
private fun PinIndicatorRow(
    pinLength: Int,
    maxPinLength: Int,
) {
    // toShape() is a composable call, so the shapes are built during composition
    // rather than inside a remember block.
    val expressiveShapes =
        listOf(
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Pentagon,
            MaterialShapes.Pill,
            MaterialShapes.Ghostish,
            MaterialShapes.Diamond,
            MaterialShapes.Clover4Leaf,
        ).map { it.toShape() }

    val shapesForIndices =
        remember(maxPinLength) {
            List(maxPinLength) { index -> expressiveShapes[index % expressiveShapes.size] }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(64.dp),
    ) {
        repeat(maxPinLength) { index ->
            PinDot(
                isFilled = index < pinLength,
                isLastInput = index == pinLength - 1,
                popShape = shapesForIndices[index],
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isLastInput: Boolean,
    popShape: Shape,
) {
    var shapeState by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(isFilled) {
        if (isFilled) {
            isInitialized = true
            if (isLastInput) {
                shapeState = 1
                scale.animateTo(1.5f, tween(150, easing = FastOutSlowInEasing))
                scale.animateTo(0.8f, tween(100))
                shapeState = 0
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            } else {
                shapeState = 0
                scale.snapTo(1f)
            }
        } else {
            if (isInitialized) {
                scale.animateTo(0.5f, tween(50))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            shapeState = 0
            isInitialized = true
        }
    }

    val currentShape = if (shapeState == 1) popShape else CircleShape
    val animatedSize by animateDpAsState(
        targetValue = if (shapeState == 1) 24.dp else 16.dp,
        label = "size",
    )

    Box(
        modifier =
            Modifier
                .size(36.dp)
                .scale(scale.value),
        contentAlignment = Alignment.Center,
    ) {
        if (isFilled) {
            Box(
                modifier =
                    Modifier
                        .size(animatedSize)
                        .clip(currentShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun NumberPad(
    pinLength: Int,
    waveTrigger: Int,
    buttonSize: Dp,
    horizontalGap: Dp,
    verticalGap: Dp,
    onBiometricClick: (() -> Unit)? = null,
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearAll: () -> Unit,
) {
    val buttonScales = remember { List(10) { Animatable(1f) } }

    LaunchedEffect(waveTrigger) {
        if (waveTrigger > 0) {
            val sequence = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
            sequence.forEachIndexed { index, num ->
                launch {
                    delay(index * DELAY_WAVE_STAGGER)
                    buttonScales[num].animateTo(0.8f, tween(100))
                    buttonScales[num].animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    )
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(verticalGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        (1..9).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                row.forEach { number ->
                    NumberButton(number.toString(), buttonScales[number].value, buttonSize, onNumberClick)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
            PadButton(
                onClick = onDeleteClick,
                onLongClick = onClearAll,
                backgroundColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                enabled = pinLength > 0,
                buttonSize = buttonSize,
            ) {
                Icon(
                    painterResource(R.drawable.backspace),
                    contentDescription = null,
                    modifier = Modifier.size(buttonSize * 0.45f),
                )
            }
            NumberButton("0", buttonScales[0].value, buttonSize, onNumberClick)

            if (onBiometricClick != null) {
                PadButton(
                    onClick = onBiometricClick,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    buttonSize = buttonSize,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fingerprint),
                        contentDescription = null,
                        modifier = Modifier.size(buttonSize * 0.45f),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(buttonSize))
            }
        }
    }
}

@Composable
private fun NumberButton(
    number: String,
    externalScale: Float,
    buttonSize: Dp,
    onClick: (String) -> Unit,
) {
    PadButton(
        onClick = { onClick(number) },
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        externalScale = externalScale,
        buttonSize = buttonSize,
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            fontSize = if (buttonSize < 72.dp) 22.sp else 28.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Color,
    contentColor: Color,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    externalScale: Float = 1f,
    buttonSize: Dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "pressScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, label = "alpha")

    Surface(
        modifier =
            Modifier
                .size(buttonSize)
                .scale(pressScale * externalScale)
                .alpha(alpha)
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private const val DELAY_AUTO_SUBMIT = 100L
private const val DELAY_WAVE_STAGGER = 30L

/**
 * Full screen gate drawn over the whole app while it is locked.
 *
 * With a PIN it shows the PIN pad, optionally with a fingerprint key that opens
 * the system authentication sheet. With "same as screen lock" the system sheet
 * is the only way in.
 */
@Composable
fun AppLockGate(
    lockType: AppLockType,
    pinHash: String,
    biometricUnlock: Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val (pinLength) = rememberEnumPreference(AppLockPinLengthKey, defaultValue = AppLockPinLength.SIX)
    var pinError by remember { mutableStateOf(false) }

    BackHandler {
        // Staying locked is the point, back must not dismiss the gate.
    }

    val launchScreenLock = {
        if (activity != null) {
            SecurityUtils.showScreenLockPrompt(
                activity = activity,
                title = context.getString(R.string.app_lock),
                onSuccess = onUnlocked,
                onError = { },
            )
        }
    }

    when (lockType) {
        AppLockType.BIOMETRIC -> {
            LaunchedEffect(Unit) { launchScreenLock() }
            ScreenLockGate(onUnlockClick = { launchScreenLock() })
        }

        AppLockType.PIN -> {
            val fingerprintAvailable = biometricUnlock && SecurityUtils.canUseScreenLock(context)
            LaunchedEffect(Unit) {
                if (fingerprintAvailable) launchScreenLock()
            }
            PinLockScreen(
                title = stringResource(R.string.enter_pin),
                isError = pinError,
                maxPinLength = pinLength.digits,
                onBiometricClick = if (fingerprintAvailable) ({ launchScreenLock() }) else null,
                onPinSubmitted = { pin ->
                    if (SecurityUtils.hashPin(pin) == pinHash) {
                        onUnlocked()
                    } else {
                        pinError = true
                    }
                },
            )
        }

        AppLockType.NONE -> Unit
    }
}

@Composable
private fun ScreenLockGate(onUnlockClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AppIconDisplay()

            Text(
                text = stringResource(R.string.app_lock),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )

            Button(onClick = onUnlockClick) {
                Text(stringResource(R.string.action_unlock))
            }
        }
    }
}
