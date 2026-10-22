/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import dev.citali.lunartune.constants.EnableBetterLyricsKey
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get

/**
 * Better Lyrics, served by [BetterLyricsClient] rather than the bundled SDK.
 *
 * The SDK could not express the three things that decide whether this provider works at
 * all now: which host to talk to, whether to send album, and how to react to a 401. See
 * the client for the measurements behind each.
 */
object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val results = BetterLyricsClient.fetch(title = title, artist = artist, durationSeconds = duration)
        val first = results.firstOrNull() ?: return Result.failure(IllegalStateException("Lyrics unavailable"))
        return Result.success(first)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        BetterLyricsClient
            .fetch(title = title, artist = artist, durationSeconds = duration)
            .forEach(callback)
    }
}
