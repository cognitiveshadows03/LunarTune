/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.ui.screens.Screens
import dev.citali.lunartune.ui.theme.LunarMotion

/**
 * "Orbit dock": the capsule navbar style.
 *
 * Structure ported from LastWave-native's MainShell FloatingNavBar/FloatingNavItem
 * (github.com/Clash-Projects/LastWave-native): 32dp dock, 48dp pill items,
 * spring size-morph with expand/shrink label pushes. Adapted with LunarMotion
 * springs (tighter than their floaty 200-stiffness default), kept press bounce,
 * long-press actions and search double-tap, plus our glass edge and soft ambient shadow.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CapsuleNavigationBar(
    items: List<Screens>,
    modifier: Modifier = Modifier,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onItemLongClick: ((Screens) -> Unit)? = null,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 0.dp,
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .animateContentSize(LunarMotion.bouncy())
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Color.Black.copy(alpha = 0.4f),
                        spotColor = Color.Transparent,
                    ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Fake glass edge: a 1dp gradient sheen, API-29 safe.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.7f)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    items.forEach { screen ->
                        DockItem(
                            screen = screen,
                            selected = isSelected(screen),
                            onItemClick = onItemClick,
                            onItemLongClick = onItemLongClick,
                            onSearchItemDoubleClick = onSearchItemDoubleClick,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    screen: Screens,
    selected: Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onItemLongClick: ((Screens) -> Unit)?,
    onSearchItemDoubleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) LunarMotion.PressedScale else 1f,
        animationSpec = LunarMotion.press(),
        label = "dockPressScale",
    )
    val pillColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
        animationSpec = LunarMotion.smooth(),
        label = "dockPillColor",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = LunarMotion.smooth(),
        label = "dockContentColor",
    )
    val lastClickTime = remember(screen) { mutableLongStateOf(0L) }
    val onDoubleClick =
        remember(screen, onSearchItemDoubleClick) {
            if (screen == Screens.Search) onSearchItemDoubleClick else null
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(48.dp)
                .animateContentSize(LunarMotion.bouncy())
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(CircleShape)
                .background(pillColor)
                .combinedClickable(
                    onClick = {
                        val now = SystemClock.uptimeMillis()
                        val isDoubleClick =
                            onDoubleClick != null &&
                                now - lastClickTime.longValue <= ViewConfiguration.getDoubleTapTimeout()
                        lastClickTime.longValue = if (isDoubleClick) 0L else now
                        if (isDoubleClick) {
                            onDoubleClick.invoke()
                        } else {
                            onItemClick(screen, selected)
                        }
                    },
                    onLongClick =
                        onItemLongClick?.let { longClick ->
                            { longClick(screen) }
                        },
                    interactionSource = interactionSource,
                    indication = null,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .padding(horizontal = if (selected) 18.dp else 12.dp)
                    .height(48.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (selected) screen.iconIdActive else screen.iconIdInactive,
                    ),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(
                visible = selected,
                enter =
                    fadeIn(LunarMotion.bouncy()) +
                        expandHorizontally(
                            animationSpec = LunarMotion.bouncy(),
                            expandFrom = Alignment.Start,
                        ),
                exit =
                    fadeOut(tween(90)) +
                        shrinkHorizontally(
                            animationSpec = LunarMotion.bouncy(),
                            shrinkTowards = Alignment.Start,
                        ),
            ) {
                Text(
                    text = stringResource(screen.titleId),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
