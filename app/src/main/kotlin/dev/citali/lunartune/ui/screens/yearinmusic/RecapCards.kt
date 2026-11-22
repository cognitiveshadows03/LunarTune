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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            // Cover-page proportions. The year sits a little above the optical centre and the
            // invitation sits on the floor of the card; the two weights are unequal on purpose,
            // because the reference's opener is mostly air and the air is the point.
            Spacer(modifier = Modifier.weight(0.62f))
            // Fitted to the content width so four digits read as a masthead on every phone
            // rather than shrinking politely on the narrow ones.
            RecapFitText(
                text = card.year.toString(),
                style = RecapType.statementLarge.copy(letterSpacing = (-4).sp),
                color = ink,
                minSize = 64.sp,
                maxSize = 168.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .recapEntrance(motion, delay = 90, fromY = 44f, initialScale = 0.92f, blur = 18f),
            )
            Text(
                text = stringResource(R.string.year_in_music_recap_word),
                style =
                    RecapType.statementLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        letterSpacing = 3.sp,
                    ),
                color = ink.copy(alpha = 0.94f),
                modifier = Modifier.recapEntrance(motion, delay = 200, fromY = 26f, blur = 14f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.year_in_music_intro_hero),
                style = RecapType.statement,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.recapEntrance(motion, delay = 320, blur = 12f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.year_in_music_intro_details),
                style = RecapType.rowMeta.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 420, blur = 10f),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.year_in_music_tap_begin),
                style = RecapType.rowTitle.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = tone.secondaryInk(),
                modifier = Modifier.recapEntrance(motion, delay = 520, blur = 8f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Totals
// ---------------------------------------------------------------------------------------------

/**
 * The year in two numbers.
 *
 * The reference opens with one strip of its own artwork pinned to the top of the card and then
 * lets the figures own a flat field: no scattering of tiles around the type, no wash heavy enough
 * to drown the colour. Here the strip is full-bleed and butted together — two covers and a centre
 * album shape — and everything under it sits on the scene's own base colour.
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
    val band = remember(card.floating) { card.floating.map { it.artwork } }

    Box(modifier = Modifier.fillMaxSize().background(scene.base[1])) {
        // The band: a single row of art across the very top of the card, cropped by the edges so
        // it reads as a continuous surface rather than a gallery.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(TotalsBandHeightFraction).clipToBounds(),
        ) {
            val bandHeight = maxHeight
            val sideWidth = maxWidth * TotalsBandSideFraction
            val gap = 10.dp
            val sides = band.drop(1)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                BandTile(
                    artwork = sides.getOrNull(0) ?: band.firstOrNull(),
                    motion = motion,
                    delay = 40,
                    modifier = Modifier.width(sideWidth).height(bandHeight * 0.88f),
                    radius = 0.dp,
                )
                // The centre plate is the one rounded, taller cell, the way the reference lets
                // its middle cover break the line of an otherwise flush strip.
                BandTile(
                    artwork = band.firstOrNull(),
                    motion = motion,
                    delay = 110,
                    modifier = Modifier.weight(1f).height(bandHeight),
                    radius = 10.dp,
                )
                BandTile(
                    artwork = sides.getOrNull(1) ?: band.getOrNull(2) ?: band.firstOrNull(),
                    motion = motion,
                    delay = 180,
                    modifier = Modifier.width(sideWidth).height(bandHeight * 0.88f),
                    radius = 0.dp,
                )
            }
        }

        // A soft foot of the scene colour keeps the band from ending on a hard line, and leaves
        // the rest of the page a quiet, single-colour field for the figures.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(TotalsBandHeightFraction)
                    .background(
                        Brush.verticalGradient(
                            0.62f to Color.Transparent,
                            1.00f to scene.base[1],
                        ),
                    ),
        )

        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // The band is a share of the card, so the space reserved for it has to be too:
                // a fixed spacer drifts off the artwork on tall phones and overruns it on short
                // ones, which is how copy ends up sitting on top of the covers.
                val bandSpace = maxHeight * TotalsBandHeightFraction
                val compact = maxHeight < 620.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(bandSpace + 14.dp))
                    RecapEyebrow(
                        text = stringResource(R.string.total_listening_time),
                        tone = tone,
                        modifier = Modifier.recapEntrance(motion, blur = 8f),
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        RecapFitText(
                            text = lead,
                            style = DisplayStyle,
                            color = ink,
                            minSize = if (compact) 44.sp else 56.sp,
                            maxSize = if (compact) 104.sp else 150.sp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .offset(x = DisplayBleed)
                                    .recapEntrance(motion, delay = 90, fromY = 46f, initialScale = 0.94f, blur = 18f),
                        )
                        // The residue and the unit sentence sit together as one quiet block
                        // beneath the figure: a single display line, one readable line under it.
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
                        Spacer(modifier = Modifier.height(if (compact) 20.dp else 34.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().offset(x = DisplayBleed),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            RecapFitText(
                                text = playsText,
                                style = DisplayStyle.copy(fontSize = 80.sp, lineHeight = 76.sp),
                                color = ink,
                                minSize = 26.sp,
                                maxSize = if (compact) 56.sp else 80.sp,
                                // fill = false keeps the numeral its natural width, so its unit
                                // sits against it instead of being pushed to the far margin.
                                modifier =
                                    Modifier
                                        .weight(1f, fill = false)
                                        .recapEntrance(motion, delay = 320, fromY = 30f, blur = 14f),
                            )
                            // The noun rides the numeral's baseline instead of floating beside
                            // its centre, which keeps the pair reading as one figure with a unit.
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
}

/** One tile of the totals band; square-cropped and sliding in with the rest of the page. */
@Composable
private fun BandTile(
    artwork: String?,
    motion: () -> Long,
    delay: Int,
    modifier: Modifier = Modifier,
    radius: Dp,
) {
    Box(modifier = modifier.recapEntrance(motion, delay = delay, fromY = -18f, initialScale = 1f, blur = 0f)) {
        RecapArtwork(
            artwork = artwork,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(radius),
        )
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

/** Share of the card height the art band occupies, and of the width each flanking tile takes. */
private const val TotalsBandHeightFraction = 0.40f
private const val TotalsBandSideFraction = 0.28f

/** "[h]h [m]m" once an hour is on the clock, "[m]m" before that. */
private fun listeningTimeLead(milliseconds: Long): String {
    val totalMinutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (totalMinutes >= 60L) "${hours}h ${minutes}m" else "${minutes}m"
}

/** The residue under the lead line: seconds, zero-padded so the figure never jitters. */
private fun listeningTimeTail(milliseconds: Long): String {
    val seconds = ((milliseconds / 1_000L) % 60L).coerceAtLeast(0L)
    return "%02ds".format(seconds)
}

/**
 * The longest run of days with music on, answered with the year's own artwork.
 *
 * The reference states the streak as a sentence and then fills the rest of the page with covers;
 * here each day of the run is one cell of that field, so the grid *is* the streak — a six-day run
 * is six cells, and every extra cover is a day that kept it alive.
 */
@Composable
internal fun LongestStreakRecapCard(
    card: YearInMusicRecapCard.LongestStreak,
    scene: RecapScene,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
) {
    val ink = tone.ink()
    val days = remember(card.days) { maxOf(card.days, 1) }
    val covers = remember(card.covers, days) { cellCovers(card.covers, days) }
    val streakFigure =
        stringResource(
            R.string.year_in_music_streak_figure,
            pluralStringResource(R.plurals.year_in_music_streak_days, days, days),
        )
    val span =
        stringResource(
            R.string.year_in_music_streak_span,
            remember(card.startDay) { shortDate(card.startDay) },
            remember(card.endDay) { shortDate(card.endDay) },
        )

    Box(modifier = Modifier.fillMaxSize()) {
        // A warm radial breath behind the copy: the page is otherwise a flat scene colour, and
        // the grid needs something under it so the cells do not look pasted on.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.15f to scene.glows.first().copy(alpha = 0.22f),
                            0.75f to Color.Transparent,
                        ),
                    ),
        )
        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecapEyebrow(
                    text = stringResource(R.string.year_in_music_streak_title),
                    tone = tone,
                    modifier = Modifier.recapEntrance(motion, blur = 8f),
                )
                Spacer(modifier = Modifier.height(14.dp))
                // The sentence, then the figure it is about, set as the largest thing on the page.
                RecapFitText(
                    text = stringResource(R.string.year_in_music_streak_lead),
                    style = RecapType.statement.copy(fontSize = 30.sp, lineHeight = 38.sp),
                    color = ink.copy(alpha = 0.92f),
                    minSize = 21.sp,
                    maxSize = 30.sp,
                    maxLines = 3,
                    softWrap = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .recapEntrance(motion, delay = 80, fromY = 26f, blur = 12f),
                )
                RecapFitText(
                    text = streakFigure,
                    style = RecapType.statementLarge.copy(fontSize = 56.sp, lineHeight = 58.sp),
                    color = ink,
                    minSize = 34.sp,
                    maxSize = 56.sp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .recapEntrance(motion, delay = 150, fromY = 34f, blur = 14f),
                )
                Text(
                    text = span,
                    style = RecapType.caption.copy(fontSize = 14.sp),
                    color = tone.secondaryInk(),
                    modifier = Modifier.padding(top = 6.dp).recapEntrance(motion, delay = 240, blur = 8f),
                )
                Spacer(modifier = Modifier.height(22.dp))
                // The grid takes every pixel the copy above and the note below leave behind, so
                // a short run draws big cells and a long one still fits.
                StreakCoverGrid(
                    covers = covers,
                    motion = motion,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Text(
                    text = stringResource(R.string.year_in_music_streak_grid_note, covers.size),
                    style = RecapType.footer,
                    color = tone.secondaryInk(),
                    modifier = Modifier.padding(top = 12.dp).recapEntrance(motion, delay = 560, blur = 6f),
                )
            }
        }
    }
}

