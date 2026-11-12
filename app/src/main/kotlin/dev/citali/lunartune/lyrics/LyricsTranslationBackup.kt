/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the lyrics that were on screen right before a translation replaced them.
 *
 * Both translation paths (AI and the bundled translator) overwrite the stored
 * lyrics row with [dev.citali.lunartune.db.entities.LyricsEntity.Source.AI_TRANSLATION],
 * so the original text would otherwise be gone for good. A single small file per
 * song is enough: the translation can be removed and the original put back
 * without a network round trip or a database migration.
 *
 * The file holds the source of the saved lyrics on its first line, then the
 * lyrics themselves.
 */
object LyricsTranslationBackup {
    private const val DIRECTORY = "lyrics_original"
    private const val SEPARATOR = '\n'

    private fun backupFile(
        context: Context,
        mediaId: String,
    ): File {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.exists()) directory.mkdirs()
        val name =
            MessageDigest
                .getInstance("SHA-1")
                .digest(mediaId.toByteArray())
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(directory, "$name.txt")
    }

    /** Remembers [lyrics] as the untranslated text for [mediaId]. */
    fun save(
        context: Context,
        mediaId: String,
        lyrics: String,
        source: String,
    ): Boolean =
        runCatching {
            backupFile(context, mediaId).writeText(source + SEPARATOR + lyrics)
            true
        }.getOrDefault(false)

    /** Returns the saved lyrics and their source, or null when nothing was saved. */
    fun load(
        context: Context,
        mediaId: String,
    ): Pair<String, String>? =
        runCatching {
            val file = backupFile(context, mediaId)
            if (!file.exists()) return@runCatching null
            val text = file.readText()
            val breakIndex = text.indexOf(SEPARATOR)
            if (breakIndex <= 0) return@runCatching null
            val source = text.substring(0, breakIndex)
            val lyrics = text.substring(breakIndex + 1)
            if (lyrics.isBlank()) null else lyrics to source
        }.getOrNull()

    fun clear(
        context: Context,
        mediaId: String,
    ) {
        runCatching { backupFile(context, mediaId).delete() }
    }
}
