/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import dev.citali.lunartune.extensions.toMediaItem
import dev.citali.lunartune.utils.neverRecommendIds
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import dev.citali.lunartune.models.MediaMetadata

class YouTubeQueue(
    internal var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    internal val followAutomixPreview: Boolean = false,
    private val expandToFullQueueWhenAutoLoadMoreDisabled: Boolean = false,
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube
                    .next(
                        endpoint = endpoint,
                        continuation = continuation,
                        followAutomixPreview = followAutomixPreview,
                    ).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        var items = nextResult.items.map { it.toMediaItem() }
        var index = nextResult.currentIndex ?: 0
        if (followAutomixPreview) {
            // Radio / autoplay: drop "never recommend" songs, but keep the song the user started.
            val hidden = neverRecommendIds()
            if (hidden.isNotEmpty()) {
                val current = items.getOrNull(index)
                items = items.filterIndexed { i, item -> i == index || item.mediaId !in hidden }
                index = current?.let { items.indexOf(it) }?.coerceAtLeast(0) ?: 0
            }
        }
        return Queue.Status(
            title = nextResult.title,
            items = items,
            mediaItemIndex = index,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = expandToFullQueueWhenAutoLoadMoreDisabled

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult =
            withContext(IO) {
                YouTube
                    .next(
                        endpoint = endpoint,
                        continuation = continuation,
                        followAutomixPreview = followAutomixPreview,
                    ).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        val hidden = neverRecommendIds()
        return nextResult.items.filterNot { it.id in hidden }.map { it.toMediaItem() }
    }

    companion object {
        fun playlist(
            endpoint: WatchEndpoint,
            preloadItem: MediaMetadata? = null,
        ) = YouTubeQueue(
            endpoint = endpoint,
            preloadItem = preloadItem,
            expandToFullQueueWhenAutoLoadMoreDisabled = true,
        )

        fun radio(song: MediaMetadata) =
            YouTubeQueue(
                endpoint = WatchEndpoint(videoId = song.id),
                preloadItem = song,
                followAutomixPreview = true,
            )
    }
}
