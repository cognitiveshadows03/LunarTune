/*
 * LunarTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import android.util.Log
import dev.citali.lunartune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.unison.Unison
import dev.citali.lunartune.utils.GlobalLog
import dev.citali.lunartune.utils.dataStore
import dev.citali.lunartune.utils.get

object UnisonLyricsProvider : LyricsProvider {
    init {
        Unison.logger = { message ->
            GlobalLog.append(Log.INFO, "Unison", message)
        }
    }

    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        // The client now answers with the whole entry rather than just the text, so the
        // format and sync type stay available for the log instead of being flattened here.
        Unison.getLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
        ).map { entry ->
            GlobalLog.append(
                Log.INFO,
                "Unison",
                "using ${entry.format ?: "unknown"} (${entry.syncType ?: "unknown sync"})",
            )
            entry.lyrics
        }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = { entry -> callback(entry.lyrics) },
        )
    }
}
