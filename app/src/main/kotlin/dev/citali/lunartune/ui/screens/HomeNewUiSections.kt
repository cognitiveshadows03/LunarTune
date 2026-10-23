/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.QuickPicksDisplayMode
import dev.citali.lunartune.db.entities.Album
import dev.citali.lunartune.db.entities.Artist
import dev.citali.lunartune.db.entities.LocalItem
import dev.citali.lunartune.db.entities.Playlist
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.PlayerConnection
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.MenuState
import dev.citali.lunartune.ui.component.NumberedMediaRow
import dev.citali.lunartune.ui.menu.AlbumMenu
import dev.citali.lunartune.ui.menu.ArtistMenu
import dev.citali.lunartune.ui.menu.PlaylistMenu
import dev.citali.lunartune.ui.menu.SongMenu
import dev.citali.lunartune.ui.menu.YouTubeSongMenu
import dev.citali.lunartune.ui.utils.displayArtworkUrl
import dev.citali.lunartune.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import kotlin.random.Random

/** Corner radius of the quick-pick cards: large enough to read as a poster, not as a button. */
private val QuickPickCorner = 26.dp

/** Card width as a fraction of the feed width, so the next card always peeks. */
private const val QuickPickWidthFraction = 0.72f

/** Posters, so a touch taller than wide. */
private const val QuickPickAspectRatio = 1.28f

/** Rows per page in the speed-dial list, matching the old grid's page size. */
private const val SpeedDialRowsPerPage = 4

private val SpeedDialRowHeight = 64.dp

/**
 * One entry of a quick-pick carousel, independent of where it came from: the local library or the
 * remote home feed.
 */
private class QuickPickCardData(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val isActive: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)

