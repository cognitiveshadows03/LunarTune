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

/** A full-width lead card and balanced pairs; long titles always get room to breathe. */
@Composable
internal fun SettingsDashboardTiles(items: List<SettingsItem>) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val roomy = configuration.screenWidthDp >= 380 && configuration.fontScale <= 1.15f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var index = 0
        if (items.isNotEmpty()) {
            SettingsDashboardTile(items.first(), Modifier.fillMaxWidth(), wide = true)
            index = 1
        }
        while (index < items.size) {
            val first = items[index]
            val second = items.getOrNull(index + 1)
            if (roomy && second != null && first.title.length <= 15 && second.title.length <= 15) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val accent = lerp(MaterialTheme.colorScheme.primary, hue, 0.25f)
    val surface = lerp(MaterialTheme.colorScheme.surfaceContainerLow, accent, if (dark) 0.12f else 0.08f)
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(28.dp),
        color = surface,
        contentColor = ink,
        modifier = modifier.heightIn(min = if (portrait) 318.dp else if (wide) 164.dp else 172.dp).excludeDotTexture(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().drawBehind {
                drawCircle(accent.copy(alpha = 0.055f), radius = size.width * 0.48f,
                    center = Offset(size.width, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.035f), radius = size.width * 0.64f,
                    center = Offset(size.width, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }.padding(18.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(settingsIconShape(item.key)).background(accent.copy(alpha = 0.16f)),
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
