/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.citali.lunartune.lastfm.LastFM
import dev.citali.lunartune.models.MediaMetadata
import timber.log.Timber
import kotlin.math.min

/**
 * Drives Last.fm scrobbling. When the backup switch is on, every scrobble is
 * written to a durable queue first and sent as soon as a validated network is
 * available, so plays made offline are scrobbled when connectivity returns.
 */
class ScrobbleManager(
    private val context: Context,
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 180,
) {
    private val backupStore = ScrobbleBackupStore(context)
    private val networkLock = Any()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var backupEnabled = false

    private var scrobbleJob: Job? = null
    private var scrobbleRemainingMillis: Long = 0L
    private var scrobbleTimerStartedAt: Long = 0L
    private var songStartedAt: Long = 0L
    private var songStarted = false
    var useNowPlaying = true

    fun destroy() {
        scrobbleJob?.cancel()
        unregisterNetworkCallback()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
    }

    /**
     * Enables the offline queue and starts watching connectivity. Turning it off
     * stops queueing new plays but leaves the existing queue to drain on the
     * next flush, so nothing is silently discarded.
     */
    fun setBackupEnabled(enabled: Boolean) {
        synchronized(networkLock) {
            backupEnabled = enabled
            if (enabled) {
                registerNetworkCallback()
                scope.launch { flushPending() }
            } else {
                unregisterNetworkCallback()
                // Drain anything already queued so turning the switch off never
                // silently discards offline plays.
                scope.launch { flushPending() }
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val connectivityManager =
            context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { flushPending() }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        scope.launch { flushPending() }
                    }
                }
            }
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            context
                .getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(callback)
        }
    }

    fun onSongStart(
        metadata: MediaMetadata?,
        duration: Long? = null,
    ) {
        if (metadata == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        songStarted = true
        startScrobbleTimer(metadata, duration)
        if (useNowPlaying) {
            updateNowPlaying(metadata)
        }
    }

    fun onSongResume(metadata: MediaMetadata) {
        resumeScrobbleTimer(metadata)
    }

    fun onSongPause() {
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        stopScrobbleTimer()
        songStarted = false
    }

    private fun startScrobbleTimer(
        metadata: MediaMetadata,
        duration: Long? = null,
    ) {
        scrobbleJob?.cancel()
        val resolvedDuration = duration?.toInt()?.div(1000) ?: metadata.duration

        if (resolvedDuration <= minSongDuration) return

        val threshold = resolvedDuration * 1000L * scrobbleDelayPercent
        scrobbleRemainingMillis = min(threshold.toLong(), scrobbleDelaySeconds * 1000L)

        if (scrobbleRemainingMillis <= 0) {
            scrobbleSong(metadata)
            return
        }
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(metadata)
                scrobbleJob = null
            }
    }

    private fun pauseScrobbleTimer() {
        scrobbleJob?.cancel()
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        }
    }

    private fun resumeScrobbleTimer(metadata: MediaMetadata) {
        if (scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(metadata)
                scrobbleJob = null
            }
    }

    private fun stopScrobbleTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0
    }

    private fun scrobbleSong(metadata: MediaMetadata) {
        val item =
            PendingScrobble(
                artist = metadata.artists.joinToString(", ") { artist -> artist.name },
                track = metadata.title,
                timestamp = songStartedAt,
                album = metadata.album?.title,
                duration = metadata.duration.takeIf { it > 0 },
            )
        scope.launch {
            if (backupEnabled) {
                backupStore.edit { pending ->
                    pending.add(item)
                    while (pending.size > MAX_PENDING_SCROBBLES) pending.removeAt(0)
                }
                flushPending()
            } else {
                sendScrobble(item)
            }
        }
    }

    /** Sends every queued scrobble that is still valid, keeping the ones that failed. */
    suspend fun flushPending() {
        if (!isInternetAvailable(context)) return
        backupStore.edit { pending ->
            if (pending.isEmpty()) return@edit
            val earliest = System.currentTimeMillis() / 1000 - MAX_SCROBBLE_AGE_SECONDS
            val keep = mutableListOf<PendingScrobble>()
            for (item in pending) {
                when {
                    // The service rejects timestamps older than two weeks.
                    item.timestamp < earliest -> Unit
                    !sendScrobble(item) -> keep.add(item)
                }
            }
            while (keep.size > MAX_PENDING_SCROBBLES) keep.removeAt(0)
            pending.clear()
            pending.addAll(keep)
            Unit
        }
    }

    private suspend fun sendScrobble(item: PendingScrobble): Boolean =
        LastFM
            .scrobble(
                artist = item.artist,
                track = item.track,
                timestamp = item.timestamp,
                album = item.album,
                duration = item.duration,
            ).onSuccess {
                Timber.tag("ScrobbleManager").d("Scrobbled: ${item.track} by ${item.artist}")
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.tag("ScrobbleManager").e(throwable, "Failed to scrobble: ${item.track}")
            }.isSuccess

    private fun updateNowPlaying(metadata: MediaMetadata) {
        scope.launch {
            LastFM
                .updateNowPlaying(
                    artist = metadata.artists.joinToString(", ") { artist -> artist.name },
                    track = metadata.title,
                    album = metadata.album?.title,
                    duration = metadata.duration,
                ).onSuccess {
                    Timber.tag("ScrobbleManager").d("Updated now playing: ${metadata.title}")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag("ScrobbleManager").e(throwable, "Failed to update now playing: ${metadata.title}")
                }
        }
    }

    fun onPlayerStateChanged(
        isPlaying: Boolean,
        metadata: MediaMetadata?,
        duration: Long? = null,
    ) {
        if (metadata == null) return
        if (isPlaying) {
            if (!songStarted) {
                onSongStart(metadata, duration)
            } else {
                onSongResume(metadata)
            }
        } else {
            onSongPause()
        }
    }

    private companion object {
        const val MAX_PENDING_SCROBBLES = 300
        const val MAX_SCROBBLE_AGE_SECONDS = 14L * 24L * 60L * 60L
    }
}
