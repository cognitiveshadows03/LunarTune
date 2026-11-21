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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import kotlin.math.PI
import kotlin.math.sin

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

@Composable
internal fun TotalsRecapCard(
    card: YearInMusicRecapCard.Totals,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = tone.ink()
    val timeText = makeTimeString(card.totalListeningTime)
    val playsText = formatCount(card.totalSongsPlayed)
    val timeSentence = stringResource(R.string.year_in_music_totals_sentence, timeText)
    val playsSentence = stringResource(R.string.year_in_music_plays_sentence, playsText)

    Box(modifier = Modifier.fillMaxSize()) {
        // Replay scatters the year's artists and covers behind the sentence and lets them drift
        // the whole time the page is up. They sit under the copy and are cut off by the card.
        RecapFloatingArtwork(
            items = card.floating,
            motion = motion,
            modifier = Modifier.fillMaxSize(),
        )
        // Wash between the art and the sentence: the tiles stay visible at the edges but the
        // middle of the page keeps enough contrast for the large type.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to sceneWash(tone, 0.10f),
                            0.38f to sceneWash(tone, 0.34f),
                            0.72f to sceneWash(tone, 0.24f),
                            1.00f to sceneWash(tone, 0.46f),
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
                    // The sentence is the page, so it is set at display size with only the
                    // duration in solid ink — Replay's "You listened to 61,452 minutes" weight.
                    Text(
                        text =
                            buildAnnotatedString {
                                append(timeSentence)
                                val start = timeSentence.indexOf(timeText)
                                if (start >= 0) addStyle(SpanStyle(color = ink), start, start + timeText.length)
                            },
                        style = RecapType.heroStatement,
                        color = tone.secondaryInk(),
                        modifier = Modifier.recapEntrance(motion, delay = 120, fromY = 40f, blur = 18f),
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                    Text(
                        text =
                            buildAnnotatedString {
                                append(playsSentence)
                                val start = playsSentence.indexOf(playsText)
                                if (start >= 0) addStyle(SpanStyle(color = ink), start, start + playsText.length)
                            },
                        style = RecapType.statement,
                        color = tone.secondaryInk(),
                        modifier = Modifier.recapEntrance(motion, delay = 300, fromY = 30f, blur = 14f),
                    )
                }
                Text(
                    text = stringResource(R.string.year_in_music_totals_title),
                    style = RecapType.footer,
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, delay = 440, blur = 6f),
                )
            }
        }
    }
}

/** Scene-tinted wash colour for the legibility layer over the drifting art. */
private fun sceneWash(tone: RecapTone, alpha: Float): Color =
    if (tone == RecapTone.DARK) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)

/** Where one drifting tile sits, as fractions of the card, plus how fast it wanders. */
private data class FloatingSpot(
    val x: Float,
    val y: Float,
    val size: Float,
    val rise: Float,
    val sway: Float,
)

private val FloatingSpots =
    listOf(
        // Upper band, behind the opening line.
        FloatingSpot(0.18f, 0.16f, 0.26f, 26f, 12f),
        FloatingSpot(0.66f, 0.10f, 0.19f, 34f, -14f),
        FloatingSpot(0.90f, 0.27f, 0.23f, 22f, -10f),
        // Lower band, behind the play count and the footer.
        FloatingSpot(0.10f, 0.62f, 0.22f, 30f, 14f),
        FloatingSpot(0.46f, 0.74f, 0.28f, 24f, -12f),
        FloatingSpot(0.82f, 0.66f, 0.20f, 36f, 10f),
        FloatingSpot(0.30f, 0.40f, 0.15f, 28f, -8f),
    )

/**
 * The drifting wall on the totals page.
 *
 * Every tile rises a few dp and sways over the time the card is on screen, each at its own pace,
 * so the group never looks like one block sliding. Tiles are dimmed a little so the sentence in
 * front of them keeps its contrast, and they fade in one after another.
 */
