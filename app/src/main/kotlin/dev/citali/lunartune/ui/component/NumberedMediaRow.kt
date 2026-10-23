/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Height the redesigned list row keeps, a little tighter than the shared 72 dp list item. */
private val NumberedRowHeight = 64.dp

/** Artwork on the redesigned row: smaller than the shared list thumbnail, so the row reads denser. */
private val NumberedRowThumbnail = 48.dp
private val NumberedRowThumbnailCorner = 9.dp

/** Reserved for the active marker, so a row never shifts when it becomes the playing one. */
private val NumberedRowMarkerWidth = 3.dp
private val NumberedRowMarkerHeight = 18.dp

/** Position column. Wide enough for three digits before the artwork starts to move. */
private val NumberedRowPositionWidth = 24.dp

/**
 * The row the redesign uses wherever a list is read as a plot rather than a menu: the queue, speed
 * dial, and later recommended mixes.
 *
 * The track's place on the left, then artwork, title and subtitle, then whatever the caller puts at
 * the end. Deliberately quiet: the only colour it spends is the primary colour on the playing
 * item — a short marker beside the position and a tint on the position and title. No containers, no
 * gradients, no per-row elevation. These are long lists read while music plays, so they stay flat
 * and let the artwork do the rest.
 */
@Composable
internal fun NumberedMediaRow(
    position: Int,
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    /** Replaces the artwork when the row does not stand for a piece of media, such as shuffle. */
    thumbnail: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NumberedRowHeight)
                .padding(start = 6.dp, end = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(NumberedRowMarkerWidth)
                    .height(NumberedRowMarkerHeight)
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
            modifier = Modifier.width(NumberedRowPositionWidth),
        )
        Spacer(Modifier.width(8.dp))
        if (thumbnail != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(NumberedRowThumbnail)
                        .clip(RoundedCornerShape(NumberedRowThumbnailCorner)),
            ) {
                thumbnail()
            }
        } else {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shouldLoadImage = shouldLoadImage,
                shape = RoundedCornerShape(NumberedRowThumbnailCorner),
                modifier = Modifier.size(NumberedRowThumbnail),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent()
    }
}
