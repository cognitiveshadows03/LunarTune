/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalFoundationApi::class)

package dev.citali.lunartune.ui.screens.yearinmusic

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.citali.lunartune.R
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.SongWithStats
import dev.citali.lunartune.ui.component.AutoResizeText
import dev.citali.lunartune.ui.component.FontSizeRange
import dev.citali.lunartune.ui.player.rememberOfflineArtworkImageRequest
import dev.citali.lunartune.utils.makeTimeString
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardPadding = 22.dp
private val FloatingControlsClearance = 92.dp

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatCount(value: Int): String = formatCount(value.toLong())

/** Plural "99 plays" for a song. */
@Composable
private fun playsLabel(plays: Long): String {
    val count = plays.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(R.plurals.recap_plays_value, count.coerceAtLeast(1), formatCount(plays))
}

/** Shared page scaffold: safe insets, steady card margins and share-button clearance. */
@Composable
private fun RecapPage(
    applySafeContentInsets: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(
                    start = CardPadding,
                    end = CardPadding,
                    top = CardPadding,
                    bottom = if (applySafeContentInsets) FloatingControlsClearance else CardPadding,
                ),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Intro
// ---------------------------------------------------------------------------------------------

@Composable
internal fun IntroRecapCard(
    card: YearInMusicRecapCard.Intro,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = tone.ink()
    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecapSignature(
                text = stringResource(R.string.app_name),
                tone = tone,
                modifier = Modifier.recapEntrance(motion, blur = 8f),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                // Giant year, autosized so four digits always fit the narrowest phone.
                AutoResizeText(
                    text = card.year.toString(),
                    fontSizeRange = FontSizeRange(min = 56.sp, max = 132.sp),
                    style = RecapType.statementLarge.copy(fontSize = 132.sp, lineHeight = 132.sp),
                    fontWeight = FontWeight.Black,
                    color = ink,
                    maxLines = 1,
                    modifier =
                        Modifier.recapEntrance(motion, delay = 90, fromY = 44f, initialScale = 0.92f, blur = 18f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.year_in_music_recap_word),
                    style = RecapType.statementLarge,
                    fontWeight = FontWeight.Black,
                    color = ink.copy(alpha = 0.92f),
                    lineHeight = 44.sp,
                    modifier = Modifier.recapEntrance(motion, delay = 200, fromY = 26f, blur = 14f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.year_in_music_intro_hero),
                    style = RecapType.statement,
                    color = ink,
                    modifier = Modifier.recapEntrance(motion, delay = 320, blur = 12f),
                )
                Text(
                    text = stringResource(R.string.year_in_music_intro_details),
                    style = RecapType.rowMeta.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, delay = 420, blur = 10f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.year_in_music_tap_begin),
                    style = RecapType.rowTitle.copy(fontSize = 15.sp),
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, delay = 520, blur = 8f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Totals
// ---------------------------------------------------------------------------------------------

/**
 * The year in two numbers.
 *
 * The reference leads its totals page with one figure set so large that it runs off the edges of
 * the card, over a collage of its own artwork. Here the lead is the listening time in the largest
 * unit that still says something — "5d 2h" once a day is on the clock, "18h 40m" before that —
 * with the residue in a smaller line beneath, exactly the way the reference's number spills onto
 * a second line instead of being explained in prose.
 */
@Composable
internal fun TotalsRecapCard(
    card: YearInMusicRecapCard.Totals,
    scene: RecapScene,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = tone.ink()
    val lead = remember(card.totalListeningTime) { listeningTimeLead(card.totalListeningTime) }
    val tail = remember(card.totalListeningTime) { listeningTimeTail(card.totalListeningTime) }
    val playsText = formatCount(card.totalSongsPlayed)
    val mosaic = remember(card.floating) { card.floating.map { it.artwork } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        RecapArtMosaic(
            artworks = mosaic,
            tiles = TotalsMosaicTiles,
            motion = motion,
            modifier = Modifier.fillMaxSize(),
        )

        // The collage keeps the page's own scene under it rather than being replaced by it: a
        // violet wash at partial strength turns five unrelated covers into one backdrop, and a
        // deeper foot holds the footer's contrast without dimming the art the number sits on.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to scene.base[0].copy(alpha = 0.66f),
                            0.42f to scene.base[1].copy(alpha = 0.54f),
                            1.00f to scene.base[2].copy(alpha = 0.76f),
                        ),
                    ),
        )

        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecapEyebrow(
                    text = stringResource(R.string.total_listening_time),
                    tone = tone,
                    modifier = Modifier.recapEntrance(motion, blur = 8f),
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Both figures are pulled past the page's own margins so they bleed off the
                    // card the way the reference's type does.
                    RecapFitText(
                        text = lead,
                        style = DisplayStyle,
                        color = ink,
                        minSize = 56.sp,
                        maxSize = 150.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .offset(x = DisplayBleed)
                                .recapEntrance(motion, delay = 90, fromY = 46f, initialScale = 0.94f, blur = 18f),
                    )
                    // The residue and the unit sentence sit together as one quiet block beneath
                    // the figure, so the page has a single display line and one readable line
                    // under it — the reference's hierarchy, without a third competing headline.
                    Row(
                        modifier =
                            Modifier
                                .offset(x = DisplayBleed)
                                .padding(top = 2.dp)
                                .recapEntrance(motion, delay = 200, blur = 8f),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = tail,
                            style = RecapType.statementLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                            fontWeight = FontWeight.Bold,
                            color = ink.copy(alpha = 0.9f),
                            letterSpacing = (-1).sp,
                        )
                        Text(
                            text = stringResource(R.string.year_in_music_of_music),
                            style = RecapType.rowMeta.copy(fontSize = 17.sp, lineHeight = 22.sp),
                            color = ink.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(34.dp))
                    // The play count is the page's second number and gets the same treatment:
                    // enormous, flush with the lead, with its noun set inline.
                    Row(
                        modifier = Modifier.fillMaxWidth().offset(x = DisplayBleed),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        RecapFitText(
                            text = playsText,
                            style = DisplayStyle.copy(fontSize = 80.sp, lineHeight = 76.sp),
                            color = ink,
                            minSize = 30.sp,
                            maxSize = 80.sp,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .recapEntrance(motion, delay = 320, fromY = 30f, blur = 14f),
                        )
                        // The noun rides the numeral's baseline instead of floating beside its
                        // centre, which keeps the pair reading as one figure with a unit.
                        Text(
                            text = stringResource(R.string.year_in_music_songs_word),
                            style = RecapType.rowTitle.copy(fontSize = 21.sp, lineHeight = 24.sp),
                            fontWeight = FontWeight.Bold,
                            color = ink.copy(alpha = 0.86f),
                            modifier =
                                Modifier
                                    .padding(start = 12.dp, bottom = 10.dp)
                                    .recapEntrance(motion, delay = 380, blur = 8f),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.year_in_music_totals_title),
                    style = RecapType.footer,
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.offset(x = DisplayBleed).recapEntrance(motion, delay = 460, blur = 6f),
                )
            }
        }
    }
}