/**
 * Cells of the streak grid, one per day.
 *
 * The grid is as square as the page allows: columns are picked from the cell aspect rather than
 * fixed, so a three-day run is three big cells and a long run still tiles cleanly.
 */
@Composable
private fun StreakCoverGrid(
    covers: List<String?>,
    motion: () -> Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val columns = when {
            covers.size <= 3 -> covers.size.coerceAtLeast(1)
            covers.size <= 9 -> 3
            covers.size <= 16 -> 4
            else -> 5
        }
        val rows = ((covers.size + columns - 1) / columns).coerceAtLeast(1)
        val gap = 4.dp
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
        val cellHeight = (maxHeight - gap * (rows - 1)) / rows
        val cell = minOf(cellWidth, cellHeight)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            covers.chunked(columns).forEachIndexed { rowIndex, rowCovers ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowCovers.forEachIndexed { columnIndex, artwork ->
                        val index = rowIndex * columns + columnIndex
                        RecapArtwork(
                            artwork = artwork,
                            modifier =
                                Modifier
                                    .size(cell)
                                    .clipToBounds()
                                    .recapEntrance(
                                        motion,
                                        delay = 260 + index * 34,
                                        fromY = 16f,
                                        initialScale = 0.9f,
                                        blur = 0f,
                                    ),
                            shape = RoundedCornerShape(6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One cover per day: the run's own artwork where the year has that many, then the list repeats
 * so the grid keeps its shape instead of ending on a ragged half-row of blanks.
 */
private fun cellCovers(covers: List<String?>, days: Int): List<String?> {
    val usable = covers.filterNot { it.isNullOrBlank() }
    if (usable.isEmpty()) return List(days) { null }
    return List(days) { index -> usable[index % usable.size] }
}

/** "12 Feb" for the streak's date line, from the ISO keys the calendar stores. */
private fun shortDate(isoDay: String): String =
    runCatching {
        LocalDate
            .parse(isoDay)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }.getOrDefault(isoDay)

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

/**
 * The records that kept coming back.
 *
 * Same language as the songs and artists pages: a blurred print of the leader's cover carries the
 * page, the leader gets an offset spread with a ghosted numeral bleeding off the corner, and the
 * remaining four sit on hairline rules with outlined ranks in the gutter.
 */
@Composable
internal fun TopAlbumsRecapCard(
    card: YearInMusicRecapCard.TopAlbums,
    tone: RecapTone,
    motion: () -> Long,
    applySafeContentInsets: Boolean,
    onAlbumLongClick: (Album) -> Unit,
) {
    // Four albums, not five: the leader plus three ranked rows is what the card can hold above
    // its footer and the floating share pill. A fifth row was being cut in half by the card edge.
    val albums = card.albums.take(4)
    val leader = albums.firstOrNull()
    val rest = albums.drop(1).take(3)
    val ink = tone.ink()
    val tint = rememberDominantColor(leader?.thumbnailUrl)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFD98A63))) {
        if (leader != null) {
            AsyncImage(
                model = rememberOfflineArtworkImageRequest(leader.thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.68f)
                        .align(Alignment.TopCenter)
                        .blur(46.dp)
                        .recapHeroBreath(motion, initialScale = 1.22f),
            )
            // The clay scene's own base carries the foot of the page, with the cover's dark swatch
            // mixed into its top so the print and the field read as one surface.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to (tint ?: Color(0xFF9C5A40)).copy(alpha = 0.58f),
                                0.38f to Color(0xFFE8A57C).copy(alpha = 0.78f),
                                0.68f to Color(0xFFE8A57C),
                                1.00f to Color(0xFFD98A63),
                            ),
                        ),
            )
        }

        RecapPage(applySafeContentInsets = applySafeContentInsets) {
            Column(modifier = Modifier.fillMaxSize()) {
                RecapEyebrow(
                    text = stringResource(R.string.top_albums),
                    tone = tone,
                    modifier = Modifier.recapEntrance(motion, blur = 8f),
                )
                Spacer(modifier = Modifier.height(18.dp))
                if (leader != null) {
                    AlbumLeader(
                        album = leader,
                        ink = ink,
                        tone = tone,
                        motion = motion,
                        onLongClick = { onAlbumLongClick(leader) },
                    )
                }
                if (rest.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    rest.forEachIndexed { index, album ->
                        if (index > 0) {
                            RecapDivider(tone = tone, modifier = Modifier.padding(vertical = 9.dp))
                        }
                        RankedAlbumRow(
                            rank = index + 2,
                            album = album,
                            tone = tone,
                            onLongClick = { onAlbumLongClick(album) },
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
                    text = stringResource(R.string.year_in_music_top_albums_footer),
                    style = RecapType.footer,
                    color = ink.copy(alpha = 0.72f),
                    modifier = Modifier.recapEntrance(motion, delay = 660, blur = 6f),
                )
            }
        }
    }
}

/**
 * Rank one: the cover large on the left, its facts stacked on the right, a ghosted "1" hanging
 * off the top corner — the same offset spread the songs page uses, so the reel reads as a set.
 */
@Composable
private fun AlbumLeader(
    album: Album,
    ink: Color,
    tone: RecapTone,
    motion: () -> Long,
    onLongClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(album.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
    ) {
        RecapGhostNumeral(
            text = "1",
            tone = tone,
            fontSize = 176.sp,
            fillAlpha = 0.14f,
            strokeAlpha = 0.4f,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 26.dp, y = (-40).dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .recapEntrance(motion, delay = 120, fromY = 34f, initialScale = 1f, blur = 0f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1.18f)
                        .aspectRatio(1f)
                        .shadow(elevation = 26.dp, shape = RoundedCornerShape(10.dp), clip = false),
            ) {
                RecapArtwork(
                    artwork = album.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                )
            }
            Column(
                modifier = Modifier.weight(0.82f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = album.album.title,
                    style = RecapType.title.copy(fontSize = 26.sp, lineHeight = 29.sp),
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recapEntrance(motion, delay = 200, blur = 10f),
                )
                val artists = album.artists.take(2).joinToString(" / ") { it.name }
                if (artists.isNotBlank()) {
                    Text(
                        text = artists,
                        style = RecapType.caption.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = tone.secondaryInk(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recapEntrance(motion, delay = 260, blur = 8f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                RecapDivider(tone = tone)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = makeTimeString(album.timeListened ?: 0L),
                    style = RecapType.caption.copy(fontSize = 14.sp),
                    color = tone.secondaryInk(),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RankedAlbumRow(
    rank: Int,
    album: Album,
    tone: RecapTone,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(album.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.CenterStart) {
            RecapGhostNumeral(
                text = rank.toString(),
                tone = tone,
                fillAlpha = 0.1f,
                strokeAlpha = 0.34f,
            )
        }
        RecapArtwork(
            artwork = album.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(7.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.album.title,
                style = RecapType.rowTitle.copy(fontSize = 17.sp, lineHeight = 21.sp),
                color = tone.ink(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = album.artists.take(2).joinToString(" / ") { it.name }
            if (artists.isNotBlank()) {
                Text(
                    text = artists,
                    style = RecapType.caption,
                    color = tone.secondaryInk(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = makeTimeString(album.timeListened ?: 0L),
            style = RecapType.caption,
            color = tone.secondaryInk(),
            maxLines = 1,
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
            // A closing page is a sign-off, not another list. It says the year once, shows the
            // three faces that carried it, and then gets out of the way: the reel ends here on
            // its own, so there is no fifth row to scroll past and nothing left hanging.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.year_in_music_conclusion_lead,
                            card.year.toString(),
                        ),
                    style = RecapType.statement,
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, blur = 10f),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.year_in_music_conclusion_title),
                    style = RecapType.heroStatement,
                    fontWeight = FontWeight.Black,
                    color = ink,
                    modifier =
                        Modifier.recapEntrance(
                            motion,
                            delay = 110,
                            fromY = 34f,
                            initialScale = 0.96f,
                            blur = 14f,
                        ),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text =
                        stringResource(
                            R.string.year_in_music_conclusion_body,
                            formatCount(minutes),
                            formatCount(card.activeDays),
                        ),
                    style = RecapType.rowMeta.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = tone.secondaryInk(),
                    modifier = Modifier.recapEntrance(motion, delay = 220, blur = 8f),
                )
                if (picks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    // The three faces of the year, in one quiet rank-free row.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        picks.forEachIndexed { index, (label, artwork) ->
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .recapEntrance(
                                            motion,
                                            delay = 340 + index * 90,
                                            fromY = 28f,
                                            initialScale = 0.92f,
                                            blur = 0f,
                                        ),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    RecapArtwork(
                                        artwork = artwork,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = if (index == 0) CircleShape else RoundedCornerShape(12.dp),
                                    )
                                }
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
            // The sign-off line: the app's name and mark, then the way back to the year itself.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RecapSignature(
                    text = stringResource(R.string.app_name),
                    tone = tone,
                    modifier = Modifier.recapEntrance(motion, delay = 620, blur = 6f),
                )
                Text(
                    text = stringResource(R.string.year_in_music_back_to_year),
                    style = RecapType.rowTitle.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier =
                        Modifier
                            .recapEntrance(motion, delay = 700, blur = 6f)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
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
