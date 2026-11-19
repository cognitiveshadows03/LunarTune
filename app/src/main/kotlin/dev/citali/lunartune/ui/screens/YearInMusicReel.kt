package dev.citali.lunartune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.viewmodels.YearInMusicUiState
import java.text.NumberFormat
import kotlinx.coroutines.delay

/*
 * Highlight reel: a tap-driven story over the Year in Music stats.
 * UI and motion are ported in spirit from ArchiveTune's recap stories.
 * Data is currently read from the LunarTune stats in [YearInMusicUiState.Content].
 */

private val ReelBlack = Color(0xFF070707)
private val ReelRed = Color(0xFFFF0033)
private val ReelCream = Color(0xFFFFF7F2)

private enum class ReelChapter { INTRO, TOTALS, TOP_SONG, ARTISTS, ALBUMS, END }

private fun reelChapters(content: YearInMusicUiState.Content): List<ReelChapter> =
    buildList {
        add(ReelChapter.INTRO)
        add(ReelChapter.TOTALS)
        if (content.topSongsStats.isNotEmpty()) add(ReelChapter.TOP_SONG)
        if (content.topArtists.isNotEmpty()) add(ReelChapter.ARTISTS)
        if (content.topAlbums.isNotEmpty()) add(ReelChapter.ALBUMS)
        add(ReelChapter.END)
    }

/** 0 means the chapter waits for the user instead of advancing on its own. */
private fun reelDurationMs(chapter: ReelChapter): Long =
    when (chapter) {
        ReelChapter.INTRO, ReelChapter.END -> 0L
        else -> 7_000L
    }

private fun reelAccent(chapter: ReelChapter): Color =
    when (chapter) {
        ReelChapter.INTRO, ReelChapter.END -> ReelRed
        ReelChapter.TOTALS -> Color(0xFFFF8A3D)
        ReelChapter.TOP_SONG -> Color(0xFF9B7BFF)
        ReelChapter.ARTISTS -> Color(0xFF2DD4BF)
        ReelChapter.ALBUMS -> Color(0xFFFFC53D)
    }

@Composable
internal fun YearInMusicReel(
    content: YearInMusicUiState.Content,
    onClose: () -> Unit,
) {
    val chapters = remember(content) { reelChapters(content) }
    val lastIndex = chapters.lastIndex
    var index by remember(content) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember(content) { mutableFloatStateOf(0f) }

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

    // Auto-advance timer. Press-and-hold pauses it; resuming keeps the elapsed time.
    LaunchedEffect(index, paused) {
        if (paused || duration == 0L) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (elapsed < duration.toFloat()) {
            val now = withFrameNanos { it }
            elapsed += (now - lastFrame) / 1_000_000f
            lastFrame = now
        }
        next()
    }

    val accent by animateColorAsState(reelAccent(chapter), tween(600), label = "reelAccent")
    val progress = if (duration > 0L) (elapsed / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.fillMaxSize().background(ReelBlack)) {
        // 1. Visuals only, no pointer input.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.45f), ReelBlack, ReelBlack),
                        ),
                    ),
        )
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "reelChapter",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            ReelChapterBody(
                chapter = chapters.getOrElse(i) { ReelChapter.END },
                content = content,
                accent = accent,
            )
        }

        // 2. Full-screen tap zone: left third goes back, the rest goes forward, hold pauses.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
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

        // 4. Top bar: progress segments, brand, close.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = ReelCream,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "\u2715",
                    style = MaterialTheme.typography.titleMedium,
                    color = ReelCream,
                    modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
                )
            }
        }
    }
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
                    Text(stringResource(R.string.year_in_music_back_to_year), color = ReelCream)
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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 88.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        when (chapter) {
            ReelChapter.INTRO -> ReelIntroBody(content, accent)
            ReelChapter.TOTALS -> ReelTotalsBody(content, accent)
            ReelChapter.TOP_SONG -> ReelTopSongBody(content, accent)
            ReelChapter.ARTISTS -> ReelArtistsBody(content, accent)
            ReelChapter.ALBUMS -> ReelAlbumsBody(content, accent)
            ReelChapter.END -> ReelEndBody(accent)
        }
    }
}

@Composable
private fun ReelIntroBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    ReelReveal(delayMs = 0) {
        Text(
            text = content.selectedYear.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = ReelCream,
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
            color = ReelCream,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    ReelReveal(delayMs = 750) {
        Text(
            text = stringResource(R.string.year_in_music_intro_details),
            style = MaterialTheme.typography.bodyLarge,
            color = ReelCream.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReelTotalsBody(
    content: YearInMusicUiState.Content,
    accent: Color,
) {
    ReelHeading(R.string.year_in_music_totals_title, accent)
    Spacer(modifier = Modifier.height(32.dp))
    ReelReveal(delayMs = 150) {
        ReelStat(
            value = content.totalListeningTime / 60_000L,
            label = stringResource(R.string.year_in_music_minutes_label),
            accent = accent,
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    ReelReveal(delayMs = 400) {
        ReelStat(
            value = content.totalSongsPlayed,
            label = stringResource(R.string.year_in_music_plays_label),
            accent = accent,
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    ReelReveal(delayMs = 650) {
        ReelStat(
            value = content.activeDays.toLong(),
            label = stringResource(R.string.year_in_music_active_days),
            accent = accent,
        )
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
    ReelReveal(delayMs = 200) {
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
            color = ReelCream,
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
        ReelReveal(delayMs = 200 + i * 140) {
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
        ReelReveal(delayMs = 200 + i * 140) {
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
private fun ReelEndBody(accent: Color) {
    ReelReveal(delayMs = 0) {
        Text(
            text = stringResource(R.string.year_in_music_finale_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            textAlign = TextAlign.Start,
        )
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
private fun ReelStat(
    value: Long,
    label: String,
    accent: Color,
) {
    Column {
        ReelCountUp(
            target = value,
            color = accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = ReelCream.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReelCountUp(
    target: Long,
    color: Color,
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animated.animateTo(target.toFloat(), tween(1_400, easing = FastOutSlowInEasing))
    }
    Text(
        text = NumberFormat.getIntegerInstance().format(animated.value.toLong()),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = color,
    )
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
            color = ReelCream,
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
                        .background(if (i % 2 == 0) color else ReelCream.copy(alpha = 0.6f)),
            )
        }
    }
}

/** Fades and rises a block in after [delayMs]. Used for staggered card entrances. */
@Composable
private fun ReelReveal(
    delayMs: Int,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(550),
        label = "reelReveal",
    )
    val travel = with(LocalDensity.current) { 32.dp.toPx() }
    Box(
        modifier =
            Modifier.graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * travel
            },
    ) {
        content()
    }
}
