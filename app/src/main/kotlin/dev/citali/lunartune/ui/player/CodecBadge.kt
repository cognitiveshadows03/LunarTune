/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.R
import dev.citali.lunartune.db.entities.FormatEntity
import dev.citali.lunartune.db.entities.codecLabel

/** a stream is lossless when its codec is FLAC or ALAC, whatever the container says */
internal fun FormatEntity.isLosslessStream(): Boolean {
    val raw = codecs.ifBlank { mimeType }.uppercase()
    return raw.contains("FLAC") || raw.contains("ALAC")
}

/** the name the badge shows: "Lossless", "OPUS", "AAC", the container when nothing else fits */
internal fun FormatEntity.codecBadgeLabel(): String {
    val label = codecLabel()
    return label.takeIf { it.isNotBlank() } ?: mimeType.substringAfter("/").uppercase()
}

/**
 * The stream's badge: the lossless mark when the file really is lossless, the level mark
 * otherwise, always beside the name of what is playing.
 */
@Composable
internal fun CodecBadge(
    format: FormatEntity,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val lossless = format.isLosslessStream()
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.1f),
        border = BorderStroke(width = 1.dp, color = tint.copy(alpha = 0.13f)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (lossless) R.drawable.ic_lossless_wave else R.drawable.graphic_eq,
                    ),
                contentDescription = null,
                tint = tint.copy(alpha = 0.72f),
                modifier = Modifier.size(if (lossless) 16.dp else 15.dp),
            )
            Text(
                text = format.codecBadgeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}
