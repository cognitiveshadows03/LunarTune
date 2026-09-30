/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Album art persisted for songs the user has downloaded.
 *
 * A downloaded song only ever stored a *remote* thumbnail URL, so its cover was
 * whatever happened to be sitting in Coil's image cache. Clearing that cache,
 * or simply playing the track with no connection, left the song with no artwork
 * at all even though the audio was still on disk - the cover had no lifecycle
 * tied to the download.
 *
 * The image is written into the app's own storage, which lives and dies with the
 * downloaded audio itself.
 *
 * The file is keyed by a hash of the thumbnail URL rather than the song id. That
 * is deliberate: the artwork composables are handed a URL and nothing else, so a
 * URL-derived key is the only thing both sides can agree on, and it avoids adding
 * a column to the song table. Because the song's stored thumbnailUrl is what is
 * hashed on both sides, removing a download can still find and delete its art.
 */
object DownloadedArtwork {
    private const val DIRECTORY = "downloaded-artwork"

    /** Covers are small; anything larger is not an image and is discarded. */
    const val MAX_ART_BYTES = 4 * 1024 * 1024

    private fun fileFor(
        context: Context,
        artworkUrl: String?,
    ): File? {
        val url = artworkUrl?.trim()?.takeIf(String::isNotBlank) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val name = digest.joinToString(separator = "") { byte ->
            String.format("%02x", byte.toInt() and 0xFF)
        }
        return File(File(context.filesDir, DIRECTORY), name)
    }

    /**
     * The stored artwork for [artworkUrl], or null when there is none.
     *
     * Callers use this to prefer the local file over a network fetch, so a
     * downloaded track renders its cover offline.
     */
    fun localFile(
        context: Context,
        artworkUrl: String?,
    ): File? = fileFor(context, artworkUrl)?.takeIf { it.isFile && it.length() > 0L }

    /**
     * Writes [bytes] as the artwork for [artworkUrl].
     *
     * Returns false rather than throwing on any problem: artwork is secondary to
     * the audio, and a failed cover must never fail or roll back a download that
     * already succeeded.
     */
    fun save(
        context: Context,
        artworkUrl: String?,
        bytes: ByteArray,
    ): Boolean {
        val target = fileFor(context, artworkUrl) ?: return false
        if (bytes.isEmpty() || bytes.size > MAX_ART_BYTES) return false
        return runCatching {
            target.parentFile?.mkdirs()
            // Write to a sibling temp file and rename, so a reader never observes a
            // half-written image while a download is finishing.
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                false
            } else {
                true
            }
        }.getOrDefault(false)
    }

    /** Drops the stored artwork for [artworkUrl], if any. */
    fun delete(
        context: Context,
        artworkUrl: String?,
    ) {
        runCatching { fileFor(context, artworkUrl)?.delete() }
    }

    /**
     * Reads at most [maxBytes] from this stream, or null when the payload is
     * larger than that.
     */
    fun readBounded(
        input: InputStream,
        maxBytes: Int = MAX_ART_BYTES,
    ): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
