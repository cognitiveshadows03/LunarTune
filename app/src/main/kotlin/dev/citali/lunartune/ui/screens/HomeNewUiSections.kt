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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.QuickPicksDisplayMode
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.PlayerConnection
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.extensions.togglePlayPause
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.MenuState
import dev.citali.lunartune.ui.component.NumberedMediaRow
import dev.citali.lunartune.ui.menu.SongMenu
import dev.citali.lunartune.ui.menu.YouTubeSongMenu
import dev.citali.lunartune.ui.utils.displayArtworkUrl
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.HomePage

/** Corner radius of the quick-pick cards: large enough to read as a poster, not as a button. */
private val QuickPickCorner = 26.dp

/** Card width as a fraction of the feed width, so the next card always peeks. */
private const val QuickPickWidthFraction = 0.72f

/** Posters, so a touch taller than wide. */
private const val QuickPickAspectRatio = 1.28f

/** Height the dense quick-pick rows are laid out at. */
private val QuickPickRowHeight = 76.dp

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
                    .height(QuickPickRowHeight * 4),
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
