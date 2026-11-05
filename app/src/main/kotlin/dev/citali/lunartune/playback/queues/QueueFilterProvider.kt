/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback.queues

import androidx.media3.common.MediaItem
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.models.MediaMetadata
import dev.citali.lunartune.models.QueueFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import timber.log.Timber

/**
 * Builds the auto-play radio queue for the filter chips in the queue screen.
 *
 * Every filter still starts from the seed's radio queue, so switching a chip
 * never interrupts the song that is playing - it only changes which upcoming
 * songs are appended. Library / history data and artist/related shelves decide
 * the ordering, so a filter can bias towards discovery, familiar material, top
 * hits or deeper cuts.
 */
object QueueFilterProvider {
    suspend fun fetchFilteredQueue(
        seedSong: MediaMetadata,
        filter: QueueFilter,
        database: MusicDatabase,
        hideExplicit: Boolean = false,
        hideVideo: Boolean = false,
    ): Pair<List<MediaItem>, Queue> =
        withContext(Dispatchers.IO) {
            val seedId = seedSong.id.trim()
            val artistId = seedSong.artists.firstOrNull()?.id?.trim()
            val artistName = seedSong.artists.firstOrNull()?.name?.trim().orEmpty()

            val radioQueue =
                YouTubeQueue(
                    endpoint = WatchEndpoint(videoId = seedId),
                    followAutomixPreview = true,
                )
            val radioItems = loadRadioItems(radioQueue, seedId, hideExplicit, hideVideo)

            val items =
                when (filter) {
                    QueueFilter.ALL -> radioItems

                    QueueFilter.DISCOVER -> {
                        val knownIds = loadKnownIds(database)
                        val candidates =
                            (radioItems + fetchRelatedSongItems(seedId, hideExplicit).map { it.toMediaItem() })
                                .filter { it.mediaId != seedId }
                                .distinctBy { it.mediaId }
                        val undiscovered = candidates.filter { it.mediaId !in knownIds }
                        undiscovered + candidates.filter { it.mediaId in knownIds }
                    }

                    QueueFilter.FAMILIAR -> {
                        val knownIds = loadKnownIds(database)
                        val localArtistSongs = loadLocalArtistSongs(database, artistId, artistName, seedId)
                        val candidates = (localArtistSongs + radioItems).distinctBy { it.mediaId }
                        candidates.filter { it.mediaId in knownIds } + candidates.filter { it.mediaId !in knownIds }
                    }

                    QueueFilter.POPULAR -> {
                        val artistTopSongs = loadArtistSongs(artistId, seedId)
                        val candidates =
                            (artistTopSongs + fetchRelatedSongItems(seedId, hideExplicit).map { it.toMediaItem() } + radioItems)
                                .filter { it.mediaId != seedId }
                                .distinctBy { it.mediaId }
                        candidates
                    }

                    QueueFilter.DEEP_CUTS -> {
                        val artistSongs = loadArtistSongs(artistId, seedId)
                        val topIds = artistSongs.take(5).mapTo(HashSet(5)) { it.mediaId }
                        val deeperArtistSongs = artistSongs.filter { it.mediaId !in topIds }
                        val deeperRadio = if (radioItems.size > 8) radioItems.drop(4) else radioItems
                        (deeperArtistSongs + deeperRadio).distinctBy { it.mediaId }
                    }
                }

            items.take(50) to radioQueue
        }

    private suspend fun loadRadioItems(
        radioQueue: YouTubeQueue,
        seedId: String,
        hideExplicit: Boolean,
        hideVideo: Boolean,
    ): List<MediaItem> =
        runCatching {
            radioQueue
                .getInitialStatus()
                .filterExplicit(hideExplicit)
                .filterVideo(hideVideo)
                .items
                .filter { it.mediaId != seedId }
        }.onFailure { throwable -> Timber.w(throwable, "QueueFilterProvider: radio failed" ) }
            .getOrDefault(emptyList())

    private suspend fun loadKnownIds(database: MusicDatabase): Set<String> =
        runCatching {
            val inLibrary = database.songsByRowIdAsc().first().map { it.id }.toSet()
            val history = database.songsByPlayTimeAsc().first().map { it.id }.toSet()
            inLibrary + history
        }.getOrDefault(emptySet())

    private suspend fun loadLocalArtistSongs(
        database: MusicDatabase,
        artistId: String?,
        artistName: String,
        seedId: String,
    ): List<MediaItem> =
        if (artistId == null && artistName.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                database
                    .songsByRowIdAsc()
                    .first()
                    .filter { song ->
                        song.artists.any { artist ->
                            artist.name.equals(artistName, ignoreCase = true) ||
                                (artistId != null && artist.id == artistId)
                        }
                    }.map { it.toMediaItem() }
                    .filter { it.mediaId != seedId }
            }.onFailure { throwable -> Timber.w(throwable, "QueueFilterProvider: local artist songs failed") }
                .getOrDefault(emptyList())
        }

    private suspend fun loadArtistSongs(
        artistId: String?,
        seedId: String,
    ): List<MediaItem> =
        if (artistId == null || !artistId.startsWith("UC")) {
            emptyList()
        } else {
            runCatching {
                YouTube
                    .artist(artistId)
                    .getOrNull()
                    ?.sections
                    ?.flatMap { it.items }
                    ?.filterIsInstance<SongItem>()
                    ?.map { it.toMediaItem() }
                    ?.filter { it.mediaId != seedId }
                    .orEmpty()
            }.onFailure { throwable -> Timber.w(throwable, "QueueFilterProvider: artist page failed") }
                .getOrDefault(emptyList())
        }

    private suspend fun fetchRelatedSongItems(
        videoId: String,
        hideExplicit: Boolean,
    ): List<SongItem> =
        runCatching {
            val relatedEndpoint =
                YouTube
                    .next(WatchEndpoint(videoId = videoId))
                    .getOrNull()
                    ?.relatedEndpoint
                    ?: BrowseEndpoint(browseId = "FErelated")
            val songs = YouTube.related(relatedEndpoint).getOrNull()?.songs.orEmpty()
            if (hideExplicit) songs.filter { !it.explicit } else songs
        }.onFailure { throwable -> Timber.w(throwable, "QueueFilterProvider: related songs failed") }
            .getOrDefault(emptyList())
}
