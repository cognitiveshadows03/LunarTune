package dev.citali.lunartune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.viewmodels.YearInMusicUiState
import java.text.NumberFormat

/*
 * Highlight reel: a tap-driven story over the Year in Music stats.
 * Layout and motion are LunarTune's own; data comes from [YearInMusicUiState.Content].
 */

private val ReelBlack = Color(0xFF070707)
private val ReelRed = Color(0xFFFF0033)
private val ReelCream = Color(0xFFFFF7F2)

/** Text colour for the current chapter; dark on the light intro, light elsewhere. */
private val LocalReelInk = compositionLocalOf { ReelCream }

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun reelInk(): Color = LocalReelInk.current

/** Chapter-local motion clock in ms. It only advances while the reel is playing, so pausing freezes the motion. */
private val LocalReelClock = compositionLocalOf<() -> Float> { { Float.MAX_VALUE } }

private enum class ReelChapter { INTRO, TOTALS, TOP_SONG, ARTISTS, ALBUMS, SUMMARY, END }

private fun reelChapters(content: YearInMusicUiState.Content): List<ReelChapter> =
    buildList {
        add(ReelChapter.INTRO)
        add(ReelChapter.TOTALS)
        if (content.topSongsStats.isNotEmpty()) add(ReelChapter.TOP_SONG)
        if (content.topArtists.isNotEmpty()) add(ReelChapter.ARTISTS)
        if (content.topAlbums.isNotEmpty()) add(ReelChapter.ALBUMS)
        add(ReelChapter.SUMMARY)
        add(ReelChapter.END)
    }

/** 0 means the chapter waits for the user instead of advancing on its own. */
private fun reelDurationMs(chapter: ReelChapter): Long =
    when (chapter) {
        ReelChapter.INTRO, ReelChapter.END -> 0L
        ReelChapter.SUMMARY -> 9_000L
        else -> 7_000L
    }

/** Background gradient per chapter, in the soft, colour-led style of the reference reel. */
private fun reelPalette(chapter: ReelChapter): Pair<Color, Color> =
    when (chapter) {
        ReelChapter.INTRO -> Color(0xFFFFF4D6) to Color(0xFFD9F2C4)
        ReelChapter.TOTALS -> Color(0xFF58B8C6) to Color(0xFF2F6F86)
        ReelChapter.TOP_SONG -> Color(0xFF5C78D6) to Color(0xFF2D3F8A)
        ReelChapter.ARTISTS -> Color(0xFFB9A85A) to Color(0xFFB55A3C)
        ReelChapter.ALBUMS -> Color(0xFFF2B8C6) to Color(0xFF6F86D6)
        ReelChapter.SUMMARY -> Color(0xFF6E8FD8) to Color(0xFF2B2F6B)
        ReelChapter.END -> Color(0xFF4D6DCF) to Color(0xFF1E2C66)
    }

private fun reelInkFor(chapter: ReelChapter): Color =
    when (chapter) {
        ReelChapter.INTRO -> Color(0xFF1A1A1A)
        else -> ReelCream
    }

private fun reelAccent(chapter: ReelChapter): Color =
    when (chapter) {
        ReelChapter.INTRO, ReelChapter.END -> ReelRed
        ReelChapter.TOTALS -> Color(0xFFFF8A3D)
        ReelChapter.TOP_SONG -> Color(0xFF9B7BFF)
        ReelChapter.ARTISTS -> Color(0xFF2DD4BF)
        ReelChapter.ALBUMS -> Color(0xFFFFC53D)
        ReelChapter.SUMMARY -> Color(0xFFFF5C8A)
    }

