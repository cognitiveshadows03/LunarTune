/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.yearinmusic

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.ui.player.rememberOfflineArtworkImageRequest
import dev.citali.lunartune.ui.theme.LyricsFontFamily
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

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
    // The reel is one bright family: every page is a warm, high-key wash in the same register as
    // the cover, and the ink stays near-black throughout. The colours change page to page so the
    // reel still has chapters, but none of them drop the lights, because a recap that switches
    // from cream to near-black and back reads as two different features stitched together.
    //
    // Each scene keeps its own hue — the warmth of Totals, the green of Top Artists, the clay of
    // Top Albums — and each is simply lifted: the darks that used to run to 0x0E now stop around
    // 0xC0, which is what keeps a page readable as "bright" without washing the ink out.

    /** The cover: the warmest, palest page, and the one the closing page answers. */
    private val Parchment =
        RecapScene(
            base = listOf(Color(0xFFFBF0DF), Color(0xFFF6E3CB), Color(0xFFEFD2B1)),
            glows = listOf(Color(0xFFFFFBF2), Color(0xFFF7C9A4), Color(0xFFE9D3B4)),
            tone = RecapTone.LIGHT,
        )

    /**
     * Totals: sky. The page that carries the year's headline numbers was reading as a warmer
     * repeat of the cover, so it takes the one hue the rest of the reel does not use. Cool
     * against the warm cover and the warm clay further down, and still high-key.
     */
    private val Sky =
        RecapScene(
            base = listOf(Color(0xFFE7F1FB), Color(0xFFD3E5F7), Color(0xFFBBD6F0)),
            glows = listOf(Color(0xFFF8FBFF), Color(0xFF9CC4E8), Color(0xFFD8E9F8)),
            tone = RecapTone.LIGHT,
        )

    /** Top songs: a cool mint, so the tracks page is not another warm one. */
    private val Mint =
        RecapScene(
            base = listOf(Color(0xFFE6F4E9), Color(0xFFD3EBDC), Color(0xFFBEDFCB)),
            glows = listOf(Color(0xFFF7FDF8), Color(0xFFA9DCBE), Color(0xFFCDE8D6)),
            tone = RecapTone.LIGHT,
        )

    /** Top artists: soft sage, the olive page brought up into daylight. */
    private val Sage =
        RecapScene(
            base = listOf(Color(0xFFEEF1D6), Color(0xFFE2E7BF), Color(0xFFD0D8A6)),
            glows = listOf(Color(0xFFFAFCEF), Color(0xFFC2CE8A), Color(0xFFE4E9C6)),
            tone = RecapTone.LIGHT,
        )

    /** Top albums: clay, lifted until the covers sit on it rather than sink into it. */
    private val Clay =
        RecapScene(
            base = listOf(Color(0xFFFBE0CB), Color(0xFFF7CFB2), Color(0xFFF1BC99)),
            glows = listOf(Color(0xFFFFF3E7), Color(0xFFF0A077), Color(0xFFF7CEB2)),
            tone = RecapTone.LIGHT,
        )

    /** The empty state: still bright, but a quieter page, since there is nothing to celebrate. */
    private val Linen =
        RecapScene(
            base = listOf(Color(0xFFF6F2EA), Color(0xFFEDE7DC), Color(0xFFE2DACD)),
            glows = listOf(Color(0xFFFFFDF9), Color(0xFFDCD3C4), Color(0xFFEFEAE1)),
            tone = RecapTone.LIGHT,
        )

    // The streak is parked while it is reworked, but it keeps a scene so bringing it back needs
    // no work here. It uses the same warm register as everything else.
    private val Smoke =
        RecapScene(
            base = listOf(Color(0xFFF4E9E4), Color(0xFFEBDAD3), Color(0xFFE0C8BE)),
            glows = listOf(Color(0xFFFDF6F3), Color(0xFFD9AFA4), Color(0xFFEFDFD9)),
            tone = RecapTone.LIGHT,
        )

    /** The closing page: the cover's own light, a note warmer. */
    private val Dawn =
        RecapScene(
            base = listOf(Color(0xFFF6E7CF), Color(0xFFEFD3B4), Color(0xFFE3B394)),
            glows = listOf(Color(0xFFFFF6E6), Color(0xFFECA07E), Color(0xFFF7DCC0)),
            tone = RecapTone.LIGHT,
        )

    fun forCard(card: YearInMusicRecapCard): RecapScene =
        when (card) {
            is YearInMusicRecapCard.Empty -> Linen
            is YearInMusicRecapCard.Intro -> Parchment
            is YearInMusicRecapCard.Totals -> Sky
            is YearInMusicRecapCard.LongestStreak -> Smoke
            is YearInMusicRecapCard.TopSongs -> Mint
            is YearInMusicRecapCard.TopArtists -> Sage
            is YearInMusicRecapCard.TopAlbums -> Clay
            is YearInMusicRecapCard.Summary -> Dawn
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

internal fun springCurve(t: Float): Float = if (t >= 1f) 1f else 1f - exp(-6.5f * t) * cos(9.5f * t)

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

    /** The totals sentence: the largest statement in the reel. */
    val heroStatement =
        statement.copy(
            fontSize = 44.sp,
            lineHeight = 51.sp,
            letterSpacing = (-0.7).sp,
        )

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

/**
 * App mark beside the name, how every Replay screen signs off.
 *
 * The mark is the app's own icon. The artwork is a transparent 月 glyph drawn in near-black,
 * so it is tinted to the scene's ink instead of being placed in a white circle that would read
 * as a stray blob on a dark page.
 */
@Composable
internal fun RecapSignature(
    text: String,
    tone: RecapTone,
    modifier: Modifier = Modifier,
    markSize: Dp = 16.dp,
) {
    Row(
        modifier = modifier.semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.lunartune_brand_round),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tone.ink().copy(alpha = 0.82f)),
            modifier = Modifier.size(markSize),
        )
        Text(
            text = text,
            style = RecapType.footer,
            color = tone.ink().copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Editorial primitives: mosaic, ghost art and outlined ranks
// ---------------------------------------------------------------------------------------------

/**
 * Outlined numeral set behind the ranked lists.
 *
 * The reference lets its type be bigger than the card; a filled numeral that size would swallow
 * the rows, so the rank is drawn as an outline with a faint fill — present, enormous, and never
 * in the way of a song title.
 *
 * The outline is the numeral drawn a ring of times behind itself, which keeps it independent of
 * any text-stroke API and identical across platform versions.
 */
@Composable
internal fun RecapGhostNumeral(
    text: String,
    tone: RecapTone,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 92.sp,
    fillAlpha: Float = 0.12f,
    strokeAlpha: Float = 0.4f,
) {
    val ink = tone.ink()
    val style =
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            lineHeight = fontSize,
            letterSpacing = (-0.06).em,
        )
    val outline =
        Modifier.drawWithContent {
            val radius = 1.4.dp.toPx()
            for (step in 0 until OutlineRingSteps) {
                val angle = step * 6.2831855f / OutlineRingSteps
                translate(left = cos(angle) * radius, top = sin(angle) * radius) {
                    this@drawWithContent.drawContent()
                }
            }
        }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style,
            color = ink.copy(alpha = strokeAlpha),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = outline,
        )
        Text(
            text = text,
            style = style,
            color = ink.copy(alpha = fillAlpha),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private const val OutlineRingSteps = 12

/**
 * Display type that is measured, not guessed.
 *
 * The reference's numbers are sized to the card, not the other way round: the figure grows until
 * it *just* fits the width and is cropped by the edges only when it is meant to be. [AutoResizeText]
 * shrinks on height overflow alone, which would let a long figure run off the side, so the size is
 * picked here by measuring candidate sizes against the real constraints.
 */
@Composable
internal fun RecapFitText(
    text: String,
    style: TextStyle,
    color: Color,
    minSize: TextUnit,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    softWrap: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val annotated = remember(text) { AnnotatedString(text) }
    BoxWithConstraints(modifier = modifier) {
        val layoutWidth = constraints.maxWidth
        val fontSize: Float =
            remember(annotated, layoutWidth, style, minSize, maxSize, maxLines, softWrap, measurer) {
                if (layoutWidth <= 0) {
                    minSize.value
                } else {
                    // Seven bisection passes land within a quarter of a point of the largest
                    // size that still fits, which is finer than the eye can resolve.
                    var low = minSize.value
                    var high = maxSize.value.coerceAtLeast(low)
                    repeat(FitPasses) {
                        val mid = (low + high) / 2f
                        val measured =
                            measurer.measure(
                                text = annotated,
                                style = style.copy(fontSize = mid.sp, lineHeight = (mid * style.lineHeightFactor()).sp),
                                maxLines = maxLines,
                                softWrap = softWrap,
                                overflow = TextOverflow.Clip,
                                constraints = Constraints(maxWidth = layoutWidth),
                            )
                        if (measured.hasVisualOverflow) high = mid else low = mid
                    }
                    low
                }
            }
        Text(
            text = text,
            style = style.copy(fontSize = fontSize.sp, lineHeight = (fontSize * style.lineHeightFactor()).sp),
            color = color,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Share of the font size a style spends on a line, kept when fitting so leading stays proportional. */
private fun TextStyle.lineHeightFactor(): Float =
    if (fontSize.value > 0f && lineHeight.type == TextUnitType.Sp) (lineHeight.value / fontSize.value).coerceIn(0.8f, 1.4f) else 1f

private const val FitPasses = 7

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
