/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.citali.lunartune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import dev.citali.lunartune.R
import dev.citali.lunartune.constants.AiApiKeyKey
import dev.citali.lunartune.constants.AiApiValidationStatus
import dev.citali.lunartune.constants.AiApiValidationStatusKey
import dev.citali.lunartune.constants.AiCustomEndpointKey
import dev.citali.lunartune.constants.AiProvider
import dev.citali.lunartune.constants.AiProviderKey
import dev.citali.lunartune.constants.AlbumFilter
import dev.citali.lunartune.constants.AlbumFilterKey
import dev.citali.lunartune.constants.AlbumSortDescendingKey
import dev.citali.lunartune.constants.AlbumSortType
import dev.citali.lunartune.constants.AlbumSortTypeKey
import dev.citali.lunartune.constants.ArtistFilter
import dev.citali.lunartune.constants.ArtistFilterKey
import dev.citali.lunartune.constants.ArtistSongSortDescendingKey
import dev.citali.lunartune.constants.ArtistSongSortType
import dev.citali.lunartune.constants.ArtistSongSortTypeKey
import dev.citali.lunartune.constants.ArtistSortDescendingKey
import dev.citali.lunartune.constants.ArtistSortType
import dev.citali.lunartune.constants.ArtistSortTypeKey
import dev.citali.lunartune.constants.HideExplicitKey
import dev.citali.lunartune.constants.HideVideoKey
import dev.citali.lunartune.constants.LibraryFilter
import dev.citali.lunartune.constants.PlaylistSortDescendingKey
import dev.citali.lunartune.constants.PlaylistSortType
import dev.citali.lunartune.constants.PlaylistSortTypeKey
import dev.citali.lunartune.constants.SongFilter
import dev.citali.lunartune.constants.SongFilterKey
import dev.citali.lunartune.constants.SongSortDescendingKey
import dev.citali.lunartune.constants.SongSortType
import dev.citali.lunartune.constants.SongSortTypeKey
import dev.citali.lunartune.constants.TopSize
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.Playlist
import dev.citali.lunartune.db.entities.Song
import dev.citali.lunartune.extensions.filterExplicit
import dev.citali.lunartune.extensions.filterExplicitAlbums
import dev.citali.lunartune.extensions.filterVideo
import dev.citali.lunartune.extensions.reversed
import dev.citali.lunartune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import dev.citali.lunartune.library.LibraryTopMix
import dev.citali.lunartune.library.ObserveLibraryTopMixesUseCase
import dev.citali.lunartune.library.RefreshLibraryTopMixesResult
import dev.citali.lunartune.library.RefreshLibraryTopMixesUseCase
import dev.citali.lunartune.library.TopMixGenerationFailure
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.toMediaMetadata
import dev.citali.lunartune.playback.DownloadUtil
import dev.citali.lunartune.utils.SyncUtils
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import dev.citali.lunartune.utils.reportException
import java.text.Collator
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val MOST_PLAYED_ALBUM_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1000L

