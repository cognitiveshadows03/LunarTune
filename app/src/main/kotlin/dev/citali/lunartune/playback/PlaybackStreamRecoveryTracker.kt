/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

internal class PlaybackStreamRecoveryTracker(
    private val maxRetriesPerItem: Int = DEFAULT_MAX_RETRIES_PER_ITEM,
) {
    private var trackedMediaId: String? = null
    private var attemptsForTrackedItem: Int = 0

    fun registerRetryAttempt(mediaId: String): Boolean {
        if (trackedMediaId != mediaId) {
            trackedMediaId = mediaId
            attemptsForTrackedItem = 0
        }
        if (attemptsForTrackedItem >= maxRetriesPerItem) return false
        attemptsForTrackedItem += 1
        return true
    }

    fun isFinalRetryAttempt(mediaId: String): Boolean =
        trackedMediaId == mediaId && attemptsForTrackedItem >= maxRetriesPerItem

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && trackedMediaId == mediaId) {
            trackedMediaId = null
            attemptsForTrackedItem = 0
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (trackedMediaId != currentMediaId) {
            trackedMediaId = null
            attemptsForTrackedItem = 0
        }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES_PER_ITEM = 3
    }
}
