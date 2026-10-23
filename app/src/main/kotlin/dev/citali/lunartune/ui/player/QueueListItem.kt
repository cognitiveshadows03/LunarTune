/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.ui.component.ItemThumbnail
import dev.citali.lunartune.ui.component.MediaMetadataListItem
import dev.citali.lunartune.utils.makeTimeString

/** Height the new queue row keeps, a little tighter than the shared 72 dp list item. */
private val QueueRowHeight = 64.dp

/** Artwork on the new queue row: smaller than the shared list thumbnail, so the row reads denser. */
private val QueueRowThumbnail = 48.dp
private val QueueRowThumbnailCorner = 9.dp

/** Reserved for the active marker, so a row never shifts when it becomes the playing one. */
private val QueueRowMarkerWidth = 3.dp
private val QueueRowMarkerHeight = 18.dp

/** Position column. Wide enough for three digits before the artwork starts to move. */
private val QueueRowPositionWidth = 24.dp

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
        QueueListItem(
            mediaMetadata = mediaMetadata,
            position = position,
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

/**
 * The redesigned queue row: the track's place in the queue on the left, then artwork, title and
 * artist, and whatever the caller puts at the end.
 *
 * Deliberately quiet. The only colour the row spends is the primary colour on the playing track —
 * a short marker beside its position and its title — and the playing indicator the artwork already
 * draws. No containers, no gradients, no per-row elevation: the queue is a long list that is read
 * while music plays, so the rows stay flat and let the artwork do the rest.
 */
@Composable
internal fun QueueListItem(
    mediaMetadata: MediaMetadata,
    position: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val subtitle =
        remember(mediaMetadata.artists, mediaMetadata.duration) {
            val artists = mediaMetadata.artists.joinToString { it.name }
            if (mediaMetadata.duration > 0 && artists.isNotBlank()) {
                "$artists · ${makeTimeString(mediaMetadata.duration * 1000L)}"
            } else if (mediaMetadata.duration > 0) {
                makeTimeString(mediaMetadata.duration * 1000L)
            } else {
                artists
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = QueueRowHeight)
                .padding(start = 6.dp, end = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(QueueRowMarkerWidth)
                    .height(QueueRowMarkerHeight)
                    .clip(CircleShape)
                    .background(if (isActive) accent else Color.Transparent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(QueueRowPositionWidth),
        )
        Spacer(Modifier.width(8.dp))
        ItemThumbnail(
            thumbnailUrl = mediaMetadata.thumbnailUrl,
            albumIndex = null,
            isSelected = isSelected,
            isActive = isActive,
            isPlaying = isPlaying,
            shouldLoadImage = shouldLoadImage,
            shape = RoundedCornerShape(QueueRowThumbnailCorner),
            modifier = Modifier.size(QueueRowThumbnail),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent()
    }
}