@HiltViewModel
class LibrarySongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        val allSongs =
            context.dataStore.data
                .map {
                    Triple(
                        Triple(
                            it[SongFilterKey].toEnum(SongFilter.LIKED),
                            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                            (it[SongSortDescendingKey] ?: true),
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit, hideVideo) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        SongFilter.LIBRARY -> {
                            database.songs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        SongFilter.LIKED -> {
                            database.likedSongs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        SongFilter.DOWNLOADED -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs.filter { song: Song ->
                                            downloads[song.id]?.state == Download.STATE_COMPLETED
                                        }
                                    }.map { songs ->
                                        when (sortType) {
                                            SongSortType.CREATE_DATE -> {
                                                songs.sortedBy { song: Song ->
                                                    downloads[song.id]?.updateTimeMs ?: 0L
                                                }
                                            }

                                            SongSortType.NAME -> {
                                                songs.sortedBy { song: Song -> song.song.title }
                                            }

                                            SongSortType.ARTIST -> {
                                                val collator =
                                                    Collator.getInstance(Locale.getDefault())
                                                collator.strength = Collator.PRIMARY
                                                songs.sortedWith(
                                                    compareBy<Song, String>(collator) { song ->
                                                        song.artists.joinToString("") { artist -> artist.name }
                                                    },
                                                )
                                            }

                                            SongSortType.PLAY_TIME -> {
                                                songs.sortedBy { song: Song -> song.song.totalPlayTime }
                                            }
                                        }.reversed(descending)
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo)
                                    }
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: SongFilter) {
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    when (filter) {
                        SongFilter.LIKED -> syncUtils.syncLikedSongs()
                        SongFilter.LIBRARY -> syncUtils.syncLibrarySongs()
                        SongFilter.DOWNLOADED -> Unit
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun syncLikedSongs() {
            refresh(SongFilter.LIKED)
        }

        fun syncLibrarySongs() {
            refresh(SongFilter.LIBRARY)
        }
    }

private const val LIBRARY_BACKFILL_MAX_ITEMS = 25
private const val LIBRARY_BACKFILL_PARALLELISM = 4

@HiltViewModel
class LibraryArtistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        val allArtists =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                        it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                        it[ArtistSortDescendingKey] ?: true,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filter, sortType, descending) ->
                    when (filter) {
                        ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                        ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: ArtistFilter) {
            if (filter != ArtistFilter.LIKED) return
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    syncUtils.syncArtistsSubscriptions()
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun sync() {
            refresh(ArtistFilter.LIKED)
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                allArtists.collect { artists ->
                    // Bounded backfill: cap each pass and fetch with limited parallelism so a
                    // large stale library can't saturate the network and starve the UI.
                    val staleArtists =
                        artists
                            .map { it.artist }
                            .filter {
                                it.thumbnailUrl == null || Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                            }.take(LIBRARY_BACKFILL_MAX_ITEMS)
                    if (staleArtists.isEmpty()) return@collect
                    coroutineScope {
                        val backfillSemaphore = Semaphore(LIBRARY_BACKFILL_PARALLELISM)
                        staleArtists.map { artist ->
                            launch {
                                backfillSemaphore.withPermit {
                                    YouTube.artist(artist.id).onSuccess { artistPage ->
                                        database.query {
                                            update(artist, artistPage)
                                        }
                                    }
                                }
                            }
                        }.joinAll()
                    }
                }
            }
        }
    }

