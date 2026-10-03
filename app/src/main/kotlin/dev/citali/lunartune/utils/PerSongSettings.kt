/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import dev.citali.lunartune.constants.NeverRecommendSongIdsKey
import dev.citali.lunartune.constants.PerSongAlbumArtOverridesKey
import dev.citali.lunartune.constants.PerSongTagsKey

fun parseTabMap(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separator = line.indexOf('\t')
            if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
            line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
}

fun serializeTabMap(map: Map<String, String>): String =
    map.entries
        .sortedBy { it.key }
        .joinToString("\n") { "${it.key}\t${it.value}" }

fun setTabMapValue(
    raw: String,
    id: String,
    value: String?,
): String {
    if (id.isBlank()) return raw
    val next = parseTabMap(raw).toMutableMap()
    if (value.isNullOrBlank()) next.remove(id) else next[id] = value
    return serializeTabMap(next)
}

fun parseIdSet(raw: String): Set<String> =
    raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

fun serializeIdSet(ids: Set<String>): String = ids.sorted().joinToString("\n")

fun toggleIdSet(
    raw: String,
    id: String,
    enabled: Boolean,
): String {
    val next = parseIdSet(raw).toMutableSet()
    if (enabled) next.add(id) else next.remove(id)
    return serializeIdSet(next)
}

fun neverRecommendIds(): Set<String> = parseIdSet(PreferenceStore.get(NeverRecommendSongIdsKey).orEmpty())

fun copySongArtworkFromGallery(
    context: Context,
    songId: String,
    source: Uri,
): String? {
    if (songId.isBlank()) return null
    val destDir = File(context.filesDir, "song_art")
    if (!destDir.exists() && !destDir.mkdirs()) return null

    // Local-song IDs can be file/content URIs and therefore contain '/' and other characters
    // that are not valid in a single filename. Hash the ID instead of using it as a path.
    val safeId =
        MessageDigest
            .getInstance("SHA-256")
            .digest(songId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    val dest = File(destDir, "$safeId.img")
    val pending = File(destDir, "$safeId.tmp")

    return runCatching {
        val copiedBytes =
            context.contentResolver.openInputStream(source)?.use { input ->
                pending.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        if (copiedBytes <= 0L) {
            pending.delete()
            return null
        }
        if (!pending.renameTo(dest)) {
            pending.copyTo(dest, overwrite = true)
            pending.delete()
        }
        Uri.fromFile(dest).toString()
    }.onFailure {
        pending.delete()
    }.getOrNull()
}

fun parseSongTags(raw: String): List<String> =
    raw
        .split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

fun formatSongTags(tags: List<String>): String = tags.joinToString(", ")
