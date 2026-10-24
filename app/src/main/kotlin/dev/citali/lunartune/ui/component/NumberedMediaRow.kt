/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Height the redesigned list row keeps. Exposed so hosts can reserve the same space. */
internal val NumberedRowHeight = 76.dp

/** Artwork on the redesigned row. */
private val NumberedRowThumbnail = 56.dp
private val NumberedRowThumbnailCorner = 14.dp

/** Reserved for the active marker, so a row never shifts when it becomes the playing one. */
private val NumberedRowMarkerWidth = 3.dp
private val NumberedRowMarkerHeight = 22.dp

/** Position column. Wide enough for three digits before the artwork starts to move. */
private val NumberedRowPositionWidth = 26.dp

/**
 * The row the redesign uses wherever a list is read as a plot rather than a menu: the queue, and
 * the dense variant of the quick picks.
 */
@Composable
internal fun NumberedMediaRow(
    position: Int,
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,
    /** When set, a soft wash fades out behind the title and artist from this colour. */
    textFrost: Color? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
    positionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    thumbnailBorderColor: Color = Color.Transparent,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val thumbShape = RoundedCornerShape(NumberedRowThumbnailCorner)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NumberedRowHeight)
                .padding(start = 8.dp, end = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(NumberedRowMarkerWidth)
                    .height(NumberedRowMarkerHeight)
                    .clip(CircleShape)
                    .background(if (isActive) accentColor else Color.Transparent),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (isActive) {
                    accentColor
                } else {
                    positionColor
                },
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(NumberedRowPositionWidth),
        )
        Spacer(Modifier.width(10.dp))
        ItemThumbnail(
            thumbnailUrl = thumbnailUrl,
            albumIndex = null,
            isSelected = isSelected,
            isActive = isActive,
            isPlaying = isPlaying,
            shouldLoadImage = shouldLoadImage,
            shape = thumbShape,
            modifier =
                Modifier
                    .size(NumberedRowThumbnail)
                    .then(
                        if (thumbnailBorderColor != Color.Transparent) {
                            Modifier.border(
                                width = 0.75.dp,
                                color = thumbnailBorderColor,
                                shape = thumbShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (textFrost != null) {
                            Modifier.drawBehind {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(listOf(textFrost, Color.Transparent)),
                                    cornerRadius = CornerRadius(14.dp.toPx()),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent()
    }
}
