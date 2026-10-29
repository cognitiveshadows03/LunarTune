/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.theme

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Typeface as AndroidTypeface

/**
 * Loads the user's custom typeface.
 *
 * The chosen font is copied once into app-private storage, so at every later
 * start the family can be rebuilt straight from a local file: no SAF round
 * trip, no permission that may have been revoked, and no wait on a picker
 * provider that has gone away. [cachedFontFamily] is the synchronous entry
 * point — the theme asks for it on the very first composition so the custom
 * face is already in place on frame one instead of snapping in a moment later.
 */
object CustomFontLoader {
    /** Extensions we can hand to the platform typeface loader. */
    private val supportedExtensions = arrayOf("ttf", "otf", "ttc")

    val supportedMimeTypes =
        arrayOf(
            "font/ttf",
            "font/otf",
            "font/collection",
            "application/x-font-ttf",
            "application/x-font-truetype",
            "application/x-font-opentype",
            "application/vnd.ms-opentype",
            "application/octet-stream",
        )

    /** Families already built in this process, keyed by the picked URI string. */
    private val familyCache = ConcurrentHashMap<String, FontFamily>()

    fun displayName(
        context: Context,
        uri: Uri,
    ): String {
        val resolvedName =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    }
            }.getOrNull()

        return resolvedName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    fun isSupportedTtf(
        context: Context,
        uri: Uri,
    ): Boolean {
        val name = displayName(context, uri)
        return supportedExtensions.any { name.endsWith(".$it", ignoreCase = true) }
    }

    /** The private copy of the picked font, when one has already been saved. */
    fun localFontFile(
        context: Context,
        uriString: String,
    ): File? {
        if (uriString.isBlank()) return null
        val directory = File(context.applicationContext.filesDir, "custom_fonts")
        val stem = Integer.toHexString(uriString.hashCode())
        return supportedExtensions
            .map { File(directory, "$stem.$it") }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    /**
     * Builds (or returns) the custom family without suspending, using only the
     * saved private copy. Returns null while that copy does not exist yet, in
     * which case [loadFontFamily] imports it in the background.
     */
    fun cachedFontFamily(
        context: Context,
        uriString: String,
    ): FontFamily? {
        if (uriString.isBlank()) return null
        familyCache[uriString]?.let { return it }

        val file = localFontFile(context, uriString) ?: return null
        return runCatching {
            FontFamily(Typeface(AndroidTypeface.createFromFile(file)))
        }.getOrNull()?.also { familyCache[uriString] = it }
    }

    /** Warms [cachedFontFamily] so the first frame already carries the face. */
    fun preload(
        context: Context,
        uriString: String,
    ) {
        if (uriString.isBlank()) return
        runCatching { cachedFontFamily(context, uriString) }
    }

    /**
     * Copies the picked font into private storage right away, so the choice is
     * saved the moment it is made rather than only when the theme reloads it.
     * Returns the family, or null if the pick could not be read.
     */
    suspend fun importFontFamily(
        context: Context,
        uriString: String,
    ): FontFamily? =
        withContext(Dispatchers.IO) {
            if (uriString.isBlank()) return@withContext null
            runCatching {
                val uri = Uri.parse(uriString)
                if (!isSupportedTtf(context, uri)) return@withContext null
                val fontFile = copyToPrivateFontFile(context, uri, uriString)
                FontFamily(Typeface(AndroidTypeface.createFromFile(fontFile)))
                    .also { familyCache[uriString] = it }
            }.getOrNull()
        }

    suspend fun loadFontFamily(
        context: Context,
        uriString: String,
    ): FontFamily? {
        cachedFontFamily(context, uriString)?.let { return it }
        return try {
            importFontFamily(context, uriString)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    fun forget(uriString: String) {
        familyCache.remove(uriString)
    }

    private fun copyToPrivateFontFile(
        context: Context,
        uri: Uri,
        uriString: String,
    ): File {
        val fontDirectory = File(context.filesDir, "custom_fonts")
        if (!fontDirectory.exists()) fontDirectory.mkdirs()

        val name = displayName(context, uri)
        val extension =
            supportedExtensions.firstOrNull { name.endsWith(".$it", ignoreCase = true) } ?: "ttf"
        val fontFile = File(fontDirectory, "${Integer.toHexString(uriString.hashCode())}.$extension")

        // A previous pick with the same URI already produced a good copy.
        if (fontFile.isFile && fontFile.length() > 0L) return fontFile

        context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream)
            fontFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return fontFile
    }
}