@Composable
private fun RecapFloatingArtwork(
    items: List<RecapFloatingArt>,
    motion: () -> Long,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val cardWidth = maxWidth
        items.take(FloatingSpots.size).forEachIndexed { index, item ->
            val spot = FloatingSpots[index]
            val diameter = cardWidth * spot.size
            Box(
                modifier =
                    Modifier
                        .offset(
                            x = cardWidth * spot.x - diameter / 2,
                            y = maxHeight * spot.y - diameter / 2,
                        ).size(diameter)
                        .recapEntrance(
                            motion,
                            delay = index * 110,
                            fromY = 0f,
                            initialScale = 0.7f,
                            blur = 0f,
                        ).graphicsLayer {
                            // Every tile keeps its own period, so the wall never moves as one slab.
                            // The motion settles by 9 s, which is past the time this page is up.
                            val drift = (motion() / 9_000f).coerceIn(0f, 1f)
                            val pace = 1f + index * 0.17f
                            val swing = sin(drift * pace * PI.toFloat() * 1.6f)
                            translationY = -drift * spot.rise.dp.toPx()
                            translationX = swing * spot.sway.dp.toPx()
                        }.alpha(0.62f),
            ) {
                if (item.circle) {
                    RecapArtistPortrait(artwork = item.artwork, modifier = Modifier.fillMaxSize())
                } else {
                    RecapArtwork(
                        artwork = item.artwork,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }
    }
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

@Composable
internal fun TopSongsRecapCard(
    card: YearInMusicRecapCard.TopSongs,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.top_songs),
                    style = RecapType.statement,
                    color = tone.ink(),
                    modifier =
                        Modifier
                            .recapEntrance(motion, fromY = 24f, blur = 14f)
                            .padding(bottom = 16.dp),
                )
                card.songs.take(5).forEachIndexed { index, song ->
                    if (index > 0) RecapDivider(tone = tone, modifier = Modifier.padding(vertical = 11.dp))
                    RankedSongRow(
                        rank = index + 1,
                        song = song,
                        tone = tone,
                        modifier =
                            Modifier.recapEntrance(
                                motion,
                                delay = 180 + index * 90,
                                fromX = 26f,
                                fromY = 0f,
                                blur = 6f,
                            ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.year_in_music_top_songs_footer),
                style = RecapType.footer,
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 640, blur = 6f),
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = rank.toString(),
            style = RecapType.rank.copy(fontSize = if (rank == 1) 24.sp else 19.sp),
            color = tone.secondaryInk(),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        RecapArtwork(
            artwork = song.thumbnailUrl,
            modifier = Modifier.size(if (rank == 1) 54.dp else 46.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = song.title,
                style = if (rank == 1) RecapType.rowTitle else RecapType.rowTitle.copy(fontSize = 17.sp),
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

@Composable
internal fun TopArtistsRecapCard(
    card: YearInMusicRecapCard.TopArtists,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onArtistLongClick: (Artist) -> Unit,
) {
    val standout = card.artists.firstOrNull()
    val ink = tone.ink()

    RecapPage(applySafeContentInsets = applySafeContentInsets) {
        Column(modifier = Modifier.fillMaxSize()) {
            RecapEyebrow(
                text = stringResource(R.string.top_artists),
                tone = tone,
                modifier = Modifier.recapEntrance(motion, blur = 8f),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                if (standout != null) {
                    ArtistHero(
                        artist = standout,
                        ink = ink,
                        tone = tone,
                        motion = motion,
                        onLongClick = { onArtistLongClick(standout) },
                    )
                    if (card.artists.size > 1) {
                        Spacer(modifier = Modifier.height(22.dp))
                        card.artists.drop(1).take(4).forEachIndexed { index, artist ->
                            if (index > 0) RecapDivider(tone = tone, modifier = Modifier.padding(vertical = 10.dp))
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
                }
            }
            Text(
                text = stringResource(R.string.year_in_music_ranked_artists),
                style = RecapType.footer,
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 680, blur = 6f),
            )
        }
    }
}

/** The #1 artist: portrait, name, then the sentence Replay uses under it. */
@Composable
private fun ArtistHero(
    artist: Artist,
    ink: Color,
    tone: RecapTone,
    motion: () -> Long,
    onLongClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // Long press only: an empty onClick would register the row as a button that
                // TalkBack announces and then does nothing when activated.
                .pointerInput(artist.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecapArtistPortrait(
            artwork = artist.artist.thumbnailUrl,
            modifier =
                Modifier
                    .size(136.dp)
                    .recapEntrance(motion, delay = 90, fromY = 30f, initialScale = 0.9f, blur = 0f),
        )
        // The name holds up to two lines; Japanese and Latin names both get the full width
        // before the meter truncates, so long romanised names never get cut mid-word.
        AutoResizeText(
            text = artist.artist.name,
            fontSizeRange = FontSizeRange(min = 19.sp, max = 32.sp),
            style = RecapType.title,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 2,
            modifier = Modifier.recapEntrance(motion, delay = 200, fromY = 22f, blur = 10f),
        )
        Text(
            text =
                stringResource(
                    R.string.year_in_music_artist_sentence,
                    formatCount(((artist.timeListened ?: 0) / 60_000).toLong()),
                ),
            style = RecapType.rowMeta,
            color = tone.secondaryInk(),
            modifier = Modifier.recapEntrance(motion, delay = 280, blur = 8f),
        )
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
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // Rank first, then the circle: Replay keeps the number outside the artwork so the
        // portraits line up as a column.
        Text(
            text = rank.toString(),
            style = RecapType.rank.copy(fontSize = 19.sp),
            color = tone.secondaryInk(),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
        )
        RecapArtistPortrait(
            artwork = artist.artist.thumbnailUrl,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = artist.artist.name,
            style = RecapType.rowTitle.copy(fontSize = 17.sp),
            color = tone.ink(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // One time reading per row, like Replay: the plain duration is what the reference shows
        // in the trailing column, so no second minutes figure competes with it.
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
