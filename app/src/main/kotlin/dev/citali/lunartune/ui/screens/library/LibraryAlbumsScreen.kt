/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.citali.lunartune.LocalDatabase
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.LocalPlayerConnection
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AlbumFilter
import dev.citali.lunartune.constants.AlbumFilterKey
import dev.citali.lunartune.constants.AlbumSortDescendingKey
import dev.citali.lunartune.constants.AlbumSortType
import dev.citali.lunartune.constants.AlbumSortTypeKey
import dev.citali.lunartune.constants.HideExplicitKey
import dev.citali.lunartune.constants.YtmSyncKey
import dev.citali.lunartune.playback.queues.LocalAlbumRadio
import dev.citali.lunartune.ui.component.ExpressivePullToRefreshBox
import dev.citali.lunartune.ui.component.ItemThumbnail
import dev.citali.lunartune.ui.component.LocalMenuState
import dev.citali.lunartune.ui.menu.AlbumMenu
import dev.citali.lunartune.utils.rememberEnumPreference
import dev.citali.lunartune.utils.rememberPreference
import dev.citali.lunartune.viewmodels.LibraryAlbumsViewModel

/**
 * Narrowest an album tile may get. The tile has to be wide enough to print an
 * artist name at 10.sp, so the grid asks for a healthy minimum and lets the
 * column count fall out of the screen width instead of forcing four columns.
 */
private val AlbumGridMinCellWidth = 104.dp

private val AlbumGridSidePadding = 16.dp

private val AlbumGridColumnSpacing = 8.dp

/** Corner radius of an album tile and its cover. */
private val AlbumTileCorner = 22.dp

/** Side inset that keeps a label clear of the tile's rounded corners. */
private val AlbumTileLabelSideInset = 5.dp

