/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import dev.citali.lunartune.constants.EnableBetterLyricsPortatoKey
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get

/**
 * The QQ/Portato endpoint of the Better Lyrics API, kept as its own provider for the
 * syllable-level QRC timing it returns.
 *
 * Routed through [BetterLyricsClient] so it shares the same host, credential and
 * per-endpoint 401 backoff as [BetterLyricsProvider]. A miss on the main endpoint no
 * longer blocks this one, which keeps its own cache.
 */
object BetterLyricsPortatoProvider : LyricsProvider {
    override val name = "BetterLyrics Portato"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsPortatoKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val results =
            BetterLyricsClient.fetch(
                title = title,
                artist = artist,
                durationSeconds = duration,
                endpoints = BetterLyricsClient.portatoEndpoints,
            )
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
            .fetch(
                title = title,
                artist = artist,
                durationSeconds = duration,
                endpoints = BetterLyricsClient.portatoEndpoints,
            ).forEach(callback)
    }
}
