/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.component.MediaMetadataListItem
import dev.citali.lunartune.ui.component.NumberedMediaRow
import dev.citali.lunartune.utils.makeTimeString

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
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    if (newUi) {
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
        NumberedMediaRow(
            position = position,
            title = mediaMetadata.title,
            subtitle = subtitle,
            thumbnailUrl = mediaMetadata.thumbnailUrl,
            isSelected = isSelected,
            isActive = isActive,
            isPlaying = isPlaying,
            shouldLoadImage = shouldLoadImage,
            modifier = modifier,
            trailingContent = trailingContent,
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