/** Display face for the page's figures: black weight, tight tracking, near-solid leading. */
private val DisplayStyle =
    RecapType.statementLarge.copy(
        fontSize = 150.sp,
        lineHeight = 140.sp,
        letterSpacing = (-7).sp,
    )

/** How far the figures hang past the page margin, in dp. */
private val DisplayBleed = (-8).dp

/**
 * The collage under the totals number: five covers butted together, two of them peeking in from
 * just off the sides so the page never looks framed.
 */
private val TotalsMosaicTiles =
    listOf(
        RecapMosaicTile(-0.18f, 0.34f, 0.02f, 0.30f, radius = 0.dp, revealDelay = 40),
        RecapMosaicTile(0.36f, 0.72f, 0.05f, 0.26f, radius = 6.dp, revealDelay = 110),
        RecapMosaicTile(0.74f, 1.16f, 0.00f, 0.22f, radius = 0.dp, revealDelay = 170),
        RecapMosaicTile(-0.12f, 0.28f, 0.58f, 0.86f, radius = 6.dp, revealDelay = 230),
        RecapMosaicTile(0.60f, 1.14f, 0.62f, 0.96f, radius = 0.dp, revealDelay = 290),
    )

/** "[h]h [m]m" once an hour is on the clock, "[m]m [s]s" before that. */
private fun listeningTimeLead(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (totalMinutes >= 60L) "${hours}h ${minutes}m" else "${minutes}m"
}

/** What does not fit the lead line: seconds, always; under an hour they move up to the tail. */
private fun listeningTimeTail(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val seconds = ((milliseconds / 1_000L) % 60L).coerceAtLeast(0L)
    return if (totalMinutes >= 60L) "%02ds".format(seconds) else "%02ds".format(seconds)
}

