/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playlistimport

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.citali.lunartune.db.entities.ArtistEntity
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.db.entities.SongEntity
import dev.citali.lunartune.spotify.SpotifyLibraryRepository
import dev.citali.lunartune.spotify.models.SpotifyPlaylist
import dev.citali.lunartune.spotify.models.SpotifyTrack
import javax.inject.Inject

@HiltViewModel
class MusicTransferViewModel
    @Inject
    constructor(
        private val spotifyRepository: SpotifyLibraryRepository,
        private val appleMusicRepository: AppleMusicPlaylistRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MusicTransferUiState())
        val uiState: StateFlow<MusicTransferUiState> = _uiState.asStateFlow()

        private val eventChannel = Channel<MusicTransferEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        init {
            refreshSpotify()
        }

        fun refreshSpotify() {
            if (_uiState.value.isRefreshingSpotify) return
            _uiState.update { it.copy(isRefreshingSpotify = true, errorMessage = null) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    spotifyRepository.restoreCachedPlaylists()
                    val session = spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) {
                        _uiState.update {
                            it.copy(
                                isSpotifyAuthenticated = false,
                                spotifyAccountName = "",
                                spotifyPlaylists = emptyList(),
                                isRefreshingSpotify = false,
                            )
                        }
                        return@launch
                    }

                    val playlists = spotifyRepository.refreshPlaylists()
                    _uiState.update {
                        it.copy(
                            isSpotifyAuthenticated = true,
                            spotifyAccountName = session.accountName,
                            spotifyPlaylists = playlists,
                            isRefreshingSpotify = false,
                            errorMessage = spotifyRepository.errorMessage.value,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(
                            isRefreshingSpotify = false,
                            errorMessage = error.message ?: "Spotify playlists could not be loaded.",
                        )
                    }
                } finally {
                    _uiState.update { it.copy(isRefreshingSpotify = false) }
                }
            }
        }

        fun loadSpotifyPlaylist(playlistId: String) {
            if (_uiState.value.loadingSpotifyPlaylistId != null || _uiState.value.isLoadingAppleMusic) return
            val title = _uiState.value.spotifyPlaylists.firstOrNull { it.id == playlistId }?.name.orEmpty()
            _uiState.update { it.copy(loadingSpotifyPlaylistId = playlistId, errorMessage = null) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val songs =
                        spotifyRepository
                            .playlistTracks(playlistId)
                            .mapIndexedNotNull { index, track -> track.toTransferSong(index) }
                    if (songs.isEmpty()) {
                        throw IllegalStateException("That Spotify playlist has no importable tracks.")
                    }
                    eventChannel.send(
                        MusicTransferEvent.PlaylistReady(
                            title = title.ifBlank { "Spotify playlist" },
                            songs = songs,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Spotify playlist tracks could not be loaded.")
                    }
                } finally {
                    _uiState.update { it.copy(loadingSpotifyPlaylistId = null) }
                }
            }
        }

        fun loadAppleMusicPlaylist(shareUrl: String) {
            if (_uiState.value.isLoadingAppleMusic || _uiState.value.loadingSpotifyPlaylistId != null) return
            _uiState.update { it.copy(isLoadingAppleMusic = true, errorMessage = null) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val playlist = appleMusicRepository.fetchPlaylist(shareUrl)
                    val songs = playlist.tracks.mapIndexed { index, track -> track.toTransferSong(index) }
                    eventChannel.send(
                        MusicTransferEvent.PlaylistReady(
                            title = playlist.title,
                            songs = songs,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Apple Music playlist could not be loaded.")
                    }
                } finally {
                    _uiState.update { it.copy(isLoadingAppleMusic = false) }
                }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        override fun onCleared() {
            eventChannel.close()
            super.onCleared()
        }
    }

@Immutable
data class MusicTransferUiState(
    val isRefreshingSpotify: Boolean = false,
    val isSpotifyAuthenticated: Boolean = false,
    val spotifyAccountName: String = "",
    val spotifyPlaylists: List<SpotifyPlaylist> = emptyList(),
    val loadingSpotifyPlaylistId: String? = null,
    val isLoadingAppleMusic: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface MusicTransferEvent {
    data class PlaylistReady(
        val title: String,
        val songs: List<Song>,
    ) : MusicTransferEvent
}

private fun SpotifyTrack.toTransferSong(index: Int): Song? {
    val songTitle = name.trim().takeIf(String::isNotBlank) ?: return null
    val sourceId = id.takeIf(String::isNotBlank) ?: "track_$index"
    val artistEntities =
        artists.mapIndexedNotNull { artistIndex, artist ->
            artist.name.trim().takeIf(String::isNotBlank)?.let { artistName ->
                ArtistEntity(
                    id = "transfer_spotify_artist_${sourceId}_$artistIndex",
                    name = artistName,
                )
            }
        }

    return Song(
        song =
            SongEntity(
                id = "transfer_spotify_$sourceId",
                title = songTitle,
                duration = (durationMs / 1_000).coerceAtLeast(0),
                thumbnailUrl = album?.images?.maxByOrNull { it.width ?: it.height ?: 0 }?.url,
            ),
        artists = artistEntities,
    )
}

private fun AppleMusicTrack.toTransferSong(index: Int): Song {
    val sourceId = id?.takeIf(String::isNotBlank) ?: "track_$index"
    return Song(
        song =
            SongEntity(
                id = "transfer_apple_music_$sourceId",
                title = title,
                duration = durationSeconds.coerceAtLeast(0),
                thumbnailUrl = artworkUrl,
            ),
        artists =
            artists.mapIndexed { artistIndex, artist ->
                ArtistEntity(
                    id = "transfer_apple_music_artist_${sourceId}_$artistIndex",
                    name = artist,
                )
            },
    )
}