@Composable
internal fun YearInMusicReel(
    content: YearInMusicUiState.Content,
    onClose: () -> Unit,
) {
    val chapters = remember(content) { reelChapters(content) }
    val lastIndex = chapters.lastIndex
    var index by remember(content) { mutableIntStateOf(0) }
    var pressing by remember { mutableStateOf(false) }
    var manualPaused by remember { mutableStateOf(false) }
    var elapsed by remember(content) { mutableFloatStateOf(0f) }
    val paused = pressing || manualPaused

    val chapter = chapters.getOrElse(index) { ReelChapter.END }
    val duration = reelDurationMs(chapter)

    fun next() {
        if (index >= lastIndex) {
            onClose()
        } else {
            index += 1
            elapsed = 0f
        }
    }

    fun prev() {
        if (index > 0) {
            index -= 1
            elapsed = 0f
        }
    }

    fun restart() {
        index = 0
        elapsed = 0f
    }

    BackHandler(onBack = onClose)

    // Shared timer. It runs while the reel is playing; intro and end only wait for the user.
    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsed += (now - lastFrame) / 1_000_000f
            lastFrame = now
            if (duration > 0L && elapsed >= duration.toFloat()) break
        }
        next()
    }

    val accent by animateColorAsState(reelAccent(chapter), tween(600), label = "reelAccent")
    val palette = reelPalette(chapter)
    val paletteTop by animateColorAsState(palette.first, tween(700), label = "reelTop")
    val paletteBottom by animateColorAsState(palette.second, tween(700), label = "reelBottom")
    val ink by animateColorAsState(reelInkFor(chapter), tween(700), label = "reelInk")
    val progress = if (duration > 0L) (elapsed / duration.toFloat()).coerceIn(0f, 1f) else 0f

    CompositionLocalProvider(LocalReelInk provides ink) {
    Box(modifier = Modifier.fillMaxSize().background(ReelBlack)) {
        // 1. Visuals only, no pointer input.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(paletteTop, paletteBottom))),
        )
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "reelChapter",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            val clock: () -> Float = { if (i == index) elapsed else Float.MAX_VALUE }
            CompositionLocalProvider(LocalReelClock provides clock) {
                ReelChapterBody(
                    chapter = chapters.getOrElse(i) { ReelChapter.END },
                    content = content,
                    accent = accent,
                )
            }
        }

        // 2. Full-screen tap zone: left third goes back, the rest goes forward, holding pauses.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onPress = {
                                pressing = true
                                tryAwaitRelease()
                                pressing = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width / 3f) prev() else next()
                            },
                        )
                    },
        )

        // 3. Actions sit above the tap zone so their taps are never swallowed.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ReelActions(
                chapter = chapter,
                accent = accent,
                onNext = ::next,
                onRewatch = ::restart,
                onClose = onClose,
            )
        }

        // 4. Top bar: pause and close, progress segments, labels.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReelIconButton(onClick = { manualPaused = !manualPaused }) {
                    if (manualPaused) drawReelPlay(ink) else drawReelPauseBars(ink)
                }
                Spacer(modifier = Modifier.width(8.dp))
                ReelIconButton(onClick = onClose) { drawReelClose(ink) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chapters.indices.forEach { i ->
                    val fraction =
                        when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(ReelCream.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .background(ReelCream),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.year_in_music)} ${content.selectedYear}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReelCream,
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = ReelCream.copy(alpha = 0.7f),
                )
            }
        }
    }
    }
}

@Composable
private fun ReelIconButton(
    onClick: () -> Unit,
    icon: DrawScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ReelCream.copy(alpha = 0.16f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) { icon() }
    }
}

private fun DrawScope.drawReelPauseBars(color: Color) {
    val w = size.width
    val h = size.height
    val radius = CornerRadius(w * 0.12f)
    drawRoundRect(color, topLeft = Offset(0f, 0f), size = Size(w * 0.3f, h), cornerRadius = radius)
    drawRoundRect(color, topLeft = Offset(w * 0.7f, 0f), size = Size(w * 0.3f, h), cornerRadius = radius)
}

private fun DrawScope.drawReelPlay(color: Color) {
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
            moveTo(w * 0.1f, 0f)
            lineTo(w, h / 2f)
            lineTo(w * 0.1f, h)
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.drawReelClose(color: Color) {
    val stroke = 2.5.dp.toPx()
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
}

@Composable
private fun ReelActions(
    chapter: ReelChapter,
    accent: Color,
    onNext: () -> Unit,
    onRewatch: () -> Unit,
    onClose: () -> Unit,
) {
    when (chapter) {
        ReelChapter.INTRO -> {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ReelRed, contentColor = Color.Black),
            ) {
                Text(stringResource(R.string.year_in_music_tap_begin), style = MaterialTheme.typography.titleMedium)
            }
        }
        ReelChapter.END -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRewatch,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.year_in_music_rewatch), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.year_in_music_back_to_year), color = reelInk())
                }
            }
        }
        else -> Spacer(modifier = Modifier.height(0.dp))
    }
}

