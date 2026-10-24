/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferingStallRecoveryTrackerTest {
    @Test
    fun allowsOnlyOneRetryWhileTheSameTrackRemainsStalled() {
        val tracker = BufferingStallRecoveryTracker()

        assertFalse(tracker.hasAttemptedRecovery("track-a"))
        assertTrue(tracker.registerRecoveryAttempt("track-a"))
        assertTrue(tracker.hasAttemptedRecovery("track-a"))
        assertFalse(tracker.registerRecoveryAttempt("track-a"))
    }

    @Test
    fun changingTracksClearsThePreviousTracksRetry() {
        val tracker = BufferingStallRecoveryTracker()
        tracker.registerRecoveryAttempt("track-a")

        tracker.onMediaItemChanged("track-b")

        assertFalse(tracker.hasAttemptedRecovery("track-b"))
        assertTrue(tracker.registerRecoveryAttempt("track-b"))
    }

    @Test
    fun playbackRecoveryAllowsALaterStallToRetry() {
        val tracker = BufferingStallRecoveryTracker()
        tracker.registerRecoveryAttempt("track-a")

        tracker.onPlaybackRecovered("track-a")

        assertFalse(tracker.hasAttemptedRecovery("track-a"))
        assertTrue(tracker.registerRecoveryAttempt("track-a"))
    }
}
