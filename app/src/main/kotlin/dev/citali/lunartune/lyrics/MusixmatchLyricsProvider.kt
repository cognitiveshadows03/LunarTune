/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import android.util.Log
import dev.citali.lunartune.constants.EnableMusixmatchLyricsKey
import dev.citali.lunartune.utils.GlobalLog
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get
import moe.rukamori.archivetune.musixmatch.Musixmatch

/**
 * Musixmatch, word-synced where it has it.
 *
 * This is the one word-level source that needs no key of its own: Musixmatch's rich sync is
 * what its own apps display, and the client module handles the token, the search and the
 * rich-sync-to-TTML conversion. That matters because the other word-level sources are either
 * cache-gated (BetterLyrics without a key) or gone (Paxsenix).
 *
 * The module resolves a track by name, artist, album and duration rather than by any id, and
 * falls back from its macro endpoint to a search when the metadata does not line up - so the
 * only input this provider owes it is clean metadata.
 */
object MusixmatchLyricsProvider : LyricsProvider {
    private const val LOG_TAG = "Musixmatch"

    init {
        Musixmatch.logger = { message -> GlobalLog.append(Log.INFO, LOG_TAG, message) }
    }

    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMusixmatchLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        Musixmatch.getLyrics(
            title = title.trim(),
            artist = artist.trim(),
            album = album,
            durationSeconds = duration,
        )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Musixmatch.getAllLyrics(
            title = title.trim(),
            artist = artist.trim(),
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
