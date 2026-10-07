/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.citali.lunartune.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.citali.lunartune.LocalPlayerAwareWindowInsets
import dev.citali.lunartune.R
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.playlistimport.MusicTransferEvent
import dev.citali.lunartune.playlistimport.MusicTransferUiState
import dev.citali.lunartune.playlistimport.MusicTransferViewModel
import dev.citali.lunartune.spotify.models.SpotifyPlaylist
import dev.citali.lunartune.ui.menu.AddToPlaylistDialogOnline
import dev.citali.lunartune.ui.menu.LoadingScreen

private enum class MusicTransferSource {
    SPOTIFY,
    APPLE_MUSIC,
}

@Composable
fun MusicTransferScreen(
    navController: NavController,
    viewModel: MusicTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedSource by rememberSaveable { mutableStateOf(MusicTransferSource.SPOTIFY) }
    var appleMusicUrl by rememberSaveable { mutableStateOf("") }
    var pendingPlaylistTitle by rememberSaveable { mutableStateOf("") }
    val pendingSongs = remember { mutableStateListOf<Song>() }
    var showDestinationPicker by remember { mutableStateOf(false) }
    var isImportProcessing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    var importStatus by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshSpotify()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MusicTransferEvent.PlaylistReady -> {
                    pendingPlaylistTitle = event.title
                    pendingSongs.clear()
                    pendingSongs.addAll(event.songs)
                    showDestinationPicker = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.music_transfer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSource == MusicTransferSource.SPOTIFY,
                    onClick = {
                        selectedSource = MusicTransferSource.SPOTIFY
                        viewModel.dismissError()
                    },
                    label = { Text(stringResource(R.string.music_transfer_spotify)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.spotify_icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = selectedSource == MusicTransferSource.APPLE_MUSIC,
                    onClick = {
                        selectedSource = MusicTransferSource.APPLE_MUSIC
                        viewModel.dismissError()
                    },
                    label = { Text(stringResource(R.string.music_transfer_apple_music)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }

            when (selectedSource) {
                MusicTransferSource.SPOTIFY -> {
                    SpotifyTransferContent(
                        state = state,
                        onConnect = { navController.navigate("settings/integration") },
                        onRefresh = viewModel::refreshSpotify,
                        onPlaylistClick = viewModel::loadSpotifyPlaylist,
                        modifier = Modifier.weight(1f),
                    )
                }

                MusicTransferSource.APPLE_MUSIC -> {
                    AppleMusicTransferContent(
                        url = appleMusicUrl,
                        onUrlChange = {
                            appleMusicUrl = it
                            viewModel.dismissError()
                        },
                        isLoading = state.isLoadingAppleMusic,
                        isOtherSourceLoading = state.loadingSpotifyPlaylistId != null,
                        onLoad = { viewModel.loadAppleMusicPlaylist(appleMusicUrl) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    AddToPlaylistDialogOnline(
        isVisible = showDestinationPicker,
        allowSyncing = true,
        showDestinationTypeLabels = true,
        initialTextFieldValue = pendingPlaylistTitle,
        songs = pendingSongs,
        onDismiss = { showDestinationPicker = false },
        onProgressStart = { isImportProcessing = it },
        onPercentageChange = { importProgress = it },
        onStatusChange = { importStatus = it },
    )

    LoadingScreen(
        isVisible = isImportProcessing,
        value = importProgress,
        title = stringResource(R.string.music_transfer_matching_title),
        stepText = importStatus,
        indeterminate = importProgress == 0,
    )
}

@Composable
private fun SpotifyTransferContent(
    state: MusicTransferUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!state.isSpotifyAuthenticated) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isRefreshingSpotify) {
                    CircularWavyProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = stringResource(R.string.music_transfer_spotify_connect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onConnect, enabled = !state.isRefreshingSpotify) {
                    Icon(
                        painter = painterResource(R.drawable.spotify_icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.music_transfer_connect_spotify))
                }
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.music_transfer_spotify_playlists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.spotifyAccountName.isNotBlank()) {
                    Text(
                        text = state.spotifyAccountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !state.isRefreshingSpotify) {
                if (state.isRefreshingSpotify) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(22.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = stringResource(R.string.music_transfer_refresh_spotify),
                    )
                }
            }
        }

        when {
            state.spotifyPlaylists.isEmpty() && state.isRefreshingSpotify -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }
            }

            state.spotifyPlaylists.isEmpty() -> {
                Text(
                    text = stringResource(R.string.music_transfer_spotify_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.spotifyPlaylists,
                        key = SpotifyPlaylist::id,
                    ) { playlist ->
                        SpotifyTransferPlaylistRow(
                            playlist = playlist,
                            isLoading = state.loadingSpotifyPlaylistId == playlist.id,
                            isEnabled = state.loadingSpotifyPlaylistId == null,
                            onClick = { onPlaylistClick(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyTransferPlaylistRow(
    playlist: SpotifyPlaylist,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = isEnabled, onClick = onClick)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val artwork = playlist.images.maxByOrNull { it.width ?: it.height ?: 0 }?.url
        if (artwork.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val itemCount = playlist.tracks?.total ?: 0
            val owner = playlist.owner?.displayName?.takeIf(String::isNotBlank)
            val details =
                listOfNotNull(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.n_song, itemCount, itemCount),
                    owner,
                ).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isLoading) {
            CircularWavyProgressIndicator(modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AppleMusicTransferContent(
    url: String,
    onUrlChange: (String) -> Unit,
    isLoading: Boolean,
    isOtherSourceLoading: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.music_transfer_apple_public_only),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.music_transfer_apple_link_label)) },
            placeholder = { Text(stringResource(R.string.music_transfer_apple_link_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLoad,
            enabled = url.isNotBlank() && !isLoading && !isOtherSourceLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.music_transfer_load_apple_playlist))
        }
    }
}
