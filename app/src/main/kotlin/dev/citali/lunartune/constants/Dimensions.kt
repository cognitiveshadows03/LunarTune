/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val NavigationBarHorizontalPadding = 12.dp
val NavigationBarBottomPadding = 10.dp
val NavigationBarMaxWidth = 420.dp
val NavigationBarHeight = 78.dp
val FloatingNavigationBarHorizontalPadding = 24.dp
val FloatingNavigationBarBottomPadding = 16.dp
val FloatingNavigationBarMaxWidth = 360.dp
val MiniPlayerHeight = 70.dp
val MiniPlayerBottomSpacing = 4.dp
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 72.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 56.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 10.dp
val GridThumbnailCornerRadius = 8.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

// Ported from 4nx3b/ArchiveTune: a bouncy main spec gives the mini player its
// signature bounce on expand, fling-collapse and dismiss.
val BottomSheetAnimationSpec =
    spring<Dp>(
        dampingRatio = 0.85f,
        stiffness = 400f,
    )

// Calm fallback used when the "Mini player bounce" motion toggle is off:
// the original no-bounce spec.
val BottomSheetCalmAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

val BottomSheetCollapseAnimationSpec =
    spring<Dp>(
        dampingRatio = 0.85f,
        stiffness = 600f,
    )

val BottomSheetSoftAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
