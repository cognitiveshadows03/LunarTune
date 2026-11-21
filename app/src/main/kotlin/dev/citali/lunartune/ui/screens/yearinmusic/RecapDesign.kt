/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.yearinmusic

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.player.rememberOfflineArtworkImageRequest
import dev.citali.lunartune.ui.theme.LyricsFontFamily
import kotlin.math.cos
import kotlin.math.exp

// ---------------------------------------------------------------------------------------------
// Palette: one ink per tone. Colour comes from the scene, never from the text.
// ---------------------------------------------------------------------------------------------

/** Whether a scene wants dark or light ink on top of it. */
internal enum class RecapTone { LIGHT, DARK }

internal val RecapInk = Color(0xFF101010)

internal fun RecapTone.ink(): Color = if (this == RecapTone.LIGHT) RecapInk else Color.White

internal fun RecapTone.secondaryInk(): Color = ink().copy(alpha = 0.66f)

// ---------------------------------------------------------------------------------------------
// Scenes: a vertical wash plus three slowly orbiting glows, the way Replay builds its backdrop.
// ---------------------------------------------------------------------------------------------

@Immutable
internal data class RecapScene(
    val base: List<Color>,
    val glows: List<Color>,
    val tone: RecapTone,
)

internal object RecapScenes {
    private val Emerald =
        RecapScene(
            base = listOf(Color(0xFF1F8A6A), Color(0xFF12705A), Color(0xFF0A4A3E)),
            glows = listOf(Color(0xFF43B08C), Color(0xFFB93A4A), Color(0xFF0B3B32)),
            tone = RecapTone.DARK,
        )
    private val UltraViolet =
        RecapScene(
            base = listOf(Color(0xFF6A47E8), Color(0xFF5233C4), Color(0xFF33207E)),
            glows = listOf(Color(0xFF9C7BFF), Color(0xFFE8B27A), Color(0xFF412A9E)),
            tone = RecapTone.DARK,
        )
    private val Smoke =
        RecapScene(
            base = listOf(Color(0xFF23211F), Color(0xFF171615), Color(0xFF0E0D0D)),
            glows = listOf(Color(0xFF8C4A5A), Color(0xFF3A2A30), Color(0xFF0A0909)),
            tone = RecapTone.DARK,
        )
    private val Olive =
        RecapScene(
            base = listOf(Color(0xFF8E9B2E), Color(0xFF77832A), Color(0xFF5C6620)),
            glows = listOf(Color(0xFFB9C64A), Color(0xFF4E8F2E), Color(0xFF6E7A22)),
            tone = RecapTone.LIGHT,
        )
    private val Clay =
        RecapScene(
            base = listOf(Color(0xFFE7A87E), Color(0xFFD99068), Color(0xFFC47552)),
            glows = listOf(Color(0xFFF3C39C), Color(0xFFC0603F), Color(0xFFE7A87E)),
            tone = RecapTone.LIGHT,
        )
    private val Midnight =
        RecapScene(
            base = listOf(Color(0xFF2B3970), Color(0xFF1E2A55), Color(0xFF111834)),
            glows = listOf(Color(0xFF3D5AA8), Color(0xFF1A2450), Color(0xFF0C1228)),
            tone = RecapTone.DARK,
        )

    fun forCard(card: YearInMusicRecapCard): RecapScene =
        when (card) {
            is YearInMusicRecapCard.Empty -> Midnight
            is YearInMusicRecapCard.Intro -> Emerald
            is YearInMusicRecapCard.Totals -> UltraViolet
            is YearInMusicRecapCard.TopSong -> Smoke
            is YearInMusicRecapCard.TopSongs -> Midnight
            is YearInMusicRecapCard.TopArtists -> Olive
            is YearInMusicRecapCard.TopAlbums -> Clay
            is YearInMusicRecapCard.Summary -> UltraViolet
            is YearInMusicRecapCard.Finale -> Midnight
        }
}

/** Drifting backdrop. [phase] comes from the story clock and moves the glows around. */
@Composable
internal fun RecapSceneBackdrop(
    scene: RecapScene,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colors = listOf(scene.base[0], scene.base[1], scene.base[2], scene.base[2]),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val turn = phase * 6.2831855f
            scene.glows.forEachIndexed { index, glow ->
                val angle = turn + index * 2.1f
                val center =
                    Offset(
                        x = size.width * (0.5f + 0.42f * cos(angle)),
                        y = size.height * (0.42f + 0.30f * cos(angle * 0.7f + index * 1.4f)),
                    )
                val radius = size.maxDimension * (0.52f + 0.08f * index)
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.5f), glow.copy(alpha = 0.16f), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                    center = center,
                )
            }
        }
        if (scene.tone == RecapTone.DARK) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.32f),
                            ),
                        ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Motion: one clock per card. Every element eases in against the same spring so the card reads
// as a single choreographed entrance.
// ---------------------------------------------------------------------------------------------

