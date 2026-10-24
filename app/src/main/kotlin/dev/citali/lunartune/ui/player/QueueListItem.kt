/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.component.MediaMetadataListItem
import dev.citali.lunartune.ui.component.NumberedMediaRow
import dev.citali.lunartune.utils.makeTimeString

private val QueueRowHighlightCorner = 18.dp

/**
 * Picks the queue row to draw. The redesigned row is behind the developer "New UI" switch, so the
 * queue can keep the shared list item until the rest of the redesign catches up.
 */
@Composable
internal fun QueueRow(
    newUi: Boolean,
    mediaMetadata: MediaMetadata,
    position: Int,
    isSelected: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    shouldLoadImage: Boolean,
    modifier: Modifier = Modifier,
    glassPalette: QueueGlassPalette? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    if (newUi && glassPalette != null) {
        val subtitle =
            remember(mediaMetadata.artists, mediaMetadata.duration) {
                val artists = mediaMetadata.artists.joinToString { it.name }
                when {
                    mediaMetadata.duration > 0 && artists.isNotBlank() ->
                        "$artists · ${makeTimeString(mediaMetadata.duration * 1000L)}"

                    mediaMetadata.duration > 0 -> makeTimeString(mediaMetadata.duration * 1000L)
                    else -> artists
                }
            }

        val highlightShape = RoundedCornerShape(QueueRowHighlightCorner)
        val rowHighlightModifier =
            when {
                isSelected ->
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(highlightShape)
                        .background(brush = glassPalette.rowSelectedBrush, shape = highlightShape)
                        .border(width = 1.dp, color = glassPalette.rowSelectedBorder, shape = highlightShape)

                isActive ->
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(highlightShape)
                        .background(brush = glassPalette.rowActiveBrush, shape = highlightShape)
                        .border(width = 1.dp, color = glassPalette.rowActiveBorder, shape = highlightShape)

                else ->
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clip(highlightShape)
            }

        NumberedMediaRow(
            position = position,
            title = mediaMetadata.title,
            subtitle = subtitle,
            thumbnailUrl = mediaMetadata.thumbnailUrl,
            isSelected = isSelected,
            isActive = isActive,
            isPlaying = isPlaying,
            shouldLoadImage = shouldLoadImage,
            modifier = rowHighlightModifier.then(modifier),
            textFrost = null,
            accentColor = glassPalette.accent,
            titleColor = glassPalette.rowTitleColor,
            subtitleColor = glassPalette.rowSubtitleColor,
            positionColor = glassPalette.rowPositionColor,
            thumbnailBorderColor =
                if (isActive) glassPalette.rowActiveBorder else glassPalette.artworkBorder,
            trailingContent = {
                CompositionLocalProvider(
                    LocalContentColor provides glassPalette.rowTrailingIconColor,
                ) {
                    trailingContent()
                }
            },
        )
    } else {
        MediaMetadataListItem(
            mediaMetadata = mediaMetadata,
            isSelected = isSelected,
            isActive = isActive,
            isPlaying = isPlaying,
            shouldLoadImage = shouldLoadImage,
            modifier = modifier,
            trailingContent = trailingContent,
        )
    }
}
