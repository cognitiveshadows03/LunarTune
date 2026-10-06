/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.CONTENT_TYPE_ALBUM
import dev.citali.lunartune.constants.CONTENT_TYPE_ARTIST
import dev.citali.lunartune.constants.CONTENT_TYPE_LIST
import dev.citali.lunartune.constants.CONTENT_TYPE_PLAYLIST
import dev.citali.lunartune.constants.CONTENT_TYPE_SONG
import dev.citali.lunartune.constants.GridThumbnailHeight
import dev.citali.lunartune.extensions.metadata
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.playback.queues.YouTubeQueue
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.MultiSelectFloatingToolbar
import dev.citali.lunartune.ui.component.YouTubeGridItem
import dev.citali.lunartune.ui.component.YouTubeListItem
import dev.citali.lunartune.ui.component.shimmer.GridItemPlaceHolder
import dev.citali.lunartune.ui.component.shimmer.ListItemPlaceHolder
import dev.citali.lunartune.ui.component.shimmer.ShimmerHost
import dev.citali.lunartune.ui.menu.YouTubeAlbumMenu
import dev.citali.lunartune.ui.menu.YouTubeArtistMenu
import dev.citali.lunartune.ui.menu.SelectionMediaMetadataMenu
import dev.citali.lunartune.ui.menu.YouTubePlaylistMenu
import dev.citali.lunartune.ui.menu.YouTubeSongMenu
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.viewmodels.ArtistItemsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val title by viewModel.title.collectAsStateWithLifecycle()
    val itemsPage by viewModel.itemsPage.collectAsStateWithLifecycle()
    val itemsLayout by viewModel.itemsLayout.collectAsStateWithLifecycle()
    val pageSongs = remember(itemsPage?.items) {
        itemsPage?.items.orEmpty().filterIsInstance<SongItem>().distinctBy { it.id }
    }
    var selectedSongIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedSongIdSet = remember(selectedSongIds) { selectedSongIds.toSet() }
    val selectedSongs = remember(pageSongs, selectedSongIdSet) {
        pageSongs.filter { it.id in selectedSongIdSet }
    }
    val isSongListPage = itemsLayout == ArtistItemsPageLayout.LIST && itemsPage?.items?.firstOrNull() is SongItem
    val activeSelectionCount = if (isSongListPage) selectedSongs.size else 0
    val clearSelection = remember { { selectedSongIds = emptyList() } }

    LaunchedEffect(isSongListPage, pageSongs) {
        val validSongIds = if (isSongListPage) pageSongs.mapTo(mutableSetOf<String>()) { it.id } else emptySet()
        selectedSongIds = selectedSongIds.filter(validSongIds::contains)
    }
    BackHandler(enabled = activeSelectionCount > 0, onBack = clearSelection)

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    if (itemsPage == null) {
        ShimmerHost(
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        ) {
            repeat(8) {
                ListItemPlaceHolder()
            }
        }
    } else if (itemsLayout == ArtistItemsPageLayout.LIST && itemsPage?.items?.firstOrNull() is SongItem) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = itemsPage?.items.orEmpty().distinctBy { it.id },
                    key = { "artist_items_${it.contentKey}" },
                    contentType = { it.contentType },
                ) { item ->
                    YouTubeListItem(
                        item = item,
                        isSelected = item is SongItem && item.id in selectedSongIdSet,
                        isActive =
                            when (item) {
                                is SongItem -> mediaMetadata?.id == item.id
                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                else -> false
                            },
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (activeSelectionCount == 0) {
                                        menuState.show {
                                            when (item) {
                                                is SongItem -> {
                                                    YouTubeSongMenu(
                                                        song = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }

                                                is AlbumItem -> {
                                                    YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }

                                                is ArtistItem -> {
                                                    YouTubeArtistMenu(
                                                        artist = item,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }

                                                is PlaylistItem -> {
                                                    YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (activeSelectionCount > 0) {
                                            if (item is SongItem) {
                                                selectedSongIds =
                                                    if (item.id in selectedSongIdSet) {
                                                        selectedSongIds - item.id
                                                    } else {
                                                        selectedSongIds + item.id
                                                    }
                                            }
                                        } else {
                                            when (item) {
                                                is SongItem -> {
                                                    if (item.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = title,
                                                                items = pageSongs.map { it.toMediaItem() },
                                                                startIndex = pageSongs.indexOfFirst { it.id == item.id }.coerceAtLeast(0),
                                                            ),
                                                        )
                                                    }
                                                }

                                                is AlbumItem -> {
                                                    navController.navigate("album/${item.id}")
                                                }

                                                is ArtistItem -> {
                                                    navController.navigate("artist/${item.id}")
                                                }

                                                is PlaylistItem -> {
                                                    navController.navigate("online_playlist/${item.id}")
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (item is SongItem) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (item.id !in selectedSongIdSet) {
                                                selectedSongIds = selectedSongIds + item.id
                                            }
                                        }
                                    },
                                ),
                    )
                }

                if (itemsPage?.continuation != null) {
                    item(key = "loading") {
                        ShimmerHost {
                            repeat(3) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            }
            MultiSelectFloatingToolbar(
                visible = activeSelectionCount > 0,
                allSelected = pageSongs.isNotEmpty() && selectedSongs.size == pageSongs.size,
                onToggleAll = {
                    selectedSongIds =
                        if (pageSongs.isNotEmpty() && selectedSongs.size == pageSongs.size) {
                            emptyList()
                        } else {
                            pageSongs.map { it.id }
                        }
                },
                onMoreClick = {
                    menuState.show {
                        SelectionMediaMetadataMenu(
                            songSelection = selectedSongs.map { it.toMediaItem().metadata!! },
                            currentItems = emptyList(),
                            onDismiss = menuState::dismiss,
                            clearAction = clearSelection,
                        )
                    }
                },
            )
        }
    } else {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            items(
                items = itemsPage?.items.orEmpty().distinctBy { it.id },
                key = { "artist_items_${it.contentKey}" },
                contentType = { it.contentType },
            ) { item ->
                YouTubeGridItem(
                    item = item,
                    isActive =
                        when (item) {
                            is SongItem -> mediaMetadata?.id == item.id
                            is AlbumItem -> mediaMetadata?.album?.id == item.id
                            else -> false
                        },
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    coroutineScope = coroutineScope,
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = {
                                    when (item) {
                                        is SongItem -> {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                    item.toMediaMetadata(),
                                                ),
                                            )
                                        }

                                        is AlbumItem -> {
                                            navController.navigate("album/${item.id}")
                                        }

                                        is ArtistItem -> {
                                            navController.navigate("artist/${item.id}")
                                        }

                                        is PlaylistItem -> {
                                            navController.navigate("online_playlist/${item.id}")
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        when (item) {
                                            is SongItem -> {
                                                YouTubeSongMenu(
                                                    song = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }

                                            is AlbumItem -> {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }

                                            is ArtistItem -> {
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }

                                            is PlaylistItem -> {
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    }
                                },
                            ).animateItem(),
                )
            }

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost(Modifier.animateItem()) {
                        GridItemPlaceHolder(fillMaxWidth = true)
                    }
                }
            }
        }
    }

    TopAppBar(
        title = {
            if (activeSelectionCount > 0) {
                Text(pluralStringResource(R.plurals.n_song, activeSelectionCount, activeSelectionCount))
            } else {
                Text(title)
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (activeSelectionCount > 0) clearSelection() else navController.navigateUp()
                },
                onLongClick = {
                    if (activeSelectionCount == 0) navController.backToMain()
                },
            ) {
                Icon(
                    painterResource(if (activeSelectionCount > 0) R.drawable.close else R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (activeSelectionCount == 0 && pageSongs.isNotEmpty()) {
                IconButton(
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = pageSongs.map { it.toMediaItem() },
                            ),
                        )
                    },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                    )
                }
                IconButton(
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = pageSongs.shuffled().map { it.toMediaItem() },
                            ),
                        )
                    },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

private val YTItem.contentKey: String
    get() {
        val type =
            when (this) {
                is SongItem -> "song"
                is AlbumItem -> "album"
                is ArtistItem -> "artist"
                is PlaylistItem -> "playlist"
                else -> "item"
            }
        return "${type}_$id"
    }

private val YTItem.contentType: Int
    get() =
        when (this) {
            is SongItem -> CONTENT_TYPE_SONG
            is AlbumItem -> CONTENT_TYPE_ALBUM
            is ArtistItem -> CONTENT_TYPE_ARTIST
            is PlaylistItem -> CONTENT_TYPE_PLAYLIST
            else -> CONTENT_TYPE_LIST
        }