// ---------------------------------------------------------------------------------------------
// #1 track
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TopSongRecapCard(
    card: YearInMusicRecapCard.TopSong,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onLongClick: () -> Unit,
) {
    val ink = Color.White
    val imageModel = rememberOfflineArtworkImageRequest(card.song.thumbnailUrl)
    // Replay paints this page with the cover's own hue; fall back to plain black if the
    // cover yields nothing usable.
    val tint = rememberDominantColor(card.song.thumbnailUrl)

    // Only the long press belongs to this page: the reel's own tap handling is disabled here
    // (tapAdvances = false), so a tap on the cover never flips the page out from under a menu.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(RecapScenes.forCard(card).base.first())
                .pointerInput(card.song.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(26.dp)
                    .recapHeroBreath(motion, initialScale = 1.16f),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                if (tint != null) {
                                    listOf(
                                        tint.copy(alpha = 0.34f),
                                        tint.copy(alpha = 0.72f),
                                        tint.copy(alpha = 0.92f),
                                    )
                                } else {
                                    listOf(
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.72f),
                                        Color.Black.copy(alpha = 0.95f),
                                    )
                                },
                        ),
                    ),
        )
        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "#1 ${stringResource(R.string.year_in_music_top_track)}",
                    style = RecapType.eyebrow,
                    color = ink.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .recapEntrance(motion, blur = 8f)
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .widthIn(max = 300.dp)
                                .fillMaxWidth(0.78f)
                                .recapEntrance(motion, delay = 100, fromY = 48f, initialScale = 0.86f, blur = 0f)
                                .recapHeroBreath(motion, initialScale = 1.02f),
                    ) {
                        RecapArtwork(
                            artwork = card.song.thumbnailUrl,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    AutoResizeText(
                        text = card.song.title,
                        fontSizeRange = FontSizeRange(min = 20.sp, max = 34.sp),
                        style = RecapType.title,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.recapEntrance(motion, delay = 260, fromY = 22f, blur = 10f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playsLabel(card.plays),
                        style = RecapType.rowMeta,
                        color = ink.copy(alpha = 0.72f),
                        modifier = Modifier.recapEntrance(motion, delay = 340, fromY = 18f, blur = 8f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().recapEntrance(motion, delay = 420, blur = 6f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.year_in_music_top_pick),
                        style = RecapType.footer,
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = makeTimeString(card.milliseconds),
                        style = RecapType.footer,
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Dominant colour of an artwork URL, straight from the cover.
 *
 * Replay paints its top-track page with the album art, so the page inherits the cover's own
 * hue. The request runs through Coil's cache, so the very image the card draws is reused.
 */
@Composable
private fun rememberDominantColor(artwork: String?): Color? {
    val context = LocalContext.current
    var color by remember(artwork) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artwork) {
        if (artwork.isNullOrBlank()) return@LaunchedEffect
        color = withContext(Dispatchers.Default) { dominantColor(context, artwork) }
    }
    return color
}

private suspend fun dominantColor(
    context: Context,
    artwork: String,
): Color? =
    runCatching {
        val request =
            ImageRequest
                .Builder(context)
                .data(artwork)
                .allowHardware(false)
                .size(112)
                .build()
        val result = context.imageLoader.execute(request)
        if (result !is SuccessResult) return null
        val palette = Palette.from(result.image.toBitmap()).maximumColorCount(16).generate()
        val swatch =
            palette.darkMutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
        swatch?.rgb?.let(::Color)
    }.getOrNull()

// ---------------------------------------------------------------------------------------------
// Ranked songs
// ---------------------------------------------------------------------------------------------

/**
 * The five tracks that owned the year, in one editorial ladder.
 *
 * The reference makes its type do the work: numerals bigger than the rows that hold them, art
 * cropped hard into the page. Here the leader gets a full-width cover with its rank set ghosted
 * behind the title, and ranks two to five sit on hairline rules with oversized outlined numerals
 * hanging off the left gutter.
 */
@Composable
internal fun TopSongsRecapCard(
    card: YearInMusicRecapCard.TopSongs,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = Color.White
    val songs = card.songs.take(5)
    val leader = songs.firstOrNull()
    val rest = songs.drop(1)
    val tint = rememberDominantColor(leader?.thumbnailUrl)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (leader != null) {
            // The leader's own cover is the page: blurred, pushed back, and washed with the
            // cover's own dark swatch so the white type stays legible whatever the art is.
            AsyncImage(
                model = rememberOfflineArtworkImageRequest(leader.thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp).recapHeroBreath(motion, initialScale = 1.2f),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to Color.Black.copy(alpha = 0.62f),
                                0.34f to (tint ?: Color.Black).copy(alpha = 0.72f),
                                0.70f to Color.Black.copy(alpha = 0.88f),
                                1.00f to Color.Black.copy(alpha = 0.96f),
                            ),
                        ),
            )
        }

        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            // The page is measured before it is laid out: on a tall phone every row fits at its
            // natural size, and on a short one the leader's cover concedes height first so the
            // five ranks never get pushed past the share button.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxHeight < 660.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.top_songs).uppercase(),
                        style = RecapType.eyebrow.copy(fontSize = 17.sp, letterSpacing = 2.6.sp),
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        modifier = Modifier.recapEntrance(motion, blur = 10f),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    if (leader != null) {
                        LeaderSong(song = leader, ink = ink, motion = motion, compact = compact)
                    }
                    if (rest.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(if (compact) 16.dp else 22.dp))
                        rest.forEachIndexed { index, song ->
                            if (index > 0) {
                                RecapDivider(
                                    tone = RecapTone.DARK,
                                    modifier = Modifier.padding(vertical = if (compact) 6.dp else 9.dp),
                                )
                            }
                                RankedSongRow(
                                rank = index + 2,
                                song = song,
                                tone = RecapTone.DARK,
                                compact = compact,
                                modifier =
                                    Modifier.recapEntrance(
                                        motion,
                                        delay = 300 + index * 80,
                                        fromX = 24f,
                                        fromY = 0f,
                                        blur = 6f,
                                    ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.year_in_music_top_songs_footer),
                        style = RecapType.footer,
                        color = ink.copy(alpha = 0.66f),
                        modifier = Modifier.recapEntrance(motion, delay = 640, blur = 6f),
                    )
                }
            }
        }
    }
}