private fun springCurve(t: Float): Float = if (t >= 1f) 1f else 1f - exp(-6.5f * t) * cos(9.5f * t)

internal fun Modifier.recapEntrance(
    motion: () -> Long,
    delay: Int = 0,
    fromX: Float = 0f,
    fromY: Float = 30f,
    initialScale: Float = 1f,
    blur: Float = 12f,
    driftX: Float = 0f,
    driftY: Float = 0f,
): Modifier =
    graphicsLayer {
        val elapsed = motion()
        val fraction = ((elapsed - delay) / 850f).coerceIn(0f, 1f)
        val progress = springCurve(fraction)
        alpha = (fraction * 2.6f).coerceIn(0f, 1f)
        translationY =
            (1f - progress) * fromY.dp.toPx() +
                ((elapsed - delay - 850) / 9_000f).coerceIn(0f, 1f) * driftY.dp.toPx()
        translationX =
            (1f - progress) * fromX.dp.toPx() +
                ((elapsed - delay - 850) / 9_000f).coerceIn(0f, 1f) * driftX.dp.toPx()
        scaleX = initialScale + (1f - initialScale) * progress
        scaleY = scaleX
        val radius = (1f - fraction * 1.7f).coerceIn(0f, 1f) * blur.dp.toPx()
        renderEffect =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) {
                BlurEffect(radius, radius, TileMode.Decal)
            } else {
                null
            }
    }

/** Slow push-in for hero artwork, so a card that sits on screen keeps breathing. */
internal fun Modifier.recapHeroBreath(
    motion: () -> Long,
    initialScale: Float = 1.08f,
): Modifier =
    graphicsLayer {
        val elapsed = motion()
        val settle = (elapsed / 1_400f).coerceIn(0f, 1f)
        val breathe = 1f + 0.02f * (elapsed / 8_000f).coerceIn(0f, 1f)
        val scale = (initialScale + (1f - initialScale) * springCurve(settle)) * breathe
        scaleX = scale
        scaleY = scale
    }

// ---------------------------------------------------------------------------------------------
// Typography: tight bold sans, borrowed from the shipped SF Display cut.
// ---------------------------------------------------------------------------------------------

internal object RecapType {
    val eyebrow =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 1.6.sp,
        )

    val statement =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 33.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.4).sp,
        )

    val statementLarge = statement.copy(fontSize = 39.sp, lineHeight = 45.sp)

    val title =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 35.sp,
            letterSpacing = (-0.3).sp,
        )

    val rowTitle =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
        )

    val rowMeta =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )

    val rank =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
        )

    val caption =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )

    val footer =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
}

// ---------------------------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------------------------

/** Tracked all-caps label that opens a card. */
@Composable
internal fun RecapEyebrow(
    text: String,
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = RecapType.eyebrow,
        color = tone.ink().copy(alpha = if (tone == RecapTone.LIGHT) 0.72f else 0.8f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Cover art with the app's placeholder mark underneath, rounded like Replay's tiles. */
@Composable
internal fun RecapArtwork(
    artwork: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val request = rememberOfflineArtworkImageRequest(artwork)
    Box(
        modifier = modifier.clip(shape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.small_icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Circle variant for artists. */
@Composable
internal fun RecapArtistPortrait(
    artwork: String?,
    modifier: Modifier = Modifier,
) = RecapArtwork(artwork = artwork, modifier = modifier, shape = CircleShape)

/** Hairline divider between ranked rows. */
@Composable
internal fun RecapDivider(
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tone.ink().copy(alpha = 0.14f)),
    )
}

/** Round translucent control for the reel chrome. */
@Composable
internal fun RecapGlassButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tone.ink().copy(alpha = 0.14f),
        contentColor = tone.ink(),
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), label, Modifier.size(19.dp))
        }
    }
}

/** App mark beside the name, how every Replay screen signs off. */
@Composable
internal fun RecapSignature(
    text: String,
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = tone.ink().copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = RecapType.footer,
            color = tone.ink().copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}

/** Segment row at the very top: finished cards filled, the current one filling. */
@Composable
internal fun RecapStoryProgress(
    current: Int,
    count: Int,
    progress: () -> Float,
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val description = stringResource(R.string.recap_progress, current + 1, count)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(tone.ink().copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                scaleX =
                                    when {
                                        index < current -> 1f
                                        index == current -> progress().coerceIn(0f, 1f)
                                        else -> 0f
                                    }
                            }.background(tone.ink()),
                )
            }
        }
    }
}

/** Replay-style pill: a solid light pill for the primary action, plain text for the rest. */
@Composable
internal fun RecapPillButton(
    text: String,
    onClick: () -> Unit,
    tone: RecapTone,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val content = tone.ink()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (filled) content else Color.Transparent,
        contentColor = if (filled) (if (tone == RecapTone.LIGHT) Color.White else RecapInk) else content,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = RecapType.rowTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}
