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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.lunartune.ui.component.excludeDotTexture
import kotlin.math.cos
import kotlin.math.sin

/** Three-item runs form a portrait tile beside two stacked tiles; no nested scrolling. */
@Composable
internal fun SettingsDashboardTiles(items: List<SettingsItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var index = 0
        while (index < items.size) {
            val remaining = items.size - index
            if (remaining >= 3) {
                val tall = items[index]
                val top = items[index + 1]
                val bottom = items[index + 2]
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsDashboardTile(tall, Modifier.weight(1f).fillMaxHeight(), portrait = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsDashboardTile(top, Modifier.fillMaxWidth())
                        SettingsDashboardTile(bottom, Modifier.fillMaxWidth())
                    }
                }
                index += 3
            } else if (remaining == 2) {
                // A wide card breaks up the masonry rather than ending every section in squares.
                SettingsDashboardTile(items[index], Modifier.fillMaxWidth(), wide = true)
                SettingsDashboardTile(items[index + 1], Modifier.fillMaxWidth(), wide = true)
                index += 2
            } else {
                SettingsDashboardTile(items[index], Modifier.fillMaxWidth(), wide = true)
                index++
            }
        }
    }
}

@Composable
private fun SettingsDashboardTile(
    item: SettingsItem,
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
    wide: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hue = when (item.key) {
        "appearance" -> Color(0xFF8544BC)
        "playback", "internet" -> Color(0xFF287D9C)
        "lyrics", "downloads" -> Color(0xFF426CB0)
        "content", "storage" -> Color(0xFFAC6935)
        "behavior", "backup_restore" -> Color(0xFF40885C)
        "integration", "ai_integration" -> Color(0xFF9E477A)
        else -> MaterialTheme.colorScheme.primary
    }
    val surface = if (dark) lerp(Color(0xFF131116), hue, 0.55f) else lerp(Color.White, hue, 0.16f)
    val ink = if (dark) lerp(Color.White, hue, 0.18f) else lerp(Color(0xFF17121D), hue, 0.2f)
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(28.dp),
        color = surface,
        contentColor = ink,
        modifier = modifier.heightIn(min = if (portrait) 318.dp else if (wide) 172.dp else 154.dp).excludeDotTexture(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().drawBehind {
                // A quiet, clipped petal silhouette echoes the category's expressive badge.
                val center = Offset(size.width * 0.98f, size.height * 0.95f)
                val radius = size.width * 0.48f
                val path = Path()
                for (i in 0..120) {
                    val angle = i / 120f * 6.283185f
                    val r = radius * (1f + 0.12f * sin(angle * 7f))
                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, ink.copy(alpha = 0.055f))
            }.padding(18.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(settingsIconShape(item.key)).background(ink.copy(alpha = 0.12f)),
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
                style = if (portrait || wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            if ((portrait || wide) && !item.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.76f))
            }
            item.badge?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
            }
        }
    }
}