/**
 * Rank one: an offset spread, the way the reference composes its pages.
 *
 * A giant cover on one side and the number and facts stacked on the other, with a ghosted "1"
 * bleeding off the top corner and the title hanging beneath the facts. Nothing is centred; the
 * asymmetry is the point.
 */
@Composable
private fun LeaderSong(
    song: SongWithStats,
    ink: Color,
    motion: () -> Long,
    compact: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth().recapEntrance(motion, delay = 120, fromY = 34f, blur = 0f)) {
        RecapGhostNumeral(
            text = "1",
            tone = RecapTone.DARK,
            fontSize = 176.sp,
            fillAlpha = 0.16f,
            strokeAlpha = 0.5f,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 26.dp, y = (-40).dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1.18f)
                            .aspectRatio(if (compact) 1.3f else 1f)
                            .shadow(elevation = 26.dp, shape = RoundedCornerShape(10.dp), clip = false),
                ) {
                    RecapArtwork(
                        artwork = song.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(0.82f).align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = formatCount(song.songCountListened.toLong()),
                        style = RecapType.statementLarge.copy(fontSize = 44.sp, lineHeight = 46.sp),
                        fontWeight = FontWeight.Black,
                        color = ink,
                        letterSpacing = (-2).sp,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.year_in_music_plays_short).uppercase(),
                        style = RecapType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 2.sp),
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RecapDivider(tone = RecapTone.DARK)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = makeTimeString(song.timeListened ?: 0L),
                        style = RecapType.caption.copy(fontSize = 14.sp),
                        color = ink.copy(alpha = 0.82f),
                        maxLines = 2,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = song.title,
                style = RecapType.title.copy(fontSize = 33.sp, lineHeight = 37.sp),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recapEntrance(motion, delay = 220, blur = 10f),
            )
        }
    }
}