@Composable
private fun ReelChapterBody(
    chapter: ReelChapter,
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    // List chapters sit low on the card, like the reference layout; the rest centre vertically.
    val arrangement =
        when (chapter) {
            ReelChapter.ARTISTS, ReelChapter.ALBUMS -> Arrangement.Bottom
            else -> Arrangement.Center
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 120.dp, bottom = 120.dp),
        verticalArrangement = arrangement,
        horizontalAlignment = Alignment.Start,
    ) {
        when (chapter) {
            ReelChapter.INTRO -> ReelIntroBody(content, accent)
            ReelChapter.TOTALS -> ReelTotalsBody(content, accent)
            ReelChapter.TOP_SONG -> ReelTopSongBody(content, accent)
            ReelChapter.ARTISTS -> ReelArtistsBody(content, accent)
            ReelChapter.ALBUMS -> ReelAlbumsBody(content, accent)
            ReelChapter.SUMMARY -> ReelSummaryBody(content, accent)
            ReelChapter.END -> ReelEndBody(accent)
        }
    }
}

@Composable
private fun ReelIntroBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    ReelReveal(delayMs = 0, fromScale = 0.86f) {
        Text(
            text = content.selectedYear.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = reelInk(),
        )
    }
    ReelReveal(delayMs = 200) {
        Text(
            text = stringResource(R.string.year_in_music_recap_word),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
    Spacer(modifier = Modifier.height(40.dp))
    ReelReveal(delayMs = 400) { ReelEqualizer(color = accent) }
    Spacer(modifier = Modifier.height(40.dp))
    ReelReveal(delayMs = 600) {
        Text(
            text = stringResource(R.string.year_in_music_intro_hero),
            style = MaterialTheme.typography.headlineSmall,
            color = reelInk(),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReelReveal(delayMs = 750) {
        Text(
            text = stringResource(R.string.year_in_music_intro_details),
            style = MaterialTheme.typography.bodyLarge,
            color = reelInk().copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReelTotalsBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    val clock = LocalReelClock.current
    val ease = FastOutSlowInEasing.transform(reelProgress(clock(), delayMs = 150, durationMs = 1_400f))
    val minutes = (content.totalListeningTime / 60_000L * ease).toLong()
    val faded = reelInk().copy(alpha = 0.55f)
    val statement =
        buildAnnotatedString {
            withStyle(SpanStyle(color = faded)) { append("You listened for ") }
            withStyle(SpanStyle(color = reelInk(), fontWeight = FontWeight.Bold)) {
                append(NumberFormat.getIntegerInstance().format(minutes))
            }
            withStyle(SpanStyle(color = faded)) { append(" minutes in ") }
            withStyle(SpanStyle(color = reelInk(), fontWeight = FontWeight.Bold)) {
                append(content.selectedYear.toString())
            }
            withStyle(SpanStyle(color = faded)) { append(".") }
        }
    ReelReveal(delayMs = 0) {
        Text(
            text = statement,
            style = MaterialTheme.typography.displaySmall,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    ReelReveal(delayMs = 350) {
        Text(
            text =
                "${NumberFormat.getIntegerInstance().format(content.totalSongsPlayed)} ${stringResource(R.string.year_in_music_plays_label)}" +
                    "  \u00b7  ${content.activeDays} ${stringResource(R.string.year_in_music_active_days)}",
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
        )
    }
    Spacer(modifier = Modifier.height(32.dp))
    val tiles = content.topSongsStats.take(6)
    tiles.chunked(3).forEachIndexed { row, rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEachIndexed { col, song ->
                ReelReveal(delayMs = 500 + (row * 3 + col) * 110, fromScale = 0.8f) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ReelTopSongBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    val top = content.topSongsStats.firstOrNull() ?: return
    val minutes = (top.timeListened ?: 0L) / 60_000L
    ReelHeading(R.string.year_in_music_top_track, accent)
    Spacer(modifier = Modifier.height(24.dp))
    ReelReveal(delayMs = 200, fromScale = 0.84f) {
        AsyncImage(
            model = top.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(28.dp)),
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    ReelReveal(delayMs = 400) {
        Text(
            text = top.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = reelInk(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ReelReveal(delayMs = 550) {
        Text(
            text = "${NumberFormat.getIntegerInstance().format(top.songCountListened)} ${stringResource(R.string.year_in_music_plays_short)}  \u00b7  $minutes ${stringResource(R.string.year_in_music_minutes_unit)}",
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
        )
    }
}

@Composable
private fun ReelArtistsBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    ReelHeading(R.string.year_in_music_ranked_artists, accent)
    Spacer(modifier = Modifier.height(24.dp))
    content.topArtists.take(5).forEachIndexed { i, artist ->
        ReelReveal(delayMs = 200 + i * 140, fromX = 48.dp) {
            ReelRankRow(
                rank = i + 1,
                title = artist.artist.name,
                imageUrl = artist.artist.thumbnailUrl,
                accent = accent,
                circle = true,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ReelAlbumsBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    ReelHeading(R.string.year_in_music_ranked_albums, accent)
    Spacer(modifier = Modifier.height(24.dp))
    content.topAlbums.take(5).forEachIndexed { i, album ->
        ReelReveal(delayMs = 200 + i * 140, fromX = 48.dp) {
            ReelRankRow(
                rank = i + 1,
                title = album.album.title,
                imageUrl = album.album.thumbnailUrl,
                accent = accent,
                circle = false,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ReelSummaryBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    val top = content.topSongsStats.firstOrNull()
    val topSong = content.topSongs.firstOrNull { it.id == top?.id }
    val topArtist = content.topArtists.firstOrNull()
    val minutes = content.totalListeningTime / 60_000L
    ReelReveal(delayMs = 0, fromScale = 0.92f) {
        AsyncImage(
            model = top?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp)),
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
    ReelReveal(delayMs = 150) {
        Text(
            text = content.selectedYear.toString(),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = reelInk(),
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        ReelReveal(delayMs = 300, fromX = (-24).dp, modifier = Modifier.weight(1f)) {
            ReelFact(
                label = stringResource(R.string.year_in_music_summary_top_song),
                value = top?.title ?: "-",
                detail = topSong?.artists?.joinToString { it.name }.orEmpty(),
                accent = accent,
            )
        }
        ReelReveal(delayMs = 380, fromX = 24.dp, modifier = Modifier.weight(1f)) {
            ReelFact(
                label = stringResource(R.string.year_in_music_minutes_label),
                value = NumberFormat.getIntegerInstance().format(minutes),
                detail = "",
                accent = accent,
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        ReelReveal(delayMs = 460, fromX = (-24).dp, modifier = Modifier.weight(1f)) {
            ReelFact(
                label = stringResource(R.string.year_in_music_summary_top_artist),
                value = topArtist?.artist?.name ?: "-",
                detail = "",
                accent = accent,
            )
        }
        ReelReveal(delayMs = 540, fromX = 24.dp, modifier = Modifier.weight(1f)) {
            ReelFact(
                label = stringResource(R.string.year_in_music_plays_label),
                value = NumberFormat.getIntegerInstance().format(content.totalSongsPlayed),
                detail = "",
                accent = accent,
            )
        }
    }
}

@Composable
private fun ReelFact(
    label: String,
    value: String,
    detail: String,
    accent: Color,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = accent)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = reelInk(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = reelInk().copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReelEndBody(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ReelReveal(delayMs = 0) {
            Text(
                text = stringResource(R.string.year_in_music_finale_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReelHeading(
    textRes: Int,
    accent: Color,
) {
    ReelReveal(delayMs = 0) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun ReelRankRow(
    rank: Int,
    title: String,
    imageUrl: String?,
    accent: Color,
    circle: Boolean,
) {
    val thumbShape = if (circle) CircleShape else RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.width(44.dp),
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(thumbShape),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = reelInk(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReelEqualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "reelEqualizer")
    Row(
        modifier = Modifier.height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(900, 700, 1_100, 800, 1_000).forEachIndexed { i, duration ->
            val fraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "reelBar$i",
            )
            Box(
                modifier =
                    Modifier
                        .width(12.dp)
                        .fillMaxHeight(fraction)
                        .clip(CircleShape)
                        .background(if (i % 2 == 0) color else reelInk().copy(alpha = 0.6f)),
            )
        }
    }
}

private fun reelProgress(nowMs: Float, delayMs: Int, durationMs: Float): Float =
    ((nowMs - delayMs) / durationMs).coerceIn(0f, 1f)

/**
 * Fades, rises, scales and slides a block in after [delayMs] on the chapter clock.
 * Driven from draw, so it does not recompose and freezes while the reel is paused.
 */
@Composable
private fun ReelReveal(
    delayMs: Int,
    modifier: Modifier = Modifier,
    fromX: Dp = 0.dp,
    fromScale: Float = 0.94f,
    content: @Composable () -> Unit,
) {
    val clock = LocalReelClock.current
    val density = LocalDensity.current
    val travel = with(density) { 32.dp.toPx() }
    val travelX = with(density) { fromX.toPx() }
    Box(
        modifier =
            modifier.graphicsLayer {
                val p = FastOutSlowInEasing.transform(reelProgress(clock(), delayMs, 550f))
                alpha = p
                translationY = (1f - p) * travel
                translationX = (1f - p) * travelX
                val scale = fromScale + (1f - fromScale) * p
                scaleX = scale
                scaleY = scale
            },
    ) {
        content()
    }
}