/** One row of the speed-dial list, similarly independent of whether it came from the library. */
private class SpeedDialRowData(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val isActive: Boolean,
    val trailing: String?,
    val isShuffle: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * The redesigned quick picks (New UI): large poster cards that run off the right edge of the
 * screen, numbered so the strip reads as a ranking rather than a carousel of equal things.
 *
 * Colour is kept to the artwork: a light scrim at the top so the number stays legible on a bright
 * cover and another at the bottom for the title. There is no border and no container — the artwork
 * is the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewQuickPicksSection(
    quickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    displayMode: QuickPicksDisplayMode,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val distinct = remember(quickPicks) { quickPicks.distinctBy { it.id } }

    val cards =
        distinct.map { song ->
            QuickPickCardData(
                key = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = song.id == mediaMetadata?.id,
                onClick = {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        playerConnection.playQueue(
                            if (song.song.isLocal) {
                                ListQueue(items = listOf(song.toMediaItem()))
                            } else {
                                YouTubeQueue.radio(song.toMediaMetadata())
                            },
                        )
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        SongMenu(
                            originalSong = song,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }

    if (displayMode == QuickPicksDisplayMode.CARD) {
        QuickPickCards(cards = cards, isPlaying = isPlaying, modifier = modifier)
    } else {
        QuickPickRows(
            cards = cards,
            isPlaying = isPlaying,
            haptic = haptic,
            modifier = modifier,
        )
    }
}

/**
 * Same as [NewQuickPicksSection], fed by the remote home feed instead of the local library.
 */
@Composable
internal fun NewRemoteQuickPicksSection(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    displayMode: QuickPicksDisplayMode,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val songs =
        remember(section.items) {
            section.items.filterIsInstance<SongItem>().distinctBy(SongItem::id)
        }

    val cards =
        songs.map { song ->
            QuickPickCardData(
                key = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                thumbnailUrl = song.thumbnail,
                isActive = song.id == mediaMetadata?.id,
                onClick = {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        YouTubeSongMenu(
                            song = song,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }

    if (displayMode == QuickPicksDisplayMode.CARD) {
        QuickPickCards(cards = cards, isPlaying = isPlaying, modifier = modifier)
    } else {
        QuickPickRows(
            cards = cards,
            isPlaying = isPlaying,
            haptic = haptic,
            modifier = modifier,
        )
    }
}

@Composable
private fun QuickPickCards(
    cards: List<QuickPickCardData>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * QuickPickWidthFraction).coerceIn(200.dp, 360.dp)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items = cards, key = { _, card -> card.key }) { index, card ->
                QuickPickCard(
                    number = index + 1,
                    card = card,
                    isPlaying = isPlaying && card.isActive,
                    width = cardWidth,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickPickCard(
    number: Int,
    card: QuickPickCardData,
    isPlaying: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val height = width * QuickPickAspectRatio
    val widthPx = with(density) { width.roundToPx().coerceAtLeast(1) }
    val heightPx = with(density) { height.roundToPx().coerceAtLeast(1) }
    val artworkUrl =
        remember(card.thumbnailUrl, widthPx, heightPx) {
            card.thumbnailUrl?.displayArtworkUrl(width = widthPx, height = heightPx)
        }
    val shadowBlurPx = with(density) { 10.dp.toPx() }

    Box(
        modifier =
            modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(QuickPickCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(onClick = card.onClick, onLongClick = card.onLongClick),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }

        // The number sits on the artwork in the top corner, so it needs a whisper of a scrim to
        // stay readable over a light cover.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.34f to Color.Transparent,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.52f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.76f),
                        ),
                    ),
        )

        Text(
            text = number.toString(),
            style =
                MaterialTheme.typography.displaySmall.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = shadowBlurPx),
                ),
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 10.dp),
        )

        if (isPlaying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.volume_up),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.subtitle.isNotBlank()) {
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The dense variant of the same strip, for people who chose the list layout: two columns of
 * numbered rows instead of posters. Uses the same row as the queue, so the two screens read as one
 * design once the redesign reaches them both.
 */
@Composable
private fun QuickPickRows(
    cards: List<QuickPickCardData>,
    isPlaying: Boolean,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val itemWidth = maxWidth * widthFactor
        val gridState = rememberLazyGridState()
        LazyHorizontalGrid(
            state = gridState,
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SpeedDialRowHeight * 4),
        ) {
            itemsIndexed(
                items = cards,
                key = { _, card -> card.key },
                contentType = { _, _ -> "quick_pick_row" },
            ) { index, card ->
                NumberedMediaRow(
                    position = index + 1,
                    title = card.title,
                    subtitle = card.subtitle,
                    thumbnailUrl = card.thumbnailUrl,
                    isActive = card.isActive,
                    isPlaying = isPlaying && card.isActive,
                    modifier =
                        Modifier
                            .width(itemWidth)
                            .combinedClickable(
                                onClick = card.onClick,
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    card.onLongClick()
                                },
                            ),
                    trailingContent = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                card.onLongClick()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * The redesigned speed dial (New UI): a page of numbered rows on a rounded surface, the next page
 * peeking in from the right. Flat rows, one container per page, numbers carried across pages so the
 * list reads as one sequence.
 */
@Composable
internal fun NewSpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artistLabel = stringResource(R.string.speed_dial_artist)

    val distinct =
        remember(speedDialItems) {
            speedDialItems
                .distinctBy {
                    when (it) {
                        is Song -> "song_${it.id}"
                        is Album -> "album_${it.id}"
                        is Artist -> "artist_${it.id}"
                        is Playlist -> "playlist_${it.id}"
                    }
                }.take(24)
        }
    val speedDialSongs = remember(distinct) { distinct.filterIsInstance<Song>() }
    val songIndexById = remember(speedDialSongs) { speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap() }

    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }

    val rows =
        distinct.map { item ->
            val key =
                when (item) {
                    is Song -> "song_${item.id}"
                    is Album -> "album_${item.id}"
                    is Artist -> "artist_${item.id}"
                    is Playlist -> "playlist_${item.id}"
                }
            val subtitle =
                when (item) {
                    is Song -> item.artists.joinToString { it.name }
                    is Album -> item.artists.joinToString { it.name }
                    is Artist -> artistLabel
                    is Playlist ->
                        context.resources.getQuantityString(R.plurals.n_song, item.songCount, item.songCount)
                }
            val trailing =
                (item as? Song)?.let { makeTimeString(it.song.duration * 1000L) }
            SpeedDialRowData(
                key = key,
                title = item.title,
                subtitle = subtitle,
                thumbnailUrl =
                    when (item) {
                        is Song -> item.song.thumbnailUrl
                        is Album -> item.album.thumbnailUrl
                        is Artist -> item.artist.thumbnailUrl
                        is Playlist -> item.thumbnails.firstOrNull()
                    },
                isActive =
                    when (item) {
                        is Song -> item.id == mediaMetadata?.id
                        is Album -> item.id == mediaMetadata?.album?.id
                        else -> false
                    },
                trailing = trailing,
                onClick = {
                    when (item) {
                        is Song -> {
                            if (item.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playSpeedDialQueue(songIndexById[item.id] ?: 0)
                            }
                        }

                        is Album -> navController.navigate("album/${item.id}")
                        is Artist -> navController.navigate("artist/${item.id}")
                        is Playlist -> navController.navigate("local_playlist/${item.id}")
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is Song -> {
                                SongMenu(
                                    originalSong = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is Album -> {
                                AlbumMenu(
                                    originalAlbum = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is Artist -> {
                                ArtistMenu(
                                    originalArtist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is Playlist -> {
                                PlaylistMenu(
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                    }
                },
            )
        } +
            SpeedDialRowData(
                key = "shuffle",
                title = context.getString(R.string.speed_dial_shuffle),
                subtitle = context.getString(R.string.speed_dial_shuffle_description),
                thumbnailUrl = null,
                isActive = false,
                trailing = null,
                isShuffle = true,
                onClick = {
                    if (speedDialSongs.isNotEmpty()) {
                        playSpeedDialQueue(Random.nextInt(speedDialSongs.size))
                    }
                },
            )

    val pages = rows.chunked(SpeedDialRowsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val pageHeight = SpeedDialRowHeight * SpeedDialRowsPerPage + 12.dp

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            pageSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
            key = { page -> pages[page].firstOrNull()?.key ?: "speed_dial_page_$page" },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(pageHeight),
        ) { page ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp),
                ) {
                    pages[page].forEachIndexed { indexInPage, row ->
                        SpeedDialRow(
                            row = row,
                            position = page * SpeedDialRowsPerPage + indexInPage + 1,
                            isPlaying = isPlaying && row.isActive,
                        )
                    }
                }
            }
        }

        if (pages.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                repeat(pages.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier =
                            Modifier
                                .size(if (selected) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialRow(
    row: SpeedDialRowData,
    position: Int,
    isPlaying: Boolean,
) {
    NumberedMediaRow(
        position = position,
        title = row.title,
        subtitle = row.subtitle,
        thumbnailUrl = row.thumbnailUrl,
        thumbnail =
            if (row.isShuffle) {
                {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                null
            },
        isActive = row.isActive,
        isPlaying = isPlaying,
        modifier =
            Modifier
                .padding(horizontal = 6.dp)
                .combinedClickable(
                    onClick = row.onClick,
                    onLongClick = row.onLongClick,
                ),
        trailingContent = {
            row.trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    )
}