@Composable
private fun RankedSongRow(
    rank: Int,
    song: SongWithStats,
    tone: RecapTone,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The numeral hangs off the gutter, oversized and outlined, the way a magazine sets a
        // list number — it takes real space, but as an outline it never competes with the art.
        Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) {
            RecapGhostNumeral(
                text = rank.toString(),
                tone = tone,
                fillAlpha = 0.1f,
                strokeAlpha = 0.42f,
            )
        }
        RecapArtwork(
            artwork = song.thumbnailUrl,
            modifier = Modifier.size(if (compact) 40.dp else 48.dp),
            shape = RoundedCornerShape(7.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = RecapType.rowTitle.copy(fontSize = 17.sp, lineHeight = 21.sp),
                color = tone.ink(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playsLabel(song.songCountListened.toLong()),
                style = RecapType.caption,
                color = tone.secondaryInk(),
                maxLines = 1,
            )
        }
        Text(
            text = makeTimeString(song.timeListened ?: 0L),
            style = RecapType.caption,
            color = tone.secondaryInk(),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Artists
// ---------------------------------------------------------------------------------------------

/**
 * The artists who owned the year.
 *
 * The leader is treated as a poster: a portrait that bleeds to both edges of the card, the
 * artist's name set over the bottom of it, and a ghosted "1" hanging off the far corner. Ranks
 * two to five drop to a tidy column with small portraits, so the page has one subject and one
 * list rather than five equal rows.
 */
@Composable
internal fun TopArtistsRecapCard(
    card: YearInMusicRecapCard.TopArtists,
    scene: RecapScene,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onArtistLongClick: (Artist) -> Unit,
) {
    val standout = card.artists.firstOrNull()
    val rest = card.artists.drop(1).take(4)
    val ink = tone.ink()

    Box(modifier = Modifier.fillMaxSize()) {
        // The page's art is the leader's own portrait: a tall blurred print of it bleeding past
        // both edges, so the olive scene reads as a wash over the artist rather than a flat field.
        if (standout?.artist?.thumbnailUrl != null) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                AsyncImage(
                    model = rememberOfflineArtworkImageRequest(standout.artist.thumbnailUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f)
                            .align(Alignment.TopCenter)
                            .blur(46.dp)
                            .recapHeroBreath(motion, initialScale = 1.22f),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.00f to scene.base[2].copy(alpha = 0.30f),
                                    0.30f to scene.base[1].copy(alpha = 0.58f),
                                    0.62f to scene.base[0].copy(alpha = 0.96f),
                                    1.00f to scene.base[0],
                                ),
                            ),
                )
            }
        }

        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecapEyebrow(
                    text = stringResource(R.string.top_artists),
                    tone = tone,
                    modifier = Modifier.recapEntrance(motion, blur = 8f),
                )
                if (standout != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ArtistHero(
                        artist = standout,
                        tone = tone,
                        motion = motion,
                        onLongClick = { onArtistLongClick(standout) },
                    )
                }
                if (rest.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    rest.forEachIndexed { index, artist ->
                        if (index > 0) RecapDivider(tone = tone, modifier = Modifier.padding(vertical = 9.dp))
                        RankedArtistRow(
                            rank = index + 2,
                            artist = artist,
                            tone = tone,
                            onLongClick = { onArtistLongClick(artist) },
                            modifier =
                                Modifier.recapEntrance(
                                    motion,
                                    delay = 300 + index * 80,
                                    fromX = 22f,
                                    fromY = 0f,
                                    blur = 6f,
                                ),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.year_in_music_ranked_artists),
                    style = RecapType.footer,
                    color = ink.copy(alpha = 0.78f),
                    modifier = Modifier.recapEntrance(motion, delay = 680, blur = 6f),
                )
            }
        }
    }
}

/**
 * The #1 artist, set as a poster: the name leads at display size, the minutes follow as the
 * supporting line, and a ghosted numeral hangs over the far corner of the block.
 */
@Composable
private fun ArtistHero(
    artist: Artist,
    tone: RecapTone,
    motion: () -> Long,
    onLongClick: () -> Unit,
) {
    val ink = tone.ink()
    val minutes = formatCount(((artist.timeListened ?: 0) / 60_000).toLong())
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                // Long press only: an empty onClick would register the row as a button that
                // TalkBack announces and then does nothing when activated.
                .pointerInput(artist.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
    ) {
        RecapGhostNumeral(
            text = "1",
            tone = tone,
            fontSize = 200.sp,
            fillAlpha = 0.10f,
            strokeAlpha = 0.34f,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-52).dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().recapEntrance(motion, delay = 90, fromY = 30f, blur = 0f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Portrait large enough to be the subject, ringed so it still reads as a circle
            // against the blurred print behind it.
            Box(
                modifier =
                    Modifier
                        .size(152.dp)
                        .shadow(elevation = 22.dp, shape = CircleShape, clip = false),
            ) {
                RecapArtistPortrait(artwork = artist.artist.thumbnailUrl, modifier = Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AutoResizeText(
                    text = artist.artist.name,
                    fontSizeRange = FontSizeRange(min = 20.sp, max = 40.sp),
                    style = RecapType.title.copy(fontSize = 38.sp, lineHeight = 42.sp),
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 3,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.year_in_music_minutes_line, minutes),
                    style = RecapType.caption.copy(fontSize = 14.sp),
                    color = ink.copy(alpha = 0.76f),
                    modifier = Modifier.recapEntrance(motion, delay = 280, blur = 8f),
                )
            }
        }
    }
}

