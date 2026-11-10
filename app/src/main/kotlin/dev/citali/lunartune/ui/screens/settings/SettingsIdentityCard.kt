/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.theme.rememberProfileCardColors
import kotlin.math.sin

@Composable
internal fun SettingsIdentityCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connectedBelow: Boolean = false,
    bannerModel: Any? = null,
) {
    var bannerLoaded by remember(bannerModel) { mutableStateOf(false) }
    val titleInk = if (bannerLoaded) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryInk = if (bannerLoaded) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val colors = rememberProfileCardColors(imageUrl)
    val accent = colors.accent ?: MaterialTheme.colorScheme.primary
    val deep = colors.deep ?: MaterialTheme.colorScheme.secondaryContainer
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = if (connectedBelow) 6.dp else 28.dp, bottomEnd = if (connectedBelow) 6.dp else 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Box {
            if (bannerModel != null) {
                AsyncImage(
                    model = bannerModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onSuccess = { bannerLoaded = true },
                    onError = { bannerLoaded = false },
                )
                if (bannerLoaded) {
                    Box(Modifier.matchParentSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.66f), Color.Black.copy(alpha = 0.42f))),
                    ))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().drawBehind {
                    if (!bannerLoaded) {
                        drawRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), deep.copy(alpha = 0.12f), Color.Transparent)))
                        for (row in -1..7) {
                            val path = Path()
                            for (segment in 0..40) {
                                val x = size.width * segment / 40f
                                val y = size.height * row / 6f + sin(segment / 40f * 6.28f + row) * size.height * 0.12f
                                if (segment == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, accent.copy(alpha = 0.08f), style = Stroke(0.7.dp.toPx()))
                        }
                    }
                }.padding(20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(painterResource(R.drawable.account), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = titleInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
        }
        }
    }
}

/** The listening shortcut shares the dashboard's tonal surface rather than a mismatched list row. */
@Composable
internal fun SettingsListeningShortcut(item: SettingsItem, modifier: Modifier = Modifier) {
    Surface(
        onClick = item.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary, 0.10f),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(painterResource(R.drawable.navigate_next), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
