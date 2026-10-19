/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.ui.theme.LunarMotion
import dev.citali.lunartune.utils.rememberPreference

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    internal var dialogContent by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(content: @Composable ColumnScope.() -> Unit) {
        dialogContent = null
        isVisible = true
        this.content = content
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss() {
        isVisible = false
    }

    fun showDialog(content: @Composable () -> Unit) {
        isVisible = false
        dialogContent = content
    }

    fun dismissDialog() {
        dialogContent = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val focusManager = LocalFocusManager.current

    state.dialogContent?.invoke()

    if (state.isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            },
            modifier = modifier.fillMaxHeight(),
        ) {
            // The menu's own content settles on a spring instead of simply being there
            // when the sheet finishes rising. Scale grows out of the bottom edge - where
            // the sheet comes from - and a light overshoot gives the panel the "settles
            // into place" feel rather than the flat stop a tween produces.
            //
            // The content is already fully composed by the time this runs (menus use a
            // plain Column now, not a LazyColumn), so nothing pops in mid-spring.
            val appear = remember { Animatable(0f) }
            val animationsDisabled = LocalAnimationsDisabled.current

            // Where the finger was, in this panel's own coordinate space. The panel
            // grows outward from that column, the way ColorOS menus unfold from the
            // element that was touched, falling back to bottom-centre when no touch was
            // recorded (keyboard, accessibility action, deep link).
            val contentBounds = remember { mutableStateOf<Rect?>(null) }

            LaunchedEffect(Unit) {
                if (animationsDisabled) {
                    appear.snapTo(1f)
                } else {
                    appear.animateTo(1f, LunarMotion.settle())
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .onGloballyPositioned { contentBounds.value = it.boundsInWindow() }
                        .graphicsLayer {
                            val p = appear.value
                            val scale = 0.94f + 0.06f * p
                            scaleX = scale
                            scaleY = scale
                            alpha = p.coerceIn(0f, 1f)

                            // Resolved in the draw phase, not in composition: the origin
                            // is a pure function of the bounds and the recorded touch, so
                            // nothing needs to be recomposed to find it.
                            val bounds = contentBounds.value
                            val tap = TapAnchor.touchDownOrNull()
                            val originX =
                                if (bounds != null && tap != null && bounds.width > 0f) {
                                    ((tap.x - bounds.left) / bounds.width).coerceIn(0.08f, 0.92f)
                                } else {
                                    0.5f
                                }
                            transformOrigin = TransformOrigin(originX, 1f)
                        },
            ) {
                state.content(this)
            }
        }
    }
}
