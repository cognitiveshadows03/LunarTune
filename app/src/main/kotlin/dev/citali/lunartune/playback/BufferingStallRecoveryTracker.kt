/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

/** Tracks the one alternate-stream retry allowed during a continuous buffering stall. */
internal class BufferingStallRecoveryTracker {
    private var attemptedMediaId: String? = null

    fun hasAttemptedRecovery(mediaId: String): Boolean = attemptedMediaId == mediaId

    fun registerRecoveryAttempt(mediaId: String): Boolean {
        if (hasAttemptedRecovery(mediaId)) return false
        attemptedMediaId = mediaId
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && attemptedMediaId == mediaId) {
            attemptedMediaId = null
        }
    }

    fun onMediaItemChanged(mediaId: String?) {
        if (attemptedMediaId != mediaId) {
            attemptedMediaId = null
        }
    }
}
