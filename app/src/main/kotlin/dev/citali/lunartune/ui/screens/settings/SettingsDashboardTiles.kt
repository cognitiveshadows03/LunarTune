/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.ui.component.excludeDotTexture
import kotlin.math.cos
import kotlin.math.sin

/**
 * A full-width lead card, then balanced pairs. Two cards share a row only when both titles fit on
 * one line in a half-width tile at the current width and font scale. Narrow phones and large text
 * therefore fall back to full-width cards instead of breaking words.
 */
@Composable
internal fun SettingsDashboardTiles(items: List<SettingsItem>) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileGap = 12.dp
        val tileInnerPx = with(density) {
            ((maxWidth - tileGap) / 2 - DashboardTilePadding * 2).roundToPx()
        }
        fun fitsPairTile(item: SettingsItem): Boolean =
            measurer.measure(item.title, titleStyle, maxLines = 1).size.width <= tileInnerPx

        Column(verticalArrangement = Arrangement.spacedBy(tileGap)) {
            var index = 0
            if (items.isNotEmpty()) {
                SettingsDashboardTile(items.first(), Modifier.fillMaxWidth(), wide = true)
                index = 1
            }
            while (index < items.size) {
                val first = items[index]
                val second = items.getOrNull(index + 1)
                if (second != null && fitsPairTile(first) && fitsPairTile(second)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                        SettingsDashboardTile(first, Modifier.weight(1f).fillMaxHeight())
                        SettingsDashboardTile(second, Modifier.weight(1f).fillMaxHeight())
                    }
                    index += 2
                } else {
                    SettingsDashboardTile(first, Modifier.fillMaxWidth(), wide = true)
                    index++
                }
            }
        }
    }
}

private val DashboardTilePadding = 18.dp

@Composable
private fun SettingsDashboardTile(
    item: SettingsItem,
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
    wide: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (item.key) {
        "appearance", "playback", "updates" -> scheme.primary
        "lyrics", "integration", "internet" -> scheme.tertiary
        else -> scheme.secondary
    }
    val badgeShape = settingsIconShape(item.key)
    val container = when (item.key) {
        "appearance", "playback", "updates" -> scheme.primaryContainer
        "lyrics", "integration", "internet" -> scheme.tertiaryContainer
        else -> scheme.secondaryContainer
    }
    val surface = lerp(scheme.surfaceContainerLow, container, 0.32f)
    val ink = scheme.onSurface
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(28.dp),
        color = surface,
        contentColor = ink,
        modifier = modifier.heightIn(min = if (portrait) 318.dp else if (wide) 164.dp else 172.dp).excludeDotTexture(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().drawWithCache {
                // Exactly the icon's shape, enlarged and clipped by the card surface.
                val edge = size.minDimension * 1.25f
                val outline = badgeShape.createOutline(Size(edge, edge), layoutDirection, this)
                val left = size.width * 0.96f - edge / 2f
                val top = size.height * 0.96f - edge / 2f
                onDrawBehind {
                    withTransform({ translate(left, top) }) {
                        drawOutline(outline, accent.copy(alpha = 0.065f))
                    }
                }
            }.padding(DashboardTilePadding),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(badgeShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(badge = {
                    if (item.showUpdateIndicator) Badge(containerColor = MaterialTheme.colorScheme.error)
                }) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = ink)
                }
            }
            if (portrait) Spacer(Modifier.weight(1f).heightIn(min = 60.dp)) else Spacer(Modifier.height(18.dp))
            Text(
                item.title,
                style = if (portrait || wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            item.badge?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
            }
        }
    }
}