/** Room under the last label line so the corner arcs cannot reach it. */
private val AlbumTileLabelBottomInset = 10.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumsScreen(
    topContentPadding: Dp,
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            AlbumSortTypeKey,
            AlbumSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    var isGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val featuredAlbum = albums.firstOrNull()

    val filteredAlbums =
        if (hideExplicit) {
            albums.filter { !it.album.explicit }
        } else {
            albums
        }

    // Issue 2: player-aware bottom padding
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.sync() },
        modifier = Modifier.fillMaxSize(),
        indicatorOffset = topContentPadding - 24.dp,
    ) {


            // Main albums list or grid layout
            if (isGridView) {
                // Column count follows the screen instead of being pinned to four.
                // Four on a normal phone left each tile about 70dp wide — narrower
                // than the artist name it has to print, so names were chopped no
                // matter how they were ellipsised.
                val gridColumns = remember { GridCells.Adaptive(minSize = AlbumGridMinCellWidth) }
                LazyVerticalGrid(
                    columns = gridColumns,
                    contentPadding = PaddingValues(start = AlbumGridSidePadding, top = topContentPadding, end = AlbumGridSidePadding, bottom = playerAwareBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(AlbumGridColumnSpacing),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "album_controls", contentType = "controls") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortLabel =
                    if (filter == AlbumFilter.DOWNLOADED_FULL) {
                        stringResource(R.string.filter_downloaded)
                    } else {
                        when (sortType) {
                            AlbumSortType.CREATE_DATE -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.newest_first,
                                    )
                                } else {
                                    stringResource(R.string.oldest_first)
                                }
                            }

                            AlbumSortType.NAME -> {
                                if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                            }

                            AlbumSortType.ARTIST -> {
                                stringResource(R.string.sort_artist)
                            }

                            AlbumSortType.YEAR -> {
                                if (sortDescending) stringResource(R.string.newest_year) else stringResource(R.string.oldest_year)
                            }

                            AlbumSortType.SONG_COUNT -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.most_tracks,
                                    )
                                } else {
                                    stringResource(R.string.least_tracks)
                                }
                            }

                            AlbumSortType.LENGTH -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.longest_duration,
                                    )
                                } else {
                                    stringResource(R.string.shortest_duration)
                                }
                            }

                            AlbumSortType.PLAY_TIME -> {
                                stringResource(R.string.most_played_sort)
                            }
                        }
                    }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentSortLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.expand_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            AlbumSortType.entries.forEach { type ->
                                val label =
                                    when (type) {
                                        AlbumSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                        AlbumSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                        AlbumSortType.ARTIST -> stringResource(R.string.sort_artist)
                                        AlbumSortType.YEAR -> stringResource(R.string.year_sort)
                                        AlbumSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                        AlbumSortType.LENGTH -> stringResource(R.string.duration_sort)
                                        AlbumSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                                    }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        filter = AlbumFilter.LIKED
                                        onSortTypeChange(type)
                                        // Issue 4: A-Z sort defaults to ascending
                                        if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                        showSortMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_downloaded)) },
                                onClick = {
                                    filter = AlbumFilter.DOWNLOADED_FULL
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }

                    // Sort direction toggle button
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSortDescendingChange(!sortDescending) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                                ),
                            contentDescription =
                                if (sortDescending) {
                                    stringResource(
                                        R.string.sort_descending,
                                    )
                                } else {
                                    stringResource(R.string.sort_ascending)
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Grid / List Toggle layout controls
                Row(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isGridView = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.queue_music),
                            contentDescription = stringResource(R.string.list_view),
                            tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isGridView = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.album),
                            contentDescription = stringResource(R.string.grid_view),
                            tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
                    }
                    // Featured Album spotlight card span all 4 columns
                    item(span = { GridItemSpan(maxLineSpan) }, key = "featured_album_card") {
                        featuredAlbum?.let { album ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            navController.navigate("album/${album.id}")
                                        }.padding(20.dp),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ItemThumbnail(
                                            thumbnailUrl = album.album.thumbnailUrl,
                                            isActive = false,
                                            isPlaying = false,
                                            shape = RoundedCornerShape(24.dp),
                                            contentScale = ContentScale.Crop,
                                            modifier =
                                                Modifier
                                                    .size(80.dp),
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.featured_album_badge),
                                                style =
                                                    MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                    ),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = album.album.title,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = album.artists.joinToString(", ") { it.name },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                                    }
                                                }
                                            },
                                            shape = CircleShape,
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.play),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.play),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    AlbumMenu(
                                                        originalAlbum = album,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.more_vert),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // albums grid
                    items(filteredAlbums, key = { it.id }) { album ->
                        Column(
                            // A small inset inside the rounded tile: the corner
                            // arcs are 22dp, and a line printed flush against the
                            // bottom edge loses its first and last glyph to them.
                            // The labels therefore start clear of the corners.
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AlbumTileCorner))
                                    .combinedClickable(
                                        onClick = { navController.navigate("album/${album.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ).padding(bottom = AlbumTileLabelBottomInset),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(AlbumTileCorner)),
                            ) {
                                ItemThumbnail(
                                    thumbnailUrl = album.album.thumbnailUrl,
                                    isActive = false,
                                    isPlaying = false,
                                    shape = RoundedCornerShape(AlbumTileCorner),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                // Play Overlay button on cover
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable {
                                                coroutineScope.launch {
                                                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                                    }
                                                }
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.play),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Two lines each: an album title or artist that runs
                            // past the tile now finishes on the next line instead
                            // of disappearing behind an ellipsis.
                            Text(
                                text = album.album.title,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 14.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AlbumTileLabelSideInset),
                            )
                            Text(
                                text = album.artists.joinToString(", ") { it.name },
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AlbumTileLabelSideInset),
                            )
                        }
                    }
                }
            } else {
                // List View
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, top = topContentPadding, end = 24.dp, bottom = playerAwareBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "album_controls", contentType = "controls") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortLabel =
                    if (filter == AlbumFilter.DOWNLOADED_FULL) {
                        stringResource(R.string.filter_downloaded)
                    } else {
                        when (sortType) {
                            AlbumSortType.CREATE_DATE -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.newest_first,
                                    )
                                } else {
                                    stringResource(R.string.oldest_first)
                                }
                            }

                            AlbumSortType.NAME -> {
                                if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                            }

                            AlbumSortType.ARTIST -> {
                                stringResource(R.string.sort_artist)
                            }

                            AlbumSortType.YEAR -> {
                                if (sortDescending) stringResource(R.string.newest_year) else stringResource(R.string.oldest_year)
                            }

                            AlbumSortType.SONG_COUNT -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.most_tracks,
                                    )
                                } else {
                                    stringResource(R.string.least_tracks)
                                }
                            }

                            AlbumSortType.LENGTH -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.longest_duration,
                                    )
                                } else {
                                    stringResource(R.string.shortest_duration)
                                }
                            }

                            AlbumSortType.PLAY_TIME -> {
                                stringResource(R.string.most_played_sort)
                            }
                        }
                    }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentSortLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.expand_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            AlbumSortType.entries.forEach { type ->
                                val label =
                                    when (type) {
                                        AlbumSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                        AlbumSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                        AlbumSortType.ARTIST -> stringResource(R.string.sort_artist)
                                        AlbumSortType.YEAR -> stringResource(R.string.year_sort)
                                        AlbumSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                        AlbumSortType.LENGTH -> stringResource(R.string.duration_sort)
                                        AlbumSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                                    }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        filter = AlbumFilter.LIKED
                                        onSortTypeChange(type)
                                        // Issue 4: A-Z sort defaults to ascending
                                        if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                        showSortMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_downloaded)) },
                                onClick = {
                                    filter = AlbumFilter.DOWNLOADED_FULL
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }

                    // Sort direction toggle button
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSortDescendingChange(!sortDescending) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                                ),
                            contentDescription =
                                if (sortDescending) {
                                    stringResource(
                                        R.string.sort_descending,
                                    )
                                } else {
                                    stringResource(R.string.sort_ascending)
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Grid / List Toggle layout controls
                Row(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isGridView = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.queue_music),
                            contentDescription = stringResource(R.string.list_view),
                            tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { isGridView = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.album),
                            contentDescription = stringResource(R.string.grid_view),
                            tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(filteredAlbums, key = { it.id }) { album ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .combinedClickable(
                                        onClick = { navController.navigate("album/${album.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ItemThumbnail(
                                thumbnailUrl = album.album.thumbnailUrl,
                                isActive = false,
                                isPlaying = false,
                                shape = RoundedCornerShape(20.dp),
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(60.dp),
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.album.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = album.artists.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                                            playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                        }
                                    }
                                },
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.play),
                                    contentDescription = stringResource(R.string.play),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
    }
}
