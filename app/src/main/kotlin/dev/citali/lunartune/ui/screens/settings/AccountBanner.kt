/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object AccountBanner {
    const val SOURCE_YOUTUBE = "youtube"
    const val SOURCE_CUSTOM = "custom"
    const val SOURCE_NONE = "none"

    private const val FILE_NAME = "account_banner.jpg"
    private const val MAX_WIDTH = 1600

    fun customFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Copies (and downsizes) the picked image into app storage so it survives without URI permissions. */
    suspend fun saveCustom(
        context: Context,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
                val bitmap =
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching false
                val scaled =
                    if (bitmap.width > MAX_WIDTH) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            MAX_WIDTH,
                            (bitmap.height * MAX_WIDTH.toFloat() / bitmap.width).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else {
                        bitmap
                    }
                val target = customFile(context)
                val temp = File(target.parentFile, "$FILE_NAME.tmp")
                temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                temp.renameTo(target)
            }.onFailure(Timber::w).getOrDefault(false)
        }

    fun deleteCustom(context: Context) {
        runCatching { customFile(context).delete() }
    }

    /**
     * Reads the public YouTube channel page for [handle] and returns its banner image URL,
     * an empty string when the channel has no banner, or null when the lookup failed.
     */
    suspend fun fetchYouTubeBanner(handle: String): String? =
        withContext(Dispatchers.IO) {
            val cleanHandle = handle.trim().removePrefix("@")
            if (cleanHandle.isEmpty()) return@withContext ""
            runCatching {
                val url = URL("https://www.youtube.com/@" + URLEncoder.encode(cleanHandle, "UTF-8"))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    // Skip the EU cookie-consent interstitial.
                    setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    val sources =
                        BannerSourcesRegex.find(html)?.groupValues?.get(1)
                            ?: LegacyBannerRegex.find(html)?.groupValues?.get(1)
                            ?: return@runCatching ""
                    val first =
                        UrlRegex.find(sources)?.groupValues?.get(1)?.replace("\\u0026", "&")
                            ?: return@runCatching ""
                    val absolute = if (first.startsWith("//")) "https:$first" else first
                    // Drop YouTube's narrow 6:1 crop and ask for the full-size artwork instead.
                    val base = absolute.substringBefore('=')
                    "$base=w1600-k-c0xffffffff-no-nd-rj"
                } finally {
                    connection.disconnect()
                }
            }.onFailure(Timber::w).getOrNull()
        }

    private val BannerSourcesRegex = Regex("\"imageBannerViewModel\":\\{\"image\":\\{\"sources\":\\[(.*?)]")
    private val LegacyBannerRegex = Regex("\"banner\":\\{\"thumbnails\":\\[(.*?)]")
    private val UrlRegex = Regex("\"url\":\"(.*?)\"")
}

/** Pulls the element up by [amount] so it overlaps whatever is above it, without leaving a gap below. */
internal fun Modifier.overlapUp(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = amount.roundToPx()
        layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
            placeable.place(0, -shift)
        }
    }
