/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.LocalAnimationsDisabled

object SettingsDimensions {
    val GroupCardCornerRadius = 16.dp
    val BannerCardCornerRadius = 20.dp

    val ScreenHorizontalPadding = 16.dp

    /** Width at or below which the screens are treated as narrow and the gutter tightens. */
    const val NarrowWindowWidthDp = 360

    /** The gutter that actually gets drawn: narrower on a narrow window, unchanged above it. */
    val AdaptiveScreenHorizontalPadding: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            if (LocalConfiguration.current.screenWidthDp <= NarrowWindowWidthDp) {
                8.dp
            } else {
                ScreenHorizontalPadding
            }

    val ScreenBottomPadding = 32.dp
    val SectionSpacing = 14.dp
    val SegmentedGroupHorizontalPadding = 26.dp

    /**
     * The group gutter, tightened on narrow windows. 26dp is a deliberate inset on a normal
     * phone - it lines the cards up under the section headers - but it is 13% of the width of a
     * 320dp screen spent on margin before a single row is drawn.
     */
    val AdaptiveSegmentedGroupHorizontalPadding: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            if (LocalConfiguration.current.screenWidthDp <= NarrowWindowWidthDp) {
                16.dp
            } else {
                SegmentedGroupHorizontalPadding
            }
    val SegmentedItemGap = 2.dp
    val RowVerticalPadding = 14.dp
    val RowHorizontalPadding = 16.dp

    val RowIconSize = 36.dp
    val RowIconInnerSize = 20.dp
    val BannerIconSize = 44.dp
    val BannerIconInnerSize = 22.dp
    val ChevronSize = 18.dp

    val ProfileCardAvatarSize = 56.dp
    val ProfileCardAvatarIconSize = 28.dp

    val DividerThickness = 0.5.dp
    val DividerStartIndent = 60.dp

    val SectionHeaderBottomPadding = 6.dp
    val SectionHeaderHorizontalPadding = 20.dp

    /**
     * Widest the settings content is allowed to get.
     *
     * Everything here is laid out in dp against the width of the window, so on a phone the
     * groups simply fill the screen and the cap never applies. It only bites on a tablet, a
     * foldable's inner screen or a desktop window, where a settings row stretched across 1200dp
     * puts the switch a forearm away from its label. Capping the column and centring it is the
     * same thing the About screen already does, and the same thing large-screen guidance asks
     * for: one comfortable reading measure instead of an edge-to-edge stretch.
     */
    val ScreenContentMaxWidth = 840.dp
}

object SettingsAnimations {
    val PressScale = 0.97f

    @Composable
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (LocalAnimationsDisabled.current) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        }
}