@Composable
private fun RankedArtistRow(
    rank: Int,
    artist: Artist,
    tone: RecapTone,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // Long press only, for the same reason as the hero above.
                .pointerInput(artist.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The rank hangs in the gutter as an outline, matching the song ladder beside it.
        Box(modifier = Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            RecapGhostNumeral(
                text = rank.toString(),
                tone = tone,
                fontSize = 60.sp,
                fillAlpha = 0.1f,
                strokeAlpha = 0.4f,
            )
        }
        RecapArtistPortrait(
            artwork = artist.artist.thumbnailUrl,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = artist.artist.name,
            style = RecapType.rowTitle.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = tone.ink(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = makeTimeString((artist.timeListened ?: 0).toLong()),
            style = RecapType.caption,
            color = tone.secondaryInk(),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TopAlbumsRecapCard(
    card: YearInMusicRecapCard.TopAlbums,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onAlbumLongClick: (Album) -> Unit,
) {
    val albums = card.albums.take(5)
    val hero = albums.firstOrNull()
    val rest = albums.drop(1).take(4)

    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecapEyebrow(
                text = stringResource(R.string.top_albums),
                tone = tone,
                modifier = Modifier.recapEntrance(motion, blur = 8f),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                if (hero != null) {
                    AlbumHero(
                        album = hero,
                        tone = tone,
                        motion = motion,
                        onLongClick = { onAlbumLongClick(hero) },
                    )
                    if (rest.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(26.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rest.forEachIndexed { index, album ->
                                AlbumTile(
                                    album = album,
                                    tone = tone,
                                    onLongClick = { onAlbumLongClick(album) },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .recapEntrance(
                                                motion,
                                                delay = 300 + index * 90,
                                                fromY = 26f,
                                                initialScale = 0.92f,
                                                blur = 0f,
                                            ),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.year_in_music_ranked_albums),
                style = RecapType.footer,
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 700, blur = 6f),
            )
        }
    }
}

@Composable
private fun AlbumHero(
    album: Album,
    tone: RecapTone,
    motion: () -> Long,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(album.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // The hero cover gets a soft drop shadow so it lifts off the clay wash the way the
        // reference cover does.
        Box(
            modifier =
                Modifier
                    .size(148.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(12.dp), clip = false)
                    .recapEntrance(motion, delay = 100, fromY = 34f, initialScale = 0.92f, blur = 0f),
        ) {
            RecapArtwork(
                artwork = album.thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "#1",
                style = RecapType.caption,
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 180, blur = 6f),
            )
            AutoResizeText(
                text = album.album.title,
                fontSizeRange = FontSizeRange(min = 17.sp, max = 28.sp),
                style = RecapType.title.copy(fontSize = 28.sp, lineHeight = 33.sp),
                fontWeight = FontWeight.Bold,
                color = tone.ink(),
                maxLines = 3,
                modifier = Modifier.recapEntrance(motion, delay = 220, blur = 10f),
            )
            val artists = album.artists.take(2).joinToString(" / ") { it.name }
            if (artists.isNotBlank()) {
                Text(
                    text = artists,
                    style = RecapType.caption.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = tone.secondaryInk(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recapEntrance(motion, delay = 300, blur = 6f),
                )
            }
        }
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    tone: RecapTone,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .pointerInput(album.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp), clip = false),
        ) {
            RecapArtwork(
                artwork = album.thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
            )
        }
        Text(
            text = album.album.title,
            style = RecapType.caption.copy(fontSize = 12.sp, lineHeight = 15.sp),
            color = tone.ink(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Summary — the Replay "your year at a glance" page
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SummaryRecapCard(
    card: YearInMusicRecapCard.Summary,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onBack: () -> Unit,
) {
    val ink = tone.ink()
    val minutes = (card.totalListeningTime / 60_000L).coerceAtLeast(if (card.totalListeningTime > 0L) 1L else 0L)
    val topSongLabel = stringResource(R.string.year_in_music_summary_top_song)
    val topArtistLabel = stringResource(R.string.year_in_music_summary_top_artist)
    val topAlbumLabel = stringResource(R.string.albums)
    val picks =
        remember(card, topSongLabel, topArtistLabel, topAlbumLabel) {
            buildList {
                card.topArtists.firstOrNull()?.let { add(topArtistLabel to it.artist.thumbnailUrl) }
                card.topSongs.firstOrNull()?.let { add(topSongLabel to it.thumbnailUrl) }
                card.topAlbums.firstOrNull()?.let { add(topAlbumLabel to it.thumbnailUrl) }
            }
        }

    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${card.year} ${stringResource(R.string.year_in_music_recap_word)}",
                    style = RecapType.eyebrow,
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, blur = 8f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                AutoResizeText(
                    text = formatCount(minutes),
                    fontSizeRange = FontSizeRange(min = 54.sp, max = 92.sp),
                    style = RecapType.statementLarge.copy(fontSize = 92.sp, lineHeight = 92.sp),
                    fontWeight = FontWeight.Black,
                    color = ink,
                    maxLines = 1,
                    modifier =
                        Modifier.recapEntrance(motion, delay = 90, fromY = 40f, initialScale = 0.94f, blur = 16f),
                )
                Text(
                    text = stringResource(R.string.year_in_music_minutes_unit).uppercase(),
                    style = RecapType.eyebrow,
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, delay = 170, blur = 8f),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    SummaryFact(
                        label = stringResource(R.string.year_in_music_plays_short),
                        value = formatCount(card.totalSongsPlayed),
                        tone = tone,
                        modifier = Modifier.weight(1f).recapEntrance(motion, delay = 300, fromY = 24f, blur = 10f),
                    )
                    SummaryFact(
                        label = stringResource(R.string.year_in_music_active_days),
                        value = formatCount(card.activeDays.toLong()),
                        tone = tone,
                        modifier = Modifier.weight(1f).recapEntrance(motion, delay = 380, fromY = 24f, blur = 10f),
                    )
                }
                if (picks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Three art tiles, the way Replay signs the recap off with its top picks.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        picks.forEachIndexed { index, (label, artwork) ->
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .recapEntrance(
                                            motion,
                                            delay = 460 + index * 100,
                                            fromY = 30f,
                                            initialScale = 0.9f,
                                            blur = 0f,
                                        ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RecapArtwork(
                                    artwork = artwork,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    shape = if (index == 0) CircleShape else RoundedCornerShape(10.dp),
                                )
                                Text(
                                    text = label,
                                    style = RecapType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                    color = tone.secondaryInk(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            // The summary closes the reel now, so it carries the way out.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.year_in_music_back_to_year),
                    style = RecapType.rowTitle.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier =
                        Modifier
                            .recapEntrance(motion, delay = 760, blur = 6f)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                )
                RecapSignature(text = stringResource(R.string.app_name), tone = tone)
            }
        }
    }
}

@Composable
private fun SummaryFact(
    label: String,
    value: String,
    tone: RecapTone,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AutoResizeText(
            text = value,
            fontSizeRange = FontSizeRange(min = 20.sp, max = 30.sp),
            style = RecapType.title.copy(fontSize = 30.sp, lineHeight = 33.sp),
            fontWeight = FontWeight.Bold,
            color = tone.ink(),
            maxLines = 1,
        )
        Text(
            text = label,
            style = RecapType.caption,
            color = tone.secondaryInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------------------------

@Composable
internal fun EmptyRecapCard(
    card: YearInMusicRecapCard.Empty,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = tone.ink()
    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(color = ink.copy(alpha = 0.14f), shape = CircleShape, contentColor = ink) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.stats), contentDescription = null, Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.year_in_music_empty_title),
                style = RecapType.statement,
                color = ink,
                modifier = Modifier.recapEntrance(motion, delay = 120, fromY = 26f, blur = 14f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.year_in_music_empty_subtitle, card.year),
                style = RecapType.rowMeta.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 220, blur = 10f),
            )
        }
    }
}
