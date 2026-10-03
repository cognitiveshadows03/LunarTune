/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import dev.citali.lunartune.LocalAnimationsDisabled
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.ArtistSongSortDescendingKey
import dev.citali.lunartune.constants.ArtistSongSortType
import dev.citali.lunartune.constants.ArtistSongSortTypeKey
import dev.citali.lunartune.constants.CONTENT_TYPE_HEADER
import dev.citali.lunartune.constants.HideExplicitKey
import dev.citali.lunartune.extensions.metadata
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.extensions.togglePlayPause
import dev.citali.lunartune.playback.queues.ListQueue
import dev.citali.lunartune.ui.component.HideOnScrollFAB
import dev.citali.lunartune.ui.component.IconButton
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.component.SongListItem
import dev.citali.lunartune.ui.component.SortHeader
import dev.citali.lunartune.ui.menu.SelectionMediaMetadataMenu
import dev.citali.lunartune.ui.menu.SongMenu
import dev.citali.lunartune.ui.utils.backToMain
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.ArtistSongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            ArtistSongSortTypeKey,
            ArtistSongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            ArtistSongSortDescendingKey,
            true,
        )
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val lazyListState = rememberLazyListState()
    var selectedSongIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedSongIdSet = remember(selectedSongIds) { selectedSongIds.toSet() }
    val selectedSongs = remember(songs, selectedSongIdSet) { songs.filter { it.id in selectedSongIdSet } }
    val clearSelection = remember { { selectedSongIds = emptyList() } }
    BackHandler(enabled = selectedSongIds.isNotEmpty(), onBack = clearSelection)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                ArtistSongSortType.NAME -> R.string.sort_by_name
                                ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        },
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, item -> item.id },
            ) { index, song ->
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    isSelected = song.id in selectedSongIdSet,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                if (selectedSongIds.isNotEmpty()) return@IconButton
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectedSongIds.isNotEmpty()) {
                                        selectedSongIds = if (song.id in selectedSongIdSet) selectedSongIds - song.id else selectedSongIds + song.id
                                    } else if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(R.string.queue_all_songs),
                                                items = songs.map { it.toMediaItem() },
                                                startIndex = index,
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (song.id !in selectedSongIdSet) selectedSongIds = selectedSongIds + song.id
                                },
                            ).animateItem(),
                )
            }
        }

        TopAppBar(
            title = { Text(artist?.artist?.name.orEmpty()) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )

        ArtistSongsSelectionToolbar(
            visible = selectedSongs.isNotEmpty(),
            allSelected = songs.isNotEmpty() && selectedSongs.size == songs.size,
            onToggleAll = { selectedSongIds = if (selectedSongs.size == songs.size) emptyList() else songs.map { it.id } },
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

        HideOnScrollFAB(
            visible = selectedSongs.isEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            label = context.getString(R.string.shuffle),
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = artist?.artist?.name,
                        items = songs.shuffled().map { it.toMediaItem() },
                    ),
                )
            },
        )
    }
}

@Composable
private fun BoxScope.ArtistSongsSelectionToolbar(
    visible: Boolean,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (animationsDisabled) 0 else 220)) +
            slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it / 2 },
        exit = fadeOut(tween(if (animationsDisabled) 0 else 220)) +
            slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).padding(16.dp),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = onMoreClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            },
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(role = Role.Button, onClick = onToggleAll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(if (allSelected) R.drawable.deselect else R.drawable.select_all),
                    contentDescription = null,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = stringResource(if (allSelected) R.string.clear_selection else R.string.select),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