@HiltViewModel
class LibraryAlbumsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        val allAlbums =
            context.dataStore.data
                .map {
                    Pair(
                        Triple(
                            it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                            it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                            it[AlbumSortDescendingKey] ?: true,
                        ),
                        it[HideExplicitKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        AlbumFilter.DOWNLOADED -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs
                                            .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                                            .mapNotNull { it.song.albumId }
                                            .toSet()
                                    }.flatMapLatest { downloadedAlbumIds ->
                                        database
                                            .albumsByIds(downloadedAlbumIds, sortType, descending)
                                            .map { albums -> albums.filterExplicitAlbums(hideExplicit) }
                                    }
                            }
                        }

                        AlbumFilter.DOWNLOADED_FULL -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs
                                            .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                                            .mapNotNull { song -> song.song.albumId?.let { albumId -> albumId to song } }
                                            .groupBy({ it.first }, { it.second })
                                            .mapValues { (_, songList) -> songList.size }
                                    }.flatMapLatest { downloadedCountByAlbum ->
                                        database
                                            .albumsByIds(downloadedCountByAlbum.keys, sortType, descending)
                                            .map { albums ->
                                                albums
                                                    .filter { album ->
                                                        val totalSongsInAlbum = album.album.songCount
                                                        val downloadedSongsCount = downloadedCountByAlbum[album.album.id] ?: 0
                                                        totalSongsInAlbum > 0 && downloadedSongsCount >= totalSongsInAlbum
                                                    }.filterExplicitAlbums(hideExplicit)
                                            }
                                    }
                            }
                        }

                        AlbumFilter.LIBRARY -> {
                            database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }

                        AlbumFilter.LIKED -> {
                            database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: AlbumFilter) {
            if (filter != AlbumFilter.LIKED) return
            if (_isRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                try {
                    syncUtils.syncLikedAlbums()
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun sync() {
            refresh(AlbumFilter.LIKED)
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                allAlbums.collect { albums ->
                    // Bounded backfill: cap each pass and fetch with limited parallelism so a
                    // large stale library can't saturate the network and starve the UI.
                    val staleAlbums =
                        albums
                            .filter {
                                it.album.songCount == 0
                            }.take(LIBRARY_BACKFILL_MAX_ITEMS)
                    if (staleAlbums.isEmpty()) return@collect
                    coroutineScope {
                        val backfillSemaphore = Semaphore(LIBRARY_BACKFILL_PARALLELISM)
                        staleAlbums.map { album ->
                            launch {
                                backfillSemaphore.withPermit {
                                    YouTube
                                        .album(album.id)
                                        .onSuccess { albumPage ->
                                            database.query {
                                                update(album.album, albumPage, album.artists)
                                            }
                                        }.onFailure {
                                            reportException(it)
                                            if (it.message?.contains("NOT_FOUND") == true) {
                                                database.query {
                                                    delete(album.album)
                                                }
                                            }
                                        }
                                }
                            }
                        }.joinAll()
                    }
                }
            }
        }
    }

@HiltViewModel
class LibraryPlaylistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
    ) : ViewModel() {
        val allPlaylists =
            context.dataStore.data
                .map {
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (
                        it[PlaylistSortDescendingKey]
                            ?: true
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortType, descending) ->
                    database.playlists(sortType, descending)
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        fun sync() {
            viewModelScope.launch(Dispatchers.IO) {
                _isRefreshing.value = true
                syncUtils.syncSavedPlaylists()
                syncUtils.syncAutoSyncPlaylists()
                _isRefreshing.value = false
            }
        }

        fun updateCustomPlaylistOrder(playlists: List<Playlist>) {
            if (playlists.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                database.withTransaction {
                    playlists.forEachIndexed { index, playlist ->
                        setPlaylistCustomOrder(playlist.id, index)
                    }
                }
            }
        }

        val topValue =
            context.dataStore.data
                .map { it[TopSize] ?: "50" }
                .distinctUntilChanged()
    }

@HiltViewModel
class ArtistSongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val artistId = savedStateHandle.get<String>("artistId")!!
        val artist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        val songs =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (
                            it[ArtistSongSortDescendingKey]
                                ?: true
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortDesc, hideExplicit, hideVideo) ->
                    val (sortType, descending) = sortDesc
                    database.artistSongs(artistId, sortType, descending).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

@HiltViewModel
class LibraryMixViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        observeLibraryTopMixes: ObserveLibraryTopMixesUseCase,
        private val refreshLibraryTopMixes: RefreshLibraryTopMixesUseCase,
    ) : ViewModel() {
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()
        private val _isTopMixRefreshing = MutableStateFlow(false)
        private val _topMixInitialError = MutableStateFlow<String?>(null)
        private val _topMixEvents = MutableSharedFlow<String>()
        val topMixEvents = _topMixEvents.asSharedFlow()
        private var hasRequestedInitialTopMixGeneration = false

        private val isTopMixAiAvailable =
            context.dataStore.data
                .map { prefs ->
                    val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
                    provider != AiProvider.NONE &&
                        prefs[AiApiKeyKey].orEmpty().isNotBlank() &&
                        (provider != AiProvider.CUSTOM || prefs[AiCustomEndpointKey].orEmpty().isNotBlank()) &&
                        prefs[AiApiValidationStatusKey].toEnum(AiApiValidationStatus.UNKNOWN) != AiApiValidationStatus.FAILED
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Lazily, false)

        private val observedTopMixes =
            observeLibraryTopMixes()
                .map<List<LibraryTopMix>, List<LibraryTopMix>?> { it }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    reportException(throwable)
                    _topMixInitialError.value = context.getString(R.string.library_top_mixes_failed)
                    emit(emptyList())
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val topMixesUiState =
            combine(
                observedTopMixes,
                isTopMixAiAvailable,
                _isTopMixRefreshing,
                _topMixInitialError,
            ) { mixes, isAiAvailable, isRefreshing, initialError ->
                when {
                    mixes == null -> {
                        LibraryTopMixesUiState.Loading
                    }

                    initialError != null && mixes.isEmpty() -> {
                        LibraryTopMixesUiState.Error(initialError)
                    }

                    mixes.isNotEmpty() -> {
                        LibraryTopMixesUiState.Success(
                            mixes = ImmutableList.copyOf(mixes.map { it.toUiModel() }),
                            isRefreshing = isRefreshing,
                        )
                    }

                    !isAiAvailable -> {
                        LibraryTopMixesUiState.Empty(
                            reason = LibraryTopMixEmptyReason.AI_NOT_CONFIGURED,
                            isRefreshing = isRefreshing,
                        )
                    }

                    else -> {
                        LibraryTopMixesUiState.Empty(
                            reason = LibraryTopMixEmptyReason.NO_RECENT_HISTORY,
                            isRefreshing = isRefreshing,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryTopMixesUiState.Loading)

        val mostPlayedAlbumUiState =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database
                        .mostPlayedAlbums(
                            fromTimeStamp = System.currentTimeMillis() - MOST_PLAYED_ALBUM_WINDOW_MILLIS,
                            limit = 10,
                        ).flatMapLatest { albums ->
                            val album =
                                albums
                                    .filterExplicitAlbums(hideExplicit)
                                    .firstOrNull()
                            if (album == null) {
                                flowOf(MostPlayedAlbumUiState.Empty)
                            } else {
                                database.albumWithSongs(album.id).map { albumWithSongs ->
                                    val songs = albumWithSongs?.songs.orEmpty()
                                    if (songs.isEmpty()) {
                                        MostPlayedAlbumUiState.Empty
                                    } else {
                                        MostPlayedAlbumUiState.Success(
                                            album =
                                                MostPlayedAlbumUiModel(
                                                    id = album.id,
                                                    title = album.title,
                                                    thumbnailUrl = album.thumbnailUrl,
                                                    trackCount = songs.size,
                                                    tracks = ImmutableList.copyOf(songs.map { it.toMediaMetadata() }),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                }.flowOn(Dispatchers.IO)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    reportException(throwable)
                    emit(MostPlayedAlbumUiState.Error(context.getString(R.string.error_unknown)))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MostPlayedAlbumUiState.Loading)

        init {
            viewModelScope.launch {
                combine(observedTopMixes, isTopMixAiAvailable) { mixes, isAiAvailable ->
                    mixes != null && mixes.isEmpty() && isAiAvailable
                }.distinctUntilChanged()
                    .collect { shouldGenerate ->
                        if (shouldGenerate && !hasRequestedInitialTopMixGeneration) {
                            hasRequestedInitialTopMixGeneration = true
                            refreshTopMixesInternal(isInitialGeneration = true)
                        }
                    }
            }
        }

        fun syncAllLibrary() {
            if (_isRefreshing.value) return
            _isRefreshing.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    syncUtils.performFullSync()
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error during manual sync")
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        fun refreshTopMixes() {
            hasRequestedInitialTopMixGeneration = true
            refreshTopMixesInternal(isInitialGeneration = false)
        }

        private fun refreshTopMixesInternal(isInitialGeneration: Boolean) {
            if (_isTopMixRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isTopMixRefreshing.value = true
                _topMixInitialError.value = null
                val hasVisibleMixes = observedTopMixes.value.orEmpty().isNotEmpty()
                try {
                    when (val result = refreshLibraryTopMixes()) {
                        RefreshLibraryTopMixesResult.Success -> {
                            _topMixInitialError.value = null
                        }

                        is RefreshLibraryTopMixesResult.Failure -> {
                            result.cause?.let(::reportException)
                            val message = result.reason.toTopMixMessage(result.cause)
                            if (!isInitialGeneration && hasVisibleMixes) {
                                _topMixEvents.emit(message)
                            } else {
                                _topMixInitialError.value = message
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    val message = TopMixGenerationFailure.AI_REQUEST_FAILED.toTopMixMessage(e)
                    if (!isInitialGeneration && hasVisibleMixes) {
                        _topMixEvents.emit(message)
                    } else {
                        _topMixInitialError.value = message
                    }
                } finally {
                    _isTopMixRefreshing.value = false
                }
            }
        }

        private fun TopMixGenerationFailure.toTopMixMessage(cause: Throwable?): String =
            when (this) {
                TopMixGenerationFailure.AI_NOT_CONFIGURED -> {
                    context.getString(R.string.library_top_mixes_ai_not_configured_desc)
                }

                TopMixGenerationFailure.NO_RECENT_HISTORY -> {
                    context.getString(R.string.library_top_mixes_no_recent_history)
                }

                TopMixGenerationFailure.NO_VALID_MIXES -> {
                    context.getString(R.string.library_top_mixes_no_valid_mixes)
                }

                TopMixGenerationFailure.AI_REQUEST_FAILED -> {
                    buildString {
                        append(context.getString(R.string.library_top_mixes_failed))
                        cause?.localizedMessage?.takeIf(String::isNotBlank)?.let { message ->
                            append(": ")
                            append(message)
                        }
                    }
                }
            }

        val topValue =
            context.dataStore.data
                .map { it[TopSize] ?: "50" }
                .distinctUntilChanged()
        var artists =
            database
                .artistsBookmarked(
                    ArtistSortType.CREATE_DATE,
                    true,
                ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        var albums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        var playlists =
            context.dataStore.data
                .map {
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (it[PlaylistSortDescendingKey] ?: true)
                }.distinctUntilChanged()
                .flatMapLatest { (sortType, descending) -> database.playlists(sortType, descending) }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            viewModelScope.launch(Dispatchers.IO) {
                albums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
                            YouTube
                                .album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                artists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                        }.forEach { artist ->
                            YouTube.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }
        }
    }

@Immutable
sealed interface LibraryTopMixesUiState {
    data object Loading : LibraryTopMixesUiState

    @Immutable
    data class Success(
        val mixes: ImmutableList<LibraryTopMixUiModel>,
        val isRefreshing: Boolean,
    ) : LibraryTopMixesUiState

    @Immutable
    data class Empty(
        val reason: LibraryTopMixEmptyReason,
        val isRefreshing: Boolean,
    ) : LibraryTopMixesUiState

    @Immutable
    data class Error(
        val message: String,
    ) : LibraryTopMixesUiState
}

enum class LibraryTopMixEmptyReason {
    AI_NOT_CONFIGURED,
    NO_RECENT_HISTORY,
}

@Immutable
data class LibraryTopMixUiModel(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)

private fun LibraryTopMix.toUiModel() =
    LibraryTopMixUiModel(
        id = id,
        title = title,
        description = description,
        tracks = ImmutableList.copyOf(tracks),
    )

@Immutable
sealed interface MostPlayedAlbumUiState {
    data object Loading : MostPlayedAlbumUiState

    @Immutable
    data class Success(
        val album: MostPlayedAlbumUiModel,
    ) : MostPlayedAlbumUiState

    data object Empty : MostPlayedAlbumUiState

    @Immutable
    data class Error(
        val message: String,
    ) : MostPlayedAlbumUiState
}

@Immutable
data class MostPlayedAlbumUiModel(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val trackCount: Int,
    val tracks: ImmutableList<MediaMetadata>,
)

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor() : ViewModel() {
        private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
        val filter: MutableState<LibraryFilter> = curScreen
    }
